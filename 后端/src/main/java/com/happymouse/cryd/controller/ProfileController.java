package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学生画像控制器 — 查看、手动修正、AI分析
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final StudentRepository studentRepository;
    private final SparkClient sparkClient;

    public ProfileController(StudentRepository studentRepository, SparkClient sparkClient) {
        this.studentRepository = studentRepository;
        this.sparkClient = sparkClient;
    }

    private Student findOrCreateStudent(Long sysUserId) {
        return studentRepository.findBySysUserId(sysUserId)
            .orElse(null);
    }

    @GetMapping("/{sysUserId}")
    public Result<Map<String, Object>> getProfile(@PathVariable Long sysUserId) {
        Student student = findOrCreateStudent(sysUserId);
        if (student == null) {
            return Result.error(404, "学生画像未构建，请先完成AI导学");
        }
        return Result.success(buildProfileMap(student));
    }

    @PutMapping("/{sysUserId}")
    public Result<Map<String, Object>> updateProfile(
            @PathVariable Long sysUserId,
            @RequestBody Map<String, Object> updates) {
        Student student = findOrCreateStudent(sysUserId);

        if (student == null) {
            return Result.error(404, "学生不存在，请先完成AI导学");
        }

        if (updates.containsKey("knowledgeLevel")) student.setKnowledgeLevel((Integer) updates.get("knowledgeLevel"));
        if (updates.containsKey("cognitiveStyle")) student.setCognitiveStyle((String) updates.get("cognitiveStyle"));
        if (updates.containsKey("learningPreference")) student.setLearningPreference((String) updates.get("learningPreference"));
        if (updates.containsKey("learningPace")) student.setLearningPace((String) updates.get("learningPace"));
        if (updates.containsKey("interestDirection")) student.setInterestDirection((String) updates.get("interestDirection"));
        if (updates.containsKey("weakAreas")) student.setWeakAreas((String) updates.get("weakAreas"));
        if (updates.containsKey("studyMotivation")) student.setStudyMotivation((String) updates.get("studyMotivation"));
        if (updates.containsKey("focusLevel")) student.setFocusLevel((String) updates.get("focusLevel"));

        studentRepository.save(student);
        return Result.success(buildProfileMap(student));
    }

    @PostMapping("/{sysUserId}/analyze")
    public Result<Map<String, Object>> analyzeFromChat(@PathVariable Long sysUserId) {
        Student student = findOrCreateStudent(sysUserId);

        if (student == null) {
            return Result.error(404, "学生不存在，请先完成AI导学");
        }

        try {
            // 画像分析是关键任务
            String aiResult = sparkClient.smartChat("""
                分析该学生的学习画像，以JSON输出：
                {"cognitiveStyle":"visual/auditory/kinesthetic","preference":"video/doc/exercise/mixed",
                "pace":"fast/steady/slow","level":0-100,"interest":"方向","weakness":"薄弱点"}""",
                "学生当前画像: level=" + student.getKnowledgeLevel() + ", style=" + student.getCognitiveStyle(),
                SparkClient.TaskComplexity.CRITICAL, 256);
            String json = aiResult.replaceAll("```json|```", "").trim();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("analysis", json);
            result.put("profile", buildProfileMap(student));
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("分析失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildProfileMap(Student student) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", student.getId());
        profile.put("username", student.getUsername());
        profile.put("knowledgeLevel", student.getKnowledgeLevel());
        profile.put("cognitiveStyle", student.getCognitiveStyle());
        profile.put("learningPreference", student.getLearningPreference());
        profile.put("learningPace", student.getLearningPace());
        profile.put("interestDirection", student.getInterestDirection());
        profile.put("weakAreas", student.getWeakAreas());
        profile.put("studyMotivation", student.getStudyMotivation());
        profile.put("focusLevel", student.getFocusLevel());
        profile.put("progress", student.getProgress());
        profile.put("totalStudyMinutes", student.getTotalStudyMinutes());
        profile.put("streakDays", student.getStreakDays());
        profile.put("lastCheckinDate", student.getLastCheckinDate());
        profile.put("dimensionCount", countDimensions(student));
        profile.put("updatedAt", student.getUpdatedAt());
        return profile;
    }

    private int countDimensions(Student s) {
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
