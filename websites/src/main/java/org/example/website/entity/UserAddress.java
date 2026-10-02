package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_addresses", indexes = {
        // 為 user_id 建立索引，加速查詢某個用戶的所有地址
        @Index(name = "idx_user_address_user_id", columnList = "user_id")
})
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long addressId; // 修改為 addressId，明確對應資料庫的 address_id 欄位

    // 關聯到 User 表 (多對一關係)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 收件人姓名 (可能與註冊用戶名不同)
    @Column(name = "receiver_name", length = 100)
    private String receiverName;

    // 聯繫電話 (可能與註冊手機號不同)
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    // 完整詳細地址
    @Column(name = "full_address", length = 500, nullable = false)
    private String fullAddress;

    // 【新增】排序權重 (數值越小越靠前，預設為 0，方便業務層自定義排序邏輯)
    @Column(name = "ranking")
    private Integer ranking = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}