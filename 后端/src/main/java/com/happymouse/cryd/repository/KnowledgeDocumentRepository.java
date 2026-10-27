package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    List<KnowledgeDocument> findByKbId(Long kbId);
    List<KnowledgeDocument> findByKbIdAndStatus(Long kbId, String status);
}
