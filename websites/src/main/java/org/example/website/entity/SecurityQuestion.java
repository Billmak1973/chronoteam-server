package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_question", indexes = {
        // 從 @Index(name = "idx_sq_username", columnList = "cust_username") 改成 @Index(name = "idx_sq_user", columnList = "user_id")
        @Index(name = "idx_sq_user", columnList = "user_id")
})
@Data
public class SecurityQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long questionId; 並明確指定數據庫列名為 question_id
    @Column(name = "question_id")
    private Long questionId;

    // 從 @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false) private Customer customer; 改成 @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

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