package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 知识导图智能体 — 生成可交互的思维导图JSON数据
 * 输出格式供前端渲染为可展开/折叠的SVG思维导图
 */
@Component
public class MindmapAgent implements ResourceAgent {

    private static final Logger log = LoggerFactory.getLogger(MindmapAgent.class);

    private final SparkClient sparkClient;

    public MindmapAgent(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    /**
     * 思维导图自检：JSON结构完整性、central/branches、分支数量、子节点深度
     */
    @Override
    public double selfCheck(com.happymouse.cryd.model.entity.LearningResource resource) {
        double score = 1.0;
        String content = resource.getContent();
        if (content == null) return 0.0;

        try {
            com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(content);
            if (json == null) return 0.0;

            // 必须有central
            if (!json.containsKey("central")) score -= 0.3;

            // 必须有branches
            com.alibaba.fastjson2.JSONArray branches = json.getJSONArray("branches");
            if (branches == null || branches.isEmpty()) {
                score -= 0.3;
            } else {
                // branches数量合理（6-8个为佳，至少4个）
                if (branches.size() < 4) score -= 0.2;
                if (branches.size() > 10) score -= 0.1;

                // 每个branch应有subtopics
                for (int i = 0; i < branches.size(); i++) {
                    com.alibaba.fastjson2.JSONObject b = branches.getJSONObject(i);
                    Object subtopics = b.get("subtopics");
                    if (subtopics == null) {
                        score -= 0.05;
                    } else if (subtopics instanceof com.alibaba.fastjson2.JSONArray subArr) {
                        // 检查子节点数量是否足够丰富
                        if (subArr.size() < 3) score -= 0.03;
                        // 检查是否有深层叶节点（对象格式含leaves）
                        boolean hasLeaves = false;
                        for (int j = 0; j < subArr.size(); j++) {
                            Object sub = subArr.get(j);
                            if (sub instanceof com.alibaba.fastjson2.JSONObject subObj) {
                                if (subObj.containsKey("leaves")) hasLeaves = true;
                            }
                        }
                        if (!hasLeaves && subArr.size() > 0) score -= 0.02;
                    }
                    // 检查是否有tip
                    if (!b.containsKey("tip")) score -= 0.02;
                }
            }
        } catch (Exception e) {
            score -= 0.5;
        }

        return Math.max(0, score);
    }

    @Override
    public String getResourceType() { return ResourceType.MINDMAP.getId(); }

    @Override
    public boolean validateJsonFormat(String aiReply) {
        if (aiReply == null || aiReply.isBlank()) return false;
        try {
            com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(aiReply);
            return json != null && json.containsKey("central") && json.containsKey("branches");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public LearningResource generate(String chapterName, String knowledgePoint,
                                     String knowledgeLevel, String weakAreas) {
        String prompt = String.format("""
            你是C语言教学专家。请为「%s」章节的「%s」知识点生成详细且丰富的思维导图数据。

            【学生信息】
            知识水平：%s/100
            薄弱环节：%s
            ⚠️ 请根据薄弱环节标注各分支的level，薄弱相关的分支标为"weak"。

            【输出格式要求】
            输出JSON格式，包含中心节点和6-8个分支，每个分支4-6个子节点，每个子节点可包含2-3个叶节点。

            {
              "central": "中心知识点名称",
              "branches": [
                {
                  "topic": "分支主题",
                  "level": "weak/medium/stable",
                  "subtopics": [
                    {
                      "name": "子节点名称",
                      "leaves": ["叶节点1", "叶节点2"]
                    }
                  ],
                  "tip": "该分支的常见错误或注意事项",
                  "example": "一个简短的代码示例或具体说明"
                }
              ]
            }

            【level标注规则 — 严格按此执行】
            - weak: 该分支主题属于学生薄弱环节中的内容（将上方薄弱环节的关键词与分支主题对比，包含即为weak）
            - medium: 该分支有一定难度但不在薄弱环节中
            - stable: 基础语法、输入输出等已掌握内容

            【内容要求 — 必须丰富详细】
            - 中心节点：核心知识点名称（简洁）
            - 分支主题：6-8个主要方面（覆盖：基础概念、语法规则、核心用法、进阶技巧、常见错误、实战应用、关联知识、扩展延伸）
            - 子节点：每个分支4-6个具体知识点（用对象格式，含name和leaves）
            - 叶节点：每个子节点2-3个更细粒度的要点（如具体语法细节、注意事项、变体写法）
            - tip：每个分支的常见错误或注意事项
            - example：每个分支提供一个简短代码片段或具体说明

            【丰富度要求】
            - 每个分支必须从多个角度展开，不能只有浅层描述
            - 叶节点应包含具体的语法、语义、用法细节，而非空泛概括
            - 对于weak级别的分支，额外增加1-2个子节点深入剖析易错点

            只输出纯JSON对象，不要```json标记。
            """, chapterName, knowledgePoint, knowledgeLevel, weakAreas);

        String aiReply;
        try {
            aiReply = sparkClient.smartChat(prompt, "生成思维导图",
                    SparkClient.TaskComplexity.MEDIUM, 8192);
        } catch (Exception e) {
            log.warn("MindmapAgent LLM调用失败: {}", e.getMessage());
            return null;
        }

        if (aiReply == null || aiReply.isBlank()) return null;

        try {
            // 验证JSON格式
            com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(aiReply);
            if (json == null || !json.containsKey("central") || !json.containsKey("branches")) {
                log.warn("思维导图JSON格式错误");
                return null;
            }

            LearningResource lr = new LearningResource();
            lr.setTitle("知识导图：" + chapterName + " - " + knowledgePoint);
            lr.setType(ResourceType.MINDMAP.getId());
            lr.setContent(aiReply); // JSON格式，前端直接解析渲染
            lr.setKnowledgePoint(knowledgePoint);
            lr.setDifficulty("easy");
            return lr;
        } catch (Exception e) {
            log.warn("思维导图解析失败: error={}", e.getMessage());
            return null;
        }
    }
}
