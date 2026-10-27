package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.agent.memory.AgentMemory;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体抽象基类 —— 模板方法模式。
 *
 * <p>职责：封装 LLM 调用、记忆管理、重试机制的通用逻辑，
 * 子类只需实现 {@link #doExecute} 核心业务。</p>
 *
 * <p>注入机制：子类为 Spring {@code @Component}，Spring 自动通过
 * {@link #setSparkClient}（带 @Autowired）注入 LLM 客户端。</p>
 *
 * <p>生命周期：{@link #execute(AgentContext)} 模板方法保证
 * 预处理→执行→后处理的标准流程和异常兜底。</p>
 */
public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected SparkClient sparkClient;
    protected AgentMemory memory;
    private final Map<String, Object> config = new ConcurrentHashMap<>();

    /**
     * 返回此Agent的唯一名称
     */
    public abstract String getName();

    /**
     * 返回此Agent的能力列表，供调度器匹配
     */
    public abstract List<AgentCapability> getCapabilities();

    /**
     * 返回此Agent的系统提示词
     */
    protected abstract String getSystemPrompt();

    /**
     * 核心执行逻辑 — 子类实现
     */
    protected abstract String doExecute(AgentContext context);

    /**
     * 模板方法：预处理 -> 执行 -> 后处理
     */
    public final AgentContext.AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String agentName = getName();
        log.info("[{}] 开始执行", agentName);

        try {
            preProcess(context);
            String output = doExecute(context);
            postProcess(context, output);

            AgentContext.AgentResult result = new AgentContext.AgentResult(agentName, output, true);
            result.setDurationMs(System.currentTimeMillis() - start);
            log.info("[{}] 执行完成，耗时{}ms", agentName, result.getDurationMs());
            return result;

        } catch (Exception e) {
            log.error("[{}] 执行失败: {}", agentName, e.getMessage());
            AgentContext.AgentResult result = new AgentContext.AgentResult(agentName, null, false);
            result.setDurationMs(System.currentTimeMillis() - start);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * 带重试的执行
     * 修复P5: 加入指数退避，避免LLM超时场景下连续重试全部超时
     * 修复L1: 审核失败时 context 中已存在 review_<agentName>，重试的 execute->doExecute->callLLM 会自动读取反馈
     */
    public AgentContext.AgentResult executeWithRetry(AgentContext context, int maxRetries) {
        AgentContext.AgentResult result = null;
        for (int i = 0; i <= maxRetries; i++) {
            result = execute(context);
            result.setRetryCount(i);
            if (result.isSuccess()) break;
            if (i < maxRetries) {
                // 指数退避: 1s, 2s, 4s ...
                long backoffMs = (1L << i) * 1000L;
                log.warn("[{}] 第{}次重试（等待{}ms）", getName(), i + 1, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 预处理 — 子类可覆写
     */
    protected void preProcess(AgentContext context) {
        // 默认从记忆加载对话历史
        if (memory != null) {
            List<Map<String, String>> history = memory.load(context.getStudentId());
            if (history != null && !history.isEmpty()) {
                context.setAttribute("memoryHistory", history);
            }
        }
    }

    /**
     * 后处理 — 子类可覆写
     */
    protected void postProcess(AgentContext context, String output) {
        // 默认保存到记忆
        if (memory != null && output != null) {
            memory.save(context.getStudentId(), "assistant", output);
        }
    }

    /**
     * 调用 LLM 的便捷方法（使用智能分配）
     * 修复L1: 自动注入上次审核反馈到 userMessage，使重试能基于反馈修正
     */
    protected String callLLM(AgentContext context, String userMessage, float temperature, int maxTokens) {
        String systemPrompt = buildAugmentedPrompt(context);
        userMessage = appendRetryFeedback(context, userMessage);
        List<Map<String, String>> history = context.getConversationHistory();
        if (history != null && !history.isEmpty()) {
            return sparkClient.chatWithHistory(systemPrompt, history, userMessage, temperature, maxTokens);
        }
        // 默认使用MEDIUM复杂度
        return sparkClient.smartChat(systemPrompt, userMessage,
                SparkClient.TaskComplexity.MEDIUM, maxTokens);
    }

    /**
     * 调用 LLM 的便捷方法（指定复杂度）
     * 修复L1: 自动注入上次审核反馈
     */
    protected String callLLM(AgentContext context, String userMessage, SparkClient.TaskComplexity complexity, int maxTokens) {
        String systemPrompt = buildAugmentedPrompt(context);
        userMessage = appendRetryFeedback(context, userMessage);
        return sparkClient.smartChat(systemPrompt, userMessage, complexity, maxTokens);
    }

    /**
     * 构建增强提示词 — 子类可覆写以注入 RAG 内容
     */
    protected String buildAugmentedPrompt(AgentContext context) {
        return getSystemPrompt();
    }

    /**
     * 修复L1: 若 context 中存在针对本 Agent 的审核反馈，追加到 userMessage
     * 使重试时 Agent 能知道上次的问题并修正，闭合审核反馈循环
     */
    protected String appendRetryFeedback(AgentContext context, String userMessage) {
        Object reviewAttr = context.getAttribute("review_" + getName());
        if (reviewAttr instanceof AgentReviewer.ReviewResult review && !review.issues.isEmpty()) {
            StringBuilder fb = new StringBuilder(userMessage);
            fb.append("\n\n【上次审核反馈，请修正以下问题】");
            for (String issue : review.issues) {
                fb.append("\n- ").append(issue);
            }
            if (review.suggestion != null && !review.suggestion.isEmpty()) {
                fb.append("\n改进建议：").append(review.suggestion);
            }
            return fb.toString();
        }
        return userMessage;
    }

    // --- Setter 注入 ---
    @Autowired
    public void setSparkClient(SparkClient sparkClient) { this.sparkClient = sparkClient; }
    public void setMemory(AgentMemory memory) { this.memory = memory; }
    public void setConfig(String key, Object value) { config.put(key, value); }
    public Object getConfig(String key) { return config.get(key); }
    public Map<String, Object> getConfig() { return config; }
}
