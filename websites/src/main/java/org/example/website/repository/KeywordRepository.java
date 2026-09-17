package org.example.website.repository;

import org.example.website.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 搜索關鍵詞數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 主要用於管理熱門搜索詞、搜索歷史或自定義 SEO 關鍵詞。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    /**
     * 查詢所有關鍵詞記錄，並按創建時間降序排列。
     * <p>
     * 通常用於後台管理系統展示關鍵詞列表，或前端獲取最新的熱門搜索推薦。
     * </p>
     *
     * @return 按創建時間倒序排列的關鍵詞列表
     */
    List<Keyword> findAllByOrderByCreatedAtDesc();

    /**
     * 檢查指定的關鍵詞是否已經存在於數據庫中。
     * <p>
     * 通常用於在新增關鍵詞時進行防重校驗，避免重複插入相同的詞條。
     * </p>
     *
     * @param keyword 需要檢查的關鍵詞字符串
     * @return 若存在則返回 true，否則返回 false
     */
    boolean existsByKeyword(String keyword);
}