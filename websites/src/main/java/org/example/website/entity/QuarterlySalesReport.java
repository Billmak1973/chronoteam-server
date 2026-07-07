package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
//  核心約束：年份 + 季度 + 商品ID + 單價 必須唯一。
@Table(name = "quarterly_sales_report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"year", "quarter", "product_id", "unit_price"}),
        indexes = {
                @Index(name = "idx_quarterly_year_quarter", columnList = "year, quarter")
        }
)
@Data
public class QuarterlySalesReport {

    //  核心規範：絕對不使用 id，使用具體的 reportId
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    /** 年份 (例如: 2024) */
    @Column(name = "year", nullable = false)
    private Integer year;

    /** 季度 (1, 2, 3, 4) */
    @Column(name = "quarter", nullable = false)
    private Integer quarter;

    /** 商品ID */
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    /** 商品名稱快照 */
    @Column(name = "product_name", length = 200)
    private String productName;

    // ================= 銷量與價格 (歷史快照，不可篡改) =================

    /** 成交單價 (核心：同一商品單價改變，會觸發唯一約束，生成新的記錄，絕不合併) */
    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    /** 該單價下的總銷售數量 (包含後來可能被退貨的，保持原始銷售記錄) */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 總價 / 成交總額 (必須記錄：作為財務快照，防止精度誤差) */
    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    // ================= 退貨統計 (獨立記錄，不破壞銷售快照) =================

    /** 該單價下的退貨數量 (發生退貨時，只增加這個字段，不去動上面的 quantity) */
    @Column(name = "refund_quantity")
    private Integer refundQuantity = 0;

    /** 該單價下的退貨總金額 */
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    // ================= 其他維度 =================

    /** 品牌快照 (方便按品牌維度進行季度匯總分析) */
    @Column(name = "brand", length = 50)
    private String brand;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}