package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.website.dto.Result;
import org.example.website.dto.ReturnRequestDTO;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.AfterSalesRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Tag(name = "售後管理", description = "退貨與換貨申請相關接口")
public class AfterSalesController {

    private final AfterSalesRequestService afterSalesRequestService;
    private final OrderRepository orderRepository;
    private final AfterSalesRequestRepository afterSalesRequestRepository;
    private final OfflineStoreRepository offlineStoreRepository;
    // 【修改處】添加 final 關鍵字，讓 @RequiredArgsConstructor 自動注入
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    /**
     * 提交售後申請 (通用：支持退貨/換貨)
     */
    @Operation(summary = "提交售後申請", description = "用戶選擇商品、數量及預約時間後提交退貨或換貨申請")
    @PostMapping("/after-sales-request")
    @Transactional // 確保通知和申請在同一事務中保存
    public ResponseEntity<?> submitAfterSalesRequest(
            @RequestBody ReturnRequestDTO dto,
            Authentication authentication) {

        String username = authentication.getName();
        try {
            // 1. 動態解析枚舉 (默認為 RETURN，若前端傳 EXCHANGE 則為 EXCHANGE)
            AfterSalesRequest.RequestType reqType = AfterSalesRequest.RequestType.valueOf(
                    dto.getRequestType() != null ? dto.getRequestType() : "RETURN"
            );

            // ==========================================
            // 【核心新增】數量校驗邏輯
            // ==========================================
            Order order = orderRepository.findByOrderNoAndUser_Username(dto.getOrderNo(), username)
                    .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

            OrderItem orderItem = order.getItems().stream()
                    .filter(item -> item.getProduct().getProductId().equals(dto.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("訂單中找不到該商品"));

            int originalQuantity = orderItem.getQuantity();

            List<AfterSalesRequestItem> existingPendingItems = afterSalesRequestRepository
                    .findAllByOrderNoAndProductIdAndStatus(
                            dto.getOrderNo(),
                            dto.getProductId(),
                            AfterSalesRequest.RequestStatus.PENDING
                    );

            int totalPendingQuantity = existingPendingItems.stream()
                    .mapToInt(AfterSalesRequestItem::getReturnQuantity)
                    .sum();

            if (totalPendingQuantity + dto.getQuantity() > originalQuantity) {
                return ResponseEntity.badRequest().body(Result.error(
                        String.format("申請失敗：該商品剩餘可申請數量為 %d (原始 %d - 已申請 %d)，您申請了 %d",
                                originalQuantity - totalPendingQuantity,
                                originalQuantity,
                                totalPendingQuantity,
                                dto.getQuantity())
                ));
            }
            // ==========================================

            // 4. 調用 Service 層處理業務邏輯並保存數據庫
            AfterSalesRequest request = afterSalesRequestService.createAfterSalesRequest(username, dto, reqType);

            // ==========================================
            // 【新增】發送系統通知
            // ==========================================
            User currentUser = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            Notification notification = new Notification();
            notification.setRecipient(currentUser);
            notification.setSender(null); // null 代表系統自動發送
            notification.setType(Notification.NotificationType.SYSTEM);

            String actionText = (reqType == AfterSalesRequest.RequestType.EXCHANGE) ? "換貨" : "退貨";
            notification.setTitle(String.format("✅ %s申請已成功提交", actionText));
            notification.setContent(String.format(
                    "您的%s申請已成功提交。\n訂單編號：%s\n預約店鋪 ID：%s\n預約時間：%s %s\n請按時前往店鋪處理。",
                    actionText, dto.getOrderNo(), dto.getStoreId(), dto.getReturnDate(), dto.getReturnTimeSlot()
            ));
            notification.setTargetUrl("/account/orders"); // 引導用戶回到訂單列表或售後詳情頁
            notification.setRead(false);

            notificationRepository.save(notification);
            // ==========================================

            String successMsg = (reqType == AfterSalesRequest.RequestType.EXCHANGE)
                    ? "換貨申請已提交，系統已發送通知，請按時前往店鋪處理"
                    : "退貨申請已提交，系統已發送通知，請按時前往店鋪處理";

            return ResponseEntity.ok(Result.okWithData(successMsg, request.getRequestId()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error("無效的申請類型，必須為 RETURN 或 EXCHANGE"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，申請失敗"));
        }
    }




    /**
     * 獲取訂單現有的待處理售後申請 (通用：支持退貨/換貨 + 雙向借用)
     * 【核心合併】：同時返回同訂單同商品的另一種申請類型數量，並在無同類型記錄時，自動返回另一類型的預約信息供前端借用。
     */
    @Operation(summary = "獲取現有售後申請", description = "根據訂單號和申請類型獲取記錄。支持獲取同商品的另一種類型數量，並在無同類型記錄時自動返回另一類型的預約信息供借用。")
    @GetMapping("/after-sales-request")
    @ResponseBody
    public ResponseEntity<?> getExistingAfterSalesRequest(
            @RequestParam String orderNo,
            @RequestParam String type, // 接收 "RETURN" 或 "EXCHANGE"
            @RequestParam Integer productId, // 【保留代碼1】商品ID，用於精確匹配該商品的申請數量
            Authentication authentication) {

        String username = authentication.getName();
        // 校驗訂單權限
        orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

        // 動態解析枚舉
        AfterSalesRequest.RequestType reqType = AfterSalesRequest.RequestType.valueOf(type);
        AfterSalesRequest.RequestType otherType = (reqType == AfterSalesRequest.RequestType.RETURN)
                ? AfterSalesRequest.RequestType.EXCHANGE
                : AfterSalesRequest.RequestType.RETURN;

        // 1. 優先查找當前類型的申請
        AfterSalesRequest currentRequest = afterSalesRequestRepository
                .findByOriginalOrder_OrderNoAndRequestTypeAndStatus(
                        orderNo, reqType, AfterSalesRequest.RequestStatus.PENDING)
                .orElse(null);

        // 2. 查找另一種類型的申請 (用於獲取另一種申請的數量，或作為借用信息)
        AfterSalesRequest otherRequest = afterSalesRequestRepository
                .findByOriginalOrder_OrderNoAndRequestTypeAndStatus(
                        orderNo, otherType, AfterSalesRequest.RequestStatus.PENDING)
                .orElse(null);

        // 3. 構建返回數據
        Map<String, Object> data = new HashMap<>();

        if (currentRequest != null) {
            // 情況 A：存在同類型的申請，正常返回完整信息
            data.put("requestId", currentRequest.getRequestId());
            data.put("storeId", currentRequest.getStoreId());
            data.put("appointmentDate", currentRequest.getAppointmentDate());
            data.put("appointmentTimeSlot", currentRequest.getAppointmentTimeSlot());
            data.put("reason", currentRequest.getReason());

            // 【保留代碼1】提取當前申請中該特定商品的數量
            AfterSalesRequestItem currentItem = currentRequest.getItems().stream()
                    .filter(item -> item.getProduct().getProductId().equals(productId))
                    .findFirst()
                    .orElse(null);
            if (currentItem != null) {
                data.put("currentQuantity", currentItem.getReturnQuantity());
            }

            // 【保留代碼1】提取當前申請的所有商品明細 (供前端展示或處理)
            List<Map<String, Object>> items = currentRequest.getItems().stream().map(item -> {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("productId", item.getProduct().getProductId());
                itemMap.put("returnQuantity", item.getReturnQuantity());
                return itemMap;
            }).collect(Collectors.toList());
            data.put("items", items);

        }
        else if (otherRequest != null) {
            // ==========================================
            // 【核心修改】：檢查預約時間是否已過期
            // ==========================================
            boolean isTimeValid = true; // 默認有效

            if (otherRequest.getAppointmentDate() != null) {
                LocalDate appointmentDate = otherRequest.getAppointmentDate();
                String timeSlotStr = otherRequest.getAppointmentTimeSlot(); // 例如 "12:00" 或 "10:00 - 12:00"

                LocalDateTime appointmentDateTime;

                // 嘗試解析具體時間
                if (timeSlotStr != null && !timeSlotStr.isEmpty()) {
                    try {
                        // 假設時間格式是 "HH:mm" 或者 "HH:mm - HH:mm"，我們取開始時間來對比
                        String startTimeStr = timeSlotStr.split(" - ")[0].trim();
                        LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
                        appointmentDateTime = LocalDateTime.of(appointmentDate, startTime);
                    } catch (Exception e) {
                        // 如果解析失敗（例如格式不對），保守起見，只對比日期（視為當天有效）
                        appointmentDateTime = appointmentDate.atStartOfDay();
                    }
                } else {
                    // 如果沒有具體時間，只對比日期
                    appointmentDateTime = appointmentDate.atStartOfDay();
                }

                // 【核心判斷】：如果當前時間已經晚於預約時間，則視為過期，不借用
                if (LocalDateTime.now().isAfter(appointmentDateTime)) {
                    isTimeValid = false;
                    System.out.println("⚠️ 另一種類型的申請預約時間已過期 (" + appointmentDateTime + ")，取消自動借用。");
                }
            }

            // 只有當時間有效時，才返回借用信息
            if (isTimeValid) {
                data.put("borrowedInfo", Map.of(
                        "storeId", otherRequest.getStoreId(),
                        "appointmentDate", otherRequest.getAppointmentDate(),
                        "appointmentTimeSlot", otherRequest.getAppointmentTimeSlot()
                ));
            }
        }

        // 【核心新增】如果存在另一種類型的申請，無論當前類型是否存在，都返回該商品在另一種申請中的數量，以便前端計算剩餘可申請數量
        if (otherRequest != null) {
            AfterSalesRequestItem otherItem = otherRequest.getItems().stream()
                    .filter(item -> item.getProduct().getProductId().equals(productId))
                    .findFirst()
                    .orElse(null);
            if (otherItem != null) {
                data.put("otherTypeQuantity", otherItem.getReturnQuantity());
                data.put("otherRequestType", otherType.name());
            }
        }

        // 如果兩種類型都沒有找到申請，則返回無現有申請
        if (currentRequest == null && otherRequest == null) {
            return ResponseEntity.ok(Result.okWithData("無現有申請", null));
        }

        return ResponseEntity.ok(Result.okWithData("找到相關申請信息", data));
    }

    @PutMapping("/after-sales-request/{requestId}")
    @ResponseBody
    @Transactional // 【關鍵】：確保能正確訪問 Lazy 加載的關聯實體，並保證通知與更新在同一事務
    public ResponseEntity<?> updateAfterSalesRequest(
            @PathVariable Long requestId,
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {

        String username = authentication.getName();
        AfterSalesRequest request = afterSalesRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("售後申請不存在"));

        if (!request.getUser().getUsername().equals(username)) {
            throw new RuntimeException("無權操作此售後申請");
        }
        if (request.getStatus() != AfterSalesRequest.RequestStatus.PENDING) {
            throw new RuntimeException("該售後申請已處理，無法修改");
        }

        // ==========================================
        // 1. 獲取並校驗 requestType
        // ==========================================
        String requestTypeStr = payload.get("requestType") != null ? payload.get("requestType").toString() : "RETURN";
        AfterSalesRequest.RequestType requestType;
        try {
            requestType = AfterSalesRequest.RequestType.valueOf(requestTypeStr);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("無效的申請類型，必須為 RETURN 或 EXCHANGE");
        }

        if (request.getRequestType() != requestType) {
            throw new RuntimeException("申請類型與原始記錄不匹配，無法更新");
        }

        // ==========================================
        // 2. 數量校驗邏輯 (更新時)
        // ==========================================
        Integer productId = Integer.valueOf(payload.get("productId").toString());
        Integer newQuantity = Integer.valueOf(payload.get("quantity").toString());

        Order order = request.getOriginalOrder();
        OrderItem orderItem = order.getItems().stream()
                .filter(item -> item.getProduct().getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("訂單中找不到該商品"));
        int originalQuantity = orderItem.getQuantity();

        List<AfterSalesRequestItem> allPendingItems = afterSalesRequestRepository
                .findAllByOrderNoAndProductIdAndStatus(
                        order.getOrderNo(),
                        productId,
                        AfterSalesRequest.RequestStatus.PENDING
                );

        int otherPendingQuantity = allPendingItems.stream()
                .filter(item -> !item.getAfterSalesRequest().getRequestId().equals(requestId))
                .mapToInt(AfterSalesRequestItem::getReturnQuantity)
                .sum();

        if (otherPendingQuantity + newQuantity > originalQuantity) {
            return ResponseEntity.badRequest().body(Result.error(
                    String.format("更新失敗：該商品剩餘可申請數量為 %d (原始 %d - 其他已申請 %d)，您申請了 %d",
                            originalQuantity - otherPendingQuantity,
                            originalQuantity,
                            otherPendingQuantity,
                            newQuantity)
            ));
        }

        // ==========================================
        // 3. 更新主表信息
        // ==========================================
        if (payload.get("storeId") != null) {
            request.setStoreId(payload.get("storeId").toString());
        }
        if (payload.get("returnDate") != null) {
            request.setAppointmentDate(LocalDate.parse(payload.get("returnDate").toString()));
        }
        if (payload.get("returnTimeSlot") != null) {
            request.setAppointmentTimeSlot(payload.get("returnTimeSlot").toString());
        }
        if (payload.get("reason") != null) {
            request.setReason(payload.get("reason").toString());
        }

        // ==========================================
        // 4. 處理商品明細
        // ==========================================
        if (productId != null && newQuantity != null) {
            AfterSalesRequestItem existingItem = request.getItems().stream()
                    .filter(item -> item.getProduct().getProductId().equals(productId))
                    .findFirst().orElse(null);

            if (existingItem != null) {
                existingItem.setReturnQuantity(newQuantity);
            } else {
                OrderItem targetOrderItem = request.getOriginalOrder().getItems().stream()
                        .filter(item -> item.getProduct().getProductId().equals(productId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("找不到對應的訂單商品明細，無法追加"));

                AfterSalesRequestItem newItem = new AfterSalesRequestItem();
                newItem.setAfterSalesRequest(request);
                newItem.setOrderItem(targetOrderItem);
                newItem.setProduct(targetOrderItem.getProduct());
                newItem.setReturnQuantity(newQuantity);
                request.getItems().add(newItem);
            }
        }

        // ==========================================
        // 5. 保存更新
        // ==========================================
        afterSalesRequestRepository.save(request);

        // ==========================================
        // 【新增】發送系統通知 (告知用戶信息已更新)
        // ==========================================
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Notification notification = new Notification();
        notification.setRecipient(currentUser);
        notification.setSender(null); // 系統發送
        notification.setType(Notification.NotificationType.SYSTEM);

        String actionText = (requestType == AfterSalesRequest.RequestType.EXCHANGE) ? "換貨" : "退貨";
        notification.setTitle(String.format("🔄 %s申請信息已更新", actionText));
        notification.setContent(String.format(
                "您的%s申請預約信息已成功更新。\n訂單編號：%s\n最新預約時間：%s %s\n請留意店鋪處理進度。",
                actionText,
                order.getOrderNo(),
                request.getAppointmentDate(),
                request.getAppointmentTimeSlot()
        ));
        notification.setTargetUrl("/account/orders");
        notification.setRead(false);

        notificationRepository.save(notification);
        // ==========================================

        return ResponseEntity.ok(Result.ok(actionText + "申請更新成功，系統已發送通知"));
    }

    /**
     * 【新增】快速檢查商品是否已有售后申請 (用於列表頁顯示提示文字)
     */
    @Operation(summary = "檢查售后申請存在性", description = "檢查指定訂單的指定商品是否已有退貨或換貨申請")
    @GetMapping("/after-sales-existence")
    @ResponseBody
    public ResponseEntity<?> checkAfterSalesExistence(
            @RequestParam String orderNo,
            @RequestParam Integer productId,
            Authentication authentication) {

        String username = authentication.getName();
        orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

        // 查詢該訂單下所有 PENDING 狀態的申請
        List<AfterSalesRequest> allRequests = afterSalesRequestRepository
                .findByOriginalOrder_OrderNoAndStatus(orderNo, AfterSalesRequest.RequestStatus.PENDING);

        boolean hasReturn = false;
        boolean hasExchange = false;

        // 遍歷申請，檢查是否包含該商品
        for (AfterSalesRequest req : allRequests) {
            for (AfterSalesRequestItem item : req.getItems()) {
                if (item.getProduct().getProductId().equals(productId)) {
                    if (req.getRequestType() == AfterSalesRequest.RequestType.RETURN) hasReturn = true;
                    if (req.getRequestType() == AfterSalesRequest.RequestType.EXCHANGE) hasExchange = true;
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("hasReturn", hasReturn);
        data.put("hasExchange", hasExchange);

        return ResponseEntity.ok(Result.okWithData("查詢成功", data));
    }

    /**
     * 【新增】獲取售后申請詳情 (用於彈窗展示，包含新訂單信息)
     */
    @Operation(summary = "獲取售后申請詳情", description = "獲取指定商品的所有售后申請詳情，換貨時包含新訂單信息")
    @GetMapping("/after-sales-details")
    @ResponseBody
    @Transactional // 確保能加載 newOrder 和 items
    public ResponseEntity<?> getAfterSalesDetails(
            @RequestParam String orderNo,
            @RequestParam Integer productId,
            Authentication authentication) {

        String username = authentication.getName();
        orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

        List<AfterSalesRequest> allRequests = afterSalesRequestRepository
                .findByOriginalOrder_OrderNoAndStatus(orderNo, AfterSalesRequest.RequestStatus.PENDING);

        Map<String, Object> resultData = new HashMap<>();

        for (AfterSalesRequest req : allRequests) {
            // 檢查該申請是否包含目標商品
            AfterSalesRequestItem targetItem = req.getItems().stream()
                    .filter(item -> item.getProduct().getProductId().equals(productId))
                    .findFirst()
                    .orElse(null);

            if (targetItem != null) {
                Map<String, Object> reqInfo = new HashMap<>();
                reqInfo.put("requestId", req.getRequestId());
                reqInfo.put("type", req.getRequestType().name()); // RETURN or EXCHANGE
                reqInfo.put("status", req.getStatus().name());
                reqInfo.put("reason", req.getReason());

                // ==========================================
                // 【核心修改】：獲取店鋪名稱
                // ==========================================
                String storeIdStr = req.getStoreId();
                if (storeIdStr != null && !storeIdStr.isEmpty()) {
                    reqInfo.put("storeId", storeIdStr); // 保留 ID 供前端邏輯使用

                    try {
                        // AfterSalesRequest 的 storeId 是 String，OfflineStore 的 ID 是 Long，需要轉換
                        Long storeId = Long.parseLong(storeIdStr);
                        // 查詢店鋪實體
                        OfflineStore store = offlineStoreRepository.findById(storeId).orElse(null);

                        if (store != null) {
                            reqInfo.put("storeName", store.getName()); // 【關鍵】返回店鋪名稱
                        } else {
                            reqInfo.put("storeName", "未知店鋪");
                        }
                    } catch (NumberFormatException e) {
                        reqInfo.put("storeName", "未知店鋪");
                    }
                } else {
                    reqInfo.put("storeName", "未指定店鋪");
                }
                // ==========================================

                reqInfo.put("appointmentDate", req.getAppointmentDate());
                reqInfo.put("appointmentTimeSlot", req.getAppointmentTimeSlot());
                reqInfo.put("quantity", targetItem.getReturnQuantity());

                // 【核心】如果是換貨且有新訂單，加載新訂單信息
                if (req.getRequestType() == AfterSalesRequest.RequestType.EXCHANGE && req.getNewOrder() != null) {
                    reqInfo.put("newOrderNo", req.getNewOrder().getOrderNo());
                }

                if (req.getRequestType() == AfterSalesRequest.RequestType.RETURN) {
                    resultData.put("returnRequest", reqInfo);
                } else {
                    resultData.put("exchangeRequest", reqInfo);
                }
            }
        }

        return ResponseEntity.ok(Result.okWithData("獲取詳情成功", resultData));
    }

    @PutMapping("/after-sales/cancel/{requestId}")
    @Transactional // 【建議新增】確保狀態更新與通知保存在同一個事務中
    public ResponseEntity<Result> cancelAfterSalesRequest(
            @PathVariable Long requestId,
            Authentication authentication) {

        try {
            String username = authentication.getName();

            // 1. 查找申請記錄
            AfterSalesRequest request = afterSalesRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("申請記錄不存在"));

            // 2. 權限校驗：只能取消自己的申請
            if (!request.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Result.error("無權操作此申請"));
            }

            // 3. 狀態校驗：只能取消 PENDING 狀態的申請
            if (request.getStatus() != AfterSalesRequest.RequestStatus.PENDING) {
                return ResponseEntity.badRequest().body(Result.error("只有待處理的申請才能取消"));
            }

            // 4. 更新狀態為 CANCELLED 並保存
            request.setStatus(AfterSalesRequest.RequestStatus.CANCELLED);
            afterSalesRequestRepository.save(request);

            // ==========================================
            // 【新增】發送系統通知 (告知用戶申請已取消)
            // ==========================================
            User currentUser = request.getUser(); // 直接使用關聯的 User 實體

            Notification notification = new Notification();
            notification.setRecipient(currentUser);
            notification.setSender(null); // null 代表系統自動發送
            notification.setType(Notification.NotificationType.SYSTEM);

            // 根據申請類型動態生成文案
            String actionText = (request.getRequestType() == AfterSalesRequest.RequestType.EXCHANGE) ? "換貨" : "退貨";

            notification.setTitle(String.format("🚫 您的%s申請已取消", actionText));
            notification.setContent(String.format(
                    "您的%s申請已成功取消。\n訂單編號：%s\n如有需要，您可以隨時重新提交申請。",
                    actionText,
                    request.getOriginalOrder().getOrderNo()
            ));
            notification.setTargetUrl("/account/orders"); // 引導用戶回到訂單列表或售後詳情頁
            notification.setRead(false);

            notificationRepository.save(notification);
            // ==========================================

            return ResponseEntity.ok(Result.ok("申請已成功取消，系統已發送通知"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("取消失敗: " + e.getMessage()));
        }
    }
}