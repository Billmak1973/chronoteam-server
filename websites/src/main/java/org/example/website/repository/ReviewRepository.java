package org.example.website.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.website.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    //  新增：检查同一订单同一商品是否已评价
    Review findByOrderNoAndProduct_ProductId(String orderNo, Integer productId);

    //  3. 统计某条评论的回复总数
    long countByParentId(Long parentId);

    // 分頁查詢根評論（支持動態排序）
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.parentId IS NULL")
    Page<Review> findByProduct_IdAndParentIdIsNull(@Param("productId") Integer productId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.product.productId = :productId AND r.parentId IS NULL")
    Page<Review> findByProduct_ProductIdAndParentIdIsNull(@Param("productId") Integer productId, Pageable pageable);

    //  返回該用戶對該商品的所有根評論列表
    List<Review> findByUser_UsernameAndProduct_ProductIdAndParentIdIsNull(String username, Integer productId);

    // 2. 查询某条根评论的【楼中楼回复】（支持分页和动态排序）
    @Query("SELECT r FROM Review r WHERE r.parentId = :parentId")
    Page<Review> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);


    // 1. 【我的评论】：只要是我发出的 (cust_username = 我)，按时间倒序
    Page<Review> findByUser_UsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    // 2. 【回复我的】：别人回复我 (reply_to_user = 我 且 cust_username != 我)
    Page<Review> findByReplyToUserAndUser_UsernameNotOrderByCreatedAtDesc(
            String replyToUser,
            String notUsername,
            Pageable pageable
    );

    // 3. 【@我的】：内容包含 @我 且 不是我发的 (使用 LIKE 查询)
    @Query("SELECT r FROM Review r WHERE r.content LIKE %:keyword% AND r.user.username <> :username ORDER BY r.createdAt DESC")
    Page<Review> findMentions(
            @Param("keyword") String keyword, // 传入 "@username"
            @Param("username") String username,
            Pageable pageable
    );

    long countByProduct_ProductIdAndPinned(Integer productId, Boolean pinned);

    /**
     *  高效統計：統計用戶收到的「回復我」的未讀數量
     * 邏輯：查找 reply_to_user = 當前用戶，且創建時間 > 最後查看時間 的記錄總數
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.replyToUser = :username AND r.createdAt > :lastViewedAt")
    long countUnreadReplies(@Param("username") String username, @Param("lastViewedAt") LocalDateTime lastViewedAt);

    /**
     *  高效統計：統計用戶收到的「@我的」未讀數量
     * 邏輯：查找內容包含 @用戶名，且創建時間 > 最後查看時間，且不是自己發的
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.content LIKE %:keyword% " +
            "AND r.user.username <> :username " +
            "AND r.createdAt > :lastViewedAt")
    long countUnreadMentions(@Param("keyword") String keyword,
                             @Param("username") String username,
                             @Param("lastViewedAt") LocalDateTime lastViewedAt);

    // 查詢我點贊過的評論 (通過 review_reaction 表關聯)
    @Query("SELECT r FROM Review r WHERE r.reviewId IN (SELECT rr.reviewId FROM ReviewReaction rr WHERE rr.user.username = :username AND rr.reactionType = 'LIKE')")
    Page<Review> findReviewsLikedByMe(@Param("username") String username, Pageable pageable);

    // 查询别人点赞了我的评论（排除自己点赞自己）
    @Query("SELECT r FROM Review r WHERE r.user.username = :reviewAuthorUsername " +
            "AND r.reviewId IN (SELECT rr.reviewId FROM ReviewReaction rr " +
            "WHERE rr.reactionType = 'LIKE' AND rr.user.username <> :excludeUsername) " +
            "ORDER BY r.createdAt DESC")
    Page<Review> findLikesByReviewUserUsernameNotOrderByCreatedAtDesc(
            @Param("reviewAuthorUsername") String reviewAuthorUsername,
            @Param("excludeUsername") String excludeUsername,
            Pageable pageable
    );

    /**
     * 统计未读的点赞数量
     * 逻辑：查找点赞我的评论，且创建时间 > 最后查看时间
     */
    @Query("SELECT COUNT(DISTINCT r) FROM Review r " +
            "JOIN ReviewReaction rr ON r.reviewId = rr.reviewId " +
            "WHERE r.user.username = :username " +
            "AND rr.reactionType = 'LIKE' " +
            "AND rr.createdAt > :lastViewedAt")
    long countUnreadLikes(@Param("username") String username,
                          @Param("lastViewedAt") LocalDateTime lastViewedAt);


    // 将 rr.updatedAt 改为 rr.createdAt
    @Query("SELECT r, rr.user.username as likerUsername, rr.createdAt as likeTime " +
            "FROM Review r " +
            "JOIN ReviewReaction rr ON r.reviewId = rr.reviewId " +
            "WHERE r.user.username = :reviewAuthorUsername " +
            "AND rr.user.username != :excludeUsername " +
            "AND rr.reactionType = 'LIKE' " +
            "ORDER BY rr.createdAt DESC")  // 这里也改为 createdAt
    Page<Object[]> findLikesOnMyReviews(
            @Param("reviewAuthorUsername") String reviewAuthorUsername,
            @Param("excludeUsername") String excludeUsername,
            Pageable pageable
    );
}