package com.happymouse.cryd.config;

import com.happymouse.cryd.service.AuthService;
import com.happymouse.cryd.service.WsPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    /** sessionId → 学生ID */
    static final Map<String, Long> SESSION_STUDENTS = new ConcurrentHashMap<>();
    /** 学生ID → 已连接的 session 集合 */
    public static final Map<Long, CopyOnWriteArraySet<WebSocketSession>> STUDENT_SESSIONS = new ConcurrentHashMap<>();

    private final AuthService authService;

    public WebSocketConfig(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new StudentWsHandler(authService), "/ws/student")
                .setAllowedOriginPatterns("*");
    }

    static class StudentWsHandler extends TextWebSocketHandler {

        private final AuthService authService;

        StudentWsHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            // 解析 URL 查询参数: /ws/student?token=xxx
            String query = session.getUri() != null ? session.getUri().getQuery() : null;
            String token = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6);
                        break;
                    }
                }
            }

            // Token 校验：必须携带有效 token
            if (token == null || token.isEmpty()) {
                log.warn("WS连接拒绝: 缺少token参数, sessionId={}", session.getId());
                closeQuietly(session, CloseStatus.POLICY_VIOLATION);
                return;
            }
            if (!authService.validateToken(token)) {
                log.warn("WS连接拒绝: token无效或已过期, sessionId={}", session.getId());
                closeQuietly(session, CloseStatus.POLICY_VIOLATION);
                return;
            }

            // 从 token 反查 studentId（不再信任客户端传入的 studentId 参数）
            Long sid = authService.getUserIdFromToken(token);
            if (sid == null) {
                log.warn("WS连接拒绝: token无法解析用户, sessionId={}", session.getId());
                closeQuietly(session, CloseStatus.POLICY_VIOLATION);
                return;
            }

            SESSION_STUDENTS.put(session.getId(), sid);
            STUDENT_SESSIONS.computeIfAbsent(sid, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("WS连接: studentId={}, sessionId={}", sid, session.getId());
        }

        private static void closeQuietly(WebSocketSession session, CloseStatus status) {
            try {
                session.close(status);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            Long sid = SESSION_STUDENTS.remove(session.getId());
            if (sid != null) {
                var set = STUDENT_SESSIONS.get(sid);
                if (set != null) { set.remove(session); if (set.isEmpty()) STUDENT_SESSIONS.remove(sid); }
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            // 心跳/ignore incoming
        }
    }
}
