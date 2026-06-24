package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_penalty", indexes = {
        @Index(name = "idx_penalty_username", columnList = "target_username"),
        @Index(name = "idx_penalty_status", columnList = "status")
})
@Data
public class AdminPenalty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_username", nullable = false, length = 50)
    private String targetUsername;

    @Column(name = "admin_username", nullable = false, length = 50)
    private String adminUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 20)
    private PenaltyType type;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    // 結束時間 (拉黑為 null 表示永久，封禁有具體時間)
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PenaltyStatus status = PenaltyStatus.ACTIVE;

    public enum PenaltyType {
        BAN,         // 封禁 (有期限)
        BLACKLIST    // 拉黑 (永久)
    }

    public enum PenaltyStatus {
        ACTIVE,      // 生效中 (包含永久拉黑，以及未到期的封禁)
        REVOKED,     // 已提前解除 (管理員手動原諒)
        EXPIRED      // 已過期/已完成 (封禁時間已到，系統自動釋放)
    }
}