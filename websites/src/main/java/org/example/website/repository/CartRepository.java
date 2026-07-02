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

    List<Cart> findByUser_UsernameOrderByCreatedAtDesc(String username);

    // 查询用户购物车中的特定商品
    Cart findByUser_UsernameAndProduct_ProductId(String username, Integer productId);

    // 删除用户的购物车商品
    void deleteByUser_UsernameAndProduct_ProductId(String username, Integer productId);

    // 统计用户购物车商品数量
    long countByUser_Username(String username);

    // 查询用户选中的商品
    List<Cart> findByUser_UsernameAndSelectedTrue(String username);

    // 删除用户选中的商品（结算后删除）
    void deleteByUser_UsernameAndSelectedTrue(String username);
}