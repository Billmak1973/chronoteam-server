package org.example.website.repository;

import org.example.website.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 登入日誌數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link LoginLog} 實體的安全審計與歷史查詢方法。
 * </p>
 *
 * @author ChronoTeam
 */
public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {

    /**
     * 查詢指定用戶最近的 10 條登入日誌，並按登入時間降序排列。
     * <p>
     * 通常用於用戶中心的「帳號安全」或「最近登入記錄」頁面展示。
     * </p>
     *
     * @param username 用戶名
     * @return 包含最近 10 條登入記錄的列表
     */
    List<LoginLog> findTop10ByUser_UsernameOrderByLoginTimeDesc(String username);

    /**
     * 查詢指定用戶在特定 IP 地址下的最新一條登入日誌。
     * <p>
     * 通常用於安全風控系統，判斷該用戶是否曾經使用過當前 IP 登入過，
     * 以決定是否需要觸發額外的驗證（如短信驗證碼、圖形驗證碼或異地登入警告）。
     * </p>
     *
     * @param username  用戶名
     * @param ipAddress 登入時的 IP 地址
     * @return 包含該 IP 下最新登入記錄的 {@link Optional}，若無記錄則返回 {@link Optional#empty()}
     */
    Optional<LoginLog> findTopByUser_UsernameAndIpAddressOrderByLoginTimeDesc(String username, String ipAddress);
}