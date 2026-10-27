package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 管道编排器 — 新多智能体协作管道
 * 替换旧的 AgentOrchestrator（关键词路由）
 *
 * 管道流程:
 *   用户请求 -> TaskDecomposer(拆解) -> AgentScheduler(调度执行) -> AgentReviewer(审核) -> AggregatorAgent(整合)
 */
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final TaskDecomposer decomposer;
    private final AgentScheduler scheduler;
    private final AgentReviewer reviewer;
    private final AggregatorAgent aggregator;

    // 异步任务（画像更新等）
    private Runnable asyncProfileUpdater;

    // 管道事件回调 — 每个Agent执行前后触发，用于SSE推送
    @FunctionalInterface
    public interface PipelineEvent {
        void onEvent(String agentName, String status, String detail);
    }

    private PipelineEvent eventListener;

    public PipelineOrchestrator(
            TaskDecomposer decomposer,
            AgentScheduler scheduler,
            AgentReviewer reviewer,
            AggregatorAgent aggregator) {
        this.decomposer = decomposer;
        this.scheduler = scheduler;
        this.reviewer = reviewer;
        this.aggregator = aggregator;
    }

    public void setAsyncProfileUpdater(Runnable updater) {
        this.asyncProfileUpdater = updater;
    }

    public void setEventListener(PipelineEvent listener) {
        this.eventListener = listener;
    }

    private void emitEvent(String agentName, String status, String detail) {
        if (eventListener != null) {
            try { eventListener.onEvent(agentName, status, detail); }
            catch (Exception e) { log.warn("事件回调异常", e); }
        }
    }

    /**
     * 主入口：处理用户请求
     */
    public ChatResponse process(ChatRequest request) {
        log.info("[管道] 收到请求: studentId={}, msg={}", request.getStudentId(), request.getMessage());

        // 构建上下文
        AgentContext context = new AgentContext(
            request.getStudentId(),
            request.getMessage(),
            null // history由各Agent从memory加载
        );
        context.setAttribute("mode", request.getMode() != null ? request.getMode() : "fast");

        // 步骤1: 需求拆解
        emitEvent("需求拆解", "running", "分析用户意图...");
        decomposer.execute(context);
        int taskCount = context.getTasks().size();
        emitEvent("需求拆解", "done", "拆解为" + taskCount + "个子任务");

        // 步骤2: 调度执行所有子任务
        for (AgentContext.SubTask task : context.getTasks()) {
            emitEvent(mapTaskTypeToName(task.getType()), "running", task.getDescription());
        }
        List<AgentContext.AgentResult> results = scheduler.executeTaskGraph(context);
        for (AgentContext.SubTask task : context.getTasks()) {
            String status = "completed".equals(task.getStatus()) ? "done" : "failed";
            emitEvent(mapTaskTypeToName(task.getType()), status, task.getDescription());
        }

        // 步骤3: 审核
        emitEvent("质量审核", "running", "审核回答质量...");
        reviewer.execute(context);
        Boolean reviewPassed = context.getAttribute("reviewPassed", Boolean.class);
        emitEvent("质量审核", reviewPassed != null && reviewPassed ? "done" : "failed",
                  reviewPassed != null && reviewPassed ? "审核通过" : "审核不通过，重试中...");

        // 步骤4: 重试（如有需要）
        if (reviewPassed == null || !reviewPassed) {
            // 修复P1: scheduler.executeTaskGraph 已自动跳过 status=completed 的任务
            // 这里只需重置 failed 任务的 status 为 pending 以便重新执行
            int retryCount = 0;
            for (AgentContext.SubTask task : context.getTasks()) {
                if ("failed".equals(task.getStatus())) {
                    task.setStatus("pending"); // 重置以便重试
                    emitEvent(mapTaskTypeToName(task.getType()), "running", "重试: " + task.getDescription());
                    retryCount++;
                }
            }
            if (retryCount > 0) {
                List<AgentContext.AgentResult> retryResults = scheduler.executeTaskGraph(context);
                results.addAll(retryResults);
                // 修复R1: 二次审核收敛性问题
                //   原代码 reviewer.execute(context) 会重新审核所有 result（含首次已通过的），
                //   因 LLM 评分非确定性，首次通过的 Agent 可能在二次审核中失败，导致 allPassed
                //   永远为 false，重试失去意义。改为只审核重试任务对应的结果。
                reviewer.reviewRetriedOnly(context);
            }
        }

        // 步骤5: 整合输出
        emitEvent("内容整合", "running", "合并多Agent产出...");
        String finalOutput = aggregator.doExecute(context);
        // 修复O2: 整合器返回空内容时提供兜底提示
        if (finalOutput == null || finalOutput.isBlank()
                || "# 学习内容\n\n".equals(finalOutput.trim())) {
            finalOutput = "抱歉，本次未能生成有效内容，请稍后重试或换一种方式提问。";
        }
        emitEvent("内容整合", "done", "整合完成");

        // 步骤6: 异步更新画像
        if (asyncProfileUpdater != null) {
            CompletableFuture.runAsync(asyncProfileUpdater);
        }

        // 构建响应
        ChatResponse response = new ChatResponse();
        response.setMessage(finalOutput);

        // 找到实际执行的Agent名称
        for (AgentContext.AgentResult r : results) {
            if (r.isSuccess() && r.getAgentName() != null && !r.getAgentName().startsWith("Unmatched")) {
                response.setAgentName(r.getAgentName());
                break;
            }
        }
        if (response.getAgentName() == null) {
            response.setAgentName("多智能体协作组");
        }

        // 修复D3: 用任务类型判断是否生成资源，而非输出长度>200
        // 只有 course_design/question_gen/code 等资源类任务成功才算资源生成
        boolean hasResource = context.getTasks().stream().anyMatch(t ->
            "completed".equals(t.getStatus()) && isResourceType(t.getType()));
        response.setResourceGenerated(hasResource);

        log.info("[管道] 完成: agent={}, outputLen={}", response.getAgentName(),
            finalOutput != null ? finalOutput.length() : 0);

        return response;
    }

    /**
     * 任务类型映射为中文显示名
     */
    private String mapTaskTypeToName(String type) {
        if (type == null) return "未知Agent";
        return switch (type) {
            case "tutor_answer" -> "AI导师";
            case "course_design" -> "课程设计师";
            case "question_gen" -> "出题专家";
            case "knowledge_search" -> "知识库管理员";
            case "path_plan" -> "路径规划师";
            case "advice_generate" -> "学习顾问";
            case "knowledge_graph" -> "知识图谱师";
            case "review" -> "质量审核员";
            default -> type;
        };
    }

    /**
     * 修复D3: 判断任务类型是否为资源生成类
     * 仅 course_design/question_gen 产出实体学习资源，应触发进度更新
     * tutor_answer/advice_generate 等对话类不应被误判为资源生成
     */
    private boolean isResourceType(String type) {
        if (type == null) return false;
        return "course_design".equals(type) || "question_gen".equals(type) || "code".equals(type);
    }
}
