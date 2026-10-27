package com.happymouse.cryd.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 学习资源实体
 */
@Entity
@Table(name = "learning_resource")
public class LearningResource {
    @Id
    @SequenceGenerator(name = "learningResource_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "learningResource_seq")
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 100)
    private String knowledgePoint;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 10)
    private String difficulty;

    @Column(length = 50)
    private String generatedBy;

    private Long studentId;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String tags;

    /** 音频URL路径，前端统一使用此字段播放音频 */
    @Column(length = 500)
    private String audioUrl;

    /** PPT课件封面图片URL（第一页幻灯片渲染后的PNG路径） */
    @Column(length = 500)
    private String coverImage;

    @Column(columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean shared = false;

    /** 幻觉评分 0-1，1=完全可靠，null=未评估。仅 AI 生成资源有值 */
    @Column(columnDefinition = "DOUBLE")
    private Double hallucinationScore;

    /** 幻觉风险等级：LOW / MEDIUM / HIGH / CRITICAL */
    @Column(length = 10)
    private String hallucinationRisk;

    /** 幻觉评估说明 */
    @Column(length = 255)
    private String hallucinationNote;

    /** 编译校验状态 PASSED / FAILED / SKIPPED / NOT_CHECKED。仅 type=code 资源有效 */
    @Column(length = 20)
    private String compileStatus;

    /** 编译错误信息（gcc 输出或语法检查错误） */
    @Column(length = 500)
    private String compileError;

    /** 用户反馈类型: USEFUL / ERROR / null（未反馈） */
    @Column(length = 10)
    private String feedbackType;

    /** 用户反馈说明 */
    @Column(length = 255)
    private String feedbackNote;

    /** 内容质量评分 0-1，由checkContentQuality计算 */
    @Column(columnDefinition = "DOUBLE")
    private Double qualityScore;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getKnowledgePoint() { return knowledgePoint; }
    public void setKnowledgePoint(String knowledgePoint) { this.knowledgePoint = knowledgePoint; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public Boolean getShared() { return shared; }
    public void setShared(Boolean shared) { this.shared = shared; }
    public Double getHallucinationScore() { return hallucinationScore; }
    public void setHallucinationScore(Double hallucinationScore) { this.hallucinationScore = hallucinationScore; }
    public String getHallucinationRisk() { return hallucinationRisk; }
    public void setHallucinationRisk(String hallucinationRisk) { this.hallucinationRisk = hallucinationRisk; }
    public String getHallucinationNote() { return hallucinationNote; }
    public void setHallucinationNote(String hallucinationNote) { this.hallucinationNote = hallucinationNote; }
    public String getCompileStatus() { return compileStatus; }
    public void setCompileStatus(String compileStatus) { this.compileStatus = compileStatus; }
    public String getCompileError() { return compileError; }
    public void setCompileError(String compileError) { this.compileError = compileError; }
    public String getFeedbackType() { return feedbackType; }
    public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }
    public String getFeedbackNote() { return feedbackNote; }
    public void setFeedbackNote(String feedbackNote) { this.feedbackNote = feedbackNote; }
    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}