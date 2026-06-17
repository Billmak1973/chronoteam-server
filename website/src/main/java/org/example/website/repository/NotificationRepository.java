package org.example.website.repository;

import org.example.website.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 查詢當前用戶的未讀消息數量
    long countByRecipientUsernameAndIsReadFalse(String username);

    // 查詢當前用戶的消息列表 (支持按類型篩選)
    Page<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    Page<Notification> findByRecipientUsernameAndTypeOrderByCreatedAtDesc(String username, Notification.NotificationType type, Pageable pageable);

    // 標記為已讀 (批量)
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientUsername = :username AND n.id IN :ids")
    void markAsRead(@Param("username") String username, @Param("ids") List<Long> ids);

    // 標記全部已讀
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientUsername = :username")
    void markAllAsRead(@Param("username") String username);

}