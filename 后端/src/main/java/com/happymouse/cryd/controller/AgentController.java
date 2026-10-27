package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.UsageLogRepository;
import com.happymouse.cryd.service.agent.AutoResourceGenerator;
import com.happymouse.cryd.service.rag.RagEventStore;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 多智能体协作管线状态 — 供学生端首页 Agent 卡片消费
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final RagEventStore ragEventStore;
    private final StudentRepository studentRepo;
    private final UsageLogRepository usageLogRepo;
    private final LearningResourceRepository resourceRepo;
    private final AutoResourceGenerator autoResourceGenerator;

    public AgentController(RagEventStore ragEventStore,
                           StudentRepository studentRepo,
                           UsageLogRepository usageLogRepo,
                           LearningResourceRepository resourceRepo,
                           AutoResourceGenerator autoResourceGenerator) {
        this.ragEventStore = ragEventStore;
        this.studentRepo = studentRepo;
        this.usageLogRepo = usageLogRepo;
        this.resourceRepo = resourceRepo;
        this.autoResourceGenerator = autoResourceGenerator;
    }

    /**
     * 获取资源生成状态（前端轮询用）
     */
    @GetMapping("/generating-status/{studentId}")
    public Result<Map<String, Object>> getGeneratingStatus(@PathVariable Long studentId) {
        Map<String, Object> result = new HashMap<>();
        result.put("generating", autoResourceGenerator.isGenerating(studentId));
        result.put("progress", autoResourceGenerator.getProgress(studentId));
        result.put("step", autoResourceGenerator.getStep(studentId));
        return Result.success(result);
    }

    /**
     * 获取 6 个 Agent 的管线状态
     */
    @GetMapping("/pipeline-status/{sysUserId}")
    public Result<Map<String, Object>> getPipelineStatus(@PathVariable Long sysUserId) {
        Optional<Student> opt = studentRepo.findBySysUserId(sysUserId);
        Student student = opt.orElse(null);

        Map<String, Object> ragData = ragEventStore.getMonitorData();
        Map<String, Object> overview = (Map<String, Object>) ragData.getOrDefault("overview", Map.of());

        long totalQueries = ((Number) overview.getOrDefault("totalQueries", 0)).longValue();
        long gatePassed = ((Number) overview.getOrDefault("gate1Passed", 0)).longValue();
        long hallucinationRetries = ((Number) overview.getOrDefault("hallucinationRetries", 0)).longValue();
        double avgConfidence = ((Number) overview.getOrDefault("avgConfidence", 0.85)).doubleValue();

        // 画像维度数
        int dimCount = countDimensions(student);
        int knowledgeLevel = student != null && student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30;

        // 最近对话统计
        long chatCount = 0;
        try {
            chatCount = usageLogRepo.countByFeatureAndUserIdSince("chat", sysUserId, java.time.LocalDateTime.now().minusDays(7));
        } catch (Exception ignored) {}

        // 资源统计
        List<LearningResource> resources = student != null
                ? resourceRepo.findByStudentIdOrderByCreatedAtDesc(student.getId())
                : List.of();
        long resourceCount = resources.size();
        long generatedToday = resources.stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusDays(1)))
                .count();

        // 构造 6 个 Agent 状态
        List<Map<String, Object>> agents = new ArrayList<>();

        // 1. RAG检索Agent
        Map<String, Object> rag = new LinkedHashMap<>();
        rag.put("id", "rag");
        rag.put("name", "RAG检索Agent");
        rag.put("icon", "🔍");
        rag.put("status", totalQueries > 0 ? "done" : "running");
        rag.put("progress", totalQueries > 0 ? 100 : Math.min((int)(gatePassed * 100 / Math.max(totalQueries, 1)), 100));
        rag.put("stats", List.of(
                Map.of("label", "命中片段", "value", String.valueOf(gatePassed)),
                Map.of("label", "可信度", "value", String.format("%.0f%%", avgConfidence * 100))
        ));
        agents.add(rag);

        // 2. AI导师Agent
        Map<String, Object> tutor = new LinkedHashMap<>();
        tutor.put("id", "tutor");
        tutor.put("name", "AI导师Agent");
        tutor.put("icon", "🎓");
        tutor.put("status", chatCount > 0 ? "running" : "pending");
        tutor.put("progress", Math.min((int)(chatCount * 20), 100));
        tutor.put("stats", List.of(
                Map.of("label", "对话次数", "value", String.valueOf(chatCount)),
                Map.of("label", "学习时长", "value", chatCount > 0 ? (chatCount * 2) + "min" : "0min")
        ));
        agents.add(tutor);

        // 3. 幻觉校验Agent
        Map<String, Object> hallucination = new LinkedHashMap<>();
        hallucination.put("id", "hallucination");
        hallucination.put("name", "幻觉校验Agent");
        hallucination.put("icon", "🛡️");
        hallucination.put("status", totalQueries > 0 ? "running" : "pending");
        hallucination.put("progress", hallucinationRetries > 0
                ? Math.min(100 - (int)(hallucinationRetries * 100 / Math.max(totalQueries, 1)), 100)
                : totalQueries > 0 ? 95 : 40);
        hallucination.put("stats", List.of(
                Map.of("label", "拦截次数", "value", String.valueOf(hallucinationRetries)),
                Map.of("label", "一致性", "value", String.format("%.0f%%", avgConfidence * 100))
        ));
        agents.add(hallucination);

        // 4. 画像分析Agent
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", "profile");
        profile.put("name", "画像分析Agent");
        profile.put("icon", "👤");
        profile.put("status", dimCount >= 4 ? "running" : "pending");
        profile.put("progress", Math.min(dimCount * 12, 100));
        profile.put("stats", List.of(
                Map.of("label", "画像更新", "value", dimCount + " 维度"),
                Map.of("label", "知识水平", "value", knowledgeLevel + "分")
        ));
        agents.add(profile);

        // 5. 资源推荐Agent
        Map<String, Object> recommend = new LinkedHashMap<>();
        recommend.put("id", "recommend");
        recommend.put("name", "资源推荐Agent");
        recommend.put("icon", "🎁");
        recommend.put("status", resourceCount > 0 ? "pending" : "pending");
        String allResourceTypes = "article,explanation,mindmap,code,summary,course-video,course-ppt";
        String[] types = allResourceTypes.split(",");
        long typeCount = types.length; // 7 种类型
        recommend.put("progress", Math.min((int)(resourceCount * 10), 100));
        recommend.put("stats", List.of(
                Map.of("label", "生成资源", "value", typeCount + " 种"),
                Map.of("label", "推荐数量", "value", String.valueOf(resourceCount))
        ));
        agents.add(recommend);

        // 6. 路径规划Agent
        Map<String, Object> pathplan = new LinkedHashMap<>();
        pathplan.put("id", "pathplan");
        pathplan.put("name", "路径规划Agent");
        pathplan.put("icon", "🗺️");
        pathplan.put("status", student != null && knowledgeLevel > 0 ? "running" : "pending");
        int chapterProgress = student != null && student.getKnowledgeLevel() != null ? Math.min(student.getKnowledgeLevel() / 10, 7) : 0;
        pathplan.put("progress", Math.min(chapterProgress * 14, 100));
        pathplan.put("stats", List.of(
                Map.of("label", "规划路径", "value", chapterProgress + " 条"),
                Map.of("label", "覆盖章节", "value", chapterProgress + "/7")
        ));
        agents.add(pathplan);

        // 构建返回
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agents", agents);
        result.put("overallProgress", computeOverallProgress(agents));
        result.put("generatedResourceTypes", getGeneratedResourceTypes(resources));

        return Result.success(result);
    }

    private int computeOverallProgress(List<Map<String, Object>> agents) {
        int sum = 0;
        for (Map<String, Object> a : agents) {
            sum += ((Number) a.getOrDefault("progress", 0)).intValue();
        }
        return sum / agents.size();
    }

    private List<String> getGeneratedResourceTypes(List<LearningResource> resources) {
        Set<String> types = new LinkedHashSet<>();
        for (LearningResource r : resources) {
            if (r.getType() != null) types.add(r.getType());
        }
        return new ArrayList<>(types);
    }

    private int countDimensions(Student s) {
        if (s == null) return 0;
        int count = 0;
        if (s.getKnowledgeLevel() != null && s.getKnowledgeLevel() > 0) count++;
        if (s.getCognitiveStyle() != null) count++;
        if (s.getLearningPreference() != null) count++;
        if (s.getLearningPace() != null) count++;
        if (s.getInterestDirection() != null) count++;
        if (s.getWeakAreas() != null) count++;
        if (s.getStudyMotivation() != null) count++;
        if (s.getFocusLevel() != null) count++;
        return count;
    }
}
