package org.example.website.repository;

import org.example.website.entity.AdminPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminPenaltyRepository extends JpaRepository<AdminPenalty, Long> {

    // 查找用戶最新的一條生效中的 BAN 記錄
    Optional<AdminPenalty> findTopByTargetUsernameAndTypeAndStatusOrderByStartTimeDesc(
            String targetUsername,
            AdminPenalty.PenaltyType type,
            AdminPenalty.PenaltyStatus status
    );


    boolean existsByTargetUsernameAndTypeAndStatus(
            String targetUsername,
            AdminPenalty.PenaltyType type,
            AdminPenalty.PenaltyStatus status
    );

    // 按處罰類型查詢最新的一條記錄 (解決 BAN 和 BLACKLIST 衝突問題)
    Optional<AdminPenalty> findTopByTargetUsernameAndTypeOrderByStartTimeDesc(
            String targetUsername,
            AdminPenalty.PenaltyType type
    );

    Optional<AdminPenalty> findByNotificationId(Long notificationId);

}