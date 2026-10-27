package com.happymouse.cryd.controller;

import com.happymouse.cryd.service.AudioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

/**
 * 音频播放API — 提供学习资源音频文件访问
 */
@RestController
@RequestMapping("/api/audio")
@CrossOrigin(origins = {"http://localhost:5174", "http://localhost:5173", "http://localhost:8090"})
public class AudioController {

    private static final Logger log = LoggerFactory.getLogger(AudioController.class);

    private final AudioStorageService audioStorage;

    public AudioController(AudioStorageService audioStorage) {
        this.audioStorage = audioStorage;
    }

    /**
     * 播放章节音频文件
     * GET /api/audio/chapters/{chapter}/{filename}
     */
    @GetMapping("/chapters/{chapter}/{filename}")
    public ResponseEntity<Resource> playChapterAudio(
            @PathVariable String chapter,
            @PathVariable String filename) {

        // 路径遍历防护：拒绝含路径分隔符或 .. 的输入
        if (!isSafeName(chapter) || !isSafeName(filename)) {
            log.warn("拒绝非法音频路径参数: chapter={}, filename={}", chapter, filename);
            return ResponseEntity.badRequest().build();
        }

        String relativePath = "chapters/" + chapter + "/" + filename;
        Path filePath = audioStorage.getAudioPath(relativePath);

        log.info("请求音频文件: chapter={}, filename={}, relativePath={}, filePath={}",
                chapter, filename, relativePath, filePath.toAbsolutePath());

        if (!java.nio.file.Files.exists(filePath)) {
            log.warn("音频文件不存在: {} (绝对路径: {})", relativePath, filePath.toAbsolutePath());
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);

        // 根据文件扩展名设置正确的Content-Type
        String contentType = "audio/mpeg"; // 默认MP3
        if (filename.endsWith(".wav")) {
            contentType = "audio/wav";
        } else if (filename.endsWith(".mp3")) {
            contentType = "audio/mpeg";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    /**
     * 播放音频文件（旧接口兼容）
     * GET /api/audio/{studentId}/{filename}
     */
    @GetMapping("/{studentId}/{filename}")
    public ResponseEntity<Resource> playAudioLegacy(
            @PathVariable Long studentId,
            @PathVariable String filename) {

        // 路径遍历防护
        if (!isSafeName(filename)) {
            log.warn("拒绝非法音频文件名: filename={}", filename);
            return ResponseEntity.badRequest().build();
        }

        String relativePath = studentId + "/" + filename;
        Path filePath = audioStorage.getAudioPath(relativePath);

        if (!java.nio.file.Files.exists(filePath)) {
            log.warn("音频文件不存在: {}", relativePath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    /**
     * 校验路径段是否安全：禁止包含路径分隔符、盘符、上级目录引用
     */
    private static boolean isSafeName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains("..") || name.contains("/") || name.contains("\\"))
            return false;
        // 拒绝 Windows 盘符前缀（如 C:）
        if (name.length() >= 2 && name.charAt(1) == ':') return false;
        return true;
    }
}
