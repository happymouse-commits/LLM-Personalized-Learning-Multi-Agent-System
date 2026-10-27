package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.ErrorNotebookRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习建议智能体 - 基于学生画像和错题数据，生成个性化学习建议
 */
@Component("adviceAgent")
public class AdviceAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「学习建议师」，一位经验丰富的C语言学习顾问。

            你的职责：
            1. 分析学生的学习画像（知识水平、认知风格、学习偏好等）
            2. 分析学生的错题数据（错题知识点分布、错误类型）
            3. 生成个性化、可执行的学习建议

            输出格式（Markdown）：
            ## 📊 学情分析
            - 当前知识水平评估
            - 主要薄弱环节

            ## 🎯 学习目标
            - 短期目标（1周内可达成）
            - 中期目标（1个月内可达成）

            ## 📚 具体建议
            1. 【知识点A】：建议...（优先级：高/中/低）
            2. 【知识点B】：建议...（优先级：高/中/低）

            ## ⏰ 时间规划
            - 每日学习建议时长
            - 重点复习时间段

            ## 💡 学习技巧
            - 针对该学生的认知风格给出学习技巧

            要求：
            - 建议必须具体、可执行，不要空泛
            - 根据学生认知风格调整建议方式
            - 优先解决高频错题的知识点
            """;

    private final StudentRepository studentRepository;
    private final ErrorNotebookRepository errorNotebookRepository;

    public AdviceAgent(StudentRepository studentRepository,
                       ErrorNotebookRepository errorNotebookRepository) {
        this.studentRepository = studentRepository;
        this.errorNotebookRepository = errorNotebookRepository;
    }

    @Override
    public String getName() { return "学习建议师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("advice:generate", "生成学习建议、个性化指导", 0.95),
            AgentCapability.of("advice:plan", "学习计划制定", 0.90),
            AgentCapability.of("advice:analyze", "学情分析与建议", 0.88)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Student student = studentRepository.findBySysUserId(context.getStudentId()).orElse(null);
        if (student == null) {
            return "暂无学生画像数据，无法生成个性化建议";
        }

        // 获取错题数据
        List<ErrorNotebook> errors = errorNotebookRepository.findByStudentIdOrderByCreatedAtDesc(context.getStudentId());

        // 构建用户提示词
        String userPrompt = buildUserPrompt(student, errors, context.getOriginalMessage());

        if (sparkClient == null) {
            return "AI服务暂不可用，请稍后再试";
        }

        // 学习建议生成是关键任务，使用CRITICAL复杂度
        return sparkClient.smartChat(SYSTEM_PROMPT, userPrompt,
                SparkClient.TaskComplexity.CRITICAL, 2048);
    }

    private String buildUserPrompt(Student student, List<ErrorNotebook> errors, String userMessage) {
        StringBuilder sb = new StringBuilder();

        // 学生画像
        sb.append("【学生画像】\n");
        sb.append("- 知识水平：").append(student.getKnowledgeLevel()).append("/100\n");
        sb.append("- 认知风格：").append(styleLabel(student.getCognitiveStyle())).append("\n");
        sb.append("- 学习偏好：").append(preferenceLabel(student.getLearningPreference())).append("\n");
        sb.append("- 学习节奏：").append(paceLabel(student.getLearningPace())).append("\n");
        sb.append("- 薄弱环节：").append(student.getWeakAreas() != null ? student.getWeakAreas() : "暂无数据").append("\n");
        sb.append("- 兴趣方向：").append(student.getInterestDirection() != null ? student.getInterestDirection() : "暂无数据").append("\n");
        sb.append("- 学习动机：").append(motivationLabel(student.getStudyMotivation())).append("\n");
        sb.append("- 连续学习天数：").append(student.getStreakDays() != null ? student.getStreakDays() : 0).append("天\n");

        // 错题分析
        if (!errors.isEmpty()) {
            sb.append("\n【错题数据】（共").append(errors.size()).append("道错题）\n");

            // 统计错题知识点分布
            Map<String, Long> kpCount = errors.stream()
                    .filter(e -> e.getKnowledgePoint() != null && !e.getKnowledgePoint().isEmpty())
                    .collect(Collectors.groupingBy(ErrorNotebook::getKnowledgePoint, LinkedHashMap::new, Collectors.counting()));

            sb.append("错题知识点分布：\n");
            kpCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> sb.append("  - ").append(e.getKey()).append("：").append(e.getValue()).append("次\n"));
        } else {
            sb.append("\n【错题数据】暂无错题记录\n");
        }

        // 用户具体问题
        if (userMessage != null && !userMessage.isEmpty()) {
            sb.append("\n【学生问题】").append(userMessage);
        } else {
            sb.append("\n【请求】请根据以上信息生成个性化学习建议");
        }

        return sb.toString();
    }

    private String styleLabel(String style) {
        if (style == null) return "未测定";
        return switch (style) {
            case "visual" -> "视觉型（适合图表、思维导图）";
            case "auditory" -> "听觉型（适合听课、讨论）";
            case "kinesthetic" -> "动觉型（适合动手实践）";
            default -> style;
        };
    }

    private String preferenceLabel(String pref) {
        if (pref == null) return "未测定";
        return switch (pref) {
            case "video" -> "视频学习";
            case "doc" -> "文档阅读";
            case "exercise" -> "练习实践";
            case "mixed" -> "混合方式";
            default -> pref;
        };
    }

    private String paceLabel(String pace) {
        if (pace == null) return "未测定";
        return switch (pace) {
            case "fast" -> "快速型";
            case "steady" -> "稳健型";
            case "slow" -> "慢速型";
            default -> pace;
        };
    }

    private String motivationLabel(String motivation) {
        if (motivation == null) return "未测定";
        return switch (motivation) {
            case "intrinsic" -> "内在驱动";
            case "extrinsic" -> "外部驱动";
            case "hybrid" -> "混合驱动";
            default -> motivation;
        };
    }
}
