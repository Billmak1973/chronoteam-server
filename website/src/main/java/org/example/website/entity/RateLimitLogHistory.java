package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rate_limit_log_history")
public class RateLimitLogHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;

    @Column(name = "times", nullable = false)
    private Integer times;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;

    @Column(name = "unbanned_at")
    private LocalDateTime unbannedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
