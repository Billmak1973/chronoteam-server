package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer", indexes = {
        @Index(name = "uk_customer_email", columnList = "cust_email", unique = true)
})
@Data
public class Customer {

    @Id
    @Column(name = "cust_username", length = 50)
    private String username;  // 主鍵是用戶名

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
