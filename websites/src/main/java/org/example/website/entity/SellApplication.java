package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sell_application", indexes = {
        // 從 @Index(name = "idx_sell_username", columnList = "cust_username") 改成 @Index(name = "idx_sell_user", columnList = "user_id")
        @Index(name = "idx_sell_user", columnList = "user_id"),
        @Index(name = "idx_sell_status", columnList = "status")
})
@Data
public class SellApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 @Column(name = "sell_id") private Long id; 改成 @Column(name = "application_id") private Long applicationId;
    @Column(name = "application_id")
    private Long applicationId;

    // 從 @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false) private Customer customer; 改成 @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    // 手錶基礎資訊
    @Column(name = "brand", length = 50, nullable = false)
    private String brand;

    @Column(name = "series", length = 100, nullable = false)
    private String series;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "purchase_year")
    private Integer purchaseYear;

    // 附件狀態（JSON 格式存儲）
    @Column(name = "accessories", columnDefinition = "TEXT")
    private String accessories;

    // 成色與狀況
    @Column(name = "watch_condition", length = 20)
    private String condition;

    @Column(name = "function_status", length = 50)
    private String functionStatus;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // 圖片路徑（JSON 格式存儲）
    @Column(name = "image_paths", columnDefinition = "TEXT")
    private String imagePaths;

    // 交易模式
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_mode", length = 20, nullable = false)
    private TransactionMode transactionMode;

    // 申請狀態
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    // 估價資訊
    @Column(name = "estimated_price")
    private BigDecimal estimatedPrice;

    @Column(name = "final_price")
    private BigDecimal finalPrice;

    // 鑑定備註
    @Column(name = "appraisal_notes", columnDefinition = "TEXT")
    private String appraisalNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private BigDecimal displayPrice;

    @Transient
    private BigDecimal serviceFee;

    @Transient
    private BigDecimal profit;

    @Transient
    private String statusText;

    @Transient
    private String statusClass;

    // 枚舉：交易模式
    public enum TransactionMode {
        BUYOUT,
        CONSIGNMENT
    }

    // 枚舉：申請狀態
    public enum ApplicationStatus {
        PENDING,
        APPRAISING,
        QUOTED,
        ACCEPTED,
        REJECTED,
        COMPLETED,
        CANCELLED
    }
}