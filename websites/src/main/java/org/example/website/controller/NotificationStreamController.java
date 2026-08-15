package org.example.website.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.example.website.service.NotificationPushService; // 下面會創建這個 Service

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
public class NotificationStreamController {

    private final NotificationPushService pushService;

    public NotificationStreamController(NotificationPushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping(value = "/api/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("未登入");
        }
        String username = authentication.getName();

        // 創建一個超時時間很長的 Emitter (例如 1 小時)
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);

        // 將這個 emitter 註冊到 Service 中，以便後續推送
        pushService.addEmitter(username, emitter);

        // 處理連接關閉/超時/錯誤
        emitter.onCompletion(() -> pushService.removeEmitter(username, emitter));
        emitter.onTimeout(() -> pushService.removeEmitter(username, emitter));
        emitter.onError((e) -> pushService.removeEmitter(username, emitter));

        // 發送初始連接成功消息
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}