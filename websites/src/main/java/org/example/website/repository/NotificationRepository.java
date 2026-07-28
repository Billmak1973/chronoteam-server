package org.example.website.repository;

import org.example.website.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByRecipient_UsernameAndIsReadFalse(String username);

    // 按類型和用戶分頁查詢通知
    Page<Notification> findByTypeAndRecipient_UsernameOrderByCreatedAtDesc(
            Notification.NotificationType type, String username, Pageable pageable);


    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :userId AND n.isRead = false")
    int markAllAsReadByRecipientUserId(@Param("userId") Long userId);
}