package com.happymouse.cryd.service.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 资源类型枚举 — 集中管理资源类型ID，避免 ChatController 关键词映射与
 * ResourceAgent.getResourceType() 返回值不一致（修复E7）
 *
 * 类型ID是资源在 DB 中的 type 字段值，也是前后端约定的 canonical ID。
 */
public enum ResourceType {

    /** 知识精讲（ConceptAgent 生成） */
    EXPLANATION("explanation", "知识精讲", "📖"),
    /** 知识导图（MindmapAgent 生成） */
    MINDMAP("mindmap", "知识导图", "🗺️"),
    /** 编程实战（CodeAgent 生成） */
    CODE("code", "编程实战", "💻"),
    /** 巩固练习（ExerciseAgent 生成） */
    EXERCISE("exercise", "巩固练习", "📝"),
    /** 错题诊断（WeaknessAgent 生成） */
    WEAKNESS("weakness", "错题诊断", "🎯"),
    /** 视频讲解（VideoAgent 推荐） */
    COURSE_VIDEO("course-video", "视频讲解", "🎬"),
    /** 学习课件（PptAgent 提供） */
    COURSE_PPT("course-ppt", "学习课件", "📑"),
    /** 听学笔记（AudioAgent 生成） */
    ARTICLE("article", "听学笔记", "🎧");

    private final String id;
    private final String displayName;
    private final String icon;

    ResourceType(String id, String displayName, String icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }

    /** ID → 枚举的快速查找 */
    private static final Map<String, ResourceType> ID_MAP = new HashMap<>();
    static {
        for (ResourceType t : values()) ID_MAP.put(t.id, t);
    }

    /** 按 ID 查找，未知返回 null */
    public static ResourceType fromId(String id) {
        if (id == null) return null;
        return ID_MAP.get(id);
    }

    /** 按 ID 查找显示名，未知返回"学习资源" */
    public static String displayNameOf(String id) {
        ResourceType t = fromId(id);
        return t != null ? t.displayName : "学习资源";
    }

    /** 按 ID 查找图标，未知返回"📄" */
    public static String iconOf(String id) {
        ResourceType t = fromId(id);
        return t != null ? t.icon : "📄";
    }

    /** 所有有效ID列表（用于校验） */
    public static List<String> validIds() {
        return Arrays.stream(values()).map(ResourceType::getId).toList();
    }
}
