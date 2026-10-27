package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentScheduler;
import com.happymouse.cryd.agent.core.AggregatorAgent;
import com.happymouse.cryd.agent.core.AgentReviewer;
import com.happymouse.cryd.agent.core.PipelineOrchestrator;
import com.happymouse.cryd.agent.core.TaskDecomposer;
import com.happymouse.cryd.agent.memory.AgentMemory;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;

/**
 * 智能体编排器 - 核心调度中心
 * @deprecated 使用 PipelineOrchestrator 替代，保留此类以兼容旧代码
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final PipelineOrchestrator pipeline;
    private final AgentScheduler scheduler;
    private final ProfileAnalystAgent profileAnalyst;
    private final KnowledgeManagerAgent knowledgeManager;
    private final PathPlannerAgent pathPlanner;
    private final TutorAgent tutorAgent;
    private final AdviceAgent adviceAgent;
    private final KnowledgeGraphAgent knowledgeGraphAgent;

    /** 修复R3: 从 application.yml 读取审核阈值，注入 AgentReviewer */
    @Value("${agent.pipeline.review-threshold:0.6}")
    private double reviewThreshold;

    public AgentOrchestrator(
            SparkClient sparkClient,
            ProfileAnalystAgent profileAnalyst,
            KnowledgeManagerAgent knowledgeManager,
            PathPlannerAgent pathPlanner,
            TutorAgent tutorAgent,
            AdviceAgent adviceAgent,
            KnowledgeGraphAgent knowledgeGraphAgent) {
        this.profileAnalyst = profileAnalyst;
        this.knowledgeManager = knowledgeManager;
        this.pathPlanner = pathPlanner;
        this.tutorAgent = tutorAgent;
        this.adviceAgent = adviceAgent;
        this.knowledgeGraphAgent = knowledgeGraphAgent;

        // 构建新管道
        TaskDecomposer decomposer = new TaskDecomposer(sparkClient);
        this.scheduler = new AgentScheduler(3, 120_000);

        // 为每个Agent创建独立的记忆实例并注入（修复S1: 记忆系统失效问题）
        // 注：Spring @Component 的 Agent 由 Spring 自动注入 SparkClient，此处仅为冗余保险
        profileAnalyst.setSparkClient(sparkClient);
        knowledgeManager.setSparkClient(sparkClient);
        pathPlanner.setSparkClient(sparkClient);
        tutorAgent.setSparkClient(sparkClient);
        adviceAgent.setSparkClient(sparkClient);
        knowledgeGraphAgent.setSparkClient(sparkClient);

        // 为每个Agent注入AgentMemory，使preProcess/postProcess能加载/保存对话历史
        profileAnalyst.setMemory(new AgentMemory(profileAnalyst.getName()));
        knowledgeManager.setMemory(new AgentMemory(knowledgeManager.getName()));
        pathPlanner.setMemory(new AgentMemory(pathPlanner.getName()));
        tutorAgent.setMemory(new AgentMemory(tutorAgent.getName()));
        adviceAgent.setMemory(new AgentMemory(adviceAgent.getName()));
        knowledgeGraphAgent.setMemory(new AgentMemory(knowledgeGraphAgent.getName()));

        // 注册所有Agent到调度器
        scheduler.register(profileAnalyst);
        scheduler.register(knowledgeManager);
        scheduler.register(pathPlanner);
        scheduler.register(tutorAgent);
        scheduler.register(adviceAgent);
        scheduler.register(knowledgeGraphAgent);

        AgentReviewer reviewer = new AgentReviewer(sparkClient);
        // 修复R3: 将配置中的 review-threshold 注入 reviewer
        reviewer.setReviewThreshold(reviewThreshold);
        AggregatorAgent aggregator = new AggregatorAgent(sparkClient);

        this.pipeline = new PipelineOrchestrator(decomposer, scheduler, reviewer, aggregator);
        this.pipeline.setAsyncProfileUpdater(() -> {}); // 画像更新由主流程触发

        log.info("AgentOrchestrator已升级为PipelineOrchestrator管道模式");
    }

    /**
     * 修复S3: 应用关闭时清理 scheduler 线程池，避免虚拟线程池泄漏
     * 原 scheduler 是局部变量，shutdown() 永远不会被调用
     */
    @PreDestroy
    public void shutdown() {
        log.info("AgentOrchestrator 关闭，清理 scheduler 线程池");
        scheduler.shutdown();
    }

    /**
     * @deprecated 使用 PipelineOrchestrator.process() 替代
     */
    public ChatResponse process(ChatRequest request) {
        log.info("收到消息(管道模式): studentId={}, message={}", request.getStudentId(), request.getMessage());

        // 先调用画像分析（异步不阻塞）
        try {
            profileAnalyst.analyze(request);
        } catch (Exception e) {
            log.warn("画像分析失败: {}", e.getMessage());
        }

        // 如果PipelineOrchestrator有任务拆解结果，走新管道
        // 否则降级到旧的关键词路由
        ChatResponse response = pipeline.process(request);

        // 如果是学习/练习类请求，更新进度（传入 response 以读取实际资源数量）
        if (response.isResourceGenerated()) {
            pathPlanner.updateProgress(request, response);
        }

        return response;
    }

    /**
     * 苏格拉底式引导模式 —— 直接调用 TutorAgent 使用引导 prompt，带上对话历史
     */
    public ChatResponse processWithSocraticMode(ChatRequest request, List<Map<String, String>> history) {
        log.info("苏格拉底式辅导: studentId={}, message={}, historySize={}", request.getStudentId(), request.getMessage(), history != null ? history.size() : 0);
        if (history != null && !history.isEmpty()) {
            return tutorAgent.answerWithHistory(request, history);
        }
        return process(request);
    }
}
