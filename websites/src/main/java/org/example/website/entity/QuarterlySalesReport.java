package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
// 【核心修改】：唯一約束現在包含 store_id
// 意義：同一季度、同一商品、同一單價、同一渠道、同一門店 -> 必須合併為一條記錄進行累加
@Table(name = "quarterly_sales_report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"year", "quarter", "product_id", "unit_price", "sales_channel", "store_id"}),
        indexes = {
                @Index(name = "idx_quarterly_year_quarter", columnList = "year, quarter"),
                @Index(name = "idx_quarterly_channel", columnList = "sales_channel"),
                @Index(name = "idx_quarterly_store", columnList = "store_id") // 【新增】為門店ID加索引，加速按門店統計
        }
)
@Data
public class QuarterlySalesReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "quarter", nullable = false)
    private Integer quarter;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_name", length = 200)
    private String productName;

    // ================= 銷售渠道 =================
    /**
     * ONLINE: 線上商城 (通常 store_id 為 null 或指向總倉虛擬ID)
     * OFFLINE: 線下門店 (store_id 必填)
     */
    @Column(name = "sales_channel", length = 20, nullable = false)
    private String salesChannel;

    // ================= 【新增】門店 ID =================
    /**
     * 關聯的門店 ID
     * - 如果是線下銷售，這裡存具體的 store_id
     * - 如果是純線上銷售且無門店歸屬，這裡可以為 NULL
     */
    @Column(name = "store_id")
    private Long storeId;

    // ================= 銷量與價格 =================

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    // ================= 退貨統計 =================

    @Column(name = "refund_quantity")
    private Integer refundQuantity = 0;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "brand", length = 50)
    private String brand;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}