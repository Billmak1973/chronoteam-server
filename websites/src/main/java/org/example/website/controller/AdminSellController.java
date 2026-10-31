package org.example.website.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.FileStorageService;
import org.example.website.service.SystemConfigService;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final StoreDailyReportRepository storeDailyReportRepository;
    private final SystemConfigService systemConfigService;

    public AdminSellController(SellApplicationRepository sellApplicationRepository, OfflineStoreRepository offlineStoreRepository, FileStorageService fileStorageService, ObjectMapper objectMapper, UserRepository userRepository, ProductRepository productRepository, StoreDailyReportRepository storeDailyReportRepository, SystemConfigService systemConfigService) {
        this.sellApplicationRepository = sellApplicationRepository;
        this.offlineStoreRepository = offlineStoreRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.storeDailyReportRepository = storeDailyReportRepository;
        this.systemConfigService = systemConfigService;
    }

    @Hidden
    @GetMapping("/sell-applications")
    public String sellApplicationsPage(Model model, org.springframework.security.core.Authentication authentication) {
        model.addAttribute("activePage", "sell-app");

        // 【核心修復】：動態判斷當前用戶是否擁有「最高覆蓋權限」(不僅限於 ADMIN，店主也可以)
        boolean isOverrideRole = false;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            try {
                // 獲取當前用戶的角色名稱 (例如: "STORE_MANAGER")
                org.example.website.security.CustomUserDetails userDetails =
                        (org.example.website.security.CustomUserDetails) authentication.getPrincipal();
                String roleName = userDetails.getRole().name();

                // 獲取系統配置的覆蓋權限列表
                List<String> overrideRoles = systemConfigService.getAfterSalesOverrideRoles();

                // 判斷當前角色是否在列表中
                isOverrideRole = overrideRoles.contains(roleName);
            } catch (Exception e) {
                // 未登入或解析異常，默認為 false
            }
        }

        // 將動態計算的結果傳遞給前端 Thymeleaf
        model.addAttribute("isOverrideRole", isOverrideRole);

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
        List<String> storeIdStrs = appPage.getContent().stream()
                .map(SellApplication::getStoreId)
                .filter(id -> id != null && !id.trim().isEmpty() && id.matches("\\d+"))
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> storeMap = new HashMap<>();
        if (!storeIdStrs.isEmpty()) {
            List<Long> storeIds = storeIdStrs.stream().map(Long::valueOf).collect(Collectors.toList());
            List<OfflineStore> stores = offlineStoreRepository.findByStoreIdIn(storeIds);
            for (OfflineStore store : stores) {
                storeMap.put(String.valueOf(store.getStoreId()), store.getName());
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
    public ResponseEntity<?> getApplicationDetail(@PathVariable Long id, Authentication authentication) {
        SellApplication app = sellApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("申請記錄不存在"));

        // ==========================================
        // 【核心新增】：計算當前登錄用戶是否有權編輯此申請，並標記是否為 Override Role
        // ==========================================
        boolean canEdit = false;
        boolean isOverrideRole = false; // <--- 新增此變量

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String currentUsername = authentication.getName();
            User currentUser = userRepository.findByUsername(currentUsername).orElse(null);

            if (currentUser != null) {
                boolean isStaffLock = systemConfigService.getAfterSalesStaffLock();
                List<String> overrideRoles = systemConfigService.getAfterSalesOverrideRoles();
                List<String> appraisalRoles = systemConfigService.getAppraisalAuthorizedRoles();

                // 判斷是否為最高權限角色
                isOverrideRole = overrideRoles.contains(currentUser.getRole().name()); // <--- 記錄狀態

                // 規則 1：如果沒有開啟鎖定，允許編輯
                if (!isStaffLock) {
                    canEdit = true;
                }
                // 規則 2：如果開啟了鎖定，且當前用戶就是該申請的 operator，允許編輯
                else if (app.getOperator() != null && app.getOperator().getUsername().equals(currentUsername)) {
                    canEdit = true;
                }
                // 規則 3：如果當前用戶的角色在「最高覆蓋權限」列表中，允許編輯
                else if (isOverrideRole) {
                    canEdit = true;
                }

                // 【規則 4 新增】：如果 operator 為 null (尚未被接收)，且當前用戶角色在「鑑定授權角色」列表中，允許編輯 (視為接收並編輯)
                else if (app.getOperator() == null && appraisalRoles.contains(currentUser.getRole().name())) {
                    canEdit = true;
                }
            }
        }

        // 為了防止實體類直接序列化報錯，這裡手動組裝 Map 返回
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
        data.put("imagePaths", app.getImagePaths());
        data.put("transactionMode", app.getTransactionMode().name());
        data.put("status", app.getStatus().name());
        data.put("estimatedPrice", app.getEstimatedPrice());
        data.put("finalPrice", app.getFinalPrice());
        data.put("appraisalNotes", app.getAppraisalNotes());
        data.put("createdAt", app.getCreatedAt());
        data.put("appointmentDate", app.getAppointmentDate() != null ? app.getAppointmentDate().toString() : null);
        data.put("appointmentTimeSlot", app.getAppointmentTimeSlot());

        // 【核心新增】：將權限標識返回給前端，讓前端決定是否渲染手動選擇框
        data.put("canEdit", canEdit);
        data.put("isOverrideRole", isOverrideRole);

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
            appraiserMap.put("id", app.getAppraiser().getId());
            appraiserMap.put("username", app.getAppraiser().getUsername());
            data.put("appraiser", appraiserMap);
        }
        // 處理最後負責人 (Operator)
        if (app.getOperator() != null) {
            Map<String, Object> operatorMap = new HashMap<>();
            operatorMap.put("id", app.getOperator().getId());
            operatorMap.put("username", app.getOperator().getUsername());
            data.put("operator", operatorMap);
        } else {
            data.put("operator", null);
        }
        if (app.getProduct() != null) {
            Map<String, Object> productMap = new HashMap<>();
            productMap.put("productId", app.getProduct().getProductId());
            productMap.put("description", app.getProduct().getDescription());
            productMap.put("price", app.getProduct().getPrice());
            data.put("product", productMap);
        }

        return ResponseEntity.ok(Result.okWithData("獲取成功", data));
    }

    @Operation(summary = "更新出售申請", description = "管理員修改申請狀態、手錶基礎資訊、鑑定報價、關聯商品與鑑定師，並管理實物圖片")
    @PutMapping(value = "/api/sell-applications/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> updateApplication(
            @PathVariable Long id,
            @RequestParam Map<String, String> form,
            MultipartHttpServletRequest multipartRequest,
            Authentication authentication) {

        SellApplication app = sellApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("申請記錄不存在"));

        try {
            // ==========================================
            // 【核心校驗 0】：基於 Config 的動態權限校驗 (雙重保險)
            // ==========================================
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                String currentUsername = authentication.getName();
                User currentUser = userRepository.findByUsername(currentUsername)
                        .orElseThrow(() -> new RuntimeException("操作人不存在: " + currentUsername));

                boolean isStaffLock = systemConfigService.getAfterSalesStaffLock();
                List<String> overrideRoles = systemConfigService.getAfterSalesOverrideRoles();
                List<String> appraisalRoles = systemConfigService.getAppraisalAuthorizedRoles();

                boolean hasPermission = false;
                if (!isStaffLock) {
                    hasPermission = true;
                } else if (app.getOperator() != null && app.getOperator().getUsername().equals(currentUsername)) {
                    hasPermission = true;
                } else if (overrideRoles.contains(currentUser.getRole().name())) {
                    hasPermission = true;
                }    // 【規則 4 新增】：如果 operator 為 null，且當前用戶角色在「鑑定授權角色」列表中，允許操作
                else if (app.getOperator() == null && appraisalRoles.contains(currentUser.getRole().name())) {
                    hasPermission = true;
                }


                if (!hasPermission) {
                    return ResponseEntity.status(403).body(Result.error("您無權編輯此申請，該申請已被其他員工接收或您沒有相應權限。"));
                }

                // ==========================================
                // 【核心校驗 0.5】：精细化核心字段修改權限控制
                // 規則：最高權限可隨意修改。
                //       鑑定授權角色在「非 COMPLETED」狀態下可修改核心字段。
                //       其他情況禁止修改核心字段。
                // ==========================================
                boolean isOverrideRole = overrideRoles.contains(currentUser.getRole().name());
                boolean isAppraisalRole = appraisalRoles.contains(currentUser.getRole().name());
                boolean isCompleted = app.getStatus() == SellApplication.ApplicationStatus.COMPLETED;

                // 如果 不是最高權限，並且 (不是鑑定授權角色 OR 狀態已為 COMPLETED)
                if (!isOverrideRole && (!isAppraisalRole || isCompleted)) {
                    boolean isCoreChanged = false;

                    // 輔助方法：安全比較字符串 (處理 null 和 空字符串)
                    java.util.function.BiPredicate<String, String> isStrChanged = (formVal, originalVal) -> {
                        String f = formVal == null ? "" : formVal.trim();
                        String o = originalVal == null ? "" : originalVal.toString().trim();
                        return !f.equals(o);
                    };

                    if (isStrChanged.test(form.get("brand"), app.getBrand())) isCoreChanged = true;
                    if (isStrChanged.test(form.get("series"), app.getSeries())) isCoreChanged = true;
                    if (isStrChanged.test(form.get("model"), app.getModel())) isCoreChanged = true;
                    if (isStrChanged.test(form.get("condition"), app.getCondition())) isCoreChanged = true;
                    if (isStrChanged.test(form.get("functionStatus"), app.getFunctionStatus())) isCoreChanged = true;
                    if (isStrChanged.test(form.get("status"), app.getStatus() != null ? app.getStatus().name() : "")) isCoreChanged = true;

                    // 處理 Integer 類型
                    Integer formYear = (form.get("purchaseYear") != null && !form.get("purchaseYear").isEmpty()) ? Integer.parseInt(form.get("purchaseYear")) : null;
                    if (!java.util.Objects.equals(formYear, app.getPurchaseYear())) isCoreChanged = true;

                    Integer formQty = (form.get("quantity") != null && !form.get("quantity").isEmpty()) ? Integer.parseInt(form.get("quantity")) : null;
                    if (!java.util.Objects.equals(formQty, app.getQuantity())) isCoreChanged = true;

                    // 處理 Long 類型 (appraiserId)
                    Long formAppraiserId = (form.get("appraiserId") != null && !form.get("appraiserId").isEmpty()) ? Long.parseLong(form.get("appraiserId")) : null;
                    Long originalAppraiserId = app.getAppraiser() != null ? app.getAppraiser().getId() : null;
                    if (!java.util.Objects.equals(formAppraiserId, originalAppraiserId)) isCoreChanged = true;

                    // 處理 BigDecimal 類型 (finalPrice)
                    java.math.BigDecimal formFinalPrice = (form.get("finalPrice") != null && !form.get("finalPrice").isEmpty()) ? new java.math.BigDecimal(form.get("finalPrice")) : null;
                    if (!java.util.Objects.equals(formFinalPrice, app.getFinalPrice())) isCoreChanged = true;

                    if (isCoreChanged) {
                        return ResponseEntity.status(403).body(Result.error("您沒有權限修改核心字段（品牌、型號、狀態、鑑定師、成交價等），或該申請已完成鎖定，請聯繫擁有最高權限的人員！"));
                    }
                }

                // ==========================================
                // 【核心修改】：處理負責人的自動/手動邏輯
                // ==========================================
                if (isOverrideRole && form.containsKey("operatorId") && !form.get("operatorId").trim().isEmpty()) {
                    try {
                        Long operatorId = Long.parseLong(form.get("operatorId").trim());
                        User operator = userRepository.findById(operatorId)
                                .orElseThrow(() -> new RuntimeException("指定的負責人不存在，ID: " + operatorId));
                        app.setOperator(operator); // 手動指定負責人
                    } catch (NumberFormatException e) {
                        return ResponseEntity.badRequest().body(Result.error("負責人ID格式無效"));
                    }
                } else {
                    // 非最高權限角色，或前端未傳遞 operatorId (選擇了"自動")，則自動記錄為當前登入者
                    app.setOperator(currentUser);
                }

            } else {
                return ResponseEntity.status(401).body(Result.error("請先登入"));
            }

            // ==========================================
            // 【核心校驗 1】：檢查是否試圖將狀態改為 COMPLETED，若是則必須填寫鑑定師和最終成交價
            // ==========================================
            boolean isChangingToCompleted = false;
            if (form.get("status") != null && !form.get("status").isEmpty()) {
                SellApplication.ApplicationStatus newStatus = SellApplication.ApplicationStatus.valueOf(form.get("status"));
                if (newStatus == SellApplication.ApplicationStatus.COMPLETED) {
                    isChangingToCompleted = true;
                    app.setCompletedAt(java.time.LocalDateTime.now());
                    String appraiserIdStr = form.get("appraiserId");
                    String finalPriceStr = form.get("finalPrice");

                    if (appraiserIdStr == null || appraiserIdStr.trim().isEmpty()) {
                        return ResponseEntity.badRequest().body(Result.error("狀態為「已完成」時，負責鑑定師不能為空！"));
                    }
                    if (finalPriceStr == null || finalPriceStr.trim().isEmpty()) {
                        return ResponseEntity.badRequest().body(Result.error("狀態為「已完成」時，最終成交價不能為空！"));
                    }
                }
            }

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

            // ===== 4. 關聯平台商品 (productId) =====
            if (form.containsKey("productId")) {
                String productIdStr = form.get("productId");
                if (productIdStr != null && !productIdStr.trim().isEmpty()) {
                    Product product = productRepository.findById(Integer.valueOf(productIdStr))
                            .orElseThrow(() -> new RuntimeException("關聯的商品不存在，ID: " + productIdStr));
                    app.setProduct(product);
                } else {
                    app.setProduct(null);
                }
            }

            // ===== 5. 負責鑑定師 (appraiserId) =====
            if (form.containsKey("appraiserId")) {
                String appraiserIdStr = form.get("appraiserId");
                if (appraiserIdStr != null && !appraiserIdStr.trim().isEmpty()) {
                    User appraiser = userRepository.findById(Long.valueOf(appraiserIdStr))
                            .orElseThrow(() -> new RuntimeException("指派的鑑定師不存在，ID: " + appraiserIdStr));
                    app.setAppraiser(appraiser);
                } else {
                    app.setAppraiser(null);
                }
            }

            // ===== 6. 圖片管理 (更換 / 移動 / 刪除) =====
            Map<String, String> oldMap = parseImageMap(app.getImagePaths());
            Map<String, String> finalMap = new java.util.LinkedHashMap<>();
            String imageMapJson = form.get("imageMapJson");
            if (imageMapJson != null && !imageMapJson.isEmpty()) {
                finalMap.putAll(objectMapper.readValue(imageMapJson,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}));
            }

            Map<String, MultipartFile> uploads = new HashMap<>();
            multipartRequest.getFileMap().forEach((slot, file) -> {
                if (file != null && !file.isEmpty()) uploads.put(slot, file);
            });

            if (!uploads.isEmpty() || imageMapJson != null) {
                if (!uploads.isEmpty()) {
                    String folder = "sell_app_" + app.getApplicationId();
                    Map<String, String> uploaded = fileStorageService.uploadMultipleImages(uploads, folder);
                    finalMap.putAll(uploaded);
                }
                Set<String> finalPaths = new HashSet<>(finalMap.values());
                for (String oldPath : oldMap.values()) {
                    if (oldPath != null && !finalPaths.contains(oldPath)) {
                        fileStorageService.deleteFile(oldPath);
                    }
                }
                app.setImagePaths(objectMapper.writeValueAsString(finalMap));
            }

            sellApplicationRepository.save(app);

            // ==========================================
            // 【核心新增】：如果狀態剛剛變更為 COMPLETED，且有關聯門店，則更新門店每日報表
            // ==========================================
            if (isChangingToCompleted && app.getStoreId() != null && !app.getStoreId().trim().isEmpty()) {
                try {
                    Long storeId = Long.parseLong(app.getStoreId());
                    LocalDate reportDate = LocalDate.now();

                    StoreDailyReport report = storeDailyReportRepository.findByStore_StoreIdAndReportDate(storeId, reportDate)
                            .orElseGet(() -> {
                                OfflineStore store = offlineStoreRepository.findById(storeId)
                                        .orElseThrow(() -> new RuntimeException("門店不存在, ID: " + storeId));
                                StoreDailyReport newReport = new StoreDailyReport();
                                newReport.setStore(store);
                                newReport.setReportDate(reportDate);
                                return newReport;
                            });

                    BigDecimal finalPrice = app.getFinalPrice() != null ? app.getFinalPrice() : BigDecimal.ZERO;
                    BigDecimal currentCost = report.getStoreAcquisitionCost() != null ? report.getStoreAcquisitionCost() : BigDecimal.ZERO;
                    report.setStoreAcquisitionCost(currentCost.add(finalPrice));
                    storeDailyReportRepository.save(report);
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ 無效的門店ID格式，無法更新門店每日報表: " + app.getStoreId());
                } catch (Exception e) {
                    System.err.println("⚠️ 更新門店每日報表失敗: " + e.getMessage());
                }
            }
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
            description = "根據系統配置動態獲取具備權限的用戶列表，用於後台分配鑑定師或處理售後。"
    )
    @GetMapping("/api/staff")
    @ResponseBody
    public ResponseEntity<?> getStaffUsers() {

        // 1. 【核心修改】：從系統配置中動態獲取允許執行鑑定操作的角色列表 (徹底消除硬編碼)
        List<String> authorizedRoles = systemConfigService.getAppraisalAuthorizedRoles();

        // 2. 動態查詢這些角色的用戶，並使用 Set 自動去重 (防止未來角色權限重疊或重複查詢)
        Set<User> staffUsers = new HashSet<>();
        for (String roleName : authorizedRoles) {
            try {
                // 將字符串轉換為枚舉並查詢
                User.Role role = User.Role.valueOf(roleName);
                staffUsers.addAll(userRepository.findByRole(role));
            } catch (IllegalArgumentException e) {
                // 容錯處理：忽略配置中無效的角色名稱，防止系統崩潰
                System.err.println("⚠️ 系統配置中存在無效的角色名稱: " + roleName);
            }
        }

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