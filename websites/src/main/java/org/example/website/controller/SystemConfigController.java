package org.example.website.controller;

import org.example.website.entity.SystemConfig;
import org.example.website.repository.SystemConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigController(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    /**
     * 獲取滾動通知配置
     */
    @GetMapping("/notification")
    public ResponseEntity<Map<String, String>> getNotificationConfig() {
        Map<String, String> config = new HashMap<>();

        // 獲取通知文字
        config.put("notificationText",
                systemConfigRepository.findById("NOTIFICATION_TEXT")
                        .map(SystemConfig::getConfigValue)
                        .orElse(""));

        // 獲取文字顏色
        config.put("textColor",
                systemConfigRepository.findById("NOTIFICATION_TEXT_COLOR")
                        .map(SystemConfig::getConfigValue)
                        .orElse("#FFFFFF"));

        // 獲取字體粗細
        config.put("fontWeight",
                systemConfigRepository.findById("NOTIFICATION_FONT_WEIGHT")
                        .map(SystemConfig::getConfigValue)
                        .orElse("normal"));

        // 獲取字體大小
        config.put("fontSize",
                systemConfigRepository.findById("NOTIFICATION_FONT_SIZE")
                        .map(SystemConfig::getConfigValue)
                        .orElse("14"));

        // 獲取是否斜體
        config.put("fontItalic",
                systemConfigRepository.findById("NOTIFICATION_FONT_ITALIC")
                        .map(SystemConfig::getConfigValue)
                        .orElse("false"));

        // 獲取是否啟用滾動
        config.put("scrollEnabled",
                systemConfigRepository.findById("SCROLL_ENABLED")
                        .map(SystemConfig::getConfigValue)
                        .orElse("true")); // 默認啟用

        // 獲取滾動方向
        config.put("scrollDirection",
                systemConfigRepository.findById("NOTIFICATION_SCROLL_DIRECTION")
                        .map(SystemConfig::getConfigValue)
                        .orElse("rtl"));

        // 獲取滾動速度
        config.put("scrollSpeed",
                systemConfigRepository.findById("NOTIFICATION_SCROLL_SPEED")
                        .map(SystemConfig::getConfigValue)
                        .orElse("normal"));

        return ResponseEntity.ok(config);
    }
}