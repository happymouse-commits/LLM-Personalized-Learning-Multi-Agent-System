package com.happymouse.cryd.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * 本地哈希向量化提供商 — 基于 n-gram 哈希投影
 * 无需外部API，作为fallback使用
 */
public class LocalHashEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalHashEmbeddingProvider.class);

    private final int vectorDimension;
    private static final int NGRAM_MIN = 2;
    private static final int NGRAM_MAX = 5;

    public LocalHashEmbeddingProvider(int vectorDimension) {
        this.vectorDimension = vectorDimension;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[vectorDimension];
        }

        float[] vector = new float[vectorDimension];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            Set<String> seen = new HashSet<>();

            for (int n = NGRAM_MIN; n <= NGRAM_MAX; n++) {
                for (int i = 0; i <= text.length() - n; i++) {
                    String ngram = text.substring(i, i + n);
                    if (seen.add(ngram)) {
                        byte[] hash = md.digest(ngram.getBytes(StandardCharsets.UTF_8));
                        for (int j = 0; j < vectorDimension; j++) {
                            int byteIdx = j % hash.length;
                            float val = ((hash[byteIdx] & 0xFF) - 127.5f) / 127.5f;
                            vector[j] += val;
                        }
                    }
                }
            }

            // L2 normalize
            double norm = 0;
            for (float v : vector) norm += v * v;
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) (vector[i] / norm);
                }
            }
        } catch (Exception e) {
            log.warn("向量化失败: text_len={}, error={}", text.length(), e.getMessage());
        }
        return vector;
    }

    @Override
    public int getDimension() {
        return vectorDimension;
    }

    @Override
    public String getProviderName() {
        return "local-hash";
    }
}
