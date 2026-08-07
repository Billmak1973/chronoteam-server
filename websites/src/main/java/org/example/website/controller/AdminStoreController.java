package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.OfflineStore;
import org.example.website.entity.User;
import org.example.website.repository.OfflineStoreRepository;
import org.example.website.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/stores")
public class AdminStoreController {

    private final OfflineStoreRepository storeRepository;

    public AdminStoreController(OfflineStoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /**
     *  核心修復：權限校驗基於 user_type (Role == ADMIN)，而非用戶名是否等於 "admin"
     * CustomUserDetails 在登入時已從數據庫載入 Role 枚舉，直接判斷，零查庫開銷
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
            return "redirect:/"; // 非管理員不允許進入頁面
        }
        return "admin/admin-stores"; // 對應 templates/admin/admin-stores.html
    }

    // API: 獲取所有店鋪
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<?> getAllStores() {
        List<OfflineStore> stores = storeRepository.findAll();
        return ResponseEntity.ok(stores);
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