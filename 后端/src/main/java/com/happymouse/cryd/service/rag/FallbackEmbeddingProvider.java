package com.happymouse.cryd.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 带本地兜底的Embedding提供商
 * 优先使用主提供商，主提供商失败时自动降级到本地哈希
 * 降级后以1%概率探测恢复
 */
public class FallbackEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackEmbeddingProvider.class);

    private final EmbeddingProvider primary;
    private final EmbeddingProvider fallback;
    private volatile boolean primaryHealthy = true;

    public FallbackEmbeddingProvider(EmbeddingProvider primary, EmbeddingProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public float[] embed(String text) {
        // 降级后以1%概率探测主提供商是否恢复
        if (!primaryHealthy && ThreadLocalRandom.current().nextInt(100) == 0) {
            try {
                float[] test = primary.embed(text);
                if (test != null && test.length > 0 && !isZeroVector(test)) {
                    log.info("主Embedding提供商已恢复，切回主提供商");
                    primaryHealthy = true;
                    return test;
                }
            } catch (Exception ignored) {}
        }
        if (primaryHealthy) {
            try {
                float[] result = primary.embed(text);
                if (result != null && result.length > 0 && !isZeroVector(result)) {
                    return result;
                }
                log.warn("主提供商返回空向量，降级到本地兜底");
                primaryHealthy = false;
            } catch (Exception e) {
                log.warn("主提供商调用失败，降级到本地兜底: {}", e.getMessage());
                primaryHealthy = false;
            }
        }
        return fallback.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (primaryHealthy) {
            try {
                List<float[]> results = primary.embedBatch(texts);
                if (results != null && !results.isEmpty() && !isZeroVector(results.get(0))) {
                    return results;
                }
                log.warn("主提供商批量调用返回空，降级到本地兜底");
                primaryHealthy = false;
            } catch (Exception e) {
                log.warn("主提供商批量调用失败，降级到本地兜底: {}", e.getMessage());
                primaryHealthy = false;
            }
        }
        return fallback.embedBatch(texts);
    }

    @Override
    public int getDimension() {
        return primary.getDimension();
    }

    @Override
    public String getProviderName() {
        return primary.getProviderName() + "+" + fallback.getProviderName();
    }

    private boolean isZeroVector(float[] vec) {
        if (vec == null) return true;
        for (float v : vec) {
            if (v != 0) return false;
        }
        return true;
    }
}
