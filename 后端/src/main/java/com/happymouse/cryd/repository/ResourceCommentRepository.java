package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.ResourceComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceCommentRepository extends JpaRepository<ResourceComment, Long> {
    List<ResourceComment> findByResourceIdOrderByCreatedAtDesc(Long resourceId);
    long countByResourceId(Long resourceId);
}
