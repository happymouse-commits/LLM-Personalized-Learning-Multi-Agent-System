package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.dto.ResourceButton;
import com.happymouse.cryd.model.entity.ChatMessage;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.ChatMessageRepository;
import com.happymouse.cryd.repository.ErrorNotebookRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.WsPushService;
import com.happymouse.cryd.service.agent.AgentOrchestrator;
import com.happymouse.cryd.service.agent.AutoResourceGenerator;
import com.happymouse.cryd.service.agent.OnboardingAgent;
import com.happymouse.cryd.service.agent.TutorAgent;
import com.happymouse.cryd.service.agent.WeaknessAgent;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.service.rag.RagService;
import com.happymouse.cryd.service.rag.RagEventStore;
import com.happymouse.cryd.service.rag.VectorStore;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final AgentOrchestrator orchestrator;
    private final ChatMessageRepository chatMessageRepository;
    private final StudentRepository studentRepository;
    private final ErrorNotebookRepository errorNotebookRepository;

    private final SparkClient sparkClient;
    private final RagService ragService;
    private final com.happymouse.cryd.service.rag.HallucinationGuard hallucinationGuard;
    private final AutoResourceGenerator autoResourceGenerator;
    private final OnboardingAgent onboardingAgent;
    private final WsPushService wsPushService;
    private final Executor agentTaskExecutor;
    private final WeaknessAgent weaknessAgent;
    private final LearningResourceRepository resourceRepo;
    private final RagEventStore ragEventStore;

    /** 幻觉率阈值（百分比），超过此值触发自动重生成。来自 application.yml，可热调整 */
    @Value("${rag.hallucination.threshold:30.0}")
    private double hallucinationThreshold;

    /** 幻觉超阈值时的最大重生成次数 */
    @Value("${rag.hallucination.max-retries:2}")
    private int hallucinationMaxRetries;

    public ChatController(AgentOrchestrator orchestrator, ChatMessageRepository chatMessageRepository,
                          StudentRepository studentRepository, ErrorNotebookRepository errorNotebookRepository,
                          SparkClient sparkClient, RagService ragService,
                          com.happymouse.cryd.service.rag.HallucinationGuard hallucinationGuard,
                          AutoResourceGenerator autoResourceGenerator, OnboardingAgent onboardingAgent,
                          WsPushService wsPushService,
                          @Qualifier("agentTaskExecutor") Executor agentTaskExecutor,
                          WeaknessAgent weaknessAgent, LearningResourceRepository resourceRepo,
                          RagEventStore ragEventStore) {
        this.orchestrator = orchestrator;
        this.chatMessageRepository = chatMessageRepository;
        this.studentRepository = studentRepository;
        this.errorNotebookRepository = errorNotebookRepository;
        this.sparkClient = sparkClient;
        this.ragService = ragService;
        this.hallucinationGuard = hallucinationGuard;
        this.autoResourceGenerator = autoResourceGenerator;
        this.onboardingAgent = onboardingAgent;
        this.wsPushService = wsPushService;
        this.agentTaskExecutor = agentTaskExecutor;
        this.weaknessAgent = weaknessAgent;
        this.resourceRepo = resourceRepo;
        this.ragEventStore = ragEventStore;
    }

    /**
     * 获取引导状态（前端用于判断是否需要强制进入AI导学页）
     */
    @GetMapping("/onboarding/status")
    public Result<Map<String, Object>> onboardingStatus(@RequestParam Long studentId) {
        boolean needs = onboardingAgent.needsOnboarding(studentId);
        int step = needs ? onboardingAgent.inferCurrentStep(studentId) : 0;
        Map<String, Object> result = new HashMap<>();
        result.put("needsOnboarding", needs);
        result.put("currentStep", step);
        return Result.success(result);
    }

    /**
     * 测试资源检测（调试用）
     */
    @GetMapping("/test-detect")
    public Result<Map<String, Object>> testDetect(@RequestParam String msg) {
        String[] result = detectResourceRequest(msg);
        Map<String, Object> response = new HashMap<>();
        response.put("input", msg);
        response.put("result", result);
        response.put("mapSize", RESOURCE_TYPE_KEYWORDS.size());
        return Result.success(response);
    }

    @PostMapping("/send")
    public Result<ChatResponse> send(@RequestBody ChatRequest request) {
        log.info("收到聊天请求: studentId={}, msg={}, onboardingStep={}", request.getStudentId(), request.getMessage(), request.getOnboardingStep());

        Long studentId = request.getStudentId();

        // 演示模式（studentId=0）：自动创建/查找演示学生，走完整AI流程
        if (studentId == null || studentId == 0) {
            studentId = getOrCreateDemoStudent();
            request.setStudentId(studentId);
        }

        // ===== 导学模式：前端携带 onboardingStep 时走导学流程 =====
        log.info("[Chat] onboardingStep={}, 判断是否走导学流程", request.getOnboardingStep());
        if (request.getOnboardingStep() != null && request.getOnboardingStep() > 0) {
            log.info("[Chat] >>> 走导学流程");
            return handleOnboardingChat(request);
        }
        log.info("[Chat] >>> 走正常对话流程");

        // ===== 正常对话模式 =====
        ChatMessage userMsg = new ChatMessage();
        userMsg.setStudentId(studentId);
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        chatMessageRepository.save(userMsg);

        try {
            String msg = request.getMessage();

            // 检测是否为资源生成请求（优先级最高，跳过正常AI对话）
            String[] resourceRequest = detectResourceRequest(msg);
            log.info("[Chat] 资源检测结果: {}", resourceRequest != null ? Arrays.toString(resourceRequest) : "null");

            if (resourceRequest != null) {
                String resourceType = resourceRequest[0];
                String knowledgePoint = resourceRequest[1];
                String typeName = getResourceTypeName(resourceType);
                log.info("[Chat] 检测到资源生成请求: type={}, kp={}", resourceType, knowledgePoint);

                // 直接返回生成中提示
                ChatResponse resourceResponse = new ChatResponse();
                resourceResponse.setAgentName("AI导师");
                resourceResponse.setMessage("好的，正在为你生成「" + knowledgePoint + "」的" + typeName + "，请稍候...");

                // 保存AI消息
                ChatMessage aiMsg = new ChatMessage();
                aiMsg.setStudentId(request.getStudentId());
                aiMsg.setRole("assistant");
                aiMsg.setContent(resourceResponse.getMessage());
                aiMsg.setAgentName("AI导师");
                chatMessageRepository.save(aiMsg);

                // 异步生成资源
                final Long reqStudentId = studentId;
                final String reqType = resourceType;
                final String reqKp = knowledgePoint;
                agentTaskExecutor.execute(() -> {
                    try {
                        List<ResourceButton> buttons = generateResourceButtons(reqStudentId, reqType, reqKp);
                        log.info("资源生成完成: count={}", buttons.size());
                        // WebSocket推送刷新今日推荐
                        wsPushService.pushResourceUpdate(reqStudentId, buttons);
                    } catch (Exception e) {
                        log.warn("资源生成异常: {}", e.getMessage());
                    }
                });

                resourceResponse.setProfileUpdated(true);
                return Result.success(resourceResponse);
            }

            // 短消息且非复杂问题 → 快速通道，跳过 PipelineOrchestrator
            boolean isShort = msg != null && msg.length() < 30;
            boolean isComplex = msg != null && (msg.contains("代码") || msg.contains("编程")
                    || msg.contains("算法") || msg.contains("指针") || msg.contains("题目"));
            // 苏格拉底式引导：概念/原理/调试类问题优先用引导式回答
            boolean useSocratic = TutorAgent.isSocraticCandidate(msg);

            // 检查是否在回答苏格拉底式问题（用户说不知道、不明白等）
            if (!useSocratic && msg != null) {
                String lowerMsg = msg.toLowerCase();
                String[] socraticAnswers = {"不知道", "不清楚", "不明白", "不懂", "没学过", "忘了", "不确定", "不会", "没印象"};
                boolean isSocraticAnswer = Arrays.stream(socraticAnswers).anyMatch(lowerMsg::contains);

                if (isSocraticAnswer) {
                    // 检查上一条AI消息是否是苏格拉底式引导（只取最近5条，无需全量加载）
                    List<ChatMessage> recentMessages = chatMessageRepository
                            .findTop20ByStudentIdOrderByCreatedAtDesc(studentId)
                            .stream()
                            .limit(5)
                            .toList();

                    boolean lastAiWasSocratic = recentMessages.stream()
                            .filter(m -> "assistant".equals(m.getRole()))
                            .findFirst()
                            .map(m -> m.getContent() != null && (
                                m.getContent().contains("想先问你") ||
                                m.getContent().contains("你觉得") ||
                                m.getContent().contains("先想想") ||
                                m.getContent().contains("告诉我你的想法")
                            ))
                            .orElse(false);

                    if (lastAiWasSocratic) {
                        useSocratic = true; // 继续用苏格拉底式引导
                    }
                }
            }

            ChatResponse response;
            // 加载最近对话历史，让AI有上下文记忆
            List<Map<String, String>> history = buildConversationHistory(request.getStudentId());
            if (isShort && !isComplex && !useSocratic) {
                // 简单问答使用LOW复杂度（Flash模型快速响应）
                String systemPrompt = "你是C语言AI导师，回答详细友好，用Markdown格式。学生水平：大一。回答要完整详细，包含：1.概念解释 2.简单例子 3.注意事项。字数控制在200-400字。";
                String aiReply;
                if (!history.isEmpty()) {
                    aiReply = sparkClient.chatWithHistory(systemPrompt, history, msg, 0.7f, 1024);
                } else {
                    aiReply = sparkClient.smartChat(systemPrompt, msg, SparkClient.TaskComplexity.MEDIUM, 1024);
                }
                response = new ChatResponse();
                response.setAgentName("AI导师");
                response.setMessage(aiReply);
            } else if (useSocratic) {
                // 苏格拉底式引导：通过 TutorAgent 使用引导 prompt，带上对话历史
                ChatRequest socraticReq = new ChatRequest();
                socraticReq.setStudentId(request.getStudentId());
                socraticReq.setMessage(msg);
                socraticReq.setOnboardingStep(request.getOnboardingStep());
                response = orchestrator.processWithSocraticMode(socraticReq, history);
            } else {
                response = orchestrator.process(request);
            }

            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setStudentId(request.getStudentId());
            aiMsg.setRole("assistant");
            aiMsg.setContent(response.getMessage());
            aiMsg.setAgentName(response.getAgentName());
            chatMessageRepository.save(aiMsg);

            // 对话后自动分析 & 资源生成（异步）
            final Long finalStudentId = studentId;
            agentTaskExecutor.execute(() -> {
                try { postChatAutoPipeline(finalStudentId, msg); }
                catch (Exception e) { log.warn("Post-chat异常: studentId={}", finalStudentId, e); }
            });

            response.setProfileUpdated(true);
            return Result.success(response);

        } catch (Exception e) {
            log.error("AI处理失败", e);
            ChatResponse fallback = new ChatResponse();
            fallback.setAgentName("系统");
            fallback.setMessage("抱歉，AI导学暂时不可用，请稍后再试");
            return Result.success(fallback);
        }
    }

    /**
     * SSE 流式对话接口 — 实时逐 token 推送，前端无需假打字机效果
     * 仅在非引导模式下使用；引导模式仍走 /send 接口
     */
    @PostMapping("/stream")
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟超时
        String msg = request.getMessage();

        // 演示模式（studentId=0）：自动创建/查找演示学生
        Long studentId = request.getStudentId();
        if (studentId == null || studentId == 0) {
            studentId = getOrCreateDemoStudent();
            request.setStudentId(studentId);
        }
        final Long finalStudentId = studentId;

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setStudentId(finalStudentId);
        userMsg.setRole("user");
        userMsg.setContent(msg);
        chatMessageRepository.save(userMsg);

        // 判断是否走管道
        boolean isComplex = msg != null && (msg.contains("代码") || msg.contains("编程")
                || msg.contains("算法") || msg.contains("指针") || msg.contains("题目")
                || msg.contains("解释") || msg.contains("什么是") || msg.contains("为什么")
                || msg.contains("讲讲") || msg.contains("学习"));
        boolean useSocratic = TutorAgent.isSocraticCandidate(msg);

        // 检测是否为资源生成请求（场景4：用户主动请求）
        String[] resourceRequest = detectResourceRequest(msg);

        agentTaskExecutor.execute(() -> {
            try {
                if (resourceRequest != null) {
                    // 场景4：资源生成请求：同步生成资源后返回按钮
                    processResourceRequest(request, emitter, msg, finalStudentId, resourceRequest);
                } else if (isComplex || useSocratic) {
                    // 场景3：复杂问题或苏格拉底式引导，AI回复后检测是否需要资源
                    processWithResourceDetection(request, emitter, msg, finalStudentId);
                } else {
                    processSimpleStream(request, emitter, msg, finalStudentId);
                }
            } catch (Exception e) {
                log.error("SSE对话失败: studentId={}", finalStudentId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("AI回复失败，请稍后重试"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 资源生成请求：生成资源并返回按钮
     */
    private void processResourceRequest(ChatRequest request, SseEmitter emitter,
                                         String msg, Long studentId, String[] resourceRequest) throws IOException {
        String resourceType = resourceRequest[0];
        String knowledgePoint = resourceRequest[1];
        String typeName = getResourceTypeName(resourceType);

        // 告知用户正在生成，然后关闭SSE
        String thinkingMsg = "好的，正在为你生成「" + knowledgePoint + "」的" + typeName + "，生成后会在「今日推荐」中展示~";
        emitter.send(SseEmitter.event().name("chunk").data(thinkingMsg));
        emitter.send(SseEmitter.event().name("done").data(thinkingMsg));
        emitter.complete();

        // 保存这条提示消息
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setStudentId(studentId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(thinkingMsg);
        aiMsg.setAgentName("AI导师");
        chatMessageRepository.save(aiMsg);

        // 异步生成资源 — 静默放到今日推荐，不发额外聊天消息
        final Long reqStudentId = studentId;
        final String reqType = resourceType;
        final String reqKp = knowledgePoint;
        CompletableFuture.runAsync(() -> {
            try {
                generateResourceButtons(reqStudentId, reqType, reqKp);
                log.info("资源生成完成: type={}, kp={}", reqType, reqKp);
                // WebSocket推送刷新今日推荐
                wsPushService.pushResourceUpdate(reqStudentId);
            } catch (Exception e) {
                log.error("资源生成失败: type={}, kp={}", reqType, reqKp, e);
            }
        }, agentTaskExecutor);
    }

    /**
     * 场景3：AI回复后检测是否需要资源（DeepSeek智能分析）
     * 流程：AI回复 → 检测用户是否"真不会" → 询问用户 → 用户同意则生成资源
     */
    private void processWithResourceDetection(ChatRequest request, SseEmitter emitter,
                                               String msg, Long studentId) throws IOException {
        doStreamChat(emitter, msg, studentId, true, request);
    }

    /**
     * 简单问题：直接流式LLM，不走管道
     */
    private void processSimpleStream(ChatRequest request, SseEmitter emitter,
                                      String msg, Long studentId) throws IOException {
        doStreamChat(emitter, msg, studentId, false, request);
    }

    /**
     * 公共流式对话逻辑 — RAG增强 + 流式推送 + 幻觉率校验 + 超阈值自动重生成 + 消息保存 + 后处理
     * @param runPostPipeline 是否执行对话后自动管道
     * @param request 前端请求（含 extraContext / systemPrompt / history，编程题页面传入）
     */
    @SuppressWarnings("unchecked")
    private void doStreamChat(SseEmitter emitter, String msg, Long studentId, boolean runPostPipeline,
                              ChatRequest request) throws IOException {
        Map<String, Object> ragResult = buildRagAugmentedSystemPromptWithFragments(msg);
        String ragPrompt = (String) ragResult.get("prompt");

        // ===== 构建最终 system prompt =====
        StringBuilder promptBuilder = new StringBuilder();
        boolean hasFrontendPrompt = request != null && request.getSystemPrompt() != null
                && !request.getSystemPrompt().isBlank();
        boolean isAutoMode = request != null && "auto".equals(request.getMode());
        boolean hasExtraContext = request != null && request.getExtraContext() != null
                && !request.getExtraContext().isBlank();

        if (hasFrontendPrompt) {
            // 编程题页面：以前端传入的自适应 prompt 为基础
            promptBuilder.append(request.getSystemPrompt());

            // auto 模式：根据问题类型选择性追加苏格拉底/直答指令
            if (isAutoMode && hasExtraContext) {
                if (TutorAgent.isSocraticCandidate(msg)) {
                    // 苏格拉底式：学生寻求思路引导，追加引导指令
                    promptBuilder.append("\n\n【本次交互策略：苏格拉底式引导】")
                        .append("学生正在寻求解题思路，请通过2-3个精炼的思考方向引导学生自己推导，")
                        .append("不直接给完整代码或答案。如果学生卡住，给出下一步具体思考方向。");
                } else {
                    // 直答模式：学生问概念/报错/语法，直接回答
                    promptBuilder.append("\n\n【本次交互策略：直接答疑】")
                        .append("请直接回答学生的问题，给出清晰完整的解释或具体修改建议。");
                }
            }

            // 追加 RAG 知识库检索结果（增强准确性）
            if (ragPrompt != null && !ragPrompt.isBlank()) {
                promptBuilder.append("\n\n").append(ragPrompt);
            }
        } else {
            // 首页 AI 助手：用后端 RAG + 苏格拉底判断
            promptBuilder.append(TutorAgent.isSocraticCandidate(msg)
                    ? buildSocraticAugmentedPrompt(ragPrompt)
                    : ragPrompt);
        }

        // 追加前端构建的编程题上下文（题目描述 + 题解 + 学生代码等）
        if (request != null && request.getExtraContext() != null && !request.getExtraContext().isBlank()) {
            promptBuilder.append("\n\n【当前编程题上下文】\n").append(request.getExtraContext());
            System.out.println("[doStreamChat] extraContext received, length=" + request.getExtraContext().length()
                    + ", hasSystemPrompt=" + hasFrontendPrompt
                    + ", mode=" + request.getMode()
                    + ", isSocratic=" + TutorAgent.isSocraticCandidate(msg));
        } else {
            System.out.println("[doStreamChat] extraContext is NULL or BLANK! hasFrontendPrompt=" + hasFrontendPrompt);
        }

        final String systemPrompt = promptBuilder.toString();
        System.out.println("[doStreamChat] final systemPrompt length=" + systemPrompt.length());
        List<RagService.KnowledgeFragment> ragFragments =
                (List<RagService.KnowledgeFragment>) ragResult.get("fragments");

        // 前端传入的对话历史
        final List<Map<String, String>> history = (request != null) ? request.getHistory() : null;
        final boolean hasHistory = history != null && !history.isEmpty();

        // ===== 第一轮生成 =====
        final StringBuilder fullContent = new StringBuilder();
        Consumer<String> onChunk = chunk -> {
            fullContent.append(chunk);
            try {
                emitter.send(SseEmitter.event().name("chunk").data(chunk));
            } catch (IOException e) {
                throw new RuntimeException("Client disconnected", e);
            }
        };

        if (hasHistory) {
            sparkClient.smartChatStreamWithHistory(systemPrompt, msg, SparkClient.TaskComplexity.MEDIUM,
                    onChunk, history);
        } else {
            sparkClient.smartChatStream(systemPrompt, msg, SparkClient.TaskComplexity.MEDIUM, onChunk);
        }

        String content = fullContent.toString();

        // ===== 幻觉率校验 + 超阈值自动重生成（带问题反馈 + 事件埋点）=====
        // 当 max-retries=0 时跳过自动重生成（回退功能已禁用），仅记录幻觉率
        boolean autoRegenerated = false;
        if (ragFragments != null && !ragFragments.isEmpty() && hallucinationMaxRetries > 0) {
            HallucinationAssessment assessment = assessHallucination(content, ragFragments);
            double hallucinationRate = assessment.rate;
            int retriesLeft = hallucinationMaxRetries;

            while (hallucinationRate > hallucinationThreshold && retriesLeft > 0) {
                int attemptNum = hallucinationMaxRetries - retriesLeft + 1;
                autoRegenerated = true;
                log.info("幻觉率 {}% 超过阈值 {}%，启动第 {} 次重生成（问题：{}）",
                        String.format("%.1f", hallucinationRate), hallucinationThreshold,
                        attemptNum, assessment.reason);

                // 通知前端：正在重新生成
                emitter.send(SseEmitter.event().name("regenerating").data(
                    "检测到回答可靠性不足（幻觉率 " + String.format("%.1f", hallucinationRate) +
                    "%），正在基于知识库重新生成更准确的回答…"));

                // 记录幻觉事故（含修正前内容），用于监控面板 diff 对比
                ragEventStore.recordHallucinationRetry(msg, attemptNum,
                        hallucinationRate / 100.0, assessment.reason, content, null);
                ragEventStore.recordFactCheckFail(msg, ragFragments.size(),
                        hallucinationRate / 100.0, assessment.reason);

                // 构建带问题反馈的精准重生成提示词：把具体不一致点喂给模型，而非泛泛要求"更严格"
                String stricterPrompt = systemPrompt
                    + "\n\n【重要提醒】你的上一次回答经过事实校验后发现幻觉率过高（"
                    + String.format("%.1f", hallucinationRate) + "%），存在以下具体问题：\n"
                    + assessment.reason + "\n"
                    + "请严格基于上面的知识库参考资料重新回答，要求：\n"
                    + "1. 只使用知识库资料中的信息，不要自行补充任何资料外内容\n"
                    + "2. 针对上述问题逐条修正，每个论点都必须有对应的参考资料依据\n"
                    + "3. 如果知识库资料不足以完整回答，请明确说明哪些部分有依据、哪些不确定\n"
                    + "4. 保持简洁，100字以内";

                // 重新生成
                String beforeContent = content;
                fullContent.setLength(0);
                if (hasHistory) {
                    sparkClient.smartChatStreamWithHistory(stricterPrompt, msg, SparkClient.TaskComplexity.MEDIUM,
                        onChunk, history);
                } else {
                    sparkClient.smartChatStream(stricterPrompt, msg, SparkClient.TaskComplexity.MEDIUM,
                        onChunk);
                }

                content = fullContent.toString();
                // 回填修正后内容，便于监控面板对比修正前后差异
                ragEventStore.updateLastIncidentAfterContent(content);
                assessment = assessHallucination(content, ragFragments);
                hallucinationRate = assessment.rate;
                retriesLeft--;
                // 防御：避免相同内容死循环（修正前后完全一致则提前退出）
                if (content.equals(beforeContent)) {
                    log.warn("重生成内容与上一版完全一致，提前结束重试避免死循环");
                    break;
                }
            }

            log.info("最终幻觉率: {}%（阈值 {}%，{}）",
                    String.format("%.1f", hallucinationRate), hallucinationThreshold,
                    autoRegenerated ? "已重生成" : "无需重生成");
        }

        // ===== 保存消息 =====
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setStudentId(studentId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(content);
        aiMsg.setAgentName("AI导师");
        chatMessageRepository.save(aiMsg);

        if (ragFragments != null && !ragFragments.isEmpty()) {
            sendRagMetadata(emitter, ragFragments, content, autoRegenerated);
        }

        emitter.send(SseEmitter.event().name("done").data(content));
        emitter.complete();

        if (runPostPipeline) {
            final String userMsgContent = msg;
            final Long finalStudentId = studentId;
            agentTaskExecutor.execute(() -> {
                try { postChatAutoPipeline(finalStudentId, userMsgContent); }
                catch (Exception e) { log.warn("Post-chat异常", e); }
            });
        }
    }

    /**
     * 幻觉率综合评估结果。
     * @param rate                 幻觉率（百分比 0-100，= 100 - 综合可信度）
     * @param reason               事实校验发现的具体问题（用于重生成提示词反馈 & 前端展示）
     * @param factCheckConfidence 事实校验置信度（0-1）
     * @param factCheckPassed      事实校验是否通过
     * @param avgSimilarity        平均检索相似度（0-1）
     */
    private record HallucinationAssessment(double rate, String reason,
                                           double factCheckConfidence, boolean factCheckPassed,
                                           double avgSimilarity) {}

    /**
     * 综合评估生成内容的幻觉率与具体问题。
     * 综合可信度 = 60% 事实校验置信度 + 40% 平均检索相似度；幻觉率 = 100 - 综合可信度。
     * 同时返回事实校验的 reason，用于：① 喂回重生成提示词做针对性修正；② 前端展示。
     */
    private HallucinationAssessment assessHallucination(String generatedContent, List<RagService.KnowledgeFragment> ragFragments) {
        double avgSimilarity = ragFragments.stream()
            .mapToDouble(f -> f.similarity)
            .average()
            .orElse(0.0);

        double factCheckConfidence = 0.7;
        String reason = "未执行深度校验";
        boolean factCheckPassed = true;
        try {
            var checkResult = hallucinationGuard.factCheck(generatedContent, ragFragments);
            factCheckConfidence = checkResult.confidence;
            reason = checkResult.reason;
            factCheckPassed = checkResult.passed;
        } catch (Exception e) {
            log.debug("事实校验计算异常，使用默认置信度", e);
            reason = "事实校验异常：" + e.getMessage();
        }

        double overallReliability = factCheckConfidence * 0.6 + avgSimilarity * 0.4;
        double rate = Math.max(0, Math.min(100, (1.0 - overallReliability) * 100));
        return new HallucinationAssessment(rate, reason, factCheckConfidence, factCheckPassed, avgSimilarity);
    }

    /**
     * 将 RAG 检索结果 + 幻觉率指标 序列化为 JSON 并通过 SSE 发送给前端
     * @param autoRegenerated 本次回答是否触发过自动重生成（由 doStreamChat 传入）
     */
    private void sendRagMetadata(SseEmitter emitter, List<RagService.KnowledgeFragment> ragFragments,
                                 String generatedContent, boolean autoRegenerated) {
        try {
            // 复用评估逻辑，避免重复事实校验调用
            HallucinationAssessment assessment = assessHallucination(generatedContent, ragFragments);
            double avgSimilarity = assessment.avgSimilarity;
            double factCheckConfidence = assessment.factCheckConfidence;
            String factCheckReason = assessment.reason;
            boolean factCheckPassed = assessment.factCheckPassed;
            double overallReliability = factCheckConfidence * 0.6 + avgSimilarity * 0.4;
            double hallucinationRate = assessment.rate;

            // 可靠性等级
            String reliabilityLevel;
            if (overallReliability >= 0.85) reliabilityLevel = "high";
            else if (overallReliability >= 0.65) reliabilityLevel = "medium";
            else reliabilityLevel = "low";

            // 构建片段数组
            com.alibaba.fastjson2.JSONArray fragmentsJson = new com.alibaba.fastjson2.JSONArray();
            for (RagService.KnowledgeFragment f : ragFragments) {
                com.alibaba.fastjson2.JSONObject obj = new com.alibaba.fastjson2.JSONObject();
                obj.put("content", f.content);
                obj.put("source", f.source != null ? f.source : "");
                obj.put("score", f.similarity);
                obj.put("knowledgePoint", f.tags != null ? f.tags : "");
                fragmentsJson.add(obj);
            }

            // 构建完整RAG元数据
            com.alibaba.fastjson2.JSONObject ragMeta = new com.alibaba.fastjson2.JSONObject();
            ragMeta.put("fragments", fragmentsJson);
            ragMeta.put("hallucinationRate", Math.round(hallucinationRate * 10) / 10.0);
            ragMeta.put("reliability", Math.round(overallReliability * 1000) / 10.0);
            ragMeta.put("factCheckConfidence", Math.round(factCheckConfidence * 1000) / 10.0);
            ragMeta.put("avgSimilarity", Math.round(avgSimilarity * 1000) / 10.0);
            ragMeta.put("reliabilityLevel", reliabilityLevel);
            ragMeta.put("factCheckPassed", factCheckPassed);
            ragMeta.put("factCheckReason", factCheckReason);
            ragMeta.put("fragmentCount", ragFragments.size());
            ragMeta.put("hallucinationThreshold", hallucinationThreshold);
            ragMeta.put("autoRegenerated", autoRegenerated);

            emitter.send(SseEmitter.event().name("rag").data(ragMeta.toJSONString()));
        } catch (Exception e) {
            log.warn("发送RAG元数据失败", e);
        }
    }

    /**
     * 新生引导对话处理 —— AI通过对话了解学生信息
     */
    private Result<ChatResponse> handleOnboardingChat(ChatRequest request) {
        Long sysUserId = request.getStudentId();
        String userMessage = request.getMessage();
        log.info("[导学-chat] >>> 进入handleOnboardingChat, studentId={}, onboardingStep={}", sysUserId, request.getOnboardingStep());

        // 确保 Student 实体存在（用 SysUser ID 构建 username）
        studentRepository.findBySysUserId(sysUserId).orElseGet(() -> {
            Student s = new Student();
            s.setUsername("student_" + sysUserId);
            s.setSysUserId(sysUserId);
            s.setKnowledgeLevel(0);
            s.setProgress(0);
            s.setOnboarded(false);
            return studentRepository.save(s);
        });

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setStudentId(sysUserId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        chatMessageRepository.save(userMsg);

        // 推断当前步骤：优先用前端传入的 onboardingStep，否则从消息数推断
        int currentStep;
        if (request.getOnboardingStep() != null && request.getOnboardingStep() > 0) {
            currentStep = request.getOnboardingStep();
        } else {
            currentStep = onboardingAgent.inferCurrentStep(sysUserId);
        }
        log.info("[导学] studentId={}, onboardingStep={}, currentStep={}, message={}", sysUserId, request.getOnboardingStep(), currentStep, userMessage);

        // 如果没有用户消息内容（首次进入），直接返回第1步
        ChatResponse response;
        if (userMessage == null || userMessage.isBlank()) {
            response = onboardingAgent.startOnboarding(sysUserId);
        } else {
            response = onboardingAgent.processOnboardingStep(sysUserId, userMessage, currentStep);
        }

        // 保存AI消息
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setStudentId(sysUserId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(response.getMessage());
        aiMsg.setAgentName(response.getAgentName());
        chatMessageRepository.save(aiMsg);

        log.info("[导学] 回复: onboardingStep={}, onboardingComplete={}, message={}", response.getOnboardingStep(), response.isOnboardingComplete(), response.getMessage());

        // 引导完成后异步自动生成第一批资源
        if (response.isOnboardingComplete()) {
            final Long sid = sysUserId;
            agentTaskExecutor.execute(() -> {
                try {
                    log.info("新生引导完成，自动生成第一批资源: sysUserId={}", sid);
                    autoResourceGenerator.autoGenerate(sid);
                } catch (Exception e) {
                    log.warn("首批资源生成失败", e);
                }
            });
        }

        return Result.success(response);
    }

    /**
     * 对话后自动管道：检查是否需要自动生成资源 → 如果需要则调用 AutoResourceGenerator
     */
    private void postChatAutoPipeline(Long studentId, String userMessage) {
        // 1. 从最新消息中提取知识点
        Set<String> kps = autoResourceGenerator.extractKnowledgePoints(userMessage);
        log.info("Post-chat管道: studentId={}, 提取知识点={}", studentId, kps);

        // 1.5 课程资源推荐（由VideoAgent在autoGenerate中自动处理）
        // 课程推荐已集成到AutoResourceGenerator中，无需单独调用

        // 2.0 WebSocket 推送 — 通知前端刷新资源/画像
        wsPushService.pushResourceUpdate(studentId);
        wsPushService.pushProfileUpdate(studentId);

        // 3. 检测是否需要自动生成资源
        if (autoResourceGenerator.shouldAutoGenerate(studentId)) {
            log.info("Post-chat管道: 触发自动资源生成 studentId={}", studentId);
            List<LearningResource> generated = autoResourceGenerator.autoGenerate(studentId);
            log.info("Post-chat管道: 自动生成完成 studentId={}, count={}", studentId, generated.size());

            // WebSocket 推送通知
            wsPushService.pushResourceUpdate(studentId);
            wsPushService.pushProfileUpdate(studentId);
        } else {
            log.debug("Post-chat管道: 跳过自动生成 studentId={}", studentId);
        }

        // 4. 错题辅导逻辑：检测用户是否有相关错题，生成针对性资源
        try {
            handleErrorCoaching(studentId, userMessage);
        } catch (Exception e) {
            log.warn("错题辅导处理异常: studentId={}", studentId, e);
        }
    }

    /**
     * 错题辅导处理：根据用户消息检测相关错题，生成针对性资源
     */
    private void handleErrorCoaching(Long studentId, String userMessage) {
        // 查询用户最近的错题（active状态）
        List<ErrorNotebook> recentErrors = errorNotebookRepository
                .findByStudentIdAndStatus(studentId, "active");

        if (recentErrors.isEmpty()) {
            log.debug("错题辅导: 学生{}没有活跃错题，跳过", studentId);
            return;
        }

        // 分析用户消息是否与错题相关
        String lowerMsg = userMessage.toLowerCase();
        boolean isRelatedToErrors = false;
        ErrorNotebook relatedError = null;

        for (ErrorNotebook error : recentErrors) {
            if (error.getKnowledgePoint() != null &&
                lowerMsg.contains(error.getKnowledgePoint().toLowerCase())) {
                isRelatedToErrors = true;
                relatedError = error;
                break;
            }
        }

        // 如果用户消息与错题相关，或者用户主动询问学习问题
        if (isRelatedToErrors || isLearningQuestion(userMessage)) {
            log.info("错题辅导: 检测到相关错题，生成针对性资源 studentId={}, knowledgePoint={}",
                    studentId, relatedError != null ? relatedError.getKnowledgePoint() : "综合问题");

            // 获取最相关的知识点
            String knowledgePoint = relatedError != null ? relatedError.getKnowledgePoint() : "C语言基础";
            String errorType = relatedError != null ? relatedError.getErrorType() : "concept";
            int errorCount = relatedError != null ? (relatedError.getWrongCount() != null ? relatedError.getWrongCount() : 1) : 1;

            // 生成针对性资源
            List<LearningResource> generated = new ArrayList<>();
            String chapterName = autoResourceGenerator.inferChapter(knowledgePoint);
            LearningResource lr = weaknessAgent.generate(chapterName, knowledgePoint, "50", knowledgePoint);
            if (lr != null) {
                lr.setStudentId(studentId);
                lr.setGeneratedBy("error-coaching");
                lr.setCreatedAt(LocalDateTime.now());
                generated.add(resourceRepo.save(lr));
            }

            if (!generated.isEmpty()) {
                log.info("错题辅导: 生成{}篇针对性资源 studentId={}", generated.size(), studentId);

                // WebSocket推送资源更新
                wsPushService.pushResourceUpdate(studentId);
            }
        }
    }

    /**
     * 判断用户消息是否为学习相关问题
     */
    private boolean isLearningQuestion(String message) {
        if (message == null || message.isBlank()) return false;

        String lowerMsg = message.toLowerCase();
        String[] learningKeywords = {
            "不懂", "不明白", "不会", "什么是", "怎么", "为什么",
            "解释", "讲解", "帮助", "学习", "练习", "巩固"
        };

        for (String keyword : learningKeywords) {
            if (lowerMsg.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取或创建演示学生 —— 演示模式使用固定sysUserId=1，走完整AI流程
     */
    private Long getOrCreateDemoStudent() {
        // 演示模式使用保留ID=-1，避免与真实用户冲突
        final Long demoSysUserId = -1L;
        // 优先用 sysUserId 查，查不到再用 username 查（兼容已有数据）
        Student demo = studentRepository.findBySysUserId(demoSysUserId).orElse(null);
        if (demo == null) {
            demo = studentRepository.findByUsername("demo_student").orElse(null);
        }
        if (demo == null) {
            demo = new Student();
            demo.setUsername("demo_student");
            demo.setSysUserId(demoSysUserId);
            demo.setKnowledgeLevel(30);
            demo.setProgress(0);
            demo.setOnboarded(false);
            demo = studentRepository.save(demo);
            log.info("创建演示学生: id={}, sysUserId={}", demo.getId(), demoSysUserId);
        } else {
            // 确保 sysUserId 正确
            if (demo.getSysUserId() == null || demo.getSysUserId() == 0) {
                demo.setSysUserId(demoSysUserId);
                studentRepository.save(demo);
            }
            log.info("复用已有演示学生: id={}, sysUserId={}", demo.getId(), demo.getSysUserId());
        }
        return demoSysUserId;
    }

    /**
     * 未登录用户的语音理解 —— Flash模型快速回复，加长内容配合TTS冷却
     */
    private Result<ChatResponse> handleGuestVoiceUnderstanding(ChatRequest request) {
        String userMessage = request.getMessage();
        log.info("访客语音理解: msg={}", userMessage);

        try {
            String systemPrompt = "你是一个C语言课程的导学助手。用户通过语音回答问卷问题。"
                + "\n\n规则："
                + "\n1. 理解用户的语音内容，给出友好回应"
                + "\n2. 回复要有过渡感，比如：'好的，明白了~' 或 '收到，我记下了~'"
                + "\n3. 回复长度控制在20-30字，不要太短也不要太长"
                + "\n4. 语气亲切自然，像朋友聊天"
                + "\n5. 不需要解释太多，用户只是在做问卷";

            // 用 Flash 模型（LOW复杂度）+ 适当maxTokens
            String aiReply = sparkClient.smartChat(systemPrompt, userMessage, SparkClient.TaskComplexity.LOW, 256);

            ChatResponse response = new ChatResponse();
            response.setAgentName("AI导师");
            response.setMessage(aiReply);
            return Result.success(response);
        } catch (Exception e) {
            log.error("访客语音理解失败", e);
            ChatResponse fallback = new ChatResponse();
            fallback.setAgentName("AI导师");
            fallback.setMessage("好的，收到~");
            return Result.success(fallback);
        }
    }

    /**
     * 构建对话历史 —— 取最近10条消息作为上下文传给LLM
     */
    private List<Map<String, String>> buildConversationHistory(Long studentId) {
        List<ChatMessage> recent = chatMessageRepository
                .findTop20ByStudentIdOrderByCreatedAtDesc(studentId);
        // 倒序取最近10条，然后反转为时间正序
        List<Map<String, String>> history = new ArrayList<>();
        int start = Math.max(0, recent.size() - 10);
        for (int i = recent.size() - 1; i >= start; i--) {
            ChatMessage m = recent.get(i);
            // Map.of 不允许 null 值，role 为空时默认 "user"
            String role = m.getRole() != null ? m.getRole() : "user";
            history.add(Map.of("role", role, "content", m.getContent() != null ? m.getContent() : ""));
        }
        return history;
    }

    @GetMapping("/history/{studentId}")
    public Result<List<ChatMessage>> history(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return Result.success(chatMessageRepository.findByStudentIdOrderByCreatedAtAsc(studentId, pageable).getContent());
    }

    /**
     * 构建带 RAG 知识库上下文增强的系统提示词
     */
    private String buildRagAugmentedSystemPrompt(String userMessage) {
        return (String) buildRagAugmentedSystemPromptWithFragments(userMessage).get("prompt");
    }

    /**
     * 构建RAG提示词并返回检索到的片段（供前端展示RAG面板）
     */
    private Map<String, Object> buildRagAugmentedSystemPromptWithFragments(String userMessage) {
        String basePrompt = "你是C语言AI导师，学生水平：大一。回答要求：简洁精炼，100-200字以内。用Markdown格式，重点加粗，代码用```c包裹。只讲核心知识点，不要展开解释。";
        List<RagService.KnowledgeFragment> fragments = new ArrayList<>();
        try {
            fragments = ragService.retrieveRelevant(userMessage, 3);
            if (fragments.isEmpty()) {
                return Map.of("prompt", basePrompt, "fragments", fragments);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(basePrompt);
            sb.append("\n\n【知识库参考资料——优先基于以下资料回答，但控制在100字以内】\n");
            sb.append("1. 如果用户提问涉及C语言知识，你必须优先参考下面的资料\n");
            sb.append("2. 资料中没有的内容，要用你的知识补充但标注说明\n");
            sb.append("3. 引用的概念、代码示例必须和参考资料一致\n\n");
            for (int i = 0; i < fragments.size(); i++) {
                RagService.KnowledgeFragment f = fragments.get(i);
                sb.append("--- 参考资料").append(i + 1);
                if (f.source != null && !f.source.isEmpty()) {
                    sb.append("（来源：").append(f.source).append("）");
                }
                sb.append(" ---\n");
                sb.append(f.content).append("\n\n");
            }
            log.info("RAG增强对话提示词: query='{}', hit {} fragments",
                    userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage,
                    fragments.size());
            return Map.of("prompt", sb.toString(), "fragments", fragments);
        } catch (Exception e) {
            log.warn("RAG增强对话提示词失败，使用基础提示词", e);
            return Map.of("prompt", basePrompt, "fragments", fragments);
        }
    }

    /**
     * 苏格拉底式引导增强提示词 —— 在 RAG 上下文基础上叠加苏格拉底教学规则
     */
    private String buildSocraticAugmentedPrompt(String ragBasePrompt) {
        return ragBasePrompt
            + "\n\n回答格式：先完整回答问题（定义+解释+例子，100-150字），再提一个启发式小问题引导延伸思考。"
            + "\n语气口语化轻松，像朋友聊天。回答要全面准确。";
    }

    // ==================== 资源生成请求处理 ====================

    /**
     * 资源类型关键词映射（中文名 → 类型ID，以及关键词 → 类型ID）
     */
    private static final Map<String, String> RESOURCE_TYPE_KEYWORDS = new HashMap<>();
    static {
        RESOURCE_TYPE_KEYWORDS.put("知识精讲", "explanation");
        RESOURCE_TYPE_KEYWORDS.put("讲解", "explanation");
        RESOURCE_TYPE_KEYWORDS.put("解释", "explanation");
        RESOURCE_TYPE_KEYWORDS.put("概念", "explanation");
        RESOURCE_TYPE_KEYWORDS.put("知识导图", "mindmap");
        RESOURCE_TYPE_KEYWORDS.put("思维导图", "mindmap");
        RESOURCE_TYPE_KEYWORDS.put("导图", "mindmap");
        RESOURCE_TYPE_KEYWORDS.put("脑图", "mindmap");
        RESOURCE_TYPE_KEYWORDS.put("编程实战", "code");
        RESOURCE_TYPE_KEYWORDS.put("代码", "code");
        RESOURCE_TYPE_KEYWORDS.put("编程", "code");
        RESOURCE_TYPE_KEYWORDS.put("示例", "code");
        RESOURCE_TYPE_KEYWORDS.put("巩固练习", "exercise");
        RESOURCE_TYPE_KEYWORDS.put("练习", "exercise");
        RESOURCE_TYPE_KEYWORDS.put("习题", "exercise");
        RESOURCE_TYPE_KEYWORDS.put("训练", "exercise");
        RESOURCE_TYPE_KEYWORDS.put("错题诊断", "weakness");
        RESOURCE_TYPE_KEYWORDS.put("错题", "weakness");
        RESOURCE_TYPE_KEYWORDS.put("薄弱", "weakness");
        RESOURCE_TYPE_KEYWORDS.put("诊断", "weakness");
        RESOURCE_TYPE_KEYWORDS.put("视频讲解", "course-video");
        RESOURCE_TYPE_KEYWORDS.put("视频", "course-video");
        RESOURCE_TYPE_KEYWORDS.put("学习课件", "course-ppt");
        RESOURCE_TYPE_KEYWORDS.put("课件", "course-ppt");
        RESOURCE_TYPE_KEYWORDS.put("PPT", "course-ppt");
        RESOURCE_TYPE_KEYWORDS.put("ppt", "course-ppt");
        RESOURCE_TYPE_KEYWORDS.put("听学笔记", "article");
        RESOURCE_TYPE_KEYWORDS.put("笔记", "article");
        RESOURCE_TYPE_KEYWORDS.put("博客", "article");
    }

    /**
     * 用DeepSeek智能检测用户是否请求生成资源
     * 先用关键词预过滤，大部分消息不触发 LLM 调用
     * @return [资源类型, 知识点] 或 null（非资源请求）
     */
    private String[] detectResourceRequest(String message) {
        if (message == null) return null;

        // 轻量级关键词预过滤：不含意图词 + 资源词的消息直接跳过 LLM
        String lower = message.toLowerCase();
        boolean hasIntentWord = lower.contains("生成") || lower.contains("帮我") || lower.contains("创建")
                || lower.contains("做一个") || lower.contains("来一个") || lower.contains("给我")
                || lower.contains("需要") || lower.contains("想要") || lower.contains("资源")
                || lower.contains("来份") || lower.contains("来一篇") || lower.contains("出一道")
                || lower.contains("写一个") || lower.contains("做个");
        boolean hasResourceWord = lower.contains("导图") || lower.contains("思维导图") || lower.contains("代码")
                || lower.contains("编程") || lower.contains("练习") || lower.contains("习题")
                || lower.contains("错题") || lower.contains("薄弱") || lower.contains("笔记")
                || lower.contains("讲解") || lower.contains("课件") || lower.contains("视频")
                || lower.contains("博客") || lower.contains("诊断");

        if (!hasIntentWord || !hasResourceWord) {
            return null; // 非资源请求，必须同时有意图词和资源词才进入LLM判断
        }

        log.info("[资源检测] 关键词命中，启动 DeepSeek 分析: {}", message);

        try {
            String analysis = sparkClient.smartChat(
                "你是意图分析专家。判断用户消息是否在请求生成学习资源。",
                """
                分析以下用户消息，判断用户是否想生成学习资源。

                用户消息：""" + message + """

                重要区分：
                - "给我讲讲XX"、"什么是XX"、"解释一下XX" = 问问题，返回 {"isResource": false}
                - "帮我生成XX的练习题"、"创建XX的导图" = 生成资源，返回 {"isResource": true, ...}

                规则：
                1. 如果用户明确要求生成/创建某种学习资源（知识精讲、导图、代码示例、练习题、错题诊断、听学笔记、课件、视频），返回JSON：
                   {"isResource": true, "type": "资源类型", "knowledgePoint": "知识点"}
                2. 如果用户只是在问问题、讨论概念、请求解释，返回：
                   {"isResource": false}

                资源类型可选：explanation(知识精讲), mindmap(知识导图), code(编程实战), exercise(巩固练习), weakness(错题诊断), article(听学笔记)

                只输出JSON，不要其他文字。
                """,
                SparkClient.TaskComplexity.LOW,
                256
            );

            String json = analysis.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }
            com.alibaba.fastjson2.JSONObject result = com.alibaba.fastjson2.JSON.parseObject(json);

            boolean isResource = result.getBooleanValue("isResource");
            if (!isResource) return null;

            String type = result.getString("type");
            String kp = result.getString("knowledgePoint");
            if (type == null || type.isEmpty()) type = "explanation";
            if (kp == null || kp.isEmpty()) kp = "C语言基础";

            log.info("[资源检测] DeepSeek分析: type={}, kp={}", type, kp);
            return new String[]{type, kp};

        } catch (Exception e) {
            log.warn("[资源检测] DeepSeek分析失败，降级到关键词匹配: {}", e.getMessage());
            return detectResourceRequestFallback(message);
        }
    }

    /**
     * 关键词降级匹配（DeepSeek失败时使用）
     */
    private String[] detectResourceRequestFallback(String message) {
        String lower = message.toLowerCase();
        boolean hasIntent = lower.contains("生成") || lower.contains("帮我") || lower.contains("创建")
                || lower.contains("做一个") || lower.contains("来一个") || lower.contains("给我")
                || lower.contains("需要") || lower.contains("想要") || lower.contains("资源");
        if (!hasIntent) return null;

        String resourceType = "explanation";
        if (lower.contains("导图") || lower.contains("思维导图")) resourceType = "mindmap";
        else if (lower.contains("代码") || lower.contains("编程")) resourceType = "code";
        else if (lower.contains("练习") || lower.contains("习题")) resourceType = "exercise";
        else if (lower.contains("错题") || lower.contains("薄弱")) resourceType = "weakness";
        else if (lower.contains("笔记")) resourceType = "article";

        String knowledgePoint = "C语言基础";
        String[] kps = {"指针", "数组", "函数", "结构体", "内存管理", "控制结构", "基础语法", "文件操作"};
        for (String kp : kps) {
            if (lower.contains(kp)) { knowledgePoint = kp; break; }
        }
        return new String[]{resourceType, knowledgePoint};
    }

    /**
     * 生成资源并返回资源按钮
     */
    private List<ResourceButton> generateResourceButtons(Long studentId, String resourceType, String knowledgePoint) {
        List<ResourceButton> buttons = new ArrayList<>();

        try {
            // 获取或创建学生
            Student student = studentRepository.findBySysUserId(studentId)
                    .orElseGet(() -> {
                        Student s = new Student();
                        s.setUsername("student_" + studentId);
                        s.setSysUserId(studentId);
                        s.setKnowledgeLevel(30);
                        return studentRepository.save(s);
                    });

            // 调用 autoResourceGenerator 的 dispatch 方法生成资源
            String knowledgeLevel = String.valueOf(student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30);
            String weakAreas = student.getWeakAreas() != null ? student.getWeakAreas() : "C语言基础";

            List<LearningResource> generated = autoResourceGenerator.dispatch(
                    student.getId(), knowledgePoint, knowledgeLevel, weakAreas);

            // 过滤出请求类型的资源
            for (LearningResource lr : generated) {
                if (resourceType.equals(lr.getType())) {
                    ResourceButton button = new ResourceButton(
                            lr.getId(), lr.getTitle(), lr.getType(),
                            lr.getKnowledgePoint(), knowledgePoint,
                            "/student/resource-detail?id=" + lr.getId(),
                            getResourceIcon(lr.getType()));
                    buttons.add(button);
                }
            }

            // 如果没有精确匹配，返回所有生成的资源
            if (buttons.isEmpty()) {
                for (LearningResource lr : generated) {
                    ResourceButton button = new ResourceButton(
                            lr.getId(), lr.getTitle(), lr.getType(),
                            lr.getKnowledgePoint(), knowledgePoint,
                            "/student/resource-detail?id=" + lr.getId(),
                            getResourceIcon(lr.getType()));
                    buttons.add(button);
                }
            }

            // WebSocket推送通知前端刷新
            wsPushService.pushResourceUpdate(studentId);

        } catch (Exception e) {
            log.error("资源生成失败: studentId={}, type={}, kp={}", studentId, resourceType, knowledgePoint, e);
        }

        return buttons;
    }

    private String getResourceIcon(String type) {
        return com.happymouse.cryd.service.agent.ResourceType.iconOf(type);
    }

    private String getResourceTypeName(String type) {
        return com.happymouse.cryd.service.agent.ResourceType.displayNameOf(type);
    }
}