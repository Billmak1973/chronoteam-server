package org.example.website.repository;

import org.example.website.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {

    // 根據狀態查詢所有申訴
    List<Appeal> findByStatus(Appeal.AppealStatus status);

    List<Appeal> findByNotificationIdOrderByCreatedAtDesc(Long notificationId);

}