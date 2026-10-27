package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.ChapterProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChapterProgressRepository extends JpaRepository<ChapterProgress, Long> {
    List<ChapterProgress> findByChapterId(Long chapterId);
    Optional<ChapterProgress> findByChapterIdAndStudentId(Long chapterId, Long studentId);
    List<ChapterProgress> findByStudentId(Long studentId);
    List<ChapterProgress> findByChapterIdIn(List<Long> chapterIds);
}