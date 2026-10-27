package com.happymouse.cryd.service.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happymouse.cryd.service.spark.SparkClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * RAG 事件收集器 — 在内存中记录RAG管线关键事件，供管理员监控面板消费
 * 
 * 事件类型：
 *   GATE1_REJECT    — 第一道门拦截（知识缺口！）
 *   GATE1_PASS      — 第一道门放行
 *   FACT_CHECK_FAIL — 事实校验不通过（需重生成）
 *   HALLUCINATION_RETRY — 幻觉触发重生成（含修正前后内容）
 *   PIPELINE_DONE   — 完整管线结束
 */
@Service
public class RagEventStore {

    private static final Logger log = LoggerFactory.getLogger(RagEventStore.class);
    private static final String DATA_FILE = "data/rag_events.json";
    private static final int MAX_EVENTS = 500;

    private final ObjectMapper mapper = new ObjectMapper();
    private final SparkClient sparkClient;
    private final ConcurrentLinkedDeque<Map<String, Object>> events = new ConcurrentLinkedDeque<>();

    // 聚合统计
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong gate1Passed = new AtomicLong(0);
    private final AtomicLong gate1Rejected = new AtomicLong(0);
    private final AtomicLong hallucinationRetries = new AtomicLong(0);
    private final AtomicLong factCheckFails = new AtomicLong(0);
    private final DoubleAdder confidenceSum = new DoubleAdder();
    private final AtomicLong confidenceCount = new AtomicLong(0);

    // 被拒 query 收集（知识缺口）
    private final ConcurrentLinkedDeque<Map<String, Object>> rejectedQueries = new ConcurrentLinkedDeque<>();
    private static final int MAX_REJECTED = 100;

    // 幻觉事故列表（含修正前后内容）
    private final ConcurrentLinkedDeque<Map<String, Object>> hallucinationIncidents = new ConcurrentLinkedDeque<>();
    private static final int MAX_INCIDENTS = 100;

    // 缺口自动建议缓存（避免频繁调用LLM）
    private volatile List<Map<String, Object>> cachedGapSuggestions = null;
    private volatile long lastSuggestionTime = 0;
    private static final long SUGGESTION_CACHE_MS = 300_000; // 5分钟缓存

    public RagEventStore(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @PostConstruct
    public void init() {
        loadFromDisk();
        log.info("RagEventStore初始化: events={}, rejectedQueries={}, incidents={}",
                events.size(), rejectedQueries.size(), hallucinationIncidents.size());
    }

    @PreDestroy
    public void shutdown() {
        saveToDisk();
        log.info("RagEventStore已持久化");
    }

    // ==================== 事件记录 ====================

    public void recordGate1Pass(String query, int fragmentCount) {
        gate1Passed.incrementAndGet();
        totalQueries.incrementAndGet();
        addEvent("GATE1_PASS", query, fragmentCount, -1, "命中" + fragmentCount + "条片段");
    }

    public void recordGate1Reject(String query) {
        gate1Rejected.incrementAndGet();
        totalQueries.incrementAndGet();
        addEvent("GATE1_REJECT", query, 0, -1, "当前知识库无相关知识点");

        Map<String, Object> rj = new LinkedHashMap<>();
        rj.put("query", truncate(query, 200));
        rj.put("time", LocalDateTime.now().toString());
        rj.put("topic", guessTopic(query));
        rejectedQueries.addFirst(rj);
        while (rejectedQueries.size() > MAX_REJECTED) rejectedQueries.pollLast();
    }

    public void recordFactCheckFail(String query, int fragmentCount, double score, String reason) {
        factCheckFails.incrementAndGet();
        addEvent("FACT_CHECK_FAIL", query, fragmentCount, score, reason);
    }

    /** 
     * 幻觉触发重生成 — 含修正前后内容，用于 diff 对比
     * @param beforeContent 修正前的问题内容
     * @param afterContent  修正后的内容（可为 null，表示尚未生成）
     */
    public void recordHallucinationRetry(String query, int retryNum, double score, 
                                          String issues, String beforeContent, String afterContent) {
        hallucinationRetries.incrementAndGet();
        addEvent("HALLUCINATION_RETRY", query, 0, score,
                "第" + retryNum + "次重试: " + truncate(issues, 120));

        Map<String, Object> inc = new LinkedHashMap<>();
        inc.put("query", truncate(query, 150));
        inc.put("retryNum", retryNum);
        inc.put("score", Math.round(score * 100.0) / 100.0);
        inc.put("issues", truncate(issues, 200));
        inc.put("time", LocalDateTime.now().toString());
        inc.put("beforeContent", truncate(beforeContent, 2000));
        inc.put("afterContent", truncate(afterContent, 2000));
        hallucinationIncidents.addFirst(inc);
        while (hallucinationIncidents.size() > MAX_INCIDENTS) hallucinationIncidents.pollLast();
    }

    /** 更新已记录的幻觉事故的 afterContent（在重生成完成后调用） */
    public void updateLastIncidentAfterContent(String afterContent) {
        Map<String, Object> latest = hallucinationIncidents.peekFirst();
        if (latest != null) {
            latest.put("afterContent", truncate(afterContent, 2000));
        }
    }

    /** 兼容旧调用（无 before/after 内容） */
    public void recordHallucinationRetry(String query, int retryNum, double score, String issues) {
        recordHallucinationRetry(query, retryNum, score, issues, null, null);
    }

    public void recordPipelineDone(String query, String taskType, int fragmentCount, double confidence, boolean success) {
        confidenceSum.add(confidence);
        confidenceCount.incrementAndGet();
        addEvent("PIPELINE_DONE", query, fragmentCount, confidence,
                "taskType=" + taskType + ", success=" + success);
    }

    private void addEvent(String type, String query, int fragmentCount, double confidence, String detail) {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("type", type);
        evt.put("time", LocalDateTime.now().toString());
        evt.put("query", truncate(query, 100));
        evt.put("fragmentCount", fragmentCount);
        evt.put("confidence", confidence);
        evt.put("detail", detail);
        events.addFirst(evt);
        while (events.size() > MAX_EVENTS) events.pollLast();
    }

    // ==================== 查询接口 ====================

    public Map<String, Object> getMonitorData() {
        Map<String, Object> result = new LinkedHashMap<>();

        long total = totalQueries.get();
        long passed = gate1Passed.get();
        long rejected = gate1Rejected.get();
        long retries = hallucinationRetries.get();
        long fcFail = factCheckFails.get();

        result.put("overview", Map.of(
                "totalQueries", total,
                "gate1Passed", passed,
                "gate1Rejected", rejected,
                "gate1HitRate", total > 0 ? Math.round(1000.0 * passed / total) / 10.0 : 0,
                "hallucinationRetries", retries,
                "factCheckFails", fcFail,
                "avgConfidence", confidenceCount.get() > 0
                        ? Math.round(confidenceSum.sum() / confidenceCount.get() * 100.0) / 100.0
                        : 0
        ));

        List<Map<String, Object>> gateStats = new ArrayList<>();
        gateStats.add(gateStat("第一道门·检索门控", (int) passed, (int) rejected,
                passed > 0 ? "#52c41a" : "#ff4d4f"));
        gateStats.add(gateStat("第三道门·事实校验", (int) (total - fcFail), (int) fcFail,
                (total - fcFail) > fcFail ? "#52c41a" : "#fa8c16"));
        gateStats.add(gateStat("幻觉重试率", (int) (total - retries), (int) retries,
                retries == 0 ? "#52c41a" : "#ff4d4f"));
        result.put("gateStats", gateStats);

        // 知识缺口 Top 10
        List<Map<String, Object>> gaps = getKnowledgeGaps();
        result.put("knowledgeGaps", gaps);

        // 缺口自动建议 — 有缺口就自动生成（5分钟缓存）
        result.put("gapSuggestions", getOrGenerateGapSuggestions(gaps));

        // 幻觉事故列表（最近20条，含修正前后 diff）
        List<Map<String, Object>> incidents = new ArrayList<>(hallucinationIncidents);
        result.put("hallucinationIncidents", incidents.subList(0, Math.min(20, incidents.size())));

        // 最近事件流
        List<Map<String, Object>> recentEvents = new ArrayList<>(events);
        result.put("recentEvents", recentEvents.subList(0, Math.min(50, recentEvents.size())));

        return result;
    }

    private List<Map<String, Object>> getKnowledgeGaps() {
        Map<String, Long> topicCount = rejectedQueries.stream()
                .collect(Collectors.groupingBy(
                        r -> (String) r.getOrDefault("topic", "未分类"),
                        Collectors.counting()));

        return topicCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> gap = new LinkedHashMap<>();
                    gap.put("topic", e.getKey());
                    gap.put("count", e.getValue());
                    rejectedQueries.stream()
                            .filter(r -> e.getKey().equals(r.get("topic")))
                            .findFirst()
                            .ifPresent(r -> gap.put("sampleQuery", r.get("query")));
                    return gap;
                })
                .collect(Collectors.toList());
    }

    /**
     * 自动为知识缺口生成补充建议 — 无手动按钮，管理员进入监控页自动触发
     * LLM 调用有 5 分钟缓存，避免频繁消耗 token
     */
    private List<Map<String, Object>> getOrGenerateGapSuggestions(List<Map<String, Object>> gaps) {
        if (gaps.isEmpty()) {
            return Collections.emptyList();
        }

        // 缓存未过期直接返回
        long now = System.currentTimeMillis();
        if (cachedGapSuggestions != null && (now - lastSuggestionTime) < SUGGESTION_CACHE_MS) {
            return cachedGapSuggestions;
        }

        try {
            // 构建 prompt：让 LLM 为每个缺口生成一段建议内容
            StringBuilder gapList = new StringBuilder();
            int topN = Math.min(gaps.size(), 5);
            for (int i = 0; i < topN; i++) {
                Map<String, Object> g = gaps.get(i);
                gapList.append(i + 1).append(". ").append(g.get("topic"))
                       .append("（被拦截").append(g.get("count")).append("次，示例查询：")
                       .append(g.getOrDefault("sampleQuery", "无")).append("）\n");
            }

            String prompt = """
                你是C语言教学知识库管理员。以下是当前知识库缺失的知识点（学生提问被RAG检索门控拦截）：
                
                %s
                
                【任务】为上述每个知识点自动生成一段「知识库补充建议」，格式为纯JSON数组：
                [{"topic":"知识点名","urgency":"高/中/低","suggestion":"200字左右的知识点核心内容建议，可直接作为知识库文档的基础"},
                 ...]
                
                要求：
                - 严格只输出JSON数组，不要```json标记
                - 每个建议200字左右，覆盖核心概念、易错点
                - urgency 根据拦截次数判断：≥5次为「高」，3-4次为「中」，<3次为「低」
                """.formatted(gapList.toString());

            String aiReply = sparkClient.chat(prompt, "知识缺口自动建议", 0.3f, 2048);
            if (aiReply == null || aiReply.isBlank()) {
                log.warn("缺口建议LLM返回为空");
                return Collections.emptyList();
            }

            // 解析 JSON
            var suggestions = com.happymouse.cryd.common.JsonParser.parseArray(aiReply);
            if (suggestions == null || suggestions.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < suggestions.size(); i++) {
                var obj = suggestions.getJSONObject(i);
                Map<String, Object> sug = new LinkedHashMap<>();
                sug.put("topic", obj.getString("topic") != null ? obj.getString("topic") : "");
                sug.put("urgency", obj.getString("urgency") != null ? obj.getString("urgency") : "中");
                sug.put("suggestion", obj.getString("suggestion") != null ? obj.getString("suggestion") : "");
                result.add(sug);
            }

            // 缓存
            cachedGapSuggestions = result;
            lastSuggestionTime = now;
            log.info("自动生成{}条知识缺口建议，缓存5分钟", result.size());
            return result;

        } catch (Exception e) {
            log.warn("缺口建议自动生成失败: {}", e.getMessage());
            return cachedGapSuggestions != null ? cachedGapSuggestions : Collections.emptyList();
        }
    }

    private Map<String, Object> gateStat(String name, int passed, int rejected, String color) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("passed", passed);
        s.put("rejected", rejected);
        s.put("color", color);
        return s;
    }

    private String guessTopic(String query) {
        if (query == null) return "未知";
        String lower = query.toLowerCase();
        if (lower.contains("指针") || lower.contains("pointer")) return "指针";
        if (lower.contains("数组") || lower.contains("array")) return "数组";
        if (lower.contains("函数") || lower.contains("function")) return "函数";
        if (lower.contains("结构体") || lower.contains("struct")) return "结构体";
        if (lower.contains("链表") || lower.contains("linked")) return "链表";
        if (lower.contains("内存") || lower.contains("memory") || lower.contains("malloc")) return "内存管理";
        if (lower.contains("文件") || lower.contains("file") || lower.contains("fopen")) return "文件操作";
        if (lower.contains("循环") || lower.contains("loop") || lower.contains("for")) return "循环";
        if (lower.contains("递归") || lower.contains("recursion")) return "递归";
        if (lower.contains("排序") || lower.contains("sort") || lower.contains("算法")) return "算法";
        if (lower.contains("字符串") || lower.contains("string")) return "字符串";
        if (lower.contains("位运算") || lower.contains("bit")) return "位运算";
        if (lower.contains("编译") || lower.contains("gcc") || lower.contains("预处理器")) return "编译/预处理器";
        return "其他C语言";
    }

    public void reset() {
        events.clear();
        rejectedQueries.clear();
        hallucinationIncidents.clear();
        cachedGapSuggestions = null;
        lastSuggestionTime = 0;
        totalQueries.set(0);
        gate1Passed.set(0);
        gate1Rejected.set(0);
        hallucinationRetries.set(0);
        factCheckFails.set(0);
        confidenceSum.reset();
        confidenceCount.set(0);
        log.info("RagEventStore已重置");
    }

    // ==================== 持久化 ====================

    private void saveToDisk() {
        try {
            File dir = new File("data");
            if (!dir.exists()) dir.mkdirs();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("events", new ArrayList<>(events));
            data.put("rejectedQueries", new ArrayList<>(rejectedQueries));
            data.put("hallucinationIncidents", new ArrayList<>(hallucinationIncidents));
            data.put("totalQueries", totalQueries.get());
            data.put("gate1Passed", gate1Passed.get());
            data.put("gate1Rejected", gate1Rejected.get());
            data.put("hallucinationRetries", hallucinationRetries.get());
            data.put("factCheckFails", factCheckFails.get());
            data.put("confidenceSum", confidenceSum.sum());
            data.put("confidenceCount", confidenceCount.get());
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(DATA_FILE), data);
        } catch (Exception e) {
            log.warn("RagEventStore持久化失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        try {
            Map<String, Object> data = mapper.readValue(file, new TypeReference<Map<String, Object>>() {});
            if (data.get("events") instanceof List) {
                ((List<Map<String, Object>>) data.get("events")).forEach(events::add);
                while (events.size() > MAX_EVENTS) events.pollLast();
            }
            if (data.get("rejectedQueries") instanceof List) {
                ((List<Map<String, Object>>) data.get("rejectedQueries")).forEach(rejectedQueries::add);
                while (rejectedQueries.size() > MAX_REJECTED) rejectedQueries.pollLast();
            }
            if (data.get("hallucinationIncidents") instanceof List) {
                ((List<Map<String, Object>>) data.get("hallucinationIncidents")).forEach(hallucinationIncidents::add);
                while (hallucinationIncidents.size() > MAX_INCIDENTS) hallucinationIncidents.pollLast();
            }
            totalQueries.set(((Number) data.getOrDefault("totalQueries", 0)).longValue());
            gate1Passed.set(((Number) data.getOrDefault("gate1Passed", 0)).longValue());
            gate1Rejected.set(((Number) data.getOrDefault("gate1Rejected", 0)).longValue());
            hallucinationRetries.set(((Number) data.getOrDefault("hallucinationRetries", 0)).longValue());
            factCheckFails.set(((Number) data.getOrDefault("factCheckFails", 0)).longValue());
            confidenceSum.add(((Number) data.getOrDefault("confidenceSum", 0.0)).doubleValue());
            confidenceCount.set(((Number) data.getOrDefault("confidenceCount", 0)).longValue());
        } catch (Exception e) {
            log.warn("RagEventStore加载失败，将使用空数据: {}", e.getMessage());
        }
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
