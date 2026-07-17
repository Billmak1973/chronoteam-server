package org.example.website.controller;

import org.example.website.service.SystemConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map; // <--- 確保導入這個

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {
    private final SystemConfigService configService;

    public AdminConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @PostMapping("/update-shipping")
    public ResponseEntity<?> updateShippingConfig(@RequestBody Map<String, String> configs, Authentication authentication) {
        // 1. 權限校驗
        if (!"admin".equals(authentication.getName())) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "無權操作");
            return ResponseEntity.status(403).body(errorResp);
        }

        // 2. 執行更新
        configService.updateConfigs(configs);

        // 3. 【關鍵修改】返回 JSON 對象，而不是純字符串
        Map<String, Object> successResp = new HashMap<>();
        successResp.put("success", true);
        successResp.put("message", " 運費設置已更新，顧客下次結賬將立即生效！");

        return ResponseEntity.ok(successResp);
    }

    // 建議：添加一個 GET 接口讓前端加載當前數值
    @GetMapping("/get")
    public ResponseEntity<?> getCurrentConfig() {
        Map<String, String> configs = new HashMap<>();
        configs.put("SHIPPING_FEE", configService.getShippingFee().toString());
        configs.put("FREE_SHIPPING_THRESHOLD", configService.getFreeShippingThreshold().toString());
        return ResponseEntity.ok(configs);
    }
}