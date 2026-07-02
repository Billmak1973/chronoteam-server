package org.example.website.repository;

import org.example.website.entity.SellApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SellApplicationRepository extends JpaRepository<SellApplication, Long> {

    List<SellApplication> findByUser_UsernameOrderByCreatedAtDesc(String username);

    Page<SellApplication> findByUser_UsernameAndTransactionModeOrderByCreatedAtDesc(
            String username,
            SellApplication.TransactionMode transactionMode,
            Pageable pageable
    );

    // 根據狀態查詢
    Page<SellApplication> findByStatusOrderByCreatedAtDesc(SellApplication.ApplicationStatus status, Pageable pageable);
}