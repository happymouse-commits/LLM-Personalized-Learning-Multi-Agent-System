package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.service.PptGenerateService;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;

import java.io.File;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

/**
 * PPT生成控制器
 */
@RestController
@RequestMapping("/api/ppt")
public class PptController {

    private static final Logger log = LoggerFactory.getLogger(PptController.class);
    private final PptGenerateService pptService;

    @Value("${system.storage.resources-path:../data/resources}")
    private String resourcesPath;

    public PptController(PptGenerateService pptService) {
        this.pptService = pptService;
    }

    /** 查找项目根目录（包含 pom.xml 或 frontend 的目录） */
    private String findProjectRoot() {
        // 优先用环境变量
        String env = System.getenv("PROJECT_ROOT");
        if (env != null && new File(env).exists()) return env;

        // 从用户目录向上找
        String userDir = System.getProperty("user.dir");
        File dir = new File(userDir);
        for (int i = 0; i < 5; i++) {
            if (new File(dir, "pom.xml").exists() || new File(dir, "frontend").exists()) {
                return dir.getAbsolutePath();
            }
            dir = dir.getParentFile();
            if (dir == null) break;
        }
        // 兜底
        return userDir;
    }

    /**
     * 根据主题生成PPT
     */
    @PostMapping("/generate")
    public Result<Map<String, String>> generatePpt(@RequestBody Map<String, String> request) {
        String topic = request.get("topic");
        String style = request.getOrDefault("style", "简洁专业");

        if (topic == null || topic.isBlank()) {
            return Result.error("请输入PPT主题");
        }

        try {
            String downloadUrl = pptService.generatePpt(topic, style);
            return Result.success(Map.of(
                "url", downloadUrl,
                "message", "PPT生成成功"
            ));
        } catch (Exception e) {
            log.error("PPT生成失败: topic={}", topic, e);
            return Result.error("PPT生成失败: " + e.getMessage());
        }
    }

    /**
     * 根据Markdown内容生成PPT
     */
    @PostMapping("/generate-from-content")
    public Result<Map<String, String>> generateFromContent(@RequestBody Map<String, String> request) {
        String title = request.getOrDefault("title", "演示文稿");
        String content = request.get("content");

        if (content == null || content.isBlank()) {
            return Result.error("请输入内容");
        }

        try {
            String downloadUrl = pptService.generatePptFromContent(title, content);
            return Result.success(Map.of(
                "url", downloadUrl,
                "message", "PPT生成成功"
            ));
        } catch (Exception e) {
            log.error("PPT生成失败: title={}", title, e);
            return Result.error("PPT生成失败: " + e.getMessage());
        }
    }

    /**
     * 解析 resources 目录（存放 .pptx 源文件）。
     * 优先级：绝对 resourcesPath > 基于项目根的相对路径 > 运行目录/../data/resources。
     */
    private Path resolveResourcesDir() {
        Path p = Paths.get(resourcesPath);
        if (p.isAbsolute()) return p.normalize();
        String envData = System.getenv("DATA_DIR");
        if (envData != null && !envData.isBlank()) {
            Path envRes = Paths.get(envData, "resources").normalize();
            if (Files.isDirectory(envRes)) return envRes;
        }
        String root = findProjectRoot();
        return Paths.get(root, resourcesPath).normalize().toAbsolutePath();
    }

    /**
     * 解析 data 根目录（resources 的父目录，同时也是 xxxppt课件 预渲染目录的父目录）。
     * 多候选兜底：DATA_DIR 环境变量 > resources父目录 > projectRoot/../data。
     */
    private List<Path> resolveDataDirCandidates() {
        List<Path> candidates = new ArrayList<>();
        String envData = System.getenv("DATA_DIR");
        if (envData != null && !envData.isBlank()) candidates.add(Paths.get(envData).toAbsolutePath().normalize());
        Path resDir = resolveResourcesDir();
        if (resDir.getParent() != null) candidates.add(resDir.getParent().normalize());
        candidates.add(Paths.get(findProjectRoot(), "../data").normalize().toAbsolutePath());
        candidates.add(Paths.get(findProjectRoot(), "data").normalize().toAbsolutePath());
        candidates.add(Paths.get(System.getProperty("user.dir"), "../data").normalize().toAbsolutePath());
        return candidates;
    }

    /**
     * 查找 .pptx 源文件。按顺序在 resources 目录、uploads/ppt、data/resources 中查找。
     */
    private File findPptxFile(String fileName) {
        Path resDir = resolveResourcesDir();
        File f = resDir.resolve(fileName).toFile();
        if (f.exists()) return f;
        String root = findProjectRoot();
        f = new File(root, "uploads/ppt/" + fileName);
        if (f.exists()) return f;
        f = new File(root, "data/resources/" + fileName);
        if (f.exists()) return f;
        // 兜底：在所有 data 候选目录的 resources 子目录里找
        for (Path cand : resolveDataDirCandidates()) {
            f = cand.resolve("resources/" + fileName).toFile();
            if (f.exists()) return f;
        }
        return null;
    }

    /**
     * 下载PPT文件
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadPpt(@PathVariable String fileName) {
        try {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                log.warn("拒绝非法PPT文件名: {}", fileName);
                return ResponseEntity.badRequest().build();
            }
            File file = findPptxFile(fileName);
            if (file == null) return ResponseEntity.notFound().build();

            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8") + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
        } catch (Exception e) {
            log.error("下载PPT失败: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * PPT转图片 — 把每页幻灯片渲染成PNG，返回图片URL列表
     */
    @GetMapping("/slides/{fileName}")
    public Result<List<String>> pptToImages(@PathVariable String fileName) {
        try {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                log.warn("拒绝非法PPT文件名: {}", fileName);
                return Result.error("非法文件名");
            }

            // 1) 优先使用预渲染幻灯片目录（PowerPoint 导出版，不依赖 POI 渲染）
            File preRenderedDir = findPreRenderedSlidesDir(fileName);
            if (preRenderedDir != null) {
                File[] images = preRenderedDir.listFiles(
                    (d, n) -> n.startsWith("幻灯片") && n.toLowerCase().endsWith(".png"));
                if (images != null && images.length > 0) {
                    Arrays.sort(images, Comparator.comparingInt(f -> extractSlideNum(f.getName())));
                    List<String> urls = new ArrayList<>();
                    for (int i = 0; i < images.length; i++) {
                        urls.add("/api/ppt/slides/" + fileName + "/slide-" + (i + 1) + ".png");
                    }
                    log.info("使用预渲染幻灯片: {} -> {} ({}页)", fileName, preRenderedDir, images.length);
                    return Result.success(urls);
                }
                log.warn("预渲染目录存在但无幻灯片图片: {}", preRenderedDir);
            }

            // 2) 查找 .pptx 源文件
            File pptFile = findPptxFile(fileName);
            if (pptFile == null) {
                String looked = resolveResourcesDir() + "/" + fileName;
                return Result.error("PPT文件不存在，已尝试: " + looked);
            }

            // 3) 缓存目录：按 PPT 文件 hash 子目录，避免中文路径替换问题
            Path cacheDir = resolvePptCacheDir(fileName);
            Files.createDirectories(cacheDir);

            // 4) 已有缓存直接返回
            List<String> urls = new ArrayList<>();
            File[] cached = cacheDir.toFile().listFiles((d, n) -> n.endsWith(".png"));
            if (cached != null && cached.length > 0) {
                Arrays.sort(cached, Comparator.comparingInt(f -> extractSlideNum(f.getName())));
                for (File img : cached) {
                    urls.add("/api/ppt/slides/" + fileName + "/" + img.getName());
                }
                log.debug("命中幻灯片缓存: {} ({}页)", fileName, cached.length);
                return Result.success(urls);
            }

            // 5) POI 渲染（仅在预渲染与缓存均不存在时触发）
            log.info("开始POI渲染PPT: {}", pptFile);
            try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(pptFile.toPath()))) {
                Dimension pageSize = ppt.getPageSize();
                double scale = 2.0;
                int imgW = (int) (pageSize.getWidth() * scale);
                int imgH = (int) (pageSize.getHeight() * scale);

                int idx = 1;
                for (XSLFSlide slide : ppt.getSlides()) {
                    BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = img.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2d.scale(scale, scale);
                    // 在无头 Linux 上不设置前景色可能出现透明底，先画白底
                    g2d.setColor(Color.WHITE);
                    g2d.fillRect(0, 0, (int) pageSize.getWidth(), (int) pageSize.getHeight());
                    slide.draw(g2d);
                    g2d.dispose();

                    String imgName = "slide-" + idx + ".png";
                    File imgFile = cacheDir.resolve(imgName).toFile();
                    ImageIO.write(img, "png", imgFile);
                    urls.add("/api/ppt/slides/" + fileName + "/" + imgName);
                    idx++;
                }
            }
            log.info("POI渲染完成: {} -> {}页", fileName, urls.size());
            return Result.success(urls);
        } catch (HeadlessException he) {
            log.error("无头环境POI渲染失败(缺少X11/字体): {}", fileName, he);
            return Result.error("PPT渲染失败：服务器无头环境缺少图形库或中文字体");
        } catch (Exception e) {
            log.error("PPT转图片失败: {}", fileName, e);
            return Result.error("PPT转换失败，请检查服务器日志");
        }
    }

    /** 解析 PPT 图片缓存目录（data/ppt-cache/<hash>/） */
    private Path resolvePptCacheDir(String fileName) {
        String hash = Integer.toHexString(fileName.hashCode());
        for (Path cand : resolveDataDirCandidates()) {
            Path cacheParent = cand.resolve("ppt-cache");
            if (Files.isDirectory(cacheParent) || Files.exists(cacheParent.getParent())) {
                try {
                    Files.createDirectories(cacheParent);
                    return cacheParent.resolve(hash);
                } catch (Exception ignored) { }
            }
        }
        // 兜底：projectRoot 下
        return Paths.get(findProjectRoot(), "data/ppt-cache", hash);
    }

    /**
     * 提供已渲染的幻灯片图片
     */
    @GetMapping("/slides/{fileName}/{imageName}")
    public ResponseEntity<Resource> getSlideImage(@PathVariable String fileName, @PathVariable String imageName) {
        try {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")
                || imageName.contains("..") || imageName.contains("/") || imageName.contains("\\")) {
                log.warn("拒绝非法幻灯片路径参数: fileName={}, imageName={}", fileName, imageName);
                return ResponseEntity.badRequest().build();
            }

            // 1) 优先从预渲染目录加载（slide-N.png → 幻灯片N.png）
            File preRenderedDir = findPreRenderedSlidesDir(fileName);
            if (preRenderedDir != null) {
                int slideNum = extractSlideNum(imageName);
                if (slideNum > 0) {
                    File preRenderedFile = new File(preRenderedDir, "幻灯片" + slideNum + ".png");
                    if (preRenderedFile.exists()) {
                        return ResponseEntity.ok()
                            .contentType(MediaType.IMAGE_PNG)
                            .body(new FileSystemResource(preRenderedFile));
                    }
                }
            }

            // 2) 从缓存目录加载
            Path cacheDir = resolvePptCacheDir(fileName);
            Path imgPath = cacheDir.resolve(imageName);
            File file = imgPath.toFile();

            // 3) 缓存不存在 → 若PPT源存在则触发一次转换
            if (!file.exists()) {
                File pptFile = findPptxFile(fileName);
                if (pptFile != null) {
                    log.info("幻灯片缓存未命中，触发PPT转换: {}", fileName);
                    Result<List<String>> r = pptToImages(fileName);
                    if (r != null && r.getCode() == 0) {
                        file = imgPath.toFile();
                    }
                }
            }

            if (!file.exists()) {
                log.debug("幻灯片未找到: fileName={} imageName={} cacheDir={}", fileName, imageName, cacheDir);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
        } catch (Exception e) {
            log.warn("获取幻灯片图片失败: fileName={} imageName={}", fileName, imageName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 校验文件名是否安全：白名单 `[a-zA-Z0-9._-]`，禁止路径分隔符与 ..
     */
    private static boolean isSafeFileName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains("..") || name.contains("/") || name.contains("\\")) return false;
        // 仅允许字母、数字、点、下划线、连字符
        return name.matches("^[a-zA-Z0-9._-]+$");
    }

    /**
     * 查找预渲染幻灯片目录（PowerPoint 导出 PNG，质量比 POI 渲染高）。
     * 文件名约定：xxx_课件_xxx.pptx → <data>/xxxppt课件/幻灯片N.png
     * 例：流程控制_课件_讯飞.pptx → data/流程控制ppt课件/幻灯片1.png
     * <p>
     * 匹配优先级（针对每个 data 根候选目录）：
     *   1) chapter + "ppt课件" 精确匹配
     *   2) chapter + "PPT课件" 大写后缀匹配（目录命名大小写不一致）
     *   3) 目录名以 chapter 开头（忽略大小写），如 "流程控制-正式版课件"
     *   4) 目录名包含 chapter 关键词（忽略大小写）
     *   5) baseName 完全匹配兜底
     */
    private File findPreRenderedSlidesDir(String fileName) {
        String baseName = fileName.toLowerCase().endsWith(".pptx")
            ? fileName.substring(0, fileName.length() - 5) : fileName;
        int idx = baseName.indexOf("_课件");
        String chapter = idx > 0 ? baseName.substring(0, idx) : baseName;
        String chapterLower = chapter.toLowerCase(Locale.ROOT);

        List<Path> candidates = resolveDataDirCandidates();
        // 精确匹配 chapter + "ppt课件"（最常见约定）
        String[] exactNames = new String[]{
            chapter + "ppt课件",
            chapter + "PPT课件",
            chapter + "PPT课件"
        };
        for (Path cand : candidates) {
            for (String n : exactNames) {
                File dir = cand.resolve(n).toFile();
                if (dir.isDirectory()) return logMatch(fileName, dir);
            }
        }
        // 模糊匹配：列出每个 data 候选目录下的所有子目录，找 startsWith / contains 命中
        for (Path cand : candidates) {
            File rootDir = cand.toFile();
            if (!rootDir.isDirectory()) continue;
            File[] subDirs = rootDir.listFiles(File::isDirectory);
            if (subDirs == null || subDirs.length == 0) continue;
            File bestStarts = null, bestContains = null;
            for (File sd : subDirs) {
                String name = sd.getName();
                String nameLower = name.toLowerCase(Locale.ROOT);
                // 仅匹配有幻灯片图片的目录，避免误匹配到 ppt-cache/resources 等
                File[] pngs = sd.listFiles((d, n) -> (n.startsWith("幻灯片") || n.matches("slide-\\d+\\.png.*"))
                    && n.toLowerCase().endsWith(".png"));
                if (pngs == null || pngs.length == 0) continue;
                if (nameLower.startsWith(chapterLower)) { bestStarts = sd; break; }
                if (nameLower.contains(chapterLower) && bestContains == null) bestContains = sd;
            }
            if (bestStarts != null) return logMatch(fileName, bestStarts);
            if (bestContains != null) return logMatch(fileName, bestContains);
        }
        // baseName 完全匹配兜底（兼容自定义全路径命名）
        for (Path cand : candidates) {
            File dir = cand.resolve(baseName).toFile();
            if (dir.isDirectory()) return logMatch(fileName, dir);
        }
        log.debug("未命中预渲染目录: fileName={}, chapter={}, candidates={}",
            fileName, chapter, candidates.stream().map(Path::toString).toArray());
        return null;
    }

    private File logMatch(String fileName, File dir) {
        log.info("命中预渲染幻灯片目录: {} -> {} (含{}张png)", fileName, dir,
            Optional.ofNullable(dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"))).map(a -> a.length).orElse(0));
        return dir;
    }

    /**
     * 诊断接口：返回当前后端能识别到的所有预渲染目录列表，方便云端部署时排查"封面白图/内容不对"问题。
     * GET /api/ppt/pre-rendered
     */
    @GetMapping("/pre-rendered")
    public Result<Map<String, Object>> listPreRenderedDirs() {
        List<Path> roots = resolveDataDirCandidates();
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> dirs = new ArrayList<>();
        List<String> rootPaths = new ArrayList<>();
        for (Path r : roots) rootPaths.add(r.toAbsolutePath().toString());
        result.put("dataRootCandidates", rootPaths);

        Set<String> seen = new HashSet<>();
        for (Path cand : roots) {
            File rootDir = cand.toFile();
            if (!rootDir.isDirectory()) continue;
            File[] subs = rootDir.listFiles(File::isDirectory);
            if (subs == null) continue;
            for (File sd : subs) {
                if (!seen.add(sd.getAbsolutePath())) continue;
                File[] pngs = sd.listFiles((d, n) ->
                    (n.startsWith("幻灯片") || n.matches("slide-\\d+\\.png.*")) && n.toLowerCase().endsWith(".png"));
                if (pngs != null && pngs.length > 0) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("dir", sd.getAbsolutePath());
                    info.put("slideCount", pngs.length);
                    info.put("sampleFiles", Arrays.stream(pngs).sorted(
                        Comparator.comparingInt(f -> extractSlideNum(f.getName())))
                        .limit(3).map(File::getName).toList());
                    dirs.add(info);
                }
            }
        }
        result.put("preRenderedDirs", dirs);
        return Result.success(result);
    }

    /**
     * 诊断接口：针对某个 PPT 文件名，返回它会命中的预渲染目录、以及对应的幻灯片列表。
     * GET /api/ppt/pre-rendered/{fileName}
     */
    @GetMapping("/pre-rendered/{fileName}")
    public Result<Map<String, Object>> inspectPreRenderedDir(@PathVariable String fileName) {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return Result.error("非法文件名");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        File dir = findPreRenderedSlidesDir(fileName);
        r.put("fileName", fileName);
        r.put("matchedDir", dir != null ? dir.getAbsolutePath() : null);
        if (dir != null) {
            File[] pngs = dir.listFiles((d, n) -> n.startsWith("幻灯片") && n.toLowerCase().endsWith(".png"));
            int count = pngs != null ? pngs.length : 0;
            r.put("slideCount", count);
            if (pngs != null && count > 0) {
                Arrays.sort(pngs, Comparator.comparingInt(f -> extractSlideNum(f.getName())));
                r.put("sampleFirst3", Arrays.stream(pngs).limit(3).map(File::getName).toList());
                r.put("sampleLast2", Arrays.stream(pngs).skip(Math.max(0, count - 2L)).map(File::getName).toList());
            }
            File pptx = findPptxFile(fileName);
            r.put("pptxSource", pptx != null ? pptx.getAbsolutePath() : null);
            r.put("pptxSourceExists", pptx != null && pptx.exists());
        } else {
            List<String> tried = resolveDataDirCandidates().stream()
                .map(p -> p.toAbsolutePath().toString()).toList();
            r.put("triedDataRoots", tried);
        }
        return Result.success(r);
    }

    // =========================== 演示模式清理 + 定时任务 ===========================

    /**
     * 清理演示模式产生的 PPT 缓存（不含预渲染源目录与 .pptx 源文件）。
     * 对应前端 logout() 里发起的 DELETE /api/ppt/demo-cleanup 请求。
     * 同时开放给管理后台调用，支持非认证访问（演示环境）。
     */
    @DeleteMapping("/demo-cleanup")
    public Result<Map<String, Object>> cleanupDemoCache() {
        Map<String, Object> res = new LinkedHashMap<>();
        long freedBytes = 0;
        int deletedDirs = 0;
        int deletedFiles = 0;

        // 1) 清理 PPT 渲染缓存（ppt-cache 下所有 hash 子目录）
        for (Path cand : resolveDataDirCandidates()) {
            Path pptCache = cand.resolve("ppt-cache");
            if (Files.isDirectory(pptCache)) {
                File[] subs = pptCache.toFile().listFiles(File::isDirectory);
                if (subs != null) {
                    for (File sub : subs) {
                        long[] info = deleteDirQuietly(sub);
                        freedBytes += info[0]; deletedFiles += (int) info[1]; deletedDirs++;
                    }
                }
            }
        }

        // 2) 清理 uploads/ppt 目录中超过 24 小时的临时文件
        String root = findProjectRoot();
        File uploadsPpt = Paths.get(root, "uploads/ppt").toFile();
        if (uploadsPpt.isDirectory()) {
            long now = System.currentTimeMillis();
            File[] temps = uploadsPpt.listFiles();
            if (temps != null) {
                for (File t : temps) {
                    if (t.isFile() && now - t.lastModified() > 24L * 3600 * 1000) {
                        freedBytes += t.length();
                        if (t.delete()) deletedFiles++;
                    }
                }
            }
        }

        res.put("deletedDirs", deletedDirs);
        res.put("deletedFiles", deletedFiles);
        res.put("freedKb", Math.round(freedBytes / 1024.0));
        log.info("[demo-cleanup] 清理完成: dirs={}, files={}, freedKb={}", deletedDirs, deletedFiles, Math.round(freedBytes / 1024.0));
        return Result.success(res);
    }

    /**
     * 每小时清理过期 PPT 缓存（超过 24 小时的 hash 子目录）以及 uploads 临时文件。
     * 在用户频繁刷新、重复生成的场景下防止磁盘泄漏。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void hourlyPptCacheCleanup() {
        long freedBytes = 0;
        int deletedDirs = 0;
        int deletedFiles = 0;
        final long expireMs = 24L * 3600 * 1000;
        long now = System.currentTimeMillis();

        for (Path cand : resolveDataDirCandidates()) {
            Path pptCache = cand.resolve("ppt-cache");
            if (!Files.isDirectory(pptCache)) continue;
            File[] subs = pptCache.toFile().listFiles(File::isDirectory);
            if (subs == null) continue;
            for (File sub : subs) {
                long latest = lastModifiedRecursive(sub);
                if (now - latest > expireMs) {
                    long[] info = deleteDirQuietly(sub);
                    freedBytes += info[0]; deletedFiles += (int) info[1]; deletedDirs++;
                }
            }
        }

        String root = findProjectRoot();
        for (String rel : new String[]{ "uploads/ppt", "uploads/tmp", "uploads" }) {
            File dir = Paths.get(root, rel).toFile();
            if (!dir.isDirectory()) continue;
            File[] files = dir.listFiles(f -> f.isFile() && now - f.lastModified() > expireMs);
            if (files == null) continue;
            for (File f : files) {
                freedBytes += f.length();
                if (f.delete()) deletedFiles++;
            }
        }

        if (deletedDirs > 0 || deletedFiles > 0) {
            log.info("[hourly-cleanup] PPT缓存与上传临时文件清理: dirs={}, files={}, freedKb={}",
                deletedDirs, deletedFiles, Math.round(freedBytes / 1024.0));
        }
    }

    /** 递归计算目录最新修改时间（用于缓存过期判断） */
    private static long lastModifiedRecursive(File dir) {
        long latest = dir.lastModified();
        File[] ch = dir.listFiles();
        if (ch == null) return latest;
        for (File c : ch) {
            long m = c.isDirectory() ? lastModifiedRecursive(c) : c.lastModified();
            if (m > latest) latest = m;
        }
        return latest;
    }

    /** 安静删除目录，返回 [清理的字节数, 删除的文件数] */
    private static long[] deleteDirQuietly(File dir) {
        long bytes = 0;
        long files = 0;
        File[] ch = dir.listFiles();
        if (ch != null) {
            for (File c : ch) {
                if (c.isDirectory()) {
                    long[] sub = deleteDirQuietly(c);
                    bytes += sub[0]; files += sub[1];
                } else {
                    bytes += c.length();
                    if (c.delete()) files++;
                }
            }
        }
        dir.delete();
        return new long[]{ bytes, files };
    }

    /**
     * 从文件名中提取幻灯片编号（slide-1.png 或 幻灯片1.png → 1）
     */
    private static int extractSlideNum(String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
