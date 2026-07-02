package org.example.website.repository;

import org.example.website.entity.AdminPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminPenaltyRepository extends JpaRepository<AdminPenalty, Long> {

    //使用 TargetUser_Username 導航到 User 表的 username 字段
    Optional<AdminPenalty> findTopByTargetUser_UsernameAndTypeAndStatusOrderByStartTimeDesc(
            String username, // 參數名可以改為 username，類型依然是 String
            AdminPenalty.PenaltyType type,
            AdminPenalty.PenaltyStatus status
    );

    boolean existsByTargetUser_UsernameAndTypeAndStatus(
            String username,
            AdminPenalty.PenaltyType type,
            AdminPenalty.PenaltyStatus status
    );


    Optional<AdminPenalty> findTopByTargetUser_UsernameAndTypeOrderByStartTimeDesc(
            String username,
            AdminPenalty.PenaltyType type
    );

    Optional<AdminPenalty> findByNotificationId(Long notificationId);

}