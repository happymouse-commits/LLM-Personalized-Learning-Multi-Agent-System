package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.ErrorNotebook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ErrorNotebookRepository extends JpaRepository<ErrorNotebook, Long> {
    List<ErrorNotebook> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<ErrorNotebook> findByStudentIdAndStatus(Long studentId, String status);
    List<ErrorNotebook> findByStudentIdAndKnowledgePoint(Long studentId, String knowledgePoint);
    List<ErrorNotebook> findByStudentIdAndErrorType(Long studentId, String errorType);
    Optional<ErrorNotebook> findByStudentIdAndChapterIdAndQuestionIndex(Long studentId, Long chapterId, Integer questionIndex);
    List<ErrorNotebook> findByStudentIdAndChapterId(Long studentId, Long chapterId);
    long countByStudentIdAndStatus(Long studentId, String status);

    @Query("SELECT e.knowledgePoint, COUNT(e) FROM ErrorNotebook e WHERE e.status = 'active' AND e.knowledgePoint IS NOT NULL GROUP BY e.knowledgePoint")
    List<Object[]> countErrorsByKnowledgePoint();
}
