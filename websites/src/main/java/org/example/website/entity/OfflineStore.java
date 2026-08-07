package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

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
    private String storeCode; // 唯一標識，如 "store-central" (結帳系統依賴此代碼)

    @Column(name = "name", nullable = false, length = 100)
    private String name;      // 店鋪名稱

    @Column(name = "address", nullable = false, length = 255)
    private String address;   // 地址

    @Column(name = "phone", length = 50)
    private String phone;     // 電話

    @Column(name = "hours", length = 100)
    private String hours;     // 營業時間

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true; // 控制前台結帳時是否顯示該店鋪

    /**
     * 退貨預約最早提前天數
     * 例如：設置為 3，表示用戶必須至少提前 3 天預約才能到店退貨。
     * (若用戶 8月5日 申請，最早只能預約 8月8日 到店)
     * 若為 null 或 0，則表示無限制或當天可預約。
     */
    @Column(name = "return_advance_days")
    private Integer returnAdvanceDays;

    /**
     * 暫停退貨的開始日期 (包含)
     * 例如：2026-02-01 (農曆新年期間)
     * 若為 null，表示沒有設置暫停退貨時段。
     */
    @Column(name = "return_blackout_start_date")
    private LocalDate returnBlackoutStartDate;

    /**
     * 暫停退貨的結束日期 (包含)
     * 例如：2026-02-15
     * 若為 null，表示沒有設置暫停退貨時段。
     */
    @Column(name = "return_blackout_end_date")
    private LocalDate returnBlackoutEndDate;

    /**
     * 暫停退貨的原因備註 (選填)
     * 例如："農曆新年期間暫停退貨服務" 或 "店鋪年度盤點"
     * 用於在前端提示用戶，提升用戶體驗。
     */
    @Column(name = "return_blackout_reason", length = 255)
    private String returnBlackoutReason;

    // ==========================================
    // 【新增欄位】：每週固定不處理退貨的星期幾
    // ==========================================
    /**
     * 每週固定不處理退貨的星期幾 (逗號分隔)
     * 規則：1=週一, 2=週二, 3=週三, 4=週四, 5=週五, 6=週六, 7=週日 (與 Java DayOfWeek.getValue() 一致)
     * 例如: "1,3,7" 表示週一、週三、週日不處理退貨。
     * 若為 null 或空字符串，表示每週每天都可處理退貨。
     */
    @Column(name = "return_closed_days_of_week", length = 20)
    private String returnClosedDaysOfWeek;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}