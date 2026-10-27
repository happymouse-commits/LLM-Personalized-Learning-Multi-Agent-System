package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 审核Agent — 校验各Agent输出质量
 * 质量不达标则返回具体反馈供重试
 */
public class AgentReviewer extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(AgentReviewer.class);

    private static final String REVIEW_PROMPT =
        """
        你是一个C语言教学内容的严格质量审核专家。请评估以下AI生成的学习内容质量。

        ════════════════════════════════════════════════════════════════
        【评估维度及权重】
        ════════════════════════════════════════════════════════════════
        1. C语言知识点准确性（权重30%）：术语、概念、语法是否正确
        2. 代码质量（权重30%）：是否可编译运行、有无明显错误、是否有#include和main
        3. 难度适配（权重20%）：是否适合目标学生水平（不超纲、不遗漏重点）
        4. 内容完整性（权重20%）：是否覆盖用户需求的核心内容

        ════════════════════════════════════════════════════════════════
        【评分标准】
        ════════════════════════════════════════════════════════════════
        - score >= 0.8: 通过（passed=true），内容质量优秀
        - score 0.6-0.8: 需修改（passed=false），给出具体修改建议
        - score < 0.6: 不通过（passed=false），需要重写

        ════════════════════════════════════════════════════════════════
        【输出格式】严格输出以下JSON，不要其他文字：
        {
          "passed": true/false,
          "score": 0.85,
          "dimension_scores": {
            "accuracy": 0.9,
            "code_quality": 0.8,
            "difficulty": 0.85,
            "completeness": 0.8
          },
          "issues": ["问题1", "问题2"],
          "suggestion": "改进建议"
        }
        """;

    // 基础代码检查模式
    private static final Pattern C_CODE_BLOCK = Pattern.compile("```c\\s*\\n([\\s\\S]*?)```");
    private static final Pattern HAS_MAIN = Pattern.compile("int\\s+main\\s*\\(");
    private static final Pattern HAS_INCLUDE = Pattern.compile("#include\\s*<");

    /**
     * 修复R3: 审核通过阈值。原硬编码 0.6，与 application.yml 中
     *   agent.pipeline.review-threshold 配置不联动。
     *   由 AgentOrchestrator 构造时通过 setter 注入；默认 0.6 保持向后兼容。
     */
    private double reviewThreshold = 0.6;

    public AgentReviewer(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    /** 由外部注入配置阈值（来自 agent.pipeline.review-threshold） */
    public void setReviewThreshold(double threshold) {
        if (threshold > 0 && threshold <= 1.0) {
            this.reviewThreshold = threshold;
        }
    }

    public double getReviewThreshold() { return reviewThreshold; }

    @Override
    public String getName() { return "审核Agent"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(AgentCapability.of("review:quality", "内容质量审核、代码检查、难度评估", 0.90));
    }

    @Override
    protected String getSystemPrompt() { return REVIEW_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        // 审核所有已完成Agent的输出
        double lowestScore = 1.0;
        boolean allPassed = true;

        for (AgentContext.AgentResult result : context.getAllResults().values()) {
            // 修复R2: 跳过 failed result，避免对 null output 调 reviewOutput 污染评分
            //   原逻辑对失败 result（output=null）会返回 score=0，使 lowestScore=0，
            //   即使其他 result 全通过也会被标记为审核不通过，且会触发不必要的重试
            if (!result.isSuccess()) {
                log.warn("[审核] 跳过失败的 {} 任务（不参与评分）", result.getAgentName());
                continue;
            }
            ReviewResult review = reviewOutput(result.getOutput(), context.getStudentId());
            if (!review.passed) {
                allPassed = false;
                log.warn("[审核] {} 不通过: score={}, issues={}", result.getAgentName(), review.score, review.issues);
            }
            if (review.score < lowestScore) {
                lowestScore = review.score;
            }
            context.setAttribute("review_" + result.getAgentName(), review);
        }

        context.setAttribute("reviewPassed", allPassed);
        context.setAttribute("reviewScore", lowestScore);

        return allPassed ? "审核通过" : "审核不通过";
    }

    /**
     * 修复R1: 二次审核收敛性问题
     * 只审核"重试过"的任务结果（即首次审核已记录 review_X 但本次 result 与上次不同的），
     * 避免对首次已通过的结果重复审核（LLM 非确定性可能导致首次通过的二次反而失败）。
     *
     * 策略：对每个 result，若已存在 review_X 且 passed=true，则跳过；否则重新审核。
     * 然后基于"新审核结果 + 已通过的旧结果"重新计算 allPassed。
     */
    public void reviewRetriedOnly(AgentContext context) {
        double lowestScore = 1.0;
        boolean allPassed = true;

        for (AgentContext.AgentResult result : context.getAllResults().values()) {
            if (!result.isSuccess()) {
                // 失败结果直接拉低 allPassed
                allPassed = false;
                continue;
            }
            String attrKey = "review_" + result.getAgentName();
            Object existing = context.getAttribute(attrKey, Object.class);
            if (existing instanceof ReviewResult prevReview && prevReview.passed) {
                // 首次已通过，复用旧评分，不重复调 LLM
                if (prevReview.score < lowestScore) lowestScore = prevReview.score;
                continue;
            }
            // 未审核过或上次未通过 → 重新审核
            ReviewResult review = reviewOutput(result.getOutput(), context.getStudentId());
            if (!review.passed) {
                allPassed = false;
                log.warn("[二次审核] {} 仍不通过: score={}, issues={}",
                        result.getAgentName(), review.score, review.issues);
            } else {
                log.info("[二次审核] {} 重试后通过: score={}", result.getAgentName(), review.score);
            }
            if (review.score < lowestScore) lowestScore = review.score;
            context.setAttribute(attrKey, review);
        }

        context.setAttribute("reviewPassed", allPassed);
        context.setAttribute("reviewScore", lowestScore);
    }

    /**
     * 静态检查 + AI语义质量审核
     */
    public ReviewResult reviewOutput(String output, Long studentId) {
        ReviewResult review = new ReviewResult();
        review.passed = true;
        review.score = 1.0;

        if (output == null || output.isEmpty()) {
            review.passed = false;
            review.score = 0;
            review.issues.add("输出为空");
            review.suggestion = "请重新生成内容";
            return review;
        }

        // 静态检查：有代码块则检查质量
        // 修复L2: 静态检查改为记录严重问题数，不再与AI绝对分简单平均
        int criticalIssues = 0;
        var matcher = C_CODE_BLOCK.matcher(output);
        boolean hasCode = false;
        while (matcher.find()) {
            hasCode = true;
            String code = matcher.group(1);
            if (!HAS_INCLUDE.matcher(code).find() && !HAS_MAIN.matcher(code).find()) {
                review.issues.add("代码块缺少必要的#include或main函数");
                criticalIssues++;
            }
        }

        // 内容长度检查
        if (output.length() < 50) {
            review.issues.add("输出内容过短，可能不完整");
            criticalIssues++;
        }

        // AI语义质量审核 — 调用大模型
        double aiScore = -1; // -1 表示未获取到
        if (sparkClient != null) {
            try {
                // 修复L3: 长内容分段审核，避免只审前2000字符遗漏后半部分错误
                String contentToReview = output;
                if (output.length() > 2000) {
                    // 取前半 + 后半，确保代码尾部也被审核
                    contentToReview = output.substring(0, 1200)
                        + "\n...\n[中间部分省略]\n...\n"
                        + output.substring(output.length() - 800);
                }
                // 质量审核是高复杂度任务
                String aiReview = sparkClient.smartChat(
                    REVIEW_PROMPT,
                    "请审核以下内容：\n" + contentToReview,
                    SparkClient.TaskComplexity.HIGH, 300
                );
                if (aiReview != null && !aiReview.isEmpty()) {
                    review.aiFeedback = aiReview;
                    // 尝试提取评分
                    try {
                        String jsonStr = aiReview.replaceAll("```json|```", "").trim();
                        if (jsonStr.startsWith("{")) {
                            var json = com.alibaba.fastjson2.JSON.parseObject(jsonStr);
                            if (json.containsKey("score")) {
                                aiScore = json.getDoubleValue("score");
                            }
                            if (json.containsKey("passed")) {
                                review.passed = review.passed && json.getBooleanValue("passed");
                            }
                            if (json.containsKey("issues")) {
                                var aiIssues = json.getJSONArray("issues");
                                for (int i = 0; i < aiIssues.size(); i++) {
                                    review.issues.add(aiIssues.getString(i));
                                }
                            }
                            if (json.containsKey("suggestion")) {
                                review.suggestion = json.getString("suggestion");
                            }
                        }
                    } catch (Exception parseErr) {
                        log.debug("AI审核结果JSON解析失败，使用静态检查结果");
                    }
                }
            } catch (Exception e) {
                log.warn("AI审核调用失败，使用静态检查结果: {}", e.getMessage());
            }
        }

        // 修复L2: 综合评分逻辑改为加权而非简单平均
        // 静态检查：每个严重问题扣0.2，作为静态分
        double staticScore = Math.max(0, 1.0 - criticalIssues * 0.2);
        if (aiScore >= 0) {
            // 有AI评分时：静态分权重40%，AI分权重60%
            review.score = staticScore * 0.4 + aiScore * 0.6;
        } else {
            // 无AI评分时：仅用静态分，并降低通过门槛（因为未语义校验）
            review.score = staticScore * 0.7;
        }
        review.score = Math.max(0, review.score);
        // 静态检查有严重问题时直接不通过，无论AI评分如何
        if (criticalIssues >= 3) review.passed = false;
        // 修复R3: 用可配置阈值替代硬编码 0.6
        if (review.score < reviewThreshold) review.passed = false;

        return review;
    }

    /**
     * 审核结果
     */
    public static class ReviewResult {
        public boolean passed = true;
        public double score = 1.0;
        public List<String> issues = new java.util.ArrayList<>();
        public String suggestion = "";
        public String aiFeedback = "";

        @Override
        public String toString() {
            return String.format("Review{passed=%s, score=%.2f, issues=%s}", passed, score, issues);
        }
    }
}
