package com.happymouse.cryd.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 聊天请求
 */
public class ChatRequest {
    private Long studentId;
    private String message;
    private String agentName;
    private String mode; // fast / expert / socratic
    private Integer onboardingStep; // 前端传入的引导步骤号（1-based），null表示非引导模式

    /** 前端构建的额外上下文（编程题页面传入：题目描述 + 题解 + 学生代码等） */
    private String extraContext;

    /** 前端自定义 system prompt（如苏格拉底式引导提示词） */
    private String systemPrompt;

    /** 前端传入的最近对话历史，每条含 role(user/assistant) 和 content */
    private List<Map<String, String>> history;

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Integer getOnboardingStep() { return onboardingStep; }
    public void setOnboardingStep(Integer onboardingStep) { this.onboardingStep = onboardingStep; }
    public String getExtraContext() { return extraContext; }
    public void setExtraContext(String extraContext) { this.extraContext = extraContext; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public List<Map<String, String>> getHistory() { return history; }
    public void setHistory(List<Map<String, String>> history) { this.history = history; }
}
