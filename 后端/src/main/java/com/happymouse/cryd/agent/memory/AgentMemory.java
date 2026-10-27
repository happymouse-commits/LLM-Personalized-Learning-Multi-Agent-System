package com.happymouse.cryd.agent.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent记忆管理 — 每个Agent独立的对话上下文窗口
 * 生产环境可替换为Redis实现
 */
public class AgentMemory {

    private final String agentName;
    private final int maxHistorySize;
    private final Map<Long, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();

    public AgentMemory(String agentName, int maxHistorySize) {
        this.agentName = agentName;
        this.maxHistorySize = maxHistorySize;
    }

    public AgentMemory(String agentName) {
        this(agentName, 20);
    }

    public String getAgentName() { return agentName; }

    public void save(Long studentId, String role, String content) {
        // 使用 computeIfAbsent 保证 list 创建原子；对 list 操作用 synchronized 保证线程安全
        List<Map<String, String>> history = conversations.computeIfAbsent(studentId, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (history) {
            history.add(Map.of("role", role, "content", content));
            while (history.size() > maxHistorySize) {
                history.remove(0);
            }
        }
    }

    /**
     * 返回防御性拷贝，避免调用方修改内部状态
     */
    public List<Map<String, String>> load(Long studentId) {
        List<Map<String, String>> history = conversations.get(studentId);
        if (history == null) return Collections.emptyList();
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void clear(Long studentId) {
        conversations.remove(studentId);
    }

    public int getHistorySize(Long studentId) {
        List<Map<String, String>> history = conversations.get(studentId);
        if (history == null) return 0;
        synchronized (history) {
            return history.size();
        }
    }
}
