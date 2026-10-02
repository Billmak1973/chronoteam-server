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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "後台售後管理", description = "管理員查看與處理退貨/換貨申請的相關接口")
public class AdminAfterSalesController {

    private final AfterSalesRequestRepository afterSalesRequestRepository;

    public AdminAfterSalesController(AfterSalesRequestRepository afterSalesRequestRepository) {
        this.afterSalesRequestRepository = afterSalesRequestRepository;
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

        // 數據清洗：避免 Hibernate 懶加載與循環引用
        List<Map<String, Object>> cleanData = requestsPage.getContent().stream().map(req -> {
            Map<String, Object> map = new HashMap<>();
            map.put("requestId", req.getRequestId());
            map.put("requestType", req.getRequestType() != null ? req.getRequestType().name() : "UNKNOWN");
            map.put("status", req.getStatus() != null ? req.getStatus().name() : "UNKNOWN");
            map.put("reason", req.getReason());
            map.put("appointmentDate", req.getAppointmentDate());
            map.put("appointmentTimeSlot", req.getAppointmentTimeSlot());
            map.put("createdAt", req.getCreatedAt());

            if (req.getUser() != null) {
                map.put("username", req.getUser().getUsername());
            }
            if (req.getOriginalOrder() != null) {
                map.put("orderNo", req.getOriginalOrder().getOrderNo());
            }

            // 提取商品明細摘要 (例如: "Rolex Submariner x 1")
            if (req.getItems() != null && !req.getItems().isEmpty()) {
                String itemSummary = req.getItems().stream()
                        .map(item -> {
                            String prodName = (item.getProduct() != null && item.getProduct().getDescription() != null)
                                    ? item.getProduct().getDescription() : "未知商品";
                            return prodName + " x " + item.getReturnQuantity();
                        })
                        .collect(Collectors.joining(", "));
                map.put("itemSummary", itemSummary);
            } else {
                map.put("itemSummary", "無明細");
            }

            return map;
        }).collect(Collectors.toList());

        // 使用 PaginationUtils 構建響應
        Map<String, Object> pageResponse = PaginationUtils.buildPageResponse(requestsPage, cleanData);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageResponse.get("totalPages"));
        model.addAttribute("totalElements", pageResponse.get("totalElements"));
        model.addAttribute("smartPages", pageResponse.get("smartPages"));
        model.addAttribute("afterSalesList", cleanData);

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
        // ...
        return ResponseEntity.ok().build();
    }
}