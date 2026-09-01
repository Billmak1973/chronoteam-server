package org.example.website.controller;

import org.example.website.entity.User;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.SystemConfigService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    private final SystemConfigService systemConfigService;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_SHIPPING_FEE = "config:shipping:fee";
    private static final String KEY_FREE_SHIPPING_THRESHOLD = "config:shipping:threshold";
    private static final String KEY_DELIVERY_MODE = "config:delivery:mode";
    private static final String KEY_DELIVERY_SPECIFIC_DAYS = "config:delivery:specific_days";
    private static final String KEY_DELIVERY_CUSTOM_DAYS = "config:delivery:custom_days";
    private static final String KEY_RETURN_DAYS = "config:return:days";
    private static final String KEY_EXCHANGE_DAYS = "config:exchange:days";
    private static final String KEY_OFFLINE_PAYMENT_DAYS = "config:return:offline_payment_days";

    // 【新增】全局截单时间与偏移量
    private static final String KEY_GLOBAL_CUTOFF_TIME = "config:delivery:global_cutoff_time";
    private static final String KEY_CUTOFF_DAY_OFFSET = "config:delivery:cutoff_day_offset";
    private static final String KEY_CONTINUOUS_DAYS_MODE = "config:delivery:continuous_days_mode";

    private static final String KEY_NOTIFICATION_TEXT = "config:notification:text";
    private static final String KEY_NOTIFICATION_TEXT_COLOR = "config:notification:text_color";
    private static final String KEY_NOTIFICATION_FONT_WEIGHT = "config:notification:font_weight";
    private static final String KEY_NOTIFICATION_FONT_SIZE = "config:notification:font_size";
    private static final String KEY_NOTIFICATION_FONT_ITALIC = "config:notification:font_italic";
    private static final String KEY_SCROLL_ENABLED = "config:ui:scroll_enabled";
    private static final String KEY_SCROLL_DIRECTION = "config:ui:scroll_direction";
    private static final String KEY_SCROLL_SPEED = "config:ui:scroll_speed";
    private static final String KEY_SCROLL_INTERVAL = "config:ui:scroll_interval";

    public AdminConfigController(SystemConfigService systemConfigService, StringRedisTemplate redisTemplate) {
        this.systemConfigService = systemConfigService;
        this.redisTemplate = redisTemplate;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.getRole() == User.Role.ADMIN;
    }

    @PostMapping("/update-shipping")
    public ResponseEntity<?> updateShippingConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        if (!isAdmin(authentication)) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "無權操作，僅限管理員");
            return ResponseEntity.status(403).body(errorResp);
        }

        // 1. 保存到 Redis
        if (configs.containsKey("SHIPPING_FEE")) {
            redisTemplate.opsForValue().set(KEY_SHIPPING_FEE, configs.get("SHIPPING_FEE"));
        }
        if (configs.containsKey("FREE_SHIPPING_THRESHOLD")) {
            redisTemplate.opsForValue().set(KEY_FREE_SHIPPING_THRESHOLD, configs.get("FREE_SHIPPING_THRESHOLD"));
        }
        if (configs.containsKey("DELIVERY_MODE")) {
            redisTemplate.opsForValue().set(KEY_DELIVERY_MODE, configs.get("DELIVERY_MODE"));
        }
        if (configs.containsKey("DELIVERY_SPECIFIC_DAYS")) {
            redisTemplate.opsForValue().set(KEY_DELIVERY_SPECIFIC_DAYS, configs.get("DELIVERY_SPECIFIC_DAYS"));
        }
        if (configs.containsKey("DELIVERY_CUSTOM_DAYS")) {
            redisTemplate.opsForValue().set(KEY_DELIVERY_CUSTOM_DAYS, configs.get("DELIVERY_CUSTOM_DAYS"));
        }
        // 【新增】保存全局截单时间与偏移量
        if (configs.containsKey("GLOBAL_CUTOFF_TIME")) {
            redisTemplate.opsForValue().set(KEY_GLOBAL_CUTOFF_TIME, configs.get("GLOBAL_CUTOFF_TIME"));
        }
        if (configs.containsKey("CUTOFF_DAY_OFFSET")) {
            redisTemplate.opsForValue().set(KEY_CUTOFF_DAY_OFFSET, configs.get("CUTOFF_DAY_OFFSET"));
        }
        if (configs.containsKey("CONTINUOUS_DAYS_MODE")) {
            redisTemplate.opsForValue().set(KEY_CONTINUOUS_DAYS_MODE, configs.get("CONTINUOUS_DAYS_MODE"));
        }

        // 2. 同時保存到數據庫
        Map<String, String> dbConfigs = new HashMap<>();
        if (configs.containsKey("SHIPPING_FEE")) {
            dbConfigs.put("SHIPPING_FEE", configs.get("SHIPPING_FEE"));
        }
        if (configs.containsKey("FREE_SHIPPING_THRESHOLD")) {
            dbConfigs.put("FREE_SHIPPING_THRESHOLD", configs.get("FREE_SHIPPING_THRESHOLD"));
        }
        if (configs.containsKey("DELIVERY_MODE")) {
            dbConfigs.put("DELIVERY_MODE", configs.get("DELIVERY_MODE"));
        }
        if (configs.containsKey("DELIVERY_SPECIFIC_DAYS")) {
            dbConfigs.put("DELIVERY_SPECIFIC_DAYS", configs.get("DELIVERY_SPECIFIC_DAYS"));
        }
        if (configs.containsKey("DELIVERY_CUSTOM_DAYS")) {
            dbConfigs.put("DELIVERY_CUSTOM_DAYS", configs.get("DELIVERY_CUSTOM_DAYS"));
        }
        // 【新增】保存全局截单时间与偏移量
        if (configs.containsKey("GLOBAL_CUTOFF_TIME")) {
            dbConfigs.put("GLOBAL_CUTOFF_TIME", configs.get("GLOBAL_CUTOFF_TIME"));
        }
        if (configs.containsKey("CUTOFF_DAY_OFFSET")) {
            dbConfigs.put("CUTOFF_DAY_OFFSET", configs.get("CUTOFF_DAY_OFFSET"));
        }
        if (configs.containsKey("CONTINUOUS_DAYS_MODE")) {
            dbConfigs.put("CONTINUOUS_DAYS_MODE", configs.get("CONTINUOUS_DAYS_MODE"));
        }


        // 調用 Service 保存到數據庫
        systemConfigService.updateConfigs(dbConfigs);

        Map<String, Object> successResp = new HashMap<>();
        successResp.put("success", true);
        successResp.put("message", "運費與配送設置已更新，顧客下次結賬將立即生效！");

        return ResponseEntity.ok(successResp);
    }

    @PostMapping("/update-return-policy")
    public ResponseEntity<?> updateReturnPolicyConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        if (!isAdmin(authentication)) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "無權操作，僅限管理員");
            return ResponseEntity.status(403).body(errorResp);
        }

        // 1. 保存到 Redis
        if (configs.containsKey("RETURN_DAYS")) {
            redisTemplate.opsForValue().set(KEY_RETURN_DAYS, configs.get("RETURN_DAYS"));
        }
        if (configs.containsKey("EXCHANGE_DAYS")) {
            redisTemplate.opsForValue().set(KEY_EXCHANGE_DAYS, configs.get("EXCHANGE_DAYS"));
        }
        if (configs.containsKey("OFFLINE_PAYMENT_DAYS")) {
            redisTemplate.opsForValue().set(KEY_OFFLINE_PAYMENT_DAYS, configs.get("OFFLINE_PAYMENT_DAYS"));
        }

        // 2. 【核心修復】同時保存到數據庫，防止重啟後配置丟失
        Map<String, String> dbConfigs = new HashMap<>();
        if (configs.containsKey("RETURN_DAYS")) {
            dbConfigs.put("RETURN_DAYS", configs.get("RETURN_DAYS"));
        }
        if (configs.containsKey("EXCHANGE_DAYS")) {
            dbConfigs.put("EXCHANGE_DAYS", configs.get("EXCHANGE_DAYS"));
        }
        if (configs.containsKey("OFFLINE_PAYMENT_DAYS")) {
            dbConfigs.put("OFFLINE_PAYMENT_DAYS", configs.get("OFFLINE_PAYMENT_DAYS"));
        }

        // 調用 Service 批量保存到數據庫
        systemConfigService.updateConfigs(dbConfigs);

        Map<String, Object> successResp = new HashMap<>();
        successResp.put("success", true);
        successResp.put("message", "退換貨及線下取貨政策已更新，將立即生效！");

        return ResponseEntity.ok(successResp);
    }

    @GetMapping("/get")
    public ResponseEntity<?> getCurrentConfig() {
        Map<String, String> configs = new HashMap<>();

        String shippingFee = redisTemplate.opsForValue().get(KEY_SHIPPING_FEE);
        configs.put("SHIPPING_FEE", shippingFee != null ? shippingFee : "50");

        String freeThreshold = redisTemplate.opsForValue().get(KEY_FREE_SHIPPING_THRESHOLD);
        configs.put("FREE_SHIPPING_THRESHOLD", freeThreshold != null ? freeThreshold : "50000");

        String deliveryMode = redisTemplate.opsForValue().get(KEY_DELIVERY_MODE);
        configs.put("DELIVERY_MODE", deliveryMode != null ? deliveryMode : "NEXT_DAY");

        String specificDays = redisTemplate.opsForValue().get(KEY_DELIVERY_SPECIFIC_DAYS);
        configs.put("DELIVERY_SPECIFIC_DAYS", specificDays != null ? specificDays : "6,7");

        String customDays = redisTemplate.opsForValue().get(KEY_DELIVERY_CUSTOM_DAYS);
        configs.put("DELIVERY_CUSTOM_DAYS", customDays != null ? customDays : "3");

        String returnDays = redisTemplate.opsForValue().get(KEY_RETURN_DAYS);
        configs.put("RETURN_DAYS", returnDays != null ? returnDays : "0");

        String exchangeDays = redisTemplate.opsForValue().get(KEY_EXCHANGE_DAYS);
        configs.put("EXCHANGE_DAYS", exchangeDays != null ? exchangeDays : "0");

        String offlinePaymentDays = redisTemplate.opsForValue().get(KEY_OFFLINE_PAYMENT_DAYS);
        configs.put("OFFLINE_PAYMENT_DAYS", offlinePaymentDays != null ? offlinePaymentDays : "3");

        // 【新增】获取全局截单时间与偏移量
        String globalCutoffTime = redisTemplate.opsForValue().get(KEY_GLOBAL_CUTOFF_TIME);
        configs.put("GLOBAL_CUTOFF_TIME", globalCutoffTime != null ? globalCutoffTime : "16:00");

        String cutoffDayOffset = redisTemplate.opsForValue().get(KEY_CUTOFF_DAY_OFFSET);
        configs.put("CUTOFF_DAY_OFFSET", cutoffDayOffset != null ? cutoffDayOffset : "-1");

        return ResponseEntity.ok(configs);
    }

    // ==========================================
    // 【新增】通知文字設置相關接口
    // ==========================================

    @PostMapping("/update-notification")
    public ResponseEntity<?> updateNotificationConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        if (!isAdmin(authentication)) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "無權操作，僅限管理員");
            return ResponseEntity.status(403).body(errorResp);
        }

        // 1. 保存到 Redis
        if (configs.containsKey("NOTIFICATION_TEXT")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_TEXT, configs.get("NOTIFICATION_TEXT"));
        if (configs.containsKey("NOTIFICATION_TEXT_COLOR")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_TEXT_COLOR, configs.get("NOTIFICATION_TEXT_COLOR"));
        if (configs.containsKey("NOTIFICATION_FONT_WEIGHT")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_FONT_WEIGHT, configs.get("NOTIFICATION_FONT_WEIGHT"));
        if (configs.containsKey("NOTIFICATION_FONT_SIZE")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_FONT_SIZE, configs.get("NOTIFICATION_FONT_SIZE"));
        if (configs.containsKey("NOTIFICATION_FONT_ITALIC")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_FONT_ITALIC, configs.get("NOTIFICATION_FONT_ITALIC"));
        if (configs.containsKey("SCROLL_ENABLED")) {
            redisTemplate.opsForValue().set(KEY_SCROLL_ENABLED, configs.get("SCROLL_ENABLED"));
        }
        if (configs.containsKey("SCROLL_DIRECTION")) {
            redisTemplate.opsForValue().set(KEY_SCROLL_DIRECTION, configs.get("SCROLL_DIRECTION"));
        }
        if (configs.containsKey("SCROLL_SPEED")) {
            redisTemplate.opsForValue().set(KEY_SCROLL_SPEED, configs.get("SCROLL_SPEED"));
        }
        if (configs.containsKey("SCROLL_INTERVAL")) {
            redisTemplate.opsForValue().set(KEY_SCROLL_INTERVAL, configs.get("SCROLL_INTERVAL"));
        }

        // 2. 同時保存到數據庫
        systemConfigService.updateConfigs(configs);

        Map<String, Object> successResp = new HashMap<>();
        successResp.put("success", true);
        successResp.put("message", "通知文字設置已成功保存！");

        return ResponseEntity.ok(successResp);
    }

    @GetMapping("/get-notification")
    public ResponseEntity<?> getNotificationConfig() {
        Map<String, String> configs = new HashMap<>();

        configs.put("NOTIFICATION_TEXT", redisTemplate.opsForValue().get(KEY_NOTIFICATION_TEXT) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_TEXT) : "这里是通知文字预览");
        configs.put("NOTIFICATION_TEXT_COLOR", redisTemplate.opsForValue().get(KEY_NOTIFICATION_TEXT_COLOR) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_TEXT_COLOR) : "#FFFFFF");
        configs.put("NOTIFICATION_FONT_WEIGHT", redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_WEIGHT) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_WEIGHT) : "normal");
        configs.put("NOTIFICATION_FONT_SIZE", redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_SIZE) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_SIZE) : "14");
        configs.put("NOTIFICATION_FONT_ITALIC", redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_ITALIC) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_ITALIC) : "false");
        configs.put("SCROLL_ENABLED", redisTemplate.opsForValue().get(KEY_SCROLL_ENABLED) != null
                ? redisTemplate.opsForValue().get(KEY_SCROLL_ENABLED) : "true");
        configs.put("SCROLL_DIRECTION", redisTemplate.opsForValue().get(KEY_SCROLL_DIRECTION) != null
                ? redisTemplate.opsForValue().get(KEY_SCROLL_DIRECTION) : "rtl");
        configs.put("SCROLL_SPEED", redisTemplate.opsForValue().get(KEY_SCROLL_SPEED) != null
                ? redisTemplate.opsForValue().get(KEY_SCROLL_SPEED) : "normal");
        configs.put("SCROLL_INTERVAL",
                redisTemplate.opsForValue().get(KEY_SCROLL_INTERVAL) != null
                        ? redisTemplate.opsForValue().get(KEY_SCROLL_INTERVAL)
                        : "1"); // 默認值為 1 秒
        return ResponseEntity.ok(configs);
    }
}