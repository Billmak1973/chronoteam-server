package org.example.website.repository;

import org.example.website.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipient_UsernameOrderByCreatedAtDesc(String username);

    long countByRecipient_UsernameAndIsReadFalse(String username);

}