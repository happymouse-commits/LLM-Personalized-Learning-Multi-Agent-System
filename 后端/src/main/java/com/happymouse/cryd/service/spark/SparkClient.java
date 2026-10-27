package com.happymouse.cryd.service.spark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 大模型 HTTP 客户端（OpenAI 兼容接口）
 * 支持 DeepSeek V4 Pro（深度推理）和 Flash（快速响应）智能分配
 * 根据任务复杂度自动选择模型和思考强度
 */
@Service
public class SparkClient {

    private static final Logger log = LoggerFactory.getLogger(SparkClient.class);

    @Value("${llm.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.model:deepseek-chat}")
    private String model;  // Flash 模型（快速响应）

    @Value("${llm.api.model-pro:deepseek-v4-pro}")
    private String modelPro;  // Pro 模型（深度推理）

    // ===== 任务复杂度枚举 =====
    public enum TaskComplexity {
        TRIVIAL,    // 简单任务：关键词提取、格式转换
        LOW,        // 低复杂度：简单问答、翻译
        MEDIUM,     // 中等复杂度：资源生成、代码解释
        HIGH,       // 高复杂度：幻觉检测、事实校验
        CRITICAL    // 关键任务：学习路径规划、学情分析
    }

    // ===== 思考模式配置 =====
    private static final float[][] THINKING_CONFIGS = {
        // {temperature, maxTokens}  索引对应 TaskComplexity
        {0.3f, 512},      // TRIVIAL: 低温度，小token
        {0.4f, 1024},     // LOW
        {0.5f, 2048},     // MEDIUM
        {0.5f, 4096},     // HIGH: 需要更多思考空间
        {0.4f, 8192},     // CRITICAL: 最大思考空间，低随机性
    };

    /**
     * 智能分配：根据任务复杂度自动选择模型和思考参数
     * - TRIVIAL/LOW → Flash（快速、低成本）
     * - MEDIUM/HIGH/CRITICAL → Pro（深度推理）
     */
    public String smartChat(String systemPrompt, String userMessage, TaskComplexity complexity) {
        int level = complexity.ordinal();
        float[] config = THINKING_CONFIGS[level];
        float temperature = config[0];
        int maxTokens = (int) config[1];

        // 选择模型：复杂任务用 Pro，简单任务用 Flash
        String selectedModel;
        if (level >= TaskComplexity.MEDIUM.ordinal()) {
            selectedModel = modelPro;
            log.debug("智能分配: 任务复杂度={}, 使用Pro模型, temp={}, maxTokens={}", complexity, temperature, maxTokens);
        } else {
            selectedModel = model;
            log.debug("智能分配: 任务复杂度={}, 使用Flash模型, temp={}, maxTokens={}", complexity, temperature, maxTokens);
        }

        return chatWithModel(systemPrompt, userMessage, selectedModel, temperature, maxTokens);
    }

    /**
     * 智能分配（带自定义maxTokens覆盖）
     */
    public String smartChat(String systemPrompt, String userMessage, TaskComplexity complexity, int maxTokensOverride) {
        int level = complexity.ordinal();
        float[] config = THINKING_CONFIGS[level];
        float temperature = config[0];

        String selectedModel = (level >= TaskComplexity.MEDIUM.ordinal()) ? modelPro : model;
        return chatWithModel(systemPrompt, userMessage, selectedModel, temperature, maxTokensOverride);
    }

    // ===== 任务类型快捷方法 =====

    /** 简单任务：关键词提取、格式转换、简单分类 */
    public String chatTrivial(String prompt, String message) {
        return smartChat(prompt, message, TaskComplexity.TRIVIAL);
    }

    /** 低复杂度：简单问答、翻译、摘要 */
    public String chatLow(String prompt, String message) {
        return smartChat(prompt, message, TaskComplexity.LOW);
    }

    /** 中等复杂度：资源生成、代码解释、知识讲解 */
    public String chatMedium(String prompt, String message) {
        return smartChat(prompt, message, TaskComplexity.MEDIUM);
    }

    /** 高复杂度：幻觉检测、事实校验、内容审核 */
    public String chatHigh(String prompt, String message) {
        return smartChat(prompt, message, TaskComplexity.HIGH);
    }

    /** 关键任务：学习路径规划、学情分析、个性化推荐 */
    public String chatCritical(String prompt, String message) {
        return smartChat(prompt, message, TaskComplexity.CRITICAL);
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 同步调用 LLM，返回完整回复
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, 0.5f, 2048);
    }

    public String chat(String systemPrompt, String userMessage, float temperature, int maxTokens) {
        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);

        try {
            return callApiWithRetry(messages, temperature, maxTokens);
        } catch (IOException e) {
            log.error("LLM调用失败", e);
            throw new LLMException("LLM调用失败: " + e.getMessage(), e);
        }
   }

   /**
    /**
     * 使用 Pro 模型（deepseek-v4-pro）调用 LLM — 适合需要深度推理的任务
     * Pro 是 reasoning 模型，推理过程会消耗 token，故默认 maxTokens 设为 4096
     */
    public String chatPro(String systemPrompt, String userMessage) {
        return chatWithModel(systemPrompt, userMessage, modelPro, 0.5f, 4096);
    }

    public String chatPro(String systemPrompt, String userMessage, float temperature, int maxTokens) {
        return chatWithModel(systemPrompt, userMessage, modelPro, temperature, maxTokens);
    }

    /**
    /**
     * 使用指定模型调用 LLM（支持不同任务使用不同模型）
     * 复杂推理任务建议用 deepseek-chat（V4 Pro），简单快速任务可用 deepseek-chat 并降低 maxTokens
     */
    public String chatWithModel(String systemPrompt, String userMessage, String modelName, float temperature, int maxTokens) {
        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);
        try {
            return callApi(messages, temperature, maxTokens, modelName);
        } catch (IOException e) {
            log.error("LLM调用失败 (model={})", modelName, e);
            throw new LLMException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 多轮对话调用
     */
    public String chatWithHistory(String systemPrompt, List<Map<String, String>> history, String userMessage, float temperature, int maxTokens) {
        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        // 添加历史对话
        if (history != null) {
            for (Map<String, String> msg : history) {
                JSONObject h = new JSONObject();
                h.put("role", msg.get("role"));
                h.put("content", msg.get("content"));
                messages.add(h);
            }
        }
        // 添加当前用户消息
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);

        try {
            return callApi(messages, temperature, maxTokens);
        } catch (IOException e) {
            log.error("LLM多轮调用失败", e);
            throw new LLMException("LLM多轮调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 带图片的聊天调用（多模态模型）
     * 如果当前模型不支持图片，回退到纯文本
     */
    public String chatWithImage(String systemPrompt, String imageBase64) {
        return chatWithImage(systemPrompt, imageBase64, 0.5f, 2048);
    }

    public String chatWithImage(String systemPrompt, String imageBase64, float temperature, int maxTokens) {
        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }

        // 构建多模态消息
        JSONArray content = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", "请分析这张图片");
        content.add(textPart);

        JSONObject imagePart = new JSONObject();
        imagePart.put("type", "image_url");
        JSONObject imageUrl = new JSONObject();
        // 如果已经是完整 data URL 直接用，否则补前缀
        if (imageBase64 != null && imageBase64.startsWith("data:")) {
            imageUrl.put("url", imageBase64);
        } else {
            imageUrl.put("url", "data:image/jpeg;base64," + imageBase64);
        }
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", content);
        messages.add(userMsg);

        try {
            return callApi(messages, temperature, maxTokens);
        } catch (IOException e) {
            log.error("LLM图片分析失败", e);
            throw new LLMException("LLM图片分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 文本关键词提取
     */
    public String extractKeywords(String text) {
        String prompt = "你是文本分析专家。请从以下文本中提取5-10个最关键的术语/关键词。\n只输出关键词，用逗号分隔。不要输出其他任何内容。\n文本内容：\n" + text;
        try {
            String result = chat(prompt, text, 0.3f, 150);
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.warn("关键词提取失败", e);
            return "";
        }
    }

    /**
     * 语义相似度评分
     */
    public float semanticSimilarity(String query, String document) {
        String prompt = "你是语义相似度评估专家。请评估以下两段文本的语义相关度。\n只输出一个0到1之间的数字，表示相关度（1=完全相关，0=完全不相关）。\n不要输出其他任何内容。\n\n文本1（查询）：\n" + query + "\n\n文本2（文档）：\n" + (document.length() > 500 ? document.substring(0, 500) : document);
        try {
            String result = chat(prompt, "评估相关度", 0.1f, 10);
            if (result != null) {
                String cleaned = result.trim().replaceAll("[^0-9.]", "");
                if (!cleaned.isEmpty()) {
                    return Math.max(0, Math.min(1, Float.parseFloat(cleaned)));
                }
            }
        } catch (Exception e) {
            log.warn("语义相似度评估失败", e);
        }
        return 0.5f;
    }

    /**
     * 流式调用 LLM — 通过 Consumer 逐 chunk 推送，配合 SSE 实现实时打字效果
     *
     * @param onChunk 每收到一个文本块时回调（在 caller 线程中执行）
     */
    public void chatStream(String systemPrompt, String userMessage, float temperature, int maxTokens,
                           Consumer<String> onChunk) throws IOException {
        chatStreamWithModel(systemPrompt, userMessage, model, temperature, maxTokens, onChunk);
    }

    /**
     * 智能流式调用 — 根据任务复杂度自动选择模型
     */
    public void smartChatStream(String systemPrompt, String userMessage, TaskComplexity complexity,
                                Consumer<String> onChunk) throws IOException {
        smartChatStreamWithHistory(systemPrompt, userMessage, complexity, onChunk, null);
    }

    /**
     * 智能流式调用（带对话历史）— 编程题页面传入前端构建的 history
     */
    public void smartChatStreamWithHistory(String systemPrompt, String userMessage, TaskComplexity complexity,
                                           Consumer<String> onChunk, List<Map<String, String>> history) throws IOException {
        int level = complexity.ordinal();
        float[] config = THINKING_CONFIGS[level];
        float temperature = config[0];
        int maxTokens = (int) config[1];

        String selectedModel = (level >= TaskComplexity.MEDIUM.ordinal()) ? modelPro : model;
        chatStreamWithModel(systemPrompt, userMessage, selectedModel, temperature, maxTokens, onChunk, history);
    }

    /**
     * 使用指定模型的流式调用
     */
    public void chatStreamWithModel(String systemPrompt, String userMessage, String modelName,
                                    float temperature, int maxTokens, Consumer<String> onChunk) throws IOException {
        chatStreamWithModel(systemPrompt, userMessage, modelName, temperature, maxTokens, onChunk, null);
    }

    /**
     * 使用指定模型的流式调用（带对话历史）
     */
    public void chatStreamWithModel(String systemPrompt, String userMessage, String modelName,
                                    float temperature, int maxTokens, Consumer<String> onChunk,
                                    List<Map<String, String>> history) throws IOException {
        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        // 插入对话历史（前端传入的最近 N 轮 user/assistant 消息）
        if (history != null) {
            for (Map<String, String> h : history) {
                String role = h.get("role");
                String content = h.get("content");
                if (role != null && content != null && !content.isEmpty()) {
                    JSONObject m = new JSONObject();
                    m.put("role", role);
                    m.put("content", content);
                    messages.add(m);
                }
            }
        }
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);

        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", true);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                log.error("LLM流式API HTTP错误: code={}, body={}", response.code(), respBody);
                throw new IOException("流式API错误[" + response.code() + "]: " + respBody);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String jsonStr = line.substring(6);
                    if ("[DONE]".equals(jsonStr.trim())) break;

                    JSONObject chunk = JSON.parseObject(jsonStr);
                    if (chunk.containsKey("error")) {
                        JSONObject err = chunk.getJSONObject("error");
                        String errMsg = err != null ? err.getString("message") : jsonStr;
                        throw new IOException("流式API业务错误: " + errMsg);
                    }
                    JSONArray choices = chunk.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                        if (delta != null) {
                            String content = delta.getString("content");
                            if (content != null && !content.isEmpty()) {
                                onChunk.accept(content);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 带指数退避重试的 API 调用 — 网络抖动自动恢复
     */
    private String callApiWithRetry(JSONArray messages, float temperature, int maxTokens) throws IOException {
        int maxRetries = 3;
        long baseDelayMs = 500;
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return callApi(messages, temperature, maxTokens);
            } catch (IOException e) {
                lastException = e;
                // 4xx 错误不重试（认证/参数问题重试也没用）
                String msg = e.getMessage();
                if (msg != null && (msg.contains("[401]") || msg.contains("[403]") || msg.contains("[400]"))) {
                    throw e;
                }
                if (attempt < maxRetries) {
                    long delay = baseDelayMs * (1L << attempt); // 500ms → 1s → 2s
                    log.warn("LLM调用失败，{}ms后重试(第{}次): {}", delay, attempt + 1, e.getMessage());
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw lastException;
    }

    /**
     * 核心API调用方法
     */
    private String callApi(JSONArray messages, float temperature, int maxTokens) throws IOException {
        return callApi(messages, temperature, maxTokens, null);
    }

    private String callApi(JSONArray messages, float temperature, int maxTokens, String modelOverride) throws IOException {
        JSONObject body = new JSONObject();
        body.put("model", modelOverride != null ? modelOverride : model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("LLM API HTTP错误: code={}, body={}", response.code(), respBody);
                throw new IOException("API错误[" + response.code() + "]: " + respBody);
            }
            JSONObject json = JSON.parseObject(respBody);
            // 检查 API 业务错误
            if (json.containsKey("error")) {
                JSONObject err = json.getJSONObject("error");
                String errMsg = err != null ? err.getString("message") : respBody;
                log.error("LLM API业务错误: {}", errMsg);
                throw new IOException("LLM API错误: " + (errMsg != null ? errMsg : respBody));
            }
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                String finishReason = choice.getString("finish_reason");
                if ("length".equals(finishReason)) {
                    log.warn("LLM输出被截断(finish_reason=length), 当前max_tokens不足, 需增大");
                }
                JSONObject message = choice.getJSONObject("message");
                if (message != null) {
                    String content = message.getString("content");
                    if (content != null && !content.isEmpty()) {
                        return content;
                    }
                }
            }
            log.warn("LLM返回空内容: {}", respBody.substring(0, Math.min(300, respBody.length())));
            return "";
        }
    }
}