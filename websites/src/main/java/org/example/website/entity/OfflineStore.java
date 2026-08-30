package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "offline_stores")
@Data
public class OfflineStore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "store_code", nullable = false, unique = true, length = 50)
    private String storeCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "phone", length = 50)
    private String phone;

    // ==========================================
    // 【營業時間相關欄位】
    // ==========================================

    /**
     * 營業時間設置模式
     * "UNIFIED": 一周統一營業時間
     * "DAILY": 每天分別設置營業時間
     */
    @Column(name = "schedule_mode", length = 20)
    private String scheduleMode;

    /**
     * 統一營業時間
     * 例如："10:00 AM - 06:00 PM"
     */
    @Column(name = "hours", length = 100)
    private String hours;

    /**
     * 每天分別設置的營業時間
     * 存儲為 JSON 格式字符串。
     * 例如: {"1":{"start":"10:00 AM","end":"06:00 PM","closed":false}, "2":{"closed":true}, ...}
     */
    @Column(name = "daily_hours", columnDefinition = "TEXT")
    private String dailyHours;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true; // 控制前台結帳時是否顯示該店鋪

    // ==========================================
    // 【暫停營業相關欄位】(通用)
    // ==========================================
    @Column(name = "closed_start_date")
    private LocalDate closedStartDate;

    @Column(name = "closed_end_date")
    private LocalDate closedEndDate;

    @Column(name = "closed_reason", length = 255)
    private String closedReason;

    // ==========================================
    // 【退貨預約相關欄位】
    // ==========================================
    @Column(name = "return_advance_days")
    private Integer returnAdvanceDays;

    @Column(name = "return_blackout_start_date")
    private LocalDate returnBlackoutStartDate;

    @Column(name = "return_blackout_end_date")
    private LocalDate returnBlackoutEndDate;

    @Column(name = "return_blackout_reason", length = 255)
    private String returnBlackoutReason;

    @Column(name = "return_closed_days_of_week", length = 20)
    private String returnClosedDaysOfWeek;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}