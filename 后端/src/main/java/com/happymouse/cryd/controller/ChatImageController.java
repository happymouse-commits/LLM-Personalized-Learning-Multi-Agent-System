package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.ChatMessage;
import com.happymouse.cryd.repository.ChatMessageRepository;
import com.happymouse.cryd.service.rag.RagService;
import com.happymouse.cryd.service.rag.VectorStore;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * 图片处理 + RAG测试 — 从 ChatController 拆出
 */
@RestController
@RequestMapping("/api/chat")
public class ChatImageController {

    private static final Logger log = LoggerFactory.getLogger(ChatImageController.class);
    private final SparkClient sparkClient;
    private final ChatMessageRepository chatMessageRepository;
    private final RagService ragService;
    private final VectorStore vectorStore;

    @Value("${server.port:8080}")
    private String serverPort;

    public ChatImageController(SparkClient sparkClient, ChatMessageRepository chatMessageRepository,
                                RagService ragService, VectorStore vectorStore) {
        this.sparkClient = sparkClient;
        this.chatMessageRepository = chatMessageRepository;
        this.ragService = ragService;
        this.vectorStore = vectorStore;
    }

    /**
     * RAG四道门测试接口
     */
    @GetMapping("/rag/test")
    public Result<Map<String, Object>> testRag(@RequestParam String query,
                                                @RequestParam(defaultValue = "summary") String taskType) {
        Map<String, Object> result = new HashMap<>();
        result.put("query", query);
        result.put("taskType", taskType);
        result.put("vectorStoreSize", vectorStore.size());

        RagService.RetrievalGateResult gate = ragService.gateByRetrieval(query);
        result.put("gate1_passed", gate.passed);
        result.put("gate1_rejectReason", gate.rejectReason);
        result.put("gate1_hitCount", gate.fragments.size());

        if (!gate.passed) {
            result.put("result", "被第一道门拦截：无相关知识");
            return Result.success(result);
        }

        for (int i = 0; i < Math.min(gate.fragments.size(), 3); i++) {
            RagService.KnowledgeFragment f = gate.fragments.get(i);
            result.put("fragment_" + i + "_similarity", String.format("%.3f", f.similarity));
            result.put("fragment_" + i + "_preview",
                    f.content != null && f.content.length() > 80
                            ? f.content.substring(0, 80) + "..." : f.content);
        }

        RagService.RagPipelineResult pipelineResult = ragService.generateWithGuard(taskType, query);
        result.put("pipeline_success", pipelineResult.success);
        result.put("pipeline_confidence", pipelineResult.confidence);
        result.put("pipeline_error", pipelineResult.errorMessage);
        if (pipelineResult.content != null) {
            result.put("generated_content",
                    pipelineResult.content.length() > 500
                            ? pipelineResult.content.substring(0, 500) + "..." : pipelineResult.content);
        }

        return Result.success(result);
    }

    /**
     * 图片上传接口
     */
    @PostMapping("/upload-image")
    public Result<Map<String, String>> uploadImage(@RequestParam("image") MultipartFile imageFile) {
        log.info("收到图片上传请求, 文件大小: {}, 文件名: {}",
                imageFile.getSize(), imageFile.getOriginalFilename());
        Map<String, String> result = new HashMap<>();

        try {
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("仅支持图片文件（jpg/png/gif/webp）");
            }
            if (imageFile.getSize() > 10 * 1024 * 1024) {
                return Result.error("文件大小不能超过 10MB");
            }

            String uploadDir = "uploads/images/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String originalFilename = imageFile.getOriginalFilename();
            if (originalFilename == null) originalFilename = "image.jpg";
            String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (safeFilename.length() > 200) safeFilename = safeFilename.substring(0, 200);

            String fileName = System.currentTimeMillis() + "_" + safeFilename;
            File destFile = new File(uploadDir + fileName);
            imageFile.transferTo(destFile);

            String url = "http://localhost:" + serverPort + "/api/chat/image/" + fileName;
            result.put("url", url);

            byte[] imageBytes = Files.readAllBytes(destFile.toPath());
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String fileType = imageFile.getContentType();
            if (fileType == null) fileType = "image/jpeg";
            result.put("base64", "data:" + fileType + ";base64," + base64);

            return Result.success(result);
        } catch (IOException e) {
            log.error("图片上传失败", e);
            return Result.error("图片上传失败");
        }
    }

    /**
     * 图片访问接口
     */
    @GetMapping("/image/{fileName}")
    public ResponseEntity<byte[]> getImage(@PathVariable String fileName) {
        // 路径遍历防护
        if (fileName == null || fileName.isEmpty()
                || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            log.warn("拒绝非法图片文件名: {}", fileName);
            return ResponseEntity.notFound().build();
        }
        File file = new File("uploads/images/" + fileName);
        if (!file.exists() || !file.isFile()) {
            log.warn("图片不存在: {}", fileName);
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            log.error("读取图片失败: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 发送图片给AI分析
     */
    @PostMapping("/send-image")
    public Result<ChatResponse> sendImage(@RequestBody Map<String, Object> request) {
        Long studentId = ((Number) request.get("studentId")).longValue();
        String imageBase64 = (String) request.get("imageBase64");

        log.info("收到图片分析请求, studentId: {}", studentId);

        try {
            String prompt = "请分析这张图片的内容，描述图片中包含的信息。";
            String response = sparkClient.chatWithImage(prompt, imageBase64);

            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setAgentName("图像分析师");
            chatResponse.setMessage(response);

            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setStudentId(studentId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(response);
            aiMsg.setAgentName("图像分析师");
            chatMessageRepository.save(aiMsg);

            return Result.success(chatResponse);
        } catch (Exception e) {
            log.error("图片分析失败", e);
            ChatResponse fallback = new ChatResponse();
            fallback.setAgentName("系统");
            fallback.setMessage("图片分析失败，请重试：" + e.getMessage());
            return Result.success(fallback);
        }
    }
}
