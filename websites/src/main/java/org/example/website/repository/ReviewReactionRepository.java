package org.example.website.repository;

import org.example.website.entity.ReviewReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 評論互動反應 (點贊/踩) 數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於管理用戶對評論的點贊與踩操作記錄。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface ReviewReactionRepository extends JpaRepository<ReviewReaction, Long> {

    /**
     * 查詢指定用戶對特定評論的互動記錄。
     * <p>
     * 用於判斷用戶是否已經點贊或踩過該評論，以決定前端按鈕的激活狀態，或決定是新增還是更新反應類型。
     * </p>
     *
     * @param reviewId 評論 ID
     * @param username 用戶名
     * @return 包含互動記錄的 {@link Optional}，若無記錄則返回 {@link Optional#empty()}
     */
    Optional<ReviewReaction> findByReviewIdAndUser_Username(Long reviewId, String username);

    /**
     * 批量查詢指定用戶對多個評論的互動記錄。
     * <p>
     * 【性能優化】：
     * 在加載評論列表時，使用 IN 查詢一次性獲取當前用戶對這些評論的點贊狀態，避免 N+1 查詢問題。
     * </p>
     *
     * @param reviewIds 評論 ID 列表
     * @param username  用戶名
     * @return 匹配條件的互動記錄列表
     */
    List<ReviewReaction> findByReviewIdInAndUser_Username(List<Long> reviewIds, String username);
}