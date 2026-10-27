package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.ChatMessage;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.ChatMessageRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 学习报告智能体 — 分析对话历史和学习数据，生成周报/月报。
 */
@Service
public class ReportAgent {

    private static final Logger log = LoggerFactory.getLogger(ReportAgent.class);
    private final SparkClient sparkClient;
    private final StudentRepository studentRepo;
    private final ChatMessageRepository chatRepo;

    public ReportAgent(SparkClient sparkClient, StudentRepository studentRepo, ChatMessageRepository chatRepo) {
        this.sparkClient = sparkClient;
        this.studentRepo = studentRepo;
        this.chatRepo = chatRepo;
    }

    private static final String REPORT_PROMPT = """
        你是学习分析专家。根据学生近期的对话记录和学习数据，生成一份学习报告。

        【学生画像】
        - 知识水平：%d/100
        - 认知风格：%s
        - 学习节奏：%s
        - 薄弱环节：%s
        - 兴趣方向：%s
        - 连续学习天数：%d

        【近期对话摘要】
        %s

        【报告要求】
        生成一份 Markdown 格式的学习报告，包含以下章节：
        ## 📊 学习概览
        - 总结近期学习状态，用数据说话

        ## 🔥 学习亮点
        - 进步的方面、掌握的新知识点

        ## ⚠️ 待加强
        - 薄弱环节、需要重点复习的内容

        ## 📈 学习建议
        - 下一阶段的学习计划建议（3-5条）

        ## 📅 学习统计
        - 对话次数、涉及知识点数量等信息

        报告语气积极鼓励，适合学生阅读。全文 500-800 字。
        """;

    public String generateReport(Long sysUserId, String period) {
        // 通过 SysUser ID 查找学生画像
        var studentOpt = studentRepo.findBySysUserId(sysUserId);
        if (studentOpt.isEmpty()) return "学生数据不存在，请先完成AI导学";
        Student s = studentOpt.get();

        // 获取近期对话（chat表中studentId存的是SysUser ID）
        List<ChatMessage> recent = chatRepo.findTop20ByStudentIdOrderByCreatedAtDesc(sysUserId);
        String chatSummary = recent.stream()
                .filter(m -> m.getContent() != null)
                .map(m -> (m.getRole().equals("user") ? "👤 " : "🤖 ") + truncate(m.getContent(), 80))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(REPORT_PROMPT,
                s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 50,
                blankDefault(s.getCognitiveStyle(), "未测定"),
                blankDefault(s.getLearningPace(), "稳健型"),
                blankDefault(s.getWeakAreas(), "暂无数据"),
                blankDefault(s.getInterestDirection(), "通识基础"),
                s.getStreakDays() != null ? s.getStreakDays() : 0,
                chatSummary.isEmpty() ? "暂无对话记录" : chatSummary);

        try {
            // 学习报告生成是关键任务，使用Pro模型深度推理
            return sparkClient.smartChat(prompt, "生成" + period + "学习报告",
                    SparkClient.TaskComplexity.CRITICAL, 2048);
        } catch (Exception e) {
            log.error("报告生成失败", e);
            return "报告生成失败：" + e.getMessage();
        }
    }

    private String blankDefault(String val, String def) { return val == null || val.isBlank() ? def : val; }
    private String truncate(String s, int n) { return s.length() > n ? s.substring(0, n) + "..." : s; }
}
