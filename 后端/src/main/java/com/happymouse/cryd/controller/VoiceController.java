package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.service.XunfeiAsrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);
    private final XunfeiAsrService xunfeiAsrService;

    public VoiceController(XunfeiAsrService xunfeiAsrService) {
        this.xunfeiAsrService = xunfeiAsrService;
    }

    /**
     * 语音转文字
     * 前端发送 base64 音频，返回识别文字
     */
    @PostMapping("/recognize")
    public Result<Map<String, String>> recognize(@RequestBody Map<String, String> request) {
        String audioBase64 = request.get("audio");
        if (audioBase64 == null || audioBase64.isBlank()) {
            return Result.error("音频数据为空");
        }
        log.info("[语音识别] 收到音频数据，长度={}", audioBase64.length());
        String text = xunfeiAsrService.recognize(audioBase64);
        log.info("[语音识别] 识别结果: \"{}\"", text);
        return Result.success(Map.of("text", text != null ? text : ""));
    }
}
