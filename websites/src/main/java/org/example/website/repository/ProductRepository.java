package org.example.website.repository;

import org.example.website.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    //  核心修改：加入 actualBrands, hasOthers, mainBrands 參數
    @Query("SELECT p FROM Product p WHERE " +
            "p.visible = 1 AND " +  // <--- 這裡改成 1！
            "(:keyword IS NULL OR p.description LIKE %:keyword%) AND " +
            "(:categorySize = 0 OR p.category IN :category) AND " +
            // 品牌篩選邏輯：要麼匹配具體選擇的品牌，要麼在勾選了 others 時匹配非主流品牌
            "(:brandSize = 0 OR p.brand IN :actualBrands OR (:hasOthers = true AND p.brand NOT IN :mainBrands)) AND " +
            "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("category") List<String> category,
            @Param("categorySize") int categorySize,
            @Param("actualBrands") List<String> actualBrands, // 剔除 others 後的具體品牌
            @Param("brandSize") int brandSize,                // 原始 brand 列表的總長度
            @Param("hasOthers") boolean hasOthers,            // 是否勾選了 others
            @Param("mainBrands") List<String> mainBrands,     // 主流品牌黑名單
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    default Page<Product> searchProducts(Map<String, Object> filters, Pageable pageable) {
        String keyword = (String) filters.get("keyword");
        List<String> category = (List<String>) filters.get("category");
        List<String> brand = (List<String>) filters.get("brand");

        // 1. 計算集合大小
        int categorySize = (category != null) ? category.size() : 0;
        int brandSize = (brand != null) ? brand.size() : 0;

        // 2. 處理分類佔位符
        if (category == null || category.isEmpty()) {
            category = List.of("__DUMMY_CATEGORY__");
        }

        // 3. 定義主流品牌（這些品牌不會被歸類為 "others"）
        List<String> mainBrands = List.of("rolex", "omega", "patek", "ap", "cartier");

        boolean hasOthers = false;
        List<String> actualBrands;

        // 4. 拆解 brand 列表
        if (brand != null && !brand.isEmpty()) {
            // 檢查是否勾選了 "others"
            hasOthers = brand.contains("others");
            // 過濾掉 "others"，得到用戶勾選的具體品牌
            actualBrands = brand.stream()
                    .filter(b -> !"others".equals(b))
                    .collect(Collectors.toList());
        } else {
            actualBrands = List.of();
        }

        // 5. 防止 Hibernate 報錯：IN 子句不允許傳入空集合。
        // 如果 actualBrands 為空（例如用戶只勾選了 others），傳入佔位符
        if (actualBrands.isEmpty()) {
            actualBrands = List.of("__DUMMY_BRAND__");
        }

        // 6. 處理價格區間
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

        // 7. 調用修復後的查詢方法
        return searchProducts(
                keyword,
                category,
                categorySize,
                actualBrands,
                brandSize,
                hasOthers,
                mainBrands,
                minPrice,
                maxPrice,
                pageable
        );
    }

    List<Product> findByGroupCode(String groupCode);
}