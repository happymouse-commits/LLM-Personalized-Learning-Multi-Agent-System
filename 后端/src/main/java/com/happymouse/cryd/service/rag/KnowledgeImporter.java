package com.happymouse.cryd.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 知识库导入服务 — 读取C语言知识库JSON，向量化后灌入VectorStore
 * 支持启动时自动导入（rag.knowledge-import.enabled=true）
 */
@Service
public class KnowledgeImporter {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeImporter.class);

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    @Value("${rag.knowledge-import.enabled:false}")
    private boolean importEnabled;

    @Value("${rag.knowledge-import.base-path:}")
    private String basePath;

    @Value("${rag.knowledge-import.auto-start:true}")
    private boolean autoStart;

    @Value("${rag.knowledge-import.force-reimport:false}")
    private boolean forceReimport;

    private boolean imported = false;

    public KnowledgeImporter(VectorStore vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    /**
     * 启动时自动导入知识库
     */
    @PostConstruct
    public void autoImport() {
        if (!importEnabled || !autoStart) {
            log.info("知识库自动导入已关闭 (enabled={}, autoStart={})", importEnabled, autoStart);
            return;
        }
        try {
            if (forceReimport) {
                log.info("强制重新导入模式：清除现有向量库数据");
                vectorStore.clear();
            }
            importAll();
        } catch (Exception e) {
            log.error("知识库自动导入失败: {}", e.getMessage());
        }
    }

    /**
     * 导入全部章节JSON
     */
    public ImportResult importAll() throws IOException {
        if (basePath == null || basePath.isEmpty()) {
            throw new IllegalStateException("知识库路径未配置: rag.knowledge-import.base-path");
        }

        Path dir = Paths.get(basePath);
        if (!Files.isDirectory(dir)) {
            throw new IOException("知识库目录不存在: " + basePath);
        }

        log.info("开始导入知识库: path={}", basePath);
        long startTime = System.currentTimeMillis();

        int totalKP = 0, totalEx = 0, totalMistakes = 0;

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jsonFiles = files
                .filter(p -> p.toString().endsWith(".json"))
                .sorted()
                .toList();

            for (Path file : jsonFiles) {
                ChapterImportResult result = importChapter(file);
                totalKP += result.knowledgePoints;
                totalEx += result.exercises;
                totalMistakes += result.mistakes;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        imported = true;

        // 立即持久化，避免进程异常退出导致丢失
        vectorStore.flush();

        log.info("知识库导入完成: {}知识点, {}习题, {}易错点, 耗时{}ms, 向量库总量{}条",
            totalKP, totalEx, totalMistakes, elapsed, vectorStore.size());

        return new ImportResult(totalKP, totalEx, totalMistakes, elapsed);
    }

    /**
     * 导入单个章节JSON
     */
    private int mistakeCounter = 0;  // 全局错误计数器，确保ID唯一

    private ChapterImportResult importChapter(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        JSONObject chapter = JSON.parseObject(content);

        String chapterId = chapter.getString("chapter_id");
        String chapterName = chapter.getString("chapter_name");
        int chapterOrder = chapter.getIntValue("chapter_order");

        log.info("导入章节: {} - {} (第{}章)", chapterId, chapterName, chapterOrder);

        int kpCount = 0, exCount = 0, mistakeCount = 0;

        // 导入知识点
        JSONArray knowledgePoints = chapter.getJSONArray("knowledge_points");
        if (knowledgePoints != null) {
            for (int i = 0; i < knowledgePoints.size(); i++) {
                JSONObject kp = knowledgePoints.getJSONObject(i);
                if (importKnowledgePoint(kp, chapterId, chapterName, chapterOrder)) kpCount++;
            }
        }

        // 导入习题
        JSONArray exercises = chapter.getJSONArray("exercises");
        if (exercises != null) {
            for (int i = 0; i < exercises.size(); i++) {
                JSONObject ex = exercises.getJSONObject(i);
                if (importExercise(ex, chapterId, chapterName, chapterOrder)) exCount++;
            }
        }

        // 导入常见错误
        JSONArray mistakes = chapter.getJSONArray("common_mistakes");
        if (mistakes != null) {
            for (int i = 0; i < mistakes.size(); i++) {
                JSONObject m = mistakes.getJSONObject(i);
                if (importMistake(m, chapterId, chapterName, chapterOrder)) mistakeCount++;
            }
        }

        log.info("章节导入完成: {} - {}个知识点, {}习题, {}易错点",
            chapterId, kpCount, exCount, mistakeCount);

        return new ChapterImportResult(kpCount, exCount, mistakeCount);
    }

    /**
     * 向量化并存储知识点
     */
    private boolean importKnowledgePoint(JSONObject kp, String chapterId, String chapterName, int chapterOrder) {
        String kpId = kp.getString("kp_id");
        if (vectorStore.contains(kpId)) return false;  // 已存在则跳过，避免重复调用嵌入API
        String title = kp.getString("title");
        String kpContent = kp.getString("content");
        String difficulty = kp.getString("difficulty");
        JSONArray keywords = kp.getJSONArray("keywords");
        String codeExample = kp.getString("code_example");

        // 构建向量化文本（知识点核心内容 + 标题 + 关键词）
        StringBuilder textForEmbedding = new StringBuilder();
        textForEmbedding.append(title).append("。");
        textForEmbedding.append(kpContent);
        if (keywords != null && !keywords.isEmpty()) {
            List<String> kwList = new ArrayList<>();
            for (int j = 0; j < keywords.size(); j++) {
                kwList.add(keywords.getString(j));
            }
            textForEmbedding.append(" 关键词：").append(String.join("、", kwList));
        }

        // 元数据
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "knowledge_point");
        metadata.put("kp_id", kpId);
        metadata.put("chapter_id", chapterId);
        metadata.put("chapter_name", chapterName);
        metadata.put("chapter_order", String.valueOf(chapterOrder));
        metadata.put("title", title);
        metadata.put("content", kpContent);
        metadata.put("difficulty", difficulty);
        if (keywords != null) {
            List<String> kwList = new ArrayList<>();
            for (int j = 0; j < keywords.size(); j++) {
                kwList.add(keywords.getString(j));
            }
            metadata.put("tags", String.join(",", kwList));
        }
        if (codeExample != null && !codeExample.isEmpty()) {
            metadata.put("code_example", codeExample);
        }

        // 用kp_id作为唯一key，避免重复导入
        vectorStore.add(kpId, embeddingService.embed(textForEmbedding.toString()), metadata);
        return true;
    }

    /**
     * 向量化并存储习题
     */
    private boolean importExercise(JSONObject ex, String chapterId, String chapterName, int chapterOrder) {
        String exId = ex.getString("ex_id");
        if (vectorStore.contains(exId)) return false;
        String type = ex.getString("type");
        String question = ex.getString("question");
        String answer = ex.getString("answer");
        String explanation = ex.getString("explanation");
        String difficulty = ex.getString("difficulty");
        String kpId = ex.getString("kp_id");

        // 构建向量化文本
        StringBuilder textForEmbedding = new StringBuilder();
        textForEmbedding.append(question);
        if (ex.containsKey("options")) {
            JSONArray options = ex.getJSONArray("options");
            if (options != null && !options.isEmpty()) {
                List<String> optList = new ArrayList<>();
                for (int j = 0; j < options.size(); j++) {
                    optList.add(options.getString(j));
                }
                textForEmbedding.append(" 选项：").append(String.join(" ", optList));
            }
        }
        textForEmbedding.append(" 答案：").append(answer);
        textForEmbedding.append(" 解析：").append(explanation);

        // 元数据
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "exercise");
        metadata.put("ex_id", exId);
        metadata.put("exercise_type", type);
        metadata.put("chapter_id", chapterId);
        metadata.put("chapter_name", chapterName);
        metadata.put("chapter_order", String.valueOf(chapterOrder));
        metadata.put("question", question);
        metadata.put("answer", answer);
        metadata.put("explanation", explanation);
        metadata.put("difficulty", difficulty);
        metadata.put("kp_id", kpId);

        vectorStore.add(exId, embeddingService.embed(textForEmbedding.toString()), metadata);
        return true;
    }

    /**
     * 向量化并存储常见错误
     */
    private boolean importMistake(JSONObject m, String chapterId, String chapterName, int chapterOrder) {
        String mistakeId = "mistake_" + chapterId + "_" + (mistakeCounter++);
        if (vectorStore.contains(mistakeId)) return false;
        String mistake = m.getString("mistake");
        String correct = m.getString("correct");
        String kpId = m.getString("kp_id");

        String textForEmbedding = "常见错误：" + mistake + " 正确做法：" + correct;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "common_mistake");
        metadata.put("chapter_id", chapterId);
        metadata.put("chapter_name", chapterName);
        metadata.put("chapter_order", String.valueOf(chapterOrder));
        metadata.put("mistake", mistake);
        metadata.put("correct", correct);
        metadata.put("kp_id", kpId);
        metadata.put("content", textForEmbedding);

        vectorStore.add(mistakeId, embeddingService.embed(textForEmbedding), metadata);
        return true;
    }

    /**
     * 获取导入状态
     */
    public boolean isImported() { return imported; }
    public int getVectorCount() { return vectorStore.size(); }

    // ==================== 内部类 ====================

    public static class ImportResult {
        public final int knowledgePoints;
        public final int exercises;
        public final int mistakes;
        public final long elapsedMs;

        public ImportResult(int knowledgePoints, int exercises, int mistakes, long elapsedMs) {
            this.knowledgePoints = knowledgePoints;
            this.exercises = exercises;
            this.mistakes = mistakes;
            this.elapsedMs = elapsedMs;
        }
    }

    private static class ChapterImportResult {
        final int knowledgePoints;
        final int exercises;
        final int mistakes;

        ChapterImportResult(int knowledgePoints, int exercises, int mistakes) {
            this.knowledgePoints = knowledgePoints;
            this.exercises = exercises;
            this.mistakes = mistakes;
        }
    }
}
