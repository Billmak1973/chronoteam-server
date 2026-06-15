package org.example.website.repository;

import org.example.website.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByCustomer_Username(String username);

    List<Favorite> findByCustomer_UsernameOrderByCreatedAtDesc(String username);
    Favorite findByCustomer_UsernameAndProduct_Id(String username, Integer productId);

    //  新增：直接查詢當前用戶收藏的所有商品 ID (返回 List<Integer>)
    @Query("SELECT f.product.id FROM Favorite f WHERE f.customer.username = :username")
    List<Integer> findFavoriteProductIdsByUsername(@Param("username") String username);
}