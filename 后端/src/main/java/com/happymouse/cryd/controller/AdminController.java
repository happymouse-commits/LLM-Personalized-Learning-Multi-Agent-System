package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.SysUserRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.repository.CourseRepository;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.UsageLogRepository;
import com.happymouse.cryd.repository.ErrorNotebookRepository;
import com.happymouse.cryd.service.rag.RagEventStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SysUserRepository sysUserRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;
    private final RagEventStore ragEventStore;
    private final LearningResourceRepository resourceRepo;
    private final UsageLogRepository usageLogRepo;
    private final ErrorNotebookRepository errorRepo;

    public AdminController(SysUserRepository sysUserRepository,
                           StudentRepository studentRepository,
                           CourseRepository courseRepository,
                           PasswordEncoder passwordEncoder,
                           RagEventStore ragEventStore,
                           LearningResourceRepository resourceRepo,
                           UsageLogRepository usageLogRepo,
                           ErrorNotebookRepository errorRepo,
                           com.happymouse.cryd.service.knowledge.KnowledgeBaseService kbService) {
        this.sysUserRepository = sysUserRepository;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.passwordEncoder = passwordEncoder;
        this.ragEventStore = ragEventStore;
        this.resourceRepo = resourceRepo;
        this.usageLogRepo = usageLogRepo;
        this.errorRepo = errorRepo;
        this.kbService = kbService;
    }

    // ===== RAG 运行监控 =====

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 核心指标（从数据库实时查询）
        long totalStudents = sysUserRepository.findByRole("student").size();
        long totalUsers = totalStudents + sysUserRepository.findByRole("admin").size();

        // 真实资源总数
        long totalResources = resourceRepo.count();

        // 总学习时长和活跃学生数（单次查询）
        var allStudents = studentRepository.findAll();
        double totalHours = allStudents.stream()
                .mapToInt(s -> s.getTotalStudyMinutes() != null ? s.getTotalStudyMinutes() : 0)
                .sum() / 60.0;
        long activeStudents = allStudents.stream()
                .filter(s -> s.getProgress() != null && s.getProgress() > 0)
                .count();

        List<Map<String, Object>> metrics = new ArrayList<>();
        metrics.add(createMetric("totalUsers", "👤", (int) totalUsers, "总用户数", 0, "metric-blue"));
        metrics.add(createMetric("totalResources", "📚", (int) totalResources, "AI生成资源总数", 0, "metric-green"));
        metrics.add(createMetric("totalHours", "⏱️", (int) Math.round(totalHours), "学习总时长（小时）", 0, "metric-purple"));
        metrics.add(createMetric("activeUsers", "🔥", (int) activeStudents, "活跃学生数", 0, "metric-orange"));
        result.put("metrics", metrics);

        // 功能使用率（从 UsageLog 真实统计）
        var featureCounts = usageLogRepo.countByFeature();
        long totalUsage = featureCounts.stream().mapToLong(o -> ((Number) o[1]).longValue()).sum();
        Map<String, Integer> featureColor = Map.of(
            "chat", 0x1890ff, "resource_gen", 0x52c41a, "test", 0xfa8c16,
            "practice", 0x722ed1, "profile", 0x909399);
        List<Map<String, Object>> featureUsage = new ArrayList<>();
        for (var fc : featureCounts) {
            String name = (String) fc[0];
            long count = ((Number) fc[1]).longValue();
            int pct = totalUsage > 0 ? (int) Math.round(count * 100.0 / totalUsage) : 0;
            String label = switch (name) {
                case "chat" -> "AI导学"; case "resource_gen" -> "资源生成";
                case "test" -> "在线测试"; case "practice" -> "刷题练习";
                case "profile" -> "学习画像"; default -> name;
            };
            featureUsage.add(createFeature(label, (int) count, pct,
                String.format("#%06x", featureColor.getOrDefault(name, 0x909399))));
        }
        if (featureUsage.isEmpty()) {
            featureUsage.add(createFeature("暂无数据", 0, 0, "#909399"));
        }
        result.put("featureUsage", featureUsage);

        // 异常监控（真实数据）
        List<Map<String, Object>> anomalies = new ArrayList<>();

        // 活跃度异常：不活跃学生占比
        long inactiveStudents = totalStudents - activeStudents;
        if (inactiveStudents > 0 && totalStudents > 0 && (double) inactiveStudents / totalStudents > 0.3) {
            anomalies.add(anomaly("warn", "不活跃学生占比过高：" + inactiveStudents + "/" + totalStudents
                + "（" + Math.round(100.0 * inactiveStudents / totalStudents) + "%）"));
        }

        // 错题异常：高频错题知识点（单次聚合查询替代 N+1）
        var kpErrorCounts = errorRepo.countErrorsByKnowledgePoint();
        Map<String, Long> kpErrorCount = new java.util.LinkedHashMap<>();
        for (var row : kpErrorCounts) {
            String kp = (String) row[0];
            Long count = (Long) row[1];
            kpErrorCount.put(kp, count);
        }
        kpErrorCount.entrySet().stream()
            .filter(e -> e.getValue() >= 5)
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(e -> anomalies.add(anomaly("error",
                "高频错题知识点：「" + e.getKey() + "」累计 " + e.getValue() + " 次错误")));

        result.put("anomalies", anomalies);
        return Result.success(result);
    }

    private Map<String, Object> anomaly(String level, String msg) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", System.nanoTime() % 10000);
        a.put("level", level);
        a.put("time", java.time.LocalDateTime.now().minusHours(1).toString().substring(5, 16).replace('T', ' '));
        a.put("message", msg);
        return a;
    }

    // ===== RAG 运行监控 =====

    /**
     * RAG 管线实时监控数据：
     * - overview: 命中率、门控拦截、幻觉修正等核心指标
     * - gateStats: 各道门通过/拦截统计
     * - knowledgeGaps: 知识缺口 Top N
     * - hallucinationIncidents: 幻觉事故列表
     * - recentEvents: 最近事件流
     */
    @GetMapping("/rag-monitor")
    public Result<Map<String, Object>> getRagMonitor() {
        return Result.success(ragEventStore.getMonitorData());
    }

    /** 重置 RAG 统计数据（开发/演示用） */
    @PostMapping("/rag-monitor/reset")
    public Result<Void> resetRagMonitor() {
        ragEventStore.reset();
        return Result.success();
    }

    // ===== 知识库聚合接口 =====

    private final com.happymouse.cryd.service.knowledge.KnowledgeBaseService kbService;

    @GetMapping("/knowledge-overview")
    public Result<Map<String, Object>> getKnowledgeOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        var kbs = kbService.listAll();
        List<Map<String, Object>> docList = new ArrayList<>();
        for (var kb : kbs) {
            var docs = kbService.getDocuments(kb.getId());
            for (var doc : docs) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", doc.getId());
                item.put("kbId", doc.getKbId());
                item.put("name", doc.getFileName());
                item.put("fileType", doc.getFileType());
                item.put("status", doc.getStatus());
                item.put("tags", doc.getTags());
                item.put("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "");
                docList.add(item);
            }
        }
        result.put("documents", docList);
        result.put("totalDocs", docList.size());
        result.put("totalKbs", kbs.size());
        return Result.success(result);
    }

    private Map<String, Object> createMetric(String key, String icon, int value, String label, int trend, String styleClass) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("icon", icon);
        m.put("value", value);
        m.put("label", label);
        m.put("trend", trend);
        m.put("styleClass", styleClass);
        return m;
    }

    private Map<String, Object> createFeature(String name, int count, int percent, String color) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", name);
        f.put("count", count);
        f.put("percent", percent);
        f.put("color", color);
        return f;
    }
}
