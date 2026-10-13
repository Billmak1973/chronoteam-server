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
import org.example.website.entity.Product;
import org.example.website.repository.AfterSalesRequestRepository;
import org.example.website.repository.OfflineStoreRepository;
import org.example.website.service.AfterSalesRequestService;
import org.example.website.service.ProductService;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "後台售後管理", description = "管理員查看與處理退貨/換貨申請的相關接口")
public class AdminAfterSalesController {

    private final AfterSalesRequestRepository afterSalesRequestRepository;
    private final OfflineStoreRepository  offlineStoreRepository;
    private final AfterSalesRequestService afterSalesRequestService;
    private final ProductService productService;

    public AdminAfterSalesController(AfterSalesRequestRepository afterSalesRequestRepository, OfflineStoreRepository offlineStoreRepository, AfterSalesRequestService afterSalesRequestService, ProductService productService) {
        this.afterSalesRequestRepository = afterSalesRequestRepository;
        this.offlineStoreRepository = offlineStoreRepository;
        this.afterSalesRequestService = afterSalesRequestService;
        this.productService = productService;
    }

    /**
     * 1. 頁面骨架渲染 (Thymeleaf) - 支援動態多條件篩選 (Specification)
     */
    @Hidden
    @GetMapping("/after-sales")
    public String manageAfterSalesPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            // 用戶篩選參數
            @RequestParam(defaultValue = "all") String filterUserMode, // all 或 specific
            @RequestParam(required = false) String filterUsername,    // 指定用戶名
            // 門店篩選參數
            @RequestParam(required = false) String filterStoreId,     // 門店ID
            Model model) {

        model.addAttribute("activePage", "after-sales");
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterType", type);
        // 傳遞篩選參數回前端，保持選中狀態
        model.addAttribute("filterUserMode", filterUserMode);
        model.addAttribute("filterUsername", filterUsername);
        model.addAttribute("filterStoreId", filterStoreId);

        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, 25, Sort.by(Sort.Direction.DESC, "createdAt"));

        // ==========================================
        // 準備門店列表數據 (用於前端下拉框)
        // ==========================================
        List<String> rawStoreIds = afterSalesRequestRepository.findDistinctStoreIds();
        List<Map<String, Object>> storeList = new ArrayList<>();
        for (String sid : rawStoreIds) {
            try {
                Long id = Long.parseLong(sid);
                offlineStoreRepository.findById(id).ifPresent(store -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", id);
                    map.put("name", store.getName());
                    storeList.add(map);
                });
            } catch (NumberFormatException e) {
                // 忽略無效ID
            }
        }
        model.addAttribute("storeList", storeList);

        // ==========================================
        // 構建動態查詢 Specification
        // ==========================================
        AfterSalesRequest.RequestStatus statusEnum = parseStatus(status);
        AfterSalesRequest.RequestType typeEnum = parseType(type);

        Specification<AfterSalesRequest> spec = Specification.where(null);

        // 1. 狀態篩選
        if (statusEnum != null) {
            final AfterSalesRequest.RequestStatus finalStatus = statusEnum;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), finalStatus));
        }
        // 2. 類型篩選
        if (typeEnum != null) {
            final AfterSalesRequest.RequestType finalType = typeEnum;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("requestType"), finalType));
        }
        // 3. 用戶篩選 (只有當模式為 specific 且用戶名不為空時)
        if ("specific".equals(filterUserMode) && filterUsername != null && !filterUsername.trim().isEmpty()) {
            final String finalUsername = filterUsername.trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("user").get("username"), finalUsername));
        }
        // 4. 門店篩選 (不為空且不為 'all')
        if (filterStoreId != null && !filterStoreId.isEmpty() && !"all".equals(filterStoreId)) {
            final String finalStoreId = filterStoreId;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("storeId"), finalStoreId));
        }

        // 執行查詢
        Page<AfterSalesRequest> requestsPage = afterSalesRequestRepository.findAll(spec, pageable);

        // ==========================================
        // 【核心重構】：數據清洗，將 Entity 轉為 Map，避免前端 Thymeleaf 觸發懶加載報錯
        // ==========================================
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

            // 1. 構建 items 列表 (包含 originalPrice，防止懶加載)
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

            // 2. 計算入庫狀態
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

            // 3. 提取商品明細摘要
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

            // 4. 獲取門店名稱
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

            // 5. 判斷是否可以轉換類型 (狀態為 PENDING 且該商品無其他共存 PENDING 申請)
            boolean canConvertType = false;
            if (req.getStatus() == AfterSalesRequest.RequestStatus.PENDING && !req.getItems().isEmpty()) {
                // 獲取當前申請的第一個商品 ID (通常一個申請針對特定商品)
                Integer currentProductId = req.getItems().get(0).getProduct().getProductId();

                // 查詢該原始訂單下，所有狀態為 PENDING 的售後申請
                List<AfterSalesRequest> pendingRequests = afterSalesRequestRepository
                        .findByOriginalOrder_OrderIdAndStatus(req.getOriginalOrder().getOrderId(), AfterSalesRequest.RequestStatus.PENDING);

                // 統計這些 PENDING 申請中，涉及該特定商品的申請總數量
                long conflictingCount = pendingRequests.stream()
                        .flatMap(r -> r.getItems().stream())
                        .filter(item -> item.getProduct().getProductId().equals(currentProductId))
                        .count();
                // 如果只有當前這 1 個申請涉及該商品，說明沒有衝突，允許轉換類型
                canConvertType = (conflictingCount == 1);
            }
            map.put("canConvertType", canConvertType);

            return map;
        }).collect(Collectors.toList());

        // 使用 PaginationUtils 構建響應
        Map<String, Object> pageResponse = PaginationUtils.buildPageResponse(requestsPage, cleanData);

        // ==========================================
        // 構建專門給前端 JS 使用的 itemsDataMap
        // ==========================================
        Map<Long, List<Map<String, Object>>> itemsDataMap = new HashMap<>();
        for (Map<String, Object> req : cleanData) {
            Long reqId = (Long) req.get("requestId");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsList = (List<Map<String, Object>>) req.get("items");
            itemsDataMap.put(reqId, itemsList);
        }

        // ==========================================
        // 獲取所有品牌並傳遞給前端（用於換貨商品選擇）
        // ==========================================
        List<Product> allProducts = productService.getAllProducts();
        Set<String> allBrands = allProducts.stream()
                .map(Product::getBrand)
                .filter(brand -> brand != null && !brand.trim().isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        model.addAttribute("allBrands", allBrands);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageResponse.get("totalPages"));
        model.addAttribute("totalElements", pageResponse.get("totalElements"));
        model.addAttribute("smartPages", pageResponse.get("smartPages"));
        model.addAttribute("afterSalesList", cleanData);
        model.addAttribute("itemsDataMap", itemsDataMap);

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

    @PutMapping("/api/after-sales/{requestId}/status")
    @ResponseBody
    public ResponseEntity<?> updateAfterSalesStatus(
            @PathVariable Long requestId,
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {

        String newStatusStr = (String) payload.get("newStatus");
        AfterSalesRequest.RequestStatus newStatus = AfterSalesRequest.RequestStatus.valueOf(newStatusStr);

        // 解析舊商品退款金額
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

        // 【新增】解析換貨新商品數據
        @SuppressWarnings("unchecked")
        Map<String, Object> exchangeData = (Map<String, Object>) payload.get("exchangeData");

        try {
            afterSalesRequestService.updateAfterSalesStatus(requestId, newStatus, itemRefundAmounts, exchangeData, authentication.getName());
            return ResponseEntity.ok(Map.of("success", true, "message", "狀態更新成功，訂單狀態及財務報表已同步"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "系統錯誤"));
        }
    }

    /**
     * 轉換售後申請類型 (退貨 <-> 換貨)
     */
    @PostMapping("/api/after-sales/{requestId}/convert-type")
    @ResponseBody
    public ResponseEntity<?> convertAfterSalesType(
            @PathVariable Long requestId,
            Authentication authentication) {
        try {
            AfterSalesRequest request = afterSalesRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("售後申請不存在"));

            if (request.getStatus() != AfterSalesRequest.RequestStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "只有待處理的申請才能轉換類型"));
            }

            // 切換類型
            AfterSalesRequest.RequestType newType =
                    request.getRequestType() == AfterSalesRequest.RequestType.RETURN
                            ? AfterSalesRequest.RequestType.EXCHANGE
                            : AfterSalesRequest.RequestType.RETURN;

            request.setRequestType(newType);
            afterSalesRequestRepository.save(request);

            String typeName = newType == AfterSalesRequest.RequestType.RETURN ? "退貨" : "換貨";
            return ResponseEntity.ok(Map.of("success", true, "message", "已成功轉換為「" + typeName + "」"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * API: 確認售後商品入庫
     */
    @PostMapping("/api/after-sales/item/{requestItemId}/stock-in")
    @ResponseBody
    public ResponseEntity<?> confirmItemStockIn(
            @PathVariable Long requestItemId,
            Authentication authentication) {
        try {
            String operatorUsername = authentication.getName();
            afterSalesRequestService.confirmItemStockIn(requestItemId, operatorUsername);
            return ResponseEntity.ok(Map.of("success", true, "message", "入庫成功，門店庫存已更新！"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "系統錯誤，請稍後重試"));
        }
    }
}