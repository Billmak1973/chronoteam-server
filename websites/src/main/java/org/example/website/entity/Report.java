package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "report", indexes = {
        // 從 @Index(name = "idx_report_reporter", columnList = "reporter_username") 改成 @Index(name = "idx_report_reporter", columnList = "reporter_user_id")
        @Index(name = "idx_report_reporter", columnList = "reporter_user_id"),
        // 從 @Index(name = "idx_report_reported", columnList = "reported_username") 改成 @Index(name = "idx_report_reported", columnList = "reported_user_id")
        @Index(name = "idx_report_reported", columnList = "reported_user_id"),
        @Index(name = "idx_report_status", columnList = "status")
})
@Data
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long reportId; 並明確指定數據庫列名為 report_id
    @Column(name = "report_id")
    private Long reportId;

    // 從 @Column(name = "reporter_username", nullable = false, length = 50) private String reporterUsername;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 reporter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id",  nullable = false)
    private User reporter;

    // 從 @Column(name = "reported_username", nullable = false, length = 50) private String reportedUsername;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 reportedUser
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id",  nullable = false)
    private User reportedUser;

    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "admin_remark", columnDefinition = "TEXT")
    private String adminRemark;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ReportStatus {
        PENDING,    // 待處理
        RESOLVED,   // 已處理(如刪除評論/封禁用戶)
        DISMISSED   // 已駁回(舉報不成立)
    }
}