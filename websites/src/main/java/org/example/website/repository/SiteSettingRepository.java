package org.example.website.repository;

import org.example.website.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 網站全局設置數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於管理系統的鍵值對 (Key-Value) 配置項，
 * 例如：產品卡片邊框主題、網站名稱等全局參數。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {

    /**
     * 根據設置項的鍵 (Key) 查找對應的設置記錄。
     * <p>
     * 返回 {@link Optional} 以防止查無資料時發生空指針異常 (NullPointerException)。
     * </p>
     *
     * @param key 設置項的唯一標識鍵 (例如: "card_border_theme")
     * @return 包含設置記錄的 {@link Optional}，若無記錄則返回 {@link Optional#empty()}
     */
    Optional<SiteSetting> findByKey(String key);

    /**
     * 判斷資料庫中是否已經存在指定鍵 (Key) 的設置記錄。
     * <p>
     * 通常用於新增配置項前的防重複校驗，確保系統不會產生重複的配置鍵。
     * </p>
     *
     * @param key 設置項的唯一標識鍵
     * @return 若存在則返回 true，否則返回 false
     */
    boolean existsByKey(String key);
}