package com.happymouse.cryd.agent.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 智能体上下文容器 — 携带任务图、会话状态、各Agent产出
 */
public class AgentContext {

    private final Long studentId;
    private final String originalMessage;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<SubTask> tasks = new CopyOnWriteArrayList<>();
    private final Map<String, AgentResult> results = new ConcurrentHashMap<>();
    private final List<Map<String, String>> conversationHistory;

    public AgentContext(Long studentId, String originalMessage, List<Map<String, String>> conversationHistory) {
        this.studentId = studentId;
        this.originalMessage = originalMessage;
        // 修复O1: 复制为 CopyOnWriteArrayList 保证线程安全，且隔离外部传入的 list 避免外部修改影响
        this.conversationHistory = conversationHistory != null
                ? new CopyOnWriteArrayList<>(conversationHistory)
                : new CopyOnWriteArrayList<>();
    }

    public Long getStudentId() { return studentId; }
    public String getOriginalMessage() { return originalMessage; }
    public List<Map<String, String>> getConversationHistory() { return conversationHistory; }

    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    public Object getAttribute(String key) { return attributes.get(key); }
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) { return (T) attributes.get(key); }

    public void addTask(SubTask task) { tasks.add(task); }
    public List<SubTask> getTasks() { return Collections.unmodifiableList(tasks); }
    public boolean hasTasks() { return !tasks.isEmpty(); }

    public void putResult(String agentName, AgentResult result) { results.put(agentName, result); }
    public AgentResult getResult(String agentName) { return results.get(agentName); }
    public Map<String, AgentResult> getAllResults() { return Collections.unmodifiableMap(results); }

    /**
     * 子任务定义
     */
    public static class SubTask {
        private String id;
        private String type;
        private String description;
        private int priority;
        private List<String> dependsOn = new ArrayList<>();
        private String status = "pending"; // pending, running, completed, failed

        public SubTask() {}
        public SubTask(String id, String type, String description, int priority) {
            this.id = id;
            this.type = type;
            this.description = description;
            this.priority = priority;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public List<String> getDependsOn() { return dependsOn; }
        public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * Agent执行结果
     */
    public static class AgentResult {
        private String agentName;
        private String output;
        private boolean success;
        private int retryCount;
        private long durationMs;
        private String errorMessage;

        public AgentResult() {}
        public AgentResult(String agentName, String output, boolean success) {
            this.agentName = agentName;
            this.output = output;
            this.success = success;
        }

        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
