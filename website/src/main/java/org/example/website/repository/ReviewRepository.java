package org.example.website.repository;

import org.example.website.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    // 根據商品 ID 查詢所有評論，並按時間倒序排列
    List<Review> findByProduct_IdOrderByCreatedAtDesc(Integer productId);
}