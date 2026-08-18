package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.OfflineStore;
import org.example.website.entity.User;
import org.example.website.repository.OfflineStoreRepository;
import org.example.website.security.CustomUserDetails;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/stores")
@Tag(name = "後台店鋪管理", description = "管理員專屬的線下店鋪增刪改查與狀態管理接口")
public class AdminStoreController {

    private final OfflineStoreRepository storeRepository;

    public AdminStoreController(OfflineStoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /**
     * 權限校驗輔助方法
     */
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.getRole() == User.Role.ADMIN;
    }

    /**
     * 渲染管理頁面
     * 使用 @Hidden 隱藏此接口，因為 Swagger 專注於 REST API，不需要展示 Thymeleaf 頁面渲染接口
     */
    @Hidden
    @GetMapping
    public String storesPage(Model model, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return "redirect:/";
        }
        return "admin/admin-stores";
    }

    /**
     * API: 獲取所有店鋪 (支持 1-based 分頁 + 數據清洗避免序列化錯誤)
     */
    @Operation(
            summary = "獲取店鋪分頁列表",
            description = "管理員分頁獲取所有線下店鋪信息。返回的 currentPage 為 1-based。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功"),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<?> getAllStores(
            @Parameter(description = "當前頁碼 (1-based)", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "每頁顯示數量", example = "12")
            @RequestParam(defaultValue = "12") int size) {

        // 將 1-based 頁碼轉換為 0-based 索引供 Spring Data JPA 使用
        int pageIndex = Math.max(0, page - 1);

        // 1. 按創建時間倒序分頁查詢
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OfflineStore> storesPage = storeRepository.findAll(pageable);

        // 2. 數據清洗 (Data Cleaning)：將 Entity 轉換為 Map，避免 Jackson 序列化 Hibernate Proxy 導致報錯
        List<Map<String, Object>> cleanStores = storesPage.getContent().stream().map(store -> {
            Map<String, Object> map = new HashMap<>();
            map.put("storeId", store.getStoreId());
            map.put("storeCode", store.getStoreCode());
            map.put("name", store.getName());
            map.put("address", store.getAddress());
            map.put("phone", store.getPhone());
            map.put("hours", store.getHours());
            map.put("isActive", store.getIsActive());

            // 退貨相關字段
            map.put("returnAdvanceDays", store.getReturnAdvanceDays());
            map.put("returnBlackoutStartDate", store.getReturnBlackoutStartDate());
            map.put("returnBlackoutEndDate", store.getReturnBlackoutEndDate());
            map.put("returnBlackoutReason", store.getReturnBlackoutReason());
            map.put("returnClosedDaysOfWeek", store.getReturnClosedDaysOfWeek());

            map.put("createdAt", store.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        // 3. 使用 PaginationUtils 構建標準響應
        Map<String, Object> response = PaginationUtils.buildPageResponse(storesPage, cleanStores);

        // 關鍵步驟：覆蓋 currentPage 為 1-based
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    /**
     * API: 新增店鋪
     */
    @Operation(
            summary = "新增店鋪",
            description = "管理員新增一家線下店鋪，需提供唯一的 Store Code。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "新增成功"),
            @ApiResponse(responseCode = "400", description = "店鋪代碼已存在或參數錯誤"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @PostMapping("/api/add")
    @ResponseBody
    public ResponseEntity<?> addStore(
            @Parameter(description = "店鋪詳細信息", required = true)
            @RequestBody OfflineStore store,
            Authentication authentication) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }
        if (storeRepository.existsByStoreCode(store.getStoreCode())) {
            return ResponseEntity.badRequest().body(Result.error("店鋪代碼 (Store Code) 已存在，請使用唯一標識"));
        }
        store.setIsActive(true);
        storeRepository.save(store);
        return ResponseEntity.ok(Result.ok("店鋪新增成功"));
    }

    /**
     * API: 更新店鋪
     */
    @Operation(
            summary = "更新店鋪信息",
            description = "管理員修改指定店鋪的詳細信息（包含退貨政策設置）。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "店鋪不存在或代碼衝突"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @PutMapping("/api/update/{id}")
    @ResponseBody
    public ResponseEntity<?> updateStore(
            @Parameter(description = "店鋪 ID", required = true, example = "1")
            @PathVariable Long id,

            @Parameter(description = "更新後的店鋪詳細信息", required = true)
            @RequestBody OfflineStore storeDetails,
            Authentication authentication) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }
        OfflineStore store = storeRepository.findById(id).orElseThrow(() -> new RuntimeException("店鋪不存在"));

        if (!store.getStoreCode().equals(storeDetails.getStoreCode()) && storeRepository.existsByStoreCode(storeDetails.getStoreCode())) {
            return ResponseEntity.badRequest().body(Result.error("店鋪代碼已存在"));
        }

        store.setStoreCode(storeDetails.getStoreCode());
        store.setName(storeDetails.getName());
        store.setAddress(storeDetails.getAddress());
        store.setPhone(storeDetails.getPhone());
        store.setHours(storeDetails.getHours());
        store.setReturnAdvanceDays(storeDetails.getReturnAdvanceDays());
        store.setReturnBlackoutStartDate(storeDetails.getReturnBlackoutStartDate());
        store.setReturnBlackoutEndDate(storeDetails.getReturnBlackoutEndDate());
        store.setReturnBlackoutReason(storeDetails.getReturnBlackoutReason());
        store.setReturnClosedDaysOfWeek(storeDetails.getReturnClosedDaysOfWeek());

        storeRepository.save(store);
        return ResponseEntity.ok(Result.ok("店鋪更新成功"));
    }

    /**
     * API: 切換顯示/隱藏狀態
     */
    @Operation(
            summary = "切換店鋪顯示狀態",
            description = "管理員開啟或關閉店鋪在前台結帳頁面的顯示狀態。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "切換成功"),
            @ApiResponse(responseCode = "400", description = "店鋪不存在"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @PutMapping("/api/toggle/{id}")
    @ResponseBody
    public ResponseEntity<?> toggleStoreActive(
            @Parameter(description = "店鋪 ID", required = true, example = "1")
            @PathVariable Long id,
            Authentication authentication) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }
        OfflineStore store = storeRepository.findById(id).orElseThrow(() -> new RuntimeException("店鋪不存在"));
        store.setIsActive(!store.getIsActive());
        storeRepository.save(store);
        return ResponseEntity.ok(Result.ok(store.getIsActive() ? "店鋪已顯示" : "店鋪已隱藏，前台結帳將不再展示"));
    }

    /**
     * API: 刪除店鋪
     */
    @Operation(
            summary = "刪除店鋪",
            description = "管理員徹底刪除指定的線下店鋪記錄。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "刪除成功"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @DeleteMapping("/api/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteStore(
            @Parameter(description = "店鋪 ID", required = true, example = "1")
            @PathVariable Long id,
            Authentication authentication) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }
        storeRepository.deleteById(id);
        return ResponseEntity.ok(Result.ok("店鋪已徹底刪除"));
    }
}