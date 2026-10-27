package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱智能体 - 动态维护C语言知识图谱，分析知识点关联关系
 */
@Component("knowledgeGraphAgent")
public class KnowledgeGraphAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「知识图谱工程师」，负责维护和优化C语言知识图谱。

            C语言核心知识体系：
            ┌─────────────────────────────────────────────────────────────┐
            │                        C语言知识图谱                        │
            ├─────────────────────────────────────────────────────────────┤
            │ 基础层：数据类型 → 运算符 → 表达式 → 语句                    │
            │   ↓                                                        │
            │ 结构层：选择结构 → 循环结构 → 函数 → 数组                    │
            │   ↓                                                        │
            │ 进阶层：指针 → 字符串 → 结构体 → 文件操作                    │
            │   ↓                                                        │
            │ 高级层：动态内存 → 预处理 → 多文件编译                       │
            └─────────────────────────────────────────────────────────────┘

            你的任务：
            1. 分析学生已掌握和未掌握的知识点
            2. 计算知识点之间的前置依赖关系
            3. 推荐最优学习路径
            4. 生成知识图谱可视化数据（JSON格式）

            输出格式（JSON）：
            {
              "nodes": [
                {"id": "基础语法", "name": "基础语法", "level": 1, "status": "mastered|learning|weak|locked", "score": 85}
              ],
              "edges": [
                {"source": "基础语法", "target": "选择结构", "type": "prerequisite"}
              ],
              "recommendations": [
                {"action": "next", "target": "指针", "reason": "前置知识已掌握"}
              ],
              "analysis": "学生当前处于...阶段，建议..."
            }

            知识点状态说明：
            - mastered: 已掌握（得分≥80）
            - learning: 学习中（得分60-79）
            - weak: 薄弱（得分<60）
            - locked: 未解锁（前置知识未掌握）
            """;

    private final StudentRepository studentRepository;
    private final LearningResourceRepository resourceRepository;

    public KnowledgeGraphAgent(StudentRepository studentRepository,
                               LearningResourceRepository resourceRepository) {
        this.studentRepository = studentRepository;
        this.resourceRepository = resourceRepository;
    }

    @Override
    public String getName() { return "知识图谱工程师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("graph:analyze", "知识图谱分析、知识点关联", 0.95),
            AgentCapability.of("graph:update", "更新知识图谱状态", 0.90),
            AgentCapability.of("graph:recommend", "学习路径推荐", 0.88)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Student student = studentRepository.findBySysUserId(context.getStudentId()).orElse(null);
        if (student == null) {
            return "暂无学生数据，无法生成知识图谱";
        }

        // 获取学生资源数据
        List<LearningResource> resources = resourceRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());

        // 构建用户提示词
        String userPrompt = buildUserPrompt(student, resources);

        if (sparkClient == null) {
            return "AI服务暂不可用，请稍后再试";
        }

        // 知识图谱分析是关键任务，使用CRITICAL复杂度
        return sparkClient.smartChat(SYSTEM_PROMPT, userPrompt,
                SparkClient.TaskComplexity.CRITICAL, 3000);
    }

    private String buildUserPrompt(Student student, List<LearningResource> resources) {
        StringBuilder sb = new StringBuilder();

        // 学生基本信息
        sb.append("【学生信息】\n");
        sb.append("- 知识水平：").append(student.getKnowledgeLevel()).append("/100\n");
        sb.append("- 薄弱环节：").append(student.getWeakAreas() != null ? student.getWeakAreas() : "暂无数据\n");

        // 资源学习情况
        if (!resources.isEmpty()) {
            sb.append("\n【已学习资源】（共").append(resources.size()).append("个）\n");

            // 按知识点统计
            Map<String, Long> kpCount = resources.stream()
                    .filter(r -> r.getKnowledgePoint() != null && !r.getKnowledgePoint().isEmpty())
                    .collect(Collectors.groupingBy(LearningResource::getKnowledgePoint, Collectors.counting()));

            sb.append("知识点覆盖：\n");
            kpCount.forEach((kp, count) ->
                sb.append("  - ").append(kp).append("：").append(count).append("个资源\n")
            );

            // 按类型统计
            Map<String, Long> typeCount = resources.stream()
                    .filter(r -> r.getType() != null)
                    .collect(Collectors.groupingBy(LearningResource::getType, Collectors.counting()));

            sb.append("资源类型分布：\n");
            typeCount.forEach((type, count) ->
                sb.append("  - ").append(type).append("：").append(count).append("个\n")
            );
        } else {
            sb.append("\n【已学习资源】暂无学习记录\n");
        }

        sb.append("\n【请求】请分析该学生的知识图谱状态，生成JSON格式的图谱数据和学习建议");

        return sb.toString();
    }
}
