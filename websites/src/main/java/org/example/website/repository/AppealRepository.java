package org.example.website.repository;

import org.example.website.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {
    // 檢查用戶是否已經對某個通知提交過「待處理」的申訴 (防止重複提交)
    Optional<Appeal> findByNotificationIdAndStatus(Long notificationId, Appeal.AppealStatus status);

    Optional<Appeal> findTopByNotificationIdOrderByCreatedAtDesc(Long notificationId);

    // 根據狀態查詢所有申訴
    List<Appeal> findByStatus(Appeal.AppealStatus status);

}