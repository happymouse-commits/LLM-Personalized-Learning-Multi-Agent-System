package com.happymouse.cryd.controller;

import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.model.entity.TestRecord;
import com.happymouse.cryd.repository.ErrorNotebookRepository;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.repository.TestRecordRepository;
import com.happymouse.cryd.service.WsPushService;
import com.happymouse.cryd.service.agent.AutoResourceGenerator;
import com.happymouse.cryd.service.agent.PathPlannerAgent;
import com.happymouse.cryd.service.agent.ProfileAnalystAgent;
import com.happymouse.cryd.service.agent.ReportAgent;
import com.happymouse.cryd.service.rag.RagService;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习评估控制器 — 在线测试、错题分析、学习报告、薄弱点推荐、AI个性化分析
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final TestRecordRepository testRepo;
    private final ErrorNotebookRepository errorRepo;
    private final StudentRepository studentRepo;
    private final LearningResourceRepository resourceRepo;
    private final SparkClient sparkClient;
    private final RagService ragService;
    private final ReportAgent reportAgent;
    private final AutoResourceGenerator autoResourceGenerator;
    private final PathPlannerAgent pathPlannerAgent;
    private final ProfileAnalystAgent profileAnalystAgent;
    private final WsPushService wsPushService;

    public EvaluationController(TestRecordRepository testRepo,
                                 ErrorNotebookRepository errorRepo,
                                 StudentRepository studentRepo,
                                 LearningResourceRepository resourceRepo,
                                 SparkClient sparkClient,
                                 RagService ragService,
                                 ReportAgent reportAgent,
                                 AutoResourceGenerator autoResourceGenerator,
                                 PathPlannerAgent pathPlannerAgent,
                                 ProfileAnalystAgent profileAnalystAgent,
                                 WsPushService wsPushService) {
        this.testRepo = testRepo;
        this.errorRepo = errorRepo;
        this.studentRepo = studentRepo;
        this.resourceRepo = resourceRepo;
        this.sparkClient = sparkClient;
        this.ragService = ragService;
        this.reportAgent = reportAgent;
        this.autoResourceGenerator = autoResourceGenerator;
        this.pathPlannerAgent = pathPlannerAgent;
        this.profileAnalystAgent = profileAnalystAgent;
        this.wsPushService = wsPushService;
    }

    // 生成在线测试 — 支持根据问卷画像自适应出题
    @PostMapping("/test/generate")
    public Result<TestRecord> generateTest(@RequestBody Map<String, Object> body) {
        Long studentId = toLong(body.get("studentId"));
        String topic = (String) body.getOrDefault("topic", "C语言综合");
        int questionCount = (int) body.getOrDefault("questionCount", 10);

        // 读取问卷画像，自适应出题方向和难度
        Student student = studentId != null ? studentRepo.findById(studentId).orElse(null) : null;
        String levelHint = "";
        String interestHint = "";
        String weakHint = "";
        if (student != null) {
            int level = student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30;
            if (level <= 20) levelHint = "难度：偏基础，侧重概念理解，避免复杂代码";
            else if (level <= 50) levelHint = "难度：中等偏基础，兼顾概念和简单应用";
            else if (level <= 75) levelHint = "难度：中等偏进阶，侧重代码分析和应用";
            else levelHint = "难度：进阶，侧重综合应用和易错点";

            String interest = student.getInterestDirection();
            if (interest != null && !interest.isEmpty()) {
                interestHint = "重点方向：" + interest + "，题目优先覆盖这些知识点";
            }
            String weak = student.getWeakAreas();
            if (weak != null && !weak.isEmpty() && !"待分析".equals(weak)) {
                weakHint = "必须包含薄弱知识点：" + weak + "的题目，至少2道";
            }
        }

        String prompt = "你是C语言出题专家。请生成" + questionCount + "道「" + topic + "」的测试题。\n"
            + (levelHint.isEmpty() ? "" : levelHint + "\n")
            + (interestHint.isEmpty() ? "" : interestHint + "\n")
            + (weakHint.isEmpty() ? "" : weakHint + "\n")
            + "要求：\n"
            + "- 4个选项(A/B/C/D)，只有一个正确答案\n"
            + "- 每题附带解析\n"
            + "- 难度递进\n"
            + "严格以JSON数组格式输出: [{\"id\":1,\"question\":\"...\",\"options\":[\"A.选项A\",\"B.选项B\",\"C.选项C\",\"D.选项D\"],\"answer\":\"A\",\"knowledgePoint\":\"知识点\",\"explanation\":\"解析\"}]";

        try {
            String questionsJson = sparkClient.smartChat(prompt, "自适应生成测试题目",
                    SparkClient.TaskComplexity.MEDIUM, 2048);
            String json = questionsJson.replaceAll("```json|```", "").trim();

            TestRecord test = new TestRecord();
            test.setStudentId(studentId);
            test.setTestName(topic + "测试");
            test.setQuestions(json);
            test.setStatus("in_progress");
            testRepo.save(test);

            return Result.success(test);
        } catch (Exception e) {
            return Result.error("测试生成失败: " + e.getMessage());
        }
    }

    // 提交测试 — 自动评分 + 画像更新 + 资源生成
    @PostMapping("/test/{testId}/submit")
    public Result<TestRecord> submitTest(@PathVariable Long testId, @RequestBody Map<String, Object> body) {
        TestRecord test = testRepo.findById(testId).orElse(null);
        if (test == null) return Result.error("测试不存在");

        String answers = (String) body.get("answers");
        test.setAnswers(answers);
        test.setStatus("completed");
        test.setCompletedAt(LocalDateTime.now());
        testRepo.save(test);

        // ===== 答题后自动管道：评分→画像更新→资源生成 =====
        Long studentId = test.getStudentId();
        if (studentId != null) {
            try {
                // 1. 自动评分 + 统计正确率
                int totalCorrect = 0, totalCount = 0;
                Set<String> wrongKnowledgePoints = new HashSet<>();
                if (answers != null && test.getQuestions() != null) {
                    try {
                        String qJson = test.getQuestions().replaceAll("```json|```", "").trim();
                        List<Map> questions = com.alibaba.fastjson2.JSON.parseArray(qJson, Map.class);
                        Map<String, String> answerMap = com.alibaba.fastjson2.JSON.parseObject(answers, Map.class);
                        if (questions != null && answerMap != null) {
                            totalCount = questions.size();
                            for (Map q : questions) {
                                String qId = String.valueOf(q.get("id") != null ? q.get("id") : questions.indexOf(q));
                                String correct = String.valueOf(q.get("answer"));
                                String userAns = answerMap.get(qId);
                                if (correct.equalsIgnoreCase(userAns)) {
                                    totalCorrect++;
                                } else {
                                    String kp = (String) q.get("knowledgePoint");
                                    if (kp != null) wrongKnowledgePoints.add(kp);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("测试评分解析失败: {}", e.getMessage());
                    }
                }

                // 2. 调用ProfileAnalystAgent分析用户画像（包括薄弱点）
                int correctRate = totalCount > 0 ? (totalCorrect * 100 / totalCount) : 0;
                try {
                    // 构造消息给ProfileAnalystAgent，描述答题结果
                    String profileMessage = String.format(
                        "能力检测结果：正确率%d%%，答错的知识点：%s。请根据这个结果更新我的学习画像。",
                        correctRate,
                        wrongKnowledgePoints.isEmpty() ? "无" : String.join("、", wrongKnowledgePoints)
                    );
                    AgentContext profileCtx = new AgentContext(studentId, profileMessage, null);
                    profileAnalystAgent.execute(profileCtx);
                    log.info("用户画像分析完成: studentId={}, 正确率={}", studentId, correctRate);
                } catch (Exception e) {
                    log.warn("用户画像分析失败: studentId={}, error={}", studentId, e.getMessage());
                }

                // 3. 调用PathPlannerAgent生成学习路径
                try {
                    AgentContext pathCtx = new AgentContext(studentId, "请根据我的学习画像生成个性化学习路径", null);
                    pathPlannerAgent.execute(pathCtx);
                    log.info("学习路径生成完成: studentId={}", studentId);
                } catch (Exception e) {
                    log.warn("学习路径生成失败: studentId={}, error={}", studentId, e.getMessage());
                }

                // 4. 调用AutoResourceGenerator生成学习资源
                // 首次测试：为每个章节生成全套资源
                long existingResources = resourceRepo.countByStudentId(studentId);
                if (existingResources == 0) {
                    log.info("首次测试，触发全量章节资源生成: studentId={}", studentId);
                    var allGenerated = autoResourceGenerator.generateAllChapterResources(studentId);
                    log.info("全量章节资源生成完成: studentId={}, count={}", studentId, allGenerated.size());
                } else if (autoResourceGenerator.shouldAutoGenerate(studentId)) {
                    var generated = autoResourceGenerator.autoGenerate(studentId);
                    log.info("答题后自动生成资源: studentId={}, count={}", studentId, generated.size());
                }

                // 5. WebSocket 推送通知前端刷新
                wsPushService.pushProfileUpdate(studentId);
                wsPushService.pushResourceUpdate(studentId);

            } catch (Exception e) {
                log.error("答题后管道执行失败: studentId={}, error={}", studentId, e.getMessage(), e);
            }
        }

        return Result.success(test);
    }

    // 获取测试记录
    @GetMapping("/test/student/{studentId}")
    public Result<List<TestRecord>> getTests(@PathVariable Long studentId) {
        return Result.success(testRepo.findByStudentIdOrderByStartedAtDesc(studentId));
    }

    // 获取错题本
    @GetMapping("/errors/student/{studentId}")
    public Result<List<ErrorNotebook>> getErrors(@PathVariable Long studentId) {
        return Result.success(errorRepo.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    // 添加错题
    @PostMapping("/errors")
    public Result<ErrorNotebook> addError(@RequestBody Map<String, Object> body) {
        ErrorNotebook error = new ErrorNotebook();
        error.setStudentId(toLong(body.get("studentId")));
        error.setQuestion((String) body.get("question"));
        error.setStudentAnswer((String) body.get("studentAnswer"));
        error.setCorrectAnswer((String) body.get("correctAnswer"));
        error.setKnowledgePoint((String) body.get("knowledgePoint"));
        error.setDifficulty((String) body.get("difficulty"));
        errorRepo.save(error);
        return Result.success(error);
    }

    // 错题分析
    @PostMapping("/errors/{errorId}/analyze")
    public Result<ErrorNotebook> analyzeError(@PathVariable Long errorId) {
        ErrorNotebook error = errorRepo.findById(errorId).orElse(null);
        if (error == null) return Result.error("错题不存在");

        String prompt = "分析这道C语言错题，给出错误原因和学习建议。"
            + "题目:" + error.getQuestion() + ", 学生答案:" + error.getStudentAnswer()
            + ", 正确答案:" + error.getCorrectAnswer();

        try {
            String analysis = sparkClient.chat(prompt, "", 0.3f, 256);
            error.setAnalysis(analysis);
            errorRepo.save(error);
            return Result.success(error);
        } catch (Exception e) {
            return Result.error("分析失败: " + e.getMessage());
        }
    }

    // 薄弱点推荐
    @GetMapping("/weakness/{studentId}")
    public Result<?> getWeakness(@PathVariable Long studentId) {
        List<ErrorNotebook> errors = errorRepo.findByStudentIdAndStatus(studentId, "active");
        // 统计每个知识点的错误次数
        Map<String, Long> freq = new java.util.LinkedHashMap<>();
        for (ErrorNotebook e : errors) {
            String kp = e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "未知";
            freq.merge(kp, 1L, Long::sum);
        }
        // 按频率排序
        var sorted = freq.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(e -> Map.of("knowledgePoint", e.getKey(), "errorCount", e.getValue()))
            .toList();

        return Result.success(Map.of("weakPoints", sorted, "total", errors.size()));
    }

    // ===== AI 个性化学习分析 =====

    /**
     * AI 智能体对学生近期学习情况进行全方位个性化分析
     * 综合画像、错题、测试记录，RAG检索知识库增强分析质量
     * @param sysUserId 前端 store.id（SysUser ID）
     */
    @GetMapping("/analysis/{sysUserId}")
    public Result<Map<String, Object>> getPersonalAnalysis(@PathVariable Long sysUserId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // SysUser ID → Student 实体
        Student student = studentRepo.findBySysUserId(sysUserId).orElse(null);
        if (student == null) return Result.error(404, "学生画像未构建，请先完成引导");
        Long studentId = student.getId();

        String weakAreas = student.getWeakAreas() != null ? student.getWeakAreas() : "暂无数据";
        String cognitiveStyle = student.getCognitiveStyle() != null ? student.getCognitiveStyle() : "未测定";
        String learningPreference = student.getLearningPreference() != null ? student.getLearningPreference() : "未测定";
        int knowledgeLevel = student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30;
        int studyMinutes = student.getTotalStudyMinutes() != null ? student.getTotalStudyMinutes() : 0;
        int progress = student.getProgress() != null ? student.getProgress() : 0;

        // 错题统计
        List<ErrorNotebook> errors = errorRepo.findByStudentIdAndStatus(studentId, "active");
        Map<String, Long> errorFreq = errors.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "未知",
                        Collectors.counting()));
        String topErrors = errorFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).map(e -> e.getKey() + "(" + e.getValue() + "次)")
                .collect(Collectors.joining("、"));
        if (topErrors.isEmpty()) topErrors = "暂无错题记录";

        // 测试统计
        List<TestRecord> tests = testRepo.findByStudentIdOrderByStartedAtDesc(studentId);
        long completedTests = tests.stream().filter(t -> "completed".equals(t.getStatus())).count();

        // RAG 检索
        boolean ragUsed = false;
        try {
            var fragments = ragService.retrieveRelevant(weakAreas, 3);
            if (!fragments.isEmpty()) ragUsed = true;
        } catch (Exception ignored) {}

        // 使用 ReportAgent（报告智能体）生成个性化学习分析
        try {
            String analysis = reportAgent.generateReport(studentId, "weekly");
            result.put("analysis", analysis != null && !analysis.contains("失败") ? analysis : "分析生成失败");
        } catch (Exception e) {
            result.put("analysis", "AI分析暂不可用");
        }

        result.put("studentId", studentId);
        result.put("knowledgeLevel", knowledgeLevel);
        result.put("progress", progress);
        result.put("errorCount", errors.size());
        result.put("ragUsed", ragUsed);

        return Result.success(result);
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}
