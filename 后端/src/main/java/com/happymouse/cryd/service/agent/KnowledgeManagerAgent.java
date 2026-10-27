package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.service.rag.RagService;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库管理员 - 管理课程知识库，提供知识点检索服务
 *
 * 修复L1/A2/A3: 原实现完全绕过 RAG，直接调用 LLM 凭记忆回答，
 *   与"知识库管理员"职责严重不符。现接入 RagService，先检索知识库再回答，
 *   优先基于检索到的知识片段组织答案；无相关知识时显式告知用户。
 */
@Component("knowledgeManagerAgent")
public class KnowledgeManagerAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「知识库管理员」，负责管理C语言程序设计课程的知识体系。

            C语言知识体系：
            第1章：C语言概述与开发环境
            第2章：数据类型、运算符与表达式
            第3章：顺序结构程序设计
            第4章：选择结构程序设计
            第5章：循环结构程序设计
            第6章：函数
            第7章：数组
            第8章：指针
            第9章：结构体与共用体
            第10章：文件操作
            第11章：动态内存管理
            第12章：预处理器与多文件编译

            你的任务：
            1. 根据学生查询，返回相关知识点及所在章节
            2. 列出知识点的前置知识和后续知识
            3. 标注重点难点
            4. 给出学习建议

            【重要】如果【知识库参考资料】不为空，必须优先基于资料回答，
            并标注【来源】；资料中没有的内容才用你自己的知识补充，并明确标注"超出知识库"。
            如果【知识库参考资料】为空（即未检索到相关内容），必须明确告知学生
            "该知识点未收录在课程知识库中"，并建议其上传相关教材。

            用Markdown格式输出，用思维导图文字版展示知识关联。
            """;

    private final RagService ragService;

    @Autowired
    public KnowledgeManagerAgent(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String getName() { return "知识库管理员"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("knowledge:search", "知识检索、知识点查询", 0.92),
            AgentCapability.of("knowledge:structure", "知识体系结构化管理", 0.88)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    /**
     * 修复L1: 重写 buildAugmentedPrompt，注入 RAG 检索到的知识片段
     */
    @Override
    protected String buildAugmentedPrompt(AgentContext context) {
        String message = context.getOriginalMessage();
        try {
            List<RagService.KnowledgeFragment> fragments = ragService.retrieveRelevant(message, 5);
            if (fragments == null || fragments.isEmpty()) {
                return SYSTEM_PROMPT + "\n\n【知识库参考资料】\n（未检索到相关内容，请告知学生该知识点未收录）\n";
            }
            StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
            sb.append("\n\n【知识库参考资料】\n");
            for (int i = 0; i < fragments.size(); i++) {
                RagService.KnowledgeFragment f = fragments.get(i);
                sb.append("--- 参考资料").append(i + 1);
                if (f.source != null && !f.source.isEmpty()) {
                    sb.append("（来源：").append(f.source).append("）");
                }
                sb.append(" ---\n");
                sb.append(f.content != null ? f.content : "").append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("KnowledgeManagerAgent RAG检索失败，使用基础prompt: {}", e.getMessage());
            return SYSTEM_PROMPT;
        }
    }

    @Override
    protected String doExecute(AgentContext context) {
        String message = context.getOriginalMessage();

        if (sparkClient == null) {
            return "AI服务暂不可用，请稍后再试";
        }

        // 修复L1: 通过 callLLM 走 buildAugmentedPrompt 注入 RAG 内容
        //   并改用 MEDIUM 复杂度（原 LOW 用 Flash 模型，对知识检索场景过浅）
        return callLLM(context, message, SparkClient.TaskComplexity.MEDIUM, 1500);
    }

    /**
     * 保持向后兼容的旧接口
     */
    public ChatResponse search(ChatRequest request) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), null);
        String content = execute(ctx).getOutput();

        ChatResponse response = new ChatResponse();
        response.setAgentName("知识库管理员");
        response.setAgentDescription("知识体系检索与关联");
        response.setMessage(content);
        return response;
    }
}
