package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByCourseIdOrderByOrderNum(Long courseId);
    List<Chapter> findByCourseIdAndStatusOrderByOrderNum(Long courseId, String status);
    List<Chapter> findByStatusOrderByOrderNum(String status);
}