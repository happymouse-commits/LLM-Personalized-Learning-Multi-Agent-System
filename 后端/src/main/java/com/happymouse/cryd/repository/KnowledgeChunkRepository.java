package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    List<KnowledgeChunk> findByKbId(Long kbId);
    List<KnowledgeChunk> findByDocumentId(Long documentId);
    List<KnowledgeChunk> findByKbIdAndTagsContaining(Long kbId, String tag);
}
