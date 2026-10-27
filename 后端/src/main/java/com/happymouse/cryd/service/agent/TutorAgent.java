package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI导师 - 即时答疑解惑，提供多模态辅导
 */
@Component("tutorAgent")
public class TutorAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「AI导师」，一位耐心友好的C语言程序设计AI导师。

            回答要求：
            1. 先直接回答问题，给出清晰完整的解释
            2. 适当举例或类比帮助理解
            3. 代码示例简短（3-5行），说明关键点
            4. 语气口语化亲切，像朋友聊天
            5. 控制在200字以内，全面但不啰嗦

            用Markdown格式排版（加粗、代码块、列表等）。
            """;

    /**
     * 苏格拉底式辅导 Prompt —— 完整回答 + 引导延伸
     */
    private static final String SOCRATIC_PROMPT = """
            你是「AI导师」，一位耐心友好的C语言老师，擅长用苏格拉底式教学法。

            回答格式（必须遵守）：
            1. **先完整回答问题**：给出清晰的定义、解释和例子（100-150字）
            2. **再引导延伸思考**：提一个相关的启发式小问题（1-2句）

            示例（问"指针是什么"）：
            指针是C语言中用来**存储内存地址**的变量。你可以把它想象成一张写着"房间号"的纸条。

            比如 `int a = 10;`，用 `&a` 可以拿到a的内存地址，再用 `int *p = &a;` 把地址存到指针p里。通过 `*p` 就能访问a的值——`*p = 20` 会让a变成20。

            💡 **想一想**：如果两个指针指向同一个变量，通过其中一个修改值，另一个会受影响吗？

            要求：口语化、轻松，像朋友聊天。回答要**全面准确**但不啰嗦。
            """;

    /**
     * 判断用户消息是否适合苏格拉底式引导
     * 返回 true → 用苏格拉底式（引导思考，不直接给答案）
     * 返回 false → 直接答疑（给出明确解释/代码/修改建议）
     */
    public static boolean isSocraticCandidate(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase();

        // ---- 直接答疑模式（不使用苏格拉底式）----
        String[] directPatterns = {
            // 语法/用法查询
            "怎么用", "如何使用", "用法", "语法", "格式", "printf", "scanf",
            "怎么写", "如何写", "模板", "示例代码", "代码示例",
            "哪个版本", "什么时候", "谁发明", "哪年",
            // 编程题：报错/调试/代码审查 → 直接帮
            "报错", "编译错误", "编译失败", "运行错误", "答案不对",
            "wa", "tle", "re", "ce", "mle",
            "为什么错", "哪里错了", "错在哪", "帮忙看看", "帮我检查", "检查代码",
            "帮我改", "怎么改", "修改", "修复", "debug",
            "点评", "review", "看看代码", "看看我"
        };
        for (String p : directPatterns) {
            if (lower.contains(p)) return false;
        }

        // ---- 苏格拉底式引导模式 ----
        String[] socraticPatterns = {
            // 概念理解 / 原理探究
            "是什么", "什么是", "什么叫", "区别", "差异", "不同",
            "为什么", "为啥", "原因", "原理", "机制",
            "怎么回事", "为什么这样", "为什么会",
            "有什么区别", "有什么不同", "有什么联系",
            "能解释", "能讲讲", "能说说",
            // 编程题：思路求助 → 引导思考
            "怎么做", "不会做", "没思路", "没想法", "卡住了", "卡住",
            "不知道怎么开始", "不知道从哪", "想不出", "想不出来",
            "毫无头绪", "没有方向", "思路", "解题思路",
            "该怎么想", "从哪开始", "从哪里开始", "第一步"
        };
        for (String p : socraticPatterns) {
            if (lower.contains(p)) return true;
        }

        return false;
    }

    private final StudentRepository studentRepository;

    public TutorAgent(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public String getName() { return "AI导师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("tutor:answer", "辅导答疑、C语言知识解答", 0.95),
            AgentCapability.of("tutor:explain", "概念讲解、代码示例", 0.90),
            AgentCapability.of("tutor:debug", "代码调试、错误分析", 0.85)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Student student = studentRepository.findBySysUserId(context.getStudentId()).orElse(null);
        String message = context.getOriginalMessage();
        String mode = context.getAttribute("mode", String.class);
        if (mode == null) mode = "fast";

        // 判断是否使用苏格拉底式辅导
        boolean useSocratic = "socratic".equals(mode) || isSocraticCandidate(message);
        String systemPrompt = useSocratic ? SOCRATIC_PROMPT : SYSTEM_PROMPT;

        String userPrompt = buildUserPrompt(message, student);

        // 根据模式调整参数
        float temperature;
        int maxTokens;
        if (useSocratic) {
            // 苏格拉底式：完整回答 + 引导延伸
            userPrompt += "\n\n【请先完整回答问题（给出定义、解释、例子，100-150字），再提一个启发式小问题引导延伸思考。】";
            temperature = 0.6f;
            maxTokens = 512;
        } else if ("expert".equals(mode)) {
            userPrompt += "\n\n【请简洁分析，控制在150字以内。】";
            temperature = 0.3f;
            maxTokens = 512;
        } else {
            // 快速模式
            userPrompt += "\n\n【请简短回答，控制在100字以内，直接给结论。】";
            temperature = 0.5f;
            maxTokens = 256;
        }

        if (sparkClient == null) {
            return "AI服务暂不可用，请稍后再试";
        }

        // 修复E3: 调用 appendRetryFeedback 注入上次审核反馈
        //   原实现绕过 BaseAgent.callLLM，使审核反馈循环对 TutorAgent 失效。
        //   此处保留苏格拉底模式的 temperature/maxTokens 定制，但补上反馈注入。
        userPrompt = appendRetryFeedback(context, userPrompt);

        List<Map<String, String>> history = context.getConversationHistory();
        if (history != null && !history.isEmpty()) {
            return sparkClient.chatWithHistory(systemPrompt, history, userPrompt, temperature, maxTokens);
        }
        // 使用智能分配：AI导师对话属于中等复杂度
        return sparkClient.smartChat(systemPrompt, userPrompt,
                SparkClient.TaskComplexity.MEDIUM, maxTokens);
    }

    /**
     * 保持向后兼容的旧接口 —— 自动判断是否使用苏格拉底式引导
     */
    public ChatResponse answer(ChatRequest request) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), null);
        ctx.setAttribute("mode", isSocraticCandidate(request.getMessage()) ? "socratic" : "fast");
        String output = execute(ctx).getOutput();
        ChatResponse response = new ChatResponse();
        response.setAgentName("AI导师");
        boolean socratic = isSocraticCandidate(request.getMessage());
        response.setAgentDescription(socratic ? "苏格拉底式引导辅导" : "即时答疑，帮你搞懂知识点");
        response.setMessage(output);
        response.setProfileUpdated(true);
        return response;
    }

    public ChatResponse answerWithHistory(ChatRequest request, List<Map<String, String>> history) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), history);
        // 自动判断是否使用苏格拉底式引导
        ctx.setAttribute("mode", isSocraticCandidate(request.getMessage()) ? "socratic" : "fast");
        // 修复E4: 走 execute() 模板方法而非直接调 doExecute，确保 preProcess(加载记忆)/postProcess(保存记忆) 执行
        String output = execute(ctx).getOutput();
        ChatResponse response = new ChatResponse();
        response.setAgentName("AI导师");
        response.setAgentDescription(isSocraticCandidate(request.getMessage()) ? "苏格拉底式引导辅导" : "即时答疑，帮你搞懂知识点");
        response.setMessage(output);
        return response;
    }

    private String buildUserPrompt(String message, Student student) {
        if (student == null) return message;
        // 修复D5: 防御 null 字段，避免 String.format("%d") 对 null 抛 NPE
        Integer level = student.getKnowledgeLevel();
        String style = student.getCognitiveStyle();
        return String.format("[学生水平：%d/100，风格：%s]\n\n%s",
                level != null ? level : 0,
                style != null ? style : "未知",
                message);
    }
}
