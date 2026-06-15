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
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "reaction_type", nullable = false, length = 20)
    private String reactionType; // "LIKE" or "DISLIKE"

    @Column(name = "created_at")
    private LocalDateTime createdAt;  // 第一次点赞/踩的时间

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;  // 最后修改时间（点赞↔踩切换时更新）

    // Getters and Setters (Lombok @Data 已自动生成)
}
