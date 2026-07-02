package org.example.website.repository;

import org.example.website.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUser_UsernameOrderByCreatedAtDesc(String username);
    Favorite findByUser_UsernameAndProduct_ProductId(String username, Integer productId);


    @Query("SELECT f.product.productId FROM Favorite f WHERE f.user.username = :username")
    List<Integer> findFavoriteProductIdsByUsername(@Param("username") String username);
}