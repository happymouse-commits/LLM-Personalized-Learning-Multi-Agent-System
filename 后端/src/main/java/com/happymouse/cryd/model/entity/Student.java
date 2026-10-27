package com.happymouse.cryd.model.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学生实体
 */
@Entity
@Table(name = "student")
public class Student {
    @Id
    @SequenceGenerator(name = "student_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "student_seq")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "sys_user_id", unique = true)
    private Long sysUserId;

    @Column(length = 50)
    private String nickname;

    private Integer knowledgeLevel;

    @Column(length = 20)
    private String cognitiveStyle;

    @Column(length = 20)
    private String learningPreference;

    @Column(length = 20)
    private String learningPace;

    @Column(length = 200)
    private String interestDirection;

    @Column(length = 200)
    private String weakAreas;

    @Column(length = 20)
    private String studyMotivation;

    @Column(length = 20)
    private String focusLevel;

    private Integer progress;

    private Boolean onboarded;

    private Integer totalStudyMinutes;
    private Integer streakDays;

    private LocalDate lastCheckinDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (progress == null) progress = 0;
        if (knowledgeLevel == null) knowledgeLevel = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getSysUserId() { return sysUserId; }
    public void setSysUserId(Long sysUserId) { this.sysUserId = sysUserId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getKnowledgeLevel() { return knowledgeLevel; }
    public void setKnowledgeLevel(Integer knowledgeLevel) { this.knowledgeLevel = knowledgeLevel; }
    public String getCognitiveStyle() { return cognitiveStyle; }
    public void setCognitiveStyle(String cognitiveStyle) { this.cognitiveStyle = cognitiveStyle; }
    public String getLearningPreference() { return learningPreference; }
    public void setLearningPreference(String learningPreference) { this.learningPreference = learningPreference; }
    public String getLearningPace() { return learningPace; }
    public void setLearningPace(String learningPace) { this.learningPace = learningPace; }
    public String getInterestDirection() { return interestDirection; }
    public void setInterestDirection(String interestDirection) { this.interestDirection = interestDirection; }
    public String getWeakAreas() { return weakAreas; }
    public void setWeakAreas(String weakAreas) { this.weakAreas = weakAreas; }
    public String getStudyMotivation() { return studyMotivation; }
    public void setStudyMotivation(String studyMotivation) { this.studyMotivation = studyMotivation; }
    public String getFocusLevel() { return focusLevel; }
    public void setFocusLevel(String focusLevel) { this.focusLevel = focusLevel; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public Boolean getOnboarded() { return onboarded; }
    public void setOnboarded(Boolean onboarded) { this.onboarded = onboarded; }
    public Integer getTotalStudyMinutes() { return totalStudyMinutes; }
    public void setTotalStudyMinutes(Integer totalStudyMinutes) { this.totalStudyMinutes = totalStudyMinutes; }
    public Integer getStreakDays() { return streakDays; }
    public void setStreakDays(Integer streakDays) { this.streakDays = streakDays; }
    public LocalDate getLastCheckinDate() { return lastCheckinDate; }
    public void setLastCheckinDate(LocalDate lastCheckinDate) { this.lastCheckinDate = lastCheckinDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
