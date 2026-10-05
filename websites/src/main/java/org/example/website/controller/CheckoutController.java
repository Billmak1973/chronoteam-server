package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.website.dto.Result;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.OrderService;
import org.example.website.service.SiteSettingService;
import org.example.website.service.SystemConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
@Tag(name = "結帳與訂單管理", description = "處理訂單創建、線上/線下支付、訂單明細修改等相關接口")
public class CheckoutController {

    private final OrderService orderService;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;
    private final OfflineStoreRepository offlineStoreRepository;
    private final OrderRepository orderRepository;
    private final SiteSettingService siteSettingService;
    private final StoreInventoryRepository storeInventoryRepository;
    private final UserAddressRepository userAddressRepository;

    @Hidden
    @GetMapping
    public String checkoutPage(@RequestParam String orderNo, Model model, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElse(null);
        model.addAttribute("user", currentUser);

        Order order = orderService.getOrderByOrderNoAndUsername(orderNo, username);
        List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderNo(orderNo);

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("shippingFee", systemConfigService.getShippingFee());
        model.addAttribute("freeShippingThreshold", systemConfigService.getFreeShippingThreshold());
        model.addAttribute("offlinePaymentDays", systemConfigService.getOfflinePaymentDays());

        List<OfflineStore> activeStores = offlineStoreRepository.findByIsActiveTrue();
        model.addAttribute("activeStores", activeStores);
        model.addAttribute("hasStores", !activeStores.isEmpty());

        if (!activeStores.isEmpty()) {
            Map<Long, Map<Integer, Integer>> storeStockMap = new HashMap<>();
            for (OfflineStore store : activeStores) {
                Map<Integer, Integer> productStockMap = new HashMap<>();
                for (OrderItem item : orderItems) {
                    Optional<StoreInventory> storeInv = storeInventoryRepository
                            .findByStore_StoreIdAndProduct_ProductId(store.getStoreId(), item.getProduct().getProductId());
                    int stock = storeInv.map(StoreInventory::getQuantity).orElse(0);
                    productStockMap.put(item.getProduct().getProductId(), stock);
                }
                storeStockMap.put(store.getStoreId(), productStockMap);
            }
            model.addAttribute("storeStockMap", storeStockMap);
        }

        // ==========================================
        // 【核心重構】計算配送截止時間與預計送貨日
        // ==========================================
        String deliveryMode = systemConfigService.getDeliveryMode();
        String continuousMode = systemConfigService.getContinuousDaysMode();

        String cutoffTimeStr = systemConfigService.getGlobalCutoffTime();
        String[] timeParts = cutoffTimeStr.split(":");
        int cutoffHour = Integer.parseInt(timeParts[0]);
        int cutoffMinute = Integer.parseInt(timeParts[1]);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffDateTime;
        LocalDate estimatedDeliveryDate = now.toLocalDate();

        List<Map<String, Object>> availableDeliveryDates = new ArrayList<>();

        // 【嚴格執行】：獲取配置的最大選項數量。若無配置則為 0，絕對不給予任何默認值！
        Integer maxOptionsConfig = systemConfigService.getMaxDeliveryDateOptions();
        int maxOptionsVal = (maxOptionsConfig != null) ? maxOptionsConfig : 0;

        if ("SPECIFIC_DAYS".equals(deliveryMode)) {
            List<Integer> specificDays = systemConfigService.getDeliverySpecificDays();
            List<Integer> sortedDays = new ArrayList<>(specificDays);
            Collections.sort(sortedDays);

            boolean isSingleDay = (sortedDays.size() == 1);
            boolean isConsecutive = true;
            if (sortedDays.size() > 1) {
                for (int i = 0; i < sortedDays.size() - 1; i++) {
                    if (sortedDays.get(i + 1) - sortedDays.get(i) != 1) {
                        isConsecutive = false;
                        break;
                    }
                }
            } else {
                isConsecutive = false;
            }

            if (isSingleDay) {
                int targetDayOfWeek = sortedDays.get(0);
                LocalDate nextDeliveryDate = findNextSpecificDayDate(now.toLocalDate(), sortedDays);
                LocalDate cutoffDate = nextDeliveryDate.plusDays(systemConfigService.getCutoffDayOffset());
                cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);

                if (now.isAfter(cutoffDateTime)) {
                    nextDeliveryDate = findNextSpecificDayDate(nextDeliveryDate.plusDays(1), sortedDays);
                    cutoffDate = nextDeliveryDate.plusDays(systemConfigService.getCutoffDayOffset());
                    cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);
                }

                estimatedDeliveryDate = nextDeliveryDate;
                LocalDate currentDateOption = nextDeliveryDate;
                for (int i = 0; i < maxOptionsVal; i++) {
                    Map<String, Object> dateOption = new HashMap<>();
                    dateOption.put("date", currentDateOption.toString());
                    String displayText = getRelativeWeekdayText(currentDateOption, targetDayOfWeek, i);
                    dateOption.put("displayText", displayText);
                    availableDeliveryDates.add(dateOption);
                    currentDateOption = currentDateOption.plusWeeks(1);
                }

            } else if (continuousMode != null && continuousMode.equals("GROUP")) {
                int firstDayOfWeek = specificDays.get(0);
                LocalDate nextCycleStart = findNextSpecificDayDate(now.toLocalDate(), Collections.singletonList(firstDayOfWeek));
                LocalDate cutoffBaseDate = nextCycleStart.plusDays(systemConfigService.getCutoffDayOffset());
                cutoffDateTime = cutoffBaseDate.atTime(cutoffHour, cutoffMinute, 0);

                if (now.isAfter(cutoffDateTime)) {
                    nextCycleStart = findNextSpecificDayDate(nextCycleStart.plusDays(1), Collections.singletonList(firstDayOfWeek));
                    cutoffBaseDate = nextCycleStart.plusDays(systemConfigService.getCutoffDayOffset());
                    cutoffDateTime = cutoffBaseDate.atTime(cutoffHour, cutoffMinute, 0);
                }

                estimatedDeliveryDate = nextCycleStart;
                LocalDate currentCycleStart = nextCycleStart;
                LocalDateTime currentCutoffTime = cutoffDateTime;
                int addedCount = 0;

                while (addedCount < maxOptionsVal) {
                    for (int i = 0; i < specificDays.size(); i++) {
                        if (addedCount >= maxOptionsVal) break;

                        int dayOffset = specificDays.get(i) - firstDayOfWeek;
                        LocalDate deliveryDate = currentCycleStart.plusDays(dayOffset);
                        boolean isPastCutoff = now.isAfter(currentCutoffTime);

                        if (!isPastCutoff) {
                            Map<String, Object> dateOption = new HashMap<>();
                            dateOption.put("date", deliveryDate.toString());
                            String displayText = getNaturalWeekLabel(deliveryDate, now.toLocalDate(), specificDays.get(i));
                            dateOption.put("displayText", displayText);
                            availableDeliveryDates.add(dateOption);
                            addedCount++;
                        }
                    }
                    currentCycleStart = currentCycleStart.plusWeeks(1);
                    currentCutoffTime = currentCutoffTime.plusWeeks(1);
                    if (currentCycleStart.isAfter(now.toLocalDate().plusMonths(6))) break;
                }

            } else {
                // --- 情況 3：非連續多天 或 INDEPENDENT 模式 ---
                LocalDate searchDate = now.toLocalDate();
                int count = 0;
                int maxSearchDays = 30;

                while (count < maxOptionsVal && maxSearchDays > 0) {
                    int dayOfWeekValue = searchDate.getDayOfWeek().getValue();
                    if (specificDays.contains(dayOfWeekValue)) {
                        boolean isToday = searchDate.equals(now.toLocalDate());
                        boolean isPastCutoff = false;
                        if (isToday) {
                            LocalDate cutoffDate = searchDate.plusDays(systemConfigService.getCutoffDayOffset());
                            LocalDateTime cutoffTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);
                            if (now.isAfter(cutoffTime)) isPastCutoff = true;
                        }

                        if (!isPastCutoff) {
                            Map<String, Object> dateOption = new HashMap<>();
                            dateOption.put("date", searchDate.toString());
                            String displayText = getMultiDayDisplayText(searchDate, now.toLocalDate());
                            dateOption.put("displayText", displayText);
                            availableDeliveryDates.add(dateOption);
                            count++;
                            if (count == 1) estimatedDeliveryDate = searchDate;
                        }
                    }
                    searchDate = searchDate.plusDays(1);
                    maxSearchDays--;
                }

                if (!availableDeliveryDates.isEmpty()) {
                    LocalDate firstDate = LocalDate.parse(availableDeliveryDates.get(0).get("date").toString());
                    LocalDate cutoffDate = firstDate.plusDays(systemConfigService.getCutoffDayOffset());
                    cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);
                } else {
                    cutoffDateTime = calculateSpecificDaysCutoff(now, cutoffHour, cutoffMinute, systemConfigService.getCutoffDayOffset());
                    estimatedDeliveryDate = cutoffDateTime.toLocalDate().plusDays(-systemConfigService.getCutoffDayOffset());
                }
            }
        } else {
            // ==========================================
            // 【核心修復】：NEXT_DAY 或 CUSTOM 模式的正確邏輯
            // 邏輯：截單日基於「今天是否已過截單時間」決定，送達日 = 截單日 + 配送天數
            // ==========================================

            // 1. 確定「有效截單日」
            LocalDate today = now.toLocalDate();
            LocalDateTime todayCutoff = today.atTime(cutoffHour, cutoffMinute);

            // 如果當前時間已過今天的截單時間，則有效截單日為明天；否則為今天
            LocalDate effectiveCutoffDate = now.isAfter(todayCutoff) ? today.plusDays(1) : today;

            // 2. 計算當前訂單的截單時間
            cutoffDateTime = effectiveCutoffDate.atTime(cutoffHour, cutoffMinute);

            // 3. 計算最早送達日
            int deliveryDays = "NEXT_DAY".equals(deliveryMode) ? 1 : systemConfigService.getDeliveryCustomDays();
            estimatedDeliveryDate = effectiveCutoffDate.plusDays(deliveryDays);

            // 4. 嚴格使用 maxOptionsVal 循環生成選項
            LocalDate currentDateOption = estimatedDeliveryDate;
            for (int i = 0; i < maxOptionsVal; i++) {
                Map<String, Object> dateOption = new HashMap<>();
                dateOption.put("date", currentDateOption.toString());

                // 生成顯示文本 (相對於今天的天數)
                long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), currentDateOption);
                String dateStr = currentDateOption.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String weekDayStr = getChineseWeekDay(currentDateOption.getDayOfWeek().getValue());

                String displayText;
                if ("CUSTOM".equals(deliveryMode)) {
                    // 自定義模式：直接顯示相差天數與日期
                    displayText = daysDiff + "天後 (" + dateStr + " 星期" + weekDayStr + ")";
                } else {
                    // NEXT_DAY 模式
                    if (daysDiff == 1) {
                        displayText = "明天 (" + dateStr + " 星期" + weekDayStr + ")";
                    } else if (daysDiff == 2) {
                        displayText = "後天 (" + dateStr + " 星期" + weekDayStr + ")";
                    } else {
                        displayText = daysDiff + "天後 (" + dateStr + " 星期" + weekDayStr + ")";
                    }
                }

                dateOption.put("displayText", displayText);
                availableDeliveryDates.add(dateOption);

                // 下一個日期 (每次加 1 天)
                currentDateOption = currentDateOption.plusDays(1);
            }
        }

        List<UserAddress> userAddresses = userAddressRepository.findByUserOrderByRankingAsc(currentUser);

        model.addAttribute("cutoffDateTime", cutoffDateTime);
        model.addAttribute("deliveryMode", deliveryMode);
        model.addAttribute("estimatedDeliveryDate", estimatedDeliveryDate);
        model.addAttribute("availableDeliveryDates", availableDeliveryDates);
        model.addAttribute("userAddresses", userAddresses);
        model.addAttribute("maxDeliveryDateOptions", maxOptionsVal);

        return "checkout";
    }

    /**
     * 根據目標日期與當前日期的關係，生成精確的自然週標籤
     * 解決了因截單時間跳過導致的「本週/下週」標籤錯亂問題
     */
    private String getNaturalWeekLabel(LocalDate deliveryDate, LocalDate today, int targetDayOfWeek) {
        // 獲取今天所在自然週的星期一
        LocalDate thisMonday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 獲取發貨日所在自然週的星期一
        LocalDate deliveryMonday = deliveryDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        long weeksDiff = java.time.temporal.ChronoUnit.WEEKS.between(thisMonday, deliveryMonday);

        String chineseDay = getChineseWeekDay(targetDayOfWeek);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M月d日");
        String dateStr = deliveryDate.format(formatter);

        if (weeksDiff == 0) {
            return "本週" + chineseDay + " (" + dateStr + ")";
        } else if (weeksDiff == 1) {
            return "下週" + chineseDay + " (" + dateStr + ")";
        } else {
            return "第" + (weeksDiff + 1) + "週" + chineseDay + " (" + dateStr + ")";
        }
    }

    private String getMultiDayDisplayText(LocalDate date, LocalDate today) {
        DayOfWeek dow = date.getDayOfWeek();
        String chineseDay = getChineseWeekDay(dow.getValue());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M月d日");
        String dateStr = date.format(formatter);

        // 計算相差天數
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(today, date);

        if (daysDiff >= 0 && daysDiff <= 6) {
            // 本週內
            return "本週" + chineseDay + " (" + dateStr + ")";
        } else if (daysDiff >= 7 && daysDiff <= 13) {
            // 下週
            return "下週" + chineseDay + " (" + dateStr + ")";
        } else if (daysDiff >=14 && daysDiff <= 20) {
            // 下下週
            return "下下週" + chineseDay + " (" + dateStr + ")";
        }else {
            // 更遠的將來，直接顯示日期和星期
            return dateStr + " (" + chineseDay + ")";
        }
    }

    /**
     * 輔助方法：生成相對週幾的文字描述
     * @param date 目標日期
     * @param targetDayOfWeek 目標星期幾 (1-7)
     * @param index 第幾個週期 (0=本週, 1=下週, 2=第三週...)
     */
    private String getRelativeWeekdayText(LocalDate date, int targetDayOfWeek, int index) {
        DayOfWeek dayOfWeek = DayOfWeek.of(targetDayOfWeek);
        String chineseDay = getChineseWeekDay(dayOfWeek.getValue());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M月d日");
        String dateStr = date.format(formatter);

        if (index == 0) {
            return "本週" + chineseDay + " (" + dateStr + ")";
        } else if (index == 1) {
            return "下週" + chineseDay + " (" + dateStr + ")";
        } else {
            // 第三個週期開始，顯示 "第X週"
            int weekNumber = index + 1;
            return "第" + weekNumber + "週" + chineseDay + " (" + dateStr + ")";
        }
    }

    // 輔助方法：獲取中文星期幾
    private String getChineseWeekDay(int dayOfWeekValue) {
        switch (dayOfWeekValue) {
            case 1: return "一";
            case 2: return "二";
            case 3: return "三";
            case 4: return "四";
            case 5: return "五";
            case 6: return "六";
            case 7: return "日";
            default: return "";
        }
    }

    /**
     * 根據配送模式計算截止時間 (統一傳入 offset)
     */
    private LocalDateTime calculateCutoffDateTime(String deliveryMode, int cutoffDayOffset) {
        LocalDateTime now = LocalDateTime.now();
        String cutoffTimeStr = systemConfigService.getGlobalCutoffTime(); // 例如 "16:00"

        String[] timeParts = cutoffTimeStr.split(":");
        int cutoffHour = Integer.parseInt(timeParts[0]);
        int cutoffMinute = Integer.parseInt(timeParts[1]);

        switch (deliveryMode) {
            case "NEXT_DAY":
                return calculateNextDayCutoff(now, cutoffHour, cutoffMinute, cutoffDayOffset);
            case "SPECIFIC_DAYS":
                return calculateSpecificDaysCutoff(now, cutoffHour, cutoffMinute, cutoffDayOffset);
            case "CUSTOM":
                return calculateCustomDaysCutoff(now, cutoffHour, cutoffMinute, cutoffDayOffset);
            default:
                return calculateNextDayCutoff(now, cutoffHour, cutoffMinute, cutoffDayOffset);
        }
    }

    private LocalDateTime calculateNextDayCutoff(LocalDateTime now, int cutoffHour, int cutoffMinute, int cutoffDayOffset) {
        // 次日達：基準送貨日是明天
        LocalDate baseDeliveryDate = now.toLocalDate().plusDays(1);
        // 截單日 = 基準送貨日 + offset (通常 offset = -1，所以截單日就是今天)
        LocalDate cutoffDate = baseDeliveryDate.plusDays(cutoffDayOffset);
        LocalDateTime cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);

        // 如果當前時間已過截單時間，說明錯過這一班，基準送貨日順延1天，截單日也順延1天
        if (now.isAfter(cutoffDateTime)) {
            baseDeliveryDate = baseDeliveryDate.plusDays(1);
            cutoffDate = baseDeliveryDate.plusDays(cutoffDayOffset);
            cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);
        }
        return cutoffDateTime;
    }

    private LocalDateTime calculateSpecificDaysCutoff(LocalDateTime now, int cutoffHour, int cutoffMinute, int cutoffDayOffset) {
        List<Integer> specificDays = systemConfigService.getDeliverySpecificDays();

        // 1. 找到最近的基準送貨日
        LocalDate baseDeliveryDate = findNextSpecificDayDate(now.toLocalDate(), specificDays);
        // 2. 計算對應的截單日
        LocalDate cutoffDate = baseDeliveryDate.plusDays(cutoffDayOffset);
        LocalDateTime cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);

        // 3. 如果當前時間已過截單時間，說明錯過本週期
        if (now.isAfter(cutoffDateTime)) {
            // 從當前截單日的下一天開始，尋找下一個週期的送貨日
            baseDeliveryDate = findNextSpecificDayDate(cutoffDate.plusDays(1), specificDays);
            cutoffDate = baseDeliveryDate.plusDays(cutoffDayOffset);
            cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);
        }
        return cutoffDateTime;
    }

    private LocalDateTime calculateCustomDaysCutoff(LocalDateTime now, int cutoffHour, int cutoffMinute, int cutoffDayOffset) {
        Integer customDays = systemConfigService.getDeliveryCustomDays();

        // 基準送貨日 = 今天 + customDays
        LocalDate baseDeliveryDate = now.toLocalDate().plusDays(customDays);
        // 截單日 = 基準送貨日 + offset
        LocalDate cutoffDate = baseDeliveryDate.plusDays(cutoffDayOffset);
        LocalDateTime cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);

        // 如果已過截單時間，順延一個 customDays 週期
        if (now.isAfter(cutoffDateTime)) {
            baseDeliveryDate = baseDeliveryDate.plusDays(customDays);
            cutoffDate = baseDeliveryDate.plusDays(cutoffDayOffset);
            cutoffDateTime = cutoffDate.atTime(cutoffHour, cutoffMinute, 0);
        }
        return cutoffDateTime;
    }

    /**
     * 輔助方法：從指定日期開始，尋找下一個符合 specificDays 的日期
     */
    private LocalDate findNextSpecificDayDate(LocalDate startDate, List<Integer> specificDays) {
        LocalDate date = startDate;
        for (int i = 0; i < 14; i++) { // 最多找兩週
            int dayOfWeek = date.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
            if (specificDays.contains(dayOfWeek)) {
                return date;
            }
            date = date.plusDays(1);
        }
        return startDate; // fallback
    }


    /**
     * API: 前端點擊「去結賬」時調用，生成訂單並返回 orderNo
     */
    @Operation(
            summary = "創建訂單",
            description = "根據用戶當前購物車中已選中的商品，生成一筆新的待付款訂單，並清空已選中的購物車項目。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "訂單創建成功，返回 orderNo", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "購物車為空或商品庫存不足"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<?> createOrder(
            @Parameter(hidden = true) Authentication authentication) {
        try {
            Order order = orderService.createOrder(authentication.getName());
            return ResponseEntity.ok(Result.okWithData("訂單創建成功", order.getOrderNo()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @PostMapping("/api/pay")
    @ResponseBody
    public ResponseEntity<?> simulatePay(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "支付請求參數，需包含: orderNo, amount, deliveryMethod, storeId, deliveryDate, appointmentDate, addressId",
                    required = true
            )
            @RequestBody Map<String, Object> payload,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            String orderNo = (String) payload.get("orderNo");
            BigDecimal payAmount = new BigDecimal(payload.get("amount").toString());
            String deliveryMethod = payload.containsKey("deliveryMethod") ? (String) payload.get("deliveryMethod") : null;

            // 提取 storeId
            Long storeId = null;
            Object storeIdObj = payload.get("storeId");
            if (storeIdObj != null) {
                String storeIdStr = storeIdObj.toString();
                if (!storeIdStr.isEmpty() && !"null".equalsIgnoreCase(storeIdStr)) {
                    try { storeId = Long.valueOf(storeIdStr); } catch (NumberFormatException e) {}
                }
            }

            // 提取 deliveryDate
            LocalDate customerSelectedDeliveryDate = null;
            if (payload.containsKey("deliveryDate") && payload.get("deliveryDate") != null) {
                customerSelectedDeliveryDate = LocalDate.parse(payload.get("deliveryDate").toString());
            }

            // 提取 appointmentDate
            LocalDate appointmentDate = null;
            if (payload.containsKey("appointmentDate") && payload.get("appointmentDate") != null) {
                try {
                    appointmentDate = LocalDate.parse(payload.get("appointmentDate").toString());
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Result.error("預約取貨日期格式不正確，請使用 yyyy-MM-dd 格式"));
                }
            }

            // 【新增】提取 addressId
            Long addressId = null;
            Object addressIdObj = payload.get("addressId");
            if (addressIdObj != null) {
                String addressIdStr = addressIdObj.toString();
                if (!addressIdStr.isEmpty() && !"null".equalsIgnoreCase(addressIdStr)) {
                    try {
                        addressId = Long.valueOf(addressIdStr);
                    } catch (NumberFormatException e) {
                        // 忽略無效的 addressId
                    }
                }
            }

            // 將 addressId 作為最後一個參數傳遞給 Service 層
            Order order = orderService.simulatePayment(
                    orderNo,
                    authentication.getName(),
                    payAmount,
                    deliveryMethod,
                    storeId,
                    customerSelectedDeliveryDate,
                    appointmentDate,
                    addressId // <--- 傳入 addressId
            );

            return ResponseEntity.ok(Result.okWithData("支付成功", order.getOrderNo()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 渲染支付成功頁面
     */
    @Hidden // 隱藏純頁面渲染接口
    @GetMapping("/payment-success")
    public String paymentSuccess(@RequestParam String orderNo, Model model, Authentication authentication) {
        try {
            String username = authentication.getName();
            // 查詢訂單信息
            Order order = orderService.getOrderByOrderNoAndUsername(orderNo, username);
            model.addAttribute("order", order);
            return "payment-success";  // 返回 templates/payment-success.html
        } catch (Exception e) {
            // 如果訂單不存在或無權訪問，跳轉到首頁
            return "redirect:/";
        }
    }

    /**
     * API: 創建線下支付訂單
     */
    @Operation(
            summary = "創建線下支付訂單",
            description = "用戶選擇線下門店支付，生成訂單並關聯指定的門店信息，同時扣減庫存。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "訂單創建成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "未選擇門店或訂單狀態異常"),
            @ApiResponse(responseCode = "401", description = "未登入或無權操作此訂單")
    })
    @PostMapping("/api/offline-payment")
    @ResponseBody
    public ResponseEntity<?> createOfflinePayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "線下支付請求參數，需包含: orderNo (String), storeId (Number), deliveryMethod (String, 可選)",
                    required = true
            )
            @RequestBody Map<String, Object> payload,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            String orderNo = (String) payload.get("orderNo");
            Long storeId = payload.containsKey("storeId") ? Long.valueOf(payload.get("storeId").toString()) : null;

            // 基礎參數校驗
            if (storeId == null) {
                return ResponseEntity.badRequest().body(Result.error("請選擇線下支付店鋪"));
            }

            // 從 payload 中提取配送方式（線下支付時也可能有配送方式選擇）
            String deliveryMethod = payload.containsKey("deliveryMethod") ? (String) payload.get("deliveryMethod") : "STORE_PICKUP";

            LocalDate appointmentDate=null;
            if (payload.containsKey("appointmentDate") && payload.get("appointmentDate") != null) {
                try{
                    appointmentDate=LocalDate.parse(payload.get("appointmentDate").toString());
                }catch (Exception e){
                    return ResponseEntity.badRequest().body(Result.error("預約取貨日期格式不正確，請使用 yyyy-MM-dd 格式"));
                }
            }
            // 核心：調用 Service 層處理業務與數據庫操作
            Order order = orderService.processOfflinePayment(orderNo, authentication.getName(), storeId, deliveryMethod,appointmentDate);

            // 構建返回數據
            Map<String, Object> data = new HashMap<>();
            data.put("orderNo", order.getOrderNo());
            data.put("storeId", storeId);

            return ResponseEntity.ok(Result.okWithData("訂單已創建，請前往店鋪支付", data));
        } catch (Exception e) {
            // 統一異常處理
            return ResponseEntity.badRequest().body(Result.error("創建失敗: " + e.getMessage()));
        }
    }

    @Hidden
    @GetMapping("/offline-success")
    public String offlinePaymentSuccess(
            @RequestParam String orderNo,
            @RequestParam String storeId,
            Model model,
            Authentication authentication) {

        // 1. 查詢訂單信息 (確保是當前用戶的訂單，並獲取預約日期)
        String username = authentication.getName();
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

        // 2. 查詢店鋪信息
        OfflineStore store = offlineStoreRepository.findById(Long.parseLong(storeId))
                .orElseThrow(() -> new RuntimeException("店鋪不存在"));

        // 3. 格式化預約日期 (將 LocalDate 轉換為易讀的字符串)
        String appointmentDateStr = "未設定";
        if (order.getAppointmentDate() != null) {
            appointmentDateStr = order.getAppointmentDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        }

        // 4. 將數據傳遞給前端
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("storeName", store.getName());
        data.put("storeAddress", store.getAddress());
        data.put("storePhone", store.getPhone() != null ? store.getPhone() : "未提供");
        data.put("appointmentDate", appointmentDateStr);

        model.addAttribute("data", data);

        // 【核心新增】：獲取並傳遞卡片邊框主題 (day / night / qixi) 到前端
        String cardTheme = siteSettingService.getCardBorderTheme();
        model.addAttribute("cardTheme", cardTheme);

        return "offline-payment-success";
    }

    /**
     * API：在結賬頁面修改訂單商品數量
     */
    @Operation(
            summary = "修改訂單商品數量",
            description = "在結賬頁面動態修改某個訂單商品的數量，後端會重新校驗庫存並重新計算訂單總價與運費。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功，返回最新數量、小計、總價與運費", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "庫存不足、數量無效或訂單已支付"),
            @ApiResponse(responseCode = "401", description = "未登入或無權操作此訂單")
    })
    @PutMapping("/api/update-item/{orderItemId}")
    @ResponseBody
    public ResponseEntity<?> updateOrderItem(
            @Parameter(description = "訂單明細ID (OrderItem ID)", required = true, example = "1")
            @PathVariable Long orderItemId,
            @Parameter(description = "新的商品數量 (必須 > 0)", required = true, example = "2")
            @RequestParam Integer quantity,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            OrderItem updatedItem = orderService.updateOrderItemQuantity(orderItemId, quantity, authentication.getName());
            Order order = updatedItem.getOrder(); // 獲取已經被 recalculateOrderTotal 更新過的訂單對象

            Map<String, Object> data = new HashMap<>();
            data.put("quantity", updatedItem.getQuantity());
            data.put("subtotal", updatedItem.getPrice().multiply(BigDecimal.valueOf(updatedItem.getQuantity())));
            data.put("newTotalAmount", order.getTotalAmount());
            data.put("shippingFee", order.getShippingFee()); // 【關鍵】必須返回最新運費

            return ResponseEntity.ok(Result.okWithData("更新成功", data));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * API：從待支付訂單中移除某個商品
     */
    @Operation(
            summary = "移除訂單商品",
            description = "從待支付訂單中移除某個商品，並重新計算總價。若訂單內已無商品，則自動取消該訂單。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "移除成功，返回最新總價與運費。若訂單為空則返回 ORDER_EMPTY 錯誤碼", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "訂單已支付或商品不存在"),
            @ApiResponse(responseCode = "401", description = "未登入或無權操作此訂單")
    })
    @DeleteMapping("/api/remove-item/{orderItemId}")
    @ResponseBody
    public ResponseEntity<?> removeOrderItem(
            @Parameter(description = "訂單明細ID (OrderItem ID)", required = true, example = "1")
            @PathVariable Long orderItemId,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            // 先獲取 orderNo，以便刪除後查詢最新狀態
            OrderItem item = orderItemRepository.findById(orderItemId).orElseThrow(() -> new RuntimeException("訂單商品不存在"));
            String orderNo = item.getOrder().getOrderNo();

            // 執行刪除 (內部會調用 recalculateOrderTotal)
            orderService.removeOrderItem(orderItemId, authentication.getName());

            // 如果沒拋異常，說明訂單還在，重新查詢最新訂單狀態返回給前端
            Order updatedOrder = orderService.getOrderByOrderNoAndUsername(orderNo, authentication.getName());

            Map<String, Object> data = new HashMap<>();
            data.put("newTotalAmount", updatedOrder.getTotalAmount());
            data.put("shippingFee", updatedOrder.getShippingFee()); // 【關鍵】必須返回最新運費

            return ResponseEntity.ok(Result.okWithData("已移除", data));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("訂單已自動取消")) {
                return ResponseEntity.ok(Result.error("ORDER_EMPTY"));
            }
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}