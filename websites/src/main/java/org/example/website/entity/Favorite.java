package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "favorite", uniqueConstraints = {
        // 從 @UniqueConstraint(columnNames = {"cust_username", "prod_id"}) 改成 @UniqueConstraint(columnNames = {"user_id", "prod_id"})
        @UniqueConstraint(columnNames = {"user_id", "prod_id"})
})
@Data
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long favoriteId; 並明確指定數據庫列名為 favorite_id
    @Column(name = "favorite_id")
    private Long favoriteId;

    // 從 @JoinColumn(name = "cust_username", nullable = false) private Customer customer;
    // 改成 @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Product product;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}