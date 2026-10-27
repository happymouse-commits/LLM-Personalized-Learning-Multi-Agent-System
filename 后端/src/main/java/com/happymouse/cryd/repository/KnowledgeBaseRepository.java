package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    List<KnowledgeBase> findByCourseId(Long courseId);
}
