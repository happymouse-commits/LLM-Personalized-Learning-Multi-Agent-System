package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 巩固练习智能体 — 生成选择题/填空题/编程题 + 答案详解
 */
@Component
public class ExerciseAgent implements ResourceAgent {

    private static final Logger log = LoggerFactory.getLogger(ExerciseAgent.class);

    private final SparkClient sparkClient;

    public ExerciseAgent(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @Override
    public String getResourceType() { return ResourceType.EXERCISE.getId(); }

    /**
     * 巩固练习自检：题目类型、答案标记、解析
     */
    @Override
    public double selfCheck(com.happymouse.cryd.model.entity.LearningResource resource) {
        double score = 1.0;
        String content = resource.getContent();

        if (content == null) return 0.0;

        // 必须包含选择题
        if (!content.contains("选择题") && !content.contains("第1题") && !content.contains("题目")) {
            score -= 0.2;
        }

        // 必须有答案标记
        if (!content.contains("【答案】") && !content.contains("答案") && !content.contains("Answer")) {
            score -= 0.2;
        }

        // 必须有解析
        if (!content.contains("【解析】") && !content.contains("解析") && !content.contains("Explanation")) {
            score -= 0.1;
        }

        // 内容长度检查
        if (content.length() < 300) score -= 0.2;

        return Math.max(0, score);
    }

    @Override
    public boolean validateJsonFormat(String aiReply) {
        return aiReply != null && !aiReply.isBlank() && aiReply.length() > 50;
    }

    /**
     * 生成巩固练习资源
     */
    @Override
    public LearningResource generate(String chapterName, String knowledgePoint,
                                     String knowledgeLevel, String weakAreas) {
        String difficultyHint = inferDifficulty(knowledgeLevel);

        String prompt = String.format("""
            你是C语言教学专家和出题专家。请为「%s」章节的「%s」知识点生成一套巩固练习题。

            【学生信息】
            知识水平：%s/100
            薄弱环节：%s
            建议难度：%s

            ════════════════════════════════════════════════════════════════
            【题目质量标准 — 必须严格遵守】
            ════════════════════════════════════════════════════════════════
            1. 准确性：所有题目和答案必须100%正确
            2. 针对性：紧扣知识点，不超纲
            3. 层次性：包含基础→进阶→综合
            4. 解析性：每题必须有详细解析
            5. 实用性：题目贴近实际编程场景

            ════════════════════════════════════════════════════════════════
            【输出结构要求】
            ════════════════════════════════════════════════════════════════

            一、选择题（3道）
            每道题包含：
            - 题目描述
            - 4个选项（A/B/C/D）
            - 正确答案
            - 解析（为什么选这个，其他选项为什么错）

            二、填空题（2道）
            每道题包含：
            - 题目描述（含空格位置）
            - 正确答案
            - 解析

            三、编程题（1道）
            包含：
            - 题目要求
            - 输入输出示例
            - 参考答案（完整代码）
            - 代码解析

            ════════════════════════════════════════════════════════════════
            【格式规范】
            ════════════════════════════════════════════════════════════════
            - 使用Markdown格式
            - 题目用 **第X题 [难度]** 标记
            - 选项用 A/B/C/D 列表
            - 答案用 【答案】 标记
            - 解析用 【解析】 标记
            - 代码用 ```c 包裹

            ════════════════════════════════════════════════════════════════
            【禁止事项】
            ════════════════════════════════════════════════════════════════
            ❌ 禁止答案错误
            ❌ 禁止解析含糊不清
            ❌ 禁止题目超纲
            ❌ 禁止选项有明显漏洞
            ❌ 禁止编程题无法运行

            输出JSON格式：
            [{"title": "标题", "type": "exercise", "content": "Markdown内容", "knowledgePoint": "知识点", "difficulty": "easy/medium/hard"}]

            只输出纯JSON数组，不要```json标记。
            """, chapterName, knowledgePoint, knowledgeLevel, weakAreas, difficultyHint);

        String aiReply;
        try {
            aiReply = sparkClient.smartChat(prompt, "生成巩固练习题",
                    SparkClient.TaskComplexity.MEDIUM, 16384);
        } catch (Exception e) {
            log.error("ExerciseAgent smartChat调用失败", e);
            // 降级：返回空资源，让 tryGenerate 记录失败
            return null;
        }

        log.info("ExerciseAgent LLM返回: length={}, 前100字={}",
                aiReply != null ? aiReply.length() : 0,
                aiReply != null ? aiReply.substring(0, Math.min(100, aiReply.length())) : "null");
        if (aiReply == null || aiReply.isBlank()) return null;

        try {
            String cleanReply = aiReply.trim();
            // 去除 Markdown 包裹
            if (cleanReply.startsWith("```json")) cleanReply = cleanReply.substring(7);
            else if (cleanReply.startsWith("```")) cleanReply = cleanReply.substring(3);
            if (cleanReply.endsWith("```")) cleanReply = cleanReply.substring(0, cleanReply.length() - 3);
            cleanReply = cleanReply.trim();

            var arr = com.happymouse.cryd.common.JsonParser.parseArray(cleanReply);
            if (arr == null || arr.isEmpty()) {
                log.warn("ExerciseAgent JSON解析失败, 前200字={}", cleanReply.substring(0, Math.min(200, cleanReply.length())));
                // 降级：直接用原始内容作为资源
                LearningResource lr = new LearningResource();
                lr.setTitle("巩固练习：" + knowledgePoint);
                lr.setType(ResourceType.EXERCISE.getId());
                lr.setContent(aiReply);
                lr.setKnowledgePoint(knowledgePoint);
                lr.setDifficulty("medium");
                return lr;
            }

            var obj = arr.getJSONObject(0);
            LearningResource lr = new LearningResource();
            lr.setTitle(obj.getString("title") != null ? obj.getString("title") : "巩固练习：" + knowledgePoint);
            lr.setType(ResourceType.EXERCISE.getId());
            lr.setContent(obj.getString("content") != null ? obj.getString("content") : aiReply);
            lr.setKnowledgePoint(knowledgePoint);
            String diff = obj.getString("difficulty");
            lr.setDifficulty(diff != null && diff.length() <= 10 ? diff : "medium");
            return lr;
        } catch (Exception e) {
            log.warn("练习题解析失败: error={}, class={}",
                    e.getMessage(), e.getClass().getSimpleName());
            // 降级：直接用原始内容作为资源
            LearningResource lr = new LearningResource();
            lr.setTitle("巩固练习：" + knowledgePoint);
            lr.setType(ResourceType.EXERCISE.getId());
            lr.setContent(aiReply);
            lr.setKnowledgePoint(knowledgePoint);
            lr.setDifficulty("medium");
            return lr;
        }
    }

    private String inferDifficulty(String knowledgeLevel) {
        try {
            int level = Integer.parseInt(knowledgeLevel);
            if (level < 30) return "基础题（概念理解、简单语法）";
            if (level < 60) return "基础题（基本应用、小代码编写）";
            if (level < 80) return "进阶题（综合应用、算法设计）";
            return "挑战题（复杂算法、系统设计）";
        } catch (NumberFormatException e) {
            return "基础题";
        }
    }
}
