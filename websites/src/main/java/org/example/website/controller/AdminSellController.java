package org.example.website.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.website.dto.Result;
import org.example.website.dto.SellApplicationDTO;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final SellApplicationAuditLogRepository auditLogRepository; // 確保已注入
    private final StoreInventoryRepository storeInventoryRepository;
    private final InventoryAdjustmentLogRepository adjustmentLogRepository;

    public AdminSellController(SellApplicationRepository sellApplicationRepository, OfflineStoreRepository offlineStoreRepository, FileStorageService fileStorageService, ObjectMapper objectMapper, UserRepository userRepository, ProductRepository productRepository, StoreDailyReportRepository storeDailyReportRepository, SystemConfigService systemConfigService, SellApplicationAuditLogRepository auditLogRepository, StoreInventoryRepository storeInventoryRepository, InventoryAdjustmentLogRepository adjustmentLogRepository) {
        this.sellApplicationRepository = sellApplicationRepository;
        this.offlineStoreRepository = offlineStoreRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.storeDailyReportRepository = storeDailyReportRepository;
        this.systemConfigService = systemConfigService;
        this.auditLogRepository = auditLogRepository;
        this.storeInventoryRepository = storeInventoryRepository;
        this.adjustmentLogRepository = adjustmentLogRepository;
    }


    @Hidden
    @GetMapping("/sell-applications")
    public String sellApplicationsPage(Model model, org.springframework.security.core.Authentication authentication) {
        model.addAttribute("activePage", "sell-app");

        // 【核心修復】：動態判斷當前用戶是否擁有「最高覆蓋權限」
        boolean isOverrideRole = false;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            try {
                org.example.website.security.CustomUserDetails userDetails =
                        (org.example.website.security.CustomUserDetails) authentication.getPrincipal();
                String roleName = userDetails.getRole().name();
                List<String> overrideRoles = systemConfigService.getAfterSalesOverrideRoles();
                isOverrideRole = overrideRoles.contains(roleName);
            } catch (Exception e) {
                // 未登入或解析異常，默認為 false
            }
        }
        model.addAttribute("isOverrideRole", isOverrideRole);

        // 獲取所有出現過的商店選項 (通過 storeId 關聯獲取 name)
        List<String> storeIdStrs = sellApplicationRepository.findDistinctStoreIds();
        List<Map<String, Object>> storeOptions = new ArrayList<>();
        if (storeIdStrs != null && !storeIdStrs.isEmpty()) {
            List<Long> storeIds = storeIdStrs.stream()
                    .filter(id -> id != null && id.matches("\\d+"))
                    .map(Long::valueOf)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            if (!storeIds.isEmpty()) {
                List<OfflineStore> stores = offlineStoreRepository.findByStoreIdIn(storeIds);
                for (OfflineStore store : stores) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("storeId", store.getStoreId());
                    map.put("name", store.getName());
                    storeOptions.add(map);
                }
            }
        }
        model.addAttribute("storeOptions", storeOptions);

        // 獲取所有出現過的品牌選項 (去重)
        List<String> brandOptions = sellApplicationRepository.findDistinctBrands();
        model.addAttribute("brandOptions", brandOptions);

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
            map.put("isStockedIn", app.getIsStockedIn()); // 確保列表能獲取入庫狀態

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
        // 【核心新增】：計算當前登錄用戶是否有權編輯此申請，並標記權限角色
        // ==========================================
        boolean canEdit = false;
        boolean isOverrideRole = false;
        boolean isAppraisalRole = false; // 聲明鑑定授權角色標識，提升作用域
        User currentUser = null; // 將聲明移到 if 外部，解決作用域問題

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String currentUsername = authentication.getName();
            currentUser = userRepository.findByUsername(currentUsername).orElse(null);

            if (currentUser != null) {
                boolean isStaffLock = systemConfigService.getAfterSalesStaffLock();
                List<String> overrideRoles = systemConfigService.getAfterSalesOverrideRoles();
                List<String> appraisalRoles = systemConfigService.getAppraisalAuthorizedRoles();

                // 判斷是否為最高權限角色
                isOverrideRole = overrideRoles.contains(currentUser.getRole().name());

                // 【新增】：判斷當前用戶是否為鑑定授權角色
                isAppraisalRole = appraisalRoles.contains(currentUser.getRole().name());

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
                else if (app.getOperator() == null && isAppraisalRole) {
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
        data.put("isStockedIn", app.getIsStockedIn()); // 確保詳情頁能獲取入庫狀態

        // 將權限標識與當前用戶資訊返回給前端，讓前端決定如何渲染
        data.put("canEdit", canEdit);
        data.put("isOverrideRole", isOverrideRole);
        data.put("isAppraisalRole", isAppraisalRole); // 傳遞鑑定授權角色標識

        if (currentUser != null) {
            data.put("currentUserId", currentUser.getId());       // 返回當前登入用戶的 ID
            data.put("currentUsername", currentUser.getUsername()); // 傳遞當前用戶名
        }

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

// ==========================================
// 【核心新增】：查詢並組裝審計日誌 (Audit Logs)
// ==========================================
// 1. 查詢該申請的所有審計日誌
        List<SellApplicationAuditLog> auditLogs = auditLogRepository.findAllByApplication_ApplicationIdOrderByCreatedAtDesc(id);

// 2. 【核心修改】：解析 changedFields，收集所有涉及的用戶 ID，以便批量查詢用戶名
// 注意：需要引入 com.fasterxml.jackson.databind.ObjectMapper;
        ObjectMapper objectMapper = new ObjectMapper(); // 如果類裡沒注入，這裡 new 一個，或者用 @Autowired 注入的
        Set<Long> userIdsToFetch = new HashSet<>();

        for (SellApplicationAuditLog log : auditLogs) {
            try {
                // changedFields 存的是 JSON 字符串，例如：[{"newValue":"1","fieldName":"operatorId","oldValue":"5"}]
                JsonNode rootNode = objectMapper.readTree(log.getChangedFields());
                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        String fieldName = node.get("fieldName").asText();
                        // 只處理負責人相關字段
                        if ("operatorId".equals(fieldName) || "appraiserId".equals(fieldName)) {
                            if (node.has("oldValue") && !node.get("oldValue").isNull() && !node.get("oldValue").asText().isEmpty()) {
                                try { userIdsToFetch.add(Long.parseLong(node.get("oldValue").asText())); } catch (Exception ignored) {}
                            }
                            if (node.has("newValue") && !node.get("newValue").isNull() && !node.get("newValue").asText().isEmpty()) {
                                try { userIdsToFetch.add(Long.parseLong(node.get("newValue").asText())); } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略解析錯誤
            }
        }

// 3. 批量查詢用戶信息 (ID -> Username)
// 使用 userRepository.findAllById 一次性查出來，避免 N+1 問題
        List<User> users = userRepository.findAllById(userIdsToFetch);
        Map<String, String> idToNameMap = new HashMap<>();
        for (User u : users) {
            // 這裡決定顯示 username 還是 name。看你的截圖是 "admin"，通常是 username。
            // 如果想顯示真實姓名，改為 u.getName()
            idToNameMap.put(String.valueOf(u.getId()), u.getUsername());
        }

// 4. 構建返回給前端的日誌列表，並替換 ID 為名字
        List<Map<String, Object>> cleanAuditLogs = auditLogs.stream().map(log -> {
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("actionType", log.getActionType());

            // 【核心修改】：處理 changedFields，將 ID 替換為名字
            String processedChangedFields = log.getChangedFields();
            try {
                JsonNode rootNode = objectMapper.readTree(log.getChangedFields());
                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        String fieldName = node.get("fieldName").asText();
                        if ("operatorId".equals(fieldName) || "appraiserId".equals(fieldName)) {
                            // 替換 oldValue
                            if (node.has("oldValue") && !node.get("oldValue").isNull()) {
                                String oldIdStr = node.get("oldValue").asText();
                                if (idToNameMap.containsKey(oldIdStr)) {
                                    // 將 JSON 節點的值改為用戶名
                                    ((ObjectNode) node).put("oldValue", idToNameMap.get(oldIdStr));
                                }
                            }
                            // 替換 newValue
                            if (node.has("newValue") && !node.get("newValue").isNull()) {
                                String newIdStr = node.get("newValue").asText();
                                if (idToNameMap.containsKey(newIdStr)) {
                                    ((ObjectNode) node).put("newValue", idToNameMap.get(newIdStr));
                                }
                            }
                        }
                    }
                    // 重新序列化為字符串
                    processedChangedFields = objectMapper.writeValueAsString(rootNode);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            logMap.put("changedFields", processedChangedFields);
            logMap.put("remark", log.getRemark());
            logMap.put("createdAt", log.getCreatedAt());

            // 操作日誌的人 (Operator) - 這個本來就有名字，不用動
            Map<String, Object> logOperatorMap = new HashMap<>();
            if (log.getOperator() != null) {
                logOperatorMap.put("id", log.getOperator().getId());
                logOperatorMap.put("username", log.getOperator().getUsername());
            }
            logMap.put("operator", logOperatorMap);

            return logMap;
        }).collect(Collectors.toList());

        data.put("auditLogs", cleanAuditLogs);

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
            // 【步驟 1】：在修改前，記錄所有需要審計的「舊值」
            // ==========================================
            SellApplication.ApplicationStatus oldStatus = app.getStatus();
            java.time.LocalDateTime oldCompletedAt = app.getCompletedAt();
            String oldImagePathsJson = app.getImagePaths();
            String oldBrand = app.getBrand();
            String oldSeries = app.getSeries();
            String oldModel = app.getModel();
            Integer oldPurchaseYear = app.getPurchaseYear();
            Integer oldQuantity = app.getQuantity();
            String oldCondition = app.getCondition();
            String oldFunctionStatus = app.getFunctionStatus();
            Long oldAppraiserId = app.getAppraiser() != null ? app.getAppraiser().getId() : null;

            // 【新增】：備份原本的主要負責人
            Long oldOperatorId = app.getOperator() != null ? app.getOperator().getId() : null;

            java.math.BigDecimal oldFinalPrice = app.getFinalPrice();
            java.math.BigDecimal oldEstimatedPrice = app.getEstimatedPrice();
            Integer oldProductId = app.getProduct() != null ? app.getProduct().getProductId() : null;
            String oldAppraisalNotes = app.getAppraisalNotes();

            // ==========================================
            // 【核心修復】：將需要在方法末尾使用的變數聲明提升到 try 塊頂部，解決作用域問題
            // ==========================================
            User currentUser = null;
            String currentUsername = null;
            boolean isOverrideRole = false;
            boolean isMyApplication = false;
            boolean isCompleted = false;
            String modificationReason = null;

            // ==========================================
            // 【核心校驗 0】：基於 Config 的動態權限校驗 (雙重保險)
            // ==========================================
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                final String authUsername = authentication.getName();
                currentUser = userRepository.findByUsername(authUsername)
                        .orElseThrow(() -> new RuntimeException("操作人不存在: " + authUsername));

                boolean isStaffLock = systemConfigService.getAfterSalesStaffLock();
                List<String> overrideRoles = systemConfigService.getAfterSalesOverrideRoles();
                List<String> appraisalRoles = systemConfigService.getAppraisalAuthorizedRoles();

                boolean hasPermission = false;
                if (!isStaffLock) {
                    hasPermission = true;
                } else if (app.getOperator() != null && app.getOperator().getUsername().equals(authUsername)) {
                    hasPermission = true;
                } else if (overrideRoles.contains(currentUser.getRole().name())) {
                    hasPermission = true;
                } else if (app.getOperator() == null && appraisalRoles.contains(currentUser.getRole().name())) {
                    hasPermission = true;
                }

                if (!hasPermission) {
                    return ResponseEntity.status(403).body(Result.error("您無權編輯此申請，該申請已被其他員工接收或您沒有相應權限。"));
                }

                // ==========================================
                // 【核心校驗 0.5】：精细化核心字段修改權限控制
                // ==========================================
                isOverrideRole = overrideRoles.contains(currentUser.getRole().name());
                boolean isAppraisalRole = appraisalRoles.contains(currentUser.getRole().name());
                isCompleted = app.getStatus() == SellApplication.ApplicationStatus.COMPLETED;
                isMyApplication = app.getOperator() != null && app.getOperator().getId().equals(currentUser.getId());

// ==========================================
// 【新增】：鑑定師與主要負責人獨立權限校驗 (嚴格支援認領邏輯)
// ==========================================
                String operatorIdStr = form.get("operatorId");
                Long requestedOperatorId = null;
                if (operatorIdStr != null && !operatorIdStr.trim().isEmpty()) {
                    requestedOperatorId = Long.valueOf(operatorIdStr.trim());
                }

                // 1. 非最高權限者的 operatorId 修改限制
                if (!isOverrideRole) {
                    // 允許的情況 A：認領 (原本沒有負責人 oldOperatorId == null，現在指定為自己)
                    boolean isClaiming = (oldOperatorId == null && requestedOperatorId != null && requestedOperatorId.equals(currentUser.getId()));
                    // 允許的情況 B：保持 (原本就是自己，現在還是自己)
                    boolean isKeepingOwn = (oldOperatorId != null && oldOperatorId.equals(currentUser.getId()) && requestedOperatorId != null && requestedOperatorId.equals(currentUser.getId()));

                    // 如果不是認領，也不是保持自己的狀態，且表單中包含了 operatorId，則攔截
                    if (!isClaiming && !isKeepingOwn && form.containsKey("operatorId")) {
                        return ResponseEntity.status(403).body(Result.error("只有最高權限者才可以修改主要負責人，或您無權認領此已被接手的訂單！"));
                    }
                }

                // 2. 鑑定師發生變更時，必須明確指定主要負責人
                String appraiserIdStr = form.get("appraiserId");
                Long requestedAppraiserId = null;
                if (appraiserIdStr != null && !appraiserIdStr.trim().isEmpty()) {
                    requestedAppraiserId = Long.valueOf(appraiserIdStr.trim());
                }

                boolean isAppraiserChanged = !java.util.Objects.equals(requestedAppraiserId, oldAppraiserId);
                if (isAppraiserChanged) {
                    if (operatorIdStr == null || operatorIdStr.trim().isEmpty()) {
                        return ResponseEntity.badRequest().body(Result.error("修改負責鑑定師時，必須明確指定主要負責人！"));
                    }
                }

                    // 3. 原有的核心字段修改限制 (保持不變)
                if (!isOverrideRole && (!isAppraisalRole || isCompleted)) {
                    boolean isCoreChanged = false;
                    java.util.function.BiPredicate<String, String> isStrChanged = (formVal, originalVal) -> {
                        String f = formVal == null ? "" : formVal.trim();
                        String o = originalVal == null ? "" : originalVal.toString().trim();
                        return !f.equals(o);
                    };

                    if (isStrChanged.test(form.get("brand"), oldBrand)) isCoreChanged = true;
                    if (isStrChanged.test(form.get("series"), oldSeries)) isCoreChanged = true;
                    if (isStrChanged.test(form.get("model"), oldModel)) isCoreChanged = true;
                    if (isStrChanged.test(form.get("condition"), oldCondition)) isCoreChanged = true;
                    if (isStrChanged.test(form.get("functionStatus"), oldFunctionStatus)) isCoreChanged = true;
                    if (isStrChanged.test(form.get("status"), oldStatus != null ? oldStatus.name() : "")) isCoreChanged = true;

                    Integer formYear = (form.get("purchaseYear") != null && !form.get("purchaseYear").isEmpty()) ? Integer.parseInt(form.get("purchaseYear")) : null;
                    if (!java.util.Objects.equals(formYear, oldPurchaseYear)) isCoreChanged = true;

                    Integer formQty = (form.get("quantity") != null && !form.get("quantity").isEmpty()) ? Integer.parseInt(form.get("quantity")) : null;
                    if (!java.util.Objects.equals(formQty, oldQuantity)) isCoreChanged = true;

                    if (!java.util.Objects.equals(requestedAppraiserId, oldAppraiserId)) isCoreChanged = true;

                    java.math.BigDecimal formFinalPrice = (form.get("finalPrice") != null && !form.get("finalPrice").isEmpty()) ? new java.math.BigDecimal(form.get("finalPrice")) : null;
                    if (!java.util.Objects.equals(formFinalPrice, oldFinalPrice)) isCoreChanged = true;

                    if (isCoreChanged) {
                        return ResponseEntity.status(403).body(Result.error("您沒有權限修改核心字段（品牌、型號、狀態、鑑定師、成交價等），或該申請已完成鎖定，請聯繫擁有最高權限的人員！"));
                    }
                }

                // ==========================================
                // 【核心新增校驗】：最高權限修改非本人或已完成申請時，強制要求填寫原因
                // ==========================================
                modificationReason = form.get("modificationReason"); // 【賦值】
                // 如果申請尚未被接收 (operator 為 null)，則不強制要求填寫修改原因
                if (isOverrideRole && app.getOperator() != null && (!isMyApplication || isCompleted)) {
                    if (modificationReason == null || modificationReason.trim().isEmpty()) {
                        return ResponseEntity.badRequest().body(Result.error("最高權限修改非本人申請或已完成的申請時，必須填寫「修改原因」以供審計！"));
                    }
                }

                // 【已刪除】：處理負責人的自動/手動邏輯已完全移除

            } else {
                return ResponseEntity.status(401).body(Result.error("請先登入"));
            }

            // ==========================================
            // 【核心校驗 1】：檢查是否試圖將狀態改為 COMPLETED
            // ==========================================
            boolean isChangingToCompleted = false;
            boolean isChangingFromCompletedToOther = false; // 標記是否從 COMPLETED 變為其他狀態
            SellApplication.ApplicationStatus newStatus = null;
            if (form.get("status") != null && !form.get("status").isEmpty()) {
                newStatus = SellApplication.ApplicationStatus.valueOf(form.get("status"));

                // 如果已經入庫 (is_stocked_in == true)，則禁止修改狀態
                if (Boolean.TRUE.equals(app.getIsStockedIn()) && newStatus != oldStatus) {
                    return ResponseEntity.badRequest().body(Result.error("該申請已入庫，狀態已被鎖定，無法修改！"));
                }

                if (newStatus == SellApplication.ApplicationStatus.COMPLETED && oldStatus != SellApplication.ApplicationStatus.COMPLETED) {
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
                } //如果原本是 COMPLETED，但現在改成了其他狀態
                else if (oldStatus == SellApplication.ApplicationStatus.COMPLETED && newStatus != SellApplication.ApplicationStatus.COMPLETED) {
                    isChangingFromCompletedToOther = true;
                    app.setCompletedAt(null); // 【核心】清空完成時間
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

            // ===== 6. 指定負責人 (operatorId) =====
            if (form.containsKey("operatorId")) {
                String operatorIdStr = form.get("operatorId");
                if (operatorIdStr != null && !operatorIdStr.trim().isEmpty()) {
                    User operator = userRepository.findById(Long.valueOf(operatorIdStr))
                            .orElseThrow(() -> new RuntimeException("指定的負責人不存在，ID: " + operatorIdStr));
                    app.setOperator(operator);
                } else {
                    app.setOperator(null);
                }
            }

            // ===== 7. 圖片管理 (更換 / 移動 / 刪除) =====
            Map<String, String> oldMap = parseImageMap(oldImagePathsJson);
            Map<String, String> finalMap = new java.util.LinkedHashMap<>(oldMap);

            String imageMapJson = form.get("imageMapJson");
            if (imageMapJson != null && !imageMapJson.isEmpty()) {
                finalMap.clear();
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

            // ==========================================
            // 審計日誌 Diff 比對與記錄邏輯 (嚴格比對，杜絕誤判)
            // ==========================================
            List<Map<String, String>> changesList = new ArrayList<>();

            java.util.function.BiPredicate<String, String> isStrChanged = (newVal, oldVal) -> {
                String n = newVal == null ? "" : newVal.trim();
                String o = oldVal == null ? "" : oldVal.toString().trim();
                return !n.equals(o);
            };

            java.util.function.BiPredicate<java.math.BigDecimal, java.math.BigDecimal> isBigDecimalChanged = (newVal, oldVal) -> {
                if (newVal == null && oldVal == null) return false;
                if (newVal == null || oldVal == null) return true;
                return newVal.compareTo(oldVal) != 0;
            };

            String newImagePathsJson = app.getImagePaths();
            boolean isImageChanged = !java.util.Objects.equals(oldImagePathsJson, newImagePathsJson);
            if (isImageChanged) {
                addChange(changesList, "imagePaths", oldImagePathsJson, newImagePathsJson);
            }

            if (isStrChanged.test(app.getBrand(), oldBrand)) addChange(changesList, "brand", oldBrand, app.getBrand());
            if (isStrChanged.test(app.getSeries(), oldSeries)) addChange(changesList, "series", oldSeries, app.getSeries());
            if (isStrChanged.test(app.getModel(), oldModel)) addChange(changesList, "model", oldModel, app.getModel());
            if (isStrChanged.test(app.getCondition(), oldCondition)) addChange(changesList, "condition", oldCondition, app.getCondition());
            if (isStrChanged.test(app.getFunctionStatus(), oldFunctionStatus)) addChange(changesList, "functionStatus", oldFunctionStatus, app.getFunctionStatus());

            if (isStrChanged.test(app.getAppraisalNotes(), oldAppraisalNotes)) {
                addChange(changesList, "appraisalNotes", oldAppraisalNotes, app.getAppraisalNotes());
            }

            String newStatusStr = app.getStatus() != null ? app.getStatus().name() : "";
            String oldStatusStr = oldStatus != null ? oldStatus.name() : "";
            if (isStrChanged.test(newStatusStr, oldStatusStr)) addChange(changesList, "status", oldStatusStr, newStatusStr);

            if (!java.util.Objects.equals(app.getPurchaseYear(), oldPurchaseYear)) addChange(changesList, "purchaseYear", String.valueOf(oldPurchaseYear), String.valueOf(app.getPurchaseYear()));
            if (!java.util.Objects.equals(app.getQuantity(), oldQuantity)) addChange(changesList, "quantity", String.valueOf(oldQuantity), String.valueOf(app.getQuantity()));

            Long newAppraiserId = app.getAppraiser() != null ? app.getAppraiser().getId() : null;
            if (!java.util.Objects.equals(newAppraiserId, oldAppraiserId)) {
                addChange(changesList, "appraiserId", String.valueOf(oldAppraiserId), String.valueOf(newAppraiserId));
            }

            // ==========================================
            // 【新增】：主要負責人變更審計
            // ==========================================
            Long newOperatorId = app.getOperator() != null ? app.getOperator().getId() : null;
            if (!java.util.Objects.equals(newOperatorId, oldOperatorId)) {
                addChange(changesList, "operatorId", String.valueOf(oldOperatorId), String.valueOf(newOperatorId));
            }

            if (isBigDecimalChanged.test(app.getEstimatedPrice(), oldEstimatedPrice)) {
                addChange(changesList, "estimatedPrice", String.valueOf(oldEstimatedPrice), String.valueOf(app.getEstimatedPrice()));
            }
            if (isBigDecimalChanged.test(app.getFinalPrice(), oldFinalPrice)) {
                addChange(changesList, "finalPrice", String.valueOf(oldFinalPrice), String.valueOf(app.getFinalPrice()));
            }

            Integer newProductId = app.getProduct() != null ? app.getProduct().getProductId() : null;
            if (!java.util.Objects.equals(newProductId, oldProductId)) addChange(changesList, "productId", String.valueOf(oldProductId), String.valueOf(newProductId));

            boolean hasRemark = modificationReason != null && !modificationReason.trim().isEmpty();

            if (changesList.isEmpty() && !isImageChanged && !hasRemark) {
                return ResponseEntity.ok(Result.ok("申請資料未發生任何變更，無需保存"));
            }

            String actionType = "UPDATE_APPLICATION";
            if (isChangingToCompleted) {
                actionType = "COMPLETE_TRANSACTION";
            } else if (isImageChanged) {
                actionType = "UPDATE_IMAGES";
            }

            // ==========================================
            // 9. 持久化審計日誌 (現在這些變數都在作用域內，不會報錯了)
            // ==========================================
            if (isOverrideRole && (!isMyApplication || isCompleted)) {
                SellApplicationAuditLog auditLog = new SellApplicationAuditLog();
                auditLog.setApplication(app);
                auditLog.setOperator(currentUser);
                auditLog.setActionType(actionType);
                auditLog.setChangedFields(objectMapper.writeValueAsString(changesList));
                auditLog.setRemark(modificationReason); // 核心：保存到 remark 字段

                auditLogRepository.save(auditLog);
            }

            // ==========================================
            // 保存業務數據 (先保存申請，確保 app.getCompletedAt() 等數據是最新的)
            // ==========================================
            sellApplicationRepository.save(app);

            // ==========================================
            // 【 核心財務報表更新邏輯整合 (修正版) 】
            // 觸發條件:
            // 1. 狀態剛剛變更為 COMPLETED (isChangingToCompleted) -> 首次計入
            // 2. 狀態已經是 COMPLETED，但 finalPrice 被修改了 -> 差額修正
            // 3. 從 COMPLETED 變更為其他狀態，需扣減原 finalPrice
            // ==========================================
            boolean isFinalPriceChanged = isBigDecimalChanged.test(app.getFinalPrice(), oldFinalPrice);

            // 【核心修改】：只要涉及門店且狀態/價格有變動，就進入處理
            if (app.getStoreId() != null && !app.getStoreId().trim().isEmpty()) {

                if (isChangingToCompleted || (oldStatus == SellApplication.ApplicationStatus.COMPLETED && isFinalPriceChanged)) {
                    // === 情況 A & B：變更為 COMPLETED，或已是 COMPLETED 但修改了價格 (保持您原有的邏輯) ===
                    try {
                        Long storeId = Long.parseLong(app.getStoreId());
                        LocalDate reportDate = (app.getCompletedAt() != null) ? app.getCompletedAt().toLocalDate() : LocalDate.now();

                        StoreDailyReport report = storeDailyReportRepository.findByStore_StoreIdAndReportDate(storeId, reportDate)
                                .orElseGet(() -> {
                                    StoreDailyReport newReport = new StoreDailyReport();
                                    OfflineStore store = offlineStoreRepository.findById(storeId)
                                            .orElseThrow(() -> new RuntimeException("找不到關聯的門店，ID: " + storeId));
                                    newReport.setStore(store);
                                    newReport.setReportDate(reportDate);
                                    return newReport;
                                });

                        java.math.BigDecimal currentCost = report.getStoreAcquisitionCost() != null ? report.getStoreAcquisitionCost() : java.math.BigDecimal.ZERO;
                        java.math.BigDecimal newFinalPrice = app.getFinalPrice() != null ? app.getFinalPrice() : java.math.BigDecimal.ZERO;

                        if (isChangingToCompleted) {
                            report.setStoreAcquisitionCost(currentCost.add(newFinalPrice));
                        } else {
                            java.math.BigDecimal oldPriceVal = oldFinalPrice != null ? oldFinalPrice : java.math.BigDecimal.ZERO;
                            java.math.BigDecimal diff = newFinalPrice.subtract(oldPriceVal);
                            report.setStoreAcquisitionCost(currentCost.add(diff));
                        }
                        storeDailyReportRepository.save(report);
                    } catch (Exception e) {
                        throw new RuntimeException("更新門店每日報表失敗: " + e.getMessage());
                    }

                } else if (isChangingFromCompletedToOther) {
                    // === 【新增情況 C】：從 COMPLETED 變更為其他狀態，需扣減原 finalPrice ===
                    try {
                        Long storeId = Long.parseLong(app.getStoreId());

                        // 【關鍵】：必須使用備份的 oldCompletedAt，因為 app.getCompletedAt() 剛剛已經被設為 null 了！
                        LocalDate reportDate = (oldCompletedAt != null) ? oldCompletedAt.toLocalDate() : LocalDate.now();

                        StoreDailyReport report = storeDailyReportRepository.findByStore_StoreIdAndReportDate(storeId, reportDate).orElse(null);

                        if (report != null) {
                            java.math.BigDecimal currentCost = report.getStoreAcquisitionCost() != null ? report.getStoreAcquisitionCost() : java.math.BigDecimal.ZERO;
                            java.math.BigDecimal oldPriceVal = oldFinalPrice != null ? oldFinalPrice : java.math.BigDecimal.ZERO;

                            // 【核心】：從收購成本中減去舊的最終成交價
                            report.setStoreAcquisitionCost(currentCost.subtract(oldPriceVal));

                            storeDailyReportRepository.save(report);
                            System.out.println("⚠️ [財務審計] 檢測到申請從 COMPLETED 變更為其他狀態，門店 [" + storeId + "] 收購成本已扣減: " + oldPriceVal);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("⚠️ 無效的門店ID格式，無法更新門店每日報表: " + app.getStoreId());
                    } catch (Exception e) {
                        System.err.println("⚠️ 更新門店每日報表失敗: " + e.getMessage());
                        throw new RuntimeException("更新門店每日報表失敗: " + e.getMessage());
                    }
                }
            }

            return ResponseEntity.ok(Result.ok("申請資料更新成功"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error("欄位值無效 (狀態/數字格式錯誤): " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("更新失敗: " + e.getMessage()));
        }
    }
    /**
     * 【輔助方法】：快速構建變更記錄 Map
     */
    private void addChange(List<Map<String, String>> changesList, String fieldName, Object oldValue, Object newValue) {
        Map<String, String> change = new HashMap<>();
        change.put("fieldName", fieldName);
        change.put("oldValue", oldValue != null ? String.valueOf(oldValue) : "");
        change.put("newValue", newValue != null ? String.valueOf(newValue) : "");
        changesList.add(change);
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

    @Operation(summary = "確認出售申請入庫", description = "將已完成的出售申請商品入庫至指定門店庫存")
    @PostMapping("/api/sell-applications/{id}/stock-in")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> stockInSellApplication(
            @PathVariable Long id,
            Authentication authentication) {

        // 1. 查找申請記錄
        SellApplication app = sellApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("申請記錄不存在"));

        // 2. 核心校驗：必須是 COMPLETED 且未入庫
        if (app.getStatus() != SellApplication.ApplicationStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(Result.error("只有狀態為「已完成」的申請才能入庫"));
        }
        if (Boolean.TRUE.equals(app.getIsStockedIn())) {
            return ResponseEntity.badRequest().body(Result.error("該申請已經入庫，請勿重複操作"));
        }

        // 3. 獲取門店資訊
        if (app.getStoreId() == null || app.getStoreId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Result.error("該申請未關聯門店，無法執行門店入庫！"));
        }
        Long storeId = Long.parseLong(app.getStoreId());
        OfflineStore store = offlineStoreRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("關聯的門店不存在，ID: " + storeId));

        // 4. 獲取商品與數量
        Product product = app.getProduct();
        if (product == null) {
            return ResponseEntity.badRequest().body(Result.error("該申請未關聯商品，無法入庫"));
        }
        Integer quantity = app.getQuantity() != null ? app.getQuantity() : 0;
        if (quantity <= 0) {
            return ResponseEntity.badRequest().body(Result.error("入庫數量必須大於 0"));
        }

        // 5. 查找或創建門店庫存記錄
        StoreInventory storeInv = storeInventoryRepository.findByStoreAndProduct(store, product)
                .orElseGet(() -> {
                    StoreInventory newInv = new StoreInventory();
                    newInv.setStore(store);
                    newInv.setProduct(product);
                    newInv.setQuantity(0);
                    return newInv;
                });

        Integer previousQty = storeInv.getQuantity();
        Integer newQty = previousQty + quantity;

        // 6. 更新門店庫存
        storeInv.setQuantity(newQty);
        storeInventoryRepository.save(storeInv);

        // 7. 記錄庫存調整日誌
        String operatorUsername = authentication.getName();
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new RuntimeException("操作人不存在"));

        InventoryAdjustmentLog log = new InventoryAdjustmentLog();
        log.setProduct(product);
        log.setOperator(operator);
        log.setWarehouseType(InventoryAdjustmentLog.WarehouseType.OFFLINE);
        log.setStore(store);
        log.setPreviousQuantity(previousQty);
        log.setNewQuantity(newQty);
        log.setChangeQuantity(quantity);
        log.setReason("出售申請買斷入庫 (申請ID: " + app.getApplicationId() + ")");
        // 註：這裡使用 PURCHASE_IN，因為平台買斷本質上是採購入庫。
        // 如果您希望語義更精確，可以在 InventoryAdjustmentLog.AdjustmentType 枚舉中新增 SELL_IN，並將此行改為 SELL_IN。
        log.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.PURCHASE_IN);
        adjustmentLogRepository.save(log);

        // 8. 更新出售申請狀態為已入庫
        app.setIsStockedIn(true);
        sellApplicationRepository.save(app);

        return ResponseEntity.ok(Result.ok("入庫成功，門店庫存已更新！"));
    }


    @Operation(summary = "授權人員手動創建出售申請", description = "管理員、店長、鑑定師等授權人員代為創建出售申請，操作人自動記錄為當前登入者")
    @PostMapping(value = "/api/sell-applications/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @Transactional // 【核心】確保實體保存與財務報表更新在同一事務中，失敗時可整體回滾
    public ResponseEntity<?> createApplicationByAdmin(
            @ModelAttribute @Valid SellApplicationDTO dto,
            @RequestParam String targetUsername, // 從前端 FormData 中獲取目標用戶名
            MultipartHttpServletRequest multipartRequest,
            Authentication authentication) {

        try {
            // 1. 【核心修改】動態權限校驗：檢查當前用戶是否在允許執行鑑定/創建操作的授權角色列表中
            String operatorUsername = authentication.getName();
            User operator = userRepository.findByUsername(operatorUsername)
                    .orElseThrow(() -> new RuntimeException("操作人不存在: " + operatorUsername));

            List<String> authorizedRoles = systemConfigService.getAppraisalAuthorizedRoles();
            if (!authorizedRoles.contains(operator.getRole().name())) {
                return ResponseEntity.status(403).body(Result.error("無權操作，僅限授權人員 (如管理員、鑑定師、店長等)"));
            }

            // 2. 查找目標用戶 (申請人)
            User targetUser = userRepository.findByUsername(targetUsername)
                    .orElseThrow(() -> new RuntimeException("目標用戶不存在: " + targetUsername));

            // 3. 查找商品 (如果前端有選擇)
            Product product = null;
            if (dto.getProductId() != null) {
                product = productRepository.findById(dto.getProductId())
                        .orElseThrow(() -> new RuntimeException("關聯的商品不存在，ID: " + dto.getProductId()));
            }

            // 4. 查找鑑定師 (如果前端有選擇)
            User appraiser = null;
            if (dto.getAppraiserId() != null) {
                appraiser = userRepository.findById(dto.getAppraiserId())
                        .orElseThrow(() -> new RuntimeException("指派的鑑定師不存在，ID: " + dto.getAppraiserId()));
            }

            // 5. 構建實體
            SellApplication application = new SellApplication();
            application.setUser(targetUser);

            if (product != null) {
                application.setProduct(product);
                application.setBrand(product.getBrand());
                application.setSeries(product.getDescription());
            } else {
                application.setBrand(dto.getBrand());
                application.setSeries(dto.getSeries());
            }

            application.setModel(dto.getModel());
            application.setPurchaseYear(dto.getPurchaseYear());
            application.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
            application.setCondition(dto.getCondition());
            application.setFunctionStatus(dto.getFunctionStatus());
            application.setNotes(dto.getNotes());
            application.setTransactionMode(SellApplication.TransactionMode.valueOf(dto.getTransactionMode()));

            // 解析目標狀態
            SellApplication.ApplicationStatus targetStatus = SellApplication.ApplicationStatus.valueOf(dto.getStatus());
            application.setStatus(targetStatus);

            // 【核心修改】：如果狀態為 COMPLETED，設置完成時間
            if (targetStatus == SellApplication.ApplicationStatus.COMPLETED) {
                application.setCompletedAt(LocalDateTime.now());
            } else {
                application.setCompletedAt(null);
            }

            // 處理 BigDecimal 轉換
            BigDecimal finalPrice = null;
            if (dto.getEstimatedPrice() != null && !dto.getEstimatedPrice().trim().isEmpty()) {
                application.setEstimatedPrice(new BigDecimal(dto.getEstimatedPrice()));
            }
            if (dto.getFinalPrice() != null && !dto.getFinalPrice().trim().isEmpty()) {
                finalPrice = new BigDecimal(dto.getFinalPrice());
                application.setFinalPrice(finalPrice);
            }

            application.setAppraisalNotes(dto.getAppraisalNotes());
            application.setAppraiser(appraiser);

            // 設置操作人為當前登入者
            application.setOperator(operator);

            application.setStoreId(dto.getStoreId());
            application.setAppointmentDate(dto.getAppointmentDate());
            application.setAppointmentTimeSlot(dto.getAppointmentTimeSlot());
            application.setIsStockedIn(false); // 新建默認未入庫

            // 6. 處理附件列表 (轉為 JSON)
            if (dto.getAccessories() != null && !dto.getAccessories().isEmpty()) {
                application.setAccessories(objectMapper.writeValueAsString(dto.getAccessories()));
            }

            // 7. 處理圖片上傳
            Map<String, org.springframework.web.multipart.MultipartFile> imageFiles = new HashMap<>();
            if (dto.getImgDial() != null && !dto.getImgDial().isEmpty()) imageFiles.put("dial", dto.getImgDial());
            if (dto.getImgCaseback() != null && !dto.getImgCaseback().isEmpty()) imageFiles.put("caseback", dto.getImgCaseback());
            if (dto.getImgCard() != null && !dto.getImgCard().isEmpty()) imageFiles.put("card", dto.getImgCard());
            if (dto.getImgFlaws() != null && !dto.getImgFlaws().isEmpty()) imageFiles.put("flaws", dto.getImgFlaws());
            if (dto.getImgExtra() != null && !dto.getImgExtra().isEmpty()) imageFiles.put("extra", dto.getImgExtra());

            if (!imageFiles.isEmpty()) {
                String tempId = "admin_create_" + System.currentTimeMillis() + "_" + targetUsername;
                Map<String, String> imagePaths = fileStorageService.uploadMultipleImages(imageFiles, tempId);
                application.setImagePaths(objectMapper.writeValueAsString(imagePaths));
            }

            // 8. 保存到數據庫
            SellApplication saved = sellApplicationRepository.save(application);

            // 9. 【核心財務邏輯】：如果狀態為 COMPLETED 且有關聯門店與最終成交價，更新門店每日報表
            if (targetStatus == SellApplication.ApplicationStatus.COMPLETED &&
                    saved.getStoreId() != null && !saved.getStoreId().trim().isEmpty() &&
                    finalPrice != null) {

                try {
                    Long storeId = Long.parseLong(saved.getStoreId());
                    LocalDate reportDate = saved.getCompletedAt() != null ? saved.getCompletedAt().toLocalDate() : LocalDate.now();

                    StoreDailyReport report = storeDailyReportRepository.findByStore_StoreIdAndReportDate(storeId, reportDate)
                            .orElseGet(() -> {
                                StoreDailyReport newReport = new StoreDailyReport();
                                OfflineStore store = offlineStoreRepository.findById(storeId)
                                        .orElseThrow(() -> new RuntimeException("找不到關聯的門店，ID: " + storeId));
                                newReport.setStore(store);
                                newReport.setReportDate(reportDate);
                                newReport.setStoreAcquisitionCost(BigDecimal.ZERO); // 初始化為 0
                                return newReport;
                            });

                    BigDecimal currentCost = report.getStoreAcquisitionCost() != null ? report.getStoreAcquisitionCost() : BigDecimal.ZERO;
                    // 將最終成交價累加到門店收購成本中
                    report.setStoreAcquisitionCost(currentCost.add(finalPrice));

                    storeDailyReportRepository.save(report);
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ 無效的門店ID格式，無法更新門店每日報表: " + saved.getStoreId());
                } catch (Exception e) {
                    System.err.println("⚠️ 更新門店每日報表失敗: " + e.getMessage());
                    // 若業務要求財務記錄失敗必須回滾整個創建流程，可將下行註解取消：
                    // throw new RuntimeException("更新門店每日報表失敗: " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Result.okWithData("申請創建成功", saved.getApplicationId()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error("欄位值無效 (狀態/數字格式錯誤): " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，創建失敗: " + e.getMessage()));
        }
    }

}
