package com.happymouse.cryd.service;

import com.alibaba.fastjson2.JSON;
import com.happymouse.cryd.config.WebSocketConfig;
import com.happymouse.cryd.model.dto.ResourceButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

@Service
public class WsPushService {

    private static final Logger log = LoggerFactory.getLogger(WsPushService.class);

    public void pushResourceUpdate(Long studentId) {
        push(studentId, "resources", null);
    }

    public void pushResourceUpdate(Long studentId, List<ResourceButton> buttons) {
        push(studentId, "resources", buttons);
    }

    public void pushProfileUpdate(Long studentId) {
        push(studentId, "profile", null);
    }

    private void push(Long studentId, String type, Object data) {
        var sessions = WebSocketConfig.STUDENT_SESSIONS.get(studentId);
        if (sessions == null || sessions.isEmpty()) return;

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", type);
        payload.put("studentId", studentId);
        payload.put("timestamp", System.currentTimeMillis());
        if (data != null) {
            payload.put("data", data);
        }

        String json = JSON.toJSONString(payload);
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            try { s.sendMessage(msg); } catch (Exception ignored) {}
        }
    }
}
