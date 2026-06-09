package org.example.website.repository;

import org.example.website.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // 根據訂單號和用戶名查詢，確保用戶只能操作自己的訂單（防越權）
    Optional<Order> findByOrderNoAndCustomer_Username(String orderNo, String username);

    List<Order> findByCustomer_UsernameOrderByCreatedAtDesc(String username);

}