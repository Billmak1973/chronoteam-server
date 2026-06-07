package org.example.website.repository;

import org.example.website.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    // 查询用户的所有购物车商品
    List<Cart> findByCustomer_UsernameOrderByCreatedAtDesc(String username);

    // 查询用户购物车中的特定商品
    Cart findByCustomer_UsernameAndProduct_Id(String username, Integer productId);

    // 更新商品数量
    @Modifying
    @Query("UPDATE Cart c SET c.quantity = :quantity WHERE c.id = :id")
    void updateQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

    // 删除用户的购物车商品
    void deleteByCustomer_UsernameAndProduct_Id(String username, Integer productId);

    // 清空用户购物车
    void deleteByCustomer_Username(String username);

    // 统计用户购物车商品数量
    long countByCustomer_Username(String username);
}