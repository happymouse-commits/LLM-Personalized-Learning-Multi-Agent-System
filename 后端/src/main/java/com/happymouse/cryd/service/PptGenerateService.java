package com.happymouse.cryd.service;

import com.happymouse.cryd.service.spark.SparkClient;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * PPT生成服务 — 优先使用本地预置PPT文件，无匹配时降级为简单POI生成
 * 不再调用LLM生成PPT大纲（节省token，保证演示稳定性）
 */
@Service
public class PptGenerateService {

    private static final Logger log = LoggerFactory.getLogger(PptGenerateService.class);
    private static final String OUTPUT_DIR = "uploads/ppt";

    private final SparkClient sparkClient;
    private final Executor agentTaskExecutor;

    @Value("${system.storage.resources-path:../data/resources}")
    private String resourcesPath;

    public PptGenerateService(SparkClient sparkClient,
                              @Qualifier("agentTaskExecutor") Executor agentTaskExecutor) {
        this.sparkClient = sparkClient;
        this.agentTaskExecutor = agentTaskExecutor;

        // 确保输出目录存在
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            log.warn("创建PPT输出目录失败", e);
        }
    }

    /**
     * 根据主题生成PPT — 优先从本地预置文件中查找，无匹配时降级为简单POI生成
     */
    public String generatePpt(String topic, String style) throws Exception {
        log.info("开始生成PPT: topic={}, style={}", topic, style);

        // 1. 优先从本地预置PPT文件中查找
        String localPpt = findLocalPpt(topic);
        if (localPpt != null) {
            log.info("使用本地预置PPT文件: {}", localPpt);
            return localPpt;
        }

        // 2. 无预置文件，降级为简单POI生成（不调用LLM）
        log.info("无预置PPT文件，使用简单POI生成: topic={}", topic);
        List<SlideContent> slides = buildSimpleSlides(topic);
        String fileName = UUID.randomUUID().toString() + ".pptx";
        String outputPath = OUTPUT_DIR + "/" + fileName;
        createPptFile(slides, topic, outputPath);
        log.info("PPT文件生成完成: {}", outputPath);

        return "/api/ppt/download/" + fileName;
    }

    /**
     * 从本地预置目录查找匹配的PPT文件
     * 返回下载URL，或null表示未找到
     */
    private String findLocalPpt(String topic) {
        try {
            Path resourcesDir = Paths.get(resourcesPath);
            if (!resourcesDir.isAbsolute()) {
                String userDir = System.getProperty("user.dir");
                Path absolutePath = Paths.get(userDir, resourcesPath).normalize();
                if (Files.exists(absolutePath)) {
                    resourcesDir = absolutePath;
                }
            }

            if (!Files.isDirectory(resourcesDir)) return null;

            // 按关键词匹配本地PPT文件
            String keyword = extractKeyword(topic);
            List<Path> pptFiles;
            try (var stream = Files.list(resourcesDir)) {
                pptFiles = stream
                    .filter(f -> f.toString().endsWith(".pptx"))
                    .filter(f -> f.getFileName().toString().contains(keyword))
                    .collect(Collectors.toList());
            }

            if (!pptFiles.isEmpty()) {
                // 返回第一个匹配的PPT文件下载URL
                String fileName = pptFiles.get(0).getFileName().toString();
                return "/api/ppt/download/" + fileName;
            }

            // 没有精确匹配，尝试任何PPT文件
            try (var stream = Files.list(resourcesDir)) {
                var any = stream.filter(f -> f.toString().endsWith(".pptx")).findFirst();
                if (any.isPresent()) {
                    String fileName = any.get().getFileName().toString();
                    return "/api/ppt/download/" + fileName;
                }
            }
        } catch (Exception e) {
            log.warn("查找本地PPT文件失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从主题中提取关键词，用于匹配PPT文件名
     */
    private String extractKeyword(String topic) {
        if (topic == null) return "";
        // 常见章节关键词映射
        String[][] mappings = {
            {"基础语法", "基础语法"}, {"数据类型", "基础语法"}, {"变量", "基础语法"},
            {"流程控制", "流程控制"}, {"控制结构", "流程控制"}, {"循环", "流程控制"},
            {"数组", "数组与指针"}, {"指针", "数组与指针"},
            {"函数", "基础语法"}, {"结构体", "数组与指针"},
            {"文件", "数组与指针"}, {"内存", "数组与指针"},
        };
        for (String[] m : mappings) {
            if (topic.contains(m[0])) return m[1];
        }
        return topic;
    }

    /**
     * 构建简单的幻灯片内容（不调用LLM）
     */
    private List<SlideContent> buildSimpleSlides(String topic) {
        List<SlideContent> slides = new ArrayList<>();

        SlideContent cover = new SlideContent();
        cover.title = topic;
        cover.subtitle = "C语言学习课件";
        cover.points = new ArrayList<>();
        slides.add(cover);

        SlideContent content = new SlideContent();
        content.title = "核心概念";
        content.points = List.of("理解基本概念和原理", "掌握关键语法和用法", "注意常见错误和陷阱", "通过实践巩固知识");
        slides.add(content);

        SlideContent summary = new SlideContent();
        summary.title = "总结";
        summary.points = List.of("回顾本节要点", "完成课后练习", "预习下一节内容");
        slides.add(summary);

        return slides;
    }

    /**
     * 根据内容直接生成PPT（无需LLM）
     */
    public String generatePptFromContent(String title, String content) throws Exception {
        // 先尝试从本地查找
        String localPpt = findLocalPpt(title);
        if (localPpt != null) return localPpt;

        List<SlideContent> slides = parseMarkdownContent(content);
        String fileName = UUID.randomUUID().toString() + ".pptx";
        String outputPath = OUTPUT_DIR + "/" + fileName;
        createPptFile(slides, title, outputPath);
        return "/api/ppt/download/" + fileName;
    }

    /**
     * 异步生成PPT，返回任务ID和结果查询URL
     */
    public String generatePptAsync(String topic, String style) {
        String taskId = UUID.randomUUID().toString();
        agentTaskExecutor.execute(() -> {
            try {
                String url = generatePpt(topic, style);
                asyncResults.put(taskId, url);
                log.info("异步PPT生成完成: task={}, url={}", taskId, url);
            } catch (Exception e) {
                asyncResults.put(taskId, "ERROR:" + e.getMessage());
                log.error("异步PPT生成失败: task={}", taskId, e);
            }
        });
        return taskId;
    }

    /**
     * 查询异步生成结果
     * @return URL字符串，null表示还在生成中，"ERROR:xxx"表示失败
     */
    public String getAsyncResult(String taskId) {
        return asyncResults.get(taskId);
    }

    private final java.util.concurrent.ConcurrentHashMap<String, String> asyncResults = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 生成PPT大纲（已弃用LLM调用，保留方法体供后续可能恢复）
     */
    @Deprecated
    private String generateOutlineLegacy(String topic, String style) {
        String prompt = "你是一个PPT大纲生成专家。请为以下主题生成一个PPT大纲。\n\n" +
            "要求：\n" +
            "1. 包含封面页（标题+副标题）\n" +
            "2. 3-8个内容页，每页有标题和2-4个要点\n" +
            "3. 包含总结页\n" +
            "4. 用Markdown格式输出\n" +
            "5. 风格：" + (style != null ? style : "简洁专业") + "\n\n" +
            "格式示例：\n" +
            "# 封面\n" +
            "标题：xxx\n" +
            "副标题：xxx\n\n" +
            "# 第一章标题\n" +
            "- 要点1\n" +
            "- 要点2\n" +
            "- 要点3\n\n" +
            "# 第二章标题\n" +
            "- 要点1\n" +
            "- 要点2\n\n" +
            "# 总结\n" +
            "- 总结点1\n" +
            "- 总结点2\n\n" +
            "主题：" + topic;

        return sparkClient.chatPro(prompt, topic);
    }

    /**
     * 解析LLM输出的大纲
     */
    private List<SlideContent> parseOutline(String outline) {
        List<SlideContent> slides = new ArrayList<>();
        String[] lines = outline.split("\n");
        SlideContent currentSlide = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 匹配标题行 (# xxx)
            if (line.startsWith("# ")) {
                if (currentSlide != null) {
                    slides.add(currentSlide);
                }
                currentSlide = new SlideContent();
                currentSlide.title = line.substring(2).trim();
                currentSlide.points = new ArrayList<>();
            }
            // 匹配要点行 (- xxx)
            else if (line.startsWith("- ") || line.startsWith("* ")) {
                if (currentSlide != null) {
                    String point = line.substring(2).trim();
                    if (!point.isEmpty()) {
                        currentSlide.points.add(point);
                    }
                }
            }
            // 匹配标题行格式：标题：xxx
            else if (line.startsWith("标题：") || line.startsWith("标题:")) {
                if (currentSlide == null) {
                    currentSlide = new SlideContent();
                    currentSlide.points = new ArrayList<>();
                }
                currentSlide.title = line.replace("标题：", "").replace("标题:", "").trim();
            }
            // 匹配副标题行
            else if (line.startsWith("副标题：") || line.startsWith("副标题:")) {
                if (currentSlide != null) {
                    currentSlide.subtitle = line.replace("副标题：", "").replace("副标题:", "").trim();
                }
            }
        }

        // 添加最后一个幻灯片
        if (currentSlide != null) {
            slides.add(currentSlide);
        }

        // 如果没有解析出内容，创建一个默认的
        if (slides.isEmpty()) {
            SlideContent defaultSlide = new SlideContent();
            defaultSlide.title = "演示文稿";
            defaultSlide.points = List.of("内容生成中...");
            slides.add(defaultSlide);
        }

        return slides;
    }

    /**
     * 解析Markdown内容
     */
    private List<SlideContent> parseMarkdownContent(String content) {
        List<SlideContent> slides = new ArrayList<>();
        String[] lines = content.split("\n");
        SlideContent currentSlide = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("## ")) {
                if (currentSlide != null) {
                    slides.add(currentSlide);
                }
                currentSlide = new SlideContent();
                currentSlide.title = line.substring(3).trim();
                currentSlide.points = new ArrayList<>();
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                if (currentSlide != null) {
                    String point = line.substring(2).trim();
                    if (!point.isEmpty()) {
                        currentSlide.points.add(point);
                    }
                }
            }
        }

        if (currentSlide != null) {
            slides.add(currentSlide);
        }

        return slides;
    }

    /**
     * 创建PPT文件
     */
    private void createPptFile(List<SlideContent> slides, String title, String outputPath) throws IOException {
        XMLSlideShow ppt = new XMLSlideShow();
        ppt.setPageSize(new Dimension(914, 685)); // 16:9 比例

        // 创建封面页
        createCoverSlide(ppt, title, slides.isEmpty() ? "" : slides.get(0).subtitle);

        // 创建内容页
        for (int i = 0; i < slides.size(); i++) {
            SlideContent slide = slides.get(i);
            if (i == 0 && slide.subtitle != null) {
                // 第一页如果有副标题，跳过（已在封面显示）
                continue;
            }
            createContentSlide(ppt, slide);
        }

        // 创建总结页（如果没有）
        if (slides.isEmpty() || !slides.get(slides.size() - 1).title.contains("总结")) {
            createSummarySlide(ppt, title);
        }

        // 保存文件
        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            ppt.write(out);
        }
    }

    /**
     * 创建封面幻灯片
     */
    private void createCoverSlide(XMLSlideShow ppt, String title, String subtitle) {
        XSLFSlide slide = ppt.createSlide();

        // 设置标题
        XSLFTextShape titleShape = slide.getPlaceholder(0);
        if (titleShape != null) {
            titleShape.clearText();
            XSLFTextParagraph para = titleShape.addNewTextParagraph();
            XSLFTextRun run = para.addNewTextRun();
            run.setText(title);
            run.setFontSize(36.0);
            run.setBold(true);
            run.setFontColor(new Color(0x33, 0x33, 0x33));
        }

        // 设置副标题
        if (subtitle != null && !subtitle.isEmpty()) {
            XSLFTextShape subtitleShape = slide.getPlaceholder(1);
            if (subtitleShape != null) {
                subtitleShape.clearText();
                XSLFTextParagraph para = subtitleShape.addNewTextParagraph();
                XSLFTextRun run = para.addNewTextRun();
                run.setText(subtitle);
                run.setFontSize(18.0);
                run.setFontColor(new Color(0x66, 0x66, 0x66));
            }
        }
    }

    /**
     * 创建内容幻灯片
     */
    private void createContentSlide(XMLSlideShow ppt, SlideContent content) {
        XSLFSlide slide = ppt.createSlide();

        // 设置标题
        XSLFTextShape titleShape = slide.getPlaceholder(0);
        if (titleShape != null) {
            titleShape.clearText();
            XSLFTextParagraph para = titleShape.addNewTextParagraph();
            XSLFTextRun run = para.addNewTextRun();
            run.setText(content.title);
            run.setFontSize(28.0);
            run.setBold(true);
            run.setFontColor(new Color(0x1a, 0x56, 0xdb));
        }

        // 设置内容
        XSLFTextShape contentShape = slide.getPlaceholder(1);
        if (contentShape != null) {
            contentShape.clearText();
            for (int i = 0; i < content.points.size(); i++) {
                XSLFTextParagraph para = contentShape.addNewTextParagraph();
                para.setIndentLevel(0);

                XSLFTextRun run = para.addNewTextRun();
                run.setText("• " + content.points.get(i));
                run.setFontSize(18.0);
                run.setFontColor(new Color(0x33, 0x33, 0x33));
            }
        }
    }

    /**
     * 创建总结幻灯片
     */
    private void createSummarySlide(XMLSlideShow ppt, String title) {
        XSLFSlide slide = ppt.createSlide();

        XSLFTextShape titleShape = slide.getPlaceholder(0);
        if (titleShape != null) {
            titleShape.clearText();
            XSLFTextParagraph para = titleShape.addNewTextParagraph();
            XSLFTextRun run = para.addNewTextRun();
            run.setText("总结");
            run.setFontSize(36.0);
            run.setBold(true);
            run.setFontColor(new Color(0x1a, 0x56, 0xdb));
        }

        XSLFTextShape subtitleShape = slide.getPlaceholder(1);
        if (subtitleShape != null) {
            subtitleShape.clearText();
            XSLFTextParagraph para = subtitleShape.addNewTextParagraph();
            XSLFTextRun run = para.addNewTextRun();
            run.setText("感谢观看");
            run.setFontSize(24.0);
            run.setFontColor(new Color(0x66, 0x66, 0x66));
        }
    }

    /**
     * 幻灯片内容数据类
     */
    private static class SlideContent {
        String title;
        String subtitle;
        List<String> points;
    }
}
