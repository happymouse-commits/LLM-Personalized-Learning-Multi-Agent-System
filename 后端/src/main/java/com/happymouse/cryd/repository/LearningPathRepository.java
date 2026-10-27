package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.LearningPath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {
    List<LearningPath> findByStudentId(Long studentId);
    Optional<LearningPath> findByStudentIdAndStatus(Long studentId, String status);
}
