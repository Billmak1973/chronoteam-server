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

    // 1. 將 id 設為主鍵，並開啟數據庫自增
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    // 2. username 變為唯一業務鍵（不再是主鍵，但必須唯一）
    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    @Column(name = "uid", unique = true, length = 50, updatable = false)
    private String uid;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    // 3. 新增：家庭地址
    @Column(name = "address", length = 255)
    private String address;

    // 4. 新增：用戶類型/角色 (admin, customer, staff)
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20, nullable = false)
    private Role role = Role.CUSTOMER; // 默認為顧客

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 用戶角色枚舉
    public enum Role {
        ADMIN,    // 管理員
        CUSTOMER, // 普通顧客
        STAFF     // 員工
    }
}