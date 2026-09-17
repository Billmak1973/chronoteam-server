package org.example.website.repository;

import org.example.website.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 購物車數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link Cart} 實體的自定義查詢與刪除方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * 根據用戶名查詢該用戶的所有購物車記錄，並按創建時間降序排列。
     * <p>
     * 通常用於在購物車頁面加載並展示用戶的購物車列表。
     * </p>
     *
     * @param username 用戶名
     * @return 按創建時間倒序排列的購物車記錄列表
     */
    List<Cart> findByUser_UsernameOrderByCreatedAtDesc(String username);

    /**
     * 查詢指定用戶購物車中是否包含特定的商品。
     * <p>
     * 通常用於在「加入購物車」時，檢查該商品是否已存在於購物車中，
     * 以便決定是新增一條記錄還是將現有記錄的數量 +1。
     * </p>
     *
     * @param username  用戶名
     * @param productId 商品 ID
     * @return 匹配的購物車記錄，若不存在則返回 null
     */
    Cart findByUser_UsernameAndProduct_ProductId(String username, Integer productId);

    /**
     * 根據用戶名和商品 ID，刪除購物車中的特定商品記錄。
     * <p>
     * 通常用於用戶點擊「移除商品」按鈕時的單項刪除操作。
     * </p>
     *
     * @param username  用戶名
     * @param productId 商品 ID
     */
    void deleteByUser_UsernameAndProduct_ProductId(String username, Integer productId);

    /**
     * 統計指定用戶購物車中的商品總數量。
     * <p>
     * 通常用於在網站導航欄右上角顯示購物車的數字角標 (Badge)。
     * </p>
     *
     * @param username 用戶名
     * @return 購物車中的商品總數量
     */
    long countByUser_Username(String username);

    /**
     * 查詢指定用戶購物車中所有已勾選（選中）的商品記錄。
     * <p>
     * 通常用於結算頁面，僅獲取用戶準備購買的商品列表以計算總價和創建訂單。
     * </p>
     *
     * @param username 用戶名
     * @return 已選中的購物車記錄列表
     */
    List<Cart> findByUser_UsernameAndSelectedTrue(String username);

    /**
     * 批量刪除指定用戶購物車中所有已勾選（選中）的商品記錄。
     * <p>
     * 通常用於訂單創建成功（結算完成）後，將已購買的商品從購物車中清理掉。
     * </p>
     *
     * @param username 用戶名
     */
    void deleteByUser_UsernameAndSelectedTrue(String username);
}