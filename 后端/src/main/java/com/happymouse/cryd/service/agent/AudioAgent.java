package com.happymouse.cryd.service.agent;

import com.alibaba.fastjson2.JSONObject;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.service.AudioStorageService;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 听学资源智能体 — 生成播客式文字内容 + MiMo VoiceClone TTS音频（MP3）
 */
@Component
public class AudioAgent implements ResourceAgent {

    private static final Logger log = LoggerFactory.getLogger(AudioAgent.class);

    private final SparkClient sparkClient;
    private final AudioStorageService audioStorage;

    @Value("${mimo.tts.api-key:}")
    private String ttsApiKey;

    @Value("${mimo.tts.base-url:https://api.xiaomimimo.com/v1}")
    private String ttsBaseUrl;

    @Value("${mimo.tts.model:mimo-v2.5-tts-voiceclone}")
    private String ttsModel;

    @Value("${mimo.tts.model-simple:mimo-v2.5-tts}")
    private String ttsModelSimple;

    @Value("${mimo.tts.reference-voice-path:}")
    private String referenceVoicePath;

    // 缓存参考音频的 base64
    private volatile String cachedVoiceBase64 = null;

    public AudioAgent(SparkClient sparkClient, AudioStorageService audioStorage) {
        this.sparkClient = sparkClient;
        this.audioStorage = audioStorage;
    }

    @Override
    public boolean validateJsonFormat(String aiReply) {
        return aiReply != null && !aiReply.isBlank() && aiReply.length() > 50;
    }

    /**
     * 听学资源自检：开场白、内容长度、口语化风格
     */
    @Override
    public double selfCheck(com.happymouse.cryd.model.entity.LearningResource resource) {
        double score = 1.0;
        String content = resource.getContent();
        if (content == null) return 0.0;

        // 必须有开场白
        if (!content.contains("同学们好") && !content.contains("今天我们") && !content.contains("大家好")) {
            score -= 0.15;
        }

        // 内容长度（播客内容应500字以上）
        if (content.length() < 500) score -= 0.3;

        // 口语化风格检查（应有引导词）
        boolean hasColloquial = content.contains("想象一下") || content.contains("你可能会问") ||
                                content.contains("比如说") || content.contains("举个例子");
        if (!hasColloquial) score -= 0.1;

        // 必须有结尾
        if (!content.contains("今天") && !content.contains("下次") && !content.contains("再见")) {
            score -= 0.1;
        }

        return Math.max(0, score);
    }

    @Override
    public String getResourceType() { return ResourceType.ARTICLE.getId(); }

    /**
     * 生成听学资源（接口方法，不含音频）
     */
    @Override
    public LearningResource generate(String chapterName, String knowledgePoint,
                                     String knowledgeLevel, String weakAreas) {
        String textContent = generateTextContent(chapterName, knowledgePoint, knowledgeLevel, weakAreas);
        if (textContent == null || textContent.isBlank()) return null;

        return buildResource(chapterName, knowledgePoint, textContent, null, null, knowledgeLevel);
    }

    /**
     * 生成听学资源（含 VoiceClone 音频 MP3）
     */
    public LearningResource generateWithAudio(String chapterName, String knowledgePoint,
                                              Long studentId, String resourceId,
                                              String knowledgeLevel, String weakAreas) {
        String textContent = generateTextContent(chapterName, knowledgePoint, knowledgeLevel, weakAreas);
        if (textContent == null || textContent.isBlank()) return null;

        // 调用 MiMo VoiceClone TTS → PCM16
        String pcmBase64 = callMiMoVoiceCloneTts(textContent);

        // PCM16 → MP3 → 保存
        String audioPath = null;
        if (pcmBase64 != null) {
            audioPath = audioStorage.saveAudioAsMp3(studentId, resourceId, pcmBase64);
        }

        return buildResource(chapterName, knowledgePoint, textContent, studentId, audioPath, knowledgeLevel);
    }

    /**
     * 加载参考音频（reference.wav → data:audio/wav;base64,...）
     * 优先级：配置显式路径 > Docker标准路径 > 本地开发相对路径 > Windows绝对路径兜底
     */
    private String loadReferenceVoice() {
        if (cachedVoiceBase64 != null) return cachedVoiceBase64;
        try {
            String userDir = System.getProperty("user.dir");
            java.util.List<String> candidateList = new java.util.ArrayList<>();

            // 1. 配置显式路径（优先级最高）
            if (referenceVoicePath != null && !referenceVoicePath.isBlank()) {
                candidateList.add(referenceVoicePath);
            }
            // 2. Docker / 生产环境标准路径
            candidateList.add("/app/data/reference.wav");
            candidateList.add("/app/config/reference.wav");
            candidateList.add(userDir + "/data/reference.wav");
            // 3. 本地开发相对路径
            candidateList.add(userDir + "/../frontend/public/voice/reference.wav");
            candidateList.add(userDir + "/frontend/public/voice/reference.wav");
            // 4. Windows 本地兜底
            candidateList.add("F:/最新/frontend/public/voice/reference.wav");

            Path voicePath = null;
            for (String p : candidateList) {
                Path t = Paths.get(p);
                if (Files.exists(t)) { voicePath = t; break; }
            }
            if (voicePath != null) {
                byte[] bytes = Files.readAllBytes(voicePath);
                cachedVoiceBase64 = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(bytes);
                log.info("已加载参考音频: path={}, bytes={}", voicePath, bytes.length);
                return cachedVoiceBase64;
            }

            // 兜底：classpath 读取 jar 包内置 reference/reference.wav
            try (InputStream is = AudioAgent.class.getClassLoader().getResourceAsStream("reference/reference.wav")) {
                if (is != null) {
                    byte[] bytes = is.readAllBytes();
                    cachedVoiceBase64 = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(bytes);
                    log.info("已加载参考音频(classpath): bytes={}", bytes.length);
                    return cachedVoiceBase64;
                }
            } catch (Exception e) {
                log.debug("classpath加载参考音频失败: {}", e.getMessage());
            }

            log.warn("参考音频文件不存在，将降级为simple模型（无语音克隆）");
        } catch (Exception e) {
            log.warn("加载参考音频失败: {}", e.getMessage());
        }
        return "";
    }

    private String generateTextContent(String chapterName, String knowledgePoint,
                                       String knowledgeLevel, String weakAreas) {
        String weakHint = (weakAreas != null && !weakAreas.isBlank())
                ? "\n【学生薄弱点 — 请重点围绕以下内容讲解】\n" + weakAreas
                  + "\n⚠️ 开场后先花1-2分钟回顾薄弱点，再进入新内容。"
                : "";

        String prompt = String.format("""
            你是C语言教学专家，请为「%s」章节的「%s」知识点生成一段播客式听学内容。

            【学生信息】
            知识水平：%s/100
            %s

            【风格要求】
            - 口语化、对话式，像老师在和学生聊天
            - 开场白：同学们好，今天我们来聊聊...
            - 中间讲解：用"想象一下"、"你可能会问"等引导词
            - 结尾：今天我们学了...，下次见！
            - 适合边听边学，有节奏感

            【内容要求】
            - 核心概念讲解（围绕薄弱点展开，弱化已掌握内容）
            - 1-2个生活化比喻
            - 1个简单代码示例（口述：定义变量、赋值、打印等步骤）
            - 常见错误提醒（结合薄弱点举例）
            - 字数：500-700字

            直接输出内容，不要标题和格式标记。
            """, chapterName, knowledgePoint, knowledgeLevel, weakHint);

        try {
            return sparkClient.smartChat(prompt, "生成听学内容",
                    SparkClient.TaskComplexity.MEDIUM, 2048);
        } catch (Exception e) {
            log.warn("AudioAgent LLM调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用 MiMo VoiceClone TTS — 带参考音频
     */
    private String callMiMoVoiceCloneTts(String text) {
        if (ttsApiKey == null || ttsApiKey.isBlank()) {
            log.warn("TTS API key未配置");
            return null;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("model", ttsModel);
            body.put("stream", false);

            var messages = new com.alibaba.fastjson2.JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "自然清晰的中文朗读，语速适中");
            messages.add(userMsg);

            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", text);
            messages.add(assistantMsg);

            body.put("messages", messages);

            // VoiceClone: 必须带上参考音频；缺失时降级到 simple 模型以避免 MiMo 返回 400
            JSONObject audio = new JSONObject();
            audio.put("format", "pcm16");
            String voice = loadReferenceVoice();
            if (voice != null && !voice.isBlank() && voice.startsWith("data:")) {
                audio.put("voice", voice);
                body.put("model", ttsModel);
            } else {
                log.warn("无有效参考音频(DataURL)，AudioAgent降级为simple模型");
                audio.put("voice", "mimo_default");
                body.put("model", ttsModelSimple);
            }
            body.put("audio", audio);

            String url = ttsBaseUrl + "/chat/completions";
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + ttsApiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(120000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String responseBody = new String(
                code == 200 ? conn.getInputStream().readAllBytes() : conn.getErrorStream().readAllBytes(),
                StandardCharsets.UTF_8
            );

            if (code != 200) {
                log.warn("VoiceClone TTS API错误: code={}, body={}", code, responseBody);
                return null;
            }

            JSONObject json = com.alibaba.fastjson2.JSON.parseObject(responseBody);
            JSONObject audioObj = json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getJSONObject("audio");

            String pcmData = audioObj != null ? audioObj.getString("data") : null;
            if (pcmData != null) {
                log.info("VoiceClone TTS成功: pcm长度={} chars", pcmData.length());
            }
            return pcmData;
        } catch (Exception e) {
            log.error("VoiceClone TTS调用失败", e);
            return null;
        }
    }

    private LearningResource buildResource(String chapterName, String knowledgePoint,
                                           String content, Long studentId, String audioPath,
                                           String knowledgeLevel) {
        LearningResource lr = new LearningResource();
        lr.setTitle("听学笔记：" + chapterName + " - " + knowledgePoint);
        lr.setType(ResourceType.ARTICLE.getId());
        lr.setContent(content);
        lr.setKnowledgePoint(knowledgePoint);
        int level = 30;
        try { level = Integer.parseInt(knowledgeLevel); } catch (Exception ignored) {}
        lr.setDifficulty(level < 40 ? "easy" : level < 70 ? "medium" : "hard");
        lr.setStudentId(studentId);
        lr.setGeneratedBy("audio-agent");
        lr.setCategory("听学资源");
        if (audioPath != null) {
            lr.setTags(audioPath);
            // 同时设置audioUrl字段，前端统一使用audioUrl
            lr.setAudioUrl(audioPath);
        }
        return lr;
    }
}
