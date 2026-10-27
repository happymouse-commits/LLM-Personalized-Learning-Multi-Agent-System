package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.*;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.agent.ExerciseAgent;
import com.happymouse.cryd.service.agent.PathPlannerAgent;
import com.happymouse.cryd.service.agent.AutoResourceGenerator;
import com.happymouse.cryd.service.agent.CourseResourceRecommender;
import com.happymouse.cryd.service.WsPushService;
import com.happymouse.cryd.service.rag.RagService;
import com.happymouse.cryd.service.spark.SparkClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 刷题房控制器 —— 教师作业、错题回练、AI错题知识点总结。
 *
 * <p>三区功能：
 * <ul>
 *   <li>核心区 — 作业接收与作答，自动评分 + 错题沉淀</li>
 *   <li>错题区 — 错题列表（按课程/章节/次数筛选），标记已掌握，AI解析</li>
 *   <li>突破区 — 多智能体资源生成（讲解/思维导图/练习/代码）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    private final ChapterRepository chapterRepository;
    private final ChapterProgressRepository progressRepository;
    private final StudentRepository studentRepository;
    private final SysUserRepository sysUserRepository;
    private final CourseRepository courseRepository;
    private final ErrorNotebookRepository errorRepo;
    private final LearningResourceRepository resourceRepo;
    private final SparkClient sparkClient;
    private final ExerciseAgent exerciseAgent;
    private final RagService ragService;
    private final PathPlannerAgent pathPlanner;
    private final AutoResourceGenerator autoResourceGenerator;
    private final CourseResourceRecommender courseRecommender;
    private final WsPushService wsPushService;
    private final Executor agentTaskExecutor;

    public PracticeController(ChapterRepository chapterRepository,
                              ChapterProgressRepository progressRepository,
                              StudentRepository studentRepository,
                              SysUserRepository sysUserRepository,
                              CourseRepository courseRepository,
                              ErrorNotebookRepository errorRepo,
                              LearningResourceRepository resourceRepo,
                              SparkClient sparkClient,
                              ExerciseAgent exerciseAgent,
                              RagService ragService,
                              PathPlannerAgent pathPlanner,
                              AutoResourceGenerator autoResourceGenerator,
                              CourseResourceRecommender courseRecommender,
                              WsPushService wsPushService,
                              @Qualifier("agentTaskExecutor") Executor agentTaskExecutor) {
        this.chapterRepository = chapterRepository;
        this.progressRepository = progressRepository;
        this.studentRepository = studentRepository;
        this.sysUserRepository = sysUserRepository;
        this.courseRepository = courseRepository;
        this.errorRepo = errorRepo;
        this.resourceRepo = resourceRepo;
        this.sparkClient = sparkClient;
        this.exerciseAgent = exerciseAgent;
        this.ragService = ragService;
        this.pathPlanner = pathPlanner;
        this.autoResourceGenerator = autoResourceGenerator;
        this.courseRecommender = courseRecommender;
        this.wsPushService = wsPushService;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    // ==================== 核心区：作业接收与作答 ====================

    /**
     * 获取学生作业列表，按状态分类
     */
    @GetMapping("/assignments/{studentId}")
    public Result<Map<String, Object>> getAssignments(@PathVariable Long studentId) {
        List<ChapterProgress> myProgress = progressRepository.findByStudentId(studentId);
        Map<Long, ChapterProgress> progressMap = new HashMap<>();
        for (ChapterProgress cp : myProgress) {
            progressMap.put(cp.getChapterId(), cp);
        }

        List<Chapter> publishedChapters = chapterRepository.findByStatusOrderByOrderNum("published");
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> completed = new ArrayList<>();
        List<Map<String, Object>> graded = new ArrayList<>();

        for (Chapter ch : publishedChapters) {
            ChapterProgress cp = progressMap.get(ch.getId());
            if (cp == null) continue;

            Map<String, Object> item = buildChapterItem(ch, cp);
            courseRepository.findById(ch.getCourseId()).ifPresent(c -> item.put("courseName", c.getName()));

            String status = cp.getStatus();
            if ("completed".equals(status) && cp.getGradedBy() != null) {
                graded.add(item);
            } else if ("completed".equals(status)) {
                completed.add(item);
            } else {
                pending.add(item);
            }
        }

        // 统计
        long errorCount = errorRepo.countByStudentIdAndStatus(studentId, "active");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAssignments", myProgress.size());
        summary.put("pendingCount", pending.size());
        summary.put("completedCount", completed.size() + graded.size());
        summary.put("gradedCount", graded.size());
        summary.put("errorCount", errorCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("pending", pending);
        result.put("completed", completed);
        result.put("graded", graded);
        return Result.success(result);
    }

    /**
     * 获取关卡题目（给学生答题用）
     * 当题库题目不足5题时，自动触发AI动态出题
     */
    @GetMapping("/assignment/{chapterId}/questions")
    public Result<Map<String, Object>> getQuestions(@PathVariable Long chapterId,
                                                    @RequestParam Long studentId) {
        return chapterRepository.findById(chapterId).map(ch -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chapterId", ch.getId());
            result.put("chapterName", ch.getName());

            // 检查题库是否充足，不足则AI动态生成
            String questionsJson = ch.getQuestions();
            int questionCount = questionsJson != null ? getQuestionCount(questionsJson) : 0;
            if (questionCount < 5) {
                log.info("章节「{}」题库不足(当前{}题)，触发AI动态出题", ch.getName(), questionCount);
                JSONArray aiQuestions = generateQuestionsWithAI(ch);
                if (aiQuestions != null && !aiQuestions.isEmpty()) {
                    JSONArray allQuestions = new JSONArray();
                    if (questionsJson != null && questionCount > 0) {
                        try { allQuestions.addAll(JSON.parseArray(questionsJson)); } catch (Exception ignored) {}
                    }
                    allQuestions.addAll(aiQuestions);
                    questionsJson = allQuestions.toJSONString();
                    ch.setQuestions(questionsJson);
                    chapterRepository.save(ch);
                    log.info("章节「{}」AI动态出题完成: +{}题, 共{}题", ch.getName(), aiQuestions.size(), allQuestions.size());
                } else {
                    log.warn("章节「{}」AI动态出题失败，使用现有{}题", ch.getName(), questionCount);
                }
            }
            result.put("questions", questionsJson);

            progressRepository.findByChapterIdAndStudentId(chapterId, studentId).ifPresent(cp -> {
                result.put("previousAnswers", cp.getAnswers());
                result.put("previousScore", cp.getScore());
                result.put("previousStatus", cp.getStatus());
                result.put("previousFeedback", cp.getFeedback());
            });
            return Result.success(result);
        }).orElse(Result.<Map<String, Object>>error(404, "关卡不存在"));
    }

    /**
     * 提交答案 — 自动评分 + 错题沉淀 + 画像更新
     */
    @PostMapping("/assignment/{chapterId}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long chapterId,
                                              @RequestBody Map<String, Object> body) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        String answers = body.get("answers").toString();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) return Result.error(404, "关卡不存在");

        // 更新或创建进度记录
        ChapterProgress cp = progressRepository.findByChapterIdAndStudentId(chapterId, studentId)
                .orElse(new ChapterProgress());
        cp.setChapterId(chapterId);
        cp.setStudentId(studentId);
        cp.setAnswers(answers);
        cp.setStatus("submitted");
        cp.setSubmittedAt(LocalDateTime.now());
        progressRepository.save(cp);

        try {
            JSONArray questions = JSON.parseArray(chapter.getQuestions());
            JSONObject studentAnswers = JSON.parseObject(answers);
            int total = questions.size();
            int correct = 0;
            StringBuilder fb = new StringBuilder();
            List<String> newWeakAreas = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                JSONObject q = questions.getJSONObject(i);
                String type = q.getString("type");
                String correctAnswer = q.getString("answer");
                String studentAnswer = studentAnswers.getString(String.valueOf(i + 1));
                String knowledgePoint = q.getString("knowledgePoint");
                int qIndex = i + 1;

                boolean isCorrect;
                if ("code".equals(type)) {
                    if (studentAnswer != null && studentAnswer.length() >= 10) {
                        String aiFeedback = gradeCodeQuestion(q.getString("content"),
                                correctAnswer, studentAnswer);
                        isCorrect = aiFeedback.contains("通过") || aiFeedback.contains("正确");
                        fb.append(aiFeedback);
                    } else {
                        fb.append("✗第").append(qIndex).append("题(未作答) ");
                        isCorrect = false;
                    }
                } else {
                    isCorrect = studentAnswer != null && correctAnswer != null
                            && correctAnswer.equalsIgnoreCase(studentAnswer.trim());
                    if (isCorrect) {
                        correct++;
                        fb.append("✓第").append(qIndex).append("题 ");
                    } else {
                        fb.append("✗第").append(qIndex).append("题 ");
                    }
                }

                if (!isCorrect) {
                    // 错题自动沉淀到 ErrorNotebook
                    saveErrorNotebook(studentId, chapterId, q, studentAnswer, correctAnswer, qIndex);
                    if (knowledgePoint != null && !knowledgePoint.isEmpty()) {
                        newWeakAreas.add(knowledgePoint);
                    }
                }
            }

            int score = (int) Math.round((double) correct / total * 100);
            cp.setScore(score);
            cp.setFeedback(fb.toString());
            cp.setStatus("completed");
            cp.setGradedBy("system");
            cp.setGradedAt(LocalDateTime.now());
            progressRepository.save(cp);

            // 更新学生画像
            updateStudentProfile(studentId, score, newWeakAreas);

            // ★ 动态调整闭环：评估后→画像更新→路径重规划
            String adjustedInfo = null;
            try {
                var student = studentRepository.findById(studentId).orElse(null);
                if (student != null) {
                    pathPlanner.updateProgress(new com.happymouse.cryd.model.dto.ChatRequest() {{
                        setStudentId(studentId);
                        setMessage("课后评估完成: score=" + score + ", weakAreas=" + (student.getWeakAreas() != null ? student.getWeakAreas() : ""));
                    }});
                    adjustedInfo = score < 60 ? "⚠️ 得分偏低，学习路径已自动调整，建议加强「" + newWeakAreas.stream().limit(3).reduce("", (a,b)->a+" "+b) + "」" :
                                   score < 80 ? "📊 表现良好，路径已微调" :
                                   "🎉 优秀！路径已升级到下一阶段";
                }
            } catch (Exception e) {
                log.warn("路径重规划失败: {}", e.getMessage());
            }

            // 每次提交后立即触发AI错题解析（异步，不阻塞响应）
            final Long finalCourseId = chapter.getCourseId();
            final Long finalStudentId = studentId;
            final Long finalChapterId = chapterId;
            agentTaskExecutor.execute(() -> {
                try {
                    generateAiErrorAnalysis(finalStudentId, finalChapterId, finalCourseId);
                } catch (Exception ex) {
                    log.warn("AI错题解析异步生成失败: {}", ex.getMessage());
                }
            });

            // 异步推送错题对应视频资源 + 自动生成针对性学习资源
            if (!newWeakAreas.isEmpty()) {
                agentTaskExecutor.execute(() -> {
                    try {
                        int count = courseRecommender.recommendAndSave(studentId);
                        if (count > 0) {
                            log.info("错题推送视频: studentId={}, count={}", studentId, count);
                        }
                        // 根据错题自动生成讲解/练习/思维导图等资源
                        if (autoResourceGenerator.shouldAutoGenerate(studentId)) {
                            var generated = autoResourceGenerator.autoGenerate(studentId);
                            log.info("练习后自动生成资源: studentId={}, count={}", studentId, generated.size());
                        }
                        wsPushService.pushResourceUpdate(studentId);
                        wsPushService.pushProfileUpdate(studentId);
                    } catch (Exception ex) {
                        log.warn("练习后资源生成失败: studentId={}", studentId, ex);
                    }
                });
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("score", score);
            result.put("correctCount", correct);
            result.put("totalCount", total);
            result.put("feedback", fb.toString());
            result.put("adjustedInfo", adjustedInfo);
            return Result.success(result);
        } catch (Exception e) {
            log.error("自动评分失败", e);
            return Result.error(500, "评分失败：" + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> buildChapterItem(Chapter ch, ChapterProgress cp) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", ch.getId());
        item.put("name", ch.getName());
        item.put("description", ch.getDescription());
        item.put("orderNum", ch.getOrderNum());
        item.put("status", cp.getStatus());
        item.put("score", cp.getScore());
        item.put("feedback", cp.getFeedback());
        item.put("submittedAt", cp.getSubmittedAt());
        item.put("gradedAt", cp.getGradedAt());
        item.put("questionCount", getQuestionCount(ch.getQuestions()));
        return item;
    }

    private static final String AI_QUESTION_GEN_PROMPT = """
        你是C语言程序设计出题专家。请根据给定的章节信息，生成4道选择题和1道代码题。

        必须严格遵守以下要求：
        1. 选择题：4个选项(A/B/C/D)，每个选项有意义且有且仅有一个正确答案
        2. 代码题：有明确的输入输出要求，参考答案是完整可运行的C代码
        3. 每题必须标注知识点(knowledgePoint)和解析(analysis)
        4. 题目难度适中，适合大学本科生水平
        5. 选择题的options必须是包含key和value的对象数组

        必须输出纯JSON数组格式（不要```json标记，不要其他任何文字），格式如下：
        [{"type":"choice","content":"题目","options":[{"key":"A","value":"选项A"},{"key":"B","value":"选项B"},{"key":"C","value":"选项C"},{"key":"D","value":"选项D"}],"answer":"A","knowledgePoint":"知识点","analysis":"解析"},
         {"type":"code","content":"题目描述（含输入输出要求）","answer":"参考答案C代码","knowledgePoint":"知识点","analysis":"解题思路和考点分析"}]
        """;

    /**
     * 当题库题目不足时，AI动态生成题目。返回生成的JSONArray，失败返回null
     */
    private JSONArray generateQuestionsWithAI(Chapter chapter) {
        String chapterName = chapter.getName();
        String chapterDesc = chapter.getDescription() != null ? chapter.getDescription() : "";
        String prompt = AI_QUESTION_GEN_PROMPT
                + "\n\n章节名称：" + chapterName
                + "\n章节描述：" + chapterDesc
                + "\n\n请严格按照上述JSON格式生成题目。";

        try {
            // 题目生成是中等复杂度任务
            String aiReply = sparkClient.smartChat(prompt, "请为「" + chapterName + "」生成题目",
                    SparkClient.TaskComplexity.MEDIUM, 2048);
            if (aiReply == null || aiReply.trim().isEmpty()) {
                log.warn("AI出题返回空内容: chapter={}", chapterName);
                return null;
            }
            String jsonStr = extractJsonArray(aiReply);
            if (jsonStr == null) {
                log.warn("AI出题首次解析失败，重试中: chapter={}", chapterName);
                // 重试时降低随机性
                aiReply = sparkClient.smartChat(prompt + "\n\n【重要】上次格式错误，只输出纯JSON数组，不要任何解释。",
                        "出题重试-" + chapterName, SparkClient.TaskComplexity.HIGH, 2048);
                jsonStr = extractJsonArray(aiReply);
            }
            if (jsonStr == null) {
                log.error("AI出题重试仍失败: chapter={}", chapterName);
                return null;
            }
            JSONArray questions = JSON.parseArray(jsonStr);
            log.info("AI动态出题成功: chapter={}, count={}", chapterName, questions.size());
            return questions;
        } catch (Exception e) {
            log.error("AI动态出题异常: chapter={}, error={}", chapterName, e.getMessage());
            return null;
        }
    }

    /**
     * 从AI回复中提取JSON数组（多策略容错，适配 LLM 输出特性）
     */
    private String extractJsonArray(String raw) {
        if (raw == null) return null;
        String text = raw.trim();

        // 策略1: 去掉markdown标记后提取
        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = cleaned.substring(start, end + 1);
            if (isValidJsonArray(candidate)) return candidate;
        }

        // 策略2: 修复常见JSON格式问题 + LLM常见输出修复
        start = text.indexOf('[');
        end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = text.substring(start, end + 1);
            candidate = candidate.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            // 尾逗号修复
            candidate = candidate.replaceAll(",\\s*]", "]");
            candidate = candidate.replaceAll(",\\s*}", "}");
            // LLM 常见: 字符串值中含未转义换行
            candidate = candidate.replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n");
            if (isValidJsonArray(candidate)) return candidate;
            // LLM 常见: 中文引号混入
            candidate = candidate.replace('“', '"').replace('”', '"');
            candidate = candidate.replace('‘', '\'').replace('’', '\'');
            if (isValidJsonArray(candidate)) return candidate;
        }

        // 策略3: 修复缺失的逗号（LLM有时在数组元素间漏掉逗号）
        start = text.indexOf('[');
        end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = text.substring(start, end + 1);
            candidate = candidate.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            candidate = candidate.replaceAll("}\\s*\\{", "},{");
            candidate = candidate.replaceAll(",\\s*]", "]");
            candidate = candidate.replaceAll(",\\s*}", "}");
            if (isValidJsonArray(candidate)) return candidate;
        }

        // 策略4: 逐个提取JSON对象组装
        try {
            List<String> objects = new ArrayList<>();
            int depth = 0;
            StringBuilder current = new StringBuilder();
            boolean inString = false;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '"' && (i == 0 || text.charAt(i - 1) != '\\')) inString = !inString;
                if (!inString) {
                    if (ch == '{') {
                        if (depth == 0) current = new StringBuilder();
                        depth++;
                    }
                    if (depth > 0) current.append(ch);
                    if (ch == '}') {
                        depth--;
                        if (depth == 0) objects.add(current.toString());
                    }
                } else if (depth > 0) {
                    current.append(ch);
                }
            }
            if (!objects.isEmpty()) return "[" + String.join(",", objects) + "]";
        } catch (Exception ignored) {}

        return null;
    }

    private boolean isValidJsonArray(String str) {
        try { JSON.parseArray(str); return true; } catch (Exception e) { return false; }
    }

    private void saveErrorNotebook(Long studentId, Long chapterId, JSONObject question,
                                   String studentAnswer, String correctAnswer, int qIndex) {
        // 避免重复添加同一道错题
        if (errorRepo.findByStudentIdAndChapterIdAndQuestionIndex(studentId, chapterId, qIndex).isPresent()) {
            return;
        }
        ErrorNotebook error = new ErrorNotebook();
        error.setStudentId(studentId);
        error.setChapterId(chapterId);
        error.setQuestionIndex(qIndex);
        error.setQuestion(question.getString("content"));
        error.setStudentAnswer(studentAnswer);
        error.setCorrectAnswer(correctAnswer);
        error.setKnowledgePoint(question.getString("knowledgePoint"));
        error.setDifficulty(question.getString("difficulty"));
        error.setStatus("active");
        error.setWrongCount(1);
        // AI 分析错误原因
        String et = analyzeErrorType(question.getString("content"), studentAnswer, correctAnswer);
        error.setErrorType(et);
        error.setErrorTag(mapErrorTag(et));
        errorRepo.save(error);
    }

    private String analyzeErrorType(String question, String studentAnswer, String correctAnswer) {
        String prompt = "分析以下错题的错误类型，只回复一个单词：concept(概念不清)、calculation(计算错误)、misread(审题偏差)、logic(逻辑错误)。\n"
                + "题目：" + (question != null ? question : "") + "\n"
                + "学生答案：" + (studentAnswer != null ? studentAnswer : "") + "\n"
                + "正确答案：" + (correctAnswer != null ? correctAnswer : "");
        try {
            String reply = sparkClient.chat(prompt, "分析错误类型", 0.3f, 50);
            if (reply.contains("concept") || reply.contains("概念")) return "concept";
            if (reply.contains("calculation") || reply.contains("计算")) return "calculation";
            if (reply.contains("misread") || reply.contains("审题")) return "misread";
            if (reply.contains("logic") || reply.contains("逻辑")) return "logic";
            return "concept";
        } catch (Exception e) {
            return "concept";
        }
    }

    private String mapErrorTag(String errorType) {
        return switch (errorType) {
            case "concept" -> "概念不清";
            case "calculation" -> "计算错误";
            case "misread" -> "审题偏差";
            case "logic" -> "代码逻辑错误";
            default -> "概念不清";
        };
    }

    private void updateStudentProfile(Long studentId, int score, List<String> newWeakAreas) {
        studentRepository.findBySysUserId(studentId).ifPresent(student -> {
            // 更新进度
            int currentProgress = student.getProgress() != null ? student.getProgress() : 0;
            student.setProgress(Math.min(100, currentProgress + 5));

            // 更新知识水平
            int currentLevel = student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30;
            int adjustment = score >= 80 ? 3 : score >= 60 ? 1 : -1;
            student.setKnowledgeLevel(Math.max(0, Math.min(100, currentLevel + adjustment)));

            // 更新薄弱环节
            if (!newWeakAreas.isEmpty()) {
                String existingWeak = student.getWeakAreas() != null ? student.getWeakAreas() : "";
                Set<String> areas = new LinkedHashSet<>();
                if (!existingWeak.isEmpty()) {
                    areas.addAll(Arrays.asList(existingWeak.split(",\\s*")));
                }
                areas.addAll(newWeakAreas);
                student.setWeakAreas(String.join(", ", areas));
            }

            // 累加学习时长
            int minutes = student.getTotalStudyMinutes() != null ? student.getTotalStudyMinutes() : 0;
            student.setTotalStudyMinutes(minutes + 15);

            studentRepository.save(student);
            log.info("学生画像已更新: studentId={}, score={}", studentId, score);
        });
    }

    private String gradeCodeQuestion(String questionContent, String referenceAnswer, String studentAnswer) {
        String prompt = "你是C语言代码评阅老师。\n"
            + "题目：" + questionContent + "\n"
            + "参考答案：\n" + referenceAnswer + "\n"
            + "学生代码：\n" + studentAnswer + "\n"
            + "请判断学生代码是否正确实现了题目要求。\n"
            + "如果正确，回复：代码正确，通过！\n"
            + "如果有小问题，回复：基本正确，但有小问题\n"
            + "如果错误，回复：代码未通过\n"
            + "只回复评语，不要重复代码。";
        try {
            String feedback = sparkClient.chat(prompt, studentAnswer, 0.3f, 200);
            return feedback.trim() + " ";
        } catch (Exception e) {
            log.warn("AI代码评阅失败：{}", e.getMessage());
            return "? 代码题待老师评阅 ";
        }
    }

    /**
     * 每次学生提交后立即触发AI错题解析 — 为本次提交产生的错题生成分析
     * 同时生成知识库学习资料，学生可在刷题房「AI错题解析」Tab查看
     */
    private void generateAiErrorAnalysis(Long studentId, Long chapterId, Long courseId) {
        try {
            // 获取该学生在该章节的所有活跃错题
            List<ErrorNotebook> errors = errorRepo.findByStudentIdAndChapterId(studentId, chapterId);
            if (errors.isEmpty()) {
                log.info("学生{}在章节{}无错题，跳过AI解析", studentId, chapterId);
                return;
            }

            // 过滤出还没有AI分析的错题
            List<ErrorNotebook> unanalyzed = errors.stream()
                    .filter(e -> e.getAnalysis() == null || e.getAnalysis().isEmpty()
                            || !e.getAnalysis().contains("AI解析"))
                    .toList();

            if (unanalyzed.isEmpty()) {
                log.info("学生{}在章节{}的错题都已有AI解析", studentId, chapterId);
                return;
            }

            log.info("开始为studentId={}的{}道错题生成AI解析", studentId, unanalyzed.size());

            // 为每道未分析的错题生成AI解析
            for (ErrorNotebook error : unanalyzed) {
                try {
                    String analysisPrompt = String.format("""
                        你是C语言教学专家。学生做错了以下题目，请分析错误原因并给出学习建议。

                        【题目】%s
                        【知识点】%s
                        【学生答案】%s
                        【正确答案】%s

                        请按以下格式回复（200字以内）：
                        错误原因：[分析学生为什么会错]
                        正确思路：[讲解正确的解题思路]
                        学习建议：[给出针对性的学习建议]
                        """,
                        error.getQuestion() != null ? error.getQuestion() : "",
                        error.getKnowledgePoint() != null ? error.getKnowledgePoint() : "",
                        error.getStudentAnswer() != null ? error.getStudentAnswer() : "",
                        error.getCorrectAnswer() != null ? error.getCorrectAnswer() : "");

                    String aiAnalysis = sparkClient.chat(analysisPrompt,
                            "分析错题-" + error.getKnowledgePoint(), 0.3f, 200);
                    if (aiAnalysis != null && !aiAnalysis.trim().isEmpty()) {
                        error.setAnalysis("【AI解析】\n" + aiAnalysis.trim());
                        errorRepo.save(error);
                        log.info("错题{} AI解析生成成功", error.getId());
                    }
                } catch (Exception ex) {
                    log.warn("错题{} AI解析生成失败: {}", error.getId(), ex.getMessage());
                }
            }

            // 错题AI解析已生成，不再自动创建知识库资料
        } catch (Exception ex) {
            log.warn("AI错题解析整体流程失败: {}", ex.getMessage());
        }
    }

    private int getQuestionCount(String questionsJson) {
        try { return JSON.parseArray(questionsJson).size(); } catch (Exception e) { return 0; }
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }

    // ==================== AI 智能出题（使用ExerciseAgent） ====================

    @PostMapping("/ai-generate/{studentId}")
    public Result<Map<String, Object>> aiGenerateQuestions(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "C语言基础") String chapterName,
            @RequestParam(defaultValue = "C语言基础") String knowledgePoint) {
        // 获取学生画像
        var studentOpt = studentRepository.findBySysUserId(studentId);
        String knowledgeLevel = "30";
        String weakAreas = "";
        if (studentOpt.isPresent()) {
            Student s = studentOpt.get();
            knowledgeLevel = s.getKnowledgeLevel() != null ? String.valueOf(s.getKnowledgeLevel()) : "30";
            weakAreas = s.getWeakAreas() != null ? s.getWeakAreas() : "";
        }

        // 使用ExerciseAgent生成巩固练习
        var resource = exerciseAgent.generateWithQuality(chapterName, knowledgePoint, knowledgeLevel, weakAreas, null);

        if (resource == null) {
            return Result.error(500, "题目生成失败");
        }

        // 返回格式兼容前端
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", resource.getTitle());
        result.put("content", resource.getContent());
        result.put("type", resource.getType());
        result.put("difficulty", resource.getDifficulty());
        result.put("knowledgePoint", resource.getKnowledgePoint());
        return Result.success(result);
    }

    // ===== 靶向练题：按薄弱点出题 + RAG判题讲解 =====

    /** 获取靶向练习题（轮询薄弱点 + RAG增强，一次出3题） */
    @GetMapping("/targeted/{sysUserId}/next")
    public Result<Map<String, Object>> getTargetedQuestion(@PathVariable Long sysUserId) {
        var studentOpt = studentRepository.findBySysUserId(sysUserId);
        if (studentOpt.isEmpty()) {
            return Result.error(404, "请先完成AI导学构建画像");
        }
        Student student = studentOpt.get();
        String weakAreas = student.getWeakAreas() != null && !student.getWeakAreas().isBlank()
                ? student.getWeakAreas() : "C语言基础";
        String[] areas = weakAreas.split("[,，、]");

        // 轮询：用提问次数对薄弱点数量取模，确保覆盖所有薄弱点
        int askCount = student.getProgress() != null ? student.getProgress() : 0;
        int areaIndex = askCount % areas.length;
        String primaryWeakness = areas[areaIndex].trim();

        // RAG检索：用薄弱知识点检索知识库，注入出题prompt增强质量
        String ragContext = "";
        try {
            var fragments = ragService.retrieveRelevant(primaryWeakness, 3);
            if (!fragments.isEmpty()) {
                StringBuilder kb = new StringBuilder();
                for (var f : fragments) kb.append(f.content).append("\n");
                ragContext = kb.toString();
            }
        } catch (Exception ignored) {}

        String prompt = """
            你是C语言出题专家。根据学生的薄弱环节「%s」，生成3道高质量选择题。
            要求：
            - 考察该知识点的核心概念和易错点
            - 4个选项(A/B/C/D)，只有一个正确答案
            - 每题附带解析，帮助学生理解
            - 3道题难度递进（简单→中等→较难）
            %s
            严格输出JSON数组（不要```json标记）：
            [{"question":"题目","options":["A.选项A","B.选项B","C.选项C","D.选项D"],"answer":0,"knowledgePoint":"%s","explanation":"解析"},...]
            answer是正确选项的索引(0-3)
            只输出JSON数组，不要其他文字。
            """.formatted(primaryWeakness,
                    ragContext.isEmpty() ? "" : "【知识库参考】\n" + ragContext,
                    primaryWeakness);

        try {
            String aiReply = sparkClient.chat(prompt, "针对性出题-" + primaryWeakness, 0.3f, 2048);
            String jsonStr = extractJsonArray(aiReply);
            if (jsonStr == null) {
                return Result.error("AI出题失败，请重试");
            }
            JSONArray arr = JSON.parseArray(jsonStr);
            List<Map<String, Object>> questions = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject q = arr.getJSONObject(i);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("question", q.getString("question"));
                JSONArray optArr = q.getJSONArray("options");
                List<String> options = new ArrayList<>();
                if (optArr != null) {
                    for (int j = 0; j < optArr.size(); j++) options.add(optArr.getString(j));
                }
                data.put("options", options);
                data.put("answer", q.getIntValue("answer"));
                data.put("knowledgePoint", q.getString("knowledgePoint"));
                data.put("explanation", q.getString("explanation"));
                questions.add(data);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("questions", questions);
            result.put("weakPoint", primaryWeakness);
            result.put("ragUsed", !ragContext.isEmpty());
            return Result.success(result);
        } catch (Exception e) {
            log.error("针对性出题失败", e);
            return Result.error("出题失败: " + e.getMessage());
        }
    }

    /** 提交靶向练习答案 + RAG 判题反馈 + weakAreas衰减 */
    @PostMapping("/targeted/{studentId}/answer")
    public Result<Map<String, Object>> submitAnswer(@PathVariable Long studentId,
                                                     @RequestBody Map<String, Object> body) {
        int userAnswer = ((Number) body.get("answer")).intValue();
        int correctAnswer = ((Number) body.get("correctAnswer")).intValue();
        String knowledgePoint = (String) body.get("knowledgePoint");
        boolean correct = userAnswer == correctAnswer;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("correct", correct);

        // 答对时：从weakAreas中移除该薄弱点（掌握后不再考）
        if (correct && knowledgePoint != null && !knowledgePoint.isEmpty()) {
            studentRepository.findBySysUserId(studentId).ifPresent(student -> {
                String weak = student.getWeakAreas();
                if (weak != null && !weak.isEmpty()) {
                    String[] areas = weak.split("[,，、]");
                    String decayed = Arrays.stream(areas)
                            .map(String::trim)
                            .filter(a -> !a.isEmpty() && !a.equals(knowledgePoint))
                            .collect(Collectors.joining(", "));
                    student.setWeakAreas(decayed);
                    studentRepository.save(student);
                    log.info("weakAreas衰减: studentId={}, removed='{}', remaining='{}'",
                            studentId, knowledgePoint, decayed);
                }
            });
        }

        if (!correct) {
            String ragKnowledge = "";
            try {
                var fragments = ragService.retrieveRelevant(knowledgePoint, 2);
                if (!fragments.isEmpty()) {
                    StringBuilder kb = new StringBuilder();
                    for (var f : fragments) kb.append(f.content).append("\n");
                    ragKnowledge = kb.toString();
                }
            } catch (Exception ignored) {}

            String explainPrompt = """
                学生做错了「%s」题。请用200字以内简洁讲解这个知识点的正确理解。
                %s
                要求：讲清楚概念和易错点，语气鼓励。
                """.formatted(knowledgePoint,
                    ragKnowledge.isEmpty() ? "" : "【知识库参考】\n" + ragKnowledge);

            try {
                String explanation = sparkClient.chat(explainPrompt, "错题讲解-" + knowledgePoint, 0.3f, 500);
                result.put("explanation", explanation);
                result.put("ragUsed", !ragKnowledge.isEmpty());
            } catch (Exception e) {
                result.put("explanation", "答错了！正确答案思路：请回顾「" + knowledgePoint + "」相关知识点。");
            }

            // 异步推送对应知识点的视频资源
            final long targetStudentId = studentId;
            agentTaskExecutor.execute(() -> {
                try {
                    int count = courseRecommender.recommendAndSave(targetStudentId);
                    if (count > 0) {
                        log.info("靶向练题推送视频: studentId={}, count={}", targetStudentId, count);
                        wsPushService.pushResourceUpdate(targetStudentId);
                    }
                } catch (Exception ex) {
                    log.warn("靶向练题视频推送失败: studentId={}", targetStudentId, ex);
                }
            });
        }

        return Result.success(result);
    }
}