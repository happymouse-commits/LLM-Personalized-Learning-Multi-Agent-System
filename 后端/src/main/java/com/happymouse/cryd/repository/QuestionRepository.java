package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByCourseIdOrderByChapterOrderAsc(Long courseId);
    List<Question> findByChapterNameOrderByTypeAsc(String chapterName);
    List<Question> findByDifficultyOrderByChapterOrderAsc(String difficulty);
    List<Question> findBySourceOrderByCreatedAtDesc(String source);
    List<Question> findByCourseIdAndDifficultyOrderByChapterOrderAsc(Long courseId, String difficulty);
    List<Question> findByCourseIdAndChapterNameOrderByTypeAsc(Long courseId, String chapterName);

    @Query("SELECT DISTINCT q.chapterName FROM Question q WHERE q.courseId = :courseId ORDER BY q.chapterOrder ASC")
    List<String> findDistinctChapterNames(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT q.difficulty FROM Question q")
    List<String> findDistinctDifficulties();

    @Query("SELECT COUNT(q) FROM Question q WHERE q.courseId = :courseId AND q.chapterName = :chapterName")
    int countByCourseIdAndChapterName(@Param("courseId") Long courseId, @Param("chapterName") String chapterName);

    List<Question> findByIdIn(List<Long> ids);
}
