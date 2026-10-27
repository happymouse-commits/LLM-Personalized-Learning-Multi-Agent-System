package com.happymouse.cryd.common;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易 IP 级速率限制器 — 内存实现，适用于单实例部署。
 * 每个 IP 在窗口时间内最多允许 maxAttempts 次请求。
 * 自动清理过期条目，防止内存泄漏。
 */
public class RateLimiter {

    private final ConcurrentHashMap<String, long[]> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowMs;

    /**
     * @param maxAttempts 窗口内最大允许次数
     * @param windowMs   时间窗口（毫秒）
     */
    public RateLimiter(int maxAttempts, long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
    }

    /**
     * 检查是否允许请求。允许则记录一次并返回 true。
     */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        long[] record = attempts.compute(key, (k, v) -> {
            if (v == null || now - v[0] > windowMs) {
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        });
        return record[1] <= maxAttempts;
    }

    /**
     * 获取剩余冷却时间（毫秒），0 表示未被限制。
     */
    public long getCooldownMs(String key) {
        long[] record = attempts.get(key);
        if (record == null) return 0;
        long elapsed = System.currentTimeMillis() - record[0];
        if (elapsed > windowMs) return 0;
        return windowMs - elapsed;
    }

    /**
     * 清理所有过期的条目，防止内存无限增长。
     * 建议定期调用（如每5分钟）。
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, long[]>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, long[]> entry = it.next();
            if (now - entry.getValue()[0] > windowMs) {
                it.remove();
            }
        }
    }
}
