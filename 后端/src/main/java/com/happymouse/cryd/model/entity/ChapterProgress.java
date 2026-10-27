package com.happymouse.cryd.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 关卡进度实体 - 学生每个关卡的完成情况
 */
@Entity
@Table(name = "chapter_progress")
public class ChapterProgress {
    @Id
    @SequenceGenerator(name = "chapterProgress_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chapterProgress_seq")
    private Long id;

    private Long chapterId;

    private Long studentId;

    /** 学生提交的答案JSON: {"1":"A","2":"B","3":"C","4":"D","5":"填空答案"} */
    @Column(columnDefinition = "TEXT")
    private String answers;

    private Integer score;

    /** 状态: not_started / in_progress / completed */
    @Column(length = 20)
    private String status;

    /** AI批改评语 */
    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(length = 50)
    private String gradedBy;

    private LocalDateTime submittedAt;

    private LocalDateTime gradedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getAnswers() { return answers; }
    public void setAnswers(String answers) { this.answers = answers; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public String getGradedBy() { return gradedBy; }
    public void setGradedBy(String gradedBy) { this.gradedBy = gradedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(LocalDateTime gradedAt) { this.gradedAt = gradedAt; }
}