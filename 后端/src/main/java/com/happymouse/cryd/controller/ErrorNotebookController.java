package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 错题本管理 + 突破资源生成 — 从 PracticeController 拆出
 */
@RestController
@RequestMapping("/api/practice")
public class ErrorNotebookController {

    private static final Logger log = LoggerFactory.getLogger(ErrorNotebookController.class);

    private final ErrorNotebookRepository errorRepo;
    private final ChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final LearningResourceRepository resourceRepo;
    private final SparkClient sparkClient;

    private static final Map<String, String> BREAKTHROUGH_PROMPTS = Map.of(
        "explanation", """
            你是「课程导师」，擅长用清晰的语言讲解知识。
            请针对学生做错的题目，生成一份结构化的课程讲解文档。
            要求：
            1. 先指出题目的知识点
            2. 讲解核心概念和原理
            3. 给出典型例题和解析
            4. 总结易错点和解题技巧
            输出格式：Markdown
            """,
        "mindmap", """
            你是「知识架构师」，擅长用思维导图组织知识。
            请针对学生做错的题目，生成一份知识点思维导图。
            要求：
            1. 使用文本缩进表示层级
            2. 中心主题是该知识点
            3. 分支包括：概念、原理、应用、易错点
            输出格式：文本形式的思维导图（用 # ## - 等标记层级）
            """,
        "exercise", """
            你是「出题专家」，擅长设计练习题。
            请针对学生做错的题目，生成3-5道同类练习题。
            要求：
            1. 题型与原题相似
            2. 每题附答案和解析
            3. 难度递进
            输出格式：Markdown
            """,
        "code", """
            你是「实践指导」，擅长设计代码实操案例。
            请针对学生做错的题目，设计与知识点相关的代码实操案例。
            要求：
            1. 提供可运行的代码示例
            2. 代码需有详细注释
            3. 说明运行结果和原理
            输出格式：Markdown（代码块用```语言标记）
            """
    );

    private static final Map<String, String> RESOURCE_TYPE_NAMES = Map.of(
        "explanation", "知识点讲解",
        "mindmap", "思维导图",
        "exercise", "练习题",
        "code", "代码演示"
    );

    public ErrorNotebookController(ErrorNotebookRepository errorRepo,
                                    ChapterRepository chapterRepository,
                                    CourseRepository courseRepository,
                                    LearningResourceRepository resourceRepo,
                                    SparkClient sparkClient) {
        this.errorRepo = errorRepo;
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
        this.resourceRepo = resourceRepo;
        this.sparkClient = sparkClient;
    }

    // ==================== 错题管理 ====================

    @GetMapping("/errors/{studentId}")
    public Result<Map<String, Object>> getErrors(@PathVariable Long studentId,
                                                  @RequestParam(required = false) String knowledgePoint,
                                                  @RequestParam(required = false) String errorType,
                                                  @RequestParam(required = false) String status) {
        List<ErrorNotebook> errors;
        if (knowledgePoint != null && !knowledgePoint.isEmpty()) {
            errors = errorRepo.findByStudentIdAndKnowledgePoint(studentId, knowledgePoint);
        } else if (errorType != null && !errorType.isEmpty()) {
            errors = errorRepo.findByStudentIdAndErrorType(studentId, errorType);
        } else if (status != null && !status.isEmpty()) {
            errors = errorRepo.findByStudentIdAndStatus(studentId, status);
        } else {
            errors = errorRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
        }
        errors = errors.stream()
                .filter(e -> "active".equals(e.getStatus()))
                .collect(Collectors.toList());

        List<Map<String, Object>> enrichedErrors = new ArrayList<>();
        for (ErrorNotebook e : errors) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("studentId", e.getStudentId());
            item.put("chapterId", e.getChapterId());
            item.put("questionIndex", e.getQuestionIndex());
            item.put("question", e.getQuestion());
            item.put("studentAnswer", e.getStudentAnswer());
            item.put("correctAnswer", e.getCorrectAnswer());
            item.put("knowledgePoint", e.getKnowledgePoint());
            item.put("errorType", e.getErrorType());
            item.put("errorTag", e.getErrorTag());
            item.put("analysis", e.getAnalysis());
            item.put("wrongCount", e.getWrongCount());
            item.put("status", e.getStatus());
            item.put("createdAt", e.getCreatedAt());

            if (e.getChapterId() != null) {
                chapterRepository.findById(e.getChapterId()).ifPresent(ch -> {
                    item.put("chapterName", ch.getName());
                    courseRepository.findById(ch.getCourseId()).ifPresent(c ->
                        item.put("courseName", c.getName()));
                });
            }
            enrichedErrors.add(item);
        }

        Set<String> courseSet = new LinkedHashSet<>();
        Set<String> chapterSet = new LinkedHashSet<>();
        for (Map<String, Object> e : enrichedErrors) {
            if (e.get("courseName") != null) courseSet.add((String) e.get("courseName"));
            if (e.get("chapterName") != null) chapterSet.add((String) e.get("chapterName"));
            if (e.get("knowledgePoint") != null) chapterSet.add((String) e.get("knowledgePoint"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("errors", enrichedErrors);
        result.put("total", enrichedErrors.size());
        result.put("courses", new ArrayList<>(courseSet));
        result.put("chapters", new ArrayList<>(chapterSet));
        return Result.success(result);
    }

    @PostMapping("/errors/{errorId}/resolve")
    public Result<ErrorNotebook> resolveError(@PathVariable Long errorId) {
        return errorRepo.findById(errorId).map(e -> {
            e.setStatus("resolved");
            e.setResolvedAt(LocalDateTime.now());
            errorRepo.save(e);
            return Result.success(e);
        }).orElse(Result.<ErrorNotebook>error(404, "错题不存在"));
    }

    @PostMapping("/errors")
    public Result<ErrorNotebook> addError(@RequestBody Map<String, Object> body) {
        ErrorNotebook error = new ErrorNotebook();
        error.setStudentId(toLong(body.get("studentId")));
        error.setQuestion((String) body.get("question"));
        error.setStudentAnswer((String) body.get("studentAnswer"));
        error.setCorrectAnswer((String) body.get("correctAnswer"));
        error.setKnowledgePoint((String) body.get("knowledgePoint"));
        error.setErrorType((String) body.getOrDefault("errorType", "concept"));
        error.setErrorTag((String) body.getOrDefault("errorTag", "手动添加"));
        error.setChapterId(toLong(body.get("chapterId")));
        error.setQuestionIndex(body.get("questionIndex") != null
                ? Integer.valueOf(body.get("questionIndex").toString()) : null);
        errorRepo.save(error);
        return Result.success(error);
    }

    @DeleteMapping("/errors/{errorId}")
    public Result<?> deleteError(@PathVariable Long errorId) {
        errorRepo.deleteById(errorId);
        return Result.success("已移除");
    }

    // ==================== AI 错题分析 ====================

    @PostMapping("/errors/{studentId}/generate-insights")
    public Result<Map<String, Object>> generateInsights(@PathVariable Long studentId) {
        List<ErrorNotebook> errors = errorRepo.findByStudentIdAndStatus(studentId, "active");
        if (errors.isEmpty()) {
            return Result.success(Map.of("insights", List.of(), "message", "暂无错题，继续保持！"));
        }

        Map<String, List<ErrorNotebook>> grouped = errors.stream()
                .filter(e -> e.getKnowledgePoint() != null && !e.getKnowledgePoint().isEmpty())
                .collect(Collectors.groupingBy(ErrorNotebook::getKnowledgePoint, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> insights = new ArrayList<>();
        int totalGenerated = 0;

        for (var entry : grouped.entrySet()) {
            String kp = entry.getKey();
            List<ErrorNotebook> kpErrors = entry.getValue();
            int errorCount = kpErrors.size();

            try {
                for (ErrorNotebook e : kpErrors) {
                    if (e.getAnalysis() == null || e.getAnalysis().isEmpty() || !e.getAnalysis().contains("AI解析")) {
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
                            e.getQuestion() != null ? e.getQuestion() : "",
                            e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "",
                            e.getStudentAnswer() != null ? e.getStudentAnswer() : "",
                            e.getCorrectAnswer() != null ? e.getCorrectAnswer() : "");

                        // 错题分析是低复杂度任务
                        String aiAnalysis = sparkClient.smartChat(analysisPrompt, "分析错题-" + e.getKnowledgePoint(),
                                SparkClient.TaskComplexity.LOW, 200);
                        if (aiAnalysis != null && !aiAnalysis.trim().isEmpty()) {
                            e.setAnalysis("【AI解析】\n" + aiAnalysis.trim());
                            errorRepo.save(e);
                            totalGenerated++;
                        }
                    }
                }

                StringBuilder errorSummary = new StringBuilder();
                for (int i = 0; i < Math.min(kpErrors.size(), 5); i++) {
                    ErrorNotebook e = kpErrors.get(i);
                    errorSummary.append(i + 1).append(". 题目：").append(e.getQuestion() != null ? e.getQuestion() : "")
                            .append(" | 学生答案：").append(e.getStudentAnswer() != null ? e.getStudentAnswer() : "")
                            .append(" | 正确答案：").append(e.getCorrectAnswer() != null ? e.getCorrectAnswer() : "")
                            .append("\n");
                }

                String summaryPrompt = String.format("""
                    你是C语言教学专家。请针对学生的高频错误知识点，生成一份精炼的知识点总结。

                    【知识点】%s
                    【错误次数】%d次
                    【错误详情】
                    %s

                    请按以下Markdown格式生成（300字以内）：
                    ## %s
                    ### 核心概念
                    （讲解该知识点的核心概念）
                    ### 常见错误
                    （列举学生常犯的错误）
                    ### 正确做法
                    （给出正确的解题思路和方法）
                    ### 练习建议
                    （1-2条针对性的练习建议）
                    """, kp, errorCount, errorSummary.toString(), kp);

                String summaryContent = sparkClient.chat(summaryPrompt, "知识总结-" + kp, 0.4f, 800);
                if (summaryContent != null && !summaryContent.trim().isEmpty()) {
                    Map<String, Object> insight = new LinkedHashMap<>();
                    insight.put("knowledgePoint", kp);
                    insight.put("errorCount", errorCount);
                    insight.put("content", summaryContent.trim());
                    insight.put("generatedAt", java.time.LocalDateTime.now().toString());
                    insights.add(insight);
                    totalGenerated++;
                }

                log.info("知识点总结已生成: kp={}, errorCount={}", kp, errorCount);
            } catch (Exception ex) {
                log.warn("生成知识点总结失败 [{}]: {}", kp, ex.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("insights", insights);
        result.put("totalKnowledgePoints", insights.size());
        result.put("totalErrorsAnalyzed", totalGenerated);
        result.put("message", "AI已为 " + insights.size() + " 个知识点生成总结，分析了 " + totalGenerated + " 道错题");
        return Result.success(result);
    }

    // ==================== 突破区 ====================

    @PostMapping("/errors/{errorId}/breakthrough")
    public Result<Map<String, Object>> breakthrough(@PathVariable Long errorId) {
        ErrorNotebook error = errorRepo.findById(errorId).orElse(null);
        if (error == null) return Result.error(404, "错题不存在");

        String knowledgePoint = error.getKnowledgePoint() != null ? error.getKnowledgePoint() : "";
        String questionContent = error.getQuestion() != null ? error.getQuestion() : "";
        String wrongAnswer = error.getStudentAnswer() != null ? error.getStudentAnswer() : "";
        String correctAnswer = error.getCorrectAnswer() != null ? error.getCorrectAnswer() : "";

        String context = String.format("""
            【题目】%s
            【知识点】%s
            【学生错误答案】%s
            【正确答案】%s
            """, questionContent, knowledgePoint, wrongAnswer, correctAnswer);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("errorId", errorId);
        result.put("knowledgePoint", knowledgePoint);

        List<Map<String, Object>> resources = new ArrayList<>();
        List<String> resourceTypes = List.of("explanation", "mindmap", "exercise", "code");

        for (String resourceType : resourceTypes) {
            String promptTemplate = BREAKTHROUGH_PROMPTS.get(resourceType);
            if (promptTemplate == null) continue;

            String prompt = promptTemplate + "\n\n" + context;
            try {
                String content = sparkClient.chat(prompt, context, 0.5f, 2048);
                log.info("突破资源生成 [{}] 成功", resourceType);

                LearningResource lr = new LearningResource();
                lr.setTitle("【错题突破】" + knowledgePoint + " - " + RESOURCE_TYPE_NAMES.get(resourceType));
                lr.setType(resourceType);
                lr.setKnowledgePoint(knowledgePoint);
                lr.setContent(content);
                lr.setStudentId(error.getStudentId());
                lr.setGeneratedBy("breakthrough");
                lr.setCategory("错题突破生成");
                lr.setTags(knowledgePoint);
                lr.setDifficulty("medium");
                lr.setShared(false);
                resourceRepo.save(lr);

                Map<String, Object> resItem = new LinkedHashMap<>();
                resItem.put("resourceType", resourceType);
                resItem.put("resourceName", RESOURCE_TYPE_NAMES.get(resourceType));
                resItem.put("content", content);
                resItem.put("resourceId", lr.getId());
                resources.add(resItem);
            } catch (Exception e) {
                log.error("资源生成失败 [{}]: {}", resourceType, e.getMessage());
                Map<String, Object> resItem = new LinkedHashMap<>();
                resItem.put("resourceType", resourceType);
                resItem.put("resourceName", RESOURCE_TYPE_NAMES.get(resourceType));
                resItem.put("content", "生成失败: " + e.getMessage());
                resources.add(resItem);
            }
        }

        error.setAnalysis("已生成突破资源");
        errorRepo.save(error);

        result.put("resources", resources);
        return Result.success(result);
    }

    @GetMapping("/breakthrough/history/{studentId}")
    public Result<List<LearningResource>> getBreakthroughHistory(@PathVariable Long studentId) {
        List<LearningResource> all = resourceRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
        List<LearningResource> history = all.stream()
                .filter(r -> "breakthrough".equals(r.getGeneratedBy()))
                .collect(Collectors.toList());
        return Result.success(history);
    }

    // ==================== 辅助方法 ====================

    public String analyzeErrorType(String question, String studentAnswer, String correctAnswer) {
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

    public String mapErrorTag(String errorType) {
        return switch (errorType) {
            case "concept" -> "概念不清";
            case "calculation" -> "计算错误";
            case "misread" -> "审题偏差";
            case "logic" -> "代码逻辑错误";
            default -> "概念不清";
        };
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}
