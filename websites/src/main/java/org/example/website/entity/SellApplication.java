package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sell_application", indexes = {
        @Index(name = "idx_sell_user", columnList = "user_id"),
        @Index(name = "idx_sell_status", columnList = "status"),
        @Index(name = "idx_sell_appraiser", columnList = "appraiser_id"),
        @Index(name = "idx_sell_operator", columnList = "operator_id") // 新增：方便按操作員查詢
})
@Data
public class SellApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    // ================= 手錶基礎資訊 =================
    @Column(name = "brand", length = 50, nullable = false)
    private String brand;

    @Column(name = "series", length = 100, nullable = false)
    private String series;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "purchase_year")
    private Integer purchaseYear;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "accessories", columnDefinition = "TEXT")
    private String accessories;

    @Column(name = "watch_condition", length = 20)
    private String condition;

    @Column(name = "function_status", length = 50)
    private String functionStatus;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "image_paths", columnDefinition = "TEXT")
    private String imagePaths;

    // ================= 交易與狀態 =================
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_mode", length = 20, nullable = false)
    private TransactionMode transactionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    // ================= 估價與鑑定 =================
    @Column(name = "estimated_price")
    private BigDecimal estimatedPrice;

    @Column(name = "final_price")
    private BigDecimal finalPrice;

    @Column(name = "appraisal_notes", columnDefinition = "TEXT")
    private String appraisalNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appraiser_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User appraiser;

    // ================= 【核心新增】審計與操作記錄 =================
    /**
     * 最後操作/處理此申請的管理員或員工
     * 用於記錄是誰執行了狀態更新（例如：點擊「完成」或「拒絕」按鈕的人）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User operator;

    // ================= 線下預約交接資訊 (可選) =================
    @Column(name = "store_id", length = 50)
    private String storeId;

    @Column(name = "appointment_date")
    private LocalDate appointmentDate;

    @Column(name = "appointment_time_slot", length = 50)
    private String appointmentTimeSlot;

    @Column(name = "is_stocked_in", nullable = false)
    private Boolean isStockedIn = false;

    // ================= 時間戳 =================
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ================= 前端展示用 (不映射到數據庫) =================
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
        BUYOUT,      // 平台直接買斷
        CONSIGNMENT  // 平台代售 (寄賣)
    }

    // 枚舉：申請狀態
    public enum ApplicationStatus {
        PENDING,     // 待處理 / 待鑑定
        APPRAISING,  // 鑑定中
        QUOTED,      // 已報價 (等待用戶確認)
        ACCEPTED,    // 用戶已接受報價
        REJECTED,    // 用戶拒絕報價 或 平台拒收
        COMPLETED,   // 交易已完成 (款項已結清)
        CANCELLED    // 用戶主動取消
    }
}