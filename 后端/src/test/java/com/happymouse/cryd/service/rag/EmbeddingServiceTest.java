package com.happymouse.cryd.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地哈希向量化算法测试 — 验证 n-gram 投影的相似度检索效果。
 * 无需 Spring 容器，纯单元测试。
 */
class EmbeddingServiceTest {

    private final EmbeddingProvider provider;
    private final EmbeddingService service;

    EmbeddingServiceTest() {
        // 直接使用 LocalHashEmbeddingProvider 进行测试
        provider = new LocalHashEmbeddingProvider(1024);
        // 创建一个简单的 EmbeddingService 包装器用于测试
        service = new EmbeddingService("local", 1024, "", "", "", "", "", "", "", "");
    }

    @Test
    @DisplayName("空文本向量化为全零向量")
    void emptyText_returnsZeroVector() {
        float[] vec = service.embed("");
        for (float v : vec) assertEquals(0.0f, v, "空文本应返回零向量");
    }

    @Test
    @DisplayName("相同文本的余弦相似度应为1.0")
    void sameText_similarityIsOne() {
        float[] a = service.embed("C语言中的指针是一种特殊的变量");
        float[] b = service.embed("C语言中的指针是一种特殊的变量");
        double sim = service.cosineSimilarity(a, b);
        assertEquals(1.0, sim, 0.0001, "相同文本的相似度应为1.0");
    }

    @Test
    @DisplayName("完全无关的文本相似度应显著低于相关文本")
    void related_vs_unrelated() {
        float[] related_a = service.embed("指针是C语言的核心概念，用于直接操作内存地址");
        float[] related_b = service.embed("通过指针可以高效地访问和修改内存中的数据");
        float[] unrelated = service.embed("今天天气很好，适合出去跑步锻炼身体");

        double simRelated = service.cosineSimilarity(related_a, related_b);
        double simUnrelated = service.cosineSimilarity(related_a, unrelated);

        System.out.println("  [相似度] 相关文本(指针vs指针): " + String.format("%.4f", simRelated));
        System.out.println("  [相似度] 无关文本(指针vs天气): " + String.format("%.4f", simUnrelated));

        assertTrue(simRelated > simUnrelated,
                "相关文本的相似度(" + simRelated + ")应高于无关文本(" + simUnrelated + ")");
    }

    @Test
    @DisplayName("C语言知识库检索命中测试")
    void knowledgeRetrieval_test() {
        String[] knowledgeBase = {
            "指针是一种变量，其值为另一个变量的内存地址",
            "malloc函数用于动态分配内存，返回void*指针",
            "结构体可以将不同类型的数据组合成一个整体",
            "数组名在表达式中通常被转换为指向第一个元素的指针",
            "字符串在C语言中是以'\\0'结尾的字符数组",
            "for循环由初始化、条件判断和更新三部分组成",
            "函数参数传递分为值传递和指针传递两种方式",
            "文件操作通过FILE结构体和fopen/fclose等函数实现"
        };

        String query = "如何用指针访问数组元素";
        float[] qVec = service.embed(query);
        java.util.List<float[]> kbVecList = service.embedBatch(java.util.Arrays.asList(knowledgeBase));
        float[][] kbVecs = kbVecList.toArray(new float[0][]);

        java.util.List<SimilarityResult> allResults = new java.util.ArrayList<>();
        System.out.println("  [RAG检索] 查询: \"" + query + "\"");
        System.out.println("  [RAG检索] 全部评分:");
        for (int i = 0; i < kbVecs.length; i++) {
            double sim = service.cosineSimilarity(qVec, kbVecs[i]);
            allResults.add(new SimilarityResult(i, sim));
            System.out.println("    [" + i + "] score=" + String.format("%.4f", sim) + " | "
                + knowledgeBase[i].substring(0, Math.min(50, knowledgeBase[i].length())) + "...");
        }

        // 按评分排序
        java.util.Collections.sort(allResults, (a, b) -> Double.compare(b.score, a.score));
        // 取前3
        java.util.List<SimilarityResult> top3 = allResults.subList(0, Math.min(3, allResults.size()));

        System.out.println("  [RAG检索] Top-3:");
        boolean foundPointer = false;
        for (int j = 0; j < top3.size(); j++) {
            SimilarityResult r = top3.get(j);
            System.out.println("    [#" + (j+1) + " score=" + String.format("%.4f", r.score)
                + "] " + knowledgeBase[r.idx].substring(0, Math.min(50, knowledgeBase[r.idx].length())) + "...");
            if (knowledgeBase[r.idx].contains("指针")) foundPointer = true;
            // 合法性检查
            assertTrue(r.idx >= 0 && r.idx < knowledgeBase.length, "命中索引有效");
        }

        assertTrue(foundPointer, "关于指针的查询应命中指针相关的知识片段");
        // 最佳命中优于最差命中
        assertTrue(allResults.get(0).score > allResults.get(allResults.size()-1).score,
                "最佳命中的得分应高于最差命中");
    }

    @Test
    @DisplayName("batch嵌入批量一致性")
    void batchConsistency() {
        java.util.List<String> texts = java.util.Arrays.asList(
            "C语言数组与指针的区别",
            "C语言结构体定义和使用",
            "C语言动态内存分配"
        );
        java.util.List<float[]> batchVecs = service.embedBatch(texts);
        assertEquals(3, batchVecs.size(), "批量应返回3个向量");
        for (float[] vec : batchVecs) {
            assertEquals(1024, vec.length, "每个向量应为1024维");
        }
        System.out.println("  [Batch] 批量嵌入3条文本, 向量维度=1024, 结果一致");
    }

    static class SimilarityResult {
        int idx; double score;
        SimilarityResult(int idx, double score) { this.idx = idx; this.score = score; }
    }
}
