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
    Review findByOrderNoAndProduct_Id(String orderNo, Integer productId);

    //  3. 统计某条评论的回复总数
    long countByParentId(Long parentId);

    // 分頁查詢根評論（支持動態排序）
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.parentId IS NULL")
    Page<Review> findByProduct_IdAndParentIdIsNull(@Param("productId") Integer productId, Pageable pageable);

    //  新增：返回該用戶對該商品的所有根評論列表
    List<Review> findByCustomer_UsernameAndProduct_IdAndParentIdIsNull(String username, Integer productId);

    // 2. 查询某条根评论的【楼中楼回复】（支持分页和动态排序）
    @Query("SELECT r FROM Review r WHERE r.parentId = :parentId")
    Page<Review> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);


    // 1. 【我的评论】：只要是我发出的 (cust_username = 我)，按时间倒序
    Page<Review> findByCustomer_UsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    // 2. 【回复我的】：别人回复我 (reply_to_user = 我 且 cust_username != 我)
    Page<Review> findByReplyToUserAndCustomer_UsernameNotOrderByCreatedAtDesc(
            String replyToUser,
            String notUsername,
            Pageable pageable
    );

    // 3. 【@我的】：内容包含 @我 且 不是我发的 (使用 LIKE 查询)
    @Query("SELECT r FROM Review r WHERE r.content LIKE %:keyword% AND r.customer.username <> :username ORDER BY r.createdAt DESC")
    Page<Review> findMentions(
            @Param("keyword") String keyword, // 传入 "@username"
            @Param("username") String username,
            Pageable pageable
    );
    long countByProduct_IdAndPinned(Integer productId, Boolean pinned);

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
            "AND r.customer.username <> :username " +
            "AND r.createdAt > :lastViewedAt")
    long countUnreadMentions(@Param("keyword") String keyword,
                             @Param("username") String username,
                             @Param("lastViewedAt") LocalDateTime lastViewedAt);
}