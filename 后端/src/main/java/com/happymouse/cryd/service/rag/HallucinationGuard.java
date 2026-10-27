package com.happymouse.cryd.service.rag;

import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 防幻觉守卫 — 第三道门（事实校验）+ 第四道门（格式锁）
 *
 * 事实校验：生成内容与知识库比对，概念/公式/答案不一致则打回
 * 格式锁：输出必须符合任务类型的固定模板
 */
@Service
public class HallucinationGuard {

    private static final Logger log = LoggerFactory.getLogger(HallucinationGuard.class);

    private final SparkClient sparkClient;

    // 格式模板关键词（格式锁用）
    private static final Map<String, List<String>> FORMAT_TEMPLATES = new HashMap<>();

    static {
        FORMAT_TEMPLATES.put("summary", Arrays.asList("知识点", "来源", "内容", "易错点"));
        FORMAT_TEMPLATES.put("exercise", Arrays.asList("题目", "选项", "答案", "解析", "难度", "知识点"));
        FORMAT_TEMPLATES.put("path", Arrays.asList("节点", "章节", "知识点", "建议"));
        FORMAT_TEMPLATES.put("weakness", Arrays.asList("薄弱点", "错题记录", "知识点", "建议"));
        FORMAT_TEMPLATES.put("courseware", Arrays.asList("章节", "关键词", "内容", "例题"));
    }

    public HallucinationGuard(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    // ==================== 第三道门：事实校验器 ====================

    /**
     * 事实校验 — 检查生成内容是否与知识库一致
     * 双重机制：关键词精确匹配 + LLM辅助校验
     */
    public FactCheckResult factCheck(String generatedContent, List<RagService.KnowledgeFragment> references) {
        if (references == null || references.isEmpty()) {
            return new FactCheckResult(false, "无参考资料可校验", 0.0);
        }

        double score = 0.0;
        List<String> issues = new ArrayList<>();

        // 1. 关键词覆盖度检查：生成内容是否覆盖了参考资料的核心概念
        double coverageScore = checkKeywordCoverage(generatedContent, references);
        score += coverageScore * 0.4; // 权重40%
        if (coverageScore < 0.3) {
            issues.add(String.format("关键词覆盖率低(%.0f%%)，生成内容可能偏离主题", coverageScore * 100));
        }

        // 2. 数值一致性检查：公式/数字是否和参考资料一致
        double numericScore = checkNumericConsistency(generatedContent, references);
        score += numericScore * 0.3; // 权重30%
        if (numericScore < 0.5) {
            issues.add("存在与参考资料不一致的数值/公式");
        }

        // 3. LLM辅助语义校验
        double llmScore = checkWithLLM(generatedContent, references);
        score += llmScore * 0.3; // 权重30%
        if (llmScore < 0.5) {
            issues.add("LLM语义校验发现不一致");
        }

        boolean passed = score >= 0.6;
        String reason = issues.isEmpty() ? "校验通过" : String.join("；", issues);

        log.info("【事实校验】score={}, passed={}, issues={}", String.format("%.2f", score), passed, issues.size());
        return new FactCheckResult(passed, reason, score);
    }

    // C语言常见同义词映射（Map.of 最多支持10个键值对，超过需用 ofEntries）
    private static final Map<String, Set<String>> SYNONYM_MAP = Map.ofEntries(
        Map.entry("指针", Set.of("pointer", "地址", "address", "内存地址")),
        Map.entry("数组", Set.of("array", "列表", "序列")),
        Map.entry("函数", Set.of("function", "方法", "子程序", "过程")),
        Map.entry("结构体", Set.of("struct", "结构", "自定义类型")),
        Map.entry("变量", Set.of("variable", "标识符", "名称")),
        Map.entry("循环", Set.of("loop", "迭代", "遍历")),
        Map.entry("条件", Set.of("condition", "判断", "分支")),
        Map.entry("内存", Set.of("memory", "存储", "空间")),
        Map.entry("字符串", Set.of("string", "文本", "字符数组")),
        Map.entry("文件", Set.of("file", "磁盘", "IO")),
        Map.entry("编译", Set.of("compile", "编译器", "构建")),
        Map.entry("语法", Set.of("syntax", "规则", "格式"))
    );

    /**
     * 关键词覆盖度：生成内容包含了参考资料中多少核心关键词
     * 支持同义词扩展匹配
     */
    private double checkKeywordCoverage(String generated, List<RagService.KnowledgeFragment> references) {
        Set<String> generatedTokens = tokenize(generated);
        // 扩展生成内容的token，包含同义词
        Set<String> expandedGeneratedTokens = expandWithSynonyms(generatedTokens);

        Set<String> refKeywords = new HashSet<>();

        for (RagService.KnowledgeFragment ref : references) {
            refKeywords.addAll(tokenize(ref.content));
            // 标签也是关键词
            if (ref.tags != null && !ref.tags.isEmpty()) {
                for (String tag : ref.tags.split("[,，\\s]+")) {
                    String t = tag.trim();
                    if (t.length() >= 2) refKeywords.add(t);
                }
            }
        }

        if (refKeywords.isEmpty()) return 1.0;

        int hit = 0;
        for (String kw : refKeywords) {
            // 直接匹配
            if (expandedGeneratedTokens.contains(kw)) {
                hit++;
            } else {
                // 同义词匹配
                Set<String> synonyms = SYNONYM_MAP.get(kw);
                if (synonyms != null) {
                    for (String syn : synonyms) {
                        if (expandedGeneratedTokens.contains(syn.toLowerCase())) {
                            hit++;
                            break;
                        }
                    }
                }
            }
        }

        return (double) hit / refKeywords.size();
    }

    /**
     * 扩展token集合，包含同义词
     */
    private Set<String> expandWithSynonyms(Set<String> tokens) {
        Set<String> expanded = new HashSet<>(tokens);
        for (String token : tokens) {
            for (Map.Entry<String, Set<String>> entry : SYNONYM_MAP.entrySet()) {
                if (token.contains(entry.getKey()) || entry.getValue().stream().anyMatch(s -> token.contains(s))) {
                    expanded.add(entry.getKey());
                    expanded.addAll(entry.getValue());
                }
            }
        }
        return expanded;
    }

    /**
     * 数值一致性：检查生成内容中的数字是否在参考资料中出现
     */
    private double checkNumericConsistency(String generated, List<RagService.KnowledgeFragment> references) {
        // 提取生成内容中的所有数字
        Set<String> genNumbers = extractNumbers(generated);
        if (genNumbers.isEmpty()) return 1.0; // 无数字则跳过

        // 提取参考资料中的所有数字
        Set<String> refNumbers = new HashSet<>();
        for (RagService.KnowledgeFragment ref : references) {
            refNumbers.addAll(extractNumbers(ref.content));
        }

        int consistent = 0;
        int total = genNumbers.size();
        for (String num : genNumbers) {
            if (refNumbers.contains(num)) {
                consistent++;
            }
        }

        return total > 0 ? (double) consistent / total : 1.0;
    }

    /**
     * LLM辅助语义校验：让模型判断生成内容是否与参考资料一致
     */
    private double checkWithLLM(String generated, List<RagService.KnowledgeFragment> references) {
        StringBuilder refText = new StringBuilder();
        for (int i = 0; i < Math.min(references.size(), 3); i++) {
            refText.append(references.get(i).content).append("\n");
        }

        // 截断避免过长
        String genTruncated = generated.length() > 800 ? generated.substring(0, 800) : generated;
        String refTruncated = refText.length() > 800 ? refText.substring(0, 800) : refText.toString();

        String prompt = "你是事实校验专家。请判断以下【生成内容】是否与【参考资料】一致。\n" +
            "只输出一个0到1的数字：1=完全一致，0=完全矛盾，0.5=部分一致。\n" +
            "只输出数字，不要输出其他内容。\n\n" +
            "【参考资料】\n" + refTruncated + "\n" +
            "【生成内容】\n" + genTruncated;

        try {
            String result = sparkClient.chat(prompt, "判断一致性", 0.1f, 10);
            if (result != null) {
                String cleaned = result.trim().replaceAll("[^0-9.]", "");
                if (!cleaned.isEmpty()) {
                    return Math.max(0, Math.min(1, Double.parseDouble(cleaned)));
                }
            }
        } catch (Exception e) {
            log.warn("LLM事实校验调用失败: {}", e.getMessage());
        }
        // 修复L4: LLM校验失败时返回低分而非0.7，避免幻觉内容在LLM不可用时通过校验
        // 防幻觉机制应在LLM不可用时更保守，而非放行
        return 0.3;
    }

    // ==================== 第四道门：格式锁 ====================

    /**
     * 格式校验：检查输出是否包含任务类型要求的所有模板字段
     */
    public boolean validateFormat(String generatedContent, String taskType) {
        List<String> requiredFields = FORMAT_TEMPLATES.getOrDefault(taskType, Collections.emptyList());
        if (requiredFields.isEmpty()) return true; // 未知任务类型不校验

        int present = 0;
        for (String field : requiredFields) {
            if (generatedContent.contains(field)) present++;
        }

        // 至少包含一半以上必填字段才算格式合格
        double ratio = (double) present / requiredFields.size();
        boolean passed = ratio >= 0.5;

        if (!passed) {
            log.warn("【格式锁】未通过: taskType={}, 命中{}/{}字段", taskType, present, requiredFields.size());
        }
        return passed;
    }

    /**
     * 获取任务类型的格式模板（注入到Prompt第二道门中）
     */
    public String getFormatTemplate(String taskType) {
        List<String> fields = FORMAT_TEMPLATES.get(taskType);
        if (fields == null || fields.isEmpty()) {
            return "【输出要求】请结构化输出，条理清晰。\n";
        }

        StringBuilder template = new StringBuilder();
        template.append("【输出格式】你必须按以下格式输出，只填写内容，不要增减字段：\n");

        switch (taskType) {
            case "summary":
                template.append("## 知识点：{知识点名称}\n");
                template.append("**来源**：{参考资料来源}\n");
                template.append("**内容**：{知识点核心解释，必须与参考资料一致}\n");
                template.append("**易错点**：{常见错误及纠正}\n");
                break;
            case "exercise":
                template.append("## 题目：{题干}\n");
                template.append("**选项**：A.{} B.{} C.{} D.{}\n");
                template.append("**答案**：{正确选项}\n");
                template.append("**解析**：{答案解析}\n");
                template.append("**难度**：{easy/medium/hard}\n");
                template.append("**知识点**：{关联知识点ID}\n");
                break;
            case "path":
                template.append("## 学习节点：{节点名称}\n");
                template.append("**章节**：{所属章节}\n");
                template.append("**知识点**：{核心知识点}\n");
                template.append("**建议**：{学习建议}\n");
                break;
            case "weakness":
                template.append("## 薄弱点：{薄弱知识点}\n");
                template.append("**错题记录**：{相关错题}\n");
                template.append("**知识点**：{需要巩固的知识点}\n");
                template.append("**建议**：{针对性学习建议}\n");
                break;
            case "courseware":
                template.append("## 章节：{章节名}\n");
                template.append("**关键词**：{本章关键词}\n");
                template.append("**内容**：{章节核心内容}\n");
                template.append("**例题**：{典型例题及解答}\n");
                break;
            default:
                template.append("请结构化输出，条理清晰。\n");
        }

        return template.toString();
    }

    // ==================== 工具方法 ====================

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) return tokens;
        // 提取中文词（2-4字）和英文词
        Matcher m = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}|[a-zA-Z]{2,}").matcher(text);
        while (m.find()) tokens.add(m.group().toLowerCase());
        return tokens;
    }

    private Set<String> extractNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        if (text == null) return numbers;
        // 修复L5: 先剔除代码块（```c ... ``` 等），避免代码中的下标/循环数/返回值被误判为数值不一致
        // C 语言教学内容的代码块里有大量数字（如 for(i=0;i<10;i++)），它们不是知识点数值
        String stripped = text.replaceAll("```[\\w]*\\s*[\\s\\S]*?```", " ");
        Matcher m = Pattern.compile("\\b\\d+\\.?\\d*\\b").matcher(stripped);
        while (m.find()) numbers.add(m.group());
        return numbers;
    }

    // ==================== 结果类 ====================

    public static class FactCheckResult {
        public final boolean passed;
        public final String reason;
        public final double confidence;

        public FactCheckResult(boolean passed, String reason, double confidence) {
            this.passed = passed;
            this.reason = reason;
            this.confidence = confidence;
        }
    }
}
