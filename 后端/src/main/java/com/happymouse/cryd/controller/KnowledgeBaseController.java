package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.KnowledgeBase;
import com.happymouse.cryd.model.entity.KnowledgeDocument;
import com.happymouse.cryd.service.knowledge.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);
    private final KnowledgeBaseService kbService;

    public KnowledgeBaseController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    // 列出所有知识库
    @GetMapping
    public Result<List<KnowledgeBase>> listAll() {
        return Result.success(kbService.listAll());
    }

    // 创建知识库
    @PostMapping
    public Result<KnowledgeBase> create(@RequestBody Map<String, Object> body) {
        Long courseId = toLong(body.get("courseId"));
        String name = (String) body.get("name");
        String desc = (String) body.get("description");
        return Result.success(kbService.createKB(courseId, name, desc));
    }

    // 获取知识库详情
    @GetMapping("/{kbId}")
    public Result<KnowledgeBase> getById(@PathVariable Long kbId) {
        return Result.success(kbService.getKB(kbId));
    }

    // 按课程获取知识库
    @GetMapping("/course/{courseId}")
    public Result<List<KnowledgeBase>> getByCourse(@PathVariable Long courseId) {
        return Result.success(kbService.getByCourse(courseId));
    }

    // 上传文档
    @PostMapping("/{kbId}/documents")
    public Result<KnowledgeDocument> uploadDocument(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file) {
        try {
            KnowledgeDocument doc = kbService.uploadDocument(kbId, file);
            return Result.success(doc);
        } catch (Exception e) {
            log.error("上传文档失败: {}", e.getMessage());
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    // 上传文本内容作为文档
    @PostMapping("/{kbId}/documents/text")
    public Result<KnowledgeDocument> uploadTextDocument(
            @PathVariable Long kbId,
            @RequestBody Map<String, String> body) {
        String fileName = body.getOrDefault("fileName", "untitled.txt");
        String content = body.get("content");
        return Result.success(kbService.uploadTextDocument(kbId, fileName, content));
    }

    // 获取文档列表
    @GetMapping("/{kbId}/documents")
    public Result<List<KnowledgeDocument>> getDocuments(@PathVariable Long kbId) {
        return Result.success(kbService.getDocuments(kbId));
    }

    // 删除文档
    @DeleteMapping("/{kbId}/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
        kbService.deleteDocument(docId);
        return Result.success(null);
    }

    // 搜索
    @GetMapping("/{kbId}/search")
    public Result<?> search(@PathVariable Long kbId,
                             @RequestParam("q") String query) {
        return Result.success(kbService.search(kbId, query, 5));
    }

    // 标签列表
    @GetMapping("/{kbId}/tags")
    public Result<List<String>> getTags(@PathVariable Long kbId) {
        return Result.success(kbService.getTags(kbId));
    }

    // 重建索引：调整切片参数后重新向量化
    @PostMapping("/{kbId}/reindex")
    public Result<?> reindex(@PathVariable Long kbId,
                             @RequestBody Map<String, Object> body) {
        try {
            int chunkSize = body.containsKey("chunkSize") ? ((Number) body.get("chunkSize")).intValue() : 512;
            int overlap = body.containsKey("overlap") ? ((Number) body.get("overlap")).intValue() : 64;
            // 参数范围限制
            chunkSize = Math.max(100, Math.min(1000, chunkSize));
            overlap = Math.max(10, Math.min(200, overlap));
            if (overlap >= chunkSize) overlap = chunkSize / 4;

            Map<String, Object> result = kbService.reindex(kbId, chunkSize, overlap);
            return Result.success(result);
        } catch (Exception e) {
            log.error("重建索引失败: {}", e.getMessage());
            return Result.error("重建索引失败: " + e.getMessage());
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}
