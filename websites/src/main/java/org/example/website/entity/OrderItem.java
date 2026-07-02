package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
@Data
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long orderItemId;
    // 並將數據庫列名統一規範為 order_item_id (與表名對應)
    @Column(name = "order_item_id")
    private Long orderItemId;

    // 【核心解答】：不要改成 Long orderId！
    // 在 JPA 中，@ManyToOne 映射的是“對象關係”，變量名代表“關聯的對象本身”。
    // @JoinColumn(name = "order_id") 已經負責在數據庫層面生成名為 order_id 的外鍵列了。
    // 保持 Order order 可以讓你在代碼裡直接調用 orderItem.getOrder().getOrderNo()。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 同理，保持 Product product，不要改成 Long productId
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price", nullable = false)
    private BigDecimal price;
}