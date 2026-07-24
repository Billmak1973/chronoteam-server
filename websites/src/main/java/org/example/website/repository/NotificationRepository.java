package org.example.website.repository;

import org.example.website.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipient_UsernameOrderByCreatedAtDesc(String username);

    long countByRecipient_UsernameAndIsReadFalse(String username);

    // 按類型和用戶分頁查詢通知
    Page<Notification> findByTypeAndRecipient_UsernameOrderByCreatedAtDesc(
            Notification.NotificationType type, String username, Pageable pageable);

    // 查詢用戶所有未讀通知 (用於進入頁面時全部標記為已讀)
    List<Notification> findByRecipient_UsernameAndIsReadFalse(String username);
}