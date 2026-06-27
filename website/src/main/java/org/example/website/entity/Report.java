package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "report", indexes = {
        @Index(name = "idx_report_reporter", columnList = "reporter_username"),
        @Index(name = "idx_report_reported", columnList = "reported_username"),
        @Index(name = "idx_report_status", columnList = "status")
})
@Data
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_username", nullable = false, length = 50)
    private String reporterUsername; // 舉報人

    @Column(name = "reported_username", nullable = false, length = 50)
    private String reportedUsername; // 被舉報人

    @Column(name = "review_id")
    private Long reviewId; // 關聯的評論ID

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType; // 舉報對象類型: REVIEW(評論), USER(用戶)

    @Column(name = "category", nullable = false, length = 50)
    private String category; // 舉報分類: SPAM, HARASSMENT, INAPPROPRIATE, OTHER

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason; // 舉報詳細原因

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "admin_remark", columnDefinition = "TEXT")
    private String adminRemark; // 管理員處理備註

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ReportStatus {
        PENDING,    // 待處理
        RESOLVED,   // 已處理(如刪除評論/封禁用戶)
        DISMISSED   // 已駁回(舉報不成立)
    }
}