package com.happymouse.cryd.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AI分析记录 - 存储Agent分析结果
 */
@Entity
@Table(name = "ai_analysis")
public class AiAnalysis {
    @Id
    @SequenceGenerator(name = "aiAnalysis_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aiAnalysis_seq")
    private Long id;

    private Long studentId;

    private Long courseId;

    @Column(length = 50)
    private String analysisType; // profile, exam, learning_plan, risk_warning

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(length = 50)
    private String agentName;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
