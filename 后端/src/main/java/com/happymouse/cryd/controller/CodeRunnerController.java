package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.service.CodeCompilerService;
import com.happymouse.cryd.service.CodeCompilerService.RunResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/code")
public class CodeRunnerController {

    private static final Logger log = LoggerFactory.getLogger(CodeRunnerController.class);
    private static final int MAX_CODE_SIZE = 65536;  // 64KB
    private static final int MAX_STDIN_SIZE = 32768;  // 32KB
    private static final int DEFAULT_TIMEOUT = 5;
    private static final long RATE_LIMIT_INTERVAL_MS = 2000L;

    private final CodeCompilerService codeCompiler;

    // 限流：按用户ID/IP记录最近调用时间戳，2秒内只允许1次
    private final ConcurrentHashMap<String, Long> lastCallTs = new ConcurrentHashMap<>();

    public CodeRunnerController(CodeCompilerService codeCompiler) {
        this.codeCompiler = codeCompiler;
    }

    @PostMapping("/run")
    public Result<RunResult> runCode(@RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        // 1. 提取参数
        String code = (String) body.get("code");
        String stdin = (String) body.getOrDefault("stdin", "");
        int timeout = body.containsKey("timeoutSeconds")
            ? ((Number) body.get("timeoutSeconds")).intValue()
            : DEFAULT_TIMEOUT;

        // 2. 参数校验
        if (code == null || code.isBlank()) {
            return Result.error("代码不能为空");
        }
        if (code.length() > MAX_CODE_SIZE) {
            return Result.error("代码长度超过限制（最大64KB）");
        }
        if (stdin != null && stdin.length() > MAX_STDIN_SIZE) {
            return Result.error("输入数据超过限制（最大32KB）");
        }
        if (timeout < 1 || timeout > 30) {
            timeout = DEFAULT_TIMEOUT;
        }

        // 3. 限流：优先按登录用户ID，未登录则按 IP（避免单 key 全局瓶颈）
        String limitKey = resolveLimitKey(request);
        long now = System.currentTimeMillis();
        Long last = lastCallTs.get(limitKey);
        if (last != null && now - last < RATE_LIMIT_INTERVAL_MS) {
            return Result.error("请求过于频繁，请" + (RATE_LIMIT_INTERVAL_MS - (now - last)) / 1000 + "秒后再试");
        }
        lastCallTs.put(limitKey, now);

        // 4. 如果 code 是 Markdown（含 ```c），优先提取代码块
        String extractedCode = CodeCompilerService.extractCCode(code);
        String cCode = (extractedCode != null) ? extractedCode : code;

        // 5. 调用编译运行
        try {
            RunResult result = codeCompiler.run(cCode, stdin != null ? stdin : "", timeout);
            return Result.success(result);
        } catch (Exception e) {
            log.error("代码运行异常", e);
            return Result.error("运行异常: " + e.getMessage());
        }
    }

    /**
     * 解析限流 key：已登录用户用 "u:{userId}"，未登录用 "ip:{ip}"
     */
    private String resolveLimitKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return "u:" + userId;
        }
        return "ip:" + request.getRemoteAddr();
    }
}
