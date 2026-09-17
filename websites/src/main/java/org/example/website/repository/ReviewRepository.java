package org.example.website.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.website.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 評論數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，提供針對 {@link Review} 實體的複雜查詢、統計及原生 SQL 操作。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 檢查同一訂單的同一商品是否已經被評價過。
     * <p>
     * 用於防止用戶對同一筆訂單的同一商品重複提交根評論。
     * </p>
     *
     * @param orderNo   訂單編號
     * @param productId 商品 ID
     * @return 若已存在該評價則返回該 {@link Review} 實體，否則返回 null
     */
    Review findByOrderNoAndProduct_ProductId(String orderNo, Integer productId);

    /**
     * 統計特定根評論下的樓中樓回覆總數。
     * <p>
     * 用於在商品詳情頁的評論列表中，顯示每條根評論的「回覆數量」徽章，避免加載整個子評論列表。
     * </p>
     *
     * @param parentId 根評論的 ID
     * @return 回覆總數
     */
    long countByParentId(Long parentId);

    /**
     * 分頁查詢特定商品的「根評論」列表。
     *
     * @param productId 商品 ID
     * @param pageable  分頁與排序參數對象
     * @return 根評論的分頁結果
     */
    @Query("SELECT r FROM Review r WHERE r.product.productId = :productId AND r.parentId IS NULL")
    Page<Review> findByProduct_ProductIdAndParentIdIsNull(@Param("productId") Integer productId, Pageable pageable);

    /**
     * 查詢指定用戶對特定商品發表的所有「根評論」列表。
     * <p>
     * 用於檢查用戶是否已經對該商品發表過評價。
     * </p>
     *
     * @param username  用戶名
     * @param productId 商品 ID
     * @return 根評論列表
     */
    List<Review> findByUser_UsernameAndProduct_ProductIdAndParentIdIsNull(String username, Integer productId);

    /**
     * 分頁查詢特定根評論下的所有「樓中樓回覆」。
     * <p>
     * 支持動態排序（如按熱門、最新、最早），用於展開評論詳情時加載子評論。
     * </p>
     *
     * @param parentId 根評論的 ID
     * @param pageable 分頁與排序參數對象
     * @return 樓中樓回覆的分頁結果
     */
    @Query("SELECT r FROM Review r WHERE r.parentId = :parentId")
    Page<Review> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);

    /**
     * 【我的評論】：分頁查詢當前用戶發表的所有評論，按創建時間降序排列。
     *
     * @param username 用戶名
     * @param pageable 分頁與排序參數對象
     * @return 評論分頁結果
     */
    Page<Review> findByUser_UsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /**
     * 【回覆我的】：分頁查詢別人回覆當前用戶的評論，按創建時間降序排列。
     * <p>
     * 條件：replyToUser = 當前用戶 且 發送者 != 當前用戶。
     * </p>
     *
     * @param replyToUser 被回覆的用戶名 (當前用戶)
     * @param notUsername 排除的用戶名 (當前用戶，防止自己回覆自己也被統計)
     * @param pageable    分頁與排序參數對象
     * @return 評論分頁結果
     */
    Page<Review> findByReplyToUserAndUser_UsernameNotOrderByCreatedAtDesc(
            String replyToUser,
            String notUsername,
            Pageable pageable
    );

    /**
     * 【@我的】：分頁查詢內容中包含 @當前用戶 且不是自己發出的評論，按創建時間降序排列。
     * <p>
     * 使用 LIKE 進行模糊匹配，用於實現 Mention (提及) 通知功能。
     * </p>
     *
     * @param keyword  搜索關鍵字 (格式為 "@username")
     * @param username 當前用戶名 (用於排除自己)
     * @param pageable 分頁與排序參數對象
     * @return 評論分頁結果
     */
    @Query("SELECT r FROM Review r WHERE r.content LIKE %:keyword% AND r.user.username <> :username ORDER BY r.createdAt DESC")
    Page<Review> findMentions(
            @Param("keyword") String keyword,
            @Param("username") String username,
            Pageable pageable
    );

    /**
     * 統計特定商品下已被置頂的評論數量。
     * <p>
     * 用於管理員置頂評論時的業務校驗（例如：每個商品最多只能置頂 3 條評論）。
     * </p>
     *
     * @param productId 商品 ID
     * @param pinned    是否為置頂狀態 (應傳入 true)
     * @return 置頂評論的數量
     */
    long countByProduct_ProductIdAndPinned(Integer productId, Boolean pinned);

    /**
     * 【高效統計】：統計用戶收到的「回覆我」的未讀數量。
     * <p>
     * 邏輯：查找 replyToUser = 當前用戶，且創建時間 > 最後查看時間 的記錄總數。
     * 避免加載完整列表，直接通過 COUNT 查詢提升性能。
     * </p>
     *
     * @param username     當前用戶名
     * @param lastViewedAt 用戶最後查看該類型消息的時間
     * @return 未讀回覆數量
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.replyToUser = :username AND r.createdAt > :lastViewedAt")
    long countUnreadReplies(@Param("username") String username, @Param("lastViewedAt") LocalDateTime lastViewedAt);

    /**
     * 【高效統計】：統計用戶收到的「@我的」未讀數量。
     * <p>
     * 邏輯：查找內容包含 @用戶名，且創建時間 > 最後查看時間，且不是自己發的。
     * </p>
     *
     * @param keyword      搜索關鍵字 (格式為 "@username")
     * @param username     當前用戶名
     * @param lastViewedAt 用戶最後查看該類型消息的時間
     * @return 未讀提及數量
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.content LIKE %:keyword% " +
            "AND r.user.username <> :username " +
            "AND r.createdAt > :lastViewedAt")
    long countUnreadMentions(@Param("keyword") String keyword,
                             @Param("username") String username,
                             @Param("lastViewedAt") LocalDateTime lastViewedAt);

    /**
     * 分頁查詢當前用戶點贊過的評論列表。
     * <p>
     * 通過子查詢關聯 {@link org.example.website.entity.ReviewReaction} 表實現。
     * </p>
     *
     * @param username 用戶名
     * @param pageable 分頁與排序參數對象
     * @return 評論分頁結果
     */
    @Query("SELECT r FROM Review r WHERE r.reviewId IN (SELECT rr.reviewId FROM ReviewReaction rr WHERE rr.user.username = :username AND rr.reactionType = 'LIKE')")
    Page<Review> findReviewsLikedByMe(@Param("username") String username, Pageable pageable);

    /**
     * 分頁查詢別人點贊了當前用戶的評論列表（排除自己點贊自己）。
     *
     * @param reviewAuthorUsername 評論作者用戶名
     * @param excludeUsername      需要排除的用戶名 (通常為評論作者本人)
     * @param pageable             分頁與排序參數對象
     * @return 評論分頁結果
     */
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
     * 【高效統計】：統計未讀的點贊數量。
     * <p>
     * 邏輯：查找點贊我的評論，且點贊操作的創建時間 > 最後查看時間。
     * </p>
     *
     * @param username     當前用戶名 (評論作者)
     * @param lastViewedAt 用戶最後查看該類型消息的時間
     * @return 未讀點贊數量
     */
    @Query("SELECT COUNT(DISTINCT r) FROM Review r " +
            "JOIN ReviewReaction rr ON r.reviewId = rr.reviewId " +
            "WHERE r.user.username = :username " +
            "AND rr.reactionType = 'LIKE' " +
            "AND rr.createdAt > :lastViewedAt")
    long countUnreadLikes(@Param("username") String username,
                          @Param("lastViewedAt") LocalDateTime lastViewedAt);

    /**
     * 分頁查詢別人點贊了我的評論的詳細信息（包含點贊者用戶名和點贊時間）。
     * <p>
     * 返回 {@link Object[]} 數組以避免複雜的 DTO 映射，數組結構為: [Review實體, 點贊者用戶名, 點贊時間]。
     * </p>
     *
     * @param reviewAuthorUsername 評論作者用戶名
     * @param excludeUsername      需要排除的用戶名 (評論作者本人)
     * @param pageable             分頁與排序參數對象
     * @return 包含評論及點贊者信息的 Object 數組分頁結果
     */
    @Query("SELECT r, rr.user.username as likerUsername, rr.createdAt as likeTime " +
            "FROM Review r " +
            "JOIN ReviewReaction rr ON r.reviewId = rr.reviewId " +
            "WHERE r.user.username = :reviewAuthorUsername " +
            "AND rr.user.username != :excludeUsername " +
            "AND rr.reactionType = 'LIKE' " +
            "ORDER BY rr.createdAt DESC")
    Page<Object[]> findLikesOnMyReviews(
            @Param("reviewAuthorUsername") String reviewAuthorUsername,
            @Param("excludeUsername") String excludeUsername,
            Pageable pageable
    );

    /**
     * 使用原生 SQL 強制指定 review_id 插入評論記錄。
     * <p>
     * 【核心業務場景】：
     * 當管理員從 {@link } (評論歸檔) 中恢復一條被刪除的評論時，
     * 必須保留其原始的 review_id、創建時間及互動數據，以確保前端跳轉鏈接和歷史數據的完整性。
     * JPA 的 save() 默認會忽略手動設置的 ID 並生成新 ID，因此必須使用原生 INSERT 語句。
     * </p>
     *
     * @param reviewId         原始評論 ID
     * @param orderNo          關聯訂單號
     * @param userId           用戶 ID
     * @param prodId           商品 ID
     * @param rating           評分
     * @param content          純文本內容
     * @param parentId         父評論 ID (樓中樓用)
     * @param replyToUser      回覆的目標用戶名
     * @param likeCount        恢復時的點贊數
     * @param dislikeCount     恢復時的踩數
     * @param pinned           是否置頂 (恢復後默認為 false)
     * @param formattedContent 富文本 HTML 內容
     * @param isFormatted      是否為富文本
     * @param createdAt        原始創建時間
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO review (review_id, order_no, user_id, prod_id, rating, content, " +
            "parent_id, reply_to_user, like_count, dislike_count, pinned, formatted_content, " +
            "is_formatted, created_at) " +
            "VALUES (:reviewId, :orderNo, :userId, :prodId, :rating, :content, " +
            ":parentId, :replyToUser, :likeCount, :dislikeCount, :pinned, :formattedContent, " +
            ":isFormatted, :createdAt)", nativeQuery = true)
    void insertReviewWithOriginalId(
            @org.springframework.data.repository.query.Param("reviewId") Long reviewId,
            @org.springframework.data.repository.query.Param("orderNo") String orderNo,
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("prodId") Integer prodId,
            @org.springframework.data.repository.query.Param("rating") Double rating,
            @org.springframework.data.repository.query.Param("content") String content,
            @org.springframework.data.repository.query.Param("parentId") Long parentId,
            @org.springframework.data.repository.query.Param("replyToUser") String replyToUser,
            @org.springframework.data.repository.query.Param("likeCount") Integer likeCount,
            @org.springframework.data.repository.query.Param("dislikeCount") Integer dislikeCount,
            @org.springframework.data.repository.query.Param("pinned") Boolean pinned,
            @org.springframework.data.repository.query.Param("formattedContent") String formattedContent,
            @org.springframework.data.repository.query.Param("isFormatted") Boolean isFormatted,
            @org.springframework.data.repository.query.Param("createdAt") LocalDateTime createdAt);

    /**
     * 根據用戶名和評論類型篩選：分頁查詢當前用戶發表的「根評論」，按創建時間降序排列。
     *
     * @param username 用戶名
     * @param pageable 分頁與排序參數對象
     * @return 評論分頁結果
     */
    @Query("SELECT r FROM Review r WHERE r.user.username = :username AND r.parentId IS NULL ORDER BY r.createdAt DESC")
    Page<Review> findByUser_UsernameAndParentIdIsNull(@Param("username") String username, Pageable pageable);

    /**
     * 根據用戶名和評論類型篩選：分頁查詢當前用戶發表的「樓中樓回覆」，按創建時間降序排列。
     *
     * @param username 用戶名
     * @param pageable 分頁與排序參數對象
     * @return 評論分頁結果
     */
    @Query("SELECT r FROM Review r WHERE r.user.username = :username AND r.parentId IS NOT NULL ORDER BY r.createdAt DESC")
    Page<Review> findByUser_UsernameAndParentIdIsNotNull(@Param("username") String username, Pageable pageable);

    /**
     * 根據評論類型篩選所有用戶：分頁查詢系統中所有的「根評論」，按創建時間降序排列。
     * <p>
     * 通常用於後台管理系統的評論審核列表。
     * </p>
     *
     * @param pageable 分頁與排序參數對象
     * @return 評論分頁結果
     */
    @Query("SELECT r FROM Review r WHERE r.parentId IS NULL ORDER BY r.createdAt DESC")
    Page<Review> findByParentIdIsNullOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 根據評論類型篩選所有用戶：分頁查詢系統中所有的「樓中樓回覆」，按創建時間降序排列。
     *
     * @param pageable 分頁與排序參數對象
     * @return 評論分頁結果
     */
    @Query("SELECT r FROM Review r WHERE r.parentId IS NOT NULL ORDER BY r.createdAt DESC")
    Page<Review> findByParentIdIsNotNullOrderByCreatedAtDesc(Pageable pageable);
}