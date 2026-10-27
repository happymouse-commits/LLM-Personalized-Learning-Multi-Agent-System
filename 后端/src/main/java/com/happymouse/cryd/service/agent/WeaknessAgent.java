package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 错题诊断智能体 — 分析常见错误 + 正确写法 + 避坑指南
 */
@Component
public class WeaknessAgent implements ResourceAgent {

    private static final Logger log = LoggerFactory.getLogger(WeaknessAgent.class);

    private final SparkClient sparkClient;

    public WeaknessAgent(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @Override
    public String getResourceType() { return ResourceType.WEAKNESS.getId(); }

    @Override
    public boolean validateJsonFormat(String aiReply) {
        return aiReply != null && !aiReply.isBlank() && aiReply.length() > 50;
    }

    /**
     * 错题诊断自检：错误示例、正确写法、代码块、避坑建议
     */
    @Override
    public double selfCheck(com.happymouse.cryd.model.entity.LearningResource resource) {
        double score = 1.0;
        String content = resource.getContent();
        if (content == null) return 0.0;

        // 必须有错误示例
        int errorCount = 0;
        if (content.contains("❌") || content.contains("错误示例")) errorCount++;
        if (content.contains("常见错误") || content.contains("错误类型")) errorCount++;
        if (errorCount < 1) score -= 0.2;

        // 必须有正确写法
        if (!content.contains("✅") && !content.contains("正确写法") && !content.contains("正确示例")) {
            score -= 0.15;
        }

        // 必须有代码块
        if (!content.contains("```")) score -= 0.2;

        // 必须有避坑建议
        if (!content.contains("建议") && !content.contains("避坑") && !content.contains("注意事项")) {
            score -= 0.1;
        }

        return Math.max(0, score);
    }

    /**
     * 错题诊断只在知识点属于薄弱环节时才生成
     */
    @Override
    public boolean supports(String chapterName, String knowledgePoint, String weakAreas) {
        if (weakAreas == null || weakAreas.isBlank()) return false;
        String[] areas = weakAreas.split("[,，、]");
        String kpLower = knowledgePoint.toLowerCase();
        for (String area : areas) {
            String trimmed = area.trim().toLowerCase();
            if (!trimmed.isEmpty() && (kpLower.contains(trimmed) || trimmed.contains(kpLower))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成错题诊断资源
     */
    @Override
    public LearningResource generate(String chapterName, String knowledgePoint,
                                     String knowledgeLevel, String weakAreas) {
        String prompt = String.format("""
            你是C语言调试专家和教学顾问。请为「%s」章节的「%s」知识点生成1份错题诊断报告。

            【学生信息】
            知识水平：%s/100
            薄弱环节：%s
            ⚠️ 请重点分析薄弱环节相关的常见错误。错误案例中至少60%应覆盖薄弱点。

            【难度要求】
            请根据知识水平选择difficulty：<40用"easy"，40-70用"medium"，>70用"hard"。

            ════════════════════════════════════════════════════════════════
            【诊断报告质量标准 — 必须严格遵守】
            ════════════════════════════════════════════════════════════════
            1. 真实性：错误必须是学生实际会犯的，不是编造的
            2. 针对性：紧扣知识点的易错点
            3. 实用性：给出可操作的避坑方法
            4. 对比性：错误写法 vs 正确写法，一目了然

            ════════════════════════════════════════════════════════════════
            【输出结构要求】
            ════════════════════════════════════════════════════════════════

            一、常见错误类型分析（3-5个）
            每个错误类型包含：
            - 错误名称
            - 错误描述（为什么会犯这个错）
            - 错误示例（错误代码）
            - 正确写法（修正后代码）
            - 原因分析（为什么这样写是对的）

            二、易错点清单
            用列表形式总结3-5个最需要注意的点

            三、避坑指南
            给出3-5条实用建议，帮助学生避免这些错误

            四、针对性练习建议
            推荐1-2个练习方向

            ════════════════════════════════════════════════════════════════
            【格式规范】
            ════════════════════════════════════════════════════════════════
            - 使用Markdown格式
            - 错误示例用 ❌ 标记，正确写法用 ✅ 标记
            - 代码用 ```c 包裹
            - 重要提醒用 ⚠️ 标记

            ════════════════════════════════════════════════════════════════
            【禁止事项】
            ════════════════════════════════════════════════════════════════
            ❌ 禁止编造不存在的错误
            ❌ 禁止正确写法本身有错误
            ❌ 禁止建议含糊不清
            ❌ 禁止代码无法运行

            输出JSON格式：
            [{"title": "标题", "type": "weakness", "content": "Markdown内容", "knowledgePoint": "知识点", "difficulty": "easy/medium/hard"}]

            只输出纯JSON数组，不要```json标记。
            """, chapterName, knowledgePoint, knowledgeLevel, weakAreas);

        String aiReply;
        try {
            aiReply = sparkClient.smartChat(prompt, "生成错题诊断报告",
                    SparkClient.TaskComplexity.MEDIUM, 16384);
        } catch (Exception e) {
            log.error("WeaknessAgent smartChat调用失败", e);
            return null;
        }

        if (aiReply == null || aiReply.isBlank()) return null;

        try {
            var arr = com.happymouse.cryd.common.JsonParser.parseArray(aiReply);
            if (arr == null || arr.isEmpty()) {
                log.warn("错题诊断JSON解析失败, 前200字={}", aiReply.substring(0, Math.min(200, aiReply.length())));
                // 降级：直接用原始内容作为资源
                LearningResource lr = new LearningResource();
                lr.setTitle("错题诊断：" + knowledgePoint);
                lr.setType(ResourceType.WEAKNESS.getId());
                lr.setContent(aiReply);
                lr.setKnowledgePoint(knowledgePoint);
                lr.setDifficulty("medium");
                return lr;
            }

            var obj = arr.getJSONObject(0);
            LearningResource lr = new LearningResource();
            lr.setTitle(obj.getString("title") != null ? obj.getString("title") : "错题诊断：" + knowledgePoint);
            lr.setType(ResourceType.WEAKNESS.getId());
            lr.setContent(obj.getString("content") != null ? obj.getString("content") : aiReply);
            lr.setKnowledgePoint(knowledgePoint);
            String diff = obj.getString("difficulty");
            lr.setDifficulty(diff != null && diff.length() <= 10 ? diff : "medium");
            return lr;
        } catch (Exception e) {
            log.warn("错题诊断解析失败: error={}", e.getMessage());
            // 降级：直接用原始内容作为资源
            LearningResource lr = new LearningResource();
            lr.setTitle("错题诊断：" + knowledgePoint);
            lr.setType(ResourceType.WEAKNESS.getId());
            lr.setContent(aiReply);
            lr.setKnowledgePoint(knowledgePoint);
            lr.setDifficulty("medium");
            return lr;
        }
    }
}
