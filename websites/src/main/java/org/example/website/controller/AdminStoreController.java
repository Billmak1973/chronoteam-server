package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.OfflineStore;
import org.example.website.entity.User;
import org.example.website.repository.OfflineStoreRepository;
import org.example.website.security.CustomUserDetails;
import org.example.website.util.PaginationUtils; // 1. 引入工具類
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

    // 渲染管理頁面
    @GetMapping
    public String storesPage(Model model, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return "redirect:/";
        }
        return "admin/admin-stores";
    }

    /**
     * API: 獲取所有店鋪 (已修復：支持 1-based 分頁 + 避免序列化錯誤)
     */
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<?> getAllStores(
            @RequestParam(defaultValue = "1") int page, // 【修改 1】默認值改為 1 (1-based)
            @RequestParam(defaultValue = "12") int size) {

        // 【修改 2】將 1-based 頁碼轉換為 0-based 索引供 Spring Data JPA 使用
        int pageIndex = Math.max(0, page - 1);

        // 1. 按創建時間倒序分頁查詢
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OfflineStore> storesPage = storeRepository.findAll(pageable);

        // 2. 【核心修復】：數據清洗 (Data Cleaning)
        // 將 Entity 轉換為 Map，只保留前端需要的字段，避免 Jackson 序列化 Hibernate Proxy 導致報錯
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
        // 傳入 cleanStores 而不是 null，確保返回的是純淨的 JSON 數據
        Map<String, Object> response = PaginationUtils.buildPageResponse(storesPage, cleanStores);

        // 【修改 3】關鍵步驟：覆蓋 currentPage 為 1-based
        // PaginationUtils 內部使用的是 storesPage.getNumber() (0-based)，這裡強制改回前端傳入的 page (1-based)
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    // API: 新增店鋪
    @PostMapping("/api/add")
    @ResponseBody
    public ResponseEntity<?> addStore(@RequestBody OfflineStore store, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作，僅限管理員"));
        }
        if (storeRepository.existsByStoreCode(store.getStoreCode())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("店鋪代碼 (Store Code) 已存在，請使用唯一標識"));
        }
        store.setIsActive(true);
        storeRepository.save(store);
        return ResponseEntity.ok(ApiResponse.ok("店鋪新增成功"));
    }

    // API: 更新店鋪
    @PutMapping("/api/update/{id}")
    @ResponseBody
    public ResponseEntity<?> updateStore(@PathVariable Long id, @RequestBody OfflineStore storeDetails, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作，僅限管理員"));
        }
        OfflineStore store = storeRepository.findById(id).orElseThrow(() -> new RuntimeException("店鋪不存在"));

        if (!store.getStoreCode().equals(storeDetails.getStoreCode()) && storeRepository.existsByStoreCode(storeDetails.getStoreCode())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("店鋪代碼已存在"));
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
        return ResponseEntity.ok(ApiResponse.ok("店鋪更新成功"));
    }

    // API: 切換顯示/隱藏狀態
    @PutMapping("/api/toggle/{id}")
    @ResponseBody
    public ResponseEntity<?> toggleStoreActive(@PathVariable Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作，僅限管理員"));
        }
        OfflineStore store = storeRepository.findById(id).orElseThrow(() -> new RuntimeException("店鋪不存在"));
        store.setIsActive(!store.getIsActive());
        storeRepository.save(store);
        return ResponseEntity.ok(ApiResponse.ok(store.getIsActive() ? "店鋪已顯示" : "店鋪已隱藏，前台結帳將不再展示"));
    }

    // API: 刪除店鋪
    @DeleteMapping("/api/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteStore(@PathVariable Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作，僅限管理員"));
        }
        storeRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("店鋪已徹底刪除"));
    }
}