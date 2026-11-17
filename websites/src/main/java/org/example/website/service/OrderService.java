package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.website.repository.SystemConfigRepository; // 新增

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final DailyBusinessReportService dailyBusinessReportService;
    private final SystemConfigRepository systemConfigRepository;
    private final QuarterlySalesReportService quarterlySalesReportService;
    private final QuarterlySalesReportRepository quarterlySalesReportRepository;
    private final DailyBusinessReportRepository dailyBusinessReportRepository;
    private final OfflineStoreRepository offlineStoreRepository;
    private final StoreInventoryRepository storeInventoryRepository;
    private final NotificationRepository notificationRepository;
    private final UserAddressRepository userAddressRepository;
    private final StoreDailyReportRepository storeDailyReportRepository;
    private final StaffRepository staffRepository;
    /**
     * 1. 創建訂單 (移除庫存扣減，僅校驗庫存是否充足)
     */
    @Transactional
    public Order createOrder(String username) {
        List<Cart> cartItems = cartRepository.findByUser_UsernameAndSelectedTrue(username);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("購物車是空的，無法創建訂單");
        }

        BigDecimal totalAmount = cartItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //  核心修改 1：使用 UserRepository 獲取 User 實體
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Order order = new Order();
        order.setOrderNo("ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());

        //  核心修改 2：設置 User 關聯，不再是 setCustomer
        order.setUser(user);
        order.setOrderSource("ONLINE");

        order.setTotalAmount(totalAmount);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        order.setPaymentMethod("PAYPAL_SIM");

        //  僅校驗庫存，不再扣減庫存！
        for (Cart item : cartItems) {
            Product product = item.getProduct();
            if (product.getStock() < item.getQuantity()) {
                throw new RuntimeException("商品 [" + product.getDescription() + "] 庫存不足，當前庫存: " + product.getStock());
            }
        }

        Order savedOrder = orderRepository.save(order);

        for (Cart cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(savedOrder);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());
            orderItemRepository.save(orderItem);
        }

        cartRepository.deleteByUser_UsernameAndSelectedTrue(username);
        return savedOrder;
    }

    /**
     * 線上訂單支付成功後的庫存扣減 (扣減線上總倉)
     */
    private void deductOnlineStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            int qty = item.getQuantity();

            // 二次校驗防超賣
            if (product.getStock() < qty) {
                throw new RuntimeException("支付失敗：線上商品 [" + product.getDescription() + "] 庫存不足！");
            }

            // 扣減線上庫存
            product.setStock(product.getStock() - qty);
            productRepository.save(product);
        }
    }

    /**
     * 線下門店訂單確認後的庫存扣減 (扣減特定門店)
     */
    private void deductOfflineStock(Order order) {
        OfflineStore store = order.getOfflineStore();
        if (store == null) {
            throw new RuntimeException("線下訂單未關聯門店，無法扣減庫存");
        }

        for (OrderItem item : order.getItems()) {
            Integer productId = item.getProduct().getProductId();
            int qty = item.getQuantity();

            // 使用原子更新語句扣減，如果返回 0 說明庫存不足(执行乐观锁更新，获取受影响的行数)
            int updatedRows = storeInventoryRepository.deductStoreStock(store.getStoreId(), productId, qty);

            if (updatedRows == 0) {
                throw new RuntimeException("支付失敗：門店 [" + store.getName() + "] 的商品 [" + item.getProduct().getDescription() + "] 庫存不足！");
            }
        }
    }

    /**
     * 2. 線上模擬支付處理 (支付成功後扣減庫存，並正確處理運費)
     */
    @Transactional
    public Order simulatePayment(String orderNo, String username, BigDecimal payAmount, String deliveryMethod,
                                 Long storeId, LocalDate customerSelectedDeliveryDate,LocalDate appointmentDate,Long addressId) {
        // 1. 查詢訂單並校驗權限
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權操作此訂單"));

        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
            throw new RuntimeException("訂單狀態異常，無法重複支付。當前狀態: " + order.getPaymentStatus());
        }

        // 2. 從數據庫動態讀取最新的運費規則
        BigDecimal realShippingFee = systemConfigRepository.findById("SHIPPING_FEE")
                .map(c -> new BigDecimal(c.getConfigValue())).orElse(new BigDecimal("50"));
        BigDecimal realThreshold = systemConfigRepository.findById("FREE_SHIPPING_THRESHOLD")
                .map(c -> new BigDecimal(c.getConfigValue())).orElse(new BigDecimal("50000"));

        // 3. 獲取純商品總價 (創建訂單時存入的 totalAmount 就是純商品總價)
        BigDecimal realSubtotal = order.getTotalAmount();
        BigDecimal realTotal = realSubtotal; // 默認總價等於商品總價

        // 4. 重新計算真實的應付總價
        if ("EXPRESS".equals(deliveryMethod) && realSubtotal.compareTo(realThreshold) < 0) {
            realTotal = realTotal.add(realShippingFee);
        }

//        System.out.println("====== 支付金额校验失败调试 ======");
//        System.out.println("1. 前端传来的金额 (payAmount): " + payAmount);
//        System.out.println("2. 订单原始商品总额 (realSubtotal): " + realSubtotal);
//        System.out.println("3. 当前数据库配置的运费: " + realShippingFee);
//        System.out.println("4. 当前数据库配置的免邮门槛: " + realThreshold);
//        System.out.println("5. 前端传来的配送方式: " + deliveryMethod);
//        System.out.println("6. 后端最终计算总额 (realTotal): " + realTotal);
//        System.out.println("================================");

        // 5. 核心安全校驗：比對前端傳來的金額與後端計算的真實總價是否一致
        if (payAmount.compareTo(realTotal) != 0) {
            throw new RuntimeException("安全警告：訂單金額與後端計算不符，可能存在篡改行為！");
        }

        // 6. 更新訂單的真實總價與運費記錄 (寫入數據庫)
        order.setTotalAmount(realTotal);
        order.setShippingFee(realTotal.compareTo(realSubtotal) > 0 ? realShippingFee : BigDecimal.ZERO);

        // 7. 設置配送方式和 delivery 字段
        order.setDeliveryMethod(deliveryMethod);
        order.setDelivery("EXPRESS".equals(deliveryMethod)); // 如果是快遞配送，delivery=true；門店自取=false

        // 如果是門店自取，且前端傳來了有效的 storeId，則保存到數據庫
        if ("STORE_PICKUP".equals(deliveryMethod) && storeId != null) {
            OfflineStore store = offlineStoreRepository.findById(storeId)
                    .orElseThrow(() -> new RuntimeException("門店不存在"));
            order.setOfflineStore(store);
            order.setOrderSource("OFFLINE");

            if (appointmentDate != null) {
                order.setAppointmentDate(appointmentDate);
            }
        }

        // 8. 如果是快遞配送，且前端傳來了有效的 addressId，則關聯收貨地址
        if ("EXPRESS".equals(deliveryMethod) && addressId != null) {
            UserAddress userAddress = userAddressRepository.findById(addressId)
                    .orElseThrow(() -> new RuntimeException("收貨地址不存在"));

            // 【安全校驗】：確保該地址確實屬於當前登錄用戶，防止越權漏洞
            if (!userAddress.getUser().getUsername().equals(username)) {
                throw new RuntimeException("無權使用此收貨地址");
            }

            order.setUserAddress(userAddress);
        }

        // 9. 【核心修正】設置預計送達日期 (如果顧客選擇了快遞且指定了日期)
        if (customerSelectedDeliveryDate != null && "EXPRESS".equals(deliveryMethod)) {
            order.setEstimatedDeliveryDate(customerSelectedDeliveryDate);
        }

        // 10. 更新支付狀態
        order.setPaymentStatus(Order.PaymentStatus.PAID_SIMULATED);
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // 11. 線上支付成功，呼叫線上專屬的庫存扣減方法！
        deductOnlineStock(savedOrder);
        // ==========================================
        // 12. 記錄銷售報表數據 (核心修改區)
        // ==========================================

        // 判斷是否為「線下門店自取」
        boolean isStorePickup = "STORE_PICKUP".equals(deliveryMethod) && savedOrder.getOfflineStore() != null;
        Long targetStoreId = isStorePickup ? savedOrder.getOfflineStore().getStoreId() : null;

        for (OrderItem item : savedOrder.getItems()) {
            Product product = item.getProduct();
            BigDecimal itemTotalAmount = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

            if (isStorePickup) {
                // === 情況 A：線上支付，線下取貨 -> 業績歸門店 ===

                // 1. 更新門店每日業務報表
                updateStoreDailyReportForPickup(savedOrder, targetStoreId);

                // 2. 更新季度銷售報表 (標記為 OFFLINE 渠道，並綁定 storeId)
                quarterlySalesReportService.recordSale(
                        product.getProductId(),
                        product.getDescription(),
                        product.getBrand(),
                        product.getPrice(),
                        item.getQuantity(),
                        itemTotalAmount,
                        "OFFLINE",  // 渠道標記為線下，因為業績歸門店
                        targetStoreId // 傳入門店 ID
                );
            } else {
                // === 情況 B：純線上快遞 -> 業績歸線上總倉 ===

                // 1. 更新每日業務報表 (線上部分)
                dailyBusinessReportService.updateDailyReport(savedOrder);

                // 2. 更新季度銷售報表 (標記為 ONLINE 渠道，storeId 為 null)
                quarterlySalesReportService.recordSale(
                        product.getProductId(),
                        product.getDescription(),
                        product.getBrand(),
                        product.getPrice(),
                        item.getQuantity(),
                        itemTotalAmount,
                        "ONLINE",
                        null
                );
            }
        }

        return savedOrder;
    }

    /**
     * 【新增輔助方法】更新門店每日業務報表 (針對線上支付線下取貨場景)
     */
    private void updateStoreDailyReportForPickup(Order order, Long storeId) {
        if (storeId == null) return;

        LocalDate reportDate = LocalDate.now(); // 或使用 order.getPaidAt().toLocalDate()

        // 查找或創建該門店當日的報表
        StoreDailyReport report = storeDailyReportRepository.findByStore_StoreIdAndReportDate(storeId, reportDate)
                .orElseGet(() -> {
                    StoreDailyReport newReport = new StoreDailyReport();
                    OfflineStore store = offlineStoreRepository.findById(storeId)
                            .orElseThrow(() -> new RuntimeException("門店不存在"));
                    newReport.setStore(store);
                    newReport.setReportDate(reportDate);
                    return newReport;
                });

        // 累加門店 GMV
        BigDecimal currentGmv = report.getStoreGmv() != null ? report.getStoreGmv() : BigDecimal.ZERO;
        report.setStoreGmv(currentGmv.add(order.getTotalAmount()));

        // 累加門店訂單數
        Integer currentOrders = report.getStoreOrders() != null ? report.getStoreOrders() : 0;
        report.setStoreOrders(currentOrders + 1);

        storeDailyReportRepository.save(report);
    }

    /**
     * 3. 獲取訂單詳情 (用於結帳頁面展示)
     */
    @Transactional(readOnly = true)
    public Order getOrderByOrderNoAndUsername(String orderNo, String username) {
        return orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權查看"));
    }

    /**
     * 獲取用戶的所有訂單
     */
    public List<Order> getUserOrders(String username) {
        return orderRepository.findByUser_UsernameOrderByCreatedAtDesc(username);
    }

    /**
     * 4. 處理線下支付邏輯 (確認線下支付訂單後扣減庫存)
     */
    @Transactional
    public Order processOfflinePayment(String orderNo, String username, Long storeId,String deliveryMethod,LocalDate appointmentDate) {
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權操作此訂單"));

        OfflineStore store = offlineStoreRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("门店不存在"));

        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
            throw new RuntimeException("訂單狀態異常，無法更改支付方式。當前狀態: " + order.getPaymentStatus());
        }

        order.setPaymentStatus(Order.PaymentStatus.PENDING_OFFLINE);
        order.setPaymentMethod("OFFLINE_STORE");
        order.setOfflineStore(store);
        order.setOrderSource("OFFLINE");
        order.setDeliveryMethod("STORE_PICKUP");
        order.setDelivery(false); // 線下支付=門店自取，delivery=false

        if(appointmentDate != null) {
            order.setAppointmentDate(appointmentDate);
        }
        order.setDeadlineAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // 【修改處 3】：線下支付確認生成訂單後，扣減庫存。
        // (註：由於前端流程在此直接跳轉成功頁，此處視為「確認支付」並扣減。
        // 若未來有「後台店員確認收款」的功能，應將此行移至後台確認收款的 API 中)
        deductOfflineStock(savedOrder);

        return savedOrder;
    }

    /**
     * 刪除訂單 (僅允許刪除待付款狀態的訂單)
     */
    @Transactional
    public void deleteOrder(String orderNo, String username) {
        // 1. 查找訂單並校驗權限 (確保只能刪自己的訂單)
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權操作"));

        // 2. 核心校驗：只允許刪除「未付款」或「待線下付款」的訂單
        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID &&
                order.getPaymentStatus() != Order.PaymentStatus.PENDING_OFFLINE) {
            throw new RuntimeException("已付款或正在處理中的訂單無法刪除");
        }

        // 3. 執行刪除
        // (因為 Order 實體中配置了 cascade = CascadeType.ALL，關聯的 OrderItem 會自動級聯刪除)
        orderRepository.delete(order);
    }



    /**
     * 修改结账页面中的订单商品数量，并重算总价
     */
    @Transactional
    public OrderItem updateOrderItemQuantity(Long orderItemId, Integer newQuantity, String username) {
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new RuntimeException("订单商品不存在"));

        Order order = item.getOrder();

        //  核心修改：getCustomer() 改為 getUser()
        // 校验权限和订单状态（必须是未付款的订单才能改）
        if (!order.getUser().getUsername().equals(username)) {
            throw new RuntimeException("无权操作此订单");
        }

        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
            throw new RuntimeException("订单已支付或正在处理中，无法修改");
        }

        // 校验库存
        if (newQuantity > item.getProduct().getStock()) {
            throw new RuntimeException("库存不足，当前库存: " + item.getProduct().getStock());
        }
        if (newQuantity <= 0) {
            throw new RuntimeException("数量必须大于0，若要删除请使用删除接口");
        }

        item.setQuantity(newQuantity);
        orderItemRepository.save(item);

        //  核心：重新计算订单总价
        recalculateOrderTotal(order);
        return item;
    }

    /**
     * 从待支付订单中删除某个商品，并重算总价
     */
    @Transactional
    public void removeOrderItem(Long orderItemId, String username) {
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new RuntimeException("订单商品不存在"));

        Order order = item.getOrder();

        // 核心修改：getCustomer() 改為 getUser()
        if (!order.getUser().getUsername().equals(username)) {
            throw new RuntimeException("无权操作此订单");
        }

        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
            throw new RuntimeException("订单已支付，无法删除商品");
        }

        // 删除该明细
        orderItemRepository.delete(item);

        //  核心：重新计算订单总价
        recalculateOrderTotal(order);

        // 如果订单被删空了，直接取消/删除该订单
        List<OrderItem> remainingItems = orderItemRepository.findByOrder_OrderNo(order.getOrderNo());
        if (remainingItems.isEmpty()) {
            orderRepository.delete(order);
            throw new RuntimeException("订单商品已清空，订单已自动取消");
        }
    }

    /**
     * 根据订单当前的 OrderItem 重新计算总价
     */
    private void recalculateOrderTotal(Order order) {
        // 1. 獲取最新的訂單明細
        List<OrderItem> items = orderItemRepository.findByOrder_OrderNo(order.getOrderNo());

        // 2. 計算純商品總價 (Subtotal)
        BigDecimal subtotal = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 【核心修復】實時從數據庫讀取管理員最新的運費配置
        BigDecimal shippingFeeConfig = systemConfigRepository.findById("SHIPPING_FEE")
                .map(c -> new BigDecimal(c.getConfigValue())).orElse(new BigDecimal("50"));
        BigDecimal freeThresholdConfig = systemConfigRepository.findById("FREE_SHIPPING_THRESHOLD")
                .map(c -> new BigDecimal(c.getConfigValue())).orElse(new BigDecimal("50000"));

        // 4. 動態計算實際運費 (如果不是門店自取，且未達免郵門檻，則收取運費)
        BigDecimal actualShippingFee = BigDecimal.ZERO;
        if (!"STORE_PICKUP".equals(order.getDeliveryMethod()) && subtotal.compareTo(freeThresholdConfig) < 0) {
            actualShippingFee = shippingFeeConfig;
        }

        // 5. 更新訂單的運費和總價，並保存
        order.setShippingFee(actualShippingFee);
        order.setTotalAmount(subtotal.add(actualShippingFee));
        orderRepository.save(order);
    }

    /**
     * 取消已付款訂單 (退款並恢復庫存)
     * 注意：此方法應由管理員後台或用戶在前端點擊「取消訂單」時調用
     */
    @Transactional
    public void cancelPaidOrder(String orderNo, String username) {
        // 1. 查找訂單並校驗權限
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權操作"));

        // 2. 核心校驗：只允許取消「已付款」狀態的訂單
        // (請根據您實際的 PaymentStatus 枚舉調整，例如 PAID_SIMULATED, PAID_REAL, PAID_OFFLINE)
        if (order.getPaymentStatus() != Order.PaymentStatus.PAID_SIMULATED &&
                order.getPaymentStatus() != Order.PaymentStatus.PAID_REAL &&
                order.getPaymentStatus() != Order.PaymentStatus.PAID_OFFLINE) {
            throw new RuntimeException("只有已付款的訂單才能執行取消/退款操作");
        }

        // 3. 更新訂單狀態為已取消
        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setPaymentStatus(Order.PaymentStatus.REFUNDED);

        orderRepository.save(order);

        // 4. 恢復庫存 (這就是您提到的缺失部分)
        restoreStock(order);

        // 5. 【核心修改】：根據訂單來源更新不同的報表
        if ("OFFLINE".equals(order.getOrderSource())) {
            // 線下訂單：更新門店日報表
            updateStoreDailyReportForCancellation(order);
        } else {
            // 線上訂單：更新全局日報表
            updateDailyBusinessReportForCancellation(order);
        }

        // 6. 季度報表更新 (保持不變，因為 QuarterlySalesReport 已經區分了 channel 和 store_id)
        updateQuarterlySalesReportForCancellation(order);
    }

    /**
     * 恢復庫存方法 (配合拆分邏輯，區分線上與線下)
     * 遍歷訂單中的商品，將賣出的數量加回對應的庫存中
     */
    private void restoreStock(Order order) {
        boolean isOffline = "STORE_PICKUP".equals(order.getDeliveryMethod());
        OfflineStore store = order.getOfflineStore();

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            int qty = item.getQuantity();

            if (isOffline && store != null) {
                // 線下訂單：將庫存還給特定門店
                StoreInventory inventory = storeInventoryRepository.findByStoreAndProduct(store, product)
                        .orElseThrow(() -> new RuntimeException("找不到門店庫存記錄，無法恢復庫存"));
                inventory.setQuantity(inventory.getQuantity() + qty);
                storeInventoryRepository.save(inventory);
            } else {
                // 線上訂單：將庫存還給線上總倉
                product.setStock(product.getStock() + qty);
                productRepository.save(product);
            }
        }
    }


    /**
     * 更新每日業務報告 (記錄退款) - 僅針對線上訂單
     * 注意：線下訂單的退款現在由 updateStoreDailyReportForCancellation 處理
     */
    @Transactional
    public void updateDailyBusinessReportForCancellation(Order order) {
        // 【核心判斷】：如果是線下訂單，直接跳過，不操作全局報表
        if ("OFFLINE".equals(order.getOrderSource())) {
            return;
        }

        // 使用訂單創建日期或付款日期作為報表日期
        LocalDate reportDate = order.getPaidAt() != null ? order.getPaidAt().toLocalDate() : order.getCreatedAt().toLocalDate();

        DailyBusinessReport report = dailyBusinessReportRepository.findByReportDate(reportDate)
                .orElseThrow(() -> new RuntimeException("找不到當日的業務報表，無法記錄退款"));

        int itemCount = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        BigDecimal refundAmount = order.getTotalAmount();

        // 1. 增加總退貨件數和總退款金額 (僅線上)
        report.setRefundCount(report.getRefundCount() + itemCount);
        report.setRefundAmount(report.getRefundAmount().add(refundAmount));

        dailyBusinessReportRepository.save(report);
    }

    /**
     * 更新【門店】每日業務報告 (記錄線下訂單取消/退款)
     * 核心原則：減少門店當日 GMV 和訂單數，增加門店當日退款額。
     */
    @Transactional
    public void updateStoreDailyReportForCancellation(Order order) {
        // 【核心判斷】：如果不是線下訂單，或者沒有關聯門店，則不處理
        if (!"OFFLINE".equals(order.getOrderSource()) || order.getOfflineStore() == null) {
            return;
        }

        OfflineStore store = order.getOfflineStore();
        // 使用訂單付款日期或創建日期作為報表日期
        LocalDate reportDate = order.getPaidAt() != null ? order.getPaidAt().toLocalDate() : order.getCreatedAt().toLocalDate();

        // 查找該門店當天的報表
        StoreDailyReport storeReport = storeDailyReportRepository.findByStore_StoreIdAndReportDate(store.getStoreId(), reportDate)
                .orElseThrow(() -> new RuntimeException("找不到該門店當日的業務報表，無法記錄退款"));

        int itemCount = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        BigDecimal refundAmount = order.getTotalAmount();

        // 1. 增加門店當日退款金額
        BigDecimal currentRefund = storeReport.getStoreRefundAmount() != null ? storeReport.getStoreRefundAmount() : BigDecimal.ZERO;
        storeReport.setStoreRefundAmount(currentRefund.add(refundAmount));

        // 2. 增加門店當日退貨件數
        Integer currentRefundCount = storeReport.getStoreRefundCount() != null ? storeReport.getStoreRefundCount() : 0;
        storeReport.setStoreRefundCount(currentRefundCount + itemCount);

        storeDailyReportRepository.save(storeReport);
    }

    @Transactional
    public void updateQuarterlySalesReportForCancellation(Order order) {
        int year = order.getCreatedAt().getYear();
        int quarter = (order.getCreatedAt().getMonthValue() - 1) / 3 + 1;

        // 【核心判斷】：確定銷售渠道和門店ID
        // 如果訂單是線下門店支付或自取，則為 OFFLINE，否則為 ONLINE
        boolean isOffline = "OFFLINE_STORE".equals(order.getPaymentMethod()) ||
                "STORE_PICKUP".equals(order.getDeliveryMethod());
        String salesChannel = isOffline ? "OFFLINE" : "ONLINE";
        Long storeId = isOffline && order.getOfflineStore() != null ? order.getOfflineStore().getStoreId() : null;

        for (OrderItem item : order.getItems()) {
            // 【核心修改】：調用新的 Repository 方法，傳入 channel 和 storeId
            QuarterlySalesReport report = quarterlySalesReportRepository
                    .findByYearAndQuarterAndProductIdAndUnitPriceAndSalesChannelAndStoreId(
                            year,
                            quarter,
                            item.getProduct().getProductId(),
                            item.getPrice(),
                            salesChannel,
                            storeId
                    )
                    .orElseThrow(() -> new RuntimeException("找不到對應的季度報表記錄，無法記錄退貨"));

            // 1. 增加該單價下的退貨數量
            Integer currentRefundQty = report.getRefundQuantity() != null ? report.getRefundQuantity() : 0;
            report.setRefundQuantity(currentRefundQty + item.getQuantity());

            // 2. 增加該單價下的退貨總金額
            BigDecimal itemRefundAmount = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal currentRefundAmt = report.getRefundAmount() != null ? report.getRefundAmount() : BigDecimal.ZERO;
            report.setRefundAmount(currentRefundAmt.add(itemRefundAmount));

            quarterlySalesReportRepository.save(report);
        }
    }

    /**
     * 隱藏訂單 (用戶端的「刪除」操作，實際為軟刪除/隱藏)
     * 僅允許對已取消或已退貨的訂單執行此操作
     */
    @Transactional
    public void hideOrder(String orderNo, String username) {
        // 1. 查找訂單並校驗權限
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權操作"));

        // 2. 核心校驗：只允許隱藏「已取消」或「已退貨」狀態的訂單
        if (order.getStatus() != Order.OrderStatus.CANCELLED && !"RETURNED".equals(order.getStatus().name())) {
            throw new RuntimeException("只能隱藏已取消或已退貨的訂單");
        }

        // 3.  設置為不可見 (軟刪除)
        order.setIsVisible(false);

        // 4. 保存更新到數據庫
        orderRepository.save(order);
    }

    @Transactional
    public void confirmPickup(String orderNo, String currentUserUsername) {
        // 1. 獲取當前用戶實體
        User currentUser = userRepository.findByUsername(currentUserUsername)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 2. 查找訂單
        Order order;

        // 【核心修改】：判斷權限邏輯
        // A. 如果是 ADMIN，可以操作所有訂單
        // B. 如果是 SALES 或 STORE_MANAGER，需要檢查他們是否屬於該訂單關聯的門店
        // C. 否則，只能操作自己的訂單

        boolean isGlobalAdmin = currentUser.getRole() == User.Role.ADMIN;

        if (isGlobalAdmin) {
            // 管理員：只校驗訂單號是否存在
            order = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new RuntimeException("訂單不存在"));
        } else {
            // 非管理員：先查出訂單（不帶用戶限制），然後進行細緻的權限校驗
            order = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new RuntimeException("訂單不存在"));

            boolean isAuthorized = false;

            // 情況 1：是訂單的主人（買家）
            if (order.getUser().getUsername().equals(currentUserUsername)) {
                isAuthorized = true;
            }
            // 情況 2：是該訂單關聯門店的員工 (SALES 或 STORE_MANAGER)
            else if (order.getOfflineStore() != null &&
                    (currentUser.getRole() == User.Role.SALES || currentUser.getRole() == User.Role.STORE_MANAGER)) {

                // 通過 Staff 表查詢該用戶是否隸屬於此門店
                Optional<Staff> staffOpt = staffRepository.findByUser(currentUser);
                if (staffOpt.isPresent()) {
                    Staff staff = staffOpt.get();
                    // 檢查員工所屬門店是否與訂單門店一致
                    if (staff.getStore() != null &&
                            staff.getStore().getStoreId().equals(order.getOfflineStore().getStoreId())) {
                        isAuthorized = true;
                    }
                }
            }

            if (!isAuthorized) {
                throw new RuntimeException("訂單不存在或無權操作此訂單");
            }
        }

        // 3. 核心校驗：必須是門店自取
        if (!"STORE_PICKUP".equals(order.getDeliveryMethod())) {
            throw new RuntimeException("此訂單非門店自取，無法執行取貨操作");
        }

        // 確保訂單關聯了門店，否則無法更新門店報表
        if (order.getOfflineStore() == null) {
            throw new RuntimeException("訂單未關聯門店，無法執行門店取貨邏輯");
        }

        // 4. 記錄實際收貨時間
        LocalDateTime now = LocalDateTime.now();
        order.setReceivedAt(now);

        // 標記是否需要更新報表（防止已付款訂單在取貨時重複計算）
        boolean shouldUpdateReport = false;

        // 5. 根據當前支付狀態執行不同邏輯
        if (order.getPaymentStatus() == Order.PaymentStatus.PENDING_OFFLINE) {
            // 情況 A：待線下付款 -> 變為已線下付款，訂單直接完成
            // 【關鍵】此時才真正產生銷售，需要更新報表
            order.setPaidAt(now);
            order.setPaymentStatus(Order.PaymentStatus.PAID_OFFLINE);

            order.setStatus(Order.OrderStatus.COMPLETED);
            shouldUpdateReport = true;

        } else if (order.getPaymentStatus() == Order.PaymentStatus.PAID_SIMULATED ||
                order.getPaymentStatus() == Order.PaymentStatus.PAID_REAL ||
                order.getPaymentStatus() == Order.PaymentStatus.PAID_OFFLINE) {
            // 情況 B：已付款狀態 -> 僅將訂單狀態改為已完成
            // 【關鍵】報表已在支付成功時記錄過，此處不可重複記錄！
            order.setStatus(Order.OrderStatus.COMPLETED);
            shouldUpdateReport = false;

        } else {
            throw new RuntimeException("當前訂單狀態不允許執行取貨操作");
        }

        // 6. 保存訂單狀態更新
        orderRepository.save(order);

        // 7. 【核心修改】如果需要，更新業務報表 (僅針對情況 A：待線下付款轉為已完成)
        if (shouldUpdateReport) {
            // 7.1 更新【門店】每日業務報表 (StoreDailyReport)
            OfflineStore store = order.getOfflineStore();
            LocalDate reportDate = now.toLocalDate(); // 使用當前日期作為報表日期

            // 查找或創建該門店當日的報表記錄
            StoreDailyReport storeReport = storeDailyReportRepository
                    .findByStore_StoreIdAndReportDate(store.getStoreId(), reportDate)
                    .orElseGet(() -> {
                        StoreDailyReport newReport = new StoreDailyReport();
                        newReport.setStore(store);
                        newReport.setReportDate(reportDate);
                        return newReport;
                    });

            // 累加門店 GMV
            BigDecimal currentGmv = storeReport.getStoreGmv() != null ? storeReport.getStoreGmv() : BigDecimal.ZERO;
            storeReport.setStoreGmv(currentGmv.add(order.getTotalAmount()));

            // 累加門店訂單數
            Integer currentOrders = storeReport.getStoreOrders() != null ? storeReport.getStoreOrders() : 0;
            storeReport.setStoreOrders(currentOrders + 1);

            // 保存門店報表
            storeDailyReportRepository.save(storeReport);


            // 7.2 更新季度銷售報表 (QuarterlySalesReport)
            Long storeId = store.getStoreId();

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                // 計算該商品的總成交額
                BigDecimal itemTotalAmount = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

                // 調用現有的 recordSale 方法，傳遞線下渠道和門店ID
                quarterlySalesReportService.recordSale(
                        product.getProductId(),
                        product.getDescription(), // productName 快照
                        product.getBrand(),       // brand 快照
                        product.getPrice(),       // unitPrice
                        item.getQuantity(),       // quantity
                        itemTotalAmount,          // totalAmount
                        "OFFLINE",                // salesChannel: 線下門店銷售
                        storeId                   // storeId: 具體門店ID
                );
            }
        }
    }

    @Transactional
    public void cancelExpiredOrder(String orderNo, String customMessage) {
        // 1. 查找訂單
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("訂單不存在"));

        // 2. 核心校驗：只允許取消「未付款」或「待線下付款」的訂單
        // 3. 更新訂單狀態為已取消
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        // 4. 【核心新增】：發送系統通知給買家
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            try {
                Notification notification = new Notification();
                notification.setRecipient(order.getUser()); // 訂單關聯的買家
                notification.setType(Notification.NotificationType.SYSTEM);
                notification.setTitle("⚠️ 訂單已因超時取消");
                notification.setContent(customMessage); // 使用管理員填寫的委婉文案
                notification.setRead(false);
                notificationRepository.save(notification);
            } catch (Exception e) {
                // 通知發送失敗不應影響訂單取消的主流程，記錄日誌即可
                System.err.println("發送取消訂單通知失敗: " + e.getMessage());
            }
        }
    }
}