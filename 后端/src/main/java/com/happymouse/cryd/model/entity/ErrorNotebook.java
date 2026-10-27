package com.happymouse.cryd.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "error_notebook")
public class ErrorNotebook {
    @Id
    @SequenceGenerator(name = "errorNotebook_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "errorNotebook_seq")
    private Long id;

    private Long studentId;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String studentAnswer;

    @Column(columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(length = 200)
    private String knowledgePoint;

    @Column(length = 50)
    private String difficulty;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    private Integer wrongCount;

    @Column(length = 20)
    private String status; // active, resolved

    /** 错误类型标签: concept(概念不清), calculation(计算错误), misread(审题偏差), logic(代码逻辑错误) */
    @Column(length = 30)
    private String errorType;

    /** 来源关卡ID */
    private Long chapterId;

    /** 题目在关卡中的序号(1-based) */
    private Integer questionIndex;

    /** 错误原因标签(中文) */
    @Column(length = 50)
    private String errorTag;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "active";
        if (wrongCount == null) wrongCount = 1;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getKnowledgePoint() { return knowledgePoint; }
    public void setKnowledgePoint(String knowledgePoint) { this.knowledgePoint = knowledgePoint; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Integer getQuestionIndex() { return questionIndex; }
    public void setQuestionIndex(Integer questionIndex) { this.questionIndex = questionIndex; }
    public String getErrorTag() { return errorTag; }
    public void setErrorTag(String errorTag) { this.errorTag = errorTag; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
