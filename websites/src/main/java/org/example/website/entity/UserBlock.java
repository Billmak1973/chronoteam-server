package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_block",
        // 從 @UniqueConstraint(columnNames = {"blocker_username", "blocked_username"}) 改成 @UniqueConstraint(columnNames = {"blocker_user_id", "blocked_user_id"})
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_user_id", "blocked_user_id"}))
@Data
public class UserBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long blockId; 並明確指定數據庫列名為 block_id
    @Column(name = "block_id")
    private Long blockId;

    // 從 @Column(name = "blocker_username", nullable = false, length = 50) private String blockerUsername;
    // 改成 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "blocker_user_id", referencedColumnName = "id", nullable = false) private User blocker;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_user_id", nullable = false)
    private User blocker;

    // 從 @Column(name = "blocked_username", nullable = false, length = 50) private String blockedUsername;
    // 改成 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "blocked_user_id", referencedColumnName = "id", nullable = false) private User blockedUser;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}