package com.happymouse.cryd.service.rag;

import java.util.List;

/**
 * Embedding提供商接口
 * 支持多种实现：本地哈希、星火等
 */
public interface EmbeddingProvider {

    /**
     * 将文本转为语义向量
     *
     * @param text 输入文本
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量向量化
     *
     * @param texts 输入文本列表
     * @return 向量列表
     */
    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /**
     * 获取向量维度
     *
     * @return 维度值
     */
    int getDimension();

    /**
     * 获取提供商名称
     *
     * @return 名称
     */
    default String getProviderName() {
        return this.getClass().getSimpleName();
    }
}
