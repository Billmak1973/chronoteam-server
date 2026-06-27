package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository; // 新增：直接獲取用戶實體
    private final OrderItemRepository orderItemRepository;

    /**
     * 1. 創建訂單 (移除庫存扣減，僅校驗庫存是否充足)
     */
    @Transactional
    public Order createOrder(String username) {
        List<Cart> cartItems = cartRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("購物車是空的，無法創建訂單");
        }

        BigDecimal totalAmount = cartItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Order order = new Order();
        order.setOrderNo("ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setCustomer(customer);
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

        //cartRepository.deleteByCustomer_Username(username);
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
     * 2. 線上模擬支付處理 (支付成功後扣減庫存)
     */
    @Transactional
    public Order simulatePayment(String orderNo, String username, BigDecimal payAmount) {
        Order order = orderRepository.findByOrderNoAndCustomer_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權操作此訂單"));

        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
            throw new RuntimeException("訂單狀態異常，無法重複支付。當前狀態: " + order.getPaymentStatus());
        }

        if (order.getTotalAmount().compareTo(payAmount) != 0) {
            throw new RuntimeException("支付金額不匹配，可能存在安全風險！");
        }

        order.setPaymentStatus(Order.PaymentStatus.PAID_SIMULATED);
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        //  【修改處 2】：線上支付成功，真正扣減庫存！
        deductStock(savedOrder);

        return savedOrder;
    }

    /**
     * 3. 獲取訂單詳情 (用於結帳頁面展示)
     */
    @Transactional(readOnly = true)
    public Order getOrderByOrderNoAndUsername(String orderNo, String username) {
        return orderRepository.findByOrderNoAndCustomer_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權查看"));
    }

    /**
     * 獲取用戶的所有訂單
     */
    public List<Order> getUserOrders(String username) {
        return orderRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);
    }

    /**
     * 4. 處理線下支付邏輯 (確認線下支付訂單後扣減庫存)
     */
    @Transactional
    public Order processOfflinePayment(String orderNo, String username, String storeId) {
        Order order = orderRepository.findByOrderNoAndCustomer_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權操作此訂單"));

        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
            throw new RuntimeException("訂單狀態異常，無法更改支付方式。當前狀態: " + order.getPaymentStatus());
        }

        order.setPaymentStatus(Order.PaymentStatus.PENDING_OFFLINE);
        order.setPaymentMethod("OFFLINE_STORE");
        order.setOfflineStoreId(storeId);
        Order savedOrder = orderRepository.save(order);

        // 【修改處 3】：線下支付確認生成訂單後，扣減庫存。
        // (註：由於前端流程在此直接跳轉成功頁，此處視為「確認支付」並扣減。
        // 若未來有「後台店員確認收款」的功能，應將此行移至後台確認收款的 API 中)
        deductStock(savedOrder);

        return savedOrder;
    }
}