package com.happymouse.cryd.service.knowledge;

import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 标签提取器 — 使用LLM自动提取知识点标签
 */
@Component
public class TagExtractor {

    private static final Logger log = LoggerFactory.getLogger(TagExtractor.class);

    private static final String EXTRACT_PROMPT = """
        你是一个C语言课程知识标签提取专家。请从以下文档内容中提取知识点标签。
        标签应涵盖：章节名称、核心概念、重点知识点、易错点、拓展内容等。

        以JSON数组格式输出：["标签1", "标签2", "标签3"]
        只输出JSON数组，不要多余文字。最多输出10个标签。
        """;

    private final SparkClient sparkClient;

    public TagExtractor(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    /**
     * AI提取标签
     */
    public List<String> extractTags(String content) {
        if (content == null || content.isEmpty() || content.length() < 50) {
            return Collections.emptyList();
        }

        // 截取前2000字用于标签提取
        String snippet = content.length() > 2000 ? content.substring(0, 2000) : content;

        try {
            String result = sparkClient.chat(EXTRACT_PROMPT, snippet, 0.3f, 512);
            result = result.trim();
            if (result.startsWith("```")) {
                result = result.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }

            List<String> tags = new ArrayList<>();
            int start = result.indexOf('[');
            int end = result.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String array = result.substring(start + 1, end);
                for (String part : array.split(",")) {
                    String tag = part.trim().replaceAll("^\"|\"$", "");
                    if (!tag.isEmpty() && tag.length() < 50) {
                        tags.add(tag);
                    }
                }
            }

            log.info("AI标签提取: {}个标签", tags.size());
            return tags;

        } catch (Exception e) {
            log.warn("AI标签提取失败: {}", e.getMessage());
            return extractByKeywords(content);
        }
    }

    /**
     * 降级：关键词提取
     */
    private List<String> extractByKeywords(String content) {
        List<String> tags = new ArrayList<>();
        String[] keywords = {"指针", "数组", "函数", "结构体", "链表", "文件", "内存",
            "变量", "循环", "条件", "递归", "字符串", "位运算", "预处理"};

        String lower = content.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                tags.add(kw);
            }
        }
        return tags;
    }
}
