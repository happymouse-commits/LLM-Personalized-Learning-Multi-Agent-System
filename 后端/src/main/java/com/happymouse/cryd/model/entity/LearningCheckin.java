package com.happymouse.cryd.model.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_checkin")
public class LearningCheckin {
    @Id
    @SequenceGenerator(name = "learningCheckin_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "learningCheckin_seq")
    private Long id;

    private Long studentId;
    private Long pathStepId;

    @Column(length = 200)
    private String description;

    private LocalDate checkinDate;

    @Column(length = 50)
    private String status; // completed, skipped

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (checkinDate == null) checkinDate = LocalDate.now();
        if (status == null) status = "completed";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getPathStepId() { return pathStepId; }
    public void setPathStepId(Long pathStepId) { this.pathStepId = pathStepId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
