package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import org.example.website.dto.Result;
import org.example.website.entity.*;
import org.example.website.repository.InventoryAdjustmentLogRepository;
import org.example.website.repository.PurchaseOrderRepository;
import org.example.website.repository.UserRepository;
import org.example.website.service.ProductService;
import org.example.website.service.PurchaseOrderService;
import org.example.website.util.PaginationUtils;
import org.example.website.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "後台採購管理", description = "採購訂單與庫存調整日誌的查詢及創建接口")
public class AdminPurchaseController {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryAdjustmentLogRepository adjustmentLogRepository;
    private final ProductService productService;

    // 新增的依賴注入
    private final PurchaseOrderService purchaseOrderService;
    private final UserRepository userRepository;

    public AdminPurchaseController(PurchaseOrderRepository purchaseOrderRepository,
                                   InventoryAdjustmentLogRepository adjustmentLogRepository,
                                   ProductService productService,
                                   PurchaseOrderService purchaseOrderService,
                                   UserRepository userRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.adjustmentLogRepository = adjustmentLogRepository;
        this.productService = productService;
        this.purchaseOrderService = purchaseOrderService;
        this.userRepository = userRepository;
    }

    // ==========================================
    // 1. 頁面視圖渲染 (僅返回 HTML 骨架，數據由前端 AJAX 獲取)
    // ==========================================
    @Hidden // 隱藏純頁面渲染接口，保持 Swagger UI 專注於 REST API
    @GetMapping("/purchases")
    public String managePurchasesPage(Model model) {
        // 1.獲取系統中所有產品
        List<Product> allProducts = productService.getAllProducts();

        // 2. 提取所有不重复的品牌名称，并使用 TreeSet 自动按字母排序
        Set<String> allBrands = allProducts.stream()
                .map(Product::getBrand)
                .filter(brand -> brand != null && !brand.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        model.addAttribute("allBrands", allBrands);
        return "admin/admin-purchases";
    }

    @Operation(summary = "獲取採購訂單分頁列表", description = "支持分頁及多條件篩選查詢，返回 1-based 頁碼及智能分頁數據。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "未登入或無權限")
    })
    @GetMapping("/api/admin/purchases/orders")
    @ResponseBody
    @Transactional(readOnly = true) // 【核心修復】：防止 Hibernate 懶加載異常，確保能獲取到 storeName 和 operatorName
    public ResponseEntity<?> getPurchaseOrders(
            @Parameter(description = "當前頁碼 (1-based)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每頁顯示數量", example = "25")
            @RequestParam(defaultValue = "25") int size,

            // 【新增】篩選參數
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String warehouseType,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Boolean isStockedIn,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String createStart,
            @RequestParam(required = false) String createEnd,
            @RequestParam(required = false) String updateStart,
            @RequestParam(required = false) String updateEnd
    ) {
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "purchaseDate"));

        // 轉換枚舉
        PurchaseOrder.PurchaseStatus statusEnum = (status != null && !status.isEmpty()) ? PurchaseOrder.PurchaseStatus.valueOf(status) : null;
        PurchaseOrder.WarehouseType warehouseTypeEnum = (warehouseType != null && !warehouseType.isEmpty()) ? PurchaseOrder.WarehouseType.valueOf(warehouseType) : null;

        // 轉換時間字符串為 LocalDateTime (開始時間為 00:00:00，結束時間為 23:59:59)
        LocalDateTime createStartDt = (createStart != null && !createStart.isEmpty()) ? LocalDate.parse(createStart).atStartOfDay() : null;
        LocalDateTime createEndDt = (createEnd != null && !createEnd.isEmpty()) ? LocalDate.parse(createEnd).atTime(23, 59, 59) : null;
        LocalDateTime updateStartDt = (updateStart != null && !updateStart.isEmpty()) ? LocalDate.parse(updateStart).atStartOfDay() : null;
        LocalDateTime updateEndDt = (updateEnd != null && !updateEnd.isEmpty()) ? LocalDate.parse(updateEnd).atTime(23, 59, 59) : null;

        // 【核心修復】：使用帶條件的查詢方法，替換原來的 findAll
        Page<PurchaseOrder> ordersPage = purchaseOrderRepository.findWithFilters(
                supplier, statusEnum, warehouseTypeEnum, storeId, isStockedIn,
                operator, createStartDt, createEndDt, updateStartDt, updateEndDt, pageable
        );

        // 數據清洗 (將 Entity 轉換為前端友好的 Map)
        var cleanData = ordersPage.getContent().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("purchaseId", order.getPurchaseId());
            map.put("productId", order.getProduct() != null ? order.getProduct().getProductId() : null);
            map.put("productName", order.getProduct() != null ? order.getProduct().getDescription() : "未知商品");
            map.put("brand", order.getProduct() != null ? order.getProduct().getBrand() : "未知品牌");
            map.put("quantity", order.getQuantity());
            map.put("totalCost", order.getTotalCost());
            map.put("supplier", order.getSupplier());
            map.put("warehouseType", order.getWarehouseType() != null ? order.getWarehouseType().name() : "ONLINE");

            // 【需求 1 & 2】：正確獲取並返回名稱
            map.put("storeId", order.getStore() != null ? order.getStore().getStoreId() : null);
            map.put("storeName", order.getStore() != null ? order.getStore().getName() : null);
            map.put("operatorName", order.getOperator() != null ? order.getOperator().getUsername() : "System");

            map.put("inventoryRecordId", order.getInventoryRecordId());
            map.put("isStockedIn", order.getIsStockedIn());
            map.put("purchaseDate", order.getPurchaseDate());
            map.put("status", order.getStatus() != null ? order.getStatus().name() : "PENDING");
            map.put("remark", order.getRemark());
            map.put("createdAt", order.getCreatedAt());
            map.put("updatedAt", order.getUpdatedAt());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = PaginationUtils.buildPageResponse(ordersPage, cleanData);
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    // ==========================================
    // 3. API: 獲取庫存調整日誌分頁列表 (【核心修復版】)
    // ==========================================
    @Operation(summary = "獲取庫存調整日誌分頁列表", description = "支持分頁及多條件篩選查詢，特別支持 'TRANSFER' 同時查詢出庫與入庫。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "未登入或無權限")
    })
    @GetMapping("/api/admin/purchases/logs")
    @ResponseBody
    public ResponseEntity<?> getAdjustmentLogs(
            @Parameter(description = "當前頁碼 (1-based)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每頁顯示數量", example = "25")
            @RequestParam(defaultValue = "25") int size,

            @Parameter(description = "調整類型 (支持 PURCHASE_IN, MANUAL_ADJUST, TRANSFER, DAMAGE, RETURN, SALE_OUT)", example = "TRANSFER")
            @RequestParam(required = false) String type,

            @Parameter(description = "倉庫類型 (ONLINE, OFFLINE)", example = "ONLINE")
            @RequestParam(required = false) String warehouse
    ) {
        // 1. 頁碼轉換
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 2. 解析倉庫類型枚舉
        InventoryAdjustmentLog.WarehouseType warehouseEnum = (warehouse != null && !warehouse.isEmpty())
                ? InventoryAdjustmentLog.WarehouseType.valueOf(warehouse) : null;

        Page<InventoryAdjustmentLog> logsPage;

        // 3. 【核心修復】：動態構建查詢條件，避免對 "TRANSFER" 執行 valueOf 導致崩潰
        if ("TRANSFER".equals(type)) {
            // 如果選了調撥，則同時查詢 OUT 和 IN
            List<InventoryAdjustmentLog.AdjustmentType> transferTypes = Arrays.asList(
                    InventoryAdjustmentLog.AdjustmentType.TRANSFER_OUT,
                    InventoryAdjustmentLog.AdjustmentType.TRANSFER_IN
            );
            if (warehouseEnum != null) {
                logsPage = adjustmentLogRepository.findByAdjustmentTypeInAndWarehouseType(transferTypes, warehouseEnum, pageable);
            } else {
                logsPage = adjustmentLogRepository.findByAdjustmentTypeIn(transferTypes, pageable);
            }
        } else if (type != null && !type.isEmpty()) {
            // 其他單一目錄類型 (如 PURCHASE_IN, DAMAGE 等)
            InventoryAdjustmentLog.AdjustmentType typeEnum = InventoryAdjustmentLog.AdjustmentType.valueOf(type);
            if (warehouseEnum != null) {
                logsPage = adjustmentLogRepository.findByAdjustmentTypeAndWarehouseType(typeEnum, warehouseEnum, pageable);
            } else {
                logsPage = adjustmentLogRepository.findByAdjustmentType(typeEnum, pageable);
            }
        } else {
            // 沒有指定 type，僅按 warehouse 篩選或查詢全部
            if (warehouseEnum != null) {
                logsPage = adjustmentLogRepository.findByWarehouseType(warehouseEnum, pageable);
            } else {
                logsPage = adjustmentLogRepository.findAll(pageable);
            }
        }

        // 4. 數據清洗 (與您原有的邏輯保持一致)
        var cleanData = logsPage.getContent().stream().map(log -> {
            Map<String, Object> map = new HashMap<>();
            map.put("logId", log.getLogId());
            map.put("productName", log.getProduct() != null ? log.getProduct().getDescription() : "未知商品");
            map.put("operatorName", log.getOperator() != null ? log.getOperator().getUsername() : "System");
            map.put("warehouseType", log.getWarehouseType() != null ? log.getWarehouseType().name() : "ONLINE");
            map.put("storeName", log.getStore() != null ? log.getStore().getName() : null);
            map.put("previousQuantity", log.getPreviousQuantity());
            map.put("newQuantity", log.getNewQuantity());
            map.put("changeQuantity", log.getChangeQuantity());
            map.put("reason", log.getReason());
            map.put("adjustmentType", log.getAdjustmentType() != null ? log.getAdjustmentType().name() : "MANUAL_ADJUST");
            map.put("transferBatchId", log.getTransferBatchId());
            map.put("createdAt", log.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        // 5. 構建標準響應
        Map<String, Object> response = PaginationUtils.buildPageResponse(logsPage, cleanData);
        response.put("currentPage", page); // 確保返回 1-based 頁碼

        return ResponseEntity.ok(response);
    }

    // ==========================================
    // 4. API: 新增採購訂單 (從 PurchaseOrderController 合併而來)
    // ==========================================
    @Operation(
            summary = "新增采购订单",
            description = "管理员创建新的采购订单，包括商品、数量、成本等信息"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无权限，仅限管理员")
    })
    @PostMapping("/api/admin/purchases/orders") // 保持與原 PurchaseOrderController 生成的 API 路徑一致
    @ResponseBody
    public ResponseEntity<Result> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderRequest request,
            Authentication authentication
    ) {
        try {
            // 获取当前登录用户
            String operatorUsername = authentication.getName();
            User operator = userRepository.findByUsername(operatorUsername)
                    .orElseThrow(() -> new RuntimeException("操作员不存在"));

            // 调用Service层创建订单
            PurchaseOrder order = purchaseOrderService.createPurchaseOrder(request, operator);

            return ResponseEntity.ok(Result.okWithData("采购订单创建成功",
                    Map.of("purchaseId", order.getPurchaseId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Result.error("创建失败: " + e.getMessage()));
        }
    }

    // ==========================================
    // 5. API: 編輯採購訂單 (PUT)
    // ==========================================
    @Operation(summary = "編輯採購訂單", description = "管理員修改現有的採購訂單信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "修改成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤"),
            @ApiResponse(responseCode = "401", description = "未登錄"),
            @ApiResponse(responseCode = "403", description = "無權限，僅限管理員")
    })
    @PutMapping("/api/admin/purchases/orders/{id}")
    @ResponseBody
    public ResponseEntity<Result> updatePurchaseOrder(
            @Parameter(description = "採購訂單ID", example = "1", required = true) @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderRequest request,
            Authentication authentication
    ) {
        try {
            String operatorUsername = authentication.getName();
            User operator = userRepository.findByUsername(operatorUsername)
                    .orElseThrow(() -> new RuntimeException("操作員不存在"));

            // 調用 Service 層更新訂單
            PurchaseOrder order = purchaseOrderService.updatePurchaseOrder(id, request, operator);

            return ResponseEntity.ok(Result.okWithData("採購訂單修改成功", Map.of("purchaseId", order.getPurchaseId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("修改失敗: " + e.getMessage()));
        }
    }

    // ==========================================
    // 請求 DTO (內部類)
    // ==========================================
    @Data
    public static class PurchaseOrderRequest {
        @Parameter(description = "商品ID", example = "101", required = true)
        private Integer productId;

        @Parameter(description = "采购数量", example = "5", required = true)
        private Integer quantity;

        @Parameter(description = "总成本", example = "150000", required = true)
        private java.math.BigDecimal totalCost;

        @Parameter(description = "供应商", example = "XX拍卖行")
        private String supplier;

        @Parameter(description = "仓库类型", example = "ONLINE", required = true)
        private String warehouseType;

        @Parameter(description = "门店ID（线下仓库时必填）", example = "1")
        private Long storeId;

        @Parameter(description = "进货日期", example = "2024-01-15")
        private LocalDate purchaseDate;

        @Parameter(description = "备注", example = "紧急采购")
        private String remark;

        @Parameter(description = "订单状态 (PENDING, COMPLETED, CANCELLED)", example = "PENDING")
        private String status;
    }

    @Operation(summary = "確認採購訂單入庫", description = "管理員確認商品已實際進入倉庫，系統將自動更新庫存並記錄日誌。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "入庫成功"),
            @ApiResponse(responseCode = "400", description = "訂單狀態不允許入庫或參數錯誤"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @PostMapping("/api/admin/purchases/{id}/stock-in")
    @ResponseBody
    public ResponseEntity<Result> confirmStockIn(
            @Parameter(description = "採購訂單ID", required = true) @PathVariable Long id,
            Authentication authentication) {

        // 1. 權限校驗 (可選，視您的安全配置而定)
        if (!SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }

        try {
            // 2. 調用 Service 層處理入庫邏輯
            purchaseOrderService.processStockIn(id, authentication.getName());
            return ResponseEntity.ok(Result.ok("入庫成功，庫存已更新"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，入庫失敗"));
        }
    }

    // ==========================================
    // 4. API: 獲取篩選選項 (已修復：路徑、操作員列表、門店數據結構)
    // ==========================================
    @Operation(summary = "獲取篩選選項")
    @GetMapping("/api/admin/purchases/filter-options")
    public ResponseEntity<Result> getFilterOptions() {
        Map<String, Object> options = new HashMap<>();

        // 1. 倉庫類型選項
        List<Map<String, String>> warehouseTypes = new ArrayList<>();
        for (PurchaseOrder.WarehouseType type : PurchaseOrder.WarehouseType.values()) {
            Map<String, String> option = new HashMap<>();
            option.put("value", type.name());
            option.put("label", type == PurchaseOrder.WarehouseType.ONLINE ? "線上總倉" : "線下門店");
            warehouseTypes.add(option);
        }
        options.put("warehouseTypes", warehouseTypes);

        // 2. 狀態選項
        List<Map<String, String>> statuses = new ArrayList<>();
        for (PurchaseOrder.PurchaseStatus status : PurchaseOrder.PurchaseStatus.values()) {
            Map<String, String> option = new HashMap<>();
            option.put("value", status.name());
            String label = switch (status) {
                case PENDING -> "待入庫";
                case COMPLETED -> "已完成";
                case CANCELLED -> "已取消";
            };
            option.put("label", label);
            statuses.add(option);
        }
        options.put("statuses", statuses);

        // 3. 供應商列表
        List<String> suppliers = purchaseOrderRepository.findDistinctSuppliers();
        options.put("suppliers", suppliers != null ? suppliers : new ArrayList<>());

        // 4. 【修復 2】操作員列表 (前端 JS 需要 data.operators)
        List<String> operators = purchaseOrderRepository.findDistinctOperatorUsernames();
        options.put("operators", operators != null ? operators : new ArrayList<>());

        // 5. 【修復 3】門店列表 (前端 JS 期望讀取 store.storeId 和 store.name)
        List<OfflineStore> stores = purchaseOrderRepository.findDistinctStoresInPurchases();
        List<Map<String, Object>> storeOptions = stores.stream().map(store -> {
            Map<String, Object> storeMap = new HashMap<>();
            storeMap.put("storeId", store.getStoreId());
            String name = store.getName() + " (" + store.getStoreCode() + ")";
            if (!Boolean.TRUE.equals(store.getIsActive())) {
                name += " [已停用]";
            }
            storeMap.put("name", name);
            return storeMap;
        }).collect(Collectors.toList());
        options.put("stores", storeOptions);

        return ResponseEntity.ok(Result.okWithData("獲取成功", options));
    }

    @Operation(summary = "搜索供應商（自動完成）", description = "根據關鍵字搜索供應商，使用 LIKE 查詢")
    @GetMapping("/api/admin/purchases/suppliers/search")
    @ResponseBody
    public ResponseEntity<?> searchSuppliers(
            @Parameter(description = "搜索關鍵字", required = true)
            @RequestParam String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(Result.okWithData("成功", new ArrayList<>()));
        }

        List<String> suppliers = purchaseOrderRepository.findSuppliersByKeyword(keyword.trim());
        return ResponseEntity.ok(Result.okWithData("成功", suppliers));
    }
}