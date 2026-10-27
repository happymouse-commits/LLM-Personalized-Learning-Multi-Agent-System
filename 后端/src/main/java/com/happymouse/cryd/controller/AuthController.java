package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.RateLimiter;
import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证API - 登录/注册 (委托给 AuthService)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    /** 注册：同一 IP 5分钟内最多 10 次 */
    private final RateLimiter registerLimiter = new RateLimiter(10, 5 * 60 * 1000L);
    /** 登录：同一 IP 1分钟内最多 20 次 */
    private final RateLimiter loginLimiter = new RateLimiter(20, 60 * 1000L);

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                              HttpServletRequest request) {
        String ip = getClientIp(request);
        if (!loginLimiter.tryAcquire(ip)) {
            return Result.error(429, "登录尝试过于频繁，请稍后再试");
        }
        return Result.success(authService.login(body.get("username"), body.get("password")));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body,
                                                 HttpServletRequest request) {
        String ip = getClientIp(request);
        if (!registerLimiter.tryAcquire(ip)) {
            long cooldown = registerLimiter.getCooldownMs(ip);
            return Result.error(429, "注册过于频繁，请" + (cooldown / 1000) + "秒后再试");
        }
        return Result.success(authService.register(body));
    }

    @GetMapping("/check-phone")
    public Result<Boolean> checkPhone(@RequestParam String phone) {
        return Result.success(authService.isPhoneTaken(phone));
    }

    @GetMapping("/check-student-id")
    public Result<Boolean> checkStudentId(@RequestParam String studentId) {
        return Result.success(authService.isStudentIdTaken(studentId));
    }

    private String getClientIp(HttpServletRequest request) {
        // 仅当直连来自受信任的反向代理（本机）时，才信任 X-Forwarded-For / X-Real-IP
        // 否则这些头可被客户端任意伪造，导致限流被绕过
        String remoteAddr = request.getRemoteAddr();
        boolean fromTrustedProxy = isTrustedProxy(remoteAddr);
        if (fromTrustedProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                // 取最左侧的客户端 IP（最接近真实客户端）
                return xff.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp.trim();
            }
        }
        return remoteAddr;
    }

    /**
     * 判断直连地址是否为受信任的反向代理
     */
    private static boolean isTrustedProxy(String addr) {
        if (addr == null) return false;
        return "127.0.0.1".equals(addr) || "0:0:0:0:0:0:0:1".equals(addr)
                || "::1".equals(addr) || "localhost".equals(addr);
    }
}
