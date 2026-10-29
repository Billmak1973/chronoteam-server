package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "sell_application_audit_log", indexes = {
        @Index(name = "idx_audit_app", columnList = "application_id"),
        @Index(name = "idx_audit_operator", columnList = "operator_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Data
public class SellApplicationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "user", "imagePaths", "accessories", "notes"})
    private SellApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User operator; // 執行此次修正的管理員/員工

    @Column(name = "action_type", length = 50, nullable = false)
    private String actionType; // 例如: "UPDATE_APPLICATION", "UPDATE_TO_COMPLETED"

    @Column(name = "changed_fields", columnDefinition = "TEXT", nullable = false)
    private String changedFields; // JSON 格式記錄所有變更，例如:
    // [{"fieldName":"finalPrice","oldValue":"10000","newValue":"12000"},{"fieldName":"status","oldValue":"QUOTED","newValue":"COMPLETED"}]

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark; // 修改原因備註

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}