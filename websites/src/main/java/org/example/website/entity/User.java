package org.example.website.entity;

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
public class User {

    // 1. 主鍵自增 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    // UID (唯一標識符)
    @Column(name = "uid", unique = true, length = 50, updatable = false)
    private String uid;

    // 姓名 (必填)
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    // 用戶名 (唯一業務鍵，必填)
    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    // 密碼 (加密存儲)
    @Column(name = "password", length = 255)
    private String password;

    // 郵箱 (唯一，必填)
    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    // 手機號 (必填)
    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    // 家庭地址 (非必填，允許為 NULL)
    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "backup_address", length = 255)
    private String backupAddress;

    // 用戶角色 (默認為顧客)
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
        ADMIN,
        CUSTOMER,
        STAFF
    }
}