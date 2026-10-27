package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.LearningResource;

/**
 * 资源智能体统一接口
 * 包含生成 + 质量保障能力
 */
public interface ResourceAgent {

    /**
     * 生成资源（基础方法，子类必须实现）
     */
    LearningResource generate(String chapterName, String knowledgePoint,
                              String knowledgeLevel, String weakAreas);

    /**
     * 带完整质量保障的生成（Agent自治版本）
     * 默认实现：生成 → 格式校验 → 自检评分 → 幻觉检测 → 重试
     * 子类可重写以实现更精细的质量控制
     *
     * @param chapterName     章节名称
     * @param knowledgePoint  知识点
     * @param knowledgeLevel  知识水平（0-100）
     * @param weakAreas       薄弱环节
     * @param supervisor      幻觉检测智能体（可为null）
     * @return 带质量评分的资源，失败返回null
     */
    default LearningResource generateWithQuality(
            String chapterName, String knowledgePoint,
            String knowledgeLevel, String weakAreas,
            HallucinationSupervisorAgent supervisor) {

        int maxRetries = 2;
        String lastError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 1. 生成资源
                LearningResource lr = generate(chapterName, knowledgePoint, knowledgeLevel, weakAreas);
                if (lr == null) {
                    lastError = "生成返回空";
                    continue;
                }

                // 2. 格式校验（所有类型）
                if (!validateJsonFormat(lr.getContent())) {
                    lastError = "格式校验失败";
                    continue;
                }

                // 3. 自检评分（Agent自己实现）
                double selfScore = selfCheck(lr);
                lr.setQualityScore(selfScore);

                // 4. 幻觉检测
                if (supervisor != null) {
                    try {
                        HallucinationSupervisorAgent.HallucinationResult hResult =
                                supervisor.evaluate(lr.getContent(), lr.getType());
                        lr.setHallucinationScore(hResult.score());
                        lr.setHallucinationRisk(hResult.risk());
                        lr.setHallucinationNote(hResult.note());

                        // 幻觉评分过低，重试
                        if (hResult.score() < 0.5 && attempt < maxRetries) {
                            lastError = "幻觉评分过低: " + hResult.score();
                            continue;
                        }
                    } catch (Exception e) {
                        // 幻觉检测异常不阻断，记录日志
                    }
                }

                // 5. 生成成功
                return lr;
            } catch (Exception e) {
                lastError = e.getMessage();
            }
        }
        return null;
    }

    /**
     * 判断该Agent是否适用于当前知识点
     * 默认所有Agent都适用，子类可重写以实现按需选择
     *
     * @param chapterName     章节名称
     * @param knowledgePoint  知识点
     * @param weakAreas       学生薄弱环节（逗号分隔）
     * @return true=应该调用该Agent
     */
    default boolean supports(String chapterName, String knowledgePoint, String weakAreas) {
        return true;
    }

    /**
     * 获取智能体名称
     */
    default String getAgentName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 获取资源类型
     */
    default String getResourceType() {
        return "unknown";
    }

    /**
     * JSON格式校验（子类可重写）
     * 容忍 Markdown 包裹的 JSON：```json ... ``` 内的内容也视为有效
     * @return true=格式正确
     */
    default boolean validateJsonFormat(String aiReply) {
        if (aiReply == null || aiReply.isBlank()) return false;
        String trimmed = aiReply.trim();
        // 去除 Markdown 代码块包裹
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();
        return trimmed.startsWith("[") || trimmed.startsWith("{");
    }

    /**
     * 内容质量自检（子类可重写）
     * @return 0-1，1=完全无误
     */
    default double selfCheck(LearningResource resource) {
        if (resource == null || resource.getContent() == null) return 0.0;
        double score = 1.0;
        // 基础检查：内容非空、长度合理
        String content = resource.getContent();
        if (content.length() < 50) score -= 0.3;
        if (content.length() > 10000) score -= 0.1;
        return Math.max(0, score);
    }
}
