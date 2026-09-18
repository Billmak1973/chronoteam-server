package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.example.website.dto.Result;
import org.example.website.entity.PurchaseOrder;
import org.example.website.entity.User;
import org.example.website.repository.PurchaseOrderRepository;
import org.example.website.repository.UserRepository;
import org.example.website.service.PurchaseOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/purchases")
@Tag(name = "采购管理", description = "采购订单相关接口")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final UserRepository userRepository;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService,
                                   UserRepository userRepository) {
        this.purchaseOrderService = purchaseOrderService;
        this.userRepository = userRepository;
    }

    @Operation(
            summary = "新增采购订单",
            description = "管理员创建新的采购订单，包括商品、数量、成本等信息"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功",
                    content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无权限，仅限管理员")
    })
    @PostMapping("/orders")
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

    // 请求DTO
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
    }
}