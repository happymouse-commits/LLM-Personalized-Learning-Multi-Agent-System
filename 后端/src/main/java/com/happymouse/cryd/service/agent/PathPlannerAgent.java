package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.LearningPath;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.ErrorNotebookRepository;
import com.happymouse.cryd.repository.LearningPathRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 路径规划师 - 根据画像和进度规划个性化学习路径
 */
@Component("pathPlannerAgent")
public class PathPlannerAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「路径规划师」，负责为C语言程序设计课程规划个性化学习路径。

            ════════════════════════════════════════════════════════════════
            【C语言课程知识体系（严格按顺序）】
            ════════════════════════════════════════════════════════════════
            1. 基础语法（变量、数据类型、运算符）← 前置
            2. 控制结构（if/switch/for/while）← 需要基础语法
            3. 函数与作用域 ← 需要1+2
            4. 数组与字符串 ← 需要1+2+3
            5. 指针（核心难点）← 需要1+4
            6. 结构体与联合 ← 需要1+5
            7. 文件操作 ← 需要1+6
            8. 动态内存管理 ← 需要1+5+7
            9. 预处理器与多文件编译 ← 需要全部

            ════════════════════════════════════════════════════════════════
            【路径规划规则】
            ════════════════════════════════════════════════════════════════
            1. 不能跳过前置知识（如没学基础语法就学指针）
            2. 每次推荐1-3个知识点，不要贪多
            3. 根据学生水平调整深度：初学者从基础开始，有基础的跳过已掌握内容
            4. 每步必须包含：学习目标、预计时间、推荐资源类型、前置依赖说明
            5. 路径总长度不超过10步

            ════════════════════════════════════════════════════════════════
            【输出格式（Markdown）】
            ════════════════════════════════════════════════════════════════
            ## 🎯 学习路径规划
            **当前水平**: [评估结果]
            **预计总时长**: [X小时]

            ### 第1步：[学习目标]
            - 📚 内容：[具体知识点]
            - ⏰ 预计时间：[X小时]
            - 📋 推荐资源：[视频/文章/代码]
            - ⚡ 前置要求：[需先掌握...]

            ### 第2步：[学习目标]
            ...
            """;

    private final StudentRepository studentRepository;
    private final LearningPathRepository pathRepository;
    private final ErrorNotebookRepository errorRepo;

    public PathPlannerAgent(StudentRepository studentRepository,
                            LearningPathRepository pathRepository,
                            ErrorNotebookRepository errorRepo) {
        this.studentRepository = studentRepository;
        this.pathRepository = pathRepository;
        this.errorRepo = errorRepo;
    }

    @Autowired
    public void setSparkClient(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @Override
    public String getName() { return "路径规划师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("path:plan", "学习路径规划、课程推荐", 0.94),
            AgentCapability.of("path:progress", "进度跟踪、步骤调整", 0.88)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Student student = studentRepository.findBySysUserId(context.getStudentId()).orElse(null);
        String message = context.getOriginalMessage();
        String userPrompt = buildUserPrompt(message, student, context.getStudentId());

        if (sparkClient == null) {
            return "AI服务暂不可用，请稍后再试";
        }

        // 学习路径规划是关键任务，使用Pro模型深度推理
        String content = sparkClient.smartChat(SYSTEM_PROMPT, userPrompt,
                SparkClient.TaskComplexity.CRITICAL, 2048);

        // 修复D4: 校验LLM输出后再保存，避免错误消息/空内容被当作学习路径存入数据库
        if (isValidPathContent(content)) {
            LearningPath path = new LearningPath();
            path.setName("AI个性化学习路径");
            path.setStudentId(context.getStudentId());
            path.setCurrentStep(student != null ? inferStep(student) : 1);
            path.setSteps(content);
            path.setStatus("active");
            pathRepository.save(path);
        } else {
            log.warn("[路径规划师] LLM输出校验未通过，不保存学习路径: contentLen={}",
                    content != null ? content.length() : 0);
        }

        return content;
    }

    /**
     * 修复D4: 校验学习路径内容是否有效
     * 有效内容应包含标题标记(##)、足够长度，且不是错误提示
     */
    private boolean isValidPathContent(String content) {
        if (content == null || content.isBlank()) return false;
        if (content.length() < 50) return false;
        // 学习路径应包含 markdown 标题
        if (!content.contains("#") && !content.contains("步")) return false;
        // 排除明显的错误消息
        String lower = content.toLowerCase();
        if (lower.contains("ai服务暂不可用") || lower.contains("error") || lower.startsWith("抱歉")) {
            return false;
        }
        return true;
    }

    /**
     * 供控制器调用的 generate 方法
     */
    public ChatResponse generate(ChatRequest request) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), null);
        String content = execute(ctx).getOutput();

        ChatResponse response = new ChatResponse();
        response.setAgentName("路径规划师");
        response.setAgentDescription("为你规划个性化学习路径");
        response.setMessage(content);
        return response;
    }

    /**
     * 保持向后兼容的旧接口
     */
    public ChatResponse plan(ChatRequest request) {
        return generate(request);
    }

    /**
     * 修复L3: 原实现每次资源生成都盲目 +2 知识水平，不看资源数量、不看学生是否真的学习。
     *   现按生成资源数量分级递增（每份资源 +0.5，单次最多 +3），
     *   并在高水平段（>=80）收紧增量，避免满级作弊。
     *   TODO: 未来接入"答题正确率"作为真实掌握度信号，替换本启发式。
     */
    public void updateProgress(ChatRequest request) {
        updateProgress(request, null);
    }

    /** 重载：可传入 ChatResponse 以读取实际资源数量 */
    public void updateProgress(ChatRequest request, com.happymouse.cryd.model.dto.ChatResponse response) {
        Student student = studentRepository.findBySysUserId(request.getStudentId()).orElse(null);
        if (student == null) return;

        int current = student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30;
        // 资源数量决定单次增量；无 response 信息时默认1份资源
        int resCount = 1;
        if (response != null && response.getResourceIds() != null) {
            resCount = Math.max(1, Math.min(response.getResourceIds().size(), 6));
        }

        double increment = resCount * 0.5;
        // 高水平段收紧：80-89 单次最多 +1.5；90+ 单次最多 +0.5（防止刷分到100）
        if (current >= 90) {
            increment = Math.min(increment, 0.5);
        } else if (current >= 80) {
            increment = Math.min(increment, 1.5);
        }

        int newLevel = (int) Math.min(100, Math.round(current + increment));
        // 防回退：若计算出的新值低于当前值（极端边界），保持不变
        if (newLevel < current) newLevel = current;

        student.setKnowledgeLevel(newLevel);
        studentRepository.save(student);
    }

    private String buildUserPrompt(String message, Student student, Long sysUserId) {
        StringBuilder sb = new StringBuilder();
        if (student != null) {
            sb.append("学生画像：\n");
            sb.append("- 知识水平：").append(student.getKnowledgeLevel()).append("/100\n");
            sb.append("- 认知风格：").append(student.getCognitiveStyle()).append("\n");
            sb.append("- 学习节奏：").append(student.getLearningPace()).append("\n");
            sb.append("- 薄弱环节：").append(student.getWeakAreas() != null ? student.getWeakAreas() : "未知").append("\n");

            // 加入错题数据 (studentId in ErrorNotebook is sysUserId)
            List<ErrorNotebook> errors = errorRepo.findByStudentIdAndStatus(sysUserId, "active");
            if (!errors.isEmpty()) {
                Map<String, Long> kpCount = errors.stream()
                        .filter(e -> e.getKnowledgePoint() != null)
                        .collect(Collectors.groupingBy(ErrorNotebook::getKnowledgePoint, LinkedHashMap::new, Collectors.counting()));
                sb.append("- 错题知识点分布：\n");
                kpCount.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(5)
                        .forEach(e -> sb.append("  ").append(e.getKey()).append("(").append(e.getValue()).append("次)\n"));
            }
        } else {
            sb.append("学生画像：知识水平30/100，视觉型，稳扎稳打\n");
        }
        sb.append("\n学生请求：").append(message);
        return sb.toString();
    }

    private int inferStep(Student student) {
        if (student == null || student.getKnowledgeLevel() == null) return 1;
        return Math.max(1, student.getKnowledgeLevel() / 12);
    }
}
