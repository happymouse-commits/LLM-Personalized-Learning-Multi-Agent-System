package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.Chapter;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.ChapterRepository;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.AudioStorageService;
import com.happymouse.cryd.service.spark.SparkClient;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 资源调度中心 — 接收信号，调度8个专业智能体生成资源
 * 不自己生成，只负责调度、收集、校验
 */
@Service
public class AutoResourceGenerator {

    private static final Logger log = LoggerFactory.getLogger(AutoResourceGenerator.class);

    // 正在生成资源的学生ID集合（用于前端轮询状态）
    private final Set<Long> generatingStudents = ConcurrentHashMap.newKeySet();
    // 每个学生的生成进度（0-100）
    private final Map<Long, Integer> generationProgress = new ConcurrentHashMap<>();
    // 每个学生当前生成步骤描述
    private final Map<Long, String> generationStep = new ConcurrentHashMap<>();

    private final SparkClient sparkClient;
    private final StudentRepository studentRepo;
    private final LearningResourceRepository resourceRepo;
    private final ChapterRepository chapterRepo;
    private final HallucinationSupervisorAgent hallucinationSupervisor;
    private final AudioStorageService audioStorage;
    /** 专用线程池：避免阻塞 IO 占用 commonPool 影响其他任务 */
    private final Executor agentTaskExecutor;

    // ==================== 质量保障常量 ====================
    // 修复L6: MAX_RETRIES / MIN_HALLUCINATION_SCORE 已随死代码 generateWithQuality 一并移除
    //   实际重试与阈值由 ResourceAgent.generateWithQuality 默认实现承担（见 ResourceAgent.java）
    private static final long AGENT_TIMEOUT_MS = 60_000;

    // 延迟清理调度器（替代 new Thread）
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "resource-cleanup");
        t.setDaemon(true);
        return t;
    });

    @Value("${system.storage.resources-path:../data/resources}")
    private String resourcesPath;

    // 8个专业智能体
    private final ConceptAgent conceptAgent;
    private final MindmapAgent mindmapAgent;
    private final CodeAgent codeAgent;
    private final ExerciseAgent exerciseAgent;
    private final WeaknessAgent weaknessAgent;
    private final VideoAgent videoAgent;
    private final PptAgent pptAgent;
    private final AudioAgent audioAgent;

    // 所有智能体列表，用于批量调度
    private final List<ResourceAgent> allAgents;

    public AutoResourceGenerator(SparkClient sparkClient,
                                 StudentRepository studentRepo,
                                 LearningResourceRepository resourceRepo,
                                 ChapterRepository chapterRepo,
                                 HallucinationSupervisorAgent hallucinationSupervisor,
                                 AudioStorageService audioStorage,
                                 @Qualifier("agentTaskExecutor") Executor agentTaskExecutor,
                                 ConceptAgent conceptAgent,
                                 MindmapAgent mindmapAgent,
                                 CodeAgent codeAgent,
                                 ExerciseAgent exerciseAgent,
                                 WeaknessAgent weaknessAgent,
                                 VideoAgent videoAgent,
                                 PptAgent pptAgent,
                                 AudioAgent audioAgent) {
        this.sparkClient = sparkClient;
        this.studentRepo = studentRepo;
        this.resourceRepo = resourceRepo;
        this.chapterRepo = chapterRepo;
        this.hallucinationSupervisor = hallucinationSupervisor;
        this.audioStorage = audioStorage;
        this.agentTaskExecutor = agentTaskExecutor;
        this.conceptAgent = conceptAgent;
        this.mindmapAgent = mindmapAgent;
        this.codeAgent = codeAgent;
        this.exerciseAgent = exerciseAgent;
        this.weaknessAgent = weaknessAgent;
        this.videoAgent = videoAgent;
        this.pptAgent = pptAgent;
        this.audioAgent = audioAgent;

        this.allAgents = List.of(
            conceptAgent, mindmapAgent, codeAgent, exerciseAgent,
            weaknessAgent, videoAgent, pptAgent, audioAgent
        );
    }

    // ==================== 自动生成判断 ====================

    /**
     * 判断是否需要为学生自动生成资源
     */
    public boolean shouldAutoGenerate(Long studentId) {
        var studentOpt = studentRepo.findById(studentId);
        if (studentOpt.isEmpty()) return false;

        List<LearningResource> existing = resourceRepo.findByStudentIdOrderByCreatedAtDesc(studentId);

        // 从未生成过 → 需要
        if (existing.isEmpty()) return true;

        // 最近一次生成时间超过2小时且资源不足10篇 → 需要
        LearningResource latest = existing.get(0);
        if (latest.getCreatedAt() != null &&
            latest.getCreatedAt().isBefore(LocalDateTime.now().minusHours(2)) &&
            existing.size() < 10) {
            return true;
        }

        return false;
    }

    // ==================== 生成状态查询 ====================

    public boolean isGenerating(Long studentId) {
        return generatingStudents.contains(studentId);
    }

    public int getProgress(Long studentId) {
        return generationProgress.getOrDefault(studentId, 0);
    }

    public String getStep(Long studentId) {
        return generationStep.getOrDefault(studentId, "");
    }

    // ==================== 核心调度方法（带质量保障） ====================

    /**
     * 自动生成资源（调度8个智能体）
     * 流程：查学生画像 → 推断章节 → 按需选择Agent → 并行生成 → 保存
     */
    public List<LearningResource> autoGenerate(Long studentId) {
        return autoGenerate(studentId, null, null, null);
    }

    /**
     * 按筛选条件自动生成学习资源
     * @param types 需要生成的资源类型列表，null表示全部
     */
    public List<LearningResource> autoGenerate(Long studentId, List<String> types) {
        return autoGenerate(studentId, types, null, null);
    }

    /**
     * 按筛选条件自动生成学习资源（支持指定章节和难度）
     * @param types       需要生成的资源类型列表，null或空表示全部
     * @param chapter     章节选择（"all" 或 "1"~"7"），null表示按学生薄弱环节推断
     * @param difficulty  难度（"easy"/"medium"/"hard"），null表示不强制
     */
    public List<LearningResource> autoGenerate(Long studentId, List<String> types,
                                               String chapter, String difficulty) {
        generatingStudents.add(studentId);
        generationProgress.put(studentId, 0);
        generationStep.put(studentId, "正在分析学习画像...");

        try {
            log.info("开始生成资源: studentId={}", studentId);

            // 1. 从数据库读取真实学生画像
            Student student = studentRepo.findBySysUserId(studentId).orElse(null);
            String knowledgeLevel = "30";
            String weakAreas = "";
            if (student != null) {
                knowledgeLevel = student.getKnowledgeLevel() != null
                        ? String.valueOf(student.getKnowledgeLevel()) : "30";
                weakAreas = student.getWeakAreas() != null ? student.getWeakAreas() : "";
            }
            log.info("学生画像: level={}, weakAreas={}", knowledgeLevel, weakAreas);

            generationProgress.put(studentId, 20);
            generationStep.put(studentId, "正在推断章节...");

            // 2. 确定章节和知识点：优先使用用户指定的章节，否则从薄弱环节推断
            String chapterName;
            String knowledgePoint;
            String mappedKp = mapChapterToKnowledgePoint(chapter);
            if (mappedKp != null) {
                // 用户明确选择了章节，以此为准
                knowledgePoint = mappedKp;
                chapterName = inferChapter(knowledgePoint);
            } else if (!weakAreas.isEmpty()) {
                // 取第一个薄弱环节作为主要知识点
                String firstWeak = weakAreas.split("[,，、]")[0].trim();
                chapterName = inferChapter(firstWeak);
                knowledgePoint = firstWeak;
            } else {
                // 无薄弱点时，查第一章
                List<Chapter> chapters = chapterRepo.findByStatusOrderByOrderNum("published");
                if (!chapters.isEmpty()) {
                    Chapter first = chapters.get(0);
                    chapterName = first.getName();
                    knowledgePoint = first.getDescription() != null ? first.getDescription() : first.getName();
                } else {
                    chapterName = "C语言基础";
                    knowledgePoint = "C语言基础";
                }
            }
            log.info("推断结果: chapter={}, knowledgePoint={}, 指定难度={}", chapterName, knowledgePoint, difficulty);

            generationProgress.put(studentId, 40);
            generationStep.put(studentId, "正在生成学习内容（多智能体并行）...");

            // 3. 调度Agent并行生成（supports() + types 过滤）
            List<LearningResource> resources = dispatch(studentId, knowledgePoint, knowledgeLevel, weakAreas, types);

            // 3.5 扫描预置PPT文件，补充所有课件资源
            boolean wantsPpt = types == null || types.isEmpty() || types.contains("course-ppt");
            if (wantsPpt) {
                List<LearningResource> pptResources = scanAllPptFiles(knowledgePoint);
                resources.addAll(pptResources);
            }

            generationProgress.put(studentId, 80);
            generationStep.put(studentId, "正在保存资源...");

            // 4. 保存所有生成的资源，若用户指定了难度则覆盖默认难度
            List<LearningResource> saved = new ArrayList<>();
            for (LearningResource lr : resources) {
                lr.setStudentId(studentId);
                lr.setGeneratedBy("auto-pipeline");
                if (difficulty != null && !difficulty.isBlank() && !"all".equals(difficulty)) {
                    lr.setDifficulty(difficulty);
                }
                lr.setCreatedAt(LocalDateTime.now());
                saved.add(resourceRepo.save(lr));
            }

            generationProgress.put(studentId, 100);
            generationStep.put(studentId, "资源生成完成");

            log.info("自动资源生成完成: studentId={}, count={}", studentId, saved.size());
            return saved;
        } catch (Exception e) {
            log.error("自动资源生成失败: studentId={}, error={}", studentId, e.getMessage(), e);
            generationStep.put(studentId, "生成失败: " + e.getMessage());
            throw new RuntimeException("资源生成失败: " + e.getMessage(), e);
        } finally {
            // 延迟清除状态，让前端有时间获取最终状态（使用线程池替代裸线程）
            Long sid = studentId;
            cleanupScheduler.schedule(() -> {
                generatingStudents.remove(sid);
                generationProgress.remove(sid);
                generationStep.remove(sid);
            }, 3, TimeUnit.SECONDS);
        }
    }

    // ==================== 带质量保障的智能体调度 ====================

    /**
     * 为单个知识点调度智能体生成资源（全量）
     */
    public List<LearningResource> dispatch(Long studentId, String knowledgePoint,
                                            String knowledgeLevel, String weakAreas) {
        return dispatch(studentId, knowledgePoint, knowledgeLevel, weakAreas, null);
    }

    /**
     * 按类型筛选调度智能体生成资源
     * @param types 需要生成的资源类型列表，null或空表示全部
     */
    public List<LearningResource> dispatch(Long studentId, String knowledgePoint,
                                            String knowledgeLevel, String weakAreas,
                                            List<String> types) {
        String chapterName = inferChapter(knowledgePoint);
        List<LearningResource> resources = new CopyOnWriteArrayList<>();

        // 按需选择Agent：先按 supports() 过滤，再按 types 过滤
        List<ResourceAgent> selectedAgents = allAgents.stream()
                .filter(agent -> agent.supports(chapterName, knowledgePoint, weakAreas))
                .filter(agent -> types == null || types.isEmpty()
                        || types.contains(agent.getResourceType()))
                .toList();
        log.info("调度Agent: 总数={}, 选中={}, 知识点={}, types={}", allAgents.size(), selectedAgents.size(), knowledgePoint, types);

        // 并行调用选中的Agent（使用专用线程池，避免阻塞 IO 占用 commonPool）
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ResourceAgent agent : selectedAgents) {
            futures.add(CompletableFuture.runAsync(() -> {
                LearningResource lr = agent.generateWithQuality(chapterName, knowledgePoint, knowledgeLevel, weakAreas, hallucinationSupervisor);
                if (lr != null) {
                    resources.add(lr);
                }
            }, agentTaskExecutor));
        }

        // 等待所有Agent完成（最多120秒）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("并行Agent执行超时或异常: {}", e.getMessage());
        }

        log.info("并行生成完成: 成功{}个", resources.size());
        return resources;
    }

    // ==================== 章节全量生成 ====================

    /**
     * 为所有章节生成全套资源
     */
    public List<LearningResource> generateAllChapterResources(Long studentId) {
        List<LearningResource> allGenerated = new ArrayList<>();
        try {
            List<Chapter> chapters = chapterRepo.findByStatusOrderByOrderNum("published");
            if (chapters.isEmpty()) return allGenerated;

            Student student = studentRepo.findById(studentId).orElse(null);
            String knowledgeLevel = student != null && student.getKnowledgeLevel() != null
                    ? String.valueOf(student.getKnowledgeLevel()) : "30";
            String weakAreas = student != null && student.getWeakAreas() != null
                    ? student.getWeakAreas() : "";

            for (Chapter chapter : chapters) {
                String chapterName = chapter.getName();
                String knowledgePoint = chapter.getDescription() != null ? chapter.getDescription() : chapterName;

                List<LearningResource> resources = dispatch(studentId, knowledgePoint, knowledgeLevel, weakAreas);
                for (LearningResource lr : resources) {
                    lr.setStudentId(studentId);
                    lr.setGeneratedBy("chapter-init");
                    lr.setCreatedAt(LocalDateTime.now());
                    allGenerated.add(resourceRepo.save(lr));
                }
                log.info("章节资源生成完成: chapter={}, count={}", chapterName, resources.size());
            }

            log.info("全量章节资源生成完成: studentId={}, total={}", studentId, allGenerated.size());
        } catch (Exception e) {
            log.error("全量章节资源生成失败: studentId={}", studentId, e);
        }
        return allGenerated;
    }

    // ==================== 艾宾浩斯复习 ====================

    /**
     * 生成艾宾浩斯复习资源
     */
    public List<LearningResource> generateReviewResources(Long studentId, Set<String> knowledgePoints, int daysSince) {
        List<LearningResource> generated = new ArrayList<>();

        // 读取学生真实画像
        Student student = studentRepo.findBySysUserId(studentId).orElse(null);
        String knowledgeLevel = student != null && student.getKnowledgeLevel() != null
                ? String.valueOf(student.getKnowledgeLevel()) : "30";
        String weakAreas = student != null && student.getWeakAreas() != null
                ? student.getWeakAreas() : "";

        for (String kp : knowledgePoints) {
            String chapterName = inferChapter(kp);
            try {
                // 复习资源主要用ConceptAgent和WeaknessAgent（使用Agent自治的质量保障）
                LearningResource article = conceptAgent.generateWithQuality(chapterName, kp, knowledgeLevel, weakAreas, hallucinationSupervisor);
                if (article != null) {
                    article.setStudentId(studentId);
                    article.setGeneratedBy("ebbinghaus-review");
                    article.setTags("第" + daysSince + "天");
                    article.setCreatedAt(LocalDateTime.now());
                    generated.add(resourceRepo.save(article));
                }

                // WeaknessAgent 只在薄弱环节时才调用
                if (weaknessAgent.supports(chapterName, kp, weakAreas)) {
                    LearningResource weakness = weaknessAgent.generateWithQuality(chapterName, kp, knowledgeLevel, weakAreas, hallucinationSupervisor);
                    if (weakness != null) {
                        weakness.setStudentId(studentId);
                        weakness.setGeneratedBy("ebbinghaus-review");
                        weakness.setTags("第" + daysSince + "天");
                        weakness.setCreatedAt(LocalDateTime.now());
                        generated.add(resourceRepo.save(weakness));
                    }
                }
            } catch (Exception e) {
                log.warn("复习资源生成失败: kp={}, error={}", kp, e.getMessage());
            }
        }
        log.info("艾宾浩斯复习资源生成完成: studentId={}, count={}", studentId, generated.size());
        return generated;
    }

    // ==================== 预置PPT文件扫描 ====================

    /** 章节名（从文件名提取）→ 中文标题映射 */
    private static final Map<String, String> PPT_TITLE_MAP = Map.of(
        "基础语法", "C语言基础语法学习课件",
        "流程控制", "C语言流程控制学习课件",
        "数组与指针", "C语言数组与指针学习课件"
    );

    /**
     * 扫描 resources 目录下所有 .pptx 文件，将每个PPT转为图片，
     * 为每个文件创建一条课件资源（content=幻灯片URL列表，coverImage=第一页图片URL）
     */
    private List<LearningResource> scanAllPptFiles(String knowledgePoint) {
        List<LearningResource> pptResources = new ArrayList<>();
        try {
            Path dir = resolvePptResourcesDir();
            if (dir == null) return pptResources;

            // 确定项目根目录（用于缓存路径）
            String projectRoot = findProjectRoot();

            try (var files = Files.list(dir)) {
                List<Path> pptFiles = files
                    .filter(f -> f.toString().endsWith(".pptx"))
                    .sorted()
                    .toList();

                for (Path pptFile : pptFiles) {
                    try {
                        String fileName = pptFile.getFileName().toString();
                        String chapterKey = fileName.split("_")[0];
                        String title = PPT_TITLE_MAP.getOrDefault(chapterKey, chapterKey + "学习课件");

                        // PPT转图片（缓存到 data/ppt-cache/）
                        List<String> slideUrls = convertPptToImages(pptFile, fileName, projectRoot);
                        if (slideUrls.isEmpty()) {
                            log.warn("PPT转图片失败，跳过: {}", fileName);
                            continue;
                        }

                        // content 存储幻灯片URL列表（换行分隔）
                        String content = String.join("\n", slideUrls);

                        LearningResource lr = new LearningResource();
                        lr.setTitle(title);
                        lr.setType(ResourceType.COURSE_PPT.getId());
                        lr.setContent(content);
                        lr.setCoverImage(slideUrls.get(0)); // 第一页作为封面
                        lr.setKnowledgePoint(knowledgePoint);
                        lr.setDifficulty("medium");
                        lr.setGeneratedBy("ppt-scan");
                        pptResources.add(lr);
                        log.info("扫描到预置PPT: {}, {}页幻灯片", fileName, slideUrls.size());
                    } catch (Exception e) {
                        log.warn("处理PPT文件失败: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("扫描PPT文件失败: {}", e.getMessage());
        }
        return pptResources;
    }

    private Path resolvePptResourcesDir() {
        Path dir = Paths.get(resourcesPath);
        if (!dir.isAbsolute()) {
            String userDir = System.getProperty("user.dir");
            Path abs = Paths.get(userDir, resourcesPath).normalize();
            if (Files.exists(abs)) dir = abs;
        }
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return null;
        return dir;
    }

    private String findProjectRoot() {
        String userDir = System.getProperty("user.dir");
        Path dir = Paths.get(userDir);
        for (int i = 0; i < 5; i++) {
            if (Files.exists(dir.resolve("pom.xml")) || Files.exists(dir.resolve("frontend"))) {
                return dir.toAbsolutePath().toString();
            }
            dir = dir.getParent();
            if (dir == null) break;
        }
        return userDir;
    }

    /**
     * 将PPT文件转为PNG图片，缓存到 data/ppt-cache/ 目录
     * @return 幻灯片图片URL列表（如 /api/ppt/slides/filename/slide-1.png）
     */
    private List<String> convertPptToImages(Path pptFile, String fileName, String projectRoot) {
        List<String> urls = new ArrayList<>();
        try {
            // 缓存目录 — 使用文件名哈希避免中文替换问题
            String cacheName = Integer.toHexString(fileName.hashCode());
            Path cacheDir = Paths.get(projectRoot, "data/ppt-cache", cacheName);
            Files.createDirectories(cacheDir);

            // 检查是否已缓存
            File[] cached = cacheDir.toFile().listFiles((d, n) -> n.endsWith(".png"));
            if (cached != null && cached.length > 0) {
                Arrays.sort(cached);
                for (File img : cached) {
                    urls.add("/api/ppt/slides/" + fileName + "/" + img.getName());
                }
                return urls;
            }

            // 用 Apache POI 渲染每页幻灯片为PNG
            try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(pptFile))) {
                java.awt.Dimension pageSize = ppt.getPageSize();
                double scale = 2.0;
                int imgW = (int) (pageSize.getWidth() * scale);
                int imgH = (int) (pageSize.getHeight() * scale);

                int idx = 1;
                for (XSLFSlide slide : ppt.getSlides()) {
                    BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = img.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.scale(scale, scale);
                    slide.draw(g2d);
                    g2d.dispose();

                    String imgName = "slide-" + idx + ".png";
                    File imgFile = cacheDir.resolve(imgName).toFile();
                    ImageIO.write(img, "png", imgFile);
                    urls.add("/api/ppt/slides/" + fileName + "/" + imgName);
                    idx++;
                }
            }
        } catch (Exception e) {
            log.warn("PPT转图片失败: {} - {}", fileName, e.getMessage());
        }
        return urls;
    }

    // ==================== 知识点提取 ====================

    /**
     * 从文本中提取知识点
     */
    public Set<String> extractKnowledgePoints(String text) {
        Set<String> kps = new HashSet<>();
        if (text == null || text.isBlank()) return kps;

        // C语言关键词映射
        Map<String, String> keywordMap = Map.ofEntries(
            Map.entry("指针", "指针"), Map.entry("pointer", "指针"),
            Map.entry("数组", "数组"), Map.entry("array", "数组"),
            Map.entry("函数", "函数"), Map.entry("function", "函数"),
            Map.entry("循环", "流程控制"), Map.entry("for", "流程控制"),
            Map.entry("while", "流程控制"), Map.entry("if", "流程控制"),
            Map.entry("结构体", "结构体"), Map.entry("struct", "结构体"),
            Map.entry("文件", "文件操作"), Map.entry("fopen", "文件操作"),
            Map.entry("内存", "内存管理"), Map.entry("malloc", "内存管理"),
            Map.entry("字符串", "字符串"), Map.entry("printf", "输入输出"),
            Map.entry("scanf", "输入输出")
        );

        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : keywordMap.entrySet()) {
            if (lower.contains(entry.getKey())) {
                kps.add(entry.getValue());
            }
        }

        return kps;
    }

    /**
     * 根据知识点推断章节
     */
    public String inferChapter(String knowledgePoint) {
        Map<String, String> chapterMap = Map.of(
            "指针", "数组与指针", "数组", "数组与指针",
            "函数", "函数", "结构体", "结构体",
            "文件", "文件操作", "内存", "数组与指针",
            "字符串", "数组与指针", "流程控制", "流程控制",
            "输入输出", "基础语法"
        );

        for (Map.Entry<String, String> entry : chapterMap.entrySet()) {
            if (knowledgePoint.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "C语言基础";
    }

    /**
     * 将前端章节选择值映射为知识点关键词
     * @param chapter "all" 或 "1"~"7"
     * @return 知识点关键词，null 表示不指定（按薄弱环节推断）
     */
    private String mapChapterToKnowledgePoint(String chapter) {
        if (chapter == null || chapter.isBlank() || "all".equals(chapter)) {
            return null;
        }
        Map<String, String> map = Map.of(
            "1", "基础语法",
            "2", "流程控制",
            "3", "函数",
            "4", "数组",
            "5", "指针",
            "6", "结构体",
            "7", "文件操作"
        );
        return map.get(chapter);
    }
}
