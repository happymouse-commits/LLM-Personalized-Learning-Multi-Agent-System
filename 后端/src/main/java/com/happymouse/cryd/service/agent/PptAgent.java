package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.LearningResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 学习课件智能体 — 优先使用预置PPT文件，无匹配时降级为AI生成大纲
 *
 * 预置PPT目录：data/resources/
 * 文件命名规则：{章节名}_课件.pptx 或 {章节名}_课件_讯飞.pptx
 */
@Component
public class PptAgent implements ResourceAgent {

    private static final Logger log = LoggerFactory.getLogger(PptAgent.class);

    /** 章节名 → PPT文件名映射（每个章节映射到对应专题PPT文件） */
    private static final Map<String, String> PPT_FILE_MAP = Map.ofEntries(
            Map.entry("基础语法", "基础语法_课件_讯飞v2.pptx"),
            Map.entry("流程控制", "流程控制_课件_讯飞.pptx"),
            Map.entry("数组与指针", "数组与指针_课件_讯飞.pptx"),
            Map.entry("C语言基础", "基础语法_课件_讯飞v2.pptx"),
            Map.entry("数据类型与运算符", "基础语法_课件_讯飞v2.pptx"),
            Map.entry("控制结构", "流程控制_课件_讯飞.pptx"),
            Map.entry("循环", "流程控制_课件_讯飞.pptx"),
            Map.entry("函数", "流程控制_课件_讯飞.pptx"),
            Map.entry("数组", "数组与指针_课件_讯飞.pptx"),
            Map.entry("指针", "数组与指针_课件_讯飞.pptx"),
            Map.entry("结构体", "数组与指针_课件_讯飞.pptx"),
            Map.entry("文件操作", "流程控制_课件_讯飞.pptx"),
            Map.entry("内存管理", "数组与指针_课件_讯飞.pptx")
    );

    @Value("${ppt.resources.dir:}")
    private String resourcesDir;

    private String resolveResourcesDir() {
        if (resourcesDir != null && !resourcesDir.isEmpty()) return resourcesDir;
        // 自动探测项目 data/resources 目录
        String userDir = System.getProperty("user.dir");
        String[] candidates = {
            userDir + "/../data/resources",
            userDir + "/data/resources",
            "F:/最新/data/resources"
        };
        for (String path : candidates) {
            if (java.nio.file.Files.isDirectory(java.nio.file.Paths.get(path))) return path;
        }
        return userDir + "/data/resources";
    }

    @Override
    public String getResourceType() { return ResourceType.COURSE_PPT.getId(); }

    /**
     * PPT课件自检：检查是否有预置文件或AI生成内容
     */
    @Override
    public double selfCheck(LearningResource resource) {
        if (resource == null || resource.getContent() == null) return 0.0;
        // 有预置文件路径 = 满分
        if (resource.getContent().startsWith("[PPT_FILE:")) return 1.0;
        // AI生成内容的自检
        double score = 1.0;
        String content = resource.getContent();
        if (!content.contains("封面") && !content.contains("第1页")) score -= 0.15;
        if (!content.contains("```")) score -= 0.15;
        return Math.max(0, score);
    }

    /**
     * 修复L2: 判断当前章节是否有可用的预置PPT文件
     * 原实现 supports() 默认 true，导致 PptAgent 每次都被调度、每次都返回 null、浪费一次调度槽位
     */
    @Override
    public boolean supports(String chapterName, String knowledgePoint, String weakAreas) {
        if (chapterName == null) return false;
        String pptFile = PPT_FILE_MAP.get(chapterName);
        if (pptFile == null) return false;
        Path pptPath = Paths.get(resolveResourcesDir(), pptFile);
        return Files.exists(pptPath);
    }

    /**
     * 生成学习课件资源 — 优先返回预置PPT文件路径
     */
    @Override
    public LearningResource generate(String chapterName, String knowledgePoint,
                                     String knowledgeLevel, String weakAreas) {
        // 1. 优先查找预置PPT文件
        String dir = resolveResourcesDir();
        String pptFile = PPT_FILE_MAP.get(chapterName);
        if (pptFile != null) {
            Path pptPath = Paths.get(dir, pptFile);
            if (Files.exists(pptPath)) {
                log.info("PPTAgent: 使用预置文件 chapter={}, file={}", chapterName, pptFile);
                LearningResource lr = new LearningResource();
                lr.setTitle(chapterName + "课件");
                lr.setType(ResourceType.COURSE_PPT.getId());
                lr.setContent("[PPT_FILE:" + pptPath.toAbsolutePath() + "]");
                lr.setKnowledgePoint(knowledgePoint);
                lr.setDifficulty("medium");
                return lr;
            }
            log.warn("PPTAgent: 预置文件不存在: {}", pptPath);
        }

        // 2. 无预置文件时返回null（不消耗token生成PPT大纲）
        log.info("PPTAgent: 无预置PPT文件 chapter={}, 跳过", chapterName);
        return null;
    }
}
