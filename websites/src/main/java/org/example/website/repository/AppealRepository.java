package org.example.website.repository;

import org.example.website.entity.Appeal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 申訴記錄數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link Appeal} 實體的自定義查詢方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {

    /**
     * 根據指定的申訴狀態查詢所有匹配的申訴記錄。
     * <p>
     * 注意：此方法會返回所有符合條件的記錄，若數據量較大，建議使用分頁查詢方法。
     * </p>
     *
     * @param status 申訴狀態枚舉 ({@link Appeal.AppealStatus})，例如 PENDING, APPROVED, REJECTED
     * @return 包含所有符合該狀態的申訴記錄列表
     */
    List<Appeal> findByStatus(Appeal.AppealStatus status);

    /**
     * 根據關聯的系統通知 ID 查詢申訴記錄，並按創建時間降序排列。
     * <p>
     * 通常用於獲取針對某個特定通知的最新申訴記錄。
     * </p>
     *
     * @param notificationId 關聯的系統通知唯一 ID
     * @return 按創建時間從新到舊排序的申訴記錄列表
     */
    List<Appeal> findByNotificationIdOrderByCreatedAtDesc(Long notificationId);

    /**
     * 根據申訴類型進行分頁查詢。
     *
     * @param appealType 申訴類型枚舉 ({@link Appeal.AppealType})，例如 BAN, BLACKLIST, DELETE_REVIEW
     * @param pageable   Spring Data 的分頁與排序參數對象 ({@link Pageable})
     * @return 包含申訴記錄的分頁結果對象 ({@link Page})
     */
    Page<Appeal> findByAppealType(Appeal.AppealType appealType, Pageable pageable);

    /**
     * 根據申訴狀態進行分頁查詢。
     *
     * @param status   申訴狀態枚舉 ({@link Appeal.AppealStatus})
     * @param pageable Spring Data 的分頁與排序參數對象 ({@link Pageable})
     * @return 包含申訴記錄的分頁結果對象 ({@link Page})
     */
    Page<Appeal> findByStatus(Appeal.AppealStatus status, Pageable pageable);

    /**
     * 根據申訴類型和狀態的組合條件進行分頁查詢。
     * <p>
     * 適用於後台管理系統中需要同時篩選「類型」與「狀態」的複雜查詢場景。
     * </p>
     *
     * @param appealType 申訴類型枚舉 ({@link Appeal.AppealType})
     * @param status     申訴狀態枚舉 ({@link Appeal.AppealStatus})
     * @param pageable   Spring Data 的分頁與排序參數對象 ({@link Pageable})
     * @return 包含符合雙重條件的申訴記錄的分頁結果對象 ({@link Page})
     */
    Page<Appeal> findByAppealTypeAndStatus(
            Appeal.AppealType appealType,
            Appeal.AppealStatus status,
            Pageable pageable
    );
}