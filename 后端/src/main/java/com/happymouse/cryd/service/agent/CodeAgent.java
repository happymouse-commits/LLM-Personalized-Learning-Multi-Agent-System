package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.service.CodeCompilerService;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 编程实战智能体 — 生成可运行的C代码 + 编译校验
 * 输出内容：完整代码 + 逐行注释 + 运行结果 + 代码解析
 */
@Component
public class CodeAgent implements ResourceAgent {

    private static final Logger log = LoggerFactory.getLogger(CodeAgent.class);

    private final SparkClient sparkClient;
    private final CodeCompilerService codeCompiler;

    public CodeAgent(SparkClient sparkClient, CodeCompilerService codeCompiler) {
        this.sparkClient = sparkClient;
        this.codeCompiler = codeCompiler;
    }

    @Override
    public String getResourceType() { return ResourceType.CODE.getId(); }

    /**
     * 编程实战自检：代码块、main函数、#include、编译状态
     */
    @Override
    public boolean validateJsonFormat(String aiReply) {
        return aiReply != null && !aiReply.isBlank() && aiReply.length() > 50;
    }

    @Override
    public double selfCheck(com.happymouse.cryd.model.entity.LearningResource resource) {
        double score = 1.0;
        String content = resource.getContent();
        if (content == null) return 0.0;

        // 必须包含代码块
        if (!content.contains("```c") && !content.contains("```C") && !content.contains("``` c")) {
            score -= 0.3;
        }

        // 必须有main函数
        if (!content.contains("main")) score -= 0.2;

        // 必须有#include
        if (!content.contains("#include")) score -= 0.2;

        // 编译状态
        if ("FAILED".equals(resource.getCompileStatus())) score -= 0.3;

        // 代码行数不超过50行
        String code = extractCodeFromContent(content);
        if (code != null) {
            long lineCount = code.lines().count();
            if (lineCount > 50) score -= 0.1;
        }

        return Math.max(0, score);
    }

    /**
     * 生成编程实战资源
     */
    @Override
    public LearningResource generate(String chapterName, String knowledgePoint,
                                     String knowledgeLevel, String weakAreas) {
        String prompt = String.format("""
            你是资深C语言开发工程师和教学专家。请为「%s」章节的「%s」知识点生成1个编程实战示例。

            【学生信息】
            知识水平：%s/100
            薄弱环节：%s
            ⚠️ 请围绕薄弱环节设计代码示例，将薄弱知识点融入代码中重点演示。

            【难度要求】
            请根据知识水平选择difficulty：<40用"easy"，40-70用"medium"，>70用"hard"。
            知识水平低时代码简单直观（20行内）；水平高时可加入指针操作、内存管理等进阶内容。

            ════════════════════════════════════════════════════════════════
            【代码质量标准 — 必须严格遵守】
            ════════════════════════════════════════════════════════════════
            1. 完整性：必须是完整的C程序，包含 #include 和 main() 函数
            2. 可编译：代码必须能通过 gcc 编译，无语法错误
            3. 可运行：程序必须有实际输出，不能是空程序
            4. 注释：每个关键步骤都有中文注释说明
            5. 示例性：代码要能体现知识点的核心用法
            6. 简洁性：代码不超过50行，聚焦知识点本身

            ════════════════════════════════════════════════════════════════
            【输出结构要求】
            ════════════════════════════════════════════════════════════════

            1. 使用 ## 问题描述 作为标题，描述这个编程题要解决什么问题

            2. 使用 ## 输入格式 作为标题，说明输入数据的格式（若无输入则写"无"）

            3. 使用 ## 输出格式 作为标题，说明输出数据的格式（若无输出则写"无"）

            4. 使用 ## 样例输入 1 作为标题，用 ```plaintext 代码块包裹一组样例输入（若无输入则代码块内写"无"）

            5. 使用 ## 样例输出 1 作为标题，用 ```plaintext 代码块包裹对应的样例输出

            6. 使用 ## 评测用例规模与约定 作为标题，说明数据范围和特殊约束

            7. 使用 ## 完整代码 作为标题，用 ```c 代码块包裹完整代码
               - 包含必要的 #include
               - main() 函数
               - 每个关键步骤有注释
               - 变量命名清晰有意义

            8. 使用 ## 运行结果 作为标题，展示程序的实际输出

            9. 使用 ## 代码解析 作为标题，逐行或逐块解释关键代码的作用

            10. 使用 ## 改进建议 作为标题，给出1-2个可以优化的方向

            ════════════════════════════════════════════════════════════════
            【禁止事项】
            ════════════════════════════════════════════════════════════════
            ❌ 禁止生成无法编译的代码
            ❌ 禁止使用未定义的变量或函数
            ❌ 禁止使用C++语法（如cin/cout）
            ❌ 禁止代码超过50行
            ❌ 禁止缺少main()函数
            ❌ 禁止缺少#include
            ❌ 禁止省略样例输入/输出，必须有至少一组完整的样例

            输出JSON格式：
            [{"title": "标题", "type": "code", "content": "Markdown内容（包含##问题描述到##改进建议共10个标准小节）", "knowledgePoint": "知识点", "difficulty": "easy/medium/hard"}]

            只输出纯JSON数组，不要```json标记。
            """, chapterName, knowledgePoint, knowledgeLevel, weakAreas);

        String aiReply;
        try {
            aiReply = sparkClient.smartChat(prompt, "生成编程实战代码",
                    SparkClient.TaskComplexity.MEDIUM, 4096);
        } catch (Exception e) {
            log.warn("CodeAgent LLM调用失败: {}", e.getMessage());
            return null;
        }

        if (aiReply == null || aiReply.isBlank()) return null;

        try {
            var arr = com.happymouse.cryd.common.JsonParser.parseArray(aiReply);
            if (arr == null || arr.isEmpty()) return null;

            var obj = arr.getJSONObject(0);
            String content = obj.getString("content");

            LearningResource lr = new LearningResource();
            lr.setTitle(obj.getString("title"));
            lr.setType(ResourceType.CODE.getId());
            lr.setContent(content);
            lr.setKnowledgePoint(obj.getString("knowledgePoint"));
            lr.setDifficulty(obj.getString("difficulty"));

            // 提取代码块进行编译校验
            String code = extractCodeFromContent(content);
            if (code != null) {
                var compileResult = codeCompiler.validate(code);
                lr.setCompileStatus(compileResult.passed() ? "PASSED" : "FAILED");
                lr.setCompileError(compileResult.passed() ? null : compileResult.message());
                if (!compileResult.passed()) {
                    log.warn("代码编译失败: {}", compileResult.message());
                }
            } else {
                lr.setCompileStatus("NOT_CHECKED");
            }

            return lr;
        } catch (Exception e) {
            log.warn("编程实战解析失败: error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Markdown内容中提取C代码块
     */
    private String extractCodeFromContent(String content) {
        if (content == null) return null;
        String lower = content.toLowerCase();
        int start = lower.indexOf("```c");
        if (start < 0) return null;
        start += 4;
        int end = content.indexOf("```", start);
        if (end < 0) return null;
        return content.substring(start, end).trim();
    }
}
