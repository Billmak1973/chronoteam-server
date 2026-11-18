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
     * 2. 線上模擬支付處理 (支付成功後扣減庫存，並正確處理運費)
     * 根據配送方式決定扣減線上總倉庫存還是線下門店庫存
     */
    @Transactional
    public Order simulatePayment(String orderNo, String username, BigDecimal payAmount,
                                 String deliveryMethod, Long storeId,
                                 LocalDate customerSelectedDeliveryDate,
                                 LocalDate appointmentDate,
                                 Long addressId) { // <--- 新增 addressId 參數

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

        // 獲取純商品總價 (創建訂單時存入的 totalAmount 就是純商品總價，這裡重新計算以防萬一，或者直接使用 order.getTotalAmount() 如果之前沒加運費)
        // 注意：createOrder 時 totalAmount 是純商品價。simulatePayment 時需要加上運費才是最終支付金額。
        BigDecimal subtotal = order.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalTotal = subtotal;

        // 3. 重新計算真實的應付總價 (包含運費邏輯)
        boolean isExpress = "EXPRESS".equals(deliveryMethod);
        if (isExpress && subtotal.compareTo(realThreshold) < 0) {
            finalTotal = finalTotal.add(realShippingFee);
        }

        // 4. 核心安全校驗：比對前端傳來的金額與後端計算的真實總價是否一致
        if (payAmount.compareTo(finalTotal) != 0) {
            throw new RuntimeException("安全警告：訂單金額與後端計算不符 (" + payAmount + " vs " + finalTotal + ")，可能存在篡改行為！");
        }

        // 5. 更新訂單的基本信息
        order.setTotalAmount(finalTotal);
        order.setShippingFee(isExpress && subtotal.compareTo(realThreshold) < 0 ? realShippingFee : BigDecimal.ZERO);
        order.setDeliveryMethod(deliveryMethod);
        order.setDelivery(isExpress); // 如果是快遞配送，delivery=true；門店自取=false

        // 6. 處理地址與門店關聯
        if ("STORE_PICKUP".equals(deliveryMethod)) {
            if (storeId == null) {
                throw new RuntimeException("門店自取必須選擇門店");
            }
            OfflineStore store = offlineStoreRepository.findById(storeId)
                    .orElseThrow(() -> new RuntimeException("門店不存在"));
            order.setOfflineStore(store);
            order.setOrderSource("OFFLINE"); // 標記為線下業績

            if (appointmentDate != null) {
                order.setAppointmentDate(appointmentDate);
            }
        } else if (isExpress) {
            order.setOrderSource("ONLINE"); // 標記為線上業績
            if (addressId != null) {
                UserAddress userAddress = userAddressRepository.findById(addressId)
                        .orElseThrow(() -> new RuntimeException("收貨地址不存在"));
                // 安全校驗：確保地址屬於當前用戶
                if (!userAddress.getUser().getUsername().equals(username)) {
                    throw new RuntimeException("無權使用此收貨地址");
                }
                order.setUserAddress(userAddress);
            }
        }

        // 7. 【核心修復】設置預計送達日期
        if (customerSelectedDeliveryDate != null && isExpress) {
            order.setEstimatedDeliveryDate(customerSelectedDeliveryDate);
        }

        // 8. ==========================================
        //    【最关键步骤】：支付成功，立即扣減庫存！
        //    根據配送方式決定扣減哪裡的庫存
        // ==========================================
        deductStockUponPayment(order, deliveryMethod, storeId);

        // 9. 更新支付狀態
        order.setPaymentStatus(Order.PaymentStatus.PAID_SIMULATED);
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // 10. 記錄銷售報表數據 (保持原有邏輯，但需根據 orderSource 區分)
        recordSalesReports(savedOrder);

        return savedOrder;
    }

    /**
     * 【新增輔助方法】：根據配送方式扣減對應庫存
     * - EXPRESS: 扣減 Product.stock (線上總倉)
     * - STORE_PICKUP: 扣減 StoreInventory.quantity (特定門店)
     */
    private void deductStockUponPayment(Order order, String deliveryMethod, Long storeId) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            int qty = item.getQuantity();

            if ("STORE_PICKUP".equals(deliveryMethod)) {
                // === 情況 A：線下門店自取 ===
                if (storeId == null) {
                    throw new RuntimeException("系統錯誤：門店自取訂單未關聯門店ID");
                }

                // 使用原子更新語句扣減門店庫存
                // deductStoreStock 返回受影響行數，若為 0 則表示庫存不足或記錄不存在
                int updatedRows = storeInventoryRepository.deductStoreStock(storeId, product.getProductId(), qty);

                if (updatedRows == 0) {
                    // 如果扣減失敗，拋出異常，事務會回滾，訂單不會變成已支付
                    throw new RuntimeException("支付失敗：門店 [" + order.getOfflineStore().getName() + "] 的商品 [" + product.getDescription() + "] 庫存不足！");
                }

            } else {
                // === 情況 B：線上快遞配送 ===
                // 二次校驗防超賣 (雖然創建訂單時校驗過，但支付瞬間可能庫存變動)
                if (product.getStock() < qty) {
                    throw new RuntimeException("支付失敗：線上商品 [" + product.getDescription() + "] 庫存不足！");
                }

                // 扣減線上總倉庫存
                product.setStock(product.getStock() - qty);
                productRepository.save(product);
            }
        }
    }

    /**
     * 【新增輔助方法】：記錄銷售報表
     */
    private void recordSalesReports(Order order) {
        boolean isStorePickup = "STORE_PICKUP".equals(order.getDeliveryMethod()) && order.getOfflineStore() != null;
        Long targetStoreId = isStorePickup ? order.getOfflineStore().getStoreId() : null;

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            BigDecimal itemTotalAmount = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

            if (isStorePickup) {
                // 線下業績：更新門店日報表 + 季度報表(渠道OFFLINE)
                updateStoreDailyReportForPickup(order, targetStoreId);
                quarterlySalesReportService.recordSale(
                        product.getProductId(),
                        product.getDescription(),
                        product.getBrand(),
                        product.getPrice(),
                        item.getQuantity(),
                        itemTotalAmount,
                        "OFFLINE",
                        targetStoreId
                );
            } else {
                // 線上業績：更新全局日報表 + 季度報表(渠道ONLINE)
                dailyBusinessReportService.updateDailyReport(order);
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
    }

    /**
     * 更新門店每日業務報表 (針對線上支付線下取貨場景)
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
     * 4. 處理線下支付邏輯 (確認線下支付訂單後【預留】庫存，而非扣減)
     */
    @Transactional
    public Order processOfflinePayment(String orderNo, String username, Long storeId, String deliveryMethod, LocalDate appointmentDate) {
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權操作此訂單"));

        OfflineStore store = offlineStoreRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("門店不存在"));

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

        Order savedOrder = orderRepository.save(order);

        // 【核心修改】：線下預訂生成訂單後，【預留】庫存，而不是扣減庫存！
        reserveOfflineStock(savedOrder);

        return savedOrder;
    }

    /**
     * 線下門店訂單預訂後的庫存預留 (增加 reserved_quantity)
     */
    private void reserveOfflineStock(Order order) {
        OfflineStore store = order.getOfflineStore();
        if (store == null) {
            throw new RuntimeException("線下訂單未關聯門店，無法預留庫存");
        }

        for (OrderItem item : order.getItems()) {
            Integer productId = item.getProduct().getProductId();
            int qty = item.getQuantity();

            // 使用原子更新語句增加預留數量
            // 如果返回 0，說明可售庫存不足 (quantity - reservedQuantity < qty)
            int updatedRows = storeInventoryRepository.reserveStoreStock(store.getStoreId(), productId, qty);

            if (updatedRows == 0) {
                // 【重要】：如果預留失敗，應該回滾訂單狀態或拋出異常告知用戶庫存不足
                // 由於這裡是在 @Transactional 方法中，拋出異常會自動回滾訂單保存操作
                throw new RuntimeException("預訂失敗：門店 [" + store.getName() + "] 的商品 [" + item.getProduct().getDescription() + "] 可售庫存不足！");
            }
        }
    }

    /**
     * 刪除訂單 (支援普通待付款與待線下付款兩種模式)
     */
    @Transactional
    public void deleteOrder(String orderNo, String username, String type) {
        // 1. 查找訂單並校驗權限
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權操作"));

        // 2. 狀態校驗
        if ("pending-offline".equals(type)) {
            if (order.getPaymentStatus() != Order.PaymentStatus.PENDING_OFFLINE) {
                throw new RuntimeException("只有「待線下付款」狀態的訂單才能以此方式刪除");
            }
            // 【核心邏輯】釋放門店預留庫存
            releaseOfflineStoreReservedStock(order);
        } else {
            // 默認 unpaid 邏輯
            if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
                throw new RuntimeException("只有「待付款」狀態的訂單才能刪除");
            }
        }

        // 3. 執行物理刪除 (級聯刪除 OrderItem)
        orderRepository.delete(order);
    }

    /**
     * 【私有輔助方法】釋放待線下付款訂單的門店預留庫存
     * 邏輯：orders.offline_store_id + order_items.prod_id -> store_inventory.store_id + product_id
     *       reserved_quantity -= order_items.quantity
     */
    private void releaseOfflineStoreReservedStock(Order order) {
        OfflineStore store = order.getOfflineStore();
        if (store == null) {
            throw new RuntimeException("待線下付款訂單未關聯門店，無法釋放庫存");
        }

        for (OrderItem item : order.getItems()) {
            Integer productId = item.getProduct().getProductId();
            int qtyToRelease = item.getQuantity();

            // 使用原子更新語句扣減 reserved_quantity
            // 注意：這裡假設你的 StoreInventoryRepository 有對應方法，如果沒有請添加
            int updatedRows = storeInventoryRepository.releaseReservedStock(
                    store.getStoreId(),
                    productId,
                    qtyToRelease
            );

            if (updatedRows == 0) {
                // 防禦性編程：如果沒更新到行，說明預留庫存可能已經被其他地方消耗或數據不一致
                System.err.println("⚠️ 警告：釋放訂單 [" + order.getOrderNo() + "] 的商品 [" + productId + "] 預留庫存失敗，當前預留數量可能不足。");
                // 根據業務需求，這裡可以選擇拋出異常終止刪除，或者僅記錄日誌繼續刪除
                // throw new RuntimeException("門店 [" + store.getName() + "] 的商品 [" + item.getProduct().getDescription() + "] 預留庫存釋放失敗！");
            }
        }
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

    /**
     * 確認門店取貨 (核心修改版：增加 PENDING_OFFLINE 狀態下的庫存精確扣減)
     */
    @Transactional
    public void confirmPickup(String orderNo, String currentUserUsername) {
        // 1. 獲取當前用戶實體
        User currentUser = userRepository.findByUsername(currentUserUsername)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 2. 查找訂單並進行權限校驗 (保持原有邏輯不變)
        Order order;
        boolean isGlobalAdmin = currentUser.getRole() == User.Role.ADMIN;

        if (isGlobalAdmin) {
            order = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new RuntimeException("訂單不存在"));
        } else {
            order = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new RuntimeException("訂單不存在"));

            boolean isAuthorized = false;
            if (order.getUser().getUsername().equals(currentUserUsername)) {
                isAuthorized = true;
            } else if (order.getOfflineStore() != null &&
                    (currentUser.getRole() == User.Role.SALES || currentUser.getRole() == User.Role.STORE_MANAGER)) {
                Optional<Staff> staffOpt = staffRepository.findByUser(currentUser);
                if (staffOpt.isPresent()) {
                    Staff staff = staffOpt.get();
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

        // 3. 核心校驗：必須是門店自取且關聯了門店
        if (!"STORE_PICKUP".equals(order.getDeliveryMethod())) {
            throw new RuntimeException("此訂單非門店自取，無法執行取貨操作");
        }
        if (order.getOfflineStore() == null) {
            throw new RuntimeException("訂單未關聯門店，無法執行門店取貨邏輯");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setReceivedAt(now);
        boolean shouldUpdateReport = false;

        // ==========================================
        // 【核心修改區】：根據支付狀態執行不同的庫存與財務邏輯
        // ==========================================
        if (order.getPaymentStatus() == Order.PaymentStatus.PENDING_OFFLINE) {
            // === 情況 A：待線下付款 -> 變為已線下付款，訂單直接完成 ===

            // 1. 更新訂單狀態與支付時間
            order.setPaidAt(now);
            order.setPaymentStatus(Order.PaymentStatus.PAID_OFFLINE);
            order.setStatus(Order.OrderStatus.COMPLETED);
            shouldUpdateReport = true;

            // 2. 【關鍵新增】執行門店庫存的「釋放預留 + 扣減物理」操作
            processOfflineStockDeduction(order);

        } else if (order.getPaymentStatus() == Order.PaymentStatus.PAID_SIMULATED ||
                order.getPaymentStatus() == Order.PaymentStatus.PAID_REAL ||
                order.getPaymentStatus() == Order.PaymentStatus.PAID_OFFLINE) {
            // === 情況 B：已付款狀態 -> 僅將訂單狀態改為已完成 ===
            // 注意：如果是 PAID_OFFLINE 且之前已經扣過庫存了，這裡就不應該再扣一次。
            // 如果你的業務中 PAID_OFFLINE 可能來自其他途徑且未扣庫存，請在此處也調用 processOfflineStockDeduction
            order.setStatus(Order.OrderStatus.COMPLETED);
            shouldUpdateReport = false;
        } else {
            throw new RuntimeException("當前訂單狀態不允許執行取貨操作");
        }

        // 4. 保存訂單狀態更新
        orderRepository.save(order);

        // 5. 如果需要，更新業務報表 (僅針對情況 A)
        if (shouldUpdateReport) {
            OfflineStore store = order.getOfflineStore();
            LocalDate reportDate = now.toLocalDate();

            // 5.1 更新【門店】每日業務報表
            StoreDailyReport storeReport = storeDailyReportRepository
                    .findByStore_StoreIdAndReportDate(store.getStoreId(), reportDate)
                    .orElseGet(() -> {
                        StoreDailyReport newReport = new StoreDailyReport();
                        newReport.setStore(store);
                        newReport.setReportDate(reportDate);
                        return newReport;
                    });

            BigDecimal currentGmv = storeReport.getStoreGmv() != null ? storeReport.getStoreGmv() : BigDecimal.ZERO;
            storeReport.setStoreGmv(currentGmv.add(order.getTotalAmount()));

            Integer currentOrders = storeReport.getStoreOrders() != null ? storeReport.getStoreOrders() : 0;
            storeReport.setStoreOrders(currentOrders + 1);
            storeDailyReportRepository.save(storeReport);

            // 5.2 更新季度銷售報表
            Long storeId = store.getStoreId();
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                BigDecimal itemTotalAmount = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

                quarterlySalesReportService.recordSale(
                        product.getProductId(),
                        product.getDescription(),
                        product.getBrand(),
                        product.getPrice(),
                        item.getQuantity(),
                        itemTotalAmount,
                        "OFFLINE",
                        storeId
                );
            }
        }
    }

    /**
     * 【新增私有輔助方法】處理門店取貨時的庫存扣減
     * 邏輯：quantity -= qty AND reserved_quantity -= qty
     * 使用原子更新防止併發超賣或預留數變成負數
     */
    private void processOfflineStockDeduction(Order order) {
        OfflineStore store = order.getOfflineStore();
        if (store == null) return;

        for (OrderItem item : order.getItems()) {
            Integer productId = item.getProduct().getProductId();
            int qty = item.getQuantity();

            // 使用 Repository 中的原子更新語句同時扣減兩個字段
            // SQL 邏輯: UPDATE store_inventory
            // SET quantity = quantity - :qty, reserved_quantity = reserved_quantity - :qty
            // WHERE store_id = :storeId AND product_id = :productId
            // AND quantity >= :qty AND reserved_quantity >= :qty
            int updatedRows = storeInventoryRepository.deductAndReleaseReservedStock(
                    store.getStoreId(),
                    productId,
                    qty
            );

            if (updatedRows == 0) {
                // 如果更新失敗，說明庫存不足或預留數量異常，拋出異常回滾整個事務
                throw new RuntimeException(
                        String.format("門店 [%s] 商品 [%s] 庫存或預留數量不足，無法完成取貨扣減！",
                                store.getName(), item.getProduct().getDescription())
                );
            }
        }
    }

    /**
     * 取消過期訂單 (支援普通待付款與待線下付款兩種模式)
     */
    @Transactional
    public void cancelExpiredOrder(String orderNo, String customMessage) {
        // 1. 查找訂單
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("訂單不存在"));

        // 2. 核心校驗：只允許取消「未付款」或「待線下付款」的訂單
        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID &&
                order.getPaymentStatus() != Order.PaymentStatus.PENDING_OFFLINE) {
            throw new RuntimeException("只有待付款或待線下付款的訂單才能執行取消操作");
        }

        // 3. 【核心新增】：如果是 PENDING_OFFLINE，必須先釋放預留庫存！
        if (order.getPaymentStatus() == Order.PaymentStatus.PENDING_OFFLINE) {
            OfflineStore store = order.getOfflineStore();
            if (store == null) {
                throw new RuntimeException("待線下付款訂單未關聯門店，無法釋放預留庫存");
            }

            for (OrderItem item : order.getItems()) {
                Integer productId = item.getProduct().getProductId();
                int qtyToRelease = item.getQuantity();

                // 使用原子更新語句釋放 reserved_quantity
                // SQL: UPDATE store_inventory SET reserved_quantity = reserved_quantity - :qty
                //      WHERE store_id = :storeId AND product_id = :productId AND reserved_quantity >= :qty
                int updatedRows = storeInventoryRepository.releaseReservedStock(
                        store.getStoreId(),
                        productId,
                        qtyToRelease
                );

                if (updatedRows == 0) {
                    // 防禦性編程：記錄警告日誌，但不阻斷取消流程
                    System.err.println("⚠️ 警告：釋放訂單 [" + order.getOrderNo() + "] 的商品 [" + productId + "] 預留庫存失敗，當前預留數量可能不足。");
                }
            }
        }

        // 4. 更新訂單狀態為已取消
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        // 5. 【核心新增】：發送系統通知給買家
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