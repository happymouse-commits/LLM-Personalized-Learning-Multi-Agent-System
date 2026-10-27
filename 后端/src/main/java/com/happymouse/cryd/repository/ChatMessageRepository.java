package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByStudentIdOrderByCreatedAtAsc(Long studentId);
    Page<ChatMessage> findByStudentIdOrderByCreatedAtAsc(Long studentId, Pageable pageable);

    @Query(value = "SELECT * FROM chat_message WHERE student_id = ?1 ORDER BY created_at DESC LIMIT 20", nativeQuery = true)
    List<ChatMessage> findTop20ByStudentIdOrderByCreatedAtDesc(Long studentId);

    @Query("SELECT DISTINCT m.studentId FROM ChatMessage m WHERE m.createdAt >= :since")
    List<Long> findActiveStudentIdsSince(@Param("since") LocalDateTime since);
}
