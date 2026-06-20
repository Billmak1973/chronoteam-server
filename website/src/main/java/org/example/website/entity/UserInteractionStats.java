package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_interaction_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"username", "type"}))
@Data
public class UserInteractionStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    // 定義枚舉或常量來區分類型，方便擴展
    @Column(name = "type", nullable = false, length = 20)
    private String type; // 例如: "REVIEW_REPLY", "REVIEW_MENTION"

    @Column(name = "last_viewed_at", nullable = false)
    private LocalDateTime lastViewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}