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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository; // 新增：直接獲取用戶實體
    private final OrderItemRepository orderItemRepository;
    private final DailyBusinessReportService dailyBusinessReportService;
    private final SystemConfigRepository systemConfigRepository;
    private final QuarterlySalesReportService quarterlySalesReportService;
    private final QuarterlySalesReportRepository quarterlySalesReportRepository;
    private final DailyBusinessReportRepository dailyBusinessReportRepository;
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
     * 統一的庫存扣減方法 (防止代碼重複，並加入支付時的二次校驗防超賣)
     */
    private void deductStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            //  關鍵防護：支付時再次校驗庫存，防止並發情況下創建訂單後、支付前庫存被他人買走
            if (product.getStock() < item.getQuantity()) {
                throw new RuntimeException("支付失敗：商品 [" + product.getDescription() + "] 庫存不足，可能已被他人搶購，請取消訂單重試。");
            }
            // 真正執行扣減
            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
        }
    }

    /**
     * 2. 線上模擬支付處理 (支付成功後扣減庫存，並正確處理運費)
     */
    @Transactional
    public Order simulatePayment(String orderNo, String username, BigDecimal payAmount, String deliveryMethod,String storeId) {
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

        // 5.  核心安全校驗：比對前端傳來的金額與後端計算的真實總價是否一致
        if (payAmount.compareTo(realTotal) != 0) {
            throw new RuntimeException("安全警告：訂單金額與後端計算不符，可能存在篡改行為！");
        }


        // 6.  更新訂單的真實總價與運費記錄 (寫入數據庫)
        order.setTotalAmount(realTotal);
        order.setShippingFee(realTotal.compareTo(realSubtotal) > 0 ? realShippingFee : BigDecimal.ZERO);

        // 7. 【新增】設置配送方式和delivery字段
        order.setDeliveryMethod(deliveryMethod);
        order.setDelivery("EXPRESS".equals(deliveryMethod)); // 如果是快遞配送，delivery=true；門店自取=false

        //  如果是門店自取，且前端傳來了有效的 storeId，則保存到數據庫
        if ("STORE_PICKUP".equals(deliveryMethod) && storeId != null && !storeId.trim().isEmpty()) {
            order.setOfflineStoreId(storeId);
        }

        // 8. 【新增】設置發貨截止時間（當前時間 ）
        order.setDeadlineAt(LocalDateTime.now());

        // 7. 更新支付狀態
        order.setPaymentStatus(Order.PaymentStatus.PAID_SIMULATED);
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // 8. 線上支付成功，真正扣減庫存！
        deductStock(savedOrder);

        // ==========================================
        // 9. 記錄季度銷售報表數據
        // ==========================================
        for (OrderItem item : savedOrder.getItems()) {
            Product product = item.getProduct();
            BigDecimal itemTotalAmount = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

            // 調用記錄服務
            quarterlySalesReportService.recordSale(
                    product.getProductId(),
                    product.getDescription(), // 作為 productName 快照
                    product.getBrand(),       // 作為 brand 快照
                    product.getPrice(),       // unitPrice
                    item.getQuantity(),       // quantity
                    itemTotalAmount           // totalAmount
            );

        }
            // 10. 更新每日業務報表
        dailyBusinessReportService.updateDailyReport(savedOrder);

        return savedOrder;
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
    public Order processOfflinePayment(String orderNo, String username, String storeId,String deliveryMethod) {
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權操作此訂單"));

        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
            throw new RuntimeException("訂單狀態異常，無法更改支付方式。當前狀態: " + order.getPaymentStatus());
        }

        order.setPaymentStatus(Order.PaymentStatus.PENDING_OFFLINE);
        order.setPaymentMethod("OFFLINE_STORE");
        order.setOfflineStoreId(storeId);

        order.setDeliveryMethod("STORE_PICKUP");
        order.setDelivery(false); // 線下支付=門店自取，delivery=false

        // 設置發貨截止時間（當前時間 + 7天）
        order.setDeadlineAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // 【修改處 3】：線下支付確認生成訂單後，扣減庫存。
        // (註：由於前端流程在此直接跳轉成功頁，此處視為「確認支付」並扣減。
        // 若未來有「後台店員確認收款」的功能，應將此行移至後台確認收款的 API 中)
        deductStock(savedOrder);

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

        // 5. 更新每日業務報表 (記錄退款，不修改原始銷售數據)
        updateDailyBusinessReportForCancellation(order);

        // 6. 更新季度銷售報表 (記錄退貨，不修改原始銷售快照)
        updateQuarterlySalesReportForCancellation(order);
    }

    /**
     *  恢復庫存方法 (私有輔助方法)
     * 遍歷訂單中的商品，將賣出的數量加回對應商品的庫存中
     */
    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            // 將庫存加回
            product.setStock(product.getStock() + item.getQuantity());
            // 保存更新後的庫存
            productRepository.save(product);
        }
    }

    /**
     * 更新每日業務報告 (記錄退款)
     * 核心原則：totalGmv, totalOrders, totalItemsSold 保持不變，只增加 refundAmount 和 refundCount
     */
    @Transactional
    public void updateDailyBusinessReportForCancellation(Order order) {
        // 使用訂單創建日期或付款日期作為報表日期
        LocalDate reportDate = order.getCreatedAt().toLocalDate();

        DailyBusinessReport report = dailyBusinessReportRepository.findByReportDate(reportDate)
                .orElseThrow(() -> new RuntimeException("找不到當日的業務報表，無法記錄退款"));

        // 1. 增加退款總金額
        BigDecimal currentRefundAmount = report.getRefundAmount() != null ? report.getRefundAmount() : BigDecimal.ZERO;
        report.setRefundAmount(currentRefundAmount.add(order.getTotalAmount()));

        // 2. 增加退貨總件數
        int itemCount = order.getItems().stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
        Integer currentRefundCount = report.getRefundCount() != null ? report.getRefundCount() : 0;
        report.setRefundCount(currentRefundCount + itemCount);

        dailyBusinessReportRepository.save(report);
    }

    /**
     * 更新季度銷售報告 (記錄退貨)
     * 核心原則：quantity, totalAmount 保持不變，只增加 refundQuantity 和 refundAmount
     */
    @Transactional
    public void updateQuarterlySalesReportForCancellation(Order order) {
        int year = order.getCreatedAt().getYear();
        int quarter = (order.getCreatedAt().getMonthValue() - 1) / 3 + 1;

        for (OrderItem item : order.getItems()) {
            // 根據 年份、季度、商品ID、單價 查找唯一的歷史快照記錄
            QuarterlySalesReport report = quarterlySalesReportRepository
                    .findByYearAndQuarterAndProductIdAndUnitPrice(
                            year,
                            quarter,
                            item.getProduct().getProductId(),
                            item.getPrice()
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
}