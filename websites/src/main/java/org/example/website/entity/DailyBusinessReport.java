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

    //  核心規範：絕對不使用 id，使用具體的 reportId
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    /** 統計日期 (每天只能有一條記錄) */
    @Column(name = "report_date", nullable = false, unique = true)
    private LocalDate reportDate;

    // ================= 銷售與收入 =================

    /** 總營業額 / GMV (買家實際支付的訂單總金額，包含後來可能被退貨的) */
    @Column(name = "total_gmv", precision = 15, scale = 2)
    private BigDecimal totalGmv = BigDecimal.ZERO;

    /** 總訂單數 */
    @Column(name = "total_orders")
    private Integer totalOrders = 0;

    /** 總售出件數 */
    @Column(name = "total_items_sold")
    private Integer totalItemsSold = 0;

    // ================= 退貨與退款 =================

    /** 退款金額 (當日發生的退貨退款總額) */
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    /** 退貨件數 (當日退貨的商品數量) */
    @Column(name = "refund_count")
    private Integer refundCount = 0;

    // ================= 成本與利潤 =================

    /** 收購二手手錶的總成本 (僅來自「平台買斷」模式的進貨支出) */
    @Column(name = "acquisition_cost", precision = 15, scale = 2)
    private BigDecimal acquisitionCost = BigDecimal.ZERO;

    /**
     * 淨利潤 = GMV - 收購成本 - 退款金額
     * (因為已刪除寄售模式，所以不再有服務費收入)
     */
    @Column(name = "net_profit", precision = 15, scale = 2)
    private BigDecimal netProfit = BigDecimal.ZERO;

    // ================= 平台活躍度指標 =================

    /** 當日新註冊用戶數 */
    @Column(name = "new_users")
    private Integer newUsers = 0;

    /** 當日新增出售申請數 (衡量賣家貨源) */
    @Column(name = "new_sell_applications")
    private Integer newSellApplications = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}