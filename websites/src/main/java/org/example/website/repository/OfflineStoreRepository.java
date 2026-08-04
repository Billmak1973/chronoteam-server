package org.example.website.repository;

import org.example.website.entity.OfflineStore;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OfflineStoreRepository extends JpaRepository<OfflineStore, Long> {
    Optional<OfflineStore> findByStoreCode(String storeCode);
    boolean existsByStoreCode(String storeCode);
    List<OfflineStore> findByIsActiveTrue(); // 獲取所有顯示中的店鋪 (供結帳頁使用)
}