package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "announcement_receipts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"announcement_id", "user_id"})
}, indexes = {
        @Index(name = "idx_receipt_user_read", columnList = "user_id, is_read")
})
@Data
public class AnnouncementReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "announcement_receipt_id")
    private Long announcementReceiptId;

    // 關聯公告主表 (一對多)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    // 關聯用戶表
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}