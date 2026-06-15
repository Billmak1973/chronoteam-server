package org.example.website.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.website.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    //  新增：检查同一订单同一商品是否已评价
    Review findByOrderNoAndProduct_Id(String orderNo, Integer productId);

    //  3. 统计某条评论的回复总数
    long countByParentId(Long parentId);

    // 分頁查詢根評論（支持動態排序）
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.parentId IS NULL")
    Page<Review> findByProduct_IdAndParentIdIsNull(@Param("productId") Integer productId, Pageable pageable);

    //  新增：返回該用戶對該商品的所有根評論列表
    List<Review> findByCustomer_UsernameAndProduct_IdAndParentIdIsNull(String username, Integer productId);

    // 2. 查询某条根评论的【楼中楼回复】（支持分页和动态排序）
    @Query("SELECT r FROM Review r WHERE r.parentId = :parentId")
    Page<Review> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);
}