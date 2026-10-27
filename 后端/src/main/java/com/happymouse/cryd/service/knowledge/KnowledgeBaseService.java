package com.happymouse.cryd.service.knowledge;

import com.happymouse.cryd.model.entity.KnowledgeBase;
import com.happymouse.cryd.model.entity.KnowledgeChunk;
import com.happymouse.cryd.model.entity.KnowledgeDocument;
import com.happymouse.cryd.repository.KnowledgeBaseRepository;
import com.happymouse.cryd.repository.KnowledgeChunkRepository;
import com.happymouse.cryd.repository.KnowledgeDocumentRepository;
import com.happymouse.cryd.service.rag.DocumentChunker;
import com.happymouse.cryd.service.rag.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepo;
    private final KnowledgeDocumentRepository docRepo;
    private final KnowledgeChunkRepository chunkRepo;
    private final VectorStore vectorStore;
    private final DocumentChunker chunker;
    private final TagExtractor tagExtractor;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                 KnowledgeDocumentRepository docRepo,
                                 KnowledgeChunkRepository chunkRepo,
                                 VectorStore vectorStore,
                                 DocumentChunker chunker,
                                 TagExtractor tagExtractor) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.vectorStore = vectorStore;
        this.chunker = chunker;
        this.tagExtractor = tagExtractor;
    }

    // --- KnowledgeBase CRUD ---
    public KnowledgeBase createKB(Long courseId, String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setCourseId(courseId);
        kb.setName(name);
        kb.setDescription(description);
        return kbRepo.save(kb);
    }

    public KnowledgeBase getKB(Long kbId) {
        return kbRepo.findById(kbId).orElse(null);
    }

    public List<KnowledgeBase> listAll() {
        return kbRepo.findAll();
    }

    public List<KnowledgeBase> getByCourse(Long courseId) {
        return kbRepo.findByCourseId(courseId);
    }

    // --- Document upload & processing ---
    @Transactional
    public KnowledgeDocument uploadDocument(Long kbId, MultipartFile file) throws IOException {
        KnowledgeBase kb = getKB(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setOriginalContent(content);
        doc.setStatus("pending");
        docRepo.save(doc);

        // 异步处理
        processDocument(doc, content);

        return doc;
    }

    @Transactional
    public KnowledgeDocument uploadTextDocument(Long kbId, String fileName, String content) {
        KnowledgeBase kb = getKB(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setFileName(fileName);
        doc.setFileType("txt");
        doc.setOriginalContent(content);
        doc.setStatus("pending");
        docRepo.save(doc);

        processDocument(doc, content);
        return doc;
    }

    /**
     * 处理文档：清洗 -> 切片 -> 标签提取 -> 向量化
     */
    private void processDocument(KnowledgeDocument doc, String content) {
        try {
            doc.setStatus("processing");
            docRepo.save(doc);

            // 清洗内容
            String cleaned = cleanContent(content);
            doc.setProcessedContent(cleaned);

            // 提取标签
            List<String> tags = tagExtractor.extractTags(cleaned);
            doc.setTags(String.join(",", tags));

            // 切片
            List<String> chunks = chunker.chunk(cleaned);

            // 向量化存储
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);

                // 保存chunk到数据库
                KnowledgeChunk kc = new KnowledgeChunk();
                kc.setDocumentId(doc.getId());
                kc.setKbId(doc.getKbId());
                kc.setChunkIndex(i);
                kc.setContent(chunkText);
                kc.setTags(String.join(",", tags));
                kc = chunkRepo.save(kc);

                // 向量化并存储
                Map<String, String> metadata = new HashMap<>();
                metadata.put("content", chunkText);
                metadata.put("tags", String.join(",", tags));
                metadata.put("source", doc.getFileName());
                metadata.put("docId", doc.getId().toString());
                metadata.put("chunkId", kc.getId().toString());

                String vectorId = vectorStore.addText(chunkText, metadata);
                kc.setVectorId(vectorId);
                chunkRepo.save(kc);
            }

            doc.setStatus("vectorized");
            docRepo.save(doc);

            // 更新知识库chunk计数
            KnowledgeBase kb = getKB(doc.getKbId());
            if (kb != null) {
                kb.setChunkCount((kb.getChunkCount() != null ? kb.getChunkCount() : 0) + chunks.size());
                kbRepo.save(kb);
            }

            log.info("文档处理完成: {} -> {}个chunk", doc.getFileName(), chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: {}", e.getMessage());
            doc.setStatus("failed");
            docRepo.save(doc);
        }
    }

    public List<KnowledgeDocument> getDocuments(Long kbId) {
        return docRepo.findByKbId(kbId);
    }

    @Transactional
    public void deleteDocument(Long docId) {
        KnowledgeDocument doc = docRepo.findById(docId).orElse(null);
        if (doc == null) return;

        // 删除chunks和向量
        List<KnowledgeChunk> chunks = chunkRepo.findByDocumentId(docId);
        for (KnowledgeChunk c : chunks) {
            if (c.getVectorId() != null) {
                vectorStore.remove(c.getVectorId());
            }
        }
        chunkRepo.deleteAll(chunks);
        docRepo.delete(doc);

        // 更新KB计数
        KnowledgeBase kb = getKB(doc.getKbId());
        if (kb != null) {
            kb.setChunkCount(Math.max(0, (kb.getChunkCount() != null ? kb.getChunkCount() : 0) - chunks.size()));
            kbRepo.save(kb);
        }
    }

    /**
     * 语义搜索
     */
    public List<KnowledgeChunk> search(Long kbId, String query, int topK) {
        // 使用VectorStore进行语义搜索
        List<com.happymouse.cryd.service.rag.VectorStore.SearchResult> results =
            vectorStore.search(query, topK, 0.5);
        // 批量查询 chunk 替代 N 个单独 findById
        List<Long> chunkIds = results.stream()
            .map(r -> r.getMetadata().get("chunkId"))
            .filter(Objects::nonNull)
            .map(Long::parseLong)
            .toList();
        if (chunkIds.isEmpty()) return List.of();
        List<KnowledgeChunk> allChunks = chunkRepo.findAllById(chunkIds);
        return allChunks.stream()
            .filter(c -> c.getKbId().equals(kbId))
            .toList();
    }

    public List<String> getTags(Long kbId) {
        Set<String> tags = new HashSet<>();
        List<KnowledgeDocument> docs = docRepo.findByKbId(kbId);
        for (KnowledgeDocument doc : docs) {
            if (doc.getTags() != null) {
                Collections.addAll(tags, doc.getTags().split(","));
            }
        }
        return new ArrayList<>(tags);
    }

    /**
     * 重建索引：用新的 chunkSize/overlap 参数对知识库所有文档重新切块、向量化
     */
    @Transactional
    public Map<String, Object> reindex(Long kbId, int chunkSize, int overlap) {
        KnowledgeBase kb = getKB(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        long startTime = System.currentTimeMillis();
        List<KnowledgeDocument> docs = docRepo.findByKbId(kbId);
        int totalOldChunks = 0, totalNewChunks = 0, docCount = 0;

        // 临时设置新的切片参数
        int oldChunkSize = 512, oldOverlap = 64;
        chunker.setChunkSize(chunkSize);
        chunker.setChunkOverlap(overlap);

        for (KnowledgeDocument doc : docs) {
            if (doc.getOriginalContent() == null || doc.getOriginalContent().isEmpty()) continue;

            // 1. 删除旧的 chunks 和向量
            List<KnowledgeChunk> oldChunks = chunkRepo.findByDocumentId(doc.getId());
            totalOldChunks += oldChunks.size();
            for (KnowledgeChunk c : oldChunks) {
                if (c.getVectorId() != null) {
                    vectorStore.remove(c.getVectorId());
                }
            }
            chunkRepo.deleteAll(oldChunks);

            // 2. 用新参数重新切片
            String cleaned = cleanContent(doc.getOriginalContent());
            List<String> newChunks = chunker.chunk(cleaned);

            // 3. 重新向量化存储
            for (int i = 0; i < newChunks.size(); i++) {
                String chunkText = newChunks.get(i);

                KnowledgeChunk kc = new KnowledgeChunk();
                kc.setDocumentId(doc.getId());
                kc.setKbId(kbId);
                kc.setChunkIndex(i);
                kc.setContent(chunkText);
                kc.setTags(doc.getTags());
                kc = chunkRepo.save(kc);

                Map<String, String> metadata = new HashMap<>();
                metadata.put("content", chunkText);
                metadata.put("tags", doc.getTags() != null ? doc.getTags() : "");
                metadata.put("source", doc.getFileName());
                metadata.put("docId", doc.getId().toString());
                metadata.put("chunkId", kc.getId().toString());

                String vectorId = vectorStore.addText(chunkText, metadata);
                kc.setVectorId(vectorId);
                chunkRepo.save(kc);
            }

            totalNewChunks += newChunks.size();
            docCount++;
        }

        // 更新知识库 chunk 计数
        kb.setChunkCount(totalNewChunks);
        kbRepo.save(kb);

        // 持久化向量库
        vectorStore.flush();

        long elapsed = System.currentTimeMillis() - startTime;

        Map<String, Object> result = new HashMap<>();
        result.put("kbId", kbId);
        result.put("docCount", docCount);
        result.put("oldChunkCount", totalOldChunks);
        result.put("newChunkCount", totalNewChunks);
        result.put("chunkSize", chunkSize);
        result.put("overlap", overlap);
        result.put("elapsedMs", elapsed);

        log.info("知识库重建索引完成: kbId={}, {}个文档, {}->{}个chunk, chunkSize={}, overlap={}, 耗时{}ms",
            kbId, docCount, totalOldChunks, totalNewChunks, chunkSize, overlap, elapsed);

        return result;
    }

    private String cleanContent(String content) {
        return content
            .replaceAll("\\r\\n", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".doc")) return "doc";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".md")) return "md";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "ppt";
        return "other";
    }
}
