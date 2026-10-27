package com.happymouse.cryd.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞星火Embedding提供商
 * 通过本地Node.js HTTP服务调用
 */
public class SparkEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(SparkEmbeddingProvider.class);

    private final int dimension;
    private final String embedUrl;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public SparkEmbeddingProvider(String appId, String apiKey, String apiSecret,
                                   String baseUrl, String model, int dimension) {
        this.dimension = dimension;
        // 使用配置的baseUrl，未配置则默认本地代理
        if (baseUrl != null && !baseUrl.isEmpty()) {
            this.embedUrl = baseUrl;
        } else {
            this.embedUrl = "http://127.0.0.1:9876/embed";
        }
        log.info("SparkEmbeddingProvider初始化: url={}, appId={}", embedUrl, appId);
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[dimension];
        }

        try {
            JSONObject body = new JSONObject();
            body.put("text", text);

            Request request = new Request.Builder()
                    .url(embedUrl)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    JSONObject json = JSON.parseObject(respBody);
                    String vector = json.getString("vector");
                    if (vector != null && !vector.isEmpty()) {
                        return decodeEmbedding(vector);
                    }
                }
                log.warn("星火Embedding失败: code={}, body={}", response.code(), respBody);
            }
        } catch (Exception e) {
            log.error("星火Embedding调用失败: {}", e.getMessage());
        }
        return new float[dimension];
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public int getDimension() { return dimension; }

    @Override
    public String getProviderName() { return "spark"; }

    private float[] decodeEmbedding(String base64Data) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Data);
            float[] vector = new float[decoded.length / 4];
            for (int i = 0; i < vector.length; i++) {
                int bits = (decoded[i * 4] & 0xFF)
                        | ((decoded[i * 4 + 1] & 0xFF) << 8)
                        | ((decoded[i * 4 + 2] & 0xFF) << 16)
                        | ((decoded[i * 4 + 3] & 0xFF) << 24);
                vector[i] = Float.intBitsToFloat(bits);
            }
            return vector;
        } catch (Exception e) {
            log.error("星火Embedding向量解码失败: {}", e.getMessage());
            return new float[dimension];
        }
    }
}
