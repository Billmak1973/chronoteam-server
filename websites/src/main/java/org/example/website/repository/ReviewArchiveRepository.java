package org.example.website.repository;

import org.example.website.entity.ReviewArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 評論歸檔數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於存儲和管理被刪除的評論快照，以備後續審計或恢復之用。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface ReviewArchiveRepository extends JpaRepository<ReviewArchive, Long> {

    /**
     * 根據原作者用戶名分頁查詢其被刪除的評論記錄，按刪除時間降序排列。
     * <p>
     * 通常用於用戶個人中心的「已刪除評論」視圖，或管理員審計特定用戶的違規歷史。
     * </p>
     *
     * @param username 原作者用戶名
     * @param pageable 分頁與排序參數對象
     * @return 歸檔記錄的分頁結果
     */
    Page<ReviewArchive> findByAuthor_UsernameOrderByDeletedAtDesc(String username, Pageable pageable);

    /**
     * 根據執行刪除操作的管理員/用戶 ID 分頁查詢歸檔記錄，按刪除時間降序排列。
     * <p>
     * 用於後台審計，追蹤特定管理員的刪除操作歷史。
     * </p>
     *
     * @param deletedById 執行刪除操作的用戶 ID
     * @param pageable    分頁與排序參數對象
     * @return 歸檔記錄的分頁結果
     */
    Page<ReviewArchive> findByDeletedByIdOrderByDeletedAtDesc(Long deletedById, Pageable pageable);

    /**
     * 根據原作者和執行刪除者同時查詢歸檔記錄，按刪除時間降序排列。
     * <p>
     * 用於精確定位「某個管理員刪除了某個用戶的哪條評論」，提供完整的審計鏈路。
     * </p>
     *
     * @param username    原作者用戶名
     * @param deletedById 執行刪除操作的用戶 ID
     * @param pageable    分頁與排序參數對象
     * @return 歸檔記錄的分頁結果
     */
    Page<ReviewArchive> findByAuthor_UsernameAndDeletedByIdOrderByDeletedAtDesc(
            String username, Long deletedById, Pageable pageable);
}