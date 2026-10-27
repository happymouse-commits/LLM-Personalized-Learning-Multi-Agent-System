package com.happymouse.cryd.model.dto;

import java.util.List;

/**
 * 聊天响应
 */
public class ChatResponse {
    private String message;
    private String agentName;
    private String agentDescription;
    private boolean profileUpdated;
    private boolean resourceGenerated;
    private List<Long> resourceIds;
    private boolean onboarding;          // 是否处于新生引导模式
    private int onboardingStep;          // 当前引导步骤 (1-7)
    private boolean onboardingComplete;  // 引导是否已完成
    private List<String> options;        // 快速回复选项（引导选择题）

    // 错题辅导相关
    private Boolean errorCoaching;           // 是否为错题辅导消息
    private Boolean suggestPractice;         // 是否建议巩固练习
    private String practiceChapterId;        // 建议练习的章节ID
    private String practiceChapterName;      // 建议练习的章节名称

    // 语音交互
    private Boolean voiceSufficient;             // 语音回答是否充分（null=非语音，true=充分，false=不充分）

    // 资源查看按钮
    private Boolean showResourceButton;      // 是否显示查看资源按钮
    private List<ResourceButton> resourceButtons; // 资源按钮列表

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentDescription() { return agentDescription; }
    public void setAgentDescription(String agentDescription) { this.agentDescription = agentDescription; }
    public boolean isProfileUpdated() { return profileUpdated; }
    public void setProfileUpdated(boolean profileUpdated) { this.profileUpdated = profileUpdated; }
    public boolean isResourceGenerated() { return resourceGenerated; }
    public void setResourceGenerated(boolean resourceGenerated) { this.resourceGenerated = resourceGenerated; }
    public List<Long> getResourceIds() { return resourceIds; }
    public void setResourceIds(List<Long> resourceIds) { this.resourceIds = resourceIds; }
    public boolean isOnboarding() { return onboarding; }
    public void setOnboarding(boolean onboarding) { this.onboarding = onboarding; }
    public int getOnboardingStep() { return onboardingStep; }
    public void setOnboardingStep(int onboardingStep) { this.onboardingStep = onboardingStep; }
    public boolean isOnboardingComplete() { return onboardingComplete; }
    public void setOnboardingComplete(boolean onboardingComplete) { this.onboardingComplete = onboardingComplete; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public Boolean getErrorCoaching() { return errorCoaching; }
    public void setErrorCoaching(Boolean errorCoaching) { this.errorCoaching = errorCoaching; }
    public Boolean getSuggestPractice() { return suggestPractice; }
    public void setSuggestPractice(Boolean suggestPractice) { this.suggestPractice = suggestPractice; }
    public String getPracticeChapterId() { return practiceChapterId; }
    public void setPracticeChapterId(String practiceChapterId) { this.practiceChapterId = practiceChapterId; }
    public String getPracticeChapterName() { return practiceChapterName; }
    public void setPracticeChapterName(String practiceChapterName) { this.practiceChapterName = practiceChapterName; }
    public Boolean getShowResourceButton() { return showResourceButton; }
    public void setShowResourceButton(Boolean showResourceButton) { this.showResourceButton = showResourceButton; }
    public List<ResourceButton> getResourceButtons() { return resourceButtons; }
    public void setResourceButtons(List<ResourceButton> resourceButtons) { this.resourceButtons = resourceButtons; }
    public Boolean getVoiceSufficient() { return voiceSufficient; }
    public void setVoiceSufficient(Boolean voiceSufficient) { this.voiceSufficient = voiceSufficient; }
}
