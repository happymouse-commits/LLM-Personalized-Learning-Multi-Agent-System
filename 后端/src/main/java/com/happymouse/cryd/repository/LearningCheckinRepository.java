package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.LearningCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface LearningCheckinRepository extends JpaRepository<LearningCheckin, Long> {
    List<LearningCheckin> findByStudentIdOrderByCheckinDateDesc(Long studentId);
    List<LearningCheckin> findByStudentIdAndCheckinDateBetween(Long studentId, LocalDate start, LocalDate end);

    @Query("SELECT COUNT(lc) > 0 FROM LearningCheckin lc WHERE lc.studentId = ?1 AND lc.checkinDate = ?2")
    boolean existsByStudentIdAndCheckinDate(Long studentId, LocalDate date);
}
