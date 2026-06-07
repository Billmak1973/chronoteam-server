package org.example.website.repository;

import org.example.website.entity.SellApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SellApplicationRepository extends JpaRepository<SellApplication, Long> {
    // 查詢用戶的所有出售申請
    Page<SellApplication> findByCustomer_UsernameOrderByCreatedAtDesc(String username, Pageable pageable);
    List<SellApplication> findByCustomer_UsernameOrderByCreatedAtDesc(String username);

    // 🟢 新增：按交易模式查詢（BUYOUT 或 CONSIGNMENT）
    Page<SellApplication> findByCustomer_UsernameAndTransactionModeOrderByCreatedAtDesc(
            String username,
            SellApplication.TransactionMode transactionMode,
            Pageable pageable
    );
    List<SellApplication> findByCustomer_UsernameAndTransactionModeOrderByCreatedAtDesc(
            String username,
            SellApplication.TransactionMode transactionMode
    );

    // 根據狀態查詢
    Page<SellApplication> findByStatusOrderByCreatedAtDesc(SellApplication.ApplicationStatus status, Pageable pageable);
}