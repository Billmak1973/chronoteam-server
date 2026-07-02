package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cart", indexes = {
        // 從 @Index(name = "idx_cart_username", columnList = "cust_username") 改成 @Index(name = "idx_cart_user", columnList = "user_id")
        @Index(name = "idx_cart_user", columnList = "user_id"),
        // 從 @Index(name = "uk_user_product", columnList = "cust_username, prod_id", unique = true) 改成 @Index(name = "uk_user_product", columnList = "user_id, prod_id", unique = true)
        @Index(name = "uk_user_product", columnList = "user_id, prod_id", unique = true)
})
@Data
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    // 從 private Long id; 改成 private Long cartId;
    private Long cartId;

    // 從 @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false) private Customer customer;
    // 改成 @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", referencedColumnName = "prod_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(name = "cart_qty", nullable = false)
    private Integer quantity = 1;

    @Column(name = "cart_price", nullable = false)
    private BigDecimal price;

    @Column(name = "cart_order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "selected", nullable = false)
    private Boolean selected = true;

    // 新增：是否選擇快遞 (true: 需要快遞配送, false: 線下門店自提)
    @Column(name = "is_express_delivery", nullable = false)
    private Boolean isExpressDelivery = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}