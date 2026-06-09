package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.Cart;
import org.example.website.entity.Customer;
import org.example.website.entity.Order;
import org.example.website.entity.Product;
import org.example.website.repository.CartRepository;
import org.example.website.repository.CustomerRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.repository.ProductRepository;
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

    /**
     * 1. 創建訂單並鎖定庫存
     */
    @Transactional
    public Order createOrder(String username) {
        // 獲取用戶購物車
        List<Cart> cartItems = cartRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("購物車是空的，無法創建訂單");
        }

        // 計算總金額
        BigDecimal totalAmount = cartItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 獲取當前用戶實體 (避免依賴 Cart 的懶加載，更加安全)
        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 創建訂單實體
        Order order = new Order();
        // 使用 UUID 生成更安全的訂單號，防止併發重複
        order.setOrderNo("ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setCustomer(customer);
        order.setTotalAmount(totalAmount);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        order.setPaymentMethod("PAYPAL_SIM"); // 模擬支付方式

        // 校驗並扣減庫存
        for (Cart item : cartItems) {
            Product product = item.getProduct();
            if (product.getStock() < item.getQuantity()) {
                throw new RuntimeException("商品 [" + product.getDescription() + "] 庫存不足，當前庫存: " + product.getStock());
            }
            // 扣減庫存
            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
        }

        // 保存訂單
        Order savedOrder = orderRepository.save(order);

        // 訂單生成後，清空購物車 (防止重複下單)
        cartRepository.deleteByCustomer_Username(username);

        return savedOrder;
        // 💡 進階提示：真實場景中，若用戶未支付，應有定時任務在 15 分鐘後取消訂單並回滾庫存。
    }

    /**
     * 2. 模擬支付處理 (核心校驗)
     */
    @Transactional
    public Order simulatePayment(String orderNo, String username, BigDecimal payAmount) {
        // 校驗 1: 訂單是否存在且屬於當前用戶 (防越權)
        Order order = orderRepository.findByOrderNoAndCustomer_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或您無權操作此訂單"));

        // 校驗 2: 訂單狀態必須是未支付 (防重複支付/冪等性)
        if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
            throw new RuntimeException("訂單狀態異常，無法重複支付。當前狀態: " + order.getPaymentStatus());
        }

        // 校驗 3: 支付金額必須與訂單總金額完全一致 (防篡改金額攻擊)
        if (order.getTotalAmount().compareTo(payAmount) != 0) {
            throw new RuntimeException("支付金額不匹配，可能存在安全風險！訂單金額: " + order.getTotalAmount() + ", 嘗試支付: " + payAmount);
        }

        // 校驗通過，更新狀態
        order.setPaymentStatus(Order.PaymentStatus.PAID_SIMULATED);
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        return orderRepository.save(order);
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
}