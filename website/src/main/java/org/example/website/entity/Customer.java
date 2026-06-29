package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer", indexes = {
        @Index(name = "uk_customer_email", columnList = "cust_email", unique = true),
        //為 id 和 uid 建立唯一索引，防止重複
        @Index(name = "uk_customer_id", columnList = "cust_id", unique = true),
        @Index(name = "uk_customer_uid", columnList = "cust_uid", unique = true)
})
@Data
public class Customer {

    //  主鍵保持不變！千萬不要動它
    @Id
    @Column(name = "cust_username", length = 50)
    private String username;

    // 數字 ID (由數據庫 AUTO_INCREMENT 自動生成)
    // 注意：因為不是主鍵，不能加 @Id 和 @GeneratedValue
    @Column(name = "cust_id", unique = true, updatable = false)
    private Long id;

    //唯一識別碼 UID (例如 UUID，由 Java 代碼生成)
    @Column(name = "cust_uid", unique = true, length = 50, updatable = false)
    private String uid;

    @Column(name = "cust_name", length = 100, nullable = false)
    private String name;

    @Column(name = "cust_email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "cust_password", length = 255)
    private String password;

    @Column(name = "cust_phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "cust_credit_limit")
    private Double creditLimit = 5000.00;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}