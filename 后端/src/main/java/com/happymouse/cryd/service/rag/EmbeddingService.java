package com.happymouse.cryd.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Embedding服务 — 路由层
 * 根据配置选择不同的Embedding提供商（本地/星火）
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingProvider provider;

    public EmbeddingService(
            @Value("${rag.embedding.provider:local}") String providerName,
            @Value("${rag.embedding.dimension:1024}") int dimension,
            @Value("${rag.embedding.openai.api-key:}") String openaiApiKey,
            @Value("${rag.embedding.openai.base-url:}") String openaiBaseUrl,
            @Value("${rag.embedding.openai.model:text-embedding-3-small}") String openaiModel,
            @Value("${rag.embedding.spark.app-id:}") String sparkAppId,
            @Value("${rag.embedding.spark.api-key:}") String sparkApiKey,
            @Value("${rag.embedding.spark.api-secret:}") String sparkApiSecret,
            @Value("${rag.embedding.spark.url:}") String sparkUrl,
            @Value("${rag.embedding.spark.model:embedding-v1}") String sparkModel) {

        this.provider = switch (providerName.toLowerCase()) {
            case "openai" -> {
                log.info("使用OpenAI Embedding提供商: model={}, 兜底=本地哈希", openaiModel);
                OpenAIEmbeddingProvider openaiProvider = new OpenAIEmbeddingProvider(openaiApiKey, openaiBaseUrl, openaiModel, dimension);
                LocalHashEmbeddingProvider localFallback = new LocalHashEmbeddingProvider(dimension);
                yield new FallbackEmbeddingProvider(openaiProvider, localFallback);
            }
            case "spark" -> {
                log.info("使用星火Embedding提供商: model={}, appId={}, 兜底=本地哈希", sparkModel, sparkAppId);
                SparkEmbeddingProvider sparkProvider = new SparkEmbeddingProvider(sparkAppId, sparkApiKey, sparkApiSecret, sparkUrl, sparkModel, dimension);
                LocalHashEmbeddingProvider localFallback = new LocalHashEmbeddingProvider(dimension);
                yield new FallbackEmbeddingProvider(sparkProvider, localFallback);
            }
            default -> {
                if (!"local".equals(providerName.toLowerCase())) {
                    log.warn("未实现的Embedding提供商: '{}'，降级为本地哈希", providerName);
                }
                log.info("使用本地哈希Embedding提供商: dimension={}", dimension);
                yield new LocalHashEmbeddingProvider(dimension);
            }
        };

        log.info("EmbeddingService初始化完成: provider={}, dimension={}", provider.getProviderName(), provider.getDimension());
    }

    /**
     * 将文本转为语义向量
     */
    public float[] embed(String text) {
        return provider.embed(text);
    }

    /**
     * 批量嵌入
     */
    public List<float[]> embedBatch(List<String> texts) {
        return provider.embedBatch(texts);
    }

    /**
     * 计算余弦相似度
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 获取向量维度
     */
    public int getDimension() {
        return provider.getDimension();
    }

    /**
     * 获取当前提供商名称
     */
    public String getProviderName() {
        return provider.getProviderName();
    }
}
