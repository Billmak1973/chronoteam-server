package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_penalty", indexes = {
        // 從 @Index(name = "idx_penalty_username", columnList = "target_username") 改成 @Index(name = "idx_penalty_user", columnList = "target_user_id")
        @Index(name = "idx_penalty_user", columnList = "target_user_id"),
        @Index(name = "idx_penalty_status", columnList = "status")
})
@Data
public class AdminPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long penaltyId; 並明確指定數據庫列名為 penalty_id
    @Column(name = "penalty_id")
    private Long penaltyId;

    // 從 @Column(name = "target_username", nullable = false, length = 50) private String targetUsername;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 targetUser
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    // 從 @Column(name = "admin_username", nullable = false, length = 50) private String adminUsername;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 adminUser
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

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

    @Column(name = "notification_id")
    private Long notificationId;

    // 新增：關聯的申訴 ID（可選）
    @Column(name = "appeal_id")
    private Long appealId;

    public enum PenaltyType {
        BAN,         // 封禁 (有期限)
        BLACKLIST    // 拉黑 (永久)
    }

    public enum PenaltyStatus {
        ACTIVE,      // 生效中 (包含永久拉黑，以及未到期的封禁)
        EXPIRED,     // 已過期/已完成 (封禁時間已到，系統自動釋放)
        REVOKED      // 已撤銷
    }
}