package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}