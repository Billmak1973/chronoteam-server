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
        @Index(name = "idx_sell_username", columnList = "cust_username"),
        @Index(name = "idx_sell_status", columnList = "status")
})
@Data
public class SellApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sell_id")
    private Long id;

    // 關聯用戶
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

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

    // 🟢 【新增】：Transient 字段，不會映射到數據庫，僅用於前端展示計算後的數據
    @Transient
    private BigDecimal displayPrice; // 展示用的價格

    @Transient
    private BigDecimal serviceFee;   // 平台服務費

    @Transient
    private BigDecimal profit;       // 賣家預估收益

    @Transient
    private String statusText;       // 狀態中文文本

    @Transient
    private String statusClass;      // 狀態對應的 CSS 類名

    // 枚舉：交易模式
    public enum TransactionMode {
        BUYOUT,           // 平台直接買斷
        CONSIGNMENT       // 平台代為寄售
    }

    // 枚舉：申請狀態
    public enum ApplicationStatus {
        PENDING,          // 待鑑定
        APPRAISING,       // 鑑定中
        QUOTED,           // 已報價
        ACCEPTED,         // 賣家接受報價
        REJECTED,         // 賣家拒絕報價
        COMPLETED,        // 交易完成
        CANCELLED         // 已取消
    }
}