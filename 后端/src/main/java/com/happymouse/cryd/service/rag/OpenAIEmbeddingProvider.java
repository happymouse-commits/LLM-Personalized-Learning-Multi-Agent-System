package com.happymouse.cryd.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI Embedding提供商
 * 使用 text-embedding-3-small 或 text-embedding-3-large
 */
public class OpenAIEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIEmbeddingProvider.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimension;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public OpenAIEmbeddingProvider(String apiKey, String baseUrl, String model, int dimension) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.model = model != null ? model : "text-embedding-3-small";
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[dimension];
        }

        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("input", text);

            JSONObject response = callApi(body);
            JSONArray data = response.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONArray embedding = data.getJSONObject(0).getJSONArray("embedding");
                return toFloatArray(embedding);
            }
        } catch (Exception e) {
            log.warn("OpenAI Embedding调用失败: error={}", e.getMessage());
        }
        return new float[dimension];
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("input", texts);

            JSONObject response = callApi(body);
            JSONArray data = response.getJSONArray("data");
            if (data != null) {
                List<float[]> results = new ArrayList<>();
                for (int i = 0; i < data.size(); i++) {
                    JSONArray embedding = data.getJSONObject(i).getJSONArray("embedding");
                    results.add(toFloatArray(embedding));
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("OpenAI Batch Embedding调用失败: error={}", e.getMessage());
        }
        return texts.stream().map(t -> new float[dimension]).toList();
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    private JSONObject callApi(JSONObject body) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/embeddings")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("OpenAI API错误[" + response.code() + "]: " + respBody);
            }
            return JSON.parseObject(respBody);
        }
    }

    private float[] toFloatArray(JSONArray jsonArray) {
        float[] result = new float[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = jsonArray.getFloatValue(i);
        }
        return result;
    }
}
