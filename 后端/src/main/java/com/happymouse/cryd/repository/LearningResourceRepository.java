package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.LearningResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {
    long countByStudentId(Long studentId);
    List<LearningResource> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<LearningResource> findByStudentIdAndTypeOrderByCreatedAtDesc(Long studentId, String type);
    Page<LearningResource> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);
    void deleteAllByStudentId(Long studentId);
    void deleteByStudentIdAndGeneratedBy(Long studentId, String generatedBy);

    // === 优化：针对性查询，替代全量加载+Java过滤 ===

    /** 统计某学生某类资源数量（替代全量加载后 stream.filter.count） */
    long countByStudentIdAndGeneratedBy(Long studentId, String generatedBy);

    /** 按 generatedBy 过滤资源 */
    List<LearningResource> findByStudentIdAndGeneratedByOrderByCreatedAtDesc(Long studentId, String generatedBy);

    /** 按 generatedBy 排除过滤资源（用于今日推荐） */
    @Query("SELECT r FROM LearningResource r WHERE r.studentId = :studentId AND r.generatedBy NOT IN :excludedTypes ORDER BY r.createdAt DESC")
    List<LearningResource> findRecentExcluding(@Param("studentId") Long studentId, @Param("excludedTypes") List<String> excludedTypes, Pageable pageable);

    /** 按知识点模糊搜索（用于章节内容） */
    List<LearningResource> findByStudentIdAndKnowledgePointContainingOrderByCreatedAtDesc(Long studentId, String knowledgePoint);

    /** 批量查询 chunk（替代 N 个单独 findById） */
    @Query("SELECT c FROM KnowledgeChunk c WHERE c.id IN :ids")
    List<com.happymouse.cryd.model.entity.KnowledgeChunk> findChunksByIds(@Param("ids") List<Long> ids);
}
