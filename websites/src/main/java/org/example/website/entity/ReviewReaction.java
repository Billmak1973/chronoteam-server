package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "review_reaction")
public class ReviewReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long reactionId; 並明確指定數據庫列名為 reaction_id
    @Column(name = "reaction_id")
    private Long reactionId;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    // 從 @Column(name = "username", nullable = false, length = 50) private String username;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reaction_type", nullable = false, length = 20)
    private String reactionType; // "LIKE" or "DISLIKE"

    @Column(name = "created_at")
    private LocalDateTime createdAt;  // 第一次点赞/踩的时间

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;  // 最后修改时间（点赞↔踩切换时更新）
}