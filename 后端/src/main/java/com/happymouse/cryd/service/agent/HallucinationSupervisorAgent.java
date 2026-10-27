package com.happymouse.cryd.service.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 独立幻觉监督智能体 — 对 AI 生成的每份学习资源进行二次校验。
 *
 * 输出：幻觉评分(0-1)、风险等级(LOW/MEDIUM/HIGH/CRITICAL)、可疑片段说明。
 * 评分 < 0.5 的资源建议驳回重生成。
 *
 * 评估维度：
 *   1. C 语言语法/语义正确性
 *   2. 概念定义是否准确
 *   3. 代码示例是否能编译运行
 *   4. 逻辑一致性（前后不矛盾）
 */
@Service
public class HallucinationSupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(HallucinationSupervisorAgent.class);

    private final SparkClient sparkClient;

    public HallucinationSupervisorAgent(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    private static final String SUPERVISOR_PROMPT = """
        你是C语言教学内容的严格审查专家。请评估以下AI生成的学习资料是否存在"幻觉"（与C语言事实不符的内容）。

        【评估标准】
        1. C语言语法正确性：代码是否符合ANSI C标准
        2. 概念准确性：术语定义、原理解释是否准确
        3. 代码可运行性：示例代码在实际编译器中能否通过
        4. 逻辑一致性：内容前后是否自洽

        【输出格式】严格输出以下JSON，不要其他文字：
        {
          "score": 0.95,
          "risk": "LOW",
          "issues": ["可能的错误1", "可能的错误2"],
          "suggestion": "修正建议"
        }

        score: 0-1，1=完全无误，0=全是幻觉
        risk: LOW(≥0.85) / MEDIUM(0.7-0.85) / HIGH(0.5-0.7) / CRITICAL(<0.5)
        issues: 具体的可疑内容列表，无问题则为空数组
        suggestion: 修正建议，无问题则为"无"

        【待评估内容】
        类型：%s
        %s
        """;

    /**
     * 评估一份生成内容，返回幻觉监督结果
     */
    public HallucinationResult evaluate(String generatedContent, String resourceType) {
        if (generatedContent == null || generatedContent.isBlank()) {
            return new HallucinationResult(0.0, "CRITICAL", "内容为空", "无法评估");
        }

        // 修复H3: 原截断只取前1500字，长内容（含代码块）的后半部分不被审核
        //   对齐 AgentReviewer 的策略：取前1200 + 后800，确保代码尾部也被审核
        String truncated;
        if (generatedContent.length() > 2000) {
            truncated = generatedContent.substring(0, 1200)
                    + "\n...\n[中间部分省略]\n...\n"
                    + generatedContent.substring(generatedContent.length() - 800);
        } else {
            truncated = generatedContent;
        }

        String prompt = String.format(SUPERVISOR_PROMPT, resourceType, truncated);

        try {
            // 幻觉检测是高复杂度任务，使用Pro模型深度推理
            String aiReply = sparkClient.smartChat(prompt, "幻觉评估", SparkClient.TaskComplexity.HIGH, 4096);
            return parseResult(aiReply, truncated);
        } catch (Exception e) {
            log.warn("幻觉评估失败: {}", e.getMessage());
            // 修复L8: 异常默认 0.7 MEDIUM 与"幻觉率超30%重生成"约束冲突
            //   改为 0.5 HIGH，让异常情况下的资源触发重生成而非直接放行
            return new HallucinationResult(0.5, "HIGH", null, "评估服务异常，默认待核查");
        }
    }

    /**
     * 批量评估资源列表，为每份资源设置幻觉字段
     */
    public void evaluateResources(java.util.List<com.happymouse.cryd.model.entity.LearningResource> resources) {
        for (var r : resources) {
            // 跳过来自课程的视频/PPT（非AI生成）
            if ("course-video".equals(r.getGeneratedBy()) || "course-ppt".equals(r.getGeneratedBy())) {
                continue;
            }
            HallucinationResult result = evaluate(r.getContent(), r.getType());
            r.setHallucinationScore(result.score);
            r.setHallucinationRisk(result.risk);
            r.setHallucinationNote(result.note);
            log.debug("幻觉评估: title={}, score={}, risk={}", r.getTitle(), result.score, result.risk);
        }
    }

    private HallucinationResult parseResult(String aiReply, String content) {
        try {
            // 尝试提取 JSON
            String json = aiReply.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            JSONObject obj = JSON.parseObject(json);
            double score = obj.getDoubleValue("score");
            String risk = obj.getString("risk");
            String note = buildNote(obj, score, risk);
            return new HallucinationResult(
                    Math.max(0, Math.min(1, score)),
                    risk != null ? risk : riskFromScore(score),
                    note,
                    obj.getString("suggestion"));
        } catch (Exception e) {
            // 解析失败，用基础评估
            log.debug("幻觉评估JSON解析失败，使用降级评估: {}", aiReply.substring(0, Math.min(100, aiReply.length())));
            return fallbackEvaluate(content);
        }
    }

    /**
     * 降级评估：基于关键词检查代码质量
     * 修复H4/E5: 原逻辑 `contains("int main(") && !contains("int main(void)") && !contains("int main()")`
     *   实际只在 `int main(int argc...)` 时触发，反了。
     *   正确语义：void main(...) 是不规范的，应扣分；其他 main 签名（int main()/int main(void)/int main(int argc,...)）均合法。
     */
    private HallucinationResult fallbackEvaluate(String content) {
        int issues = 0;
        String lower = content.toLowerCase();

        // 常见C语言错误模式
        // 修复H4: void main(...) 不符合 ANSI C 标准（应为 int main），扣分
        if (lower.contains("void main(")) issues++;
        // scanf 没有取地址符（粗略检查，可能误报但保守）
        if (lower.contains("scanf") && !lower.contains("&")) issues++;
        // gets() 是危险函数（C11 已移除），扣双倍
        if (lower.contains("gets(")) issues += 2;
        // fflush(stdin) 是未定义行为
        if (lower.contains("fflush(stdin)")) issues++;

        // 没有代码内容但有C语言声明 → 可能是概念性内容，默认更可信
        boolean hasCode = lower.contains("#include") || lower.contains("```c");
        // 修复H5/L8: LLM 不可用时降级评估应更保守，避免幻觉内容通过
        //   原值 hasCode?0.7:0.85 永远落在 MEDIUM 区，与"幻觉率超30%重生成"约束冲突
        //   改为 hasCode?0.6:0.75，使含代码内容默认落在 HIGH 区，触发更严格的重生成阈值
        double baseScore = hasCode ? 0.6 : 0.75;
        double score = Math.max(0.3, baseScore - issues * 0.15);

        return new HallucinationResult(score, riskFromScore(score),
                issues > 0 ? "关键词检测到 " + issues + " 处可疑模式" : "基础评估通过",
                null);
    }

    private String buildNote(JSONObject obj, double score, String risk) {
        StringBuilder sb = new StringBuilder();
        if (risk != null) sb.append("风险:").append(risk);
        if (obj.containsKey("issues") && !obj.getJSONArray("issues").isEmpty()) {
            String firstIssue = obj.getJSONArray("issues").getString(0);
            if (firstIssue != null && firstIssue.length() > 5) {
                sb.append(sb.length() > 0 ? " | " : "").append(firstIssue);
            }
        }
        if (sb.isEmpty()) {
            sb.append(score >= 0.85 ? "高度可信" : score >= 0.7 ? "基本可靠" : "待核查");
        }
        String note = sb.toString();
        return note.length() > 250 ? note.substring(0, 250) : note;
    }

    private String riskFromScore(double score) {
        if (score >= 0.85) return "LOW";
        if (score >= 0.7) return "MEDIUM";
        if (score >= 0.5) return "HIGH";
        return "CRITICAL";
    }

    public record HallucinationResult(double score, String risk, String note, String suggestion) {}
}
