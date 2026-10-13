package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_daily_report", indexes = {
        // 核心約束：每個門店、每一天只能有一條記錄
        @Index(name = "uk_store_date", columnList = "store_id, report_date", unique = true)
})
@Data
public class StoreDailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    // 關聯具體的線下門店
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private OfflineStore store;

    /** 統計日期 */
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    // ================= 門店銷售指標 =================
    /** 門店當日總營業額 (包含換貨補差價、直接購買) */
    @Column(name = "store_gmv", precision = 15, scale = 2)
    private BigDecimal storeGmv = BigDecimal.ZERO;

    /** 門店當日訂單數 */
    @Column(name = "store_orders")
    private Integer storeOrders = 0;

    // ================= 門店退貨/換貨指標 =================
    /** 門店當日退貨/換貨抵扣總額 */
    @Column(name = "store_refund_amount", precision = 15, scale = 2)
    private BigDecimal storeRefundAmount = BigDecimal.ZERO;

    /** 門店當日退貨件數 */
    @Column(name = "store_refund_count")
    private Integer storeRefundCount = 0;

    // ================= 門店收購指標 (二手錶回收) =================
    /** 門店當日直接收購二手錶的支出成本 */
    @Column(name = "store_acquisition_cost", precision = 15, scale = 2)
    private BigDecimal storeAcquisitionCost = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
