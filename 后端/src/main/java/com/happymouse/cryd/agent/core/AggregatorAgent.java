package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 整合Agent — 合并多Agent产出为统一响应
 */
public class AggregatorAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(AggregatorAgent.class);

    private static final String AGGREGATE_PROMPT =
        """
        你是一个C语言教学内容整合专家。请将多个AI智能体生成的内容整合为一份完整、连贯、高质量的学习材料。

        ════════════════════════════════════════════════════════════════
        【整合原则】
        ════════════════════════════════════════════════════════════════
        1. 逻辑顺序: 知识检索 → 概念讲解 → 代码示例 → 练习题 → 学习建议
        2. 去重: 删除重复的知识点和代码
        3. 补充: 如果某部分内容不完整，用你的知识补充
        4. 质量: 确保所有代码示例可编译运行，无语法错误
        5. 格式: 使用Markdown，层次分明，代码用```c包裹

        ════════════════════════════════════════════════════════════════
        【输出格式要求】
        ════════════════════════════════════════════════════════════════
        # [知识点名称]

        ## 📖 核心概念
        （简洁明了的定义和讲解）

        ## 💻 代码示例
        （完整可运行的C代码，含注释）

        ## ⚠️ 常见错误
        （错误写法 vs 正确写法）

        ## ✅ 练习题
        （2-3道练习题，含答案）

        ## 📝 学习建议
        （针对该知识点的学习建议）

        ════════════════════════════════════════════════════════════════
        【质量自检清单】
        ════════════════════════════════════════════════════════════════
        输出前检查：
        - 每个代码示例是否完整（有#include和main）？
        - 知识点是否覆盖全面？
        - 是否有重复内容需要合并？
        - Markdown格式是否正确？
        - 总字数是否在500-2000字之间？
        """;

    public AggregatorAgent(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    @Override
    public String getName() { return "整合Agent"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(AgentCapability.of("aggregate:content", "内容整合、去重、格式化输出", 0.85));
    }

    @Override
    protected String getSystemPrompt() { return AGGREGATE_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        StringBuilder combined = new StringBuilder();
        combined.append("# 学习内容\n\n");

        int idx = 1;
        for (AgentContext.AgentResult result : context.getAllResults().values()) {
            if (result.isSuccess() && result.getOutput() != null) {
                // 跳过管道Agent的元数据输出
                if (result.getOutput().startsWith("decomposed:") || result.getOutput().startsWith("审核")) {
                    continue;
                }
                combined.append("---\n\n");
                combined.append(result.getOutput()).append("\n\n");
                idx++;
            }
        }

        // 如果只有1个Agent的输出，直接返回
        if (idx <= 2) {
            // 找到第一个非管道输出
            for (AgentContext.AgentResult result : context.getAllResults().values()) {
                if (result.isSuccess() && result.getOutput() != null
                    && !result.getOutput().startsWith("decomposed:")
                    && !result.getOutput().startsWith("审核")) {
                    return result.getOutput();
                }
            }
        }

        // 多Agent输出，调用LLM整合
        if (combined.length() > 100) {
            // 直接拼接，让AI整合
            try {
                // 内容整合是中等复杂度任务
                String aggregated = sparkClient.smartChat(AGGREGATE_PROMPT,
                    "请整合以下内容：\n\n" + combined.toString(),
                    SparkClient.TaskComplexity.MEDIUM, 2048);
                return aggregated;
            } catch (Exception e) {
                log.warn("[整合] AI整合失败，直接拼接: {}", e.getMessage());
                return combined.toString();
            }
        }

        return combined.toString();
    }
}
