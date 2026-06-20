package org.example.website.repository;

import org.example.website.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 查詢某個用戶的所有通知，按時間倒序
    List<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String username);

    // 查詢未讀通知數量
    long countByRecipientUsernameAndIsReadFalse(String username);
}