package org.example.website.controller;

import org.example.website.entity.User;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.SystemConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {
    private final SystemConfigService configService;

    public AdminConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    /**
     * 核心修復：權限校驗基於 user_type (Role == ADMIN)，而非用戶名是否等於 "admin"
     * CustomUserDetails 在登入時已從數據庫載入 Role 枚舉，直接判斷，零查庫開銷
     */
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.getRole() == User.Role.ADMIN;
    }

    /**
     * 更新運費設置
     */
    @PostMapping("/update-shipping")
    public ResponseEntity<?> updateShippingConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        // 1. 權限校驗 (使用 isAdmin 輔助方法)
        if (!isAdmin(authentication)) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "無權操作，僅限管理員");
            return ResponseEntity.status(403).body(errorResp);
        }

        // 2. 執行更新
        configService.updateConfigs(configs);

        // 3. 返回 JSON 對象
        Map<String, Object> successResp = new HashMap<>();
        successResp.put("success", true);
        successResp.put("message", "運費設置已更新，顧客下次結賬將立即生效！");

        return ResponseEntity.ok(successResp);
    }

    /**
     * 更新退換貨政策設置
     */
    @PostMapping("/update-return-policy")
    public ResponseEntity<?> updateReturnPolicyConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        // 1. 權限校驗 (使用 isAdmin 輔助方法)
        if (!isAdmin(authentication)) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "無權操作，僅限管理員");
            return ResponseEntity.status(403).body(errorResp);
        }

        // 2. 執行更新
        configService.updateConfigs(configs);

        // 3. 返回 JSON 對象
        Map<String, Object> successResp = new HashMap<>();
        successResp.put("success", true);
        successResp.put("message", "退換貨政策已更新，將立即生效！");

        return ResponseEntity.ok(successResp);
    }

    /**
     * 獲取所有配置 (供前端加載當前數值)
     */
    @GetMapping("/get")
    public ResponseEntity<?> getCurrentConfig() {
        Map<String, String> configs = new HashMap<>();

        // 運費設置
        configs.put("SHIPPING_FEE", configService.getShippingFee().toString());
        configs.put("FREE_SHIPPING_THRESHOLD", configService.getFreeShippingThreshold().toString());

        // 退換貨政策設置
        configs.put("RETURN_DAYS", configService.getReturnDays().toString());
        configs.put("EXCHANGE_DAYS", configService.getExchangeDays().toString());

        return ResponseEntity.ok(configs);
    }
}