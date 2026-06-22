package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_block",
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_username", "blocked_username"}))
@Data
public class UserBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_username", nullable = false, length = 50)
    private String blockerUsername;  // 谁禁言的

    @Column(name = "blocked_username", nullable = false, length = 50)
    private String blockedUsername;  // 被禁言的人

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;  // 可选：到期时间
}