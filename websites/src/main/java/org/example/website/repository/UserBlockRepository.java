package org.example.website.repository;

import org.example.website.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 用戶禁言/拉黑數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於管理用戶之間的雙向禁言記錄。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    /**
     * 查詢用戶 A 是否禁言了用戶 B。
     *
     * @param blocker 發起禁言的用戶名
     * @param blocked 被禁言的用戶名
     * @return 包含禁言記錄的 {@link Optional}，若無記錄則返回 {@link Optional#empty()}
     */
    Optional<UserBlock> findByBlocker_UsernameAndBlockedUser_Username(String blocker, String blocked);

    /**
     * 根據發起禁言的用戶名，分頁查詢其禁言列表。
     * <p>
     * 通常用於用戶個人中心的「禁言名單」頁面展示。
     * </p>
     *
     * @param blocker  發起禁言的用戶名
     * @param pageable Spring Data 的分頁與排序參數對象
     * @return 禁言記錄的分頁結果
     */
    Page<UserBlock> findByBlocker_Username(String blocker, Pageable pageable);

    /**
     * 【核心邏輯】檢查兩個用戶之間是否存在雙向禁言記錄（任意一方禁言都算）。
     * <p>
     * 用於在用戶嘗試回復評論前，快速判斷雙方是否處於互相禁言狀態，從而阻止互動。
     * </p>
     *
     * @param user1 用戶 A 的用戶名
     * @param user2 用戶 B 的用戶名
     * @return 若存在禁言記錄則返回 true，否則返回 false
     */
    @Query("SELECT COUNT(ub) > 0 FROM UserBlock ub WHERE " +
            "(ub.blocker.username = ?1 AND ub.blockedUser.username = ?2) OR " +
            "(ub.blocker.username = ?2 AND ub.blockedUser.username = ?1)")
    boolean existsMutualBlock(String user1, String user2);

    /**
     * 刪除指定的禁言記錄（解除禁言）。
     *
     * @param blocker 發起禁言的用戶名
     * @param blocked 被禁言的用戶名
     */
    void deleteByBlocker_UsernameAndBlockedUser_Username(String blocker, String blocked);

    /**
     * 查詢當前用戶禁言了哪些人的用戶名列表。
     * <p>
     * 使用 JPQL 直接提取用戶名字段，避免加載完整的 UserBlock 實體，提升查詢性能。
     * </p>
     *
     * @param username 發起禁言的用戶名
     * @return 被禁言的用戶名列表
     */
    @Query("SELECT ub.blockedUser.username FROM UserBlock ub WHERE ub.blocker.username = :username")
    List<String> findBlockedUsernamesByBlockerUsername(@Param("username") String username);
}