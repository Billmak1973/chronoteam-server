package org.example.website.repository;

import org.example.website.entity.AdminPenalty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 管理員處罰記錄數據訪問介面
 * 用於管理用戶的封禁 (BAN) 和永久拉黑 (BLACKLIST) 記錄
 */
@Repository
public interface AdminPenaltyRepository extends JpaRepository<AdminPenalty, Long> {

    /**
     * 根據目標用戶名、處罰類型和狀態，按開始時間降序查找最新的一條處罰記錄。
     *
     * @param username 目標用戶名
     * @param type     處罰類型 (BAN 或 BLACKLIST)
     * @param status   處罰狀態 (ACTIVE, EXPIRED, REVOKED)
     * @return 最新的一條處罰記錄，若不存在則返回 Optional.empty()
     */
    Optional<AdminPenalty> findTopByTargetUser_UsernameAndTypeAndStatusOrderByStartTimeDesc(
            String username,
            AdminPenalty.PenaltyType type,
            AdminPenalty.PenaltyStatus status
    );

    /**
     * 根據目標用戶名和處罰類型，按開始時間降序查找最新的一條處罰記錄。
     * （常用於懶檢查機制：檢查用戶當前是否處於某種處罰狀態，例如是否被永久拉黑）
     *
     * @param username 目標用戶名
     * @param type     處罰類型 (BAN 或 BLACKLIST)
     * @return 最新的一條處罰記錄，若不存在則返回 Optional.empty()
     */
    Optional<AdminPenalty> findTopByTargetUser_UsernameAndTypeOrderByStartTimeDesc(
            String username,
            AdminPenalty.PenaltyType type
    );

    /**
     * 根據關聯的系統通知 ID 查找對應的處罰記錄。
     *
     * @param notificationId 系統通知 ID
     * @return 對應的處罰記錄，若不存在則返回 Optional.empty()
     */
    Optional<AdminPenalty> findByNotificationId(Long notificationId);

    /**
     * 根據關聯的申訴記錄 ID 查找對應的處罰記錄。
     *
     * @param appealId 申訴記錄 ID
     * @return 對應的處罰記錄，若不存在則返回 Optional.empty()
     */
    Optional<AdminPenalty> findByAppealId(Long appealId);

    /**
     * 查找所有狀態為 ACTIVE 且結束時間早於指定時間的記錄。
     * （主要用於系統定時任務或懶加載檢查，將已過期的封禁狀態自動批量更新為 EXPIRED）
     *
     * @param status  目標狀態 (通常傳入 ACTIVE)
     * @param endTime 截止時間 (通常傳入 LocalDateTime.now())
     * @return 符合條件的處罰記錄列表
     */
    List<AdminPenalty> findByStatusAndEndTimeBefore(AdminPenalty.PenaltyStatus status, LocalDateTime endTime);

    /**
     * 檢查是否存在關聯特定評論 ID 的處罰記錄。
     * （用於防止管理員對同一條違規評論進行重複封禁/處罰）
     *
     * @param reviewId 評論 ID
     * @return 若存在則返回 true，否則返回 false
     */
    boolean existsByReviewId(Long reviewId);

    /**
     * 根據處罰類型進行分頁查詢。
     *
     * @param type     處罰類型
     * @param pageable 分頁參數
     * @return 處罰記錄分頁結果
     */
    Page<AdminPenalty> findByType(AdminPenalty.PenaltyType type, Pageable pageable);

    /**
     * 根據處罰狀態進行分頁查詢。
     *
     * @param status   處罰狀態
     * @param pageable 分頁參數
     * @return 處罰記錄分頁結果
     */
    Page<AdminPenalty> findByStatus(AdminPenalty.PenaltyStatus status, Pageable pageable);

    /**
     * 根據處罰類型和狀態組合進行分頁查詢。
     *
     * @param type     處罰類型
     * @param status   處罰狀態
     * @param pageable 分頁參數
     * @return 處罰記錄分頁結果
     */
    Page<AdminPenalty> findByTypeAndStatus(AdminPenalty.PenaltyType type, AdminPenalty.PenaltyStatus status, Pageable pageable);

    /**
     * 僅按目標用戶名進行分頁查詢。
     *
     * @param username 目標用戶名
     * @param pageable 分頁參數
     * @return 處罰記錄分頁結果
     */
    Page<AdminPenalty> findByTargetUser_Username(String username, Pageable pageable);

    /**
     * 按目標用戶名和處罰類型進行分頁查詢。
     *
     * @param username 目標用戶名
     * @param type     處罰類型
     * @param pageable 分頁參數
     * @return 處罰記錄分頁結果
     */
    Page<AdminPenalty> findByTargetUser_UsernameAndType(String username, AdminPenalty.PenaltyType type, Pageable pageable);

    /**
     * 按目標用戶名和處罰狀態進行分頁查詢。
     *
     * @param username 目標用戶名
     * @param status   處罰狀態
     * @param pageable 分頁參數
     * @return 處罰記錄分頁結果
     */
    Page<AdminPenalty> findByTargetUser_UsernameAndStatus(String username, AdminPenalty.PenaltyStatus status, Pageable pageable);

    /**
     * 按目標用戶名、處罰類型和處罰狀態進行組合分頁查詢。
     *
     * @param username 目標用戶名
     * @param type     處罰類型
     * @param status   處罰狀態
     * @param pageable 分頁參數
     * @return 處罰記錄分頁結果
     */
    Page<AdminPenalty> findByTargetUser_UsernameAndTypeAndStatus(String username, AdminPenalty.PenaltyType type, AdminPenalty.PenaltyStatus status, Pageable pageable);
}