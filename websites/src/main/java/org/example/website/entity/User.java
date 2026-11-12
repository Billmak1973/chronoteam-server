package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "uk_user_email", columnList = "email", unique = true),
        @Index(name = "uk_user_username", columnList = "username", unique = true),
        @Index(name = "uk_user_uid", columnList = "uid", unique = true)
})
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "uid", unique = true, length = 50, updatable = false)
    private String uid;

    @Column(name = "name", length = 100, nullable = false)
    private String name; // 姓名保留在 User，因為顧客也可能需要真實姓名

    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    // 【移除】 workPhone 和 store 關聯，移至 Staff 表

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20, nullable = false)
    private Role role = Role.CUSTOMER;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Role {
        ADMIN, CUSTOMER, SALES, COURIER, APPRAISER, ONLINE_STAFF, STORE_MANAGER
    }

    // 關聯的線下門店實體 (非必填，FetchType.LAZY 避免效能問題)
    //    @ManyToOne(fetch = FetchType.LAZY)
    //    @JoinColumn(name = "store_id") // 這會在資料庫生成名為 store_id 的外鍵欄位
    //    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    //    private OfflineStore store;
    //Jackson 在嘗試將 User 實體序列化並存入 Redis 時，遇到了 store 屬性。
    //因為 store 使用了 FetchType.LAZY（延遲加載），Hibernate 返回的不是一個真實的 OfflineStore 對象，而是一個 Hibernate 代理對象 (Proxy)。
    // 這個代理對象內部包含 Hibernate 注入的 $$_hibernate_interceptor 等內部字段。Jackson 在序列化時試圖讀取這些內部字段，但發現它們沒有 Getter/Setter，從而拋出了 InvalidDefinitionException。
}