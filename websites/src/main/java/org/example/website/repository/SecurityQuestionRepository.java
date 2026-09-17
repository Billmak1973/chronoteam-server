package org.example.website.repository;

import org.example.website.entity.SecurityQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 安全問答數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link SecurityQuestion} 實體的自定義查詢方法，
 * 主要用於帳戶安全驗證、密碼找回及安全等級評估等場景。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface SecurityQuestionRepository extends JpaRepository<SecurityQuestion, Long> {

    /**
     * 查詢指定用戶的所有安全問答記錄，並按創建時間降序排列。
     * <p>
     * 通常用於以下業務場景：
     * 1. 用戶在「帳戶安全設置」頁面查看自己已設置的安全問題列表。
     * 2. 後端評估帳戶安全等級（例如：檢查該列表是否為空，以決定是否加分）。
     * 3. 修改密碼時，用於比對用戶輸入的答案與數據庫中最新設置的答案。
     * </p>
     *
     * @param username 目標用戶的用戶名
     * @return 該用戶的安全問答記錄列表（按創建時間從新到舊排序）
     */
    List<SecurityQuestion> findByUser_UsernameOrderByCreatedAtDesc(String username);
}