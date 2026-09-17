package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.entity.InventoryAdjustmentLog;
import org.example.website.entity.PurchaseOrder;
import org.example.website.repository.InventoryAdjustmentLogRepository;
import org.example.website.repository.PurchaseOrderRepository;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "後台採購管理", description = "採購訂單與庫存調整日誌的查詢接口")
public class AdminPurchaseController {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryAdjustmentLogRepository adjustmentLogRepository;

    public AdminPurchaseController(PurchaseOrderRepository purchaseOrderRepository,
                                   InventoryAdjustmentLogRepository adjustmentLogRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.adjustmentLogRepository = adjustmentLogRepository;
    }

    // ==========================================
    // 1. 頁面視圖渲染 (僅返回 HTML 骨架，數據由前端 AJAX 獲取)
    // ==========================================
    @Hidden // 隱藏純頁面渲染接口，保持 Swagger UI 專注於 REST API
    @GetMapping("/purchases")
    public String managePurchasesPage(Model model) {
        return "admin/admin-purchases";
    }

    // ==========================================
    // 2. API: 獲取採購訂單分頁列表
    // ==========================================
    @Operation(summary = "獲取採購訂單分頁列表", description = "支持分頁查詢，返回 1-based 頁碼及智能分頁數據。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "未登入或無權限")
    })
    @GetMapping("/api/admin/purchases/orders")
    @ResponseBody
    public ResponseEntity<?> getPurchaseOrders(
            @Parameter(description = "當前頁碼 (1-based)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每頁顯示數量", example = "25") // 【已修改】示例值改為 25
            @RequestParam(defaultValue = "25") int size) {          // 【已修改】默認值改為 25

        // 1. 將 1-based 頁碼轉換為 0-based 供 Spring Data JPA 使用
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "purchaseDate"));

        // 2. 執行分頁查詢
        Page<PurchaseOrder> ordersPage = purchaseOrderRepository.findAll(pageable);

        // 3. 數據清洗 (將 Entity 轉換為前端友好的 Map，避免 Hibernate 懶加載或循環引用問題)
        var cleanData = ordersPage.getContent().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("purchaseId", order.getPurchaseId());
            map.put("productName", order.getProduct() != null ? order.getProduct().getDescription() : "未知商品");
            map.put("brand", order.getProduct() != null ? order.getProduct().getBrand() : "未知品牌");
            map.put("quantity", order.getQuantity());
            map.put("totalCost", order.getTotalCost());
            map.put("supplier", order.getSupplier());
            map.put("warehouseType", order.getWarehouseType() != null ? order.getWarehouseType().name() : "ONLINE");
            map.put("storeName", order.getStore() != null ? order.getStore().getName() : null);
            map.put("purchaseDate", order.getPurchaseDate());
            map.put("operatorName", order.getOperator() != null ? order.getOperator().getUsername() : "System");
            map.put("status", order.getStatus() != null ? order.getStatus().name() : "PENDING");
            return map;
        }).collect(Collectors.toList());

        // 4. 使用 PaginationUtils 構建標準響應 (自動處理 smartPages 等)
        Map<String, Object> response = PaginationUtils.buildPageResponse(ordersPage, cleanData);

        // 5. 【關鍵修正】：覆蓋 currentPage 為 1-based，以便前端直接使用
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    // ==========================================
    // 3. API: 獲取庫存調整日誌分頁列表
    // ==========================================
    @Operation(summary = "獲取庫存調整日誌分頁列表", description = "支持分頁查詢，返回 1-based 頁碼及智能分頁數據。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "未登入或無權限")
    })
    @GetMapping("/api/admin/purchases/logs")
    @ResponseBody
    public ResponseEntity<?> getAdjustmentLogs(
            @Parameter(description = "當前頁碼 (1-based)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每頁顯示數量", example = "25") // 【已修改】示例值改為 25
            @RequestParam(defaultValue = "25") int size) {          // 【已修改】默認值改為 25

        // 1. 頁碼轉換
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 2. 執行分頁查詢
        Page<InventoryAdjustmentLog> logsPage = adjustmentLogRepository.findAll(pageable);

        // 3. 數據清洗
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

        // 4. 使用 PaginationUtils 構建標準響應
        Map<String, Object> response = PaginationUtils.buildPageResponse(logsPage, cleanData);

        // 5. 覆蓋 currentPage 為 1-based
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }
}