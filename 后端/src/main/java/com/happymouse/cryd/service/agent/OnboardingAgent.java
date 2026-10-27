package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.config.OnboardingConfig;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.ChatMessage;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.ChatMessageRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 新生引导智能体 — 新用户注册后，AI以对话方式了解学生信息，获取真实画像数据。
 *
 * 全程由AI主导提问，学生只需点击选项或简短回答。
 * 引导流程由 application.yml 的 onboarding.steps 配置驱动，可随时调整问题、选项和映射关系。
 */
@Service
public class OnboardingAgent {

    private static final Logger log = LoggerFactory.getLogger(OnboardingAgent.class);

    private final StudentRepository studentRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final OnboardingConfig config;
    private volatile List<StepDef> cachedSteps;
    private SparkClient sparkClient;

    /** 诊断状态：正确数 + 答错的知识点列表 */
    private static class DiagnosticState {
        int correctCount;
        int totalAnswered;
        List<String> wrongKnowledgePoints = new ArrayList<>();
    }
    private final Map<Long, DiagnosticState> diagnosticStates = new java.util.concurrent.ConcurrentHashMap<>();

    public OnboardingAgent(StudentRepository studentRepo,
                           ChatMessageRepository chatMessageRepo,
                           OnboardingConfig config) {
        this.studentRepo = studentRepo;
        this.chatMessageRepo = chatMessageRepo;
        this.config = config;
    }

    @Autowired
    public void setSparkClient(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    // ==================== 引导流程定义（从配置构建） ====================

    private record StepDef(String aiMessage, List<String> options, String profileKey,
                           boolean diagnostic, String knowledgePoint, String correctAnswer, String failHint) {}

    private List<StepDef> buildSteps() {
        if (cachedSteps != null) return cachedSteps;
        List<OnboardingConfig.StepConfig> configSteps = config.getSteps();
        if (configSteps == null || configSteps.isEmpty()) {
            log.warn("onboarding.steps 配置为空，使用空列表");
            cachedSteps = List.of();
            return cachedSteps;
        }
        List<StepDef> steps = new ArrayList<>();
        for (OnboardingConfig.StepConfig cs : configSteps) {
            steps.add(new StepDef(cs.getQuestion(), cs.getOptions(), cs.getProfileKey(),
                    cs.isDiagnostic(), cs.getKnowledgePoint(), cs.getCorrectAnswer(), cs.getFailHint()));
        }
        cachedSteps = steps;
        return steps;
    }

    // ==================== 引导入口 ====================

    /**
     * 开始引导 —— 返回第1步的AI开场白 + 选项
     */
    public ChatResponse startOnboarding(Long studentId) {
        ChatResponse response = new ChatResponse();
        response.setOnboarding(true);
        response.setOnboardingStep(1);
        response.setAgentName("学习导师");
        response.setMessage(buildSteps().get(0).aiMessage);
        response.setOptions(buildSteps().get(0).options);
        return response;
    }

    /**
     * 处理引导中的用户回复，推进到下一步
     * @param studentId 学生ID
     * @param userAnswer 用户的选择/回答
     * @param currentStep 当前步骤 (1-based)
     * @return 下一步的AI回复 + 选项，如果完成则 onboardingComplete=true
     */
    public ChatResponse processOnboardingStep(Long studentId, String userAnswer, int currentStep) {
        List<StepDef> steps = buildSteps();

        // 检测是否为语音回答（前端发送格式：【导学问题N】xxx\n选项：xxx\n用户语音回答：xxx）
        boolean isVoiceResponse = userAnswer != null && userAnswer.contains("【导学问题");

        int nextStep = currentStep + 1;

        // 所有步骤完成 → 引导结束
        if (nextStep > steps.size()) {
            return completeOnboarding(studentId);
        }

        StepDef nextStepDef = steps.get(nextStep - 1);
        StepDef currentStepDef = steps.get(currentStep - 1);
        ChatResponse response = new ChatResponse();
        response.setOnboarding(true);
        response.setOnboardingStep(nextStep);
        response.setAgentName("学习导师");

        // 语音回答：规则快筛 + DeepSeek判断
        if (isVoiceResponse) {
            String cleanAnswer = extractVoiceAnswer(userAnswer);
            log.info("[导学语音] step={}, cleanAnswer=\"{}\"", currentStep, cleanAnswer);
            boolean sufficient;

            if (isFillerAnswer(cleanAnswer)) {
                // 敷衍回答：直接无效，不调LLM
                sufficient = false;
            } else {
                // 有内容：调 DeepSeek 判断是否与问题相关
                sufficient = judgeVoiceWithDeepSeek(studentId, currentStep, cleanAnswer, currentStepDef);
            }

            response.setVoiceSufficient(sufficient);
            log.info("[导学语音] step={}, sufficient={}, reply=\"{}\"", currentStep, sufficient, response.getMessage());
            if (sufficient) {
                // 有效 → 记录答案 + "好的，" + 下一题
                recordAnswer(studentId, currentStep, userAnswer);
                response.setMessage("好的，");
            } else {
                // 无效 → 追问，不跳转
                response.setOnboardingStep(currentStep);
                String hint = (currentStepDef.options != null && !currentStepDef.options.isEmpty())
                        ? "或者你可以点击下方选项告诉我~" : "";
                response.setMessage("抱歉，我没有太理解你的意思。你能再具体说说吗？" + hint);
            }
        } else {
            // 选项点击：记录答案 + 直接跳转
            recordAnswer(studentId, currentStep, userAnswer);
            String ack = userAnswer != null && !userAnswer.isBlank() ? "好的，" : "";
            response.setMessage(ack + nextStepDef.aiMessage);
        }

        response.setOptions(nextStepDef.options);
        return response;
    }

    // ==================== 数据记录 ====================

    private void recordAnswer(Long sysUserId, int step, String answer) {
        var studentOpt = studentRepo.findBySysUserId(sysUserId);
        if (studentOpt.isEmpty()) return;
        Student s = studentOpt.get();

        List<OnboardingConfig.StepConfig> configSteps = config.getSteps();
        if (configSteps == null || step < 1 || step > configSteps.size()) return;
        OnboardingConfig.StepConfig cs = configSteps.get(step - 1);

        // 诊断步骤特殊处理
        if (cs.isDiagnostic()) {
            s.setInterestDirection(answer); // 临时借用字段存诊断结果
            studentRepo.save(s);
            return;
        }

        String profileKey = cs.getProfileKey();
        Map<String, Object> valueMap = cs.getValueMap();
        if (profileKey == null || valueMap == null || valueMap.isEmpty()) {
            studentRepo.save(s);
            return;
        }

        // 用 valueMap 匹配答案
        Object matched = matchFromValueMap(answer, cs.getOptions(), valueMap);

        switch (profileKey) {
            case "knowledgeLevel":
                if (matched instanceof Number) s.setKnowledgeLevel(((Number) matched).intValue());
                break;
            case "studyMotivation":
                if (matched instanceof String) s.setStudyMotivation((String) matched);
                break;
            case "cognitiveStyle":
                if (matched instanceof String) s.setCognitiveStyle((String) matched);
                break;
            case "interestDirection":
                if (matched instanceof String) s.setInterestDirection((String) matched);
                break;
            case "learningPace":
                if (matched instanceof String) s.setLearningPace((String) matched);
                break;
            default:
                log.debug("未处理的 profileKey: {}", profileKey);
        }

        studentRepo.save(s);
    }

    /**
     * 从 valueMap 中匹配用户答案。
     * 先用精确选项匹配，再用 valueMap key 做关键词匹配。
     */
    private Object matchFromValueMap(String answer, List<String> options, Map<String, Object> valueMap) {
        if (answer == null || valueMap.isEmpty()) return null;
        // 1. 精确匹配选项
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                if (answer.equals(options.get(i))) {
                    // 通过选项文本匹配到 valueMap 中的 key
                    for (Map.Entry<String, Object> e : valueMap.entrySet()) {
                        if (options.get(i).contains(e.getKey())) return e.getValue();
                    }
                    return null;
                }
            }
        }
        // 2. 关键词兜底匹配
        String lower = answer.toLowerCase();
        for (Map.Entry<String, Object> e : valueMap.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase())) return e.getValue();
        }
        return null;
    }

    /**
     * 处理诊断题回答：判分 → 记录结果 → 返回反馈+下一题（或完成）
     */
    private ChatResponse handleDiagnosticQuestion(Long sysUserId, String answer, int currentStep) {
        List<StepDef> steps = buildSteps();
        StepDef current = steps.get(currentStep - 1);

        // 获取或创建诊断状态
        DiagnosticState state = diagnosticStates.computeIfAbsent(sysUserId, k -> new DiagnosticState());
        state.totalAnswered++;

        // 精确文本匹配判分
        boolean correct = answer != null && answer.equals(current.correctAnswer);
        if (correct) {
            state.correctCount++;
        } else {
            state.wrongKnowledgePoints.add(current.knowledgePoint);
            // 将答错的知识点加入 weakAreas
            studentRepo.findBySysUserId(sysUserId).ifPresent(s -> {
                String existing = s.getWeakAreas();
                if (existing == null || existing.isBlank() || "待评估".equals(existing)) {
                    s.setWeakAreas(current.knowledgePoint);
                } else if (!existing.contains(current.knowledgePoint)) {
                    s.setWeakAreas(existing + "，" + current.knowledgePoint);
                }
                studentRepo.save(s);
            });
        }

        // 构建反馈消息
        String feedback;
        if (correct) {
            feedback = "✅ 回答正确！";
        } else {
            feedback = "❌ 答错了。" + current.failHint
                    + "\n\n正确答案是：**" + current.correctAnswer + "**";
        }

        int nextStep = currentStep + 1;

        // 还有下一题 → 返回下一题
        if (nextStep <= steps.size() && steps.get(nextStep - 1).diagnostic) {
            StepDef next = steps.get(nextStep - 1);
            ChatResponse response = new ChatResponse();
            response.setOnboarding(true);
            response.setOnboardingStep(nextStep);
            response.setAgentName("学习导师");
            response.setMessage(feedback + "\n\n---\n\n" + next.aiMessage);
            response.setOptions(next.options);
            return response;
        }

        // 所有诊断题完成 → 进入引导结束流程
        return completeOnboarding(sysUserId);
    }

    /**
     * 动态诊断反馈：根据学生选择的学习方式，给出个性化反馈并微调知识水平
     * @param completedStep 刚完成的步骤号（1-based），下一步 = completedStep + 1
     */
    private ChatResponse handleDiagnosticResponse(Long sysUserId, String answer, int completedStep) {
        var studentOpt = studentRepo.findBySysUserId(sysUserId);
        if (studentOpt.isEmpty()) return completeOnboarding(sysUserId);
        Student s = studentOpt.get();

        // 根据学习方式给出个性化反馈
        String feedback;
        int prevLevel = s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 40;
        int adjustedLevel = prevLevel;

        if (answer != null && answer.contains("视频")) {
            feedback = "好的！视频学习是很直观的方式。我会为你推荐更多视频资源。";
            adjustedLevel = Math.min(100, prevLevel + 5);
        } else if (answer != null && answer.contains("练习")) {
            feedback = "动手实践是最好的学习方式！我会多给你出练习题。";
            adjustedLevel = Math.min(100, prevLevel + 3);
        } else if (answer != null && answer.contains("文档")) {
            feedback = "喜欢读文档说明你有不错的自学能力！我会为你准备详细的学习文档。";
        } else {
            feedback = "混合学习方式很全面，我会为你平衡各种资源。";
        }

        s.setKnowledgeLevel(adjustedLevel);
        studentRepo.save(s);

        // 动态计算下一步
        List<StepDef> steps = buildSteps();
        int nextStepNum = completedStep + 1;

        if (nextStepNum > steps.size()) {
            return completeOnboarding(sysUserId);
        }

        StepDef nextStep = steps.get(nextStepNum - 1);

        ChatResponse response = new ChatResponse();
        response.setOnboarding(true);
        response.setOnboardingStep(nextStepNum);
        response.setAgentName("学习导师");
        response.setMessage(feedback + "\n\n---\n\n" + nextStep.aiMessage);
        response.setOptions(nextStep.options);
        return response;
    }

    /**
     * 引导完成 → 根据诊断结果调整知识水平 → 生成画像总结 → 触发自动资源生成
     */
    private ChatResponse completeOnboarding(Long sysUserId) {
        var studentOpt = studentRepo.findBySysUserId(sysUserId);
        if (studentOpt.isEmpty()) {
            ChatResponse resp = new ChatResponse();
            resp.setOnboardingComplete(true);
            resp.setMessage("引导完成！");
            return resp;
        }
        Student s = studentOpt.get();

        // 根据诊断正确率调整 knowledgeLevel（8道题，按正确率分档）
        DiagnosticState ds = diagnosticStates.remove(sysUserId);
        if (ds != null && ds.totalAnswered > 0) {
            int prevLevel = s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 40;
            double rate = (double) ds.correctCount / ds.totalAnswered;
            int bonus = rate >= 0.875 ? 20    // 7-8对：优秀
                      : rate >= 0.75 ? 15     // 6对：良好
                      : rate >= 0.625 ? 10    // 5对：中等
                      : rate >= 0.5 ? 5       // 4对：及格
                      : rate >= 0.25 ? 0      // 1-3对：基础薄弱但不扣分
                      : -10;                   // 0对：需要重新学习
            s.setKnowledgeLevel(Math.max(0, Math.min(100, prevLevel + bonus)));
            log.info("诊断测试完成: studentId={}, correct={}/{}, rate={}%, level {}→{}",
                    sysUserId, ds.correctCount, ds.totalAnswered, (int)(rate * 100), prevLevel, s.getKnowledgeLevel());
        }

        // ===== AI智能校验：检查用户自评与诊断结果的一致性 =====
        if (ds != null && ds.totalAnswered > 0 && sparkClient != null) {
            try {
                validateProfileWithAI(s, ds);
            } catch (Exception e) {
                log.warn("AI画像校验失败，使用原始画像: {}", e.getMessage());
            }
        }

        // 补充未填的画像维度
        if (s.getFocusLevel() == null) s.setFocusLevel("medium");
        if (s.getLearningPreference() == null) {
            String style = s.getCognitiveStyle();
            if ("visual".equals(style)) s.setLearningPreference("video");
            else if ("reading".equals(style)) s.setLearningPreference("doc");
            else if ("kinesthetic".equals(style)) s.setLearningPreference("exercise");
            else s.setLearningPreference("mixed");
        }
        if (s.getWeakAreas() == null || s.getWeakAreas().isBlank() || "待评估".equals(s.getWeakAreas())) {
            // 优先用诊断发现的薄弱点（已由handleDiagnosticQuestion写入）
            // 如果诊断也没发现薄弱点，用knowledgeLevel推断
            if (ds != null && !ds.wrongKnowledgePoints.isEmpty()) {
                // 诊断已发现薄弱点，不需要额外设置
            } else {
                int level = s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 40;
                if (level < 30) s.setWeakAreas("基础语法，判断结构，循环结构");
                else if (level < 60) s.setWeakAreas("函数与数组，指针基础");
                else if (level < 80) s.setWeakAreas("动态内存，指针进阶");
                else s.setWeakAreas("复杂数据结构，文件操作");
            }
        }
        s.setProgress(0);
        s.setStreakDays(0);
        s.setOnboarded(true);
        studentRepo.save(s);

        // 构建诊断结果摘要（8道题）
        String diagnosticSummary = "";
        if (ds != null && ds.totalAnswered > 0) {
            double rate = (double) ds.correctCount / ds.totalAnswered;
            int bonus = rate >= 0.875 ? 20 : rate >= 0.75 ? 15 : rate >= 0.625 ? 10
                      : rate >= 0.5 ? 5 : rate >= 0.25 ? 0 : -10;
            String grade = rate >= 0.875 ? "优秀" : rate >= 0.75 ? "良好" : rate >= 0.625 ? "中等"
                         : rate >= 0.5 ? "及格" : "需加强";
            diagnosticSummary = String.format("""
                    ### 诊断测试结果

                    | 项目 | 结果 |
                    |------|------|
                    | 答对题数 | %d / %d（%s）|
                    | 综合评级 | %s |
                    | 知识水平调整 | %s%d分 |
                    | 诊断发现的薄弱章节 | %s |

                    """,
                    ds.correctCount, ds.totalAnswered, (int)(rate * 100) + "%", grade,
                    bonus >= 0 ? "↑" : "↓", Math.abs(bonus),
                    ds.wrongKnowledgePoints.isEmpty() ? "无" : String.join("、", ds.wrongKnowledgePoints));
        }

        // 构建总结消息
        String summary = String.format("""
                🎉 **太棒了！我已经充分了解你的学习情况了！**

                %s
                下面是你专属的**学习画像总结**：

                | 维度 | 你的情况 |
                |------|----------|
                | 📚 知识基础 | %d分（%s）|
                | 🎯 学习目标 | %s |
                | 💪 学习动机 | %s |
                | 🧠 学习风格 | %s |
                | ⭐ 兴趣方向 | %s |
                | ⏱️ 学习节奏 | %s |
                | ⚠️ 薄弱环节 | %s |

                **学习导师已根据你的画像自动生成第一批个性化学习资源**，去 [资源中心](/student/resources) 看看吧！

                现在你可以：
                - 🤖 **继续和我对话**——随时提问C语言问题
                - 📚 **查看自动生成的资源**——左侧菜单进入资源中心
                - 🗺️ **查看学习路径**——AI为你规划的个性化路线

                准备好了吗？让我们开始你的C语言学习之旅！🚀
                """,
                diagnosticSummary,
                s.getKnowledgeLevel(),
                s.getKnowledgeLevel() >= 80 ? "扎实" : s.getKnowledgeLevel() >= 50 ? "中等" : "基础",
                s.getStudyMotivation() != null && !s.getStudyMotivation().isBlank() ? s.getStudyMotivation() : "通过课程考核",
                labelMotivation(s.getStudyMotivation()),
                labelCognitiveStyle(s.getCognitiveStyle()),
                s.getInterestDirection() != null ? s.getInterestDirection() : "通识基础",
                labelPace(s.getLearningPace()),
                s.getWeakAreas() != null ? s.getWeakAreas() : "待进一步评估"
        );

        int totalSteps = buildSteps().size();
        ChatResponse response = new ChatResponse();
        response.setOnboarding(true);
        response.setOnboardingComplete(true);
        response.setOnboardingStep(totalSteps + 1);
        response.setAgentName("学习导师");
        response.setMessage(summary);
        response.setProfileUpdated(true);
        response.setResourceGenerated(true);
        response.setOptions(null);
        return response;
    }

    // ==================== 语音回答判断 ====================

    /**
     * 从前端发送的语音消息中提取用户实际说的话
     */
    private String extractVoiceAnswer(String fullMessage) {
        if (fullMessage == null) return "";
        String[] parts = fullMessage.split("用户语音回答：");
        return parts.length > 1 ? parts[parts.length - 1].trim() : fullMessage;
    }

    /** 敷衍/无效回答关键词 */
    private static final String[] FILLER_WORDS = {"嗯", "哦", "额", "啊", "好吧", "随便", "不知道", "不清楚", "没想法", "都可以", "无所谓", "没意见"};

    /**
     * 规则快筛：空/太短/敷衍词 → 无效
     */
    private boolean isFillerAnswer(String voiceAnswer) {
        if (voiceAnswer == null || voiceAnswer.trim().length() < 3) return true;
        String lower = voiceAnswer.trim().toLowerCase();
        for (String filler : FILLER_WORDS) {
            if (voiceAnswer.trim().equals(filler) || (voiceAnswer.trim().length() <= 4 && lower.contains(filler))) return true;
        }
        return false;
    }

    /**
     * 用 DeepSeek 判断语音回答是否与当前问题相关
     * @return true=有效（跳转）, false=无效（追问）
     */
    private boolean judgeVoiceWithDeepSeek(Long studentId, int currentStep, String voiceAnswer, StepDef step) {
        if (voiceAnswer == null || voiceAnswer.isBlank()) return false;
        if (sparkClient == null) return true;
        try {
            String optionsText = step.options != null ? String.join("、", step.options) : "无固定选项";
            String prompt = String.format("""
                    判断以下用户回答是否与问卷问题相关。

                    问题：%s
                    选项：%s
                    用户回答：%s

                    规则：
                    - 回答提到了与问题主题相关的信息（学习经历、目标、时间、偏好、困难等）→ true
                    - 用户用自己的话表达，不需要精确匹配选项 → true
                    - "嗯""好的""不知道"等不含有效信息 → false
                    - 完全跑题 → false

                    只输出 true 或 false，不要其他文字。
                    """, step.aiMessage, optionsText, voiceAnswer);
            String reply = sparkClient.smartChat(prompt, "语音判断", SparkClient.TaskComplexity.LOW, 16);
            if (reply == null) return true;
            boolean result = reply.trim().toLowerCase().contains("true");
            log.info("[导学语音判断] step={}, voice=\"{}\", result={}", currentStep, voiceAnswer, result);
            return result;
        } catch (Exception e) {
            log.warn("[导学语音判断] LLM失败，默认有效: {}", e.getMessage());
            return true;
        }
    }

    // ==================== 判断方法 ====================

    /**
     * 判断学生是否需要新生引导（新用户 / 画像未完善）
     */
    public boolean needsOnboarding(Long sysUserId) {
        var studentOpt = studentRepo.findBySysUserId(sysUserId);
        if (studentOpt.isEmpty()) return true;
        Student s = studentOpt.get();
        return !Boolean.TRUE.equals(s.getOnboarded());
    }

    /**
     * 通过对话历史推算当前引导步骤（用于断线重连 / 前端未传 onboardingStep 时的兜底）
     * 只统计最近30分钟内 role=assistant 且 agentName=学习导师 的消息数，
     * 避免旧会话的历史消息干扰步骤推断。
     */
    public int inferCurrentStep(Long studentId) {
        List<ChatMessage> history = chatMessageRepo.findByStudentIdOrderByCreatedAtAsc(studentId);
        java.time.LocalDateTime thirtyMinAgo = java.time.LocalDateTime.now().minusMinutes(30);
        // 只计算最近30分钟内的引导导师回复消息
        long onboardingMsgs = history.stream()
                .filter(m -> "学习导师".equals(m.getAgentName()))
                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(thirtyMinAgo))
                .count();
        // 每条导师回复对应一步，下一步 = 已完成步数 + 1
        return Math.max(1, (int) Math.min(onboardingMsgs + 1, buildSteps().size()));
    }

    // ==================== Label映射 ====================

    private String labelMotivation(String v) {
        return switch (v != null ? v : "") {
            case "intrinsic" -> "内在驱动";
            case "extrinsic" -> "外部驱动";
            case "hybrid" -> "混合驱动";
            default -> "待分析";
        };
    }

    private String labelCognitiveStyle(String v) {
        return switch (v != null ? v : "") {
            case "visual" -> "视觉型";
            case "auditory" -> "听觉型";
            case "kinesthetic" -> "动觉型";
            case "reading" -> "阅读型";
            default -> "待分析";
        };
    }

    private String labelPace(String v) {
        return switch (v != null ? v : "") {
            case "fast" -> "快速型";
            case "steady" -> "稳健型";
            case "slow" -> "慢速型";
            default -> "待分析";
        };
    }

    // ==================== AI智能校验 ====================

    private static final String VALIDATION_PROMPT = """
            你是一位C语言教学专家。请分析以下学生的自评画像和诊断测试结果是否一致。

            【学生自评】
            - 自评知识水平：%d/100（%s）
            - 自评薄弱环节：%s

            【诊断测试结果（8道题，覆盖8大章节）】
            - 答对：%d/8（正确率%d%%）
            - 答错的知识点：%s

            请判断：
            1. 自评水平与诊断结果是否一致？（如：自评80分但只对2题→严重虚高）
            2. 自评薄弱环节与实际答错的知识点是否匹配？
            3. 如果不一致，给出修正建议（用JSON格式）

            只输出JSON，不要其他文字：
            {"consistent": true/false, "adjustedLevel": 修正后的水平(0-100), "adjustedWeak": "修正后的薄弱环节", "reason": "判断理由"}
            """;

    /**
     * 用AI校验用户自评与诊断结果的一致性，发现不一致时自动修正画像
     */
    private void validateProfileWithAI(Student s, DiagnosticState ds) {
        if (sparkClient == null) return;

        int selfLevel = s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 40;
        String selfWeak = s.getWeakAreas() != null ? s.getWeakAreas() : "未选择";
        double rate = (double) ds.correctCount / ds.totalAnswered;

        // 快速校验：如果自评与诊断结果差距不大，跳过AI调用节省时间
        // 自评等级映射：零基础=20, 学过一点=40, 有基础=60, 比较熟练=80
        // 诊断正确率映射：0/8→0, 2/8→25, 4/8→50, 6/8→75, 8/8→100
        int diagnosticLevel = (int) (rate * 100);
        int gap = Math.abs(selfLevel - diagnosticLevel);

        if (gap <= 20) {
            // 差距在20分以内，认为基本一致，只微调薄弱环节
            log.info("画像校验: 自评{}分 vs 诊断{}分，差距{}分，在可接受范围内", selfLevel, diagnosticLevel, gap);
            return;
        }

        // 差距较大，调用AI深度分析
        log.info("画像校验: 自评{}分 vs 诊断{}分，差距{}分，启动AI校验", selfLevel, diagnosticLevel, gap);

        String prompt = String.format(VALIDATION_PROMPT,
                selfLevel, selfLevel >= 80 ? "比较熟练" : selfLevel >= 60 ? "有基础" : selfLevel >= 40 ? "学过一点" : "零基础",
                selfWeak, ds.correctCount, (int)(rate * 100),
                ds.wrongKnowledgePoints.isEmpty() ? "无" : String.join("、", ds.wrongKnowledgePoints));

        String reply = sparkClient.chat(prompt, "画像校验", 0.3f, 512);
        if (reply == null || reply.isEmpty()) return;

        // 解析JSON响应
        try {
            String json = reply.trim();
            if (json.contains("```json")) json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            if (json.contains("```")) json = json.replaceAll("```\\s*", "").replaceAll("```\\s*", "");

            // 简单解析关键字段
            boolean consistent = json.contains("\"consistent\": true") || json.contains("\"consistent\":true");
            if (consistent) {
                log.info("AI校验结论: 画像一致，无需修正");
                return;
            }

            // 提取修正后的水平
            int adjustedLevel = extractJsonInt(json, "adjustedLevel", selfLevel);
            String adjustedWeak = extractJsonString(json, "adjustedWeak", selfWeak);
            String reason = extractJsonString(json, "reason", "AI校验发现不一致");

            log.info("AI校验修正: level {}→{}, weak: {}→{}, reason: {}",
                    selfLevel, adjustedLevel, selfWeak, adjustedWeak, reason);

            // 应用修正
            s.setKnowledgeLevel(Math.max(0, Math.min(100, adjustedLevel)));
            if (adjustedWeak != null && !adjustedWeak.isBlank()) {
                s.setWeakAreas(adjustedWeak);
            }

        } catch (Exception e) {
            log.warn("AI校验结果解析失败: {}", e.getMessage());
        }
    }

    private int extractJsonInt(String json, String key, int defaultVal) {
        try {
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
            return obj != null && obj.containsKey(key) ? obj.getIntValue(key, defaultVal) : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private String extractJsonString(String json, String key, String defaultVal) {
        try {
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
            return obj != null && obj.containsKey(key) ? obj.getString(key) : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
