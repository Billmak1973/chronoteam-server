package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.example.website.repository.OrderItemRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
@Tag(name = "用戶訂單管理", description = "用戶查看、刪除、取消及確認收貨等訂單相關操作接口")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;

    // 構造函數注入
    public OrderController(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                           OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
    }

    @Operation(
            summary = "獲取訂單商品明細",
            description = "根據訂單編號獲取該訂單下的商品列表。包含防越權校驗，僅限訂單所屬用戶訪問。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "獲取成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "訂單不存在或無權訪問"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @GetMapping("/{orderNo}/details")
    public ResponseEntity<?> getOrderDetails(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,

            @Parameter(hidden = true) // 隱藏 Authentication，因為它由 Spring Security 自動解析
            Authentication authentication) {
        try {
            String username = authentication.getName();

            // 1. 校驗訂單是否存在且屬於當前用戶（防越權攻擊）
            Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                    .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

            // 2. 獲取訂單商品明細
            List<OrderItem> items = orderItemRepository.findByOrder_OrderNo(orderNo);

            // 3. 關鍵：手動提取需要的數據，避免 Hibernate 關聯導致的 JSON 循環引用 (StackOverflow)
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (OrderItem item : items) {
                Map<String, Object> map = new HashMap<>();
                map.put("quantity", item.getQuantity());
                map.put("price", item.getPrice());

                // 提取 Product 信息 (與前端 JS 渲染的 item.product.xxx 完全對應)
                Map<String, Object> productMap = new HashMap<>();
                productMap.put("id", item.getProduct().getProductId());
                productMap.put("description", item.getProduct().getDescription());
                productMap.put("image", item.getProduct().getImage());
                productMap.put("category", item.getProduct().getCategory());
                map.put("product", productMap);

                resultList.add(map);
            }

            // 4. 返回訂單商品列表
            return ResponseEntity.ok(Result.okWithData("成功", resultList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "刪除訂單",
            description = "刪除指定的訂單記錄。為保障數據安全，僅允許刪除「未付款」或「待線下付款」狀態的訂單。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "刪除成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "業務異常 (如：已付款無法刪除)"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @DeleteMapping("/{orderNo}")
    public ResponseEntity<Result> deleteOrder(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,

            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            String username = authentication.getName();
            // 調用 Service 層執行刪除
            orderService.deleteOrder(orderNo, username);

            // 返回成功響應 (前端依賴 success 和 message 字段)
            return ResponseEntity.ok(Result.ok("訂單已成功刪除"));
        } catch (RuntimeException e) {
            // 捕獲業務異常 (如：訂單不存在、已付款無法刪除等)
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，刪除失敗"));
        }
    }

    @Operation(
            summary = "取消已付款訂單",
            description = "取消已付款的訂單。此操作會觸發退款流程、恢復商品庫存，並更新財務報表。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "取消並退款成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "業務異常 (如：訂單狀態不允許取消)"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/{orderNo}/cancel")
    public ResponseEntity<Result> cancelPaidOrder(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,

            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            String username = authentication.getName();
            // 調用 Service 層的 cancelPaidOrder 方法
            orderService.cancelPaidOrder(orderNo, username);

            return ResponseEntity.ok(Result.ok("訂單已成功取消並退款"));
        } catch (RuntimeException e) {
            // 捕獲業務異常 (如：訂單不存在、狀態不允許取消等)
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，取消訂單失敗"));
        }
    }

    @Operation(
            summary = "隱藏訂單 (軟刪除)",
            description = "將「已取消」或「已退貨」的訂單從用戶前端視圖中隱藏，不進行物理刪除，以便後台審計。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "隱藏成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "業務異常 (如：訂單狀態不允許隱藏)"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @DeleteMapping("/{orderNo}/hide")
    public ResponseEntity<Result> hideOrder(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,

            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            String username = authentication.getName();
            orderService.hideOrder(orderNo, username);
            return ResponseEntity.ok(Result.ok("訂單已隱藏"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，隱藏失敗"));
        }
    }

    @Operation(
            summary = "確認門店取貨",
            description = "用戶或管理員確認已完成門店取貨，訂單狀態將更新為「已完成」，並觸發相關的財務報表記錄。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "確認收貨成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "業務異常 (如：非門店自取訂單或狀態異常)"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/{orderNo}/pickup")
    public ResponseEntity<Result> confirmPickup(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,

            @Parameter(hidden = true)
            Authentication authentication) {

        // 獲取當前登錄用戶名
        String currentUsername = authentication.getName();

        // 調用 Service 層執行取貨邏輯
        orderService.confirmPickup(orderNo, currentUsername);

        return ResponseEntity.ok(Result.ok("確認收貨成功，訂單狀態已更新為已完成！"));
    }
}