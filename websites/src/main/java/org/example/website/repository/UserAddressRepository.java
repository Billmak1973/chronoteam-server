package org.example.website.repository;
import org.example.website.entity.User;
import org.example.website.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    // 按 ranking 升序獲取用戶的所有地址 (ranking 越小越靠前)
    List<UserAddress> findByUserOrderByRankingAsc(User user);

    // 獲取用戶特定 ranking 的地址 (例如 ranking=1 為主地址, ranking=2 為備用地址)
    List<UserAddress> findByUserAndRanking(User user, Integer ranking);

    List<UserAddress> findByUserOrderByRankingDesc(User user);

}