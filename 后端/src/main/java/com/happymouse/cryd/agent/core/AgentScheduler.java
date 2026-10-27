package com.happymouse.cryd.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * 调度Agent — 能力匹配、优先级调度、并行执行、失败重试
 */
public class AgentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

    private final Map<String, BaseAgent> agentRegistry = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final int maxRetries;
    private final long taskTimeoutMs;

    public AgentScheduler(int maxRetries, long taskTimeoutMs) {
        this.maxRetries = maxRetries;
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public AgentScheduler() {
        this(3, 120_000);
    }

    /**
     * 注册Agent
     */
    public void register(BaseAgent agent) {
        agentRegistry.put(agent.getName(), agent);
        log.info("[调度器] 注册Agent: {}", agent.getName());
    }

    /**
     * 执行任务图
     * 修复P1: 已完成任务(status=completed)会被跳过，避免重试时重复执行
     * 修复P2: 总超时使用固定值而非 单任务超时×任务数
     * 修复P3: 先建立所有 future 引用再统一等待，避免依赖 future 为 null
     * 修复P4: matchAgent 返回 null 时生成失败结果并记录，而非静默丢失
     * 修复E3: 失败结果也存入 context 供审核器/聚合器查阅
     */
    public List<AgentContext.AgentResult> executeTaskGraph(AgentContext context) {
        List<AgentContext.SubTask> tasks = context.getTasks();
        if (tasks.isEmpty()) {
            log.warn("[调度器] 无任务可执行");
            return Collections.emptyList();
        }

        // 过滤掉已完成的任务（修复P1: 重试时不重复执行已成功任务）
        List<AgentContext.SubTask> pendingTasks = new ArrayList<>();
        for (AgentContext.SubTask t : tasks) {
            if (!"completed".equals(t.getStatus()) && !"skip".equals(t.getStatus())) {
                pendingTasks.add(t);
            }
        }
        if (pendingTasks.isEmpty()) {
            log.info("[调度器] 所有任务已完成，跳过执行");
            return Collections.emptyList();
        }

        List<AgentContext.AgentResult> results = new CopyOnWriteArrayList<>();
        Map<String, CompletableFuture<Void>> taskFutures = new HashMap<>();
        Set<String> completedTasks = ConcurrentHashMap.newKeySet();

        // 按优先级排序
        List<AgentContext.SubTask> sorted = new ArrayList<>(pendingTasks);
        sorted.sort(Comparator.comparingInt(AgentContext.SubTask::getPriority));

        // 第一阶段：为每个任务创建 future 并放入 map（修复P3: 先建引用再等待）
        for (AgentContext.SubTask task : sorted) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            taskFutures.put(task.getId(), future);
        }

        // 第二阶段：提交执行
        for (AgentContext.SubTask task : sorted) {
            CompletableFuture<Void> future = taskFutures.get(task.getId());
            executor.submit(() -> {
                try {
                    // 等待依赖任务完成（此时所有 future 已在 map 中，不会为 null）
                    for (String depId : task.getDependsOn()) {
                        CompletableFuture<Void> depFuture = taskFutures.get(depId);
                        if (depFuture != null) {
                            try { depFuture.get(taskTimeoutMs, TimeUnit.MILLISECONDS); }
                            catch (Exception e) { log.warn("[调度器] 等待依赖{}超时/失败: {}", depId, e.getMessage()); }
                        }
                    }

                    // 匹配Agent
                    BaseAgent agent = matchAgent(task);
                    if (agent == null) {
                        // 修复P4: 不再静默丢失，生成失败结果
                        log.warn("[调度器] 未找到匹配Agent: type={}", task.getType());
                        task.setStatus("failed");
                        AgentContext.AgentResult failResult = new AgentContext.AgentResult(
                            "Unmatched:" + task.getType(), null, false);
                        failResult.setErrorMessage("无匹配Agent: " + task.getType());
                        results.add(failResult);
                        // 修复E3: 失败结果也存入 context
                        context.putResult("task_" + task.getId(), failResult);
                        completedTasks.add(task.getId());
                        return;
                    }

                    // 执行
                    task.setStatus("running");
                    log.info("[调度器] 执行任务: id={}, agent={}", task.getId(), agent.getName());
                    AgentContext.AgentResult result = agent.executeWithRetry(context, maxRetries);

                    if (result.isSuccess()) {
                        task.setStatus("completed");
                        context.putResult(agent.getName(), result);
                    } else {
                        task.setStatus("failed");
                        // 修复E3: 失败结果也存入 context
                        context.putResult(agent.getName(), result);
                    }

                    results.add(result);
                    completedTasks.add(task.getId());
                } finally {
                    future.complete(null);
                }
            });
        }

        // 等待所有任务完成（修复P2: 总超时为固定值，不再乘以任务数）
        long totalTimeout = taskTimeoutMs + 30_000L; // 单任务超时 + 30s 余量
        CompletableFuture.allOf(taskFutures.values().toArray(new CompletableFuture[0]))
                .orTimeout(totalTimeout, TimeUnit.MILLISECONDS)
                .exceptionally(e -> { log.warn("[调度器] 部分任务超时"); return null; })
                .join();

        log.info("[调度器] 执行完成: {}/{} 成功", completedTasks.size(), pendingTasks.size());
        return results;
    }

    /**
     * 能力匹配 — 根据任务类型匹配最合适的Agent
     */
    private BaseAgent matchAgent(AgentContext.SubTask task) {
        String type = task.getType();
        String description = task.getDescription();
        BaseAgent best = null;
        double bestScore = 0;

        for (BaseAgent agent : agentRegistry.values()) {
            // 排除管道Agent自身
            if (agent instanceof TaskDecomposer || agent instanceof AgentReviewer
                || agent instanceof AggregatorAgent) {
                continue;
            }
            for (AgentCapability cap : agent.getCapabilities()) {
                double score = cap.matchScore(type + " " + description);
                if (score > bestScore) {
                    bestScore = score;
                    best = agent;
                }
            }
        }

        if (best == null || bestScore < 0.3) {
            // 兜底：类型名匹配
            BaseAgent fallback = matchByTypeName(type);
            if (fallback != null) return fallback;
        }

        return best;
    }

    /**
     * 按类型名兜底匹配
     */
    private BaseAgent matchByTypeName(String type) {
        if (type == null) return null;
        for (BaseAgent agent : agentRegistry.values()) {
            for (AgentCapability cap : agent.getCapabilities()) {
                if (cap.getId().contains(type) || cap.getDescription().contains(type)) {
                    return agent;
                }
            }
        }
        // 最终兜底：返回TutorAgent
        for (BaseAgent agent : agentRegistry.values()) {
            if (agent.getName().contains("辅导") || agent.getName().contains("导师")) {
                return agent;
            }
        }
        return agentRegistry.values().stream().findFirst().orElse(null);
    }

    public Map<String, BaseAgent> getAgentRegistry() { return Collections.unmodifiableMap(agentRegistry); }

    /**
     * 修复E6: 应用关闭时清理线程池
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[调度器] 线程池已关闭");
    }
}
