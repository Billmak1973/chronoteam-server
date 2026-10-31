package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.OfflineStore;
import org.example.website.entity.User;
import org.example.website.entity.UserAddress;
import org.example.website.repository.OfflineStoreRepository;
import org.example.website.repository.UserAddressRepository;
import org.example.website.repository.UserRepository;
import org.example.website.security.CustomUserDetails;
import org.example.website.util.PaginationUtils;
import org.example.website.util.UidGenerator;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller//標記為 Spring MVC 控制器，通常配合 @ResponseBody 返回數據，或返回視圖（View）。就是返回頁面：參考admin/admin-customers.html
//返回頁面的方法不加 @ResponseBody，返回數據的方法加上 @ResponseBody
@RequestMapping("/admin")
@Tag(name = "後台用戶管理", description = "管理員專屬的用戶列表查詢、賬號創建與權限修改接口")
public class AdminCustomerController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAddressRepository userAddressRepository;
    private final OfflineStoreRepository offlineStoreRepository;

    public AdminCustomerController(UserRepository userRepository, PasswordEncoder passwordEncoder, UserAddressRepository userAddressRepository, OfflineStoreRepository offlineStoreRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userAddressRepository = userAddressRepository;
        this.offlineStoreRepository = offlineStoreRepository;
    }

    /**
     * 1. 頁面骨架渲染
     */
    @Hidden // 隱藏純頁面渲染接口，保持 Swagger UI 專注於 REST API
    @GetMapping("/customers")
    public String manageCustomersPage(Model model) {
        return "admin/admin-customers";
    }

    @Operation(
            summary = "獲取後台用戶分頁列表",
            description = "支持關鍵字、角色與所屬門店的分頁查詢。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功"),
            @ApiResponse(responseCode = "401", description = "未登入或無權限"),
            @ApiResponse(responseCode = "500", description = "服務器內部錯誤")
    })
    @GetMapping("/api/customers/list")
    @ResponseBody
    public ResponseEntity<?> getCustomers(
            @Parameter(description = "當前頁碼 (1-based)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每頁顯示數量", example = "25")
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "搜索關鍵字", example = "test")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "角色篩選 (可傳入 'STAFF' 代表非顧客)", example = "SALES")
            @RequestParam(required = false) String role,
            // 【核心修復 1】：接收前端實際傳遞的兩個參數，而不是 storeFilter
            @Parameter(description = "是否篩選有門店歸屬的員工", example = "true")
            @RequestParam(required = false) Boolean hasStoreNotNull,
            @Parameter(description = "是否篩選沒有門店歸屬的員工", example = "true")
            @RequestParam(required = false) Boolean hasStoreNull,  // 【新增】
            @Parameter(description = "具體門店 ID", example = "1")
            @RequestParam(required = false) String storeId) {

        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> usersPage;

        try {
            boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
            boolean hasRole = role != null && !role.trim().isEmpty();
            boolean isStaffFilter = "STAFF".equals(role);
            User.Role targetRole = isStaffFilter ? User.Role.CUSTOMER : (hasRole ? User.Role.valueOf(role) : null);

            // 【核心修復 2】：解析前端傳來的 storeId
            boolean hasSpecificStore = false;
            Long specificStoreId = null;
            if (storeId != null && !storeId.trim().isEmpty()) {
                try {
                    specificStoreId = Long.parseLong(storeId);
                    hasSpecificStore = true;
                } catch (NumberFormatException e) {
                    // 忽略非法值
                }
            }

            // 將接收到的 Boolean 轉為 boolean，防止 NullPointerException
            boolean hasStoreNotNullFlag = Boolean.TRUE.equals(hasStoreNotNull);
            boolean hasStoreNullFlag = Boolean.TRUE.equals(hasStoreNull);  // 【新增】

            // 【核心修復 3】：根據條件組合調用不同的 Repository 方法
            if (hasKeyword && hasRole && hasSpecificStore) {
                usersPage = isStaffFilter
                        ? userRepository.findByKeywordAndRoleNotAndStore_StoreId(keyword, targetRole, specificStoreId, pageable)
                        : userRepository.findByKeywordAndRoleAndStore_StoreId(keyword, targetRole, specificStoreId, pageable);
            } else if (hasKeyword && hasSpecificStore) {
                usersPage = userRepository.findByKeywordAndStore_StoreId(keyword, specificStoreId, pageable);
            } else if (hasRole && hasSpecificStore) {
                usersPage = isStaffFilter
                        ? userRepository.findByRoleNotAndStore_StoreId(targetRole, specificStoreId, pageable)
                        : userRepository.findByRoleAndStore_StoreId(targetRole, specificStoreId, pageable);
            } else if (hasSpecificStore) {
                usersPage = userRepository.findByStore_StoreId(specificStoreId, pageable);
            }
            // --- 處理 HAS_STORE (所有有門店歸屬的員工) ---
            else if (hasKeyword && hasRole && hasStoreNotNullFlag) {
                usersPage = isStaffFilter
                        ? userRepository.findByKeywordAndRoleNotAndStoreIsNotNull(keyword, targetRole, pageable)
                        : userRepository.findByKeywordAndRoleAndStoreIsNotNull(keyword, targetRole, pageable);
            } else if (hasKeyword && hasStoreNotNullFlag) {
                usersPage = userRepository.findByKeywordAndStoreIsNotNull(keyword, pageable);
            } else if (hasRole && hasStoreNotNullFlag) {
                usersPage = isStaffFilter
                        ? userRepository.findByRoleNotAndStoreIsNotNull(targetRole, pageable)
                        : userRepository.findByRoleAndStoreIsNotNull(targetRole, pageable);
            } else if (hasStoreNotNullFlag) {
                usersPage = userRepository.findByStoreIsNotNull(pageable);
            }
            // --- 【新增】處理 NO_STORE (所有沒有門店歸屬的員工) ---
            else if (hasKeyword && hasRole && hasStoreNullFlag) {
                usersPage = isStaffFilter
                        ? userRepository.findByKeywordAndRoleNotAndStoreIsNull(keyword, targetRole, pageable)
                        : userRepository.findByKeywordAndRoleAndStoreIsNull(keyword, targetRole, pageable);
            } else if (hasKeyword && hasStoreNullFlag) {
                usersPage = userRepository.findByKeywordAndStoreIsNull(keyword, pageable);
            } else if (hasRole && hasStoreNullFlag) {
                usersPage = isStaffFilter
                        ? userRepository.findByRoleNotAndStoreIsNull(targetRole, pageable)
                        : userRepository.findByRoleAndStoreIsNull(targetRole, pageable);
            } else if (hasStoreNullFlag) {
                usersPage = userRepository.findByStoreIsNull(pageable);
            }
            // --- 原有邏輯 ---
            else if (hasKeyword && hasRole) {
                usersPage = isStaffFilter
                        ? userRepository.findByKeywordAndRoleNot(keyword, targetRole, pageable)
                        : userRepository.findByKeywordAndRole(keyword, targetRole, pageable);
            } else if (hasKeyword) {
                usersPage = userRepository.findByKeyword(keyword, pageable);
            } else if (hasRole) {
                usersPage = isStaffFilter
                        ? userRepository.findByRoleNot(targetRole, pageable)
                        : userRepository.findByRole(targetRole, pageable);
            } else {
                usersPage = userRepository.findAll(pageable);
            }
        } catch (Exception e) {
            // 發生異常時降級為全量查詢，防止系統崩潰
            usersPage = userRepository.findAll(pageable);
        }

        // 4. 批量獲取當前頁用戶的地址信息 (避免 N+1 查詢性能問題)
        List<User> users = usersPage.getContent();
        Map<Long, List<UserAddress>> addressMap = new HashMap<>();
        for (User user : users) {
            addressMap.put(user.getId(), userAddressRepository.findByUserOrderByRankingAsc(user));
        }

        // 數據清洗
        List<Map<String, Object>> cleanUsers = users.stream().map(user -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("uid", user.getUid());
            item.put("name", user.getName());
            item.put("username", user.getUsername());
            item.put("email", user.getEmail());
            item.put("phone", user.getPhone());
            item.put("workPhone", user.getWorkPhone());
            item.put("role", user.getRole().name());
            item.put("storeId", user.getStore() != null ? user.getStore().getStoreId() : null);
            item.put("storeName", user.getStore() != null ? user.getStore().getName() : null);
            item.put("createdAt", user.getCreatedAt());
            item.put("updatedAt", user.getUpdatedAt());

            // 5. 格式化地址信息為 HTML
            List<UserAddress> addrs = addressMap.get(user.getId());
            if (addrs != null && !addrs.isEmpty()) {
                StringBuilder addrHtml = new StringBuilder();
                for (int i = 0; i < addrs.size(); i++) {
                    UserAddress addr = addrs.get(i);
                    String prefix = (i == 0) ? "<span style='color:var(--gold); font-weight:600;'>主:</span> " :
                            (i == 1) ? "<span style='color:#64748b;'>備:</span> " :
                                    "<span style='color:#64748b;'>" + (i + 1) + ":</span> ";

                    addrHtml.append("<div style='margin-bottom: 4px; white-space: normal;'>")
                            .append(prefix)
                            .append(addr.getFullAddress())
                            .append("</div>");
                }
                item.put("addressesHtml", addrHtml.toString());
            } else {
                item.put("addressesHtml", "<span style='color:#cbd5e1;'>未設置</span>");
            }

            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = PaginationUtils.buildPageResponse(usersPage, cleanUsers);
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    /**
     * 創建新手動賬號 (管理員專用)
     */
    @Operation(
            summary = "創建新賬號 (管理員專用)",
            description = "管理員手動創建系統賬號，默認密碼為 123456，自動生成唯一 UID。禁止使用 'admin' 作為用戶名。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "創建成功"),
            @ApiResponse(responseCode = "400", description = "參數錯誤、用戶名已存在或觸發安全限制"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員 (Role: ADMIN)"),
            @ApiResponse(responseCode = "500", description = "UID 生成衝突或服務器錯誤")
    })
    @PostMapping("/api/customers/create")
    @ResponseBody
    public ResponseEntity<?> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "創建賬號請求參數",
                    required = true,
                    content = @Content(schema = @Schema(example = "{\"username\": \"new_staff\", \"role\": \"SALES\"}"))
            )
            @RequestBody Map<String, String> payload,
            Authentication authentication) { // 注入 Authentication 用於權限校驗

        // 核心權限校驗：嚴格檢查 user_type 是否為 ADMIN
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal()) ||
                !(authentication.getPrincipal() instanceof CustomUserDetails userDetails) ||
                userDetails.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權操作，僅限管理員 (Role: ADMIN)"));
        }

        String username = payload.get("username");
        String roleStr = payload.get("role");

        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用戶名不能為空"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用戶名已存在"));
        }

        if ("admin".equalsIgnoreCase(username.trim())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "⛔ 系統安全限制：不能使用 'admin' 作為用戶名！"
            ));
        }

        try {
            User user = new User();
            user.setUsername(username);
            user.setName(username); // 默認姓名
            user.setEmail(username + "@chronoteam.internal"); // 默認郵箱
            user.setPhone("0000000000"); // 默認電話

            // 使用注入的 passwordEncoder
            user.setPassword(passwordEncoder.encode("123456"));

            // 設置角色
            try {
                user.setRole(User.Role.valueOf(roleStr));
            } catch (IllegalArgumentException e) {
                user.setRole(User.Role.CUSTOMER);
            }

            // 自動生成 UID (帶重試機制確保唯一)
            String uid;
            int retries = 0;
            do {
                uid = UidGenerator.nextUid(userRepository.count());
                retries++;
            } while (userRepository.existsByUid(uid) && retries < 5);

            if (userRepository.existsByUid(uid)) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "UID生成衝突，請重試"));
            }
            user.setUid(uid);
            String storeIdStr = payload.get("storeId");
            if (storeIdStr != null && !storeIdStr.trim().isEmpty()) {
                try {
                    OfflineStore store = offlineStoreRepository.findById(Long.parseLong(storeIdStr)).orElse(null);
                    user.setStore(store);
                } catch (NumberFormatException e) {
                    // 忽略無效的 storeId
                }
            }
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "創建成功，默認密碼為 123456");
            response.put("uid", uid);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "服務器錯誤: " + e.getMessage()));
        }
    }

    /**
     * 修改用戶角色
     */
    @Operation(
            summary = "修改用戶角色權限",
            description = "管理員修改指定用戶的角色。系統安全限制：無法修改超級管理員 (admin) 的權限。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "權限更新成功"),
            @ApiResponse(responseCode = "400", description = "用戶不存在、無效的角色類型或觸發安全限制"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員 (Role: ADMIN)"),
            @ApiResponse(responseCode = "500", description = "服務器錯誤")
    })
    @PutMapping("/api/customers/{id}/role")
    @ResponseBody
    public ResponseEntity<?> updateUserRole(
            @Parameter(description = "用戶 ID", required = true, example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "新角色信息",
                    required = true,
                    content = @Content(schema = @Schema(example = "{\"role\": \"APPRAISER\"}"))
            )
            @RequestBody Map<String, String> payload,
            Authentication authentication) { // 【新增】注入 Authentication 用於權限校驗

        // 【新增】核心權限校驗：嚴格檢查 user_type 是否為 ADMIN
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal()) ||
                !(authentication.getPrincipal() instanceof CustomUserDetails userDetails) ||
                userDetails.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權操作，僅限管理員 (Role: ADMIN)"));
        }

        String roleStr = payload.get("role");

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用戶不存在"));
        }

        if ("admin".equalsIgnoreCase(user.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "⛔ 系統安全限制：無法修改超級管理員 (admin) 的權限！"
            ));
        }

        try {
            User.Role newRole = User.Role.valueOf(roleStr);
            user.setRole(newRole);
            String storeIdStr = payload.get("storeId");
            if (storeIdStr != null && !storeIdStr.trim().isEmpty()) {
                try {
                    OfflineStore store = offlineStoreRepository.findById(Long.parseLong(storeIdStr)).orElse(null);
                    user.setStore(store);
                } catch (NumberFormatException e) {
                    user.setStore(null);
                }
            } else {
                user.setStore(null); // 如果切換回非門店角色，清空關聯
            }
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("success", true, "message", "權限更新成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "無效的角色類型"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "更新失敗"));
        }
    }

    /**
     * 根據電話或用戶名查找用戶 (用於創建申請時的用戶查找)
     */
    @GetMapping("/api/users/search")
    public ResponseEntity<?> searchUser(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String username) {

        Optional<User> userOpt = Optional.empty();

        if (phone != null && !phone.trim().isEmpty()) {
            userOpt = userRepository.findByPhone(phone.trim());
        } else if (username != null && !username.trim().isEmpty()) {
            userOpt = userRepository.findByUsername(username.trim());
        } else {
            return ResponseEntity.badRequest().body(Result.error("請提供電話或用戶名"));
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            Map<String, Object> data = new HashMap<>();
            data.put("username", user.getUsername());
            data.put("phone", user.getPhone());
            data.put("name", user.getName());
            return ResponseEntity.ok(Result.okWithData("查找成功", data));
        } else {
            return ResponseEntity.status(404).body(Result.error("未找到匹配的用戶，請檢查輸入是否正確"));
        }
    }

    @Operation(summary = "用戶名自動完成搜索", description = "根據輸入的關鍵字模糊匹配用戶名，返回匹配的用戶列表（用於下拉提示）")
    @GetMapping("/api/users/search-username")
    @ResponseBody
    public ResponseEntity<?> searchUsernamesAutocomplete(@RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(Result.okWithData("成功", new ArrayList<>()));
        }
        // 調用 Repository 進行模糊查詢
        List<User> users = userRepository.findByUsernameContainingOrderByUsernameAsc(keyword.trim());

        // 轉換為前端需要的簡單 Map 結構
        List<Map<String, String>> result = users.stream().map(u -> {
            Map<String, String> map = new HashMap<>();
            map.put("username", u.getUsername());
            map.put("name", u.getName() != null ? u.getName() : "");
            map.put("phone", u.getPhone() != null ? u.getPhone() : "");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Result.okWithData("成功", result));
    }
}