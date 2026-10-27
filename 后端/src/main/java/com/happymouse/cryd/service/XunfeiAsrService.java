package com.happymouse.cryd.service;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞语音听写服务 — WebSocket实时语音转文字
 */
@Service
public class XunfeiAsrService {

    private static final Logger log = LoggerFactory.getLogger(XunfeiAsrService.class);
    private static final String HOST = "iat-api.xfyun.cn";
    private static final String PATH = "/v2/iat";

    // 密钥仅通过环境变量/配置文件注入，禁止硬编码默认值（启动时未配置将快速失败）
    @Value("${xfyun.appid}")
    private String appid;

    @Value("${xfyun.api-key}")
    private String apiKey;

    @Value("${xfyun.api-secret}")
    private String apiSecret;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 语音转文字
     * @param audioBase64 Base64编码的音频数据（PCM格式，16kHz，16bit，单声道）
     * @return 识别出的文字
     */
    public String recognize(String audioBase64) {
        try {
            byte[] audioData = Base64.getDecoder().decode(audioBase64);
            return recognize(audioData);
        } catch (Exception e) {
            log.error("讯飞语音识别失败", e);
            return "";
        }
    }

    /**
     * 语音转文字
     * @param audioData 音频原始字节（PCM格式，16kHz，16bit，单声道）
     * @return 识别出的文字
     */
    public String recognize(byte[] audioData) {
        String url = buildAuthUrl();
        CountDownLatch latch = new CountDownLatch(1);
        // wpgs 动态修正模式：用 sn -> 文本片段 的有序映射，支持 pgs=rpl 替换历史片段
        java.util.TreeMap<Integer, String> snTextMap = new java.util.TreeMap<>();
        // 用数组让匿名内部类能写入最终识别结果（lambda/匿名类只能捕获 effectively final）
        final String[] recognizedText = {""};

        WebSocket ws = httpClient.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("[讯飞ASR] WebSocket连接成功");
                // 分帧发送音频（每帧3200字节，约100ms）
                int frameSize = 3200;
                for (int i = 0; i < audioData.length; i += frameSize) {
                    int end = Math.min(i + frameSize, audioData.length);
                    byte[] frame = new byte[end - i];
                    System.arraycopy(audioData, i, frame, 0, frame.length);

                    boolean isLast = (i + frameSize >= audioData.length);
                    String msg = buildFrameJson(frame, isLast, i == 0);
                    webSocket.send(msg);
                }
                // 发送结束帧
                if (audioData.length == 0) {
                    webSocket.send("{\"data\":{\"status\":2}}");
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(text);
                    // 打印原始响应便于诊断（截断避免刷屏）
                    log.info("[讯飞ASR] 响应: {}", text.length() > 500 ? text.substring(0, 500) + "..." : text);

                    // 检查讯飞顶层错误码（code != 0 表示服务端报错，如鉴权失败/余额不足/参数错误）
                    int code = json.getIntValue("code");
                    if (code != 0) {
                        String message = json.getString("message");
                        log.error("[讯飞ASR] 服务端错误: code={}, message={}, sid={}", code, message, json.getString("sid"));
                        webSocket.close(1000, "error");
                        latch.countDown();
                        return;
                    }

                    com.alibaba.fastjson2.JSONObject data = json.getJSONObject("data");
                    int status = data != null ? data.getIntValue("status") : -1;

                    // result 嵌套在 data 内（非顶层），旧代码误用 json.getString("result") 导致永远为 null
                    com.alibaba.fastjson2.JSONObject resultObj = data != null ? data.getJSONObject("result") : null;
                    if (resultObj != null) {
                        int sn = resultObj.getIntValue("sn");
                        String pgs = resultObj.getString("pgs");
                        // wpgs 动态修正：pgs=rpl 时按 rg 范围删除历史片段，再写入当前 sn
                        if ("rpl".equals(pgs)) {
                            com.alibaba.fastjson2.JSONArray rg = resultObj.getJSONArray("rg");
                            if (rg != null && rg.size() >= 2) {
                                int rgStart = rg.getIntValue(0);
                                int rgEnd = rg.getIntValue(1);
                                for (int r = rgStart; r <= rgEnd; r++) {
                                    snTextMap.remove(r);
                                }
                            }
                        }
                        // 提取当前片段的所有字
                        StringBuilder snippet = new StringBuilder();
                        com.alibaba.fastjson2.JSONArray wsArray = resultObj.getJSONArray("ws");
                        if (wsArray != null) {
                            for (int i = 0; i < wsArray.size(); i++) {
                                com.alibaba.fastjson2.JSONArray cwArray = wsArray.getJSONObject(i).getJSONArray("cw");
                                if (cwArray != null) {
                                    for (int j = 0; j < cwArray.size(); j++) {
                                        snippet.append(cwArray.getJSONObject(j).getString("w"));
                                    }
                                }
                            }
                        }
                        snTextMap.put(sn, snippet.toString());
                    }

                    if (status == 2) {
                        StringBuilder finalResult = new StringBuilder();
                        snTextMap.values().forEach(finalResult::append);
                        log.info("[讯飞ASR] 识别完成: \"{}\"", finalResult);
                        // 将最终结果写入外层 result（通过临时变量传递）
                        recognizedText[0] = finalResult.toString();
                        webSocket.close(1000, "done");
                        latch.countDown();
                    }
                } catch (Exception e) {
                    log.warn("[讯飞ASR] 解析响应失败: {}", e.getMessage());
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("[讯飞ASR] WebSocket错误: {}", t.getMessage());
                latch.countDown();
            }
        });

        try {
            // 超时从 30s 缩短到 10s：正常识别 2-3 秒内完成，超时通常意味着服务端无响应
            if (!latch.await(10, TimeUnit.SECONDS)) {
                StringBuilder partial = new StringBuilder();
                snTextMap.values().forEach(partial::append);
                log.warn("[讯飞ASR] 识别超时(10s)，返回已识别部分: \"{}\"", partial);
                return partial.toString();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return recognizedText[0];
    }

    /**
     * 构建鉴权URL
     */
    private String buildAuthUrl() {
        try {
            // 讯飞鉴权要求 date 为 RFC1123 格式的 GMT 时间（如 "Wed, 30 Jul 2026 09:50:23 GMT"）
            // 不能用 new Date().toString()（本地时间带时区），否则签名校验返回 403
            String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC));

            // 拼接签名原文
            String signatureOrigin = "host: " + HOST + "\n"
                    + "date: " + date + "\n"
                    + "GET " + PATH + " HTTP/1.1";

            // HMAC-SHA256签名
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(signatureBytes);

            // 构建authorization
            String authorizationOrigin = String.format(
                    "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                    apiKey, signature);
            String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

            return "wss://" + HOST + PATH + "?authorization=" + authorization
                    + "&date=" + java.net.URLEncoder.encode(date, "UTF-8")
                    + "&host=" + HOST;

        } catch (Exception e) {
            throw new RuntimeException("构建讯飞鉴权URL失败", e);
        }
    }

    /**
     * 构建音频帧JSON
     */
    private String buildFrameJson(byte[] audioFrame, boolean isLast, boolean isFirst) {
        String audioBase64 = Base64.getEncoder().encodeToString(audioFrame);
        com.alibaba.fastjson2.JSONObject frame = new com.alibaba.fastjson2.JSONObject();
        com.alibaba.fastjson2.JSONObject audio = new com.alibaba.fastjson2.JSONObject();

        audio.put("status", isFirst ? 0 : (isLast ? 2 : 1));
        audio.put("format", "audio/L16;rate=16000");
        audio.put("audio", audioBase64);

        if (isFirst) {
            com.alibaba.fastjson2.JSONObject common = new com.alibaba.fastjson2.JSONObject();
            common.put("app_id", appid);
            frame.put("common", common);

            com.alibaba.fastjson2.JSONObject business = new com.alibaba.fastjson2.JSONObject();
            business.put("language", "zh_cn");
            business.put("domain", "iat");
            business.put("accent", "mandarin");
            business.put("vad_eos", 3000);
            business.put("dwa", "wpgs");
            frame.put("business", business);
        }

        frame.put("data", audio);
        return frame.toJSONString();
    }
}
