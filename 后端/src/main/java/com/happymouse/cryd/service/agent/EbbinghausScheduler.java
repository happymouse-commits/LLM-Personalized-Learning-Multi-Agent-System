package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.ChatMessage;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.ChatMessageRepository;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 艾宾浩斯遗忘曲线调度器 —— 基于科学记忆规律，在关键遗忘节点自动推送复习资源。
 *
 * 遗忘曲线节点：学习后 1天 / 2天 / 4天 / 7天 / 15天
 * 每30分钟扫描一次，检查是否有学生到达复习节点。
 */
@Service
public class EbbinghausScheduler {

    private static final Logger log = LoggerFactory.getLogger(EbbinghausScheduler.class);

    // 艾宾浩斯遗忘曲线关键复习节点（天数）
    private static final int[] REVIEW_INTERVALS = {1, 2, 4, 7, 15};

    private final StudentRepository studentRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final LearningResourceRepository resourceRepo;
    private final AutoResourceGenerator autoResourceGenerator;

    public EbbinghausScheduler(StudentRepository studentRepo,
                               ChatMessageRepository chatMessageRepo,
                               LearningResourceRepository resourceRepo,
                               AutoResourceGenerator autoResourceGenerator) {
        this.studentRepo = studentRepo;
        this.chatMessageRepo = chatMessageRepo;
        this.resourceRepo = resourceRepo;
        this.autoResourceGenerator = autoResourceGenerator;
    }

    /**
     * 每30分钟扫描一次，检查需要复习的学生
     * 条件：学生有对话记录且距上次学习到达艾宾浩斯节点
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000) // 30分钟
    public void scanAndGenerateReview() {
        log.info("艾宾浩斯调度器开始扫描...");
        // 只扫描最近16天有活跃记录的学生（最大复习间隔15天）
        LocalDateTime since = LocalDateTime.now().minusDays(16);
        List<Long> activeStudentIds = chatMessageRepo.findActiveStudentIdsSince(since);

        for (Long studentId : activeStudentIds) {
            try {
                studentRepo.findById(studentId).ifPresent(this::processStudentReview);
            } catch (Exception e) {
                log.warn("处理学生复习失败: studentId={}", studentId, e);
            }
        }
        log.info("艾宾浩斯调度器扫描完成，共检查{}名活跃学生", activeStudentIds.size());
    }

    private void processStudentReview(Student student) {
        Long studentId = student.getId();

        // 获取该学生最近的对话记录
        List<ChatMessage> recentMessages = chatMessageRepo.findTop20ByStudentIdOrderByCreatedAtDesc(studentId);
        if (recentMessages.isEmpty()) return;

        // 找到最后一次学习时间（最后一条用户消息）
        LocalDateTime lastLearnTime = null;
        for (ChatMessage msg : recentMessages) {
            if ("user".equals(msg.getRole()) && msg.getCreatedAt() != null) {
                lastLearnTime = msg.getCreatedAt();
                break;
            }
        }
        if (lastLearnTime == null) return;

        // 计算距今天数
        long daysSince = java.time.Duration.between(lastLearnTime, LocalDateTime.now()).toDays();

        // 检查是否命中艾宾浩斯节点
        final int hitInterval = findHitInterval(daysSince);
        if (hitInterval < 0) return;

        // 检查该节点是否已经生成过复习资源（去重）
        List<LearningResource> existing = resourceRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
        boolean alreadyReviewed = existing.stream()
                .anyMatch(r -> "ebbinghaus-review".equals(r.getGeneratedBy())
                        && r.getTags() != null
                        && r.getTags().contains("第" + hitInterval + "天"));
        if (alreadyReviewed) {
            log.debug("学生{}第{}天复习资源已存在，跳过", studentId, hitInterval);
            return;
        }

        // 从对话中提取知识点
        String conversationText = recentMessages.stream()
                .map(m -> m.getContent() != null ? m.getContent() : "")
                .collect(Collectors.joining(" "));
        Set<String> kps = autoResourceGenerator.extractKnowledgePoints(conversationText);

        // 加上学生画像中的薄弱环节
        if (student.getWeakAreas() != null && !student.getWeakAreas().isBlank()) {
            Arrays.stream(student.getWeakAreas().split("[、，,]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(kps::add);
        }

        if (kps.isEmpty()) {
            kps = Set.of("C语言基础");
        }

        // 生成复习资源
        log.info("触发艾宾浩斯复习: studentId={}, 第{}天, 知识点={}", studentId, hitInterval, kps);
        autoResourceGenerator.generateReviewResources(studentId, kps, hitInterval);
    }

    /**
     * 修复E1: 原 findHitInterval 用 `>=` 匹配，学生学完第15天后每次30分钟扫描
     *   都会命中 15天节点，仅靠 tags 字段去重拦截。若 tags 被改/被清，会无限生成。
     *
     *   新策略：精确匹配当天（daysSince == interval）作为主路径，
     *   加 1 天宽限窗口（daysSince == interval + 1）兜底防止错过扫描，
     *   且要求上次学习时间距今不超过 interval + 2 天（避免陈旧数据反复触发）。
     */
    private int findHitInterval(long daysSince) {
        for (int interval : REVIEW_INTERVALS) {
            if (daysSince == interval || daysSince == interval + 1) {
                return interval;
            }
        }
        return -1;
    }
}