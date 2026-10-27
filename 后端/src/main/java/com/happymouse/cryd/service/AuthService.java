package com.happymouse.cryd.service;

import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.SysUserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务 - 封装登录/注册业务逻辑
 */
@Service
public class AuthService {

    private static final long TOKEN_TTL_MS = TimeUnit.HOURS.toMillis(24); // Token有效期24小时

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();
    private final Map<String, String> tokenRoleStore = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenTimestamp = new ConcurrentHashMap<>(); // Token创建时间

    public AuthService(SysUserRepository sysUserRepository, PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录 - 支持用户名/手机号/学号
     */
    public Map<String, Object> login(String account, String password) {
        if (account == null || password == null) {
            throw new IllegalArgumentException("账号和密码不能为空");
        }

        Optional<SysUser> opt = sysUserRepository.findByUsername(account);
        if (opt.isEmpty()) opt = sysUserRepository.findByPhone(account);
        if (opt.isEmpty()) opt = sysUserRepository.findByStudentId(account);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        SysUser user = opt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if ("disabled".equals(user.getStatus())) {
            throw new IllegalStateException("账号已被禁用");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        data.put("className", user.getClassName());
        data.put("department", user.getDepartment());
        data.put("phone", user.getPhone());
        data.put("studentId", user.getStudentId());
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, user.getId());
        tokenRoleStore.put(token, user.getRole());
        tokenTimestamp.put(token, System.currentTimeMillis());
        data.put("token", token);
        return data;
    }

    /**
     * 注册 - 只需账号和密码，固定为学生角色
     */
    public Map<String, Object> register(Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank()) throw new IllegalArgumentException("账号不能为空");
        if (username.length() < 4) throw new IllegalArgumentException("账号至少4位");
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("密码不能为空");
        if (password.length() < 6) throw new IllegalArgumentException("密码至少6位");
        if (sysUserRepository.findByUsername(username).isPresent()) throw new IllegalArgumentException("该账号已被注册");

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);
        user.setRole("student");
        user.setStatus("active");
        SysUser saved = sysUserRepository.save(user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("username", saved.getUsername());
        data.put("role", saved.getRole());
        return data;
    }

    public boolean isPhoneTaken(String phone) {
        return sysUserRepository.findByPhone(phone).isPresent();
    }

    public boolean isStudentIdTaken(String studentId) {
        return sysUserRepository.findByStudentId(studentId).isPresent();
    }

    /**
     * 验证Token并返回用户ID
     */
    public Long getUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) return null;
        return tokenStore.get(token);
    }

    /**
     * 从Token获取用户角色
     */
    public String getRoleFromToken(String token) {
        if (token == null || token.isEmpty()) return null;
        return tokenRoleStore.get(token);
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        if (token == null || !tokenStore.containsKey(token)) return false;
        // 检查Token是否过期
        Long createdAt = tokenTimestamp.get(token);
        if (createdAt != null && System.currentTimeMillis() - createdAt > TOKEN_TTL_MS) {
            removeToken(token);
            return false;
        }
        return true;
    }

    /**
     * 登出 - 删除指定Token
     */
    public void logout(String token) {
        removeToken(token);
    }

    /**
     * 清理过期Token — 每小时自动执行一次
     */
    @Scheduled(fixedRate = 60 * 60 * 1000L)
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        tokenTimestamp.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > TOKEN_TTL_MS) {
                tokenStore.remove(entry.getKey());
                tokenRoleStore.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void removeToken(String token) {
        tokenStore.remove(token);
        tokenRoleStore.remove(token);
        tokenTimestamp.remove(token);
    }
}