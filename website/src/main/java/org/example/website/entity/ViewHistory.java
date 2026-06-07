package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "view_history", indexes = {
        @Index(name = "idx_vh_username", columnList = "cust_username"),
        @Index(name = "idx_vh_viewed_at", columnList = "viewed_at")
})
@Data
public class ViewHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vh_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cust_username", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Product product;

    @CreationTimestamp
    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}