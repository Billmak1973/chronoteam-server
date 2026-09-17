package org.example.website.repository;

import org.example.website.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 收藏夾數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link Favorite} 實體的自定義查詢方法。
 * </p>
 *
 * @author ChronoTeam
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * 根據用戶名查詢該用戶的所有收藏記錄，並按創建時間降序排列。
     * <p>
     * 通常用於在用戶中心的「我的收藏」頁面展示收藏列表。
     * </p>
     *
     * @param username 用戶名
     * @return 按創建時間倒序排列的收藏記錄列表
     */
    List<Favorite> findByUser_UsernameOrderByCreatedAtDesc(String username);

    /**
     * 根據用戶名和商品 ID 查詢特定的收藏記錄。
     * <p>
     * 通常用於判斷用戶是否已經收藏了某個商品，以便在商品詳情頁顯示正確的「收藏/取消收藏」按鈕狀態。
     * </p>
     *
     * @param username  用戶名
     * @param productId 商品 ID
     * @return 匹配的收藏記錄，若未收藏則返回 null
     */
    Favorite findByUser_UsernameAndProduct_ProductId(String username, Integer productId);

    /**
     * 查詢指定用戶所有已收藏的商品 ID 列表。
     * <p>
     * 使用 JPQL 僅提取商品 ID，避免加載完整的 Favorite 和 Product 實體，大幅提升查詢性能。
     * 通常用於在商品列表頁（如首頁、搜索結果頁）批量判斷哪些商品已被當前用戶收藏，以便前端顯示愛心圖標。
     * </p>
     *
     * @param username 用戶名
     * @return 該用戶收藏的所有商品 ID 列表
     */
    @Query("SELECT f.product.productId FROM Favorite f WHERE f.user.username = :username")
    List<Integer> findFavoriteProductIdsByUsername(@Param("username") String username);
}