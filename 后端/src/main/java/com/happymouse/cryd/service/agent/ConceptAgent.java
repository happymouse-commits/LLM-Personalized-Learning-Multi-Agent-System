package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConceptAgent implements ResourceAgent {

    private static final Logger log = LoggerFactory.getLogger(ConceptAgent.class);
    private final SparkClient sparkClient;

    public ConceptAgent(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @Override
    public String getResourceType() { return ResourceType.EXPLANATION.getId(); }

    /**
     * 知识精讲自检：内容长度、代码块、标题结构、知识点匹配
     */
    @Override
    public double selfCheck(com.happymouse.cryd.model.entity.LearningResource resource) {
        double score = 1.0;
        String content = resource.getContent();
        if (content == null) return 0.0;

        // 内容长度：至少500字才算合格
        if (content.length() < 500) score -= 0.3;

        // 必须包含代码示例
        if (!content.contains("```c") && !content.contains("```C") && !content.contains("``` c")) {
            score -= 0.2;
        }

        // 必须有标题结构
        if (!content.contains("##")) score -= 0.1;

        // 知识点匹配
        if (resource.getKnowledgePoint() != null &&
            !content.contains(resource.getKnowledgePoint())) {
            score -= 0.2;
        }

        return Math.max(0, score);
    }

    @Override
    public boolean validateJsonFormat(String aiReply) {
        // 知识精讲返回Markdown，不要求JSON格式
        return aiReply != null && !aiReply.isBlank() && aiReply.length() > 50;
    }

    @Override
    public LearningResource generate(String chapterName, String knowledgePoint,
                                     String knowledgeLevel, String weakAreas) {
        log.info("ConceptAgent开始生成: chapter={}, kp={}", chapterName, knowledgePoint);
        
        String prompt = "你是C语言教学专家。请为" + chapterName + "章节的" + knowledgePoint + "知识点生成1篇知识精讲。"
            + "知识水平：" + knowledgeLevel + "/100，薄弱环节：" + weakAreas + "。"
            + "【重要】title必须与content的标题完全一致，content的第一行标题必须就是title的值。"
            + "输出JSON数组格式：[{\"title\":\"标题\",\"type\":\"article\",\"content\":\"Markdown内容（第一行必须是# 标题，且标题与title字段一致）\",\"knowledgePoint\":\"知识点\",\"difficulty\":\"easy/medium/hard\"}]";

        String aiReply;
        try {
            aiReply = sparkClient.smartChat(prompt, "生成知识精讲", SparkClient.TaskComplexity.MEDIUM, 8192);
        } catch (Exception e) {
            log.warn("ConceptAgent LLM调用失败: {}", e.getMessage());
            return null;
        }

        if (aiReply == null || aiReply.isBlank()) {
            log.warn("ConceptAgent LLM返回空内容");
            return null;
        }

        var arr = com.happymouse.cryd.common.JsonParser.parseArray(aiReply);
        if (arr == null || arr.isEmpty()) {
            log.warn("ConceptAgent JSON解析失败");
            return null;
        }

        var obj = arr.getJSONObject(0);
        LearningResource lr = new LearningResource();
        lr.setType(ResourceType.EXPLANATION.getId());
        lr.setContent(obj.getString("content"));
        lr.setKnowledgePoint(obj.getString("knowledgePoint"));
        lr.setDifficulty(obj.getString("difficulty"));

        // 从 content 的第一行 Markdown 标题提取 title，确保一致
        String title = obj.getString("title");
        String content = lr.getContent();
        if (content != null) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    title = trimmed.substring(2).trim();
                    break;
                }
            }
        }
        lr.setTitle(title);
        log.info("ConceptAgent生成成功: title={}", lr.getTitle());
        return lr;
    }
}
