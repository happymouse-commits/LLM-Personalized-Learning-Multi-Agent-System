package com.happymouse.cryd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置 - 密码编码器 + Bearer Token 认证
 */
@Configuration
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenFilter;

    public SecurityConfig(@Lazy TokenAuthenticationFilter tokenFilter) {
        this.tokenFilter = tokenFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 公开端点：登录/注册、WebSocket（握手时由 WebSocketConfig 校验 token）
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                // 媒体文件通过 <img>/<audio> 标签访问，无法携带 Authorization 头
                .requestMatchers("/api/chat/image/**").permitAll()
                .requestMatchers("/api/audio/**").permitAll()
                // PPT幻灯片图片通过 <img> 标签访问（含演示模式），无法携带 Authorization 头
                .requestMatchers("/api/ppt/slides/**").permitAll()
                // PPT 诊断接口（预渲染目录检测）与演示模式清理接口：登出后仍需可访问，无敏感信息
                .requestMatchers("/api/ppt/pre-rendered/**").permitAll()
                .requestMatchers("/api/ppt/demo-cleanup").permitAll()
                // 演示/导学模式：未登录访客使用的对话入口
                .requestMatchers("/api/chat/send").permitAll()
                .requestMatchers("/api/chat/stream").permitAll()
                // 演示模式语音朗读：TTS 合成接口需与对话入口同等公开（仅合成，缓存管理仍需登录）
                .requestMatchers("/api/tts/synthesize").permitAll()
                .requestMatchers("/api/tts/synthesize-simple").permitAll()
                // 演示模式语音识别：问卷语音输入需与对话入口同等公开
                .requestMatchers("/api/voice/recognize").permitAll()
                // 静态资源
                .requestMatchers("/assets/**").permitAll()
                .requestMatchers("/").permitAll()
                .requestMatchers("/*.html").permitAll()
                .requestMatchers("/*.css").permitAll()
                .requestMatchers("/*.js").permitAll()
                .requestMatchers("/*.svg").permitAll()
                .requestMatchers("/*.ico").permitAll()
                .requestMatchers("/*.png").permitAll()
                .requestMatchers("/*.jpg").permitAll()
                // 其余业务端点（TTS/语音/PPT/代码执行/Agent/资源/测试接口）一律要求登录
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
