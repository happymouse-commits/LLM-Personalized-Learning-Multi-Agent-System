package com.happymouse.cryd.model.dto;

/**
 * 资源按钮DTO
 * 用于在AI对话框中显示可点击的资源查看按钮
 */
public class ResourceButton {
    private Long resourceId;
    private String title;
    private String type;
    private String knowledgePoint;
    private String chapter;
    private String actionUrl;
    private String icon;

    public ResourceButton() {}

    public ResourceButton(Long resourceId, String title, String type,
                          String knowledgePoint, String chapter,
                          String actionUrl, String icon) {
        this.resourceId = resourceId;
        this.title = title;
        this.type = type;
        this.knowledgePoint = knowledgePoint;
        this.chapter = chapter;
        this.actionUrl = actionUrl;
        this.icon = icon;
    }

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getKnowledgePoint() { return knowledgePoint; }
    public void setKnowledgePoint(String knowledgePoint) { this.knowledgePoint = knowledgePoint; }
    public String getChapter() { return chapter; }
    public void setChapter(String chapter) { this.chapter = chapter; }
    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
