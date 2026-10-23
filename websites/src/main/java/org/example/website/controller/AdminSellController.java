package org.example.website.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.OfflineStore;
import org.example.website.entity.Product;
import org.example.website.entity.SellApplication;
import org.example.website.entity.User;
import org.example.website.repository.OfflineStoreRepository;
import org.example.website.repository.ProductRepository;
import org.example.website.repository.SellApplicationRepository;
import org.example.website.repository.UserRepository;
import org.example.website.service.FileStorageService;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "後台出售申請管理", description = "管理員審核與處理用戶出售申請")
public class AdminSellController {

    private final SellApplicationRepository sellApplicationRepository;
    private final OfflineStoreRepository offlineStoreRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public AdminSellController(SellApplicationRepository sellApplicationRepository, OfflineStoreRepository offlineStoreRepository, FileStorageService fileStorageService, ObjectMapper objectMapper, UserRepository userRepository, ProductRepository productRepository) {
        this.sellApplicationRepository = sellApplicationRepository;
        this.offlineStoreRepository = offlineStoreRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    /**
     * 1. 渲染頁面
     */
    @Hidden
    @GetMapping("/sell-applications")
    public String sellApplicationsPage(Model model) {
        // 傳遞 activePage 讓側邊欄高亮
        model.addAttribute("activePage", "sell-app");
        return "admin/admin-sell-applications";
    }

    /**
     * 2. 獲取列表 API (供 AdminTable 調用)
     */
    @Operation(summary = "獲取出售申請列表")
    @GetMapping("/api/sell-applications/list")
    @ResponseBody
    public ResponseEntity<?> getSellApplicationsList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mode) {

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 這裡建議在 Repository 中寫一個 @Query 支援多條件查詢，此處為簡化演示使用 findAll
        // 實際生產環境請替換為 sellApplicationRepository.findByFilters(...)
        Page<SellApplication> appPage = sellApplicationRepository.findAll(pageable);

        // 批量獲取門店名稱，避免 N+1 查詢
        List<String>storeIdStrs=appPage.getContent().stream()
                .map(SellApplication::getStoreId)
                .filter(id->id !=null && !id.trim().isEmpty()&& id.matches("\\d+"))
                .distinct()
                .collect(Collectors.toList());

        Map<String,String> storeMap= new HashMap<>();
        if(!storeIdStrs.isEmpty()) {
            List<Long> storeIds = storeIdStrs.stream().map(Long::valueOf).collect(Collectors.toList());
            List<OfflineStore> stores = offlineStoreRepository.findByStoreIdIn(storeIds);
            for(OfflineStore store:stores) {
                storeMap.put(String.valueOf(store.getStoreId()),store.getName());
            }
        }
        // 數據清洗 (避免 Hibernate 循環引用與序列化報錯)
        List<Map<String, Object>> cleanData = appPage.getContent().stream().map(app -> {
            Map<String, Object> map = new HashMap<>();
            map.put("applicationId", app.getApplicationId());
            map.put("brand", app.getBrand());
            map.put("series", app.getSeries());
            map.put("model", app.getModel());
            map.put("purchaseYear", app.getPurchaseYear());
            map.put("transactionMode", app.getTransactionMode().name());
            map.put("status", app.getStatus().name());
            map.put("appointmentDate", app.getAppointmentDate());
            map.put("appointmentTimeSlot", app.getAppointmentTimeSlot());
            map.put("createdAt", app.getCreatedAt());
            map.put("storeName", storeMap.getOrDefault(app.getStoreId(), "未指定"));

            if (app.getUser() != null) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("username", app.getUser().getUsername());
                userMap.put("phone", app.getUser().getPhone());
                map.put("user", userMap);
            }
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = PaginationUtils.buildPageResponse(appPage, cleanData);
        response.put("currentPage", page);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. 獲取詳情 API (供彈窗調用)
     */
    @Operation(summary = "獲取出售申請詳情")
    @GetMapping("/api/sell-applications/{id}")
    @ResponseBody
    public ResponseEntity<?> getApplicationDetail(@PathVariable Long id) {
        SellApplication app = sellApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("申請記錄不存在"));

        // 為了防止實體類直接序列化報錯，這裡手動組裝 Map 返回 (或配置 Jackson 忽略屬性)
        Map<String, Object> data = new HashMap<>();
        data.put("applicationId", app.getApplicationId());
        data.put("brand", app.getBrand());
        data.put("series", app.getSeries());
        data.put("model", app.getModel());
        data.put("purchaseYear", app.getPurchaseYear());
        data.put("quantity", app.getQuantity());
        data.put("accessories", app.getAccessories());
        data.put("condition", app.getCondition());
        data.put("functionStatus", app.getFunctionStatus());
        data.put("notes", app.getNotes());
        data.put("imagePaths", app.getImagePaths()); // 前端 JS 會解析這個 JSON
        data.put("transactionMode", app.getTransactionMode().name());
        data.put("status", app.getStatus().name());
        data.put("estimatedPrice", app.getEstimatedPrice());
        data.put("finalPrice", app.getFinalPrice());
        data.put("appraisalNotes", app.getAppraisalNotes());
        data.put("createdAt", app.getCreatedAt());
        data.put("appointmentDate", app.getAppointmentDate() != null ? app.getAppointmentDate().toString() : null);
        data.put("appointmentTimeSlot", app.getAppointmentTimeSlot());

        String storeName = "未指定";
        if (app.getStoreId() != null && !app.getStoreId().trim().isEmpty() && app.getStoreId().matches("\\d+")) {
            storeName = offlineStoreRepository.findById(Long.valueOf(app.getStoreId()))
                    .map(OfflineStore::getName)
                    .orElse("未知門店");
        }
        data.put("storeName", storeName);

        if (app.getUser() != null) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("username", app.getUser().getUsername());
            userMap.put("phone", app.getUser().getPhone());
            data.put("user", userMap);
        }
        if (app.getAppraiser() != null) {
            Map<String, Object> appraiserMap = new HashMap<>();
            appraiserMap.put("id",app.getAppraiser().getId());
            appraiserMap.put("username", app.getAppraiser().getUsername());
            data.put("appraiser", appraiserMap);
        }
        if(app.getProduct() != null) {
            Map<String,Object>productMap = new HashMap<>();
            productMap.put("productId", app.getProduct().getProductId());
            productMap.put("description", app.getProduct().getDescription());
            productMap.put("price", app.getProduct().getPrice());
            data.put("product", productMap);
        }

        return ResponseEntity.ok(Result.okWithData("獲取成功", data));
    }

    /**
     * 4. 更新出售申請 (管理員「重新編輯」專用，支援圖片更換/移動/刪除)
     * 【核心變更】：consumes 改為 multipart/form-data，才能同時接收文字欄位與新圖片檔案
     */
    @Operation(summary = "更新出售申請", description = "管理員修改申請狀態、手錶基礎資訊、鑑定報價、關聯商品與鑑定師，並管理實物圖片")
    @PutMapping(value = "/api/sell-applications/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> updateApplication(
            @PathVariable Long id,
            @RequestParam Map<String, String> form,
            MultipartHttpServletRequest multipartRequest) {
        SellApplication app = sellApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("申請記錄不存在"));

        try {
            // ===== 1. 手錶基礎資訊 =====
            String brand = emptyToNull(form.get("brand"));
            String series = emptyToNull(form.get("series"));
            if (brand == null || series == null) {
                return ResponseEntity.badRequest().body(Result.error("品牌與系列為必填欄位，不能為空"));
            }
            app.setBrand(brand);
            app.setSeries(series);
            app.setModel(emptyToNull(form.get("model")));

            if (form.get("purchaseYear") != null && !form.get("purchaseYear").isEmpty()) {
                app.setPurchaseYear(Integer.valueOf(form.get("purchaseYear")));
            } else {
                app.setPurchaseYear(null);
            }
            if (form.get("quantity") != null && !form.get("quantity").isEmpty()) {
                app.setQuantity(Integer.valueOf(form.get("quantity")));
            }
            app.setCondition(emptyToNull(form.get("condition")));
            app.setFunctionStatus(emptyToNull(form.get("functionStatus")));

            // ===== 2. 申請狀態 =====
            if (form.get("status") != null && !form.get("status").isEmpty()) {
                app.setStatus(SellApplication.ApplicationStatus.valueOf(form.get("status")));
            }

            // ===== 3. 鑑定與報價 =====
            if (form.containsKey("estimatedPrice")) {
                String v = emptyToNull(form.get("estimatedPrice"));
                app.setEstimatedPrice(v != null ? new java.math.BigDecimal(v) : null);
            }
            if (form.containsKey("finalPrice")) {
                String v = emptyToNull(form.get("finalPrice"));
                app.setFinalPrice(v != null ? new java.math.BigDecimal(v) : null);
            }
            if (form.containsKey("appraisalNotes")) {
                app.setAppraisalNotes(emptyToNull(form.get("appraisalNotes")));
            }

            // ===== 4. 【新增】關聯平台商品 (productId) =====
            if (form.containsKey("productId")) {
                String productIdStr = form.get("productId");
                if (productIdStr != null && !productIdStr.trim().isEmpty()) {
                    Product product = productRepository.findById(Integer.valueOf(productIdStr))
                            .orElseThrow(() -> new RuntimeException("關聯的商品不存在，ID: " + productIdStr));
                    app.setProduct(product);
                } else {
                    app.setProduct(null); // 若前端傳空值，則清空商品關聯
                }
            }

            // ===== 5. 【新增】負責鑑定師 (appraiserId) =====
            if (form.containsKey("appraiserId")) {
                String appraiserIdStr = form.get("appraiserId");
                if (appraiserIdStr != null && !appraiserIdStr.trim().isEmpty()) {
                    User appraiser = userRepository.findById(Long.valueOf(appraiserIdStr))
                            .orElseThrow(() -> new RuntimeException("指派的鑑定師不存在，ID: " + appraiserIdStr));
                    app.setAppraiser(appraiser);
                } else {
                    app.setAppraiser(null); // 若前端傳空值，則清空鑑定師關聯
                }
            }

            // ===== 6. 圖片管理 (更換 / 移動 / 刪除) =====
            Map<String, String> oldMap = parseImageMap(app.getImagePaths());

            // 6.1 前端回傳「舊圖最終歸屬」JSON
            Map<String, String> finalMap = new java.util.LinkedHashMap<>();
            String imageMapJson = form.get("imageMapJson");
            if (imageMapJson != null && !imageMapJson.isEmpty()) {
                finalMap.putAll(objectMapper.readValue(imageMapJson,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}));
            }

            // 6.2 收集本次新上傳的檔案
            Map<String, MultipartFile> uploads = new HashMap<>();
            multipartRequest.getFileMap().forEach((slot, file) -> {
                if (file != null && !file.isEmpty()) uploads.put(slot, file);
            });

            // 6.3 處理存儲與實體檔案清理
            if (!uploads.isEmpty() || imageMapJson != null) {
                if (!uploads.isEmpty()) {
                    String folder = app.getApplicationId() + "_edit_" + System.currentTimeMillis();
                    Map<String, String> uploaded = fileStorageService.uploadMultipleImages(uploads, folder);
                    finalMap.putAll(uploaded); // 新圖覆蓋同欄位舊圖
                }
                // Diff：舊路徑不在最終結果中 = 被刪除或被更換 → 清理實體檔案
                Set<String> finalPaths = new HashSet<>(finalMap.values());
                for (String oldPath : oldMap.values()) {
                    if (oldPath != null && !finalPaths.contains(oldPath)) {
                        fileStorageService.deleteFile(oldPath);
                    }
                }
                app.setImagePaths(objectMapper.writeValueAsString(finalMap));
            }

            sellApplicationRepository.save(app);
            return ResponseEntity.ok(Result.ok("申請資料更新成功"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error("欄位值無效 (狀態/數字格式錯誤): " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("更新失敗: " + e.getMessage()));
        }
    }

    /** 輔助方法：空字串轉 null */
    private String emptyToNull(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** 【新增】輔助方法：解析 imagePaths JSON 為 Map (失敗回傳空 Map) */
    private Map<String, String> parseImageMap(String json) {
        if (json == null || json.trim().isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    // ==========================================
    // 【新增 API】獲取具備鑑定/銷售權限的員工列表
    // ==========================================
    @Operation(
            summary = "獲取具備鑑定/銷售權限的員工列表",
            description = "獲取 Role 為 SALES、APPRAISER 或 ADMIN 的用戶列表，用於後台分配鑑定師或處理售後。"
    )
    @GetMapping("/api/staff")
    @ResponseBody
    public ResponseEntity<?> getStaffUsers() {

        // 2. 查詢三種具備權限的角色，並使用 Set 自動去重 (防止未來角色權限重疊或重複查詢)
        Set<User> staffUsers = new HashSet<>();
        staffUsers.addAll(userRepository.findByRole(User.Role.SALES));
        staffUsers.addAll(userRepository.findByRole(User.Role.APPRAISER));
        staffUsers.addAll(userRepository.findByRole(User.Role.ADMIN));

        // 3. 轉換為前端需要的輕量級 Map (簡潔的 Stream 寫法，不易報錯)
        List<Map<String, Object>> staffList = staffUsers.stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("name", u.getName() != null ? u.getName() : "");
                    m.put("role", u.getRole().name());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Result.okWithData("獲取成功", staffList));
    }

    @Operation(summary = "獲取品牌列表 (自動完成)")
    @GetMapping("/api/brands/autocomplete")
    @ResponseBody
    public ResponseEntity<?> getBrandAutocomplete(@RequestParam(required = false) String keyword) {
        // 若 keyword 為空字串，傳 null 給 Repository 以觸發 IS NULL 條件獲取全部
        String searchKey = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        List<String> brands = productRepository.findDistinctBrandsByKeyword(searchKey);
        return ResponseEntity.ok(Result.okWithData("獲取成功", brands));
    }
}