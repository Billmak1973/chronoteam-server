package org.example.website.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Service
public class NotificationPushService {

    // 存儲 username -> List<SseEmitter> (一個用戶可能有多個標籤頁打開)
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void addEmitter(String username, SseEmitter emitter) {
        emitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public void removeEmitter(String username, SseEmitter emitter) {
        if (emitters.containsKey(username)) {
            emitters.get(username).remove(emitter);
        }
    }

    /**
     * 核心方法：向指定用戶推送通知更新
     * @param targetUsername 目標用戶名
     * @param type 通知類型 (REPLY, MENTION, LIKED_ME)
     * @param newCount 最新的未讀數量
     */
    public void pushNotificationUpdate(String targetUsername, String type, long newCount) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(targetUsername);
        if (userEmitters != null && !userEmitters.isEmpty()) {
            // 構建推送數據
            String eventData = String.format("{\"type\": \"%s\", \"count\": %d}", type, newCount);

            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().name("notification-update").data(eventData));
                } catch (IOException e) {
                    // 連接已斷開，移除
                    userEmitters.remove(emitter);
                }
            }
        }
    }
}