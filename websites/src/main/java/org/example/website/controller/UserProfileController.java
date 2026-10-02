package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.UserAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用戶資料管理", description = "用戶個人資料更新及管理員用戶列表查詢相關接口")
public class UserProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 注入 UserAddressService 處理獨立地址表
    private final UserAddressService userAddressService;

    public UserProfileController(UserAddressService userAddressService) {
        this.userAddressService = userAddressService;
    }

    /**
     * 輔助方法：判斷是否為員工角色 (非普通顧客)
     */
    private boolean isStaffRole(User.Role role) {
        return role == User.Role.ADMIN ||
                role == User.Role.SALES ||
                role == User.Role.COURIER ||
                role == User.Role.APPRAISER;
    }

    @Operation(
            summary = "更新用戶個人資料",
            description = "允許用戶更新用戶名、地址和備用地址。員工角色(ADMIN/SALES/COURIER/APPRAISER)還可更新郵箱、手機和工作電話。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功，或返回特定業務提示", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效"),
            @ApiResponse(responseCode = "404", description = "用戶不存在")
    })
    @PutMapping("/update-profile")
    public ResponseEntity<?> updateProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "需要更新的字段鍵值對。支持的 key: 'username', 'address', 'backupAddress', 'email', 'phone', 'workPhone'。空字符串表示清空該字段。",
                    required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"username\": \"new_username\", \"address\": \"九龍尖沙咀\", \"backupAddress\": \"\"}"
                            )
                    )
            )
            @RequestBody Map<String, String> updates,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        // 1. 獲取當前登錄用戶
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String oldUsername = user.getUsername();
        boolean isStaff = isStaffRole(user.getRole());

        // 2. 遍歷並校驗傳入的字段
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.equals("username")) {
                if (value.equals(oldUsername)) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "SAME_USERNAME"));
                }
                if (userRepository.existsByUsername(value)) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "USERNAME_EXISTS"));
                }
                user.setUsername(value);
            }
            // === 核心修改：地址字段交由 UserAddressService 處理 ===
            else if (key.equals("address")) {
                // ranking = 1 代表主地址。若 value 為空，Service 層會自動執行刪除邏輯
                userAddressService.updateOrCreateAddress(user, 1, value.isEmpty() ? null : value);
            }
            else if (key.equals("backupAddress")) {
                // ranking = 2 代表備用地址。若 value 為空，Service 層會自動執行刪除邏輯
                userAddressService.updateOrCreateAddress(user, 2, value.isEmpty() ? null : value);
            }
            // === 員工專屬字段校驗與更新 ===
            else if (key.equals("email")) {
                if (!isStaff) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "無權修改電子郵件"));
                }
                if (!value.equals(user.getEmail()) && userRepository.findByEmail(value).isPresent()) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "EMAIL_EXISTS"));
                }
                user.setEmail(value.isEmpty() ? null : value);
            }
            else if (key.equals("phone")) {
                if (!isStaff) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "無權修改手機號碼"));
                }
                if (!value.equals(user.getPhone()) && userRepository.findByPhone(value).isPresent()) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "PHONE_EXISTS"));
                }
                user.setPhone(value.isEmpty() ? null : value);
            }
            else if (key.equals("workPhone")) {
                if (!isStaff) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "無權修改工作電話"));
                }
                user.setWorkPhone(value.isEmpty() ? null : value);
            }
        }

        // 3. 保存到數據庫 (更新 User 實體，如 username, email, phone 等)
        userRepository.save(user);

        // 4. 清除 Redis 緩存 (使用舊用戶名確保舊緩存被徹底清除)
        String cacheKey = "user:info:" + oldUsername;
        redisTemplate.delete(cacheKey);
        System.out.println("✅ 已清除用戶緩存: " + cacheKey);

        // 5. 返回成功響應
        return ResponseEntity.ok(Map.of("success", true, "message", "更新成功"));
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

    @Operation(
            summary = "獲取所有用戶列表 (管理員專用)",
            description = "獲取系統中所有用戶的基本信息（已脫敏，不包含密碼等敏感字段），僅限擁有 ADMIN 角色的管理員訪問。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(
            @Parameter(hidden = true) Authentication authentication) {

        // 1. 權限校驗：嚴格檢查 Role 是否為 ADMIN
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }

        // 2. 查詢所有用戶
        List<User> users = userRepository.findAll();

        // 3. 轉換為簡單的 Map，只返回前端需要的字段 (避免洩露密碼等敏感信息)
        List<Map<String, Object>> simpleUsers = users.stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("name", user.getName());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Result.okWithData("獲取成功", simpleUsers));
    }
}