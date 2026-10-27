package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.common.JsonParser;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.agent.HallucinationSupervisorAgent;
import com.happymouse.cryd.service.agent.AutoResourceGenerator;
import com.happymouse.cryd.service.agent.*;
import com.happymouse.cryd.service.CodeCompilerService;
import com.happymouse.cryd.service.spark.SparkClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 资源中心控制器 — 资源管理、分类、搜索、收藏、评论、导出、AI生成
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    /** 艾宾浩斯遗忘曲线复习间隔（天数） */
    private static final int[] EBBINGHAUS_INTERVALS = {1, 2, 4, 7, 15};

    @Value("${system.storage.resources-path:../data/resources}")
    private String resourcesPath;

    @Value("${system.storage.audio-chapters-path:../data/audio/chapters}")
    private String audioChaptersPath;

    private final LearningResourceRepository resourceRepo;
    private final ErrorNotebookRepository errorRepo;
    private final StudentRepository studentRepo;
    private final SparkClient sparkClient;
    private final CodeCompilerService codeCompiler;
    private final HallucinationSupervisorAgent hallucinationSupervisor;
    private final AutoResourceGenerator autoResourceGenerator;
    private final ConceptAgent conceptAgent;
    private final MindmapAgent mindmapAgent;
    private final CodeAgent codeAgent;
    private final ExerciseAgent exerciseAgent;
    private final WeaknessAgent weaknessAgent;
    private final VideoAgent videoAgent;
    private final PptAgent pptAgent;
    private final AudioAgent audioAgent;
    private final ExecutorService resourceExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "resource-gen");
        t.setDaemon(true);
        return t;
    });
    private final OkHttpClient biliHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public ResourceController(LearningResourceRepository resourceRepo,
                               ErrorNotebookRepository errorRepo,
                               StudentRepository studentRepo,
                               SparkClient sparkClient,
                               CodeCompilerService codeCompiler,
                               HallucinationSupervisorAgent hallucinationSupervisor,
                               AutoResourceGenerator autoResourceGenerator,
                               ConceptAgent conceptAgent,
                               MindmapAgent mindmapAgent,
                               CodeAgent codeAgent,
                               ExerciseAgent exerciseAgent,
                               WeaknessAgent weaknessAgent,
                               VideoAgent videoAgent,
                               PptAgent pptAgent,
                               AudioAgent audioAgent) {
        this.resourceRepo = resourceRepo;
        this.errorRepo = errorRepo;
        this.studentRepo = studentRepo;
        this.sparkClient = sparkClient;
        this.codeCompiler = codeCompiler;
        this.hallucinationSupervisor = hallucinationSupervisor;
        this.autoResourceGenerator = autoResourceGenerator;
        this.conceptAgent = conceptAgent;
        this.mindmapAgent = mindmapAgent;
        this.codeAgent = codeAgent;
        this.exerciseAgent = exerciseAgent;
        this.weaknessAgent = weaknessAgent;
        this.videoAgent = videoAgent;
        this.pptAgent = pptAgent;
        this.audioAgent = audioAgent;
    }

    /**
     * 一键生成：每种类型各生成一份资源（8种类型）
     * 异步执行，立即返回，前端轮询或刷新查看结果
     */
    @PostMapping("/generate-one-of-each/{sysUserId}")
    public Result<String> generateOneOfEach(@PathVariable Long sysUserId) {
        try {
            var studentOpt = studentRepo.findBySysUserId(sysUserId);
            if (studentOpt.isEmpty()) {
                studentOpt = studentRepo.findByUsername("student_" + sysUserId);
            }
            if (studentOpt.isEmpty()) {
                Student s = new Student();
                s.setUsername("student_" + sysUserId);
                s.setSysUserId(sysUserId);
                s.setKnowledgeLevel(30);
                try {
                    s = studentRepo.save(s);
                } catch (Exception dupEx) {
                    studentOpt = studentRepo.findByUsername("student_" + sysUserId);
                    if (studentOpt.isEmpty()) throw dupEx;
                }
                if (studentOpt.isEmpty()) studentOpt = Optional.of(s);
            }
            Student student = studentOpt.get();
            Long finalStudentId = student.getId();

            // 异步生成，立即返回
            CompletableFuture.runAsync(() -> doGenerateOneOfEach(finalStudentId, sysUserId), resourceExecutor);

            return Result.success("正在生成中，请稍后刷新页面查看");
        } catch (Exception e) {
            log.error("一键生成启动失败", e);
            return Result.error("启动失败: " + e.getMessage());
        }
    }

    private void doGenerateOneOfEach(Long studentId, Long sysUserId) {
        try {
            Student student = studentRepo.findById(studentId).orElse(null);
            if (student == null) return;
            String knowledgeLevel = String.valueOf(student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30);
            String weakAreas = student.getWeakAreas() != null ? student.getWeakAreas() : "";
            String knowledgePoint = "C语言基础";
            if (!weakAreas.isEmpty()) {
                knowledgePoint = weakAreas.split("[,，、]")[0].trim();
            }
            String chapterName = autoResourceGenerator.inferChapter(knowledgePoint);

            List<LearningResource> saved = new ArrayList<>();

            // 1. 用智能体生成：知识精讲、思维导图、编程实战、视频讲解
            tryGenerate(conceptAgent, studentId, chapterName, knowledgePoint, knowledgeLevel, weakAreas, "explanation", "知识精讲", saved);
            tryGenerate(mindmapAgent, studentId, chapterName, knowledgePoint, knowledgeLevel, weakAreas, "mindmap", "思维导图", saved);
            tryGenerate(codeAgent, studentId, chapterName, knowledgePoint, knowledgeLevel, weakAreas, "code", "编程实战", saved);
            tryGenerate(videoAgent, studentId, chapterName, knowledgePoint, knowledgeLevel, weakAreas, "course-video", "视频讲解", saved);

            // 2. 直接使用已有的PPT文件
            createPptResource(studentId, knowledgePoint, saved);

            // 3. 直接使用已有的MP3音频文件；若不存在则降级为 AudioAgent TTS 生成
            if (!createAudioResource(studentId, knowledgePoint, saved)) {
                log.info("章节音频文件不存在，降级使用 AudioAgent TTS 生成听学笔记");
                tryGenerateAudio(audioAgent, studentId, chapterName, knowledgePoint, knowledgeLevel, weakAreas, saved);
            }

            log.info("一键生成完成: sysUserId={}, count={}", sysUserId, saved.size());
        } catch (Exception e) {
            log.error("一键生成后台执行失败: sysUserId={}", sysUserId, e);
        }
    }

    /**
     * 直接使用已有的PPT文件创建资源 — 扫描 resources 目录下所有 .pptx 文件，
     * 转为图片后存储幻灯片URL列表和封面图片
     */
    private void createPptResource(Long studentId, String knowledgePoint, List<LearningResource> saved) {
        try {
            Path resourcesDir = Paths.get(resourcesPath);
            if (!resourcesDir.isAbsolute()) {
                String userDir = System.getProperty("user.dir");
                Path absolutePath = Paths.get(userDir, resourcesPath).normalize();
                if (Files.exists(absolutePath)) {
                    resourcesDir = absolutePath;
                }
            }

            if (!Files.exists(resourcesDir) || !Files.isDirectory(resourcesDir)) {
                log.warn("PPT资源目录不存在: {}", resourcesDir);
                return;
            }

            // 章节名 → 中文标题映射
            Map<String, String> chapterTitleMap = Map.of(
                "基础语法", "C语言基础语法学习课件",
                "流程控制", "C语言流程控制学习课件",
                "数组与指针", "C语言数组与指针学习课件"
            );

            String projectRoot = findProjectRoot();

            // 扫描所有 .pptx 文件，为每个文件创建一条资源
            try (var files = Files.list(resourcesDir)) {
                List<Path> pptFiles = files
                    .filter(f -> f.toString().endsWith(".pptx"))
                    .sorted()
                    .toList();

                for (Path pptFile : pptFiles) {
                    String fileName = pptFile.getFileName().toString();
                    String chapterKey = fileName.split("_")[0];
                    String title = chapterTitleMap.getOrDefault(chapterKey,
                        chapterKey + "学习课件");

                    // 检查是否已有同名资源，避免重复
                    boolean exists = saved.stream().anyMatch(r ->
                        r.getContent() != null && r.getContent().contains(fileName));
                    if (exists) continue;

                    // PPT转图片
                    List<String> slideUrls = convertPptToSlideImages(pptFile, fileName, projectRoot);

                    LearningResource lr = new LearningResource();
                    lr.setTitle(title);
                    lr.setType("course-ppt");
                    if (!slideUrls.isEmpty()) {
                        lr.setContent(String.join("\n", slideUrls));
                        lr.setCoverImage(slideUrls.get(0));
                    } else {
                        lr.setContent("[PPT_FILE:" + pptFile.toAbsolutePath() + "]");
                    }
                    lr.setKnowledgePoint(knowledgePoint);
                    lr.setDifficulty("medium");
                    lr.setStudentId(studentId);
                    lr.setGeneratedBy("one-click");
                    lr.setCreatedAt(java.time.LocalDateTime.now());
                    saved.add(resourceRepo.save(lr));
                    log.info("PPT课件添加成功: {}, {}页幻灯片", fileName, slideUrls.size());
                }
            }
        } catch (Exception e) {
            log.warn("PPT课件添加失败: {}", e.getMessage());
        }
    }

    private String findProjectRoot() {
        String userDir = System.getProperty("user.dir");
        java.nio.file.Path dir = java.nio.file.Paths.get(userDir);
        for (int i = 0; i < 5; i++) {
            if (java.nio.file.Files.exists(dir.resolve("pom.xml")) || java.nio.file.Files.exists(dir.resolve("frontend"))) {
                return dir.toAbsolutePath().toString();
            }
            dir = dir.getParent();
            if (dir == null) break;
        }
        return userDir;
    }

    private List<String> convertPptToSlideImages(Path pptFile, String fileName, String projectRoot) {
        List<String> urls = new ArrayList<>();
        try {
            // 缓存目录 — 使用文件名哈希避免中文替换问题
            String cacheName = Integer.toHexString(fileName.hashCode());
            Path cacheDir = Paths.get(projectRoot, "data/ppt-cache", cacheName);
            Files.createDirectories(cacheDir);

            File[] cached = cacheDir.toFile().listFiles((d, n) -> n.endsWith(".png"));
            if (cached != null && cached.length > 0) {
                Arrays.sort(cached);
                for (File img : cached) {
                    urls.add("/api/ppt/slides/" + fileName + "/" + img.getName());
                }
                return urls;
            }

            try (var ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(Files.newInputStream(pptFile))) {
                java.awt.Dimension pageSize = ppt.getPageSize();
                double scale = 2.0;
                int imgW = (int) (pageSize.getWidth() * scale);
                int imgH = (int) (pageSize.getHeight() * scale);

                int idx = 1;
                for (var slide : ppt.getSlides()) {
                    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(imgW, imgH, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g2d = img.createGraphics();
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.scale(scale, scale);
                    slide.draw(g2d);
                    g2d.dispose();

                    String imgName = "slide-" + idx + ".png";
                    File imgFile = cacheDir.resolve(imgName).toFile();
                    javax.imageio.ImageIO.write(img, "png", imgFile);
                    urls.add("/api/ppt/slides/" + fileName + "/" + imgName);
                    idx++;
                }
            }
        } catch (Exception e) {
            log.warn("PPT转图片失败: {} - {}", fileName, e.getMessage());
        }
        return urls;
    }

    /**
     * 直接使用已有的MP3音频文件创建资源
     * @return true 表示成功添加资源；false 表示未找到章节音频文件
     */
    private boolean createAudioResource(Long studentId, String knowledgePoint, List<LearningResource> saved) {
        try {
            Path audioDir = Paths.get(audioChaptersPath);
            if (!audioDir.isAbsolute()) {
                String userDir = System.getProperty("user.dir");
                Path absolutePath = Paths.get(userDir, audioChaptersPath).normalize();
                if (Files.exists(absolutePath)) {
                    audioDir = absolutePath;
                }
            }

            // 查找MP3文件
            Path audioFile = null;
            String chapterFolder = "基础语法"; // 默认
            String[] chapters = {"基础语法", "流程控制", "数组与指针", "函数", "结构体", "文件操作"};
            for (String ch : chapters) {
                Path chapterDir = audioDir.resolve(ch);
                if (Files.exists(chapterDir)) {
                    try (var files = Files.list(chapterDir)) {
                        Optional<Path> mp3 = files.filter(f -> f.toString().endsWith(".mp3")).findFirst();
                        if (mp3.isPresent()) {
                            audioFile = mp3.get();
                            chapterFolder = ch;
                            break;
                        }
                    }
                }
            }

            if (audioFile != null) {
                // 复制到学生音频目录
                Path studentAudioDir = audioDir.getParent().resolve(String.valueOf(studentId));
                if (!Files.exists(studentAudioDir)) {
                    Files.createDirectories(studentAudioDir);
                }
                String resourceId = "audio_" + System.currentTimeMillis();
                Path destFile = studentAudioDir.resolve(resourceId + ".mp3");
                Files.copy(audioFile, destFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                LearningResource lr = new LearningResource();
                lr.setTitle("听学笔记：" + chapterFolder + " - " + knowledgePoint);
                lr.setType("blog");
                lr.setContent("🎧 点击播放音频，收听" + chapterFolder + "章节的听学笔记讲解。");
                lr.setKnowledgePoint(knowledgePoint);
                lr.setDifficulty("easy");
                lr.setStudentId(studentId);
                lr.setGeneratedBy("one-click");
                lr.setTags(String.valueOf(studentId) + "/" + resourceId + ".mp3");
                lr.setCreatedAt(java.time.LocalDateTime.now());
                saved.add(resourceRepo.save(lr));
                log.info("听学笔记(MP3)添加成功: {}", audioFile.getFileName());
                return true;
            }
            log.warn("未找到章节音频文件，听学笔记(MP3)添加失败: audioChaptersPath={}", audioDir);
        } catch (Exception e) {
            log.warn("听学笔记(MP3)添加失败: {}", e.getMessage());
        }
        return false;
    }

    private void tryGenerate(Object agent, Long studentId, String chapterName, String knowledgePoint,
                              String knowledgeLevel, String weakAreas, String expectedType,
                              String label, List<LearningResource> saved) {
        try {
            var lr = ((com.happymouse.cryd.service.agent.ResourceAgent) agent)
                    .generate(chapterName, knowledgePoint, knowledgeLevel, weakAreas);
            if (lr == null) {
                log.warn("Agent生成为空: type={}", label);
                return;
            }
            // 如果类型不匹配但内容有效，强制设置类型
            if (!expectedType.equals(lr.getType())) {
                lr.setType(expectedType);
            }
            lr.setStudentId(studentId);
            lr.setGeneratedBy("one-click");
            lr.setCreatedAt(java.time.LocalDateTime.now());
            log.info("准备保存: type={}, title={}, content长度={}", label, lr.getTitle(),
                    lr.getContent() != null ? lr.getContent().length() : 0);
            saved.add(resourceRepo.save(lr));
            log.info("一键生成成功: type={}, title={}", label, lr.getTitle());
        } catch (Exception e) {
            log.error("一键生成{}失败: {}", label, e.getMessage(), e);
        }
    }

    private void tryGenerateAudio(Object agent, Long studentId, String chapterName, String knowledgePoint,
                                   String knowledgeLevel, String weakAreas, List<LearningResource> saved) {
        try {
            var audioAgent = (com.happymouse.cryd.service.agent.AudioAgent) agent;
            String resourceId = "audio_" + System.currentTimeMillis();
            var lrWithAudio = audioAgent.generateWithAudio(chapterName, knowledgePoint,
                    studentId, resourceId, knowledgeLevel, weakAreas);
            if (lrWithAudio == null) {
                log.warn("AudioAgent生成为空");
                return;
            }
            lrWithAudio.setStudentId(studentId);
            lrWithAudio.setGeneratedBy("one-click");
            lrWithAudio.setType("blog");
            lrWithAudio.setCreatedAt(java.time.LocalDateTime.now());
            saved.add(resourceRepo.save(lrWithAudio));
            log.info("听学笔记生成成功: title={}, audio={}", lrWithAudio.getTitle(), lrWithAudio.getTags() != null);
        } catch (Exception e) {
            log.warn("听学笔记生成失败: {}", e.getMessage());
        }
    }

    /**
     * 获取学生资源 — 通过 SysUser ID 查找（兼容前端 store.id）
     */
    @GetMapping("/student/{sysUserId}")
    public Result<List<LearningResource>> getByStudent(@PathVariable Long sysUserId) {
        var studentOpt = studentRepo.findBySysUserId(sysUserId);
        if (studentOpt.isEmpty()) {
            return Result.error(404, "学生画像未构建，请先完成引导");
        }
        Long studentId = studentOpt.get().getId();

        // 返回该学生的资源（不再自动初始化视频，由一键生成控制）
        return Result.success(resourceRepo.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    /**
     * 分页获取学生资源
     */
    @GetMapping("/student/{studentId}/page")
    public Result<Map<String, Object>> getByStudentPage(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        var pageResult = resourceRepo.findByStudentIdOrderByCreatedAtDesc(
                studentId, PageRequest.of(page, size));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", pageResult.getContent());
        result.put("totalElements", pageResult.getTotalElements());
        result.put("totalPages", pageResult.getTotalPages());
        result.put("hasMore", pageResult.hasNext());
        result.put("currentPage", page);
        result.put("pageSize", size);
        return Result.success(result);
    }

    @DeleteMapping("/student/{sysUserId}/clear")
    @org.springframework.transaction.annotation.Transactional
    public Result<?> clearByStudent(@PathVariable Long sysUserId) {
        var studentOpt = studentRepo.findBySysUserId(sysUserId);
        if (studentOpt.isEmpty()) {
            return Result.error(404, "学生不存在");
        }
        Long studentId = studentOpt.get().getId();
        long count = resourceRepo.countByStudentId(studentId);
        resourceRepo.deleteAllByStudentId(studentId);
        log.info("清空学生{}(sysUserId={})资源: {}条", studentId, sysUserId, count);
        return Result.success("已清空 " + count + " 条资源");
    }

    @GetMapping("/student/{studentId}/type/{type}")
    public Result<List<LearningResource>> getByType(@PathVariable Long studentId, @PathVariable String type) {
        return Result.success(resourceRepo.findByStudentIdAndTypeOrderByCreatedAtDesc(studentId, type));
    }

    @GetMapping("/{id}")
    public Result<LearningResource> getById(@PathVariable Long id) {
        var resource = resourceRepo.findById(id).orElse(null);
        if (resource == null) return Result.error(404, "资源不存在");
        return Result.success(resource);
    }

    @GetMapping("/{id}/export")
    public Result<?> exportResource(@PathVariable Long id) {
        var resource = resourceRepo.findById(id).orElse(null);
        if (resource == null) return Result.error("资源不存在");
        String exportContent = "# " + resource.getTitle() + "\n\n" +
            "**类型**: " + resource.getType() + "\n" +
            "**难度**: " + resource.getDifficulty() + "\n" +
            "**知识点**: " + resource.getKnowledgePoint() + "\n\n---\n\n" +
            resource.getContent();
        return Result.success(Map.of("content", exportContent, "title", resource.getTitle()));
    }

    // ==================== AI 生成资源 ====================

    private static final String RESOURCE_GEN_PROMPT = """
        你是资深C语言AI导师。根据学生错题和薄弱环节，生成5篇针对性强、高质量的个性化导学资料。

        ════════════════════════════════════════════════════════════════
        【学生数据】
        ════════════════════════════════════════════════════════════════
        📌 错题涉及知识点：%s
        ⚠️ 薄弱环节：%s

        ════════════════════════════════════════════════════════════════
        【生成策略】
        ════════════════════════════════════════════════════════════════
        1. 针对性优先：紧扣错题知识点和薄弱环节，不要泛泛而谈
        2. 循序渐进：从基础概念 → 常见错误 → 正确用法 → 实战练习
        3. 错误驱动：分析典型错误原因，给出正确写法对比
        4. 实践导向：代码示例必须完整可运行，配套练习巩固
        5. 内容丰富：每篇至少500字，代码示例要详细，解释要清晰

        ════════════════════════════════════════════════════════════════
        【输出格式】
        ════════════════════════════════════════════════════════════════
        生成5篇资料，类型依次为：article / explanation / mindmap / code / summary

        每篇资料严格包含：
        {
          "title": "针对性标题（如：XX易错点解析与实战）",
          "type": "article|explanation|mindmap|code|summary",
          "content": "Markdown正文（见下方要求）",
          "knowledgePoint": "知识点",
          "difficulty": "easy|medium|hard"
        }

        📝 各类型详细要求：

        【article - 知识讲解】(至少800字)
        - ## 问题引入（为什么这个知识点重要，实际应用场景）
        - ## 核心概念（清晰定义 + 内存模型图解 + 代码示例）
        - ## 详细用法（多种使用方式 + 对比表格）
        - ## 常见错误（错误写法 vs 正确写法对比 + 原因分析）
        - ## 最佳实践（推荐用法 + 注意事项 + 性能考虑）
        - ## 小结与练习（3-5道练习题）

        【explanation - 深度解析】(至少800字)
        - ## 概念辨析（易混淆概念对比表格）
        - ## 原理剖析（为什么这样设计 + 底层实现）
        - ## 代码对比（正确 vs 错误代码示例 + 逐行解释）
        - ## 常见陷阱（初学者容易踩的坑 + 解决方案）
        - ## 进阶技巧（高级用法 + 性能优化）

        【mindmap - 知识图谱】
        - ## 知识点总览（主题 → 子主题 → 具体内容）
        - ## 关联关系（与其他知识点的联系）
        - ## 学习路径（建议的学习顺序）
        - ## 重点难点（标注重点和难点）
        - 使用HTML格式，便于前端渲染成卡片式思维导图

        【code - 代码实战】(完整可运行 + 详细注释)
        - 包含完整的 main 函数
        - 逐行详细注释（每行代码都要解释）
        - 多个代码示例（基础版 + 进阶版）
        - 运行结果说明
        - ## 代码解析（关键逻辑解释 + 内存变化图）
        - ## 扩展练习（3个改进建议）

        【summary - 学习总结】(至少600字)
        - ## 核心要点（用 - 列出，每个要点详细解释）
        - ## 知识框架（用表格整理）
        - ## 易错点回顾（用 ⚠️ 标注 + 解决方案）
        - ## 自测问题（5道检验掌握程度的问题）
        - ## 下一步学习建议（具体的学习计划）

        ════════════════════════════════════════════════════════════════
        【质量要求】
        ════════════════════════════════════════════════════════════════
        ✅ 代码必须100%正确，能直接编译运行
        ✅ 内容必须针对具体知识点，不要空泛
        ✅ 使用Markdown格式化（表格、代码块、列表、加粗）
        ✅ 每篇内容要充实，有深度，不是简单罗列
        ✅ 代码示例要完整，包含头文件和main函数
        ❌ 禁止错误的C语法
        ❌ 禁止大段纯文本
        ❌ 禁止内容过于简单或重复

        只输出纯JSON数组，不要```json标记，不要其他任何文字。
        """;

    @PostMapping("/generate/{studentId}")
    public Result<?> generate(@PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        try {
            // 从请求体提取筛选参数
            List<String> types = null;
            if (body != null && body.containsKey("types")) {
                types = ((List<?>) body.get("types")).stream()
                        .map(Object::toString).toList();
            }
            String chapter = body != null ? (String) body.get("chapter") : null;
            String difficulty = body != null ? (String) body.get("difficulty") : null;

            log.info("开始生成资源: studentId={}, types={}, chapter={}, difficulty={}", studentId, types, chapter, difficulty);
            // 使用AutoResourceGenerator统一生成路径，确保经过完整的四道门防幻觉机制
            // 传入 chapter 和 difficulty，使生成结果与用户筛选条件一致
            List<LearningResource> generated = autoResourceGenerator.autoGenerate(studentId, types, chapter, difficulty);

            if (generated == null || generated.isEmpty()) {
                log.warn("资源生成返回空: studentId={}", studentId);
                return Result.error("资源生成失败，请稍后重试");
            }

            log.info("AI资源生成完成: studentId={}, count={}", studentId, generated.size());
            return Result.success(generated);
        } catch (Exception e) {
            log.error("AI资源生成失败: error={}", e.getMessage(), e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 为每个章节生成全套资源（article/explanation/mindmap/code/summary各一份）
     */
    @PostMapping("/generate-all-chapters/{studentId}")
    public Result<?> generateAllChapterResources(@PathVariable Long studentId) {
        try {
            List<LearningResource> generated = autoResourceGenerator.generateAllChapterResources(studentId);
            if (generated.isEmpty()) {
                return Result.error("资源生成失败，请稍后重试");
            }
            log.info("全量章节资源生成完成: studentId={}, count={}", studentId, generated.size());
            return Result.success(Map.of("count", generated.size(), "resources", generated));
        } catch (Exception e) {
            log.error("全量章节资源生成失败", e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取今日艾宾浩斯复习资源
     * 返回当前需要复习的知识点和资源列表
     */
    @GetMapping("/review-today/{sysUserId}")
    public Result<Map<String, Object>> getReviewToday(@PathVariable Long sysUserId) {
        try {
            var studentOpt = studentRepo.findBySysUserId(sysUserId);
            if (studentOpt.isEmpty()) {
                return Result.success(Map.of("hasReview", false));
            }
            Long studentId = studentOpt.get().getId();

            // 获取艾宾浩斯复习资源（直接查询，无需全量加载）
            List<LearningResource> reviewResources = resourceRepo.findByStudentIdAndGeneratedByOrderByCreatedAtDesc(studentId, "ebbinghaus-review");

            if (reviewResources.isEmpty()) {
                return Result.success(Map.of(
                        "hasReview", false,
                        "message", "暂无复习任务"
                ));
            }

            // 获取最新的复习资源（今日推荐）
            LearningResource latestReview = reviewResources.get(0);

            // 提取复习天数
            int reviewDay = 1;
            if (latestReview.getTags() != null && latestReview.getTags().contains("第")) {
                try {
                    String tag = latestReview.getTags();
                    int start = tag.indexOf("第") + 1;
                    int end = tag.indexOf("天");
                    if (end > start) {
                        reviewDay = Integer.parseInt(tag.substring(start, end));
                    }
                } catch (Exception e) {
                    reviewDay = 1;
                }
            }

            // 提取知识点
            Set<String> knowledgePoints = new LinkedHashSet<>();
            reviewResources.stream()
                    .limit(5)
                    .forEach(r -> {
                        if (r.getKnowledgePoint() != null) {
                            knowledgePoints.add(r.getKnowledgePoint());
                        }
                    });

            // 计算下一个复习节点
            int nextReviewDay = 15;
            for (int interval : EBBINGHAUS_INTERVALS) {
                if (interval > reviewDay) {
                    nextReviewDay = interval;
                    break;
                }
            }

            // 计算复习进度（已完成的节点数 / 总节点数）
            int completedNodes = 0;
            for (int interval : EBBINGHAUS_INTERVALS) {
                if (interval <= reviewDay) {
                    completedNodes++;
                }
            }
            double progress = (double) completedNodes / EBBINGHAUS_INTERVALS.length * 100;

            Map<String, Object> result = new HashMap<>();
            result.put("hasReview", true);
            result.put("reviewDay", reviewDay);
            result.put("nextReviewDay", nextReviewDay);
            result.put("knowledgePoints", new ArrayList<>(knowledgePoints));
            result.put("progress", Math.round(progress));
            result.put("completedNodes", completedNodes);
            result.put("totalNodes", EBBINGHAUS_INTERVALS.length);
            result.put("resources", reviewResources.stream().limit(3).toList());

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取复习资源失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取今日推荐资源（最新生成的资源）
     */
    @GetMapping("/today-recommend/{sysUserId}")
    public Result<List<LearningResource>> getTodayRecommend(@PathVariable Long sysUserId) {
        try {
            var studentOpt = studentRepo.findBySysUserId(sysUserId);
            if (studentOpt.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            Long studentId = studentOpt.get().getId();

            // 获取最新生成的资源（包含课件PPT）
            List<LearningResource> resources = resourceRepo.findRecentExcluding(
                    studentId, List.of("course-video"),
                    org.springframework.data.domain.PageRequest.of(0, 10));

            return Result.success(resources);
        } catch (Exception e) {
            log.error("获取今日推荐失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 初始化B站课程视频（学生首次进入资源中心时调用）
     * 课程：浙江大学 C语言程序设计 — 中国大学MOOC
     */
    @PostMapping("/student/{studentId}/init-course")
    public Result<?> initCourseResources(@PathVariable Long studentId) {
        // 已有课程资源则跳过
        long existing = resourceRepo.countByStudentIdAndGeneratedBy(studentId, "course-video");
        if (existing > 0) {
            return Result.success("课程资源已存在");
        }

        List<LearningResource> resources = buildMoocCourseResources(studentId);
        resourceRepo.saveAll(resources);
        log.info("初始化浙大MOOC课程资源: studentId={}, count={}", studentId, resources.size());
        return Result.success("已初始化 " + resources.size() + " 条课程资源");
    }

    /**
     * B站视频代理 — 后端调B站API获取高清视频直链，绕过前端CORS限制
     * 视频流从B站CDN直达用户，不经过我们的服务器，不存储任何内容
     */
    @GetMapping("/bilibili/playurl")
    public Result<Map<String, Object>> proxyBilibiliPlayUrl(
            @RequestParam String bvid,
            @RequestParam(defaultValue = "1") int p) {
        try {
            // 1. 获取视频基本信息（含cid）
            Request infoReq = new Request.Builder()
                    .url("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.bilibili.com")
                    .build();
            try (Response infoResp = biliHttpClient.newCall(infoReq).execute()) {
                if (!infoResp.isSuccessful()) {
                    return Result.error("B站API请求失败: " + infoResp.code());
                }
                String infoBody = infoResp.body() != null ? infoResp.body().string() : "{}";
                JSONObject infoJson = JSON.parseObject(infoBody);
                if (infoJson.getIntValue("code") != 0) {
                    return Result.error("视频不存在或不可访问");
                }
                JSONObject infoData = infoJson.getJSONObject("data");
                if (infoData == null) {
                    return Result.error("B站视频信息为空");
                }
                JSONArray pages = infoData.getJSONArray("pages");
                if (pages == null || pages.isEmpty()) {
                    return Result.error("视频分P信息为空");
                }
                // p从1开始, pages数组从0开始
                int idx = Math.min(Math.max(p - 1, 0), pages.size() - 1);
                JSONObject pageObj = pages.getJSONObject(idx);
                if (pageObj == null) {
                    return Result.error("视频分P不存在");
                }
                long cid = pageObj.getLongValue("cid");

                // 2. 获取播放地址（qn=116 即1080P+高码率）
                String playUrl = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid
                        + "&cid=" + cid + "&qn=116&type=&platform=web";
                Request playReq = new Request.Builder()
                        .url(playUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "https://www.bilibili.com")
                        .build();
                try (Response playResp = biliHttpClient.newCall(playReq).execute()) {
                    if (!playResp.isSuccessful()) {
                        return Result.error("B站播放地址获取失败: " + playResp.code());
                    }
                    String playBody = playResp.body() != null ? playResp.body().string() : "{}";
                    JSONObject playJson = JSON.parseObject(playBody);
                    if (playJson.getIntValue("code") != 0) {
                        return Result.error("B站播放地址API返回错误: " + playJson.getString("message"));
                    }
                    JSONObject playData = playJson.getJSONObject("data");
                    if (playData == null) {
                        return Result.error("B站播放地址数据为空");
                    }
                    JSONArray durl = playData.getJSONArray("durl");
                    if (durl == null || durl.isEmpty()) {
                        return Result.error("未找到可用视频流");
                    }
                    JSONObject firstDurl = durl.getJSONObject(0);
                    if (firstDurl == null) {
                        return Result.error("视频流数据格式异常");
                    }

                    // 取最高画质的视频地址
                    String bestUrl = firstDurl.getString("url");
                    // 获取各清晰度信息
                    JSONArray acceptQuality = playData.getJSONArray("accept_description");
                    String qualityDesc = acceptQuality != null && !acceptQuality.isEmpty()
                            ? acceptQuality.getString(0) : "";

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("url", bestUrl);
                    result.put("quality", qualityDesc);
                    result.put("bvid", bvid);
                    result.put("p", p);
                    return Result.success(result);
                }
            }
        } catch (Exception e) {
            log.error("B站视频代理失败: bvid={}, p={}, error={}", bvid, p, e.getMessage());
            return Result.error("获取视频地址失败: " + e.getMessage());
        }
    }

    /**
     * 构建 Bilibili C语言课程资源（黑马程序员 + 翁恺）
     * 构建 Bilibili C语言课程资源（黑马程序员 + 翁恺）
     */
    private List<LearningResource> buildMoocCourseResources(Long studentId) {
        String bilibiliBase = "https://www.bilibili.com/video/BV1dr4y1n7vA/?p=";
        Object[][] chapters = {
            // {标题, 集数p, 知识点, 难度, 章节}
            {"C语言简史与概述", 1, "C语言概述", "easy", "ch1"},
            {"第一个C程序：Hello World", 7, "Hello World", "easy", "ch1"},
            {"变量定义与赋值", 12, "变量", "easy", "ch2"},
            {"scanf输入与printf输出", 14, "scanf", "easy", "ch2"},
            {"算术运算符与表达式", 17, "表达式", "easy", "ch2"},
            {"if语句：条件判断", 25, "if语句", "easy", "ch3"},
            {"if-else与嵌套判断", 28, "if-else", "medium", "ch3"},
            {"switch多路分支", 32, "switch", "medium", "ch3"},
            {"while循环基础", 34, "while循环", "easy", "ch4"},
            {"do-while循环", 35, "do-while", "easy", "ch4"},
            {"for循环与应用", 39, "for循环", "easy", "ch5"},
            {"break与continue控制", 41, "break/continue", "medium", "ch5"},
            {"一维数组入门", 75, "数组概念", "easy", "ch7"},
            {"二维数组", 80, "二维数组", "medium", "ch7"},
            {"函数定义与调用", 69, "函数定义", "easy", "ch7"},
            {"函数参数与返回值", 72, "函数参数", "medium", "ch7"},
            {"取地址运算与指针概念", 81, "取地址", "medium", "ch8"},
            {"指针变量的使用", 82, "指针概念", "hard", "ch8"},
            {"指针与数组", 84, "指针与数组", "hard", "ch8"},
            {"动态内存分配malloc", 87, "malloc", "hard", "ch8"},
            {"字符串基础", 88, "字符串概念", "medium", "ch8"},
            {"字符串函数strlen/strcpy/strcat", 93, "strcpy", "medium", "ch8"},
        };

        List<LearningResource> list = new ArrayList<>();
        for (Object[] ch : chapters) {
            String title = (String) ch[0];
            int episode = (int) ch[1];
            String kp = (String) ch[2];
            String diff = (String) ch[3];
            String chapter = (String) ch[4];

            // B站视频资源
            LearningResource video = new LearningResource();
            video.setTitle("📺 " + title);
            video.setType("course-video");
            video.setContent(bilibiliBase + episode);
            video.setKnowledgePoint(kp);
            video.setDifficulty(diff);
            video.setStudentId(studentId);
            video.setGeneratedBy("course-video");
            video.setCategory("翁恺C语言");
            video.setTags(chapter + "·" + kp);
            video.setShared(true);
            list.add(video);
        }
        return list;
    }

    // ==================== 章节内容生成 ====================

    /**
     * 根据章节生成概念讲解和代码示例 — 基于知识库 + AI智能体
     */
    private static final String CHAPTER_CONTENT_PROMPT = """
        你是一个C语言教学专家。请根据以下章节主题，从知识库中提取相关内容，生成概念讲解和代码示例。

        章节主题：%s

        请生成以下内容（JSON格式）：
        {
          "concepts": [
            {
              "title": "概念标题",
              "content": "概念讲解内容（Markdown格式，包含定义、要点、示例说明）"
            }
          ],
          "codes": [
            {
              "title": "代码示例标题",
              "content": "完整可运行的C语言代码（用```c包裹）",
              "compileStatus": "PASSED"
            }
          ]
        }

        要求：
        1. 概念讲解要清晰易懂，适合初学者
        2. 代码示例要完整可运行，包含注释
        3. 内容要准确，基于C语言标准
        4. 每个部分2-3个条目即可
        5. 只输出纯JSON，不要```json标记

        知识库参考内容：
        %s
        """;

    @GetMapping("/student/{studentId}/chapter-content")
    public Result<Map<String, Object>> getChapterContent(
            @PathVariable Long studentId,
            @RequestParam String knowledgePoint) {
        try {
            // 1. 从知识库检索相关内容
            String query = knowledgePoint + " C语言";
            List<String> kbContents = new ArrayList<>();

            // 尝试从现有资源中查找相关内容（直接按知识点查询，无需全量加载）
            var resources = resourceRepo.findByStudentIdAndKnowledgePointContainingOrderByCreatedAtDesc(studentId, knowledgePoint);
            for (var r : resources) {
                if (r.getContent() != null && r.getContent().length() > 20) {
                    kbContents.add(r.getTitle() + ": " + r.getContent().substring(0, Math.min(500, r.getContent().length())));
                }
            }

            // 2. 构建知识库参考内容
            String kbRef = kbContents.isEmpty() ? "暂无相关知识库内容，请基于C语言标准知识生成。"
                : String.join("\n---\n", kbContents);

            // 3. 调用AI生成
            String prompt = String.format(CHAPTER_CONTENT_PROMPT, knowledgePoint, kbRef);
            String aiReply = sparkClient.chat("", prompt);

            if (aiReply == null || aiReply.trim().isEmpty()) {
                return Result.error("AI生成失败，请重试");
            }

            // 4. 解析JSON
            // 清理可能的markdown标记
            aiReply = aiReply.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JSONObject result = com.alibaba.fastjson2.JSON.parseObject(aiReply);

            if (result == null) {
                return Result.error("AI返回格式异常");
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("concepts", result.getJSONArray("concepts"));
            response.put("codes", result.getJSONArray("codes"));
            response.put("knowledgePoint", knowledgePoint);

            log.info("章节内容生成成功: knowledgePoint={}, concepts={}, codes={}",
                knowledgePoint,
                result.getJSONArray("concepts") != null ? result.getJSONArray("concepts").size() : 0,
                result.getJSONArray("codes") != null ? result.getJSONArray("codes").size() : 0);

            return Result.success(response);
        } catch (Exception e) {
            log.error("章节内容生成失败: knowledgePoint={}", knowledgePoint, e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    // ==================== 用户反馈闭环 ====================

    /**
     * 学生对资源提交反馈（有用/有误），反馈数据回写用于后续优化生成质量
     */
    @PostMapping("/{id}/feedback")
    public Result<?> submitFeedback(@PathVariable Long id, @RequestBody Map<String, String> body) {
        var resourceOpt = resourceRepo.findById(id);
        if (resourceOpt.isEmpty()) {
            return Result.error(404, "资源不存在");
        }
        LearningResource resource = resourceOpt.get();
        String type = body.getOrDefault("type", "").toUpperCase();
        if (!"USEFUL".equals(type) && !"ERROR".equals(type)) {
            return Result.error("反馈类型无效，必须为 USEFUL 或 ERROR");
        }

        resource.setFeedbackType(type);
        resource.setFeedbackNote(body.get("note"));

        // ERROR 反馈：触发资源重新评估标记
        if ("ERROR".equals(type)) {
            resource.setHallucinationRisk("HIGH");
            resource.setHallucinationScore(Math.min(resource.getHallucinationScore() != null ? resource.getHallucinationScore() : 0.5, 0.5));
            String existingNote = resource.getHallucinationNote() != null ? resource.getHallucinationNote() : "";
            resource.setHallucinationNote(existingNote + " | 用户反馈有误: " + body.getOrDefault("note", ""));
        }

        resourceRepo.save(resource);
        log.info("资源反馈: id={}, type={}, note={}", id, type, body.get("note"));
        return Result.success("反馈已记录");
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }

    // ===== AI 薄弱点思维导图 =====

    @GetMapping("/mindmap/{sysUserId}")
    public Result<Map<String, Object>> getMindMap(@PathVariable Long sysUserId) {
        Student s = studentRepo.findBySysUserId(sysUserId).orElse(null);
        if (s == null) return Result.error(404, "学生不存在，请先完成AI导学");

        String weakAreas = s.getWeakAreas() != null ? s.getWeakAreas() : "指针,数组,函数,结构体";
        int level = s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 30;

        String prompt = """
            你是C语言教学专家。学生薄弱环节：「%s」，知识水平 %d/100。
            请为这些薄弱知识点生成思维导图数据，严格输出JSON：
            {
              "central": "C语言知识体系",
              "branches": [
                {"topic":"知识点1","level":"weak/medium/strong","subtopics":["子概念1","子概念2","子概念3"],"tip":"一句话学习提示"},
                ...
              ]
            }
            每个薄弱环节对应一个branch，level按学生水平判断。
            每个branch的subtopics 3-4个关键子概念，tip一句话提示。
            只输出JSON，不要```json标记。
            """.formatted(weakAreas, level);

        try {
            String raw = sparkClient.chat(prompt, "思维导图生成", 0.3f, 2048);
            JSONObject mindmap = JsonParser.parseObject(raw);
            if (mindmap == null) return Result.error(500, "生成失败");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("central", mindmap.getString("central"));
            JSONArray branches = mindmap.getJSONArray("branches");
            if (branches != null) result.put("branches", branches);
            else result.put("branches", List.of());
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(500, "生成失败: " + e.getMessage());
        }
    }
}