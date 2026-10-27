package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {
    List<AiAnalysis> findByStudentId(Long studentId);
    List<AiAnalysis> findByStudentIdAndAnalysisType(Long studentId, String type);
    List<AiAnalysis> findByCourseId(Long courseId);
}
