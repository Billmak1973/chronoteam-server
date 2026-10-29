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

    // 原有配置 Key
    private static final String KEY_SHIPPING_FEE = "config:shipping:fee";
    private static final String KEY_FREE_SHIPPING_THRESHOLD = "config:shipping:threshold";
    private static final String KEY_DELIVERY_MODE = "config:delivery:mode";
    private static final String KEY_DELIVERY_SPECIFIC_DAYS = "config:delivery:specific_days";
    private static final String KEY_DELIVERY_CUSTOM_DAYS = "config:delivery:custom_days";
    private static final String KEY_RETURN_DAYS = "config:return:days";
    private static final String KEY_EXCHANGE_DAYS = "config:exchange:days";
    private static final String KEY_OFFLINE_PAYMENT_DAYS = "config:return:offline_payment_days";
    private static final String KEY_ONLINE_ORDER_RETENTION_DAYS = "config:return:online_order_retention_days";
    private static final String KEY_DELIVERY_START_TIME = "config:delivery:start_time";
    private static final String KEY_DELIVERY_END_TIME = "config:delivery:end_time";
    private static final String KEY_MAX_USER_ADDRESSES = "config:user:max_addresses";
    private static final String KEY_MAX_DELIVERY_DATE_OPTIONS = "config:delivery:max_date_options";
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

    // 【新增】權限配置 Key
    private static final String KEY_AFTER_SALES_STAFF_LOCK = "config:permissions:after_sales_staff_lock";
    private static final String KEY_AFTER_SALES_OVERRIDE_ROLES = "config:permissions:after_sales_override_roles";
    private static final String KEY_APPRAISAL_AUTHORIZED_ROLES = "config:permissions:appraisal_authorized_roles";

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
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權操作，僅限管理員"));
        }

        Map<String, String> dbConfigs = new HashMap<>();
        if (configs.containsKey("SHIPPING_FEE")) dbConfigs.put("SHIPPING_FEE", configs.get("SHIPPING_FEE"));
        if (configs.containsKey("FREE_SHIPPING_THRESHOLD")) dbConfigs.put("FREE_SHIPPING_THRESHOLD", configs.get("FREE_SHIPPING_THRESHOLD"));
        if (configs.containsKey("DELIVERY_MODE")) dbConfigs.put("DELIVERY_MODE", configs.get("DELIVERY_MODE"));
        if (configs.containsKey("DELIVERY_SPECIFIC_DAYS")) dbConfigs.put("DELIVERY_SPECIFIC_DAYS", configs.get("DELIVERY_SPECIFIC_DAYS"));
        if (configs.containsKey("DELIVERY_CUSTOM_DAYS")) dbConfigs.put("DELIVERY_CUSTOM_DAYS", configs.get("DELIVERY_CUSTOM_DAYS"));
        if (configs.containsKey("GLOBAL_CUTOFF_TIME")) dbConfigs.put("GLOBAL_CUTOFF_TIME", configs.get("GLOBAL_CUTOFF_TIME"));
        if (configs.containsKey("CUTOFF_DAY_OFFSET")) dbConfigs.put("CUTOFF_DAY_OFFSET", configs.get("CUTOFF_DAY_OFFSET"));
        if (configs.containsKey("CONTINUOUS_DAYS_MODE")) dbConfigs.put("CONTINUOUS_DAYS_MODE", configs.get("CONTINUOUS_DAYS_MODE"));

        systemConfigService.updateConfigs(dbConfigs);

        if (configs.containsKey("SHIPPING_FEE")) redisTemplate.opsForValue().set(KEY_SHIPPING_FEE, configs.get("SHIPPING_FEE"));
        if (configs.containsKey("FREE_SHIPPING_THRESHOLD")) redisTemplate.opsForValue().set(KEY_FREE_SHIPPING_THRESHOLD, configs.get("FREE_SHIPPING_THRESHOLD"));
        if (configs.containsKey("DELIVERY_MODE")) redisTemplate.opsForValue().set(KEY_DELIVERY_MODE, configs.get("DELIVERY_MODE"));
        if (configs.containsKey("DELIVERY_SPECIFIC_DAYS")) redisTemplate.opsForValue().set(KEY_DELIVERY_SPECIFIC_DAYS, configs.get("DELIVERY_SPECIFIC_DAYS"));
        if (configs.containsKey("DELIVERY_CUSTOM_DAYS")) redisTemplate.opsForValue().set(KEY_DELIVERY_CUSTOM_DAYS, configs.get("DELIVERY_CUSTOM_DAYS"));
        if (configs.containsKey("GLOBAL_CUTOFF_TIME")) redisTemplate.opsForValue().set(KEY_GLOBAL_CUTOFF_TIME, configs.get("GLOBAL_CUTOFF_TIME"));
        if (configs.containsKey("CUTOFF_DAY_OFFSET")) redisTemplate.opsForValue().set(KEY_CUTOFF_DAY_OFFSET, configs.get("CUTOFF_DAY_OFFSET"));
        if (configs.containsKey("CONTINUOUS_DAYS_MODE")) redisTemplate.opsForValue().set(KEY_CONTINUOUS_DAYS_MODE, configs.get("CONTINUOUS_DAYS_MODE"));

        return ResponseEntity.ok(Map.of("success", true, "message", "運費與配送設置已更新，顧客下次結賬將立即生效！"));
    }

    @PostMapping("/update-return-policy")
    public ResponseEntity<?> updateReturnPolicyConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權操作，僅限管理員"));
        }

        Map<String, String> dbConfigs = new HashMap<>();
        if (configs.containsKey("RETURN_DAYS")) dbConfigs.put("RETURN_DAYS", configs.get("RETURN_DAYS"));
        if (configs.containsKey("EXCHANGE_DAYS")) dbConfigs.put("EXCHANGE_DAYS", configs.get("EXCHANGE_DAYS"));
        if (configs.containsKey("OFFLINE_PAYMENT_DAYS")) dbConfigs.put("OFFLINE_PAYMENT_DAYS", configs.get("OFFLINE_PAYMENT_DAYS"));
        if (configs.containsKey("ONLINE_ORDER_RETENTION_DAYS")) dbConfigs.put("ONLINE_ORDER_RETENTION_DAYS", configs.get("ONLINE_ORDER_RETENTION_DAYS"));
        if (configs.containsKey("DELIVERY_START_TIME")) dbConfigs.put("DELIVERY_START_TIME", configs.get("DELIVERY_START_TIME"));
        if (configs.containsKey("DELIVERY_END_TIME")) dbConfigs.put("DELIVERY_END_TIME", configs.get("DELIVERY_END_TIME"));
        if (configs.containsKey("MAX_USER_ADDRESSES")) dbConfigs.put("MAX_USER_ADDRESSES", configs.get("MAX_USER_ADDRESSES"));
        if (configs.containsKey("MAX_DELIVERY_DATE_OPTIONS")) dbConfigs.put("MAX_DELIVERY_DATE_OPTIONS", configs.get("MAX_DELIVERY_DATE_OPTIONS"));

        systemConfigService.updateConfigs(dbConfigs);

        if (configs.containsKey("RETURN_DAYS")) redisTemplate.opsForValue().set(KEY_RETURN_DAYS, configs.get("RETURN_DAYS"));
        if (configs.containsKey("EXCHANGE_DAYS")) redisTemplate.opsForValue().set(KEY_EXCHANGE_DAYS, configs.get("EXCHANGE_DAYS"));
        if (configs.containsKey("OFFLINE_PAYMENT_DAYS")) redisTemplate.opsForValue().set(KEY_OFFLINE_PAYMENT_DAYS, configs.get("OFFLINE_PAYMENT_DAYS"));
        if (configs.containsKey("ONLINE_ORDER_RETENTION_DAYS")) redisTemplate.opsForValue().set(KEY_ONLINE_ORDER_RETENTION_DAYS, configs.get("ONLINE_ORDER_RETENTION_DAYS"));
        if (configs.containsKey("DELIVERY_START_TIME")) redisTemplate.opsForValue().set(KEY_DELIVERY_START_TIME, configs.get("DELIVERY_START_TIME"));
        if (configs.containsKey("DELIVERY_END_TIME")) redisTemplate.opsForValue().set(KEY_DELIVERY_END_TIME, configs.get("DELIVERY_END_TIME"));
        if (configs.containsKey("MAX_USER_ADDRESSES")) redisTemplate.opsForValue().set(KEY_MAX_USER_ADDRESSES, configs.get("MAX_USER_ADDRESSES"));
        if (configs.containsKey("MAX_DELIVERY_DATE_OPTIONS")) redisTemplate.opsForValue().set(KEY_MAX_DELIVERY_DATE_OPTIONS, configs.get("MAX_DELIVERY_DATE_OPTIONS"));

        return ResponseEntity.ok(Map.of("success", true, "message", "退換貨及網絡訂單保留政策已更新，將立即生效！"));
    }

    // ==========================================
    // 【新增】通用配置更新接口 (用於 Tab 4 權限設置)
    // ==========================================
    @PostMapping("/update")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權操作，僅限管理員"));
        }

        Map<String, String> dbConfigs = new HashMap<>();

        if (configs.containsKey("AFTER_SALES_STAFF_LOCK")) {
            dbConfigs.put("AFTER_SALES_STAFF_LOCK", configs.get("AFTER_SALES_STAFF_LOCK"));
            redisTemplate.opsForValue().set(KEY_AFTER_SALES_STAFF_LOCK, configs.get("AFTER_SALES_STAFF_LOCK"));
        }
        if (configs.containsKey("AFTER_SALES_OVERRIDE_ROLES")) {
            dbConfigs.put("AFTER_SALES_OVERRIDE_ROLES", configs.get("AFTER_SALES_OVERRIDE_ROLES"));
            redisTemplate.opsForValue().set(KEY_AFTER_SALES_OVERRIDE_ROLES, configs.get("AFTER_SALES_OVERRIDE_ROLES"));
        }
        if (configs.containsKey("APPRAISAL_AUTHORIZED_ROLES")) {
            dbConfigs.put("APPRAISAL_AUTHORIZED_ROLES", configs.get("APPRAISAL_AUTHORIZED_ROLES"));
            redisTemplate.opsForValue().set(KEY_APPRAISAL_AUTHORIZED_ROLES, configs.get("APPRAISAL_AUTHORIZED_ROLES"));
        }

        if (!dbConfigs.isEmpty()) {
            systemConfigService.updateConfigs(dbConfigs);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "權限設置已成功保存！"));
    }

    @GetMapping("/get")
    public ResponseEntity<?> getCurrentConfig() {
        Map<String, String> configs = new HashMap<>();

        configs.put("SHIPPING_FEE", redisTemplate.opsForValue().get(KEY_SHIPPING_FEE));
        configs.put("FREE_SHIPPING_THRESHOLD", redisTemplate.opsForValue().get(KEY_FREE_SHIPPING_THRESHOLD));
        configs.put("DELIVERY_MODE", redisTemplate.opsForValue().get(KEY_DELIVERY_MODE));
        configs.put("DELIVERY_SPECIFIC_DAYS", redisTemplate.opsForValue().get(KEY_DELIVERY_SPECIFIC_DAYS));
        configs.put("DELIVERY_CUSTOM_DAYS", redisTemplate.opsForValue().get(KEY_DELIVERY_CUSTOM_DAYS));
        configs.put("RETURN_DAYS", redisTemplate.opsForValue().get(KEY_RETURN_DAYS));
        configs.put("EXCHANGE_DAYS", redisTemplate.opsForValue().get(KEY_EXCHANGE_DAYS));
        configs.put("OFFLINE_PAYMENT_DAYS", redisTemplate.opsForValue().get(KEY_OFFLINE_PAYMENT_DAYS));
        configs.put("GLOBAL_CUTOFF_TIME", redisTemplate.opsForValue().get(KEY_GLOBAL_CUTOFF_TIME));
        configs.put("CUTOFF_DAY_OFFSET", redisTemplate.opsForValue().get(KEY_CUTOFF_DAY_OFFSET));
        configs.put("ONLINE_ORDER_RETENTION_DAYS", redisTemplate.opsForValue().get(KEY_ONLINE_ORDER_RETENTION_DAYS));
        configs.put("DELIVERY_START_TIME", redisTemplate.opsForValue().get(KEY_DELIVERY_START_TIME));
        configs.put("DELIVERY_END_TIME", redisTemplate.opsForValue().get(KEY_DELIVERY_END_TIME));
        configs.put("MAX_USER_ADDRESSES", redisTemplate.opsForValue().get(KEY_MAX_USER_ADDRESSES));
        configs.put("MAX_DELIVERY_DATE_OPTIONS", redisTemplate.opsForValue().get(KEY_MAX_DELIVERY_DATE_OPTIONS));

        // 【新增】返回 Tab 4 的權限配置
        configs.put("AFTER_SALES_STAFF_LOCK", redisTemplate.opsForValue().get(KEY_AFTER_SALES_STAFF_LOCK));
        configs.put("AFTER_SALES_OVERRIDE_ROLES", redisTemplate.opsForValue().get(KEY_AFTER_SALES_OVERRIDE_ROLES));
        configs.put("APPRAISAL_AUTHORIZED_ROLES", redisTemplate.opsForValue().get(KEY_APPRAISAL_AUTHORIZED_ROLES));

        return ResponseEntity.ok(configs);
    }

    @PostMapping("/update-notification")
    public ResponseEntity<?> updateNotificationConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權操作，僅限管理員"));
        }

        Map<String, String> dbConfigs = new HashMap<>();
        if (configs.containsKey("NOTIFICATION_TEXT")) dbConfigs.put("NOTIFICATION_TEXT", configs.get("NOTIFICATION_TEXT"));
        if (configs.containsKey("NOTIFICATION_TEXT_COLOR")) dbConfigs.put("NOTIFICATION_TEXT_COLOR", configs.get("NOTIFICATION_TEXT_COLOR"));
        if (configs.containsKey("NOTIFICATION_FONT_WEIGHT")) dbConfigs.put("NOTIFICATION_FONT_WEIGHT", configs.get("NOTIFICATION_FONT_WEIGHT"));
        if (configs.containsKey("NOTIFICATION_FONT_SIZE")) dbConfigs.put("NOTIFICATION_FONT_SIZE", configs.get("NOTIFICATION_FONT_SIZE"));
        if (configs.containsKey("NOTIFICATION_FONT_ITALIC")) dbConfigs.put("NOTIFICATION_FONT_ITALIC", configs.get("NOTIFICATION_FONT_ITALIC"));
        if (configs.containsKey("SCROLL_ENABLED")) dbConfigs.put("SCROLL_ENABLED", configs.get("SCROLL_ENABLED"));
        if (configs.containsKey("SCROLL_DIRECTION")) dbConfigs.put("SCROLL_DIRECTION", configs.get("SCROLL_DIRECTION"));
        if (configs.containsKey("SCROLL_SPEED")) dbConfigs.put("SCROLL_SPEED", configs.get("SCROLL_SPEED"));
        if (configs.containsKey("SCROLL_INTERVAL")) dbConfigs.put("SCROLL_INTERVAL", configs.get("SCROLL_INTERVAL"));

        systemConfigService.updateConfigs(dbConfigs);

        if (configs.containsKey("NOTIFICATION_TEXT")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_TEXT, configs.get("NOTIFICATION_TEXT"));
        if (configs.containsKey("NOTIFICATION_TEXT_COLOR")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_TEXT_COLOR, configs.get("NOTIFICATION_TEXT_COLOR"));
        if (configs.containsKey("NOTIFICATION_FONT_WEIGHT")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_FONT_WEIGHT, configs.get("NOTIFICATION_FONT_WEIGHT"));
        if (configs.containsKey("NOTIFICATION_FONT_SIZE")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_FONT_SIZE, configs.get("NOTIFICATION_FONT_SIZE"));
        if (configs.containsKey("NOTIFICATION_FONT_ITALIC")) redisTemplate.opsForValue().set(KEY_NOTIFICATION_FONT_ITALIC, configs.get("NOTIFICATION_FONT_ITALIC"));
        if (configs.containsKey("SCROLL_ENABLED")) redisTemplate.opsForValue().set(KEY_SCROLL_ENABLED, configs.get("SCROLL_ENABLED"));
        if (configs.containsKey("SCROLL_DIRECTION")) redisTemplate.opsForValue().set(KEY_SCROLL_DIRECTION, configs.get("SCROLL_DIRECTION"));
        if (configs.containsKey("SCROLL_SPEED")) redisTemplate.opsForValue().set(KEY_SCROLL_SPEED, configs.get("SCROLL_SPEED"));
        if (configs.containsKey("SCROLL_INTERVAL")) redisTemplate.opsForValue().set(KEY_SCROLL_INTERVAL, configs.get("SCROLL_INTERVAL"));

        return ResponseEntity.ok(Map.of("success", true, "message", "通知文字設置已成功保存！"));
    }

    @GetMapping("/get-notification")
    public ResponseEntity<?> getNotificationConfig() {
        Map<String, String> configs = new HashMap<>();

        configs.put("NOTIFICATION_TEXT", redisTemplate.opsForValue().get(KEY_NOTIFICATION_TEXT) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_TEXT) : "这里是通知文字预览");
        configs.put("NOTIFICATION_TEXT_COLOR", redisTemplate.opsForValue().get(KEY_NOTIFICATION_TEXT_COLOR) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_TEXT_COLOR) : "#FFFFFF");
        configs.put("NOTIFICATION_FONT_WEIGHT", redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_WEIGHT) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_WEIGHT) : "normal");
        configs.put("NOTIFICATION_FONT_SIZE", redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_SIZE) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_SIZE) : "14");
        configs.put("NOTIFICATION_FONT_ITALIC", redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_ITALIC) != null ? redisTemplate.opsForValue().get(KEY_NOTIFICATION_FONT_ITALIC) : "false");
        configs.put("SCROLL_ENABLED", redisTemplate.opsForValue().get(KEY_SCROLL_ENABLED) != null ? redisTemplate.opsForValue().get(KEY_SCROLL_ENABLED) : "true");
        configs.put("SCROLL_DIRECTION", redisTemplate.opsForValue().get(KEY_SCROLL_DIRECTION) != null ? redisTemplate.opsForValue().get(KEY_SCROLL_DIRECTION) : "rtl");
        configs.put("SCROLL_SPEED", redisTemplate.opsForValue().get(KEY_SCROLL_SPEED) != null ? redisTemplate.opsForValue().get(KEY_SCROLL_SPEED) : "normal");
        configs.put("SCROLL_INTERVAL", redisTemplate.opsForValue().get(KEY_SCROLL_INTERVAL) != null ? redisTemplate.opsForValue().get(KEY_SCROLL_INTERVAL) : "1");

        return ResponseEntity.ok(configs);
    }
}