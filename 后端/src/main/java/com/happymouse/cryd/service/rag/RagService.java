package com.happymouse.cryd.service.rag;

import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG检索增强生成服务 — 防幻觉四道门架构
 *
 * 第一道门：检索门控 — 无相关知识则拒绝生成
 * 第二道门：Prompt约束 — 只允许基于参考资料回答
 * 第三道门：事实校验器 — 概念/公式/答案匹配检查
 * 第四道门：格式锁 — 固定输出格式，模型只能填内容
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final SparkClient sparkClient;
    private final HallucinationGuard guard;
    private final RagEventStore eventStore;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    @Value("${rag.similarity-threshold:0.65}")
    private double similarityThreshold;

    @Value("${rag.hallucination.enabled:true}")
    private boolean guardEnabled;

    @Value("${rag.hallucination.fact-check-enabled:true}")
    private boolean factCheckEnabled;

    @Value("${rag.hallucination.max-retries:2}")
    private int maxRetries;

    public RagService(VectorStore vectorStore, EmbeddingService embeddingService,
                      SparkClient sparkClient, HallucinationGuard guard,
                      RagEventStore eventStore) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.sparkClient = sparkClient;
        this.guard = guard;
        this.eventStore = eventStore;
    }

    // ==================== 第一道门：检索门控 ====================

    /**
     * 检索相关知识点片段
     * 如果检索不到相关内容，返回空列表，上层必须拒绝生成
     */
    public List<KnowledgeFragment> retrieveRelevant(String query, int topK) {
        float[] queryVec = embeddingService.embed(query);
        List<VectorStore.SearchResult> results = vectorStore.search(queryVec, topK, similarityThreshold);

        return results.stream()
                .map(r -> new KnowledgeFragment(
                    r.getMetadata().getOrDefault("content", ""),
                    r.getMetadata().getOrDefault("tags", ""),
                    r.getMetadata().getOrDefault("source", ""),
                    r.getSimilarity()))
                .collect(Collectors.toList());
    }

    public List<KnowledgeFragment> retrieveRelevant(String query) {
        return retrieveRelevant(query, defaultTopK);
    }

    /**
     * 检索门控：有结果才放行，无结果直接返回拒绝消息
     */
    public RetrievalGateResult gateByRetrieval(String query) {
        List<KnowledgeFragment> fragments = retrieveRelevant(query);

        if (fragments.isEmpty()) {
            log.warn("【第一道门·检索门控】拦截: query='{}'，无相关知识", truncate(query, 50));
            return new RetrievalGateResult(false,
                "当前知识点未收录，无法生成可靠内容。请先上传相关教材资料。", fragments);
        }

        log.info("【第一道门·检索门控】放行: query='{}'，命中{}条", truncate(query, 50), fragments.size());
        return new RetrievalGateResult(true, null, fragments);
    }

    // ==================== 第二道门：Prompt约束 ====================

    /**
     * 构建防幻觉约束Prompt — 模型只能基于参考资料回答，不许编造
     */
    public String buildConstrainedPrompt(String taskType, String userQuery, List<KnowledgeFragment> fragments) {
        StringBuilder prompt = new StringBuilder();

        // 系统角色 + 硬约束
        prompt.append("你是一个C语言教学助手。你必须严格遵守以下规则：\n");
        prompt.append("1. 你只能根据下面提供的【参考资料】回答，不许编造任何内容\n");
        prompt.append("2. 如果参考资料中没有相关内容，你必须说「暂无相关知识点」\n");
        prompt.append("3. 不许超纲，不许造公式，不许编例题答案\n");
        prompt.append("4. 必须严格按输出格式模板输出\n");
        prompt.append("5. 引用的概念、公式必须和参考资料完全一致\n");
        prompt.append("6. 所有内容必须标注【来源】\n\n");

        // 注入参考资料
        prompt.append("【参考资料】\n");
        for (int i = 0; i < fragments.size(); i++) {
            KnowledgeFragment f = fragments.get(i);
            prompt.append("--- 参考资料").append(i + 1);
            if (f.source != null && !f.source.isEmpty()) {
                prompt.append("（来源：").append(f.source).append("）");
            }
            prompt.append(" ---\n");
            prompt.append(f.content).append("\n\n");
        }

        // 任务描述
        prompt.append("【任务类型】").append(taskType).append("\n");
        prompt.append("【用户需求】").append(userQuery).append("\n\n");

        // 格式模板（第四道门融合）
        prompt.append(guard.getFormatTemplate(taskType));

        return prompt.toString();
    }

    // ==================== 第三道门：事实校验器 ====================

    /**
     * 事实校验 — 检查生成内容是否与知识库一致
     * 返回校验结果，不通过则触发重新生成
     */
    public HallucinationGuard.FactCheckResult factCheck(String generatedContent, List<KnowledgeFragment> references) {
        if (!factCheckEnabled) {
            return new HallucinationGuard.FactCheckResult(true, "事实校验已关闭", 1.0);
        }
        return guard.factCheck(generatedContent, references);
    }

    // ==================== 第四道门：格式锁 ====================

    /**
     * 格式校验 — 检查输出是否符合固定格式
     */
    public boolean validateFormat(String generatedContent, String taskType) {
        return guard.validateFormat(generatedContent, taskType);
    }

    // ==================== 完整RAG流水线 ====================

    /**
     * 防幻觉RAG完整流水线
     * 四道门全部串起：检索门控 → Prompt约束 → 生成 → 事实校验 → 格式锁
     *
     * @param taskType  任务类型：summary/exercise/path/weakness/courseware
     * @param query     用户需求
     * @return 生成结果（如果被拦截则返回拦截原因）
     */
    public RagPipelineResult generateWithGuard(String taskType, String query) {
        // 第一道门：检索门控
        RetrievalGateResult gate = gateByRetrieval(query);
        if (!gate.passed) {
            eventStore.recordGate1Reject(query);
            return new RagPipelineResult(false, gate.rejectReason, null, 0);
        }
        eventStore.recordGate1Pass(query, gate.fragments.size());

        // 第二道门：构建约束Prompt（基础prompt，重试时基于此重建，避免prompt无限膨胀）
        String baseConstrainedPrompt = buildConstrainedPrompt(taskType, query, gate.fragments);

        // 生成（最多重试maxRetries次）
        String generated = null;
        String failedContent = null; // 记录修正前内容
        HallucinationGuard.FactCheckResult factResult = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            // 修复L6: 每次重试用 基础prompt + 仅本次反馈，避免多份反馈累加导致prompt膨胀和模型混淆
            String constrainedPrompt = baseConstrainedPrompt;
            if (attempt > 0 && factResult != null) {
                constrainedPrompt = baseConstrainedPrompt +
                    "\n\n【校验反馈】上次生成的内容存在以下问题：" +
                    factResult.reason + "，请修正后重新生成。";
            }

            // 调用LLM生成
            generated = sparkClient.chat(constrainedPrompt, query, 0.3f, 2048);

            if (generated == null || generated.isEmpty()) {
                log.warn("LLM生成空内容，尝试 {}/{}", attempt + 1, maxRetries);
                continue;
            }

            // 第三道门：事实校验
            factResult = factCheck(generated, gate.fragments);

            if (factResult.passed) {
                eventStore.recordPipelineDone(query, taskType, gate.fragments.size(), factResult.confidence, true);
                // 如果之前有过重试，补充 after 内容
                if (failedContent != null) {
                    eventStore.updateLastIncidentAfterContent(generated);
                }
                break; // 校验通过
            }

            log.warn("【第三道门·事实校验】第{}次未通过: {}", attempt + 1, factResult.reason);
            eventStore.recordFactCheckFail(query, gate.fragments.size(), factResult.confidence, factResult.reason);
            if (attempt < maxRetries) {
                failedContent = generated; // 保存修正前内容
                // 先记录事故（含 before），after 暂时为 null
                eventStore.recordHallucinationRetry(query, attempt + 1, factResult.confidence,
                        factResult.reason, failedContent, null);
            }
        }

        if (generated == null || generated.isEmpty()) {
            return new RagPipelineResult(false, "AI生成失败，请稍后重试", null, 0);
        }

        // 事实校验最终仍未通过，返回失败（不让幻觉内容通过第三道门）
        if (factResult == null || !factResult.passed) {
            eventStore.recordPipelineDone(query, taskType, gate.fragments.size(),
                factResult != null ? factResult.confidence : 0, false);
            return new RagPipelineResult(false, "事实校验未通过，生成内容可能不可靠", generated,
                factResult != null ? factResult.confidence : 0);
        }

        // 第四道门：格式锁
        boolean formatOk = validateFormat(generated, taskType);
        if (!formatOk) {
            log.warn("【第四道门·格式锁】格式不符合要求，但内容已生成，降级放行");
        }

        double confidence = factResult != null ? factResult.confidence : 0.5;
        log.info("RAG流水线完成: taskType={}, confidence={}, formatOk={}", taskType, String.format("%.2f", confidence), formatOk);

        return new RagPipelineResult(true, null, generated, confidence);
    }

    /**
     * 简化版：检索并构建增强Prompt（兼容旧接口）
     */
    public String retrieveAndAugment(String basePrompt, String query) {
        List<KnowledgeFragment> fragments = retrieveRelevant(query);
        if (fragments.isEmpty()) return basePrompt;
        return buildConstrainedPrompt("chat", query, fragments);
    }

    // ==================== 内部类 ====================

    public static class KnowledgeFragment {
        public final String content;
        public final String tags;
        public final String source;
        public final double similarity;

        public KnowledgeFragment(String content, String tags, String source, double similarity) {
            this.content = content;
            this.tags = tags;
            this.source = source;
            this.similarity = similarity;
        }
    }

    public static class RetrievalGateResult {
        public final boolean passed;
        public final String rejectReason;
        public final List<KnowledgeFragment> fragments;

        public RetrievalGateResult(boolean passed, String rejectReason, List<KnowledgeFragment> fragments) {
            this.passed = passed;
            this.rejectReason = rejectReason;
            this.fragments = fragments != null ? fragments : Collections.emptyList();
        }
    }

    public static class RagPipelineResult {
        public final boolean success;
        public final String errorMessage;
        public final String content;
        public final double confidence;

        public RagPipelineResult(boolean success, String errorMessage, String content, double confidence) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.content = content;
            this.confidence = confidence;
        }
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
