package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.agent.ReportAgent;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 班级学情分析控制器 — 班级概览、个体画像、对比分析、PDF导出
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final SysUserRepository sysUserRepo;
    private final StudentRepository studentRepo;
    private final ChapterProgressRepository progressRepo;
    private final LearningResourceRepository resourceRepo;
    private final ErrorNotebookRepository errorRepo;
    private final SparkClient sparkClient;
    private final ReportAgent reportAgent;

    public AnalyticsController(SysUserRepository sysUserRepo,
                                StudentRepository studentRepo,
                                ChapterProgressRepository progressRepo,
                                LearningResourceRepository resourceRepo,
                                ErrorNotebookRepository errorRepo,
                                SparkClient sparkClient,
                                ReportAgent reportAgent) {
        this.sysUserRepo = sysUserRepo;
        this.studentRepo = studentRepo;
        this.progressRepo = progressRepo;
        this.resourceRepo = resourceRepo;
        this.errorRepo = errorRepo;
        this.sparkClient = sparkClient;
        this.reportAgent = reportAgent;
    }

    // 班级整体学情概览
    @GetMapping("/class/{className}/overview")
    public Result<Map<String, Object>> getClassOverview(@PathVariable String className) {
        var users = sysUserRepo.findByClassName(className).stream()
            .filter(u -> "student".equals(u.getRole()))
            .toList();

        List<Student> profiles = new ArrayList<>();
        for (var user : users) {
            studentRepo.findBySysUserId(user.getId()).ifPresent(profiles::add);
        }

        // 统计真实答题数据
        int totalQuestionsAnswered = 0;
        int totalCorrectAnswers = 0;
        int activeStudents = 0;
        for (var user : users) {
            List<com.happymouse.cryd.model.entity.ChapterProgress> userProgress =
                    progressRepo.findByStudentId(user.getId());
            if (!userProgress.isEmpty()) activeStudents++;
            for (var cp : userProgress) {
                if (cp.getScore() != null) {
                    totalQuestionsAnswered += 5; // 每章约5题
                    totalCorrectAnswers += Math.round(cp.getScore() * 5f / 100f);
                }
            }
        }

        double avgAccuracy = totalQuestionsAnswered > 0
                ? Math.round((double) totalCorrectAnswers / totalQuestionsAnswered * 100.0)
                : 0;

        // 汇总错题知识点分布
        List<String> topErrors = getTopErrors(className, 10);
        int weakPointCount = topErrors.size();

        // 从错题中提取知识点掌握度统计
        List<Map<String, Object>> knowledgeStats = new ArrayList<>();
        Map<String, Long> kpErrorCount = new java.util.LinkedHashMap<>();
        for (var user : users) {
            List<com.happymouse.cryd.model.entity.ErrorNotebook> errors =
                    errorRepo.findByStudentIdAndStatus(user.getId(), "active");
            for (var e : errors) {
                if (e.getKnowledgePoint() != null) {
                    kpErrorCount.merge(e.getKnowledgePoint(), 1L, Long::sum);
                }
            }
        }
        // 将错题知识点转为掌握度（错误越多，掌握度越低）
        for (var entry : kpErrorCount.entrySet()) {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("name", entry.getKey());
            stat.put("mastery", Math.max(0, 100 - entry.getValue().intValue() * 10));
            knowledgeStats.add(stat);
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("className", className);
        overview.put("totalStudents", users.size());
        overview.put("activeStudents", activeStudents);
        overview.put("averageAccuracy", (int) avgAccuracy);
        overview.put("avgQuestions", users.isEmpty() ? 0 : Math.round((float) totalQuestionsAnswered / users.size()));
        overview.put("weakPointCount", weakPointCount);
        overview.put("knowledgeStats", knowledgeStats);

        double avgLevel = profiles.stream()
            .mapToInt(s -> s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 0)
            .average().orElse(0);
        double avgProgress = profiles.stream()
            .mapToInt(s -> s.getProgress() != null ? s.getProgress() : 0)
            .average().orElse(0);
        overview.put("avgKnowledgeLevel", Math.round(avgLevel * 10) / 10.0);
        overview.put("avgProgress", Math.round(avgProgress * 10) / 10.0);
        overview.put("errorTop10", topErrors);

        // 统计生成资源总数
        long totalResources = 0;
        for (var user : users) {
            var studentOpt = studentRepo.findBySysUserId(user.getId());
            if (studentOpt.isPresent()) {
                totalResources += resourceRepo.findByStudentIdOrderByCreatedAtDesc(studentOpt.get().getId()).size();
            }
        }
        overview.put("totalResourcesGenerated", totalResources);

        return Result.success(overview);
    }

    // 对比分析
    @GetMapping("/comparison")
    public Result<?> getComparison(
            @RequestParam(required = false) String type, // "horizontal" or "vertical"
            @RequestParam(required = false) String className,
            @RequestParam(required = false) Long studentId) {

        if ("horizontal".equals(type) && className != null) {
            // 横向对比：同班级学生按知识点
            var students = sysUserRepo.findByClassName(className);
            List<Map<String, Object>> data = new ArrayList<>();
            for (var user : students) {
                if (!"student".equals(user.getRole())) continue;
                var profile = studentRepo.findBySysUserId(user.getId()).orElse(null);
                if (profile != null) {
                    data.add(Map.of(
                        "name", user.getNickname() != null ? user.getNickname() : user.getUsername(),
                        "level", profile.getKnowledgeLevel() != null ? profile.getKnowledgeLevel() : 0,
                        "progress", profile.getProgress() != null ? profile.getProgress() : 0
                    ));
                }
            }
            return Result.success(Map.of("type", "horizontal", "data", data));
        } else if ("vertical".equals(type) && studentId != null) {
            // 纵向对比：同一学生不同时间段 — 基于 ChapterProgress 历史记录
            List<com.happymouse.cryd.model.entity.ChapterProgress> allProgress =
                    progressRepo.findByStudentId(studentId);
            Map<String, List<Integer>> timeSeries = new LinkedHashMap<>();
            for (var cp : allProgress) {
                String dateKey = cp.getSubmittedAt() != null
                        ? cp.getSubmittedAt().toLocalDate().toString()
                        : "unknown";
                timeSeries.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(
                        cp.getScore() != null ? cp.getScore() : 0);
            }
            List<Map<String, Object>> data = new ArrayList<>();
            for (var entry : timeSeries.entrySet()) {
                double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", entry.getKey());
                point.put("avgScore", Math.round(avg));
                point.put("submissions", entry.getValue().size());
                data.add(point);
            }
            data.sort((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")));
            return Result.success(Map.of("type", "vertical", "studentId", studentId, "data", data));
        }

        return Result.error("请指定type(horizontal/vertical)和相应参数");
    }

    // AI班级学情分析
    @GetMapping("/class/{className}/ai-analysis")
    public Result<?> getAiAnalysis(@PathVariable String className) {
        var overview = (Map<String, Object>) getClassOverview(className).getData();

        StringBuilder context = new StringBuilder();
        context.append("班级: ").append(className).append("\n");
        context.append("平均知识水平: ").append(overview.get("avgKnowledgeLevel")).append("\n");
        context.append("平均进度: ").append(overview.get("avgProgress")).append("\n");

        try {
            // 学情分析是关键任务
            String analysis = sparkClient.smartChat(
                "你是教学分析专家，请根据以下班级数据给出学情分析和教学建议。",
                context.toString(), SparkClient.TaskComplexity.CRITICAL, 1024);
            return Result.success(Map.of("analysis", analysis, "overview", overview));
        } catch (Exception e) {
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }

    // 班级学生答题详情列表
    @GetMapping("/class/{className}/students")
    public Result<List<Map<String, Object>>> getClassStudentDetails(@PathVariable String className) {
        List<com.happymouse.cryd.model.entity.SysUser> students = sysUserRepo.findByClassName(className).stream()
                .filter(u -> "student".equals(u.getRole()))
                .toList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (com.happymouse.cryd.model.entity.SysUser user : students) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", user.getStudentId() != null ? user.getStudentId() : String.valueOf(user.getId()));
            item.put("sysUserId", user.getId());
            item.put("studentName", user.getNickname() != null ? user.getNickname() : user.getUsername());
            item.put("username", user.getUsername());

            // 统计答题情况
            List<com.happymouse.cryd.model.entity.ChapterProgress> allProgress =
                    progressRepo.findByStudentId(user.getId());
            int totalQuestions = allProgress.size() * 5; // 每章约5题
            long gradedCount = allProgress.stream().filter(p -> p.getScore() != null).count();
            double avgScore = allProgress.stream()
                    .filter(p -> p.getScore() != null)
                    .mapToInt(com.happymouse.cryd.model.entity.ChapterProgress::getScore)
                    .average().orElse(0);
            long correctRate = Math.round(avgScore);

            // 获取薄弱知识点（从错题本）
            List<com.happymouse.cryd.model.entity.ErrorNotebook> errors =
                    errorRepo.findByStudentIdAndStatus(user.getId(), "active");
            List<String> weakPoints = errors.stream()
                    .map(e -> e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "未知")
                    .distinct().limit(5).toList();

            // 最近活跃时间
            com.happymouse.cryd.model.entity.ChapterProgress latest = allProgress.stream()
                    .filter(p -> p.getSubmittedAt() != null)
                    .max((a, b) -> a.getSubmittedAt().compareTo(b.getSubmittedAt()))
                    .orElse(null);
            String lastActive = latest != null ? latest.getSubmittedAt().toString() : "暂无";

            item.put("totalQuestions", totalQuestions);
            item.put("correctRate", correctRate);
            item.put("weakPoints", weakPoints);
            item.put("lastActive", lastActive);
            result.add(item);
        }
        return Result.success(result);
    }

    // 导出班级报告（简化版PDF内容，返回Markdown文本）
    @GetMapping("/class/{className}/export")
    public ResponseEntity<String> exportReport(@PathVariable String className) {
        var overview = (Map<String, Object>) getClassOverview(className).getData();

        StringBuilder report = new StringBuilder();
        report.append("# 班级学情分析报告\n\n");
        report.append("**班级**: ").append(className).append("\n\n");
        report.append("## 整体概览\n\n");
        report.append("- 学生总数: ").append(overview.get("totalStudents")).append("\n");
        report.append("- 平均知识水平: ").append(overview.get("avgKnowledgeLevel")).append("\n");
        report.append("- 平均学习进度: ").append(overview.get("avgProgress")).append("\n");
        report.append("- 生成资源总数: ").append(overview.get("totalResourcesGenerated")).append("\n");
        report.append("\n## 易错点TOP10\n\n");
        var errors = (List<?>) overview.get("errorTop10");
        if (errors != null) {
            for (Object e : errors) {
                report.append("- ").append(e).append("\n");
            }
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_" + className + ".md")
            .contentType(MediaType.TEXT_PLAIN)
            .body(report.toString());
    }

    private List<String> getTopErrors(String className, int limit) {
        // 从 ErrorNotebook 表聚合真实错误数据
        List<Long> studentIds = sysUserRepo.findByClassName(className).stream()
                .filter(u -> "student".equals(u.getRole()))
                .map(com.happymouse.cryd.model.entity.SysUser::getId)
                .toList();
        Map<String, Long> kpCount = new java.util.LinkedHashMap<>();
        for (Long sid : studentIds) {
            List<com.happymouse.cryd.model.entity.ErrorNotebook> errors =
                    errorRepo.findByStudentIdAndStatus(sid, "active");
            for (com.happymouse.cryd.model.entity.ErrorNotebook e : errors) {
                String kp = e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "未知";
                kpCount.merge(kp, 1L, Long::sum);
            }
        }
        return kpCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> e.getKey() + "(" + e.getValue() + "次)")
                .toList();
    }

    // ==================== AI 学习报告 ====================

    @GetMapping("/report/{studentId}")
    public Result<Map<String, String>> generateReport(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "weekly") String period) {
        String report = reportAgent.generateReport(studentId, period);
        Map<String, String> result = new HashMap<>();
        result.put("content", report);
        result.put("period", period);
        return Result.success(result);
    }
}