package com.happymouse.cryd.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/tts")
@CrossOrigin(origins = {"http://localhost:5174", "http://localhost:5173", "http://localhost:8090"})
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);

    // 文本长度限制
    private static final int MAX_TEXT_LENGTH = 2000;

    // 缓存目录
    private static final String CACHE_DIR = "data/tts-cache";
    private static final String VOICECLONE_CACHE_FILE = CACHE_DIR + "/voiceclone-cache.json";
    private static final String SIMPLE_CACHE_FILE = CACHE_DIR + "/simple-cache.json";

    // 参考音频缓存
    private volatile String cachedVoiceBase64 = null;

    // 内存缓存（用于快速读取）
    private final ConcurrentHashMap<String, CacheEntry> voiceCloneCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry> simpleCache = new ConcurrentHashMap<>();

    // 缓存条目（包含音频数据和元数据）
    public static class CacheEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public String text;
        public String audio;
        public long createdAt;
        public String voiceType;  // voiceclone 或 simple
        public String source;     // api 或 demo

        public CacheEntry(String text, String audio, String voiceType, String source) {
            this.text = text;
            this.audio = audio;
            this.createdAt = System.currentTimeMillis();
            this.voiceType = voiceType;
            this.source = source;
        }
    }

    @Value("${mimo.tts.api-key:}")
    private String apiKey;

    @Value("${mimo.tts.base-url:https://api.xiaomimimo.com/v1}")
    private String baseUrl;

    @Value("${mimo.tts.model:mimo-v2.5-tts-voiceclone}")
    private String model;

    @Value("${mimo.tts.model-simple:mimo-v2.5-tts}")
    private String modelSimple;

    @Value("${mimo.tts.reference-voice-path:}")
    private String referenceVoicePath;

    /**
     * 初始化：从磁盘加载缓存
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        loadCacheFromDisk(voiceCloneCache, VOICECLONE_CACHE_FILE);
        loadCacheFromDisk(simpleCache, SIMPLE_CACHE_FILE);
        log.info("TTS缓存已加载: VoiceClone={}, Simple={}", voiceCloneCache.size(), simpleCache.size());
    }

    /**
     * 查看所有缓存
     */
    @GetMapping("/cache/list")
    public Object listCache() {
        List<Map<String, Object>> voicecloneList = new ArrayList<>();
        for (var entry : voiceCloneCache.entrySet()) {
            CacheEntry e = entry.getValue();
            voicecloneList.add(Map.of(
                "key", entry.getKey(),
                "text", e.text != null ? e.text.substring(0, Math.min(50, e.text.length())) : "",
                "voiceType", "voiceclone",
                "source", e.source != null ? e.source : "unknown",
                "createdAt", e.createdAt
            ));
        }

        List<Map<String, Object>> simpleList = new ArrayList<>();
        for (var entry : simpleCache.entrySet()) {
            CacheEntry e = entry.getValue();
            simpleList.add(Map.of(
                "key", entry.getKey(),
                "text", e.text != null ? e.text.substring(0, Math.min(50, e.text.length())) : "",
                "voiceType", "simple",
                "source", e.source != null ? e.source : "unknown",
                "createdAt", e.createdAt
            ));
        }

        return Map.of(
            "voiceclone", voicecloneList,
            "simple", simpleList,
            "totalVoiceclone", voicecloneList.size(),
            "totalSimple", simpleList.size()
        );
    }

    /**
     * 删除指定缓存
     */
    @PostMapping("/cache/delete")
    public Object deleteCache(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String voiceType = body.get("voiceType");

        if (key == null || voiceType == null) {
            return Map.of("success", false, "message", "缺少参数");
        }

        boolean deleted = false;
        if ("voiceclone".equals(voiceType)) {
            deleted = voiceCloneCache.remove(key) != null;
            if (deleted) saveCacheToDisk(voiceCloneCache, VOICECLONE_CACHE_FILE);
        } else if ("simple".equals(voiceType)) {
            deleted = simpleCache.remove(key) != null;
            if (deleted) saveCacheToDisk(simpleCache, SIMPLE_CACHE_FILE);
        }

        return Map.of("success", deleted, "message", deleted ? "已删除" : "未找到");
    }

    /**
     * 清空所有缓存
     */
    @PostMapping("/cache/clear")
    public Object clearCache() {
        int voicecloneSize = voiceCloneCache.size();
        int simpleSize = simpleCache.size();
        voiceCloneCache.clear();
        simpleCache.clear();
        saveCacheToDisk(voiceCloneCache, VOICECLONE_CACHE_FILE);
        saveCacheToDisk(simpleCache, SIMPLE_CACHE_FILE);
        log.info("TTS缓存已清空: VoiceClone={}, Simple={}", voicecloneSize, simpleSize);
        return Map.of(
            "success", true,
            "message", "缓存已清空",
            "clearedVoiceClone", voicecloneSize,
            "clearedSimple", simpleSize
        );
    }

    /**
     * 保存演示模式可用的语音（外部调用）
     */
    @PostMapping("/cache/save-demo")
    public Object saveDemoVoice(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        String audio = body.get("audio");
        String voiceType = body.getOrDefault("voiceType", "simple");

        if (text == null || audio == null) {
            return Map.of("success", false, "message", "缺少参数");
        }

        String key = getSimpleCacheKey(text, null);
        CacheEntry entry = new CacheEntry(text, audio, voiceType, "demo");

        if ("voiceclone".equals(voiceType)) {
            voiceCloneCache.put(key, entry);
            saveCacheToDisk(voiceCloneCache, VOICECLONE_CACHE_FILE);
        } else {
            simpleCache.put(key, entry);
            saveCacheToDisk(simpleCache, SIMPLE_CACHE_FILE);
        }

        log.info("已保存演示语音: text={}, type={}", text.substring(0, Math.min(20, text.length())), voiceType);
        return Map.of("success", true, "message", "已保存");
    }

    // ===== 缓存操作 =====

    /**
     * 加载默认参考音频
     * 优先级：配置文件显式路径 > Docker容器内标准路径 > 本地开发相对路径 > Windows本地硬编码兜底
     */
    private String loadDefaultVoice() {
        if (cachedVoiceBase64 != null) return cachedVoiceBase64;

        String userDir = System.getProperty("user.dir");
        java.util.List<String> possiblePaths = new java.util.ArrayList<>();

        // 1. 配置文件显式指定（优先级最高，生产环境通过 application-prod.yml 或 TTS_REFERENCE_VOICE_PATH env 注入）
        if (referenceVoicePath != null && !referenceVoicePath.isBlank()) {
            possiblePaths.add(referenceVoicePath);
        }

        // 2. Docker / 生产环境标准路径
        possiblePaths.add("/app/data/reference.wav");
        possiblePaths.add("/app/config/reference.wav");
        possiblePaths.add(userDir + "/data/reference.wav");

        // 3. 本地开发相对路径（monorepo 结构）
        possiblePaths.add(userDir + "/../frontend/public/voice/reference.wav");
        possiblePaths.add(userDir + "/frontend/public/voice/reference.wav");

        // 4. Windows 本地绝对路径（本地开发环境兜底，不要让线上依赖此路径
        possiblePaths.add("F:/最新/frontend/public/voice/reference.wav");

        for (String pathStr : possiblePaths) {
            try {
                Path voicePath = Paths.get(pathStr);
                if (Files.exists(voicePath)) {
                    byte[] bytes = Files.readAllBytes(voicePath);
                    cachedVoiceBase64 = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(bytes);
                    log.info("已加载参考音频: path={}, bytes={}", pathStr, bytes.length);
                    return cachedVoiceBase64;
                }
            } catch (Exception e) {
                log.debug("加载参考音频失败 path={}: {}", pathStr, e.getMessage());
            }
        }

        // 最后兜底：从 classpath 读取 jar 包内置的 reference/reference.wav（构建时打进镜像）
        try (InputStream is = TtsController.class.getClassLoader().getResourceAsStream("reference/reference.wav")) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                cachedVoiceBase64 = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(bytes);
                log.info("已加载参考音频(classpath): bytes={}", bytes.length);
                return cachedVoiceBase64;
            }
        } catch (Exception e) {
            log.debug("classpath加载参考音频失败: {}", e.getMessage());
        }

        log.warn("未找到参考音频文件，将降级为simple模型");
        return "";
    }

    private String getVoiceCloneCacheKey(String text, String voiceBase64, String voiceInstruction) {
        String voiceHint = (voiceBase64 != null && !voiceBase64.isEmpty())
            ? voiceBase64.substring(0, Math.min(20, voiceBase64.length()))
            : "default";
        return text + "|" + voiceHint + "|" + (voiceInstruction != null ? voiceInstruction : "");
    }

    private String getSimpleCacheKey(String text, String voiceInstruction) {
        return text + "|" + (voiceInstruction != null ? voiceInstruction : "default");
    }

    private void loadCacheFromDisk(ConcurrentHashMap<String, CacheEntry> cache, String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) return;

            String json = Files.readString(path);
            Map<String, CacheEntry> loaded = JSON.parseObject(json, new com.alibaba.fastjson2.TypeReference<Map<String, CacheEntry>>() {});
            if (loaded != null) {
                cache.putAll(loaded);
            }
        } catch (Exception e) {
            log.warn("加载TTS缓存失败: {}", e.getMessage());
        }
    }

    private void saveCacheToDisk(ConcurrentHashMap<String, CacheEntry> cache, String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());

            String json = JSON.toJSONString(cache);
            Files.writeString(path, json);
        } catch (Throwable t) {
            // catch Throwable 而非 Exception：OutOfMemoryError 是 Error，不会被 catch(Exception) 捕获
            // 序列化含大量 base64 音频的缓存 Map 时可能 OOM，不能让缓存保存失败影响 TTS 请求本身
            log.warn("保存TTS缓存失败(缓存仍保留在内存中): {}", t.getClass().getSimpleName());
        }
    }

    // ===== TTS API调用 =====

    @PostMapping("/synthesize")
    public Object synthesize(@RequestBody TtsRequest request) {
        if (apiKey == null || apiKey.isEmpty()) {
            return Map.of("error", "TTS API key not configured");
        }
        String text = request.getText();
        if (text == null || text.isEmpty()) {
            return Map.of("error", "Text is required");
        }

        // 文本长度限制
        String trimmed = text.trim();
        final String finalText;
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            finalText = trimmed.substring(0, MAX_TEXT_LENGTH);
        } else {
            finalText = trimmed;
        }

        try {
            // ---- 无参考音频时，内部直接降级到 simple 模型，避免 MiMo 返回 400 再让前端降级（节省API调用和响应时间）
            boolean hasVoice = (request.getVoiceBase64() != null && !request.getVoiceBase64().isEmpty())
                || !loadDefaultVoice().isEmpty();
            if (!hasVoice) {
                log.info("[VoiceClone] 无参考音频，内部降级到 simple 模型");
                String audio = callWithRetry(() -> callMimoTtsSyncSimple(finalText, request.getVoiceInstruction()));
                String cacheKeySimple = getSimpleCacheKey(finalText, request.getVoiceInstruction());
                CacheEntry entrySimple = new CacheEntry(finalText, audio, "simple", "fallback");
                simpleCache.put(cacheKeySimple, entrySimple);
                saveCacheToDisk(simpleCache, SIMPLE_CACHE_FILE);
                return Map.of("audio", audio, "format", "pcm16", "sampleRate", 24000, "source", "api", "fallback", "simple");
            }

            // 检查缓存
            String cacheKey = getVoiceCloneCacheKey(finalText, request.getVoiceBase64(), request.getVoiceInstruction());
            CacheEntry cached = voiceCloneCache.get(cacheKey);
            if (cached != null) {
                log.info("[VoiceClone] 缓存命中, text={}...", finalText.substring(0, Math.min(20, finalText.length())));
                return Map.of("audio", cached.audio, "format", "pcm16", "sampleRate", 24000, "source", "cache");
            }

            // 调用 API
            String audio = callWithRetry(() -> callMimoTtsSync(finalText, request.getVoiceBase64(), request.getVoiceInstruction()));

            // 保存到缓存
            CacheEntry entry = new CacheEntry(finalText, audio, "voiceclone", "api");
            voiceCloneCache.put(cacheKey, entry);
            saveCacheToDisk(voiceCloneCache, VOICECLONE_CACHE_FILE);

            log.info("[VoiceClone] 成功, text={}..., audioLen={}", finalText.substring(0, Math.min(20, finalText.length())), audio.length());
            return Map.of("audio", audio, "format", "pcm16", "sampleRate", 24000, "source", "api");
        } catch (Exception e) {
            log.error("[VoiceClone] 失败: {}", e.getMessage());
            return Map.of("error", e.getMessage() != null ? e.getMessage() : "VoiceClone TTS failed");
        }
    }

    @PostMapping("/synthesize-simple")
    public Object synthesizeSimple(@RequestBody TtsRequest request) {
        if (apiKey == null || apiKey.isEmpty()) {
            return Map.of("error", "TTS API key not configured");
        }
        String text = request.getText();
        if (text == null || text.isEmpty()) {
            return Map.of("error", "Text is required");
        }

        String trimmed = text.trim();
        final String finalText;
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            finalText = trimmed.substring(0, MAX_TEXT_LENGTH);
        } else {
            finalText = trimmed;
        }

        try {
            // 检查缓存
            String cacheKey = getSimpleCacheKey(finalText, request.getVoiceInstruction());
            CacheEntry cached = simpleCache.get(cacheKey);
            if (cached != null) {
                log.info("[Simple] 缓存命中, text={}...", finalText.substring(0, Math.min(20, finalText.length())));
                return Map.of("audio", cached.audio, "format", "pcm16", "sampleRate", 24000, "source", "cache");
            }

            // 调用 API
            String audio = callWithRetry(() -> callMimoTtsSyncSimple(finalText, request.getVoiceInstruction()));

            // 保存到缓存
            CacheEntry entry = new CacheEntry(finalText, audio, "simple", "api");
            simpleCache.put(cacheKey, entry);
            saveCacheToDisk(simpleCache, SIMPLE_CACHE_FILE);

            log.info("[Simple] 成功, text={}..., audioLen={}", finalText.substring(0, Math.min(20, finalText.length())), audio.length());
            return Map.of("audio", audio, "format", "pcm16", "sampleRate", 24000, "source", "api");
        } catch (Exception e) {
            log.error("[Simple] 失败: {}", e.getMessage(), e);
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = "Simple TTS failed";
            }
            return Map.of("error", errorMsg);
        }
    }

    // ===== API调用方法 =====

    @FunctionalInterface
    interface TtsCall { String call() throws Exception; }

    private String callWithRetry(TtsCall call) throws Exception {
        for (int i = 0; i < 3; i++) {
            try {
                return call.call();
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("429") && i < 2) {
                    long delay = 1000L * (i + 1);
                    log.warn("TTS 429 rate limit, retry {}/2 after {}ms", i + 1, delay);
                    Thread.sleep(delay);
                    continue;
                }
                throw e;
            }
        }
        throw new RuntimeException("TTS failed after retries");
    }

    private String callMimoTtsSync(String text, String voiceBase64, String voiceInstruction) throws Exception {
        JSONObject body = buildTtsBody(text, voiceBase64, voiceInstruction);
        return callMimoApi(body, model);
    }

    private String callMimoTtsSyncSimple(String text, String voiceInstruction) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", modelSimple);
        body.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", (voiceInstruction != null && !voiceInstruction.isEmpty()) ? voiceInstruction : "自然清晰的中文朗读");
        messages.add(userMsg);
        JSONObject assistantMsg = new JSONObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", text);
        messages.add(assistantMsg);
        body.put("messages", messages);

        JSONObject audio = new JSONObject();
        audio.put("format", "pcm16");
        audio.put("voice", "mimo_default");
        body.put("audio", audio);

        return callMimoApi(body, modelSimple);
    }

    private JSONObject buildTtsBody(String text, String voiceBase64, String voiceInstruction) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", (voiceInstruction != null && !voiceInstruction.isEmpty()) ? voiceInstruction : "");
        messages.add(userMsg);
        JSONObject assistantMsg = new JSONObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", text);
        messages.add(assistantMsg);
        body.put("messages", messages);

        JSONObject audio = new JSONObject();
        audio.put("format", "pcm16");

        // 如果没有提供voiceBase64，加载默认的reference.wav
        String finalVoice = (voiceBase64 != null && !voiceBase64.isEmpty()) ? voiceBase64 : loadDefaultVoice();
        if (!finalVoice.isEmpty() && finalVoice.startsWith("data:")) {
            audio.put("voice", finalVoice);
        } else {
            log.warn("VoiceClone: 无有效DataURL参考音频，降级使用simple模型声音");
            audio.put("voice", "mimo_default");
            body.put("model", modelSimple);
        }
        body.put("audio", audio);

        return body;
    }

    private String callMimoApi(JSONObject body, String modelName) throws Exception {
        String url = baseUrl + "/chat/completions";
        String bodyStr = body.toJSONString();

        log.info("TTS request: model={}, textLen={}", modelName, bodyStr.length());

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyStr.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();

            // 读取响应：getErrorStream() 在无错误体时可能返回 null，需防御
            InputStream inputStream = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
            byte[] responseBytes = inputStream != null ? inputStream.readAllBytes() : new byte[0];

            // 尝试用UTF-8解析，如果失败则用ISO-8859-1（不丢数据）
            String responseBody;
            try {
                responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                // 验证是否为有效UTF-8
                if (!isValidUTF8(responseBody)) {
                    responseBody = new String(responseBytes, StandardCharsets.ISO_8859_1);
                }
            } catch (Exception e) {
                responseBody = new String(responseBytes, StandardCharsets.ISO_8859_1);
            }

            if (code != 200) {
                throw new RuntimeException("MiMo TTS API error " + code + ": " + responseBody);
            }

            // 解析JSON，处理可能的编码问题
            JSONObject json;
            try {
                json = JSON.parseObject(responseBody);
            } catch (Exception e) {
                // 如果JSON解析失败，尝试清理响应体
                log.warn("JSON解析失败，尝试清理响应体");
                String cleaned = responseBody.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
                json = JSON.parseObject(cleaned);
            }

            // 链式取值前逐级 null 检查，避免 NPE
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("MiMo TTS 响应缺少 choices 字段");
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            if (message == null) {
                throw new RuntimeException("MiMo TTS 响应缺少 message 字段");
            }
            JSONObject audioObj = message.getJSONObject("audio");
            if (audioObj == null) {
                throw new RuntimeException("No audio data in response");
            }
            return audioObj.getString("data");
        } finally {
            // 确保连接释放，避免连接泄露
            conn.disconnect();
        }
    }

    /**
     * 验证字符串是否为有效UTF-8
     */
    private boolean isValidUTF8(String str) {
        try {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            return new String(bytes, StandardCharsets.UTF_8).equals(str);
        } catch (Exception e) {
            return false;
        }
    }

    public static class TtsRequest {
        private String text;
        private String voiceBase64;
        private String voiceInstruction;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getVoiceBase64() { return voiceBase64; }
        public void setVoiceBase64(String voiceBase64) { this.voiceBase64 = voiceBase64; }
        public String getVoiceInstruction() { return voiceInstruction; }
        public void setVoiceInstruction(String voiceInstruction) { this.voiceInstruction = voiceInstruction; }
    }
}
