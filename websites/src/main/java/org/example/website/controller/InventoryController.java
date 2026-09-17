package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import org.example.website.dto.Result;
import org.example.website.dto.StoreTransferRequest;
import org.example.website.service.InventoryManagementService;
import org.example.website.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/inventory")
@Tag(name = "後台庫存管理", description = "庫存調撥、調整等相關接口")
public class InventoryController {

    private final InventoryManagementService inventoryManagementService;

    public InventoryController(InventoryManagementService inventoryManagementService) {
        this.inventoryManagementService = inventoryManagementService;
    }

    @Operation(summary = "線上總倉轉移至線下門店", description = "將指定數量的商品從線上總倉調撥至指定的線下門店。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "調撥成功"),
            @ApiResponse(responseCode = "400", description = "庫存不足或參數錯誤"),
            @ApiResponse(responseCode = "403", description = "無權限，僅限管理員")
    })
    @PostMapping("/transfer-to-store")
    public ResponseEntity<Result> transferToStore(
            @RequestBody TransferRequest request,
            Authentication authentication) {

        if (!SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }

        String operatorUsername = authentication.getName();

        try {
            inventoryManagementService.transferToStore(
                    request.getProductId(),
                    request.getStoreId(),
                    request.getQuantity(),
                    request.getReason(),
                    operatorUsername
            );
            return ResponseEntity.ok(Result.ok("成功將 " + request.getQuantity() + " 件商品轉移至門店"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @Data
    public static class TransferRequest {
        private Integer productId;
        private Long storeId;
        private Integer quantity;
        private String reason;
    }

    @Operation(summary = "門店庫存調撥轉移", description = "將指定門店的庫存調撥至其他門店或線上總倉，並生成對應的調撥出入庫日誌。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "調撥成功"),
            @ApiResponse(responseCode = "400", description = "庫存不足或參數錯誤"),
            @ApiResponse(responseCode = "403", description = "無權限，僅限管理員")
    })
    @PostMapping("/transfer-from-store")
    public ResponseEntity<Result> transferFromStore(
            @Valid @RequestBody StoreTransferRequest request,
            Authentication authentication) {

        if (!SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }

        String operatorUsername = authentication.getName();
        try {
            inventoryManagementService.transferFromStore(
                    request.getInventoryId(),
                    request.getProductId(),
                    request.getSourceStoreId(),
                    request.getTargetType(),
                    request.getTargetStoreId(),
                    request.getQuantity(),
                    request.getReason(),
                    operatorUsername
            );
            String targetDesc = "ONLINE".equals(request.getTargetType()) ? "線上總倉" : "目標門店";
            return ResponseEntity.ok(Result.ok("成功將 " + request.getQuantity() + " 件商品從門店調撥至 " + targetDesc));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}