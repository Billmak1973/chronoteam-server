
package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 🟢 1. 新增導入
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_question", indexes = {
        @Index(name = "idx_sq_username", columnList = "cust_username")
})
@Data
public class SecurityQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sq_id")
    private Long id;

    // 關聯用戶表 (主鍵是 cust_username)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // 🟢 2. 新增此註解，防止 Jackson 序列化報錯
    private Customer customer;

    // 問題內容
    @Column(name = "question", length = 100, nullable = false)
    private String question;

    // 答案 (實際生產中建議加密存儲，這裡為了演示先存小寫明文)
    @Column(name = "answer", length = 100, nullable = false)
    private String answer;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}