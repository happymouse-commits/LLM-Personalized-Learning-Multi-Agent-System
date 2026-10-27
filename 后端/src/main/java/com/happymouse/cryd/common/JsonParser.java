package com.happymouse.cryd.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM JSON 输出解析工具 — 多策略提取 JSON 数组，应对 LLM 输出格式多变的问题。
 * 消除 AutoResourceGenerator 和 ResourceController 中的重复脆弱解析代码。
 */
public final class JsonParser {

    private static final Logger log = LoggerFactory.getLogger(JsonParser.class);

    private JsonParser() {}

    /**
     * 从 LLM 返回的原始文本中提取 JSON 数组。
     * 按优先级尝试多种策略：
     *  1. 整段就是合法 JSON 数组 → 直接解析
     *  2. 提取 ```json ... ``` 代码块中的 JSON
     *  3. 搜索原始文本中第一个 [ 到最后一个 ] 的区间，修复尾逗号后解析
     *
     * @param raw LLM 原始返回文本
     * @return 解析成功返回 JSONArray，失败返回 null
     */
    public static JSONArray parseArray(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();

        // 策略1: 整段就是合法 JSON 数组 → 直接解析
        if (text.startsWith("[") && text.endsWith("]")) {
            try { return JSON.parseArray(text); } catch (Exception ignored) {}
        }

        // 策略2: 提取 ```json ... ``` 或 ``` ... ``` 代码块中的 JSON
        for (String fence : new String[]{"```json", "```"}) {
            int fenceStart = text.indexOf(fence);
            if (fenceStart >= 0) {
                int contentStart = fenceStart + fence.length();
                int fenceEnd = text.indexOf("```", contentStart);
                if (fenceEnd > contentStart) {
                    String inside = text.substring(contentStart, fenceEnd).trim();
                    if (inside.startsWith("[") && inside.endsWith("]")) {
                        String clean = fixTrailingCommas(inside);
                        try { return JSON.parseArray(clean); } catch (Exception ignored) {}
                        try { return JSON.parseArray(inside); } catch (Exception ignored) {}
                    }
                }
            }
        }

        // 策略3: 在原始文本中搜索 [ ... ] 区间，取最长的 JSON 候选
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = fixTrailingCommas(text.substring(start, end + 1));
            try { return JSON.parseArray(candidate); } catch (Exception ignored) {}
        }

        log.warn("JsonParser解析失败，原始内容前200字: {}", text.substring(0, Math.min(200, text.length())));
        return null;
    }

    /**
     * 从 LLM 返回的原始文本中提取 JSON 对象。
     * 多策略提取，与 parseArray 逻辑一致但处理单对象。
     *
     * @param raw LLM 原始返回文本
     * @return 解析成功返回 JSONObject，失败返回 null
     */
    public static JSONObject parseObject(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();

        // 策略1: 整段就是合法 JSON 对象 → 直接解析
        if (text.startsWith("{") && text.endsWith("}")) {
            try { return JSON.parseObject(text); } catch (Exception ignored) {}
        }

        // 策略2: 提取 ```json ... ``` 代码块中的 JSON
        for (String fence : new String[]{"```json", "```"}) {
            int fenceStart = text.indexOf(fence);
            if (fenceStart >= 0) {
                int contentStart = fenceStart + fence.length();
                int fenceEnd = text.indexOf("```", contentStart);
                if (fenceEnd > contentStart) {
                    String inside = text.substring(contentStart, fenceEnd).trim();
                    if (inside.startsWith("{") && inside.endsWith("}")) {
                        String clean = fixTrailingCommas(inside);
                        try { return JSON.parseObject(clean); } catch (Exception ignored) {}
                        try { return JSON.parseObject(inside); } catch (Exception ignored) {}
                    }
                }
            }
        }

        // 策略3: 在原始文本中搜索 { ... } 区间
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = fixTrailingCommas(text.substring(start, end + 1));
            try { return JSON.parseObject(candidate); } catch (Exception ignored) {}
        }

        log.warn("JsonParser.parseObject解析失败，原始内容前200字: {}", text.substring(0, Math.min(200, text.length())));
        return null;
    }

    /**
     * 修复 JSON 中常见的尾逗号问题（数组和对象都处理）
     */
    private static String fixTrailingCommas(String json) {
        return json
                .replaceAll(",\\s*]", "]")         // 数组尾逗号: [1, 2, ]
                .replaceAll(",\\s*(?=\\n?})", "");  // 对象尾逗号: {"a": 1, }
    }
}
