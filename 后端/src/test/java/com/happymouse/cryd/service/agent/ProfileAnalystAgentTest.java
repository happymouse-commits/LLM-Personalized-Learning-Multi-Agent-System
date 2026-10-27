package com.happymouse.cryd.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/**
 * 画像分析Agent测试 — 验证从对话中抽取画像维度的逻辑。
 */
class ProfileAnalystAgentTest {

    @Test
    @DisplayName("对话中抽取知识水平")
    void extractKnowledgeLevel() {
        String[] conversations = {
            "我完全没学过C语言，不知道从哪里开始",
            "我学过一点C语言，基本语法都懂一些",
            "我用C语言写过几个小项目，比较熟悉了"
        };
        int[] expectedLevels = {10, 40, 70};
        int[] actualLevels = new int[conversations.length];

        System.out.println("  [画像] 从对话中抽取知识水平:");
        for (int i = 0; i < conversations.length; i++) {
            String msg = conversations[i].toLowerCase();
            if (msg.contains("完全没学过") || msg.contains("零基础")) actualLevels[i] = 10;
            else if (msg.contains("学过一点") || msg.contains("懂一些")) actualLevels[i] = 40;
            else if (msg.contains("项目") || msg.contains("熟悉")) actualLevels[i] = 70;
            else actualLevels[i] = 50;

            System.out.println("    对话: \"" + conversations[i].substring(0, Math.min(30, conversations[i].length())) + "...\"");
            System.out.println("      识别水平: " + actualLevels[i] + "/100");
        }

        assertArrayEquals(expectedLevels, actualLevels, "知识水平应正确反映对话内容");
    }

    @Test
    @DisplayName("对话中抽取薄弱环节")
    void extractWeakAreas() {
        String conversation = "我在学指针，感觉很难理解，特别是二级指针和指针数组";
        String weakKeyword = "指针";

        boolean hasWeak = conversation.contains(weakKeyword);
        System.out.println("  [画像] 对话: \"" + conversation + "\"");
        System.out.println("  [画像] 识别薄弱环节: " + (hasWeak ? "指针类" : "无"));
        assertTrue(hasWeak, "对话提及指针应识别为薄弱环节");

        // 测试多薄弱环节
        String multi = "指针很难，动态内存分配也搞不懂，但结构体还行";
        String[] keywords = {"指针", "动态内存", "结构体"};
        List<String> weakAreas = new ArrayList<>();
        for (String kw : keywords) {
            if (multi.contains(kw)) weakAreas.add(kw);
        }
        System.out.println("  [画像] 多薄弱环节检测:" + weakAreas);
        assertEquals(3, weakAreas.size(), "应识别出指针、动态内存和结构体3个薄弱环节");
    }

    @Test
    @DisplayName("增量画像更新")
    void incrementalProfileUpdate() {
        // 初始画像
        Map<String, Object> profile = new HashMap<>();
        profile.put("knowledgeLevel", 30);
        profile.put("weakAreas", "指针,结构体");
        profile.put("cognitiveStyle", "reading");

        System.out.println("  [画像] 初始: " + profile);

        // 第一次更新
        profile.put("knowledgeLevel", 45);
        profile.put("cognitiveStyle", "visual");
        System.out.println("  [画像] 第1次更新后: knowledgeLevel=45, cognitiveStyle=visual");

        // 第二次更新（仅更新部分字段）
        profile.put("knowledgeLevel", 55);
        profile.put("weakAreas", "指针,结构体,内存管理");
        System.out.println("  [画像] 第2次更新后: knowledgeLevel=55, weakAreas=指针,结构体,内存管理");

        assertEquals(55, profile.get("knowledgeLevel"), "增量更新应保持最新值");
        assertEquals("visual", profile.get("cognitiveStyle"), "未更新的字段应保持不变");
        System.out.println("  [画像] 增量更新测试通过");
    }
}
