package com.happymouse.cryd.service.rag;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储 - 支持余弦相似度检索 + 磁盘持久化
 */
@Service
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);
    private static final String STORE_FILE = "data/vector_store.dat";
    private int currentDimension = -1;

    private final Map<String, VectorEntry> store = new ConcurrentHashMap<>();
    private final EmbeddingService embeddingService;

    public VectorStore(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        currentDimension = embeddingService.getDimension();
        loadFromDisk();

        // 清除维度不兼容的旧向量
        // 始终以当前配置维度为基准，删除与配置维度不一致的旧向量
        int referenceDim = currentDimension;

        List<String> toRemove = new ArrayList<>();
        Map<Integer, Integer> dimStats = new HashMap<>();
        for (Map.Entry<String, VectorEntry> entry : store.entrySet()) {
            int dim = entry.getValue().vector.length;
            dimStats.merge(dim, 1, Integer::sum);
            if (dim != referenceDim) {
                toRemove.add(entry.getKey());
            }
        }
        if (!toRemove.isEmpty()) {
            log.warn("清除{}条维度不兼容的旧向量（参考维度:{}, 配置维度:{}, 实际分布:{})", toRemove.size(), referenceDim, currentDimension, dimStats);
            toRemove.forEach(store::remove);
            // 立即持久化以避免下次启动又加载旧数据
            saveToDisk();
        }
        log.info("VectorStore初始化完成，当前条目数: {}，参考维度:{}", store.size(), referenceDim);
    }

    @PreDestroy
    public void shutdown() {
        saveToDisk();
        log.info("VectorStore已持久化");
    }

    /**
     * 添加向量（不做持久化，批量导入后由 KnowledgeImporter 统一保存）
     */
    public void add(String id, float[] vector, Map<String, String> metadata) {
        store.put(id, new VectorEntry(vector, metadata));
    }

    /**
     * 立即持久化到磁盘（批量导入完成后调用）
     */
    public void flush() {
        saveToDisk();
    }

    /**
     * 检查ID是否已存在
     */
    public boolean contains(String id) {
        return store.containsKey(id);
    }

    /**
     * 清空向量库
     */
    public void clear() {
        store.clear();
        log.info("向量库已清空");
    }

    /**
     * 添加文本(自动向量化)
     */
    public String addText(String text, Map<String, String> metadata) {
        String id = UUID.randomUUID().toString().substring(0, 12);
        float[] vector = embeddingService.embed(text);
        add(id, vector, metadata);
        return id;
    }

    /**
     * 余弦相似度检索
     */
    public List<SearchResult> search(float[] queryVector, int topK, double minSimilarity) {
        PriorityQueue<SearchResult> heap = new PriorityQueue<>(
            Comparator.comparingDouble(SearchResult::getSimilarity).reversed());

        for (Map.Entry<String, VectorEntry> entry : store.entrySet()) {
            double sim = embeddingService.cosineSimilarity(queryVector, entry.getValue().vector);
            if (sim >= minSimilarity) {
                heap.offer(new SearchResult(entry.getKey(), sim, entry.getValue().metadata));
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < topK && !heap.isEmpty(); i++) {
            results.add(heap.poll());
        }
        return results;
    }

    /**
     * 文本检索(自动向量化查询)
     */
    public List<SearchResult> search(String query, int topK, double minSimilarity) {
        float[] queryVec = embeddingService.embed(query);
        return search(queryVec, topK, minSimilarity);
    }

    public void remove(String id) { store.remove(id); }
    public int size() { return store.size(); }

    // --- 持久化 ---
    public synchronized void saveToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(STORE_FILE))) {
            oos.writeObject(new HashMap<>(store));
            log.info("VectorStore已保存: {}条", store.size());
        } catch (Exception e) {
            log.warn("VectorStore保存失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadFromDisk() {
        File file = new File(STORE_FILE);
        if (!file.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, VectorEntry> saved = (Map<String, VectorEntry>) ois.readObject();
            store.putAll(saved);
            log.info("VectorStore已加载: {}条", saved.size());
        } catch (Exception e) {
            log.warn("VectorStore加载失败: {}", e.getMessage());
        }
    }

    /**
     * 搜索结果
     */
    public static class SearchResult {
        private final String id;
        private final double similarity;
        private final Map<String, String> metadata;

        public SearchResult(String id, double similarity, Map<String, String> metadata) {
            this.id = id;
            this.similarity = similarity;
            this.metadata = metadata;
        }
        public String getId() { return id; }
        public double getSimilarity() { return similarity; }
        public Map<String, String> getMetadata() { return metadata; }
    }

    /**
     * 向量条目(支持序列化)
     */
    private static class VectorEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        final float[] vector;
        final Map<String, String> metadata;

        VectorEntry(float[] vector, Map<String, String> metadata) {
            this.vector = vector;
            this.metadata = new HashMap<>(metadata);
        }
    }
}
