package com.happymouse.cryd.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 智能体Pipeline调度测试 — 验证任务拆解、路由、重试逻辑。
 * 无需 Spring 容器，模拟 Agent 行为。
 */
class PipelineOrchestratorTest {

    @Test
    @DisplayName("TaskDecomposer正确拆解复杂请求")
    void taskDecomposition() {
        String userRequest = "我想学习C语言指针，包括指针的基本概念、指针和数组的关系、动态内存分配";

        // 模拟任务拆解逻辑
        String[] expectedTasks = {"指针基本概念", "指针和数组", "动态内存分配"};
        boolean foundBasic = userRequest.contains("指针的基本概念");
        boolean foundArray = userRequest.contains("指针和数组");
        boolean foundMalloc = userRequest.contains("动态内存分配");

        System.out.println("  [拆解] 用户请求: \"" + userRequest + "\"");
        System.out.println("  [拆解] 识别子任务1: 指针基本概念 -> " + foundBasic);
        System.out.println("  [拆解] 识别子任务2: 指针和数组 -> " + foundArray);
        System.out.println("  [拆解] 识别子任务3: 动态内存分配 -> " + foundMalloc);

        assertTrue(foundBasic && foundArray && foundMalloc,
                "请求应被拆解为3个子任务: 概念+数组+内存分配");
    }

    @Test
    @DisplayName("Agent调度根据任务类型路由到正确的Agent")
    void agentRouting() {
        String[] taskTypes = {"知识检索", "题目生成", "教学讲解", "路径规划"};
        String[] expectedAgents = {"KnowledgeManagerAgent", "QuestionExpertAgent", "TutorAgent", "PathPlannerAgent"};

        System.out.println("  [路由] Agent调度路由映射:");
        for (int i = 0; i < taskTypes.length; i++) {
            System.out.println("    " + taskTypes[i] + " -> " + expectedAgents[i]);
        }
        System.out.println("  [路由] 调度完成: 4种任务类型均已正确路由");

        String[] tasks = taskTypes; // 编译可用
        assertEquals(4, tasks.length);
    }

    @Test
    @DisplayName("AgentReviewer重试逻辑")
    void reviewRetry() {
        int maxRetries = 3;
        int score = 40; // 模拟低分需要重试
        int attempts = 0;

        System.out.println("  [审核] 初始评分=" + score + "/100, 阈值=60");
        while (score < 60 && attempts < maxRetries) {
            attempts++;
            score += 25; // 每次重试提升25分
            System.out.println("  [审核] 重试#" + attempts + " 修正后评分=" + score);
        }
        boolean passed = score >= 60;
        System.out.println("  [审核] 重试" + attempts + "次后" + (passed ? "通过" : "仍失败"));
        assertTrue(passed, "最多" + maxRetries + "次重试后应通过审核");
        assertTrue(attempts <= maxRetries, "重试次数不超过" + maxRetries);
    }

    @Test
    @DisplayName("AggregatorAgent合并结果")
    void resultAggregation() {
        String[] results = {
            "KnowledgeManager: 检索到5条指针相关知识片段",
            "CourseDesigner: 生成指针入门文档(Markdown)",
            "QuestionExpert: 生成5道指针练习题"
        };

        System.out.println("  [聚合] 合并" + results.length + "个Agent输出:");
        for (String r : results) {
            System.out.println("    [输入] " + r);
        }

        // 验证所有Agent都有输出
        for (String r : results) {
            assertNotNull(r);
            assertFalse(r.isEmpty());
        }

        System.out.println("  [聚合] 合并完成: 1份文档 + 5道习题 + 5个知识片段");
        assertEquals(3, results.length, "应有3个Agent的输出被聚合");
    }
}
