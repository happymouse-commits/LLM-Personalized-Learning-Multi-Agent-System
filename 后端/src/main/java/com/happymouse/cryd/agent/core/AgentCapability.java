package com.happymouse.cryd.agent.core;

import java.util.function.Function;

/**
 * Agent能力描述 — 供调度器进行能力匹配
 */
public class AgentCapability {

    private String id;
    private String description;
    private double confidence;

    public AgentCapability() {}

    public AgentCapability(String id, String description, double confidence) {
        this.id = id;
        this.description = description;
        this.confidence = confidence;
    }

    public static AgentCapability of(String id, String description, double confidence) {
        return new AgentCapability(id, description, confidence);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    /**
     * 计算此能力与需求描述的匹配度
     * 修复S3: 原实现用子串匹配，中英文混合时永远为0，调度器形同虚设。
     * 新策略: 优先用 capability.id 与任务 type 做规范化精确匹配（高权重），
     * 再叠加 description 关键词匹配（低权重）。
     */
    public double matchScore(String requirement) {
        if (requirement == null || requirement.isEmpty()) return 0;

        // 规范化: 去除分隔符(: _ - 空格)并转小写，便于跨格式匹配
        // 例: task type "tutor_answer" 与 cap id "tutor:answer" 均归一为 "tutoranswer"
        Function<String, String> normalize = s -> s == null ? "" : s.toLowerCase().replaceAll("[:_\\-\\s,，、]+", "");

        String reqNorm = normalize.apply(requirement);
        String idNorm = normalize.apply(id);

        double score = 0;

        // 1. id 与 requirement 的规范化匹配（高权重）
        //    任务描述形如 "tutor_answer C语言辅导答疑"，capability id 形如 "tutor:answer"
        if (!idNorm.isEmpty() && reqNorm.contains(idNorm)) {
            score = Math.max(score, 0.95);
        } else if (!idNorm.isEmpty()) {
            // 尝试用 requirement 的第一个 token（通常是任务 type）与 id 匹配
            String firstToken = requirement.toLowerCase().split("[\\s,，、]+")[0];
            String firstNorm = normalize.apply(firstToken);
            if (!firstNorm.isEmpty() && (firstNorm.equals(idNorm) || idNorm.startsWith(firstNorm) || firstNorm.startsWith(idNorm))) {
                score = Math.max(score, 0.9);
            }
        }

        // 2. description 关键词匹配（低权重，作为补充）
        String[] reqWords = requirement.toLowerCase().split("[\\s,，、]+");
        String[] capWords = description.toLowerCase().split("[\\s,，、]+");
        if (reqWords.length > 0 && capWords.length > 0) {
            int match = 0;
            for (String rw : reqWords) {
                if (rw.isEmpty()) continue;
                for (String cw : capWords) {
                    if (cw.isEmpty()) continue;
                    // 仅对长度>=2的词做子串匹配，避免单字误匹配
                    if ((rw.length() >= 2 && cw.length() >= 2) && (rw.contains(cw) || cw.contains(rw))) {
                        match++;
                        break;
                    }
                }
            }
            double jaccard = (double) match / (reqWords.length + capWords.length - match);
            score = Math.max(score, jaccard * 0.6); // 关键词匹配上限0.6，低于id匹配
        }

        return score * confidence;
    }

    @Override
    public String toString() {
        return "AgentCapability{id='" + id + "', desc='" + description + "', confidence=" + confidence + "}";
    }
}
