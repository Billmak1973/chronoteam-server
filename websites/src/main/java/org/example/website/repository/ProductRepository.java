package org.example.website.repository;

import org.example.website.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link Product} 實體的複雜篩選、排序及原子計數更新方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    /**
     * 【核心查詢】多條件動態篩選商品 (支援關鍵字、分類、品牌、價格區間)。
     * <p>
     * 特別處理了「其他品牌 (others)」的邏輯：若用戶勾選了 others，則匹配不在主流品牌名單中的商品。
     * 使用佔位符防止 Hibernate 在 IN 子句中傳入空集合而報錯。
     * </p>
     *
     * @param keyword      搜索關鍵字 (商品描述)
     * @param category     分類列表
     * @param categorySize 分類列表大小 (用於判斷是否為空)
     * @param actualBrands 剔除 "others" 後的具體品牌列表
     * @param brandSize    原始品牌列表大小
     * @param hasOthers    是否勾選了 "others" 品牌選項
     * @param mainBrands   主流品牌黑名單 (用於 others 邏輯)
     * @param minPrice     最低價格
     * @param maxPrice     最高價格
     * @param pageable     分頁與排序參數
     * @return 符合篩選條件的商品分頁結果
     */
    @Query("SELECT p FROM Product p WHERE " +
            "p.visible = 1 AND " +
            "(:keyword IS NULL OR p.description LIKE %:keyword%) AND " +
            "(:categorySize = 0 OR p.category IN :category) AND " +
            "(:brandSize = 0 OR p.brand IN :actualBrands OR (:hasOthers = true AND p.brand NOT IN :mainBrands)) AND " +
            "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("category") List<String> category,
            @Param("categorySize") int categorySize,
            @Param("actualBrands") List<String> actualBrands,
            @Param("brandSize") int brandSize,
            @Param("hasOthers") boolean hasOthers,
            @Param("mainBrands") List<String> mainBrands,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    /**
     * 【輔助方法】將前端傳入的 Map 過濾器轉換為具體參數，並調用核心的 {@link #searchProducts} 方法。
     * <p>
     * 負責處理價格區間字符串解析、空集合佔位符填充等預處理邏輯，保持 Controller 層的簡潔。
     * </p>
     *
     * @param filters  包含篩選條件的 Map (keyword, category, brand, priceRange)
     * @param pageable 分頁與排序參數
     * @return 符合篩選條件的商品分頁結果
     */
    default Page<Product> searchProducts(Map<String, Object> filters, Pageable pageable) {
        String keyword = (String) filters.get("keyword");
        List<String> category = (List<String>) filters.get("category");
        List<String> brand = (List<String>) filters.get("brand");

        int categorySize = (category != null) ? category.size() : 0;
        int brandSize = (brand != null) ? brand.size() : 0;

        if (category == null || category.isEmpty()) {
            category = List.of("__DUMMY_CATEGORY__");
        }

        List<String> mainBrands = List.of("rolex", "omega", "patek", "ap", "cartier");
        boolean hasOthers = false;
        List<String> actualBrands;

        if (brand != null && !brand.isEmpty()) {
            hasOthers = brand.contains("others");
            actualBrands = brand.stream()
                    .filter(b -> !"others".equals(b))
                    .collect(Collectors.toList());
        } else {
            actualBrands = List.of();
        }

        if (actualBrands.isEmpty()) {
            actualBrands = List.of("__DUMMY_BRAND__");
        }

        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        String priceRange = (String) filters.get("priceRange");

        if (priceRange != null) {
            switch (priceRange) {
                case "0-5000" -> { minPrice = BigDecimal.ZERO; maxPrice = new BigDecimal("5000"); }
                case "5000-20000" -> { minPrice = new BigDecimal("5000"); maxPrice = new BigDecimal("20000"); }
                case "20000-50000" -> { minPrice = new BigDecimal("20000"); maxPrice = new BigDecimal("50000"); }
                case "50000+" -> { minPrice = new BigDecimal("50000"); }
            }
        }

        return searchProducts(
                keyword, category, categorySize, actualBrands, brandSize,
                hasOthers, mainBrands, minPrice, maxPrice, pageable
        );
    }

    /**
     * 根據同款分組碼 (Group Code) 查詢所有相關的商品變體。
     * <p>
     * 通常用於商品詳情頁，展示同一款手錶的不同成色/狀態選項。
     * </p>
     *
     * @param groupCode 同款分組碼
     * @return 屬於該分組的商品列表
     */
    List<Product> findByGroupCode(String groupCode);

    /**
     * 查詢首頁推薦排序權重大於指定值的所有商品。
     *
     * @param order 排序權重閾值
     * @return 首頁推薦商品列表
     */
    List<Product> findAllByHomeDisplayOrderGreaterThan(Integer order);

    /**
     * 查詢首頁推薦排序權重大於或等於指定值的所有商品。
     *
     * @param order 排序權重閾值
     * @return 首頁推薦商品列表
     */
    List<Product> findAllByHomeDisplayOrderGreaterThanEqual(Integer order);

    /**
     * 查詢首頁推薦排序權重在指定範圍內的所有商品。
     * <p>
     * 通常用於調整某個商品的推薦順位時，批量更新其前後商品的排序值。
     * </p>
     *
     * @param start 起始權重
     * @param end   結束權重
     * @return 範圍內的商品列表
     */
    List<Product> findAllByHomeDisplayOrderBetween(Integer start, Integer end);

    /**
     * 獲取當前數據庫中首頁推薦商品的最大排序權重值。
     * <p>
     * 通常用於管理員新增首頁推薦商品時，自動分配下一個可用的排序號 (max + 1)。
     * </p>
     *
     * @return 最大排序權重值 (若無推薦商品則返回 null)
     */
    @Query("SELECT MAX(p.homeDisplayOrder) FROM Product p WHERE p.homeDisplayOrder IS NOT NULL AND p.homeDisplayOrder > 0")
    Integer findMaxHomeDisplayOrder();

    /**
     * 【原子操作】增加指定商品的收藏人數。
     * <p>
     * 使用 JPQL UPDATE 直接在數據庫層面執行加法，避免先查詢後更新帶來的併發覆蓋問題。
     * </p>
     *
     * @param productId 商品 ID
     * @return 受影響的行數 (通常為 1)
     */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.favoriteCount = p.favoriteCount + 1 WHERE p.productId = :productId")
    int incrementFavoriteCount(@Param("productId") Integer productId);

    /**
     * 【原子操作】減少指定商品的收藏人數。
     * <p>
     * 使用 CASE WHEN 確保計數器不會因併發問題減到負數。
     * </p>
     *
     * @param productId 商品 ID
     * @return 受影響的行數 (通常為 1)
     */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.favoriteCount = CASE WHEN p.favoriteCount > 0 THEN p.favoriteCount - 1 ELSE 0 END WHERE p.productId = :productId")
    int decrementFavoriteCount(@Param("productId") Integer productId);

    /**
     * 【原子操作】增加指定商品的到貨通知訂閱人數。
     * <p>
     * 使用原生 SQL (nativeQuery = true) 配合 COALESCE，徹底解決 JPQL 中 NULL + 1 = NULL 的潛在問題。
     * </p>
     *
     * @param productId 商品 ID
     * @return 受影響的行數 (通常為 1)
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE product SET stock_notification_count = COALESCE(stock_notification_count, 0) + 1 WHERE prod_id = :productId", nativeQuery = true)
    int incrementStockNotificationCount(@Param("productId") Integer productId);

    /**
     * 【原子操作】減少指定商品的到貨通知訂閱人數。
     * <p>
     * 使用原生 SQL 配合 CASE WHEN 和 COALESCE，確保計數器不會減到負數，且能安全處理初始 NULL 值。
     * </p>
     *
     * @param productId 商品 ID
     * @return 受影響的行數 (通常為 1)
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE product SET stock_notification_count = CASE WHEN COALESCE(stock_notification_count, 0) > 0 THEN stock_notification_count - 1 ELSE 0 END WHERE prod_id = :productId", nativeQuery = true)
    int decrementStockNotificationCount(@Param("productId") Integer productId);

    @Query("SELECT p FROM Product p WHERE p.brand = :brand ORDER BY p.description ASC")
    List<Product> findByBrand(@Param("brand") String brand);
}