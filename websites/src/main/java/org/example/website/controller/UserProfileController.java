package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.AddressRequest;
import org.example.website.dto.AddressUpdateRequest;
import org.example.website.dto.Result;
import org.example.website.entity.User;
import org.example.website.entity.UserAddress;
import org.example.website.repository.UserAddressRepository;
import org.example.website.repository.UserRepository;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.UserAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
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
    private UserAddressRepository userAddressRepository;

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

                if (oldUsername.equalsIgnoreCase("admin")) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "系統管理員帳號不允許修改用戶名"));
                }

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


    @Operation(
            summary = "新增用戶收貨地址",
            description = "允許用戶新增收貨地址，並可選擇是否設為默認地址。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "新增成功"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效")
    })
    @PostMapping("/addresses")
    @Transactional // 【新增】確保批量更新和插入在同一事務中，防止部分失敗導致數據錯亂
    public ResponseEntity<?> addAddress(@RequestBody AddressRequest request, Authentication authentication) {
        // 1. 獲取當前登錄用戶
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 2. 構建新地址實體
        UserAddress newAddress = new UserAddress();
        newAddress.setUser(user);
        newAddress.setReceiverName(request.getReceiverName());
        newAddress.setContactPhone(request.getContactPhone());
        newAddress.setFullAddress(request.getFullAddress());

        // 3. 處理 Ranking (排序權重) 邏輯
        if (request.isDefault()) {
            // 【分支 A】設為默認：將現有所有地址的 ranking +1，新地址設為 1
            List<UserAddress> existingAddresses = userAddressRepository.findByUserOrderByRankingAsc(user);

            for (UserAddress addr : existingAddresses) {
                int currentRanking = addr.getRanking() == null ? 0 : addr.getRanking();
                addr.setRanking(currentRanking + 1);
            }

            // 批量保存更新後的舊地址
            if (!existingAddresses.isEmpty()) {
                userAddressRepository.saveAll(existingAddresses);
            }

            // 新地址設為 1
            newAddress.setRanking(1);

        } else {
            // 【分支 B】不設為默認：獲取當前最大的 ranking，新地址 ranking = max + 1
            int maxRanking = userAddressRepository.findByUserOrderByRankingDesc(user)
                    .stream()
                    .findFirst()
                    .map(addr -> addr.getRanking() == null ? 0 : addr.getRanking())
                    .orElse(0);

            newAddress.setRanking(maxRanking + 1);
        }

        // 4. 保存新地址到數據庫
        userAddressRepository.save(newAddress);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "收貨地址新增成功！"
        ));
    }

    @Operation(
            summary = "刪除用戶收貨地址",
            description = "根據地址 ID 刪除指定的收貨地址。為保護主地址，ranking=1 的地址不允許直接刪除。刪除後會自動重新計算剩餘地址的排序權重。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "刪除成功"),
            @ApiResponse(responseCode = "400", description = "主地址不允許刪除"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效"),
            @ApiResponse(responseCode = "403", description = "無權刪除此地址"),
            @ApiResponse(responseCode = "404", description = "地址不存在")
    })
    @DeleteMapping("/addresses/{id}")
    @Transactional
    public ResponseEntity<?> deleteAddress(
            @PathVariable Long id,
            Authentication authentication) {

        // 1. 獲取當前登錄用戶
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 2. 查找要刪除的地址
        UserAddress address = userAddressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("地址不存在"));

        // 3. 權限校驗 (防越權)：確保該地址確實屬於當前登入用戶
        if (!address.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "無權刪除他人的收貨地址"
            ));
        }

        // 4. 業務校驗：前端已隱藏主地址的刪除按鈕，後端也需攔截以防惡意構造請求
        if (address.getRanking() != null && address.getRanking() == 1) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "主地址不允許直接刪除，請先將其他地址設為默認！"
            ));
        }

        // 5. 數據完整性維護：刪除後，將比它大的 ranking 都減 1，保持排序連續 (例如刪了2，3變成2)
        Integer deletedRanking = address.getRanking();
        if (deletedRanking != null) {
            List<UserAddress> remainingAddresses = userAddressRepository.findByUserOrderByRankingAsc(user);
            for (UserAddress addr : remainingAddresses) {
                if (addr.getRanking() != null && addr.getRanking() > deletedRanking) {
                    addr.setRanking(addr.getRanking() - 1);
                }
            }
            if (!remainingAddresses.isEmpty()) {
                userAddressRepository.saveAll(remainingAddresses);
            }
        }

        // 6. 執行刪除
        userAddressRepository.delete(address);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "地址已成功刪除！"
        ));
    }

    @Operation(
            summary = "更新用戶收貨地址",
            description = "允許用戶修改現有收貨地址的詳細信息及排序權重。系統會自動智能調整其他地址的排序，確保權重連續且不超出總數。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效"),
            @ApiResponse(responseCode = "403", description = "無權修改此地址"),
            @ApiResponse(responseCode = "404", description = "地址不存在")
    })
    @PutMapping("/addresses/{id}")
    @Transactional
    public ResponseEntity<?> updateAddress(
            @PathVariable Long id,
            @RequestBody AddressUpdateRequest request,
            Authentication authentication) {

        // 1. 獲取當前登錄用戶
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 2. 查找並校驗地址權限
        UserAddress address = userAddressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("地址不存在"));

        if (!address.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權修改此地址"));
        }

        // 3. 獲取該用戶的所有地址 (按 ranking 升序)
        List<UserAddress> allAddresses = userAddressRepository.findByUserOrderByRankingAsc(user);
        int totalAddresses = allAddresses.size();

        // 4. 確定舊的 ranking 和新的 ranking
        int oldRank = address.getRanking() != null ? address.getRanking() : 1;
        int newRank = request.getRanking() != null ? request.getRanking() : oldRank;

        // 邊界兜底校驗：確保 newRank 絕對在 1 到 totalAddresses 之間
        if (newRank < 1) newRank = 1;
        if (newRank > totalAddresses) newRank = totalAddresses;

        // 5. 智能處理 Ranking 邏輯 (只有當 ranking 發生變化時才執行)
        if (newRank != oldRank) {
            for (UserAddress addr : allAddresses) {
                if (addr.getAddressId().equals(id)) {
                    continue; // 跳過當前正在修改的地址，最後再單獨設置
                }

                int currentRank = addr.getRanking() != null ? addr.getRanking() : 0;

                if (newRank < oldRank) {
                    // 情況 A：往前移 (例如 3 -> 1)
                    // 原本在 [newRank, oldRank - 1] 範圍內的地址，都要 +1 (往後退)
                    if (currentRank >= newRank && currentRank < oldRank) {
                        addr.setRanking(currentRank + 1);
                    }
                } else {
                    // 情況 B：往後移 (例如 1 -> 3)
                    // 原本在 [oldRank + 1, newRank] 範圍內的地址，都要 -1 (往前進)
                    if (currentRank > oldRank && currentRank <= newRank) {
                        addr.setRanking(currentRank - 1);
                    }
                }
            }
            // 批量保存所有受影響的地址
            userAddressRepository.saveAll(allAddresses);
        }

        // 6. 更新目標地址的字段
        address.setReceiverName(request.getReceiverName());
        address.setContactPhone(request.getContactPhone());
        address.setFullAddress(request.getFullAddress());
        address.setRanking(newRank); // 設置為計算後的新 ranking

        // 7. 保存並返回
        userAddressRepository.save(address);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "地址更新成功！"
        ));
    }

    @Operation(
            summary = "获取用户地址的最大排序值",
            description = "获取当前登录用户所有地址中的最大ranking值，用于前端验证。"
    )
    @GetMapping("/addresses/max-ranking")
    public ResponseEntity<?> getMaxRanking(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        int maxRanking = userAddressRepository.findByUserOrderByRankingDesc(user)
                .stream()
                .findFirst()
                .map(addr -> addr.getRanking() != null ? addr.getRanking() : 1)
                .orElse(1);

        return ResponseEntity.ok(Map.of("maxRanking", maxRanking));
    }
}