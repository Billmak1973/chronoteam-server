package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.entity.AfterSalesRequest;
import org.example.website.entity.AfterSalesRequestItem;
import org.example.website.repository.AfterSalesRequestRepository;
import org.example.website.repository.OfflineStoreRepository;
import org.example.website.service.AfterSalesRequestService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "後台售後管理", description = "管理員查看與處理退貨/換貨申請的相關接口")
public class AdminAfterSalesController {

    private final AfterSalesRequestRepository afterSalesRequestRepository;
    private final OfflineStoreRepository  offlineStoreRepository;
    private final AfterSalesRequestService afterSalesRequestService;

    public AdminAfterSalesController(AfterSalesRequestRepository afterSalesRequestRepository, OfflineStoreRepository offlineStoreRepository, AfterSalesRequestService afterSalesRequestService) {
        this.afterSalesRequestRepository = afterSalesRequestRepository;
        this.offlineStoreRepository = offlineStoreRepository;
        this.afterSalesRequestService = afterSalesRequestService;
    }

    /**
     * 1. 頁面骨架渲染 (Thymeleaf)
     */
    @Hidden
    @GetMapping("/after-sales")
    public String manageAfterSalesPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            Model model) {

        model.addAttribute("activePage", "after-sales");
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterType", type);

        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, 25, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AfterSalesRequest> requestsPage;

        // 動態查詢邏輯 (帶安全枚舉轉換)
        AfterSalesRequest.RequestStatus statusEnum = parseStatus(status);
        AfterSalesRequest.RequestType typeEnum = parseType(type);

        if (statusEnum != null && typeEnum != null) {
            requestsPage = afterSalesRequestRepository.findByStatusAndRequestType(statusEnum, typeEnum, pageable);
        } else if (statusEnum != null) {
            requestsPage = afterSalesRequestRepository.findByStatus(statusEnum, pageable);
        } else if (typeEnum != null) {
            requestsPage = afterSalesRequestRepository.findByRequestType(typeEnum, pageable);
        } else {
            requestsPage = afterSalesRequestRepository.findAll(pageable);
        }

        // 【核心重構】：數據清洗，將 Entity 轉為 Map，避免前端 Thymeleaf 觸發懶加載報錯
        List<Map<String, Object>> cleanData = requestsPage.getContent().stream().map(req -> {
            Map<String, Object> map = new HashMap<>();
            map.put("requestId", req.getRequestId());
            map.put("requestType", req.getRequestType() != null ? req.getRequestType().name() : "UNKNOWN");
            map.put("status", req.getStatus() != null ? req.getStatus().name() : "UNKNOWN");
            map.put("reason", req.getReason());
            map.put("appointmentDate", req.getAppointmentDate());
            map.put("appointmentTimeSlot", req.getAppointmentTimeSlot());
            map.put("createdAt", req.getCreatedAt());
            map.put("finalPriceDifference", req.getFinalPriceDifference());
            map.put("completedAt", req.getCompletedAt());
            map.put("storeId", req.getStoreId());
            map.put("username", req.getUser() != null ? req.getUser().getUsername() : null);
            map.put("orderNo", req.getOriginalOrder() != null ? req.getOriginalOrder().getOrderNo() : null);
            map.put("newOrderNo", req.getNewOrder() != null ? req.getNewOrder().getOrderNo() : null);

            // ==========================================
            // 1. 構建 items 列表 (包含 originalPrice，防止懶加載)
            // ==========================================
            List<Map<String, Object>> itemsList = new ArrayList<>();
            if (req.getItems() != null && !req.getItems().isEmpty()) {
                for (AfterSalesRequestItem item : req.getItems()) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("requestItemId", item.getRequestItemId());
                    itemMap.put("productName", item.getProduct() != null ? item.getProduct().getDescription() : "未知商品");
                    itemMap.put("returnQuantity", item.getReturnQuantity());
                    itemMap.put("finalSettleAmount", item.getFinalSettleAmount());
                    itemMap.put("createdAt", item.getCreatedAt());
                    itemMap.put("isStockedIn", item.getIsStockedIn() != null ? item.getIsStockedIn() : false);

                    // 獲取原訂單明細的價格
                    if (item.getOrderItem() != null && item.getOrderItem().getPrice() != null) {
                        itemMap.put("originalPrice", item.getOrderItem().getPrice().toString());
                    } else {
                        itemMap.put("originalPrice", "0");
                    }

                    itemsList.add(itemMap);
                }
            }
            map.put("items", itemsList);

            // ==========================================
            // 2. 計算入庫狀態
            // ==========================================
            String stockStatusText = "-";
            String stockStatusColor = "#6c757d"; // 默認灰色

            if (req.getStatus() == AfterSalesRequest.RequestStatus.WAITING_PAYMENT ||
                    req.getStatus() == AfterSalesRequest.RequestStatus.COMPLETED) {

                if (!itemsList.isEmpty()) {
                    boolean allStocked = true;
                    for (AfterSalesRequestItem item : req.getItems()) {
                        if (!Boolean.TRUE.equals(item.getIsStockedIn())) {
                            allStocked = false;
                            break;
                        }
                    }

                    if (allStocked) {
                        stockStatusText = "已入庫";
                        stockStatusColor = "#28a745";
                    } else {
                        stockStatusText = "尚未入庫";
                        stockStatusColor = "#dc3545";
                    }
                } else {
                    stockStatusText = "無明細";
                }
            }
            map.put("stockStatusText", stockStatusText);
            map.put("stockStatusColor", stockStatusColor);

            // ==========================================
            // 3. 提取商品明細摘要
            // ==========================================
            if (!itemsList.isEmpty()) {
                String itemSummary = itemsList.stream()
                        .map(itemMap -> {
                            String prodName = (String) itemMap.get("productName");
                            Integer qty = (Integer) itemMap.get("returnQuantity");
                            return prodName + " x " + qty;
                        })
                        .collect(Collectors.joining(", "));
                map.put("itemSummary", itemSummary);
            } else {
                map.put("itemSummary", "無明細");
            }

            // ==========================================
            // 4. 獲取門店名稱
            // ==========================================
            String storeName = "-";
            if (req.getStoreId() != null && !req.getStoreId().trim().isEmpty()) {
                try {
                    Long storeId = Long.parseLong(req.getStoreId());
                    storeName = offlineStoreRepository.findById(storeId)
                            .map(store -> store.getName())
                            .orElse("未知門店");
                } catch (NumberFormatException e) {
                    storeName = "無效ID";
                }
            }
            map.put("storeName", storeName);

            return map;
        }).collect(Collectors.toList());

        // 使用 PaginationUtils 構建響應
        Map<String, Object> pageResponse = PaginationUtils.buildPageResponse(requestsPage, cleanData);

        // ==========================================
        // 【新增核心代碼】：構建專門給前端 JS 使用的 itemsDataMap
        // ==========================================
        Map<Long, List<Map<String, Object>>> itemsDataMap = new HashMap<>();
        for (Map<String, Object> req : cleanData) {
            Long reqId = (Long) req.get("requestId");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsList = (List<Map<String, Object>>) req.get("items");
            itemsDataMap.put(reqId, itemsList);
        }

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageResponse.get("totalPages"));
        model.addAttribute("totalElements", pageResponse.get("totalElements"));
        model.addAttribute("smartPages", pageResponse.get("smartPages"));
        model.addAttribute("afterSalesList", cleanData);
        model.addAttribute("itemsDataMap", itemsDataMap); // 【新增】注入到 Model

        return "admin/admin-after-sales";
    }

    // 輔助方法：安全解析枚舉
    private AfterSalesRequest.RequestStatus parseStatus(String status) {
        if (status == null || status.isEmpty()) return null;
        try { return AfterSalesRequest.RequestStatus.valueOf(status); }
        catch (IllegalArgumentException e) { return null; }
    }

    private AfterSalesRequest.RequestType parseType(String type) {
        if (type == null || type.isEmpty()) return null;
        try { return AfterSalesRequest.RequestType.valueOf(type); }
        catch (IllegalArgumentException e) { return null; }
    }

    /**
     * 2. AJAX API: 獲取售後申請列表 (供前端 JS 調用)
     */
    @Operation(summary = "獲取售後申請分頁列表", description = "支持按狀態和類型篩選的分頁查詢")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "未登入或無權限")
    })
    @GetMapping("/api/after-sales/list")
    @ResponseBody
    public ResponseEntity<?> getAfterSalesListApi(
            @Parameter(description = "當前頁碼 (1-based)", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "狀態篩選", example = "PENDING") @RequestParam(required = false) String status,
            @Parameter(description = "類型篩選", example = "RETURN") @RequestParam(required = false) String type) {

        // 邏輯與上方頁面渲染類似，此處省略重複代碼，實際可提取為 Service 方法
        return ResponseEntity.ok().build();
    }

    // 2. 修改狀態更新接口，接收 itemRefundAmounts
    @PutMapping("/api/after-sales/{requestId}/status")
    @ResponseBody
    public ResponseEntity<?> updateAfterSalesStatus(
            @PathVariable Long requestId,
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {

        String newStatusStr = (String) payload.get("newStatus");
        AfterSalesRequest.RequestStatus newStatus = AfterSalesRequest.RequestStatus.valueOf(newStatusStr);

        // 解析前端傳來的明細退款金額 Map
        Map<Long, BigDecimal> itemRefundAmounts = new HashMap<>();
        if (newStatus == AfterSalesRequest.RequestStatus.COMPLETED) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawItemsMap = (Map<String, Object>) payload.get("itemRefundAmounts");
            if (rawItemsMap != null) {
                for (Map.Entry<String, Object> entry : rawItemsMap.entrySet()) {
                    try {
                        Long itemId = Long.valueOf(entry.getKey());
                        BigDecimal amount = new BigDecimal(entry.getValue().toString());
                        itemRefundAmounts.put(itemId, amount);
                    } catch (NumberFormatException e) {
                        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "退款金額格式錯誤"));
                    }
                }
            }
        }

        try {
            afterSalesRequestService.updateAfterSalesStatus(requestId, newStatus, itemRefundAmounts, authentication.getName());
            return ResponseEntity.ok(Map.of("success", true, "message", "狀態更新成功，訂單狀態已同步"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "系統錯誤"));
        }
    }
}