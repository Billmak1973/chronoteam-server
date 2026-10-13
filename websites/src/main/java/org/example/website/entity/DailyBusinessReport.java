package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_business_report", indexes = {
        @Index(name = "idx_daily_date", columnList = "report_date", unique = true)
})
@Data
public class DailyBusinessReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    /** 統計日期 (每天只能有一條記錄) */
    @Column(name = "report_date", nullable = false, unique = true)
    private LocalDate reportDate;

    // ================= 銷售與收入 =================
    /** 總營業額 / GMV (線上 + 線下總和) */
    @Column(name = "total_gmv", precision = 15, scale = 2)
    private BigDecimal totalGmv = BigDecimal.ZERO;

    /** 【新增】線上總營業額 (GMV) */
    @Column(name = "online_gmv", precision = 15, scale = 2)
    private BigDecimal onlineGmv = BigDecimal.ZERO;

    /** 【新增】線下總營業額 (GMV) */
    @Column(name = "offline_gmv", precision = 15, scale = 2)
    private BigDecimal offlineGmv = BigDecimal.ZERO;

    /** 總訂單數 */
    @Column(name = "total_orders")
    private Integer totalOrders = 0;

    /** 【新增】線上訂單數 */
    @Column(name = "online_orders")
    private Integer onlineOrders = 0;

    /** 【新增】線下訂單數 */
    @Column(name = "offline_orders")
    private Integer offlineOrders = 0;

    /** 總售出件數 */
    @Column(name = "total_items_sold")
    private Integer totalItemsSold = 0;

    // ================= 退貨與退款 =================
    /** 總退款金額 */
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    /** 【核心修正】僅記錄線下退款金額 (因售後退換貨均在門店處理) */
    @Column(name = "offline_refund_amount", precision = 15, scale = 2)
    private BigDecimal offlineRefundAmount = BigDecimal.ZERO;

    /** 退貨件數 */
    @Column(name = "refund_count")
    private Integer refundCount = 0;

    // ================= 成本與利潤 =================
    /** 收購二手手錶的總成本 */
    @Column(name = "acquisition_cost", precision = 15, scale = 2)
    private BigDecimal acquisitionCost = BigDecimal.ZERO;

    // ================= 平台活躍度指標 =================
    /** 當日新註冊用戶數 */
    @Column(name = "new_users")
    private Integer newUsers = 0;

    /** 當日新增出售申請數 */
    @Column(name = "new_sell_applications")
    private Integer newSellApplications = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}