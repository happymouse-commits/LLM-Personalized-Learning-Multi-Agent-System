package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.service.agent.AdviceAgent;
import com.happymouse.cryd.service.agent.KnowledgeGraphAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学习建议和知识图谱 API
 */
@RestController
@RequestMapping("/api/advice")
public class AdviceController {

    private static final Logger log = LoggerFactory.getLogger(AdviceController.class);

    private final AdviceAgent adviceAgent;
    private final KnowledgeGraphAgent knowledgeGraphAgent;

    public AdviceController(AdviceAgent adviceAgent, KnowledgeGraphAgent knowledgeGraphAgent) {
        this.adviceAgent = adviceAgent;
        this.knowledgeGraphAgent = knowledgeGraphAgent;
    }

    /**
     * 获取个性化学习建议
     */
    @GetMapping("/learning/{sysUserId}")
    public Result<Map<String, Object>> getLearningAdvice(@PathVariable Long sysUserId) {
        try {
            var context = new com.happymouse.cryd.agent.core.AgentContext(sysUserId, "", null);
            String advice = adviceAgent.execute(context).getOutput();

            return Result.success(Map.of(
                "advice", advice,
                "generatedBy", "学习建议师"
            ));
        } catch (Exception e) {
            log.error("生成学习建议失败: sysUserId={}", sysUserId, e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取知识图谱数据
     */
    @GetMapping("/knowledge-graph/{sysUserId}")
    public Result<Map<String, Object>> getKnowledgeGraph(@PathVariable Long sysUserId) {
        try {
            var context = new com.happymouse.cryd.agent.core.AgentContext(sysUserId, "", null);
            String graphData = knowledgeGraphAgent.execute(context).getOutput();

            return Result.success(Map.of(
                "graph", graphData,
                "generatedBy", "知识图谱工程师"
            ));
        } catch (Exception e) {
            log.error("生成知识图谱失败: sysUserId={}", sysUserId, e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取学习建议（带问题）
     */
    @PostMapping("/learning/{sysUserId}")
    public Result<Map<String, Object>> getLearningAdviceWithQuestion(
            @PathVariable Long sysUserId,
            @RequestBody Map<String, String> body) {
        try {
            String question = body.getOrDefault("question", "");
            var context = new com.happymouse.cryd.agent.core.AgentContext(sysUserId, question, null);
            String advice = adviceAgent.execute(context).getOutput();

            return Result.success(Map.of(
                "advice", advice,
                "generatedBy", "学习建议师"
            ));
        } catch (Exception e) {
            log.error("生成学习建议失败: sysUserId={}", sysUserId, e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }
}
