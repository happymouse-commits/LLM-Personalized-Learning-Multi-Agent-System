package com.happymouse.cryd.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

/**
 * 音频存储服务 — PCM16 → WAV / MP3
 */
@Service
public class AudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(AudioStorageService.class);
    private static final String AUDIO_DIR = "data/audio";
    private static final int SAMPLE_RATE = 24000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    // 章节音频目录映射
    private static final Map<String, String> CHAPTER_AUDIO_MAP = Map.of(
        "基础语法", "chapters/基础语法",
        "流程控制", "chapters/流程控制",
        "数组与指针", "chapters/数组与指针",
        "函数", "chapters/函数",
        "结构体", "chapters/结构体",
        "文件操作", "chapters/文件操作"
    );

    /**
     * 保存音频为 MP3（先转WAV再用ffmpeg转MP3）
     */
    public String saveAudioAsMp3(Long studentId, String resourceId, String pcmBase64) {
        try {
            byte[] pcmBytes = Base64.getDecoder().decode(pcmBase64);

            Path dir = Paths.get(AUDIO_DIR, String.valueOf(studentId));
            Files.createDirectories(dir);

            // 先写临时WAV文件
            Path wavPath = dir.resolve(resourceId + "_temp.wav");
            byte[] wavBytes = pcmToWav(pcmBytes);
            Files.write(wavPath, wavBytes);

            // ffmpeg WAV → MP3
            Path mp3Path = dir.resolve(resourceId + ".mp3");
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", wavPath.toAbsolutePath().toString(),
                "-codec:a", "libmp3lame", "-b:a", "128k",
                mp3Path.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.getInputStream().readAllBytes(); // consume output
            int exitCode = proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) ? proc.exitValue() : -1;
            if (exitCode == -1) {
                proc.destroyForcibly();
                log.warn("ffmpeg超时(30s)，强制终止");
            }

            // 删除临时WAV
            Files.deleteIfExists(wavPath);

            if (exitCode == 0 && Files.exists(mp3Path)) {
                long size = Files.size(mp3Path);
                String relativePath = studentId + "/" + resourceId + ".mp3";
                log.info("MP3音频保存成功: path={}, size={}bytes", relativePath, size);
                return relativePath;
            } else {
                log.error("ffmpeg转MP3失败, exitCode={}", exitCode);
                // 降级保存WAV
                Path wavFinal = dir.resolve(resourceId + ".wav");
                Files.write(wavFinal, wavBytes);
                return studentId + "/" + resourceId + ".wav";
            }
        } catch (Exception e) {
            log.error("音频保存失败: studentId={}, resourceId={}", studentId, resourceId, e);
            return null;
        }
    }

    /**
     * 保存音频为 WAV（旧接口保留）
     */
    public String saveAudio(Long studentId, String resourceId, String pcmBase64) {
        try {
            byte[] pcmBytes = Base64.getDecoder().decode(pcmBase64);
            byte[] wavBytes = pcmToWav(pcmBytes);

            Path dir = Paths.get(AUDIO_DIR, String.valueOf(studentId));
            Files.createDirectories(dir);

            Path filePath = dir.resolve(resourceId + ".wav");
            Files.write(filePath, wavBytes);

            String relativePath = studentId + "/" + resourceId + ".wav";
            log.info("WAV音频保存成功: path={}, size={}bytes", relativePath, wavBytes.length);
            return relativePath;
        } catch (IOException e) {
            log.error("音频保存失败: studentId={}, resourceId={}", studentId, resourceId, e);
            return null;
        }
    }

    public Path getAudioPath(String relativePath) {
        return Paths.get(AUDIO_DIR, relativePath);
    }

    /**
     * 获取章节音频路径
     */
    public String getChapterAudioPath(String chapter, String audioType) {
        String dir = CHAPTER_AUDIO_MAP.get(chapter);
        if (dir == null) {
            log.warn("未知章节: {}", chapter);
            return null;
        }
        // 返回相对路径，例如: chapters/基础语法/基础语法入门-听学笔记.mp3
        return dir + "/" + chapter + audioType + ".mp3";
    }

    /**
     * 检查章节音频是否存在
     */
    public boolean chapterAudioExists(String chapter, String audioType) {
        String path = getChapterAudioPath(chapter, audioType);
        if (path == null) return false;
        Path audioPath = Paths.get(AUDIO_DIR, path);
        return Files.exists(audioPath);
    }

    private byte[] pcmToWav(byte[] pcmData) {
        int dataSize = pcmData.length;
        int fileSize = 36 + dataSize;

        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes());
        buffer.putInt(fileSize);
        buffer.put("WAVE".getBytes());

        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) CHANNELS);
        buffer.putInt(SAMPLE_RATE);
        buffer.putInt(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8);
        buffer.putShort((short) (CHANNELS * BITS_PER_SAMPLE / 8));
        buffer.putShort((short) BITS_PER_SAMPLE);

        buffer.put("data".getBytes());
        buffer.putInt(dataSize);
        buffer.put(pcmData);

        return buffer.array();
    }
}
