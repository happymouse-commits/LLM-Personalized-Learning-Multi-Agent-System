package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.TestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestRecordRepository extends JpaRepository<TestRecord, Long> {
    List<TestRecord> findByStudentIdOrderByStartedAtDesc(Long studentId);
    List<TestRecord> findByStudentIdAndStatus(Long studentId, String status);
}
