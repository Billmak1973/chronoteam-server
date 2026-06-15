package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rate_limit_log")
public class RateLimitLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;  // 1分钟窗口的开始时间

    @Column(name = "times", nullable = false)
    private Integer times = 1;  // 1分钟内的操作次数，默认1

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;  // 最后更新时间

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;  // 封禁结束时间（如果被封禁）

    // Getters and Setters (Lombok @Data 已自动生成)
}