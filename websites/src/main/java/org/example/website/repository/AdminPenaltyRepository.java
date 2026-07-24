package org.example.website.repository;

import org.example.website.entity.AdminPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdminPenaltyRepository extends JpaRepository<AdminPenalty, Long> {

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

    Optional<AdminPenalty> findByAppealId(Long appealId);

    // 查找所有状态为 ACTIVE 且 结束时间早于当前时间 的记录
    List<AdminPenalty> findByStatusAndEndTimeBefore(AdminPenalty.PenaltyStatus status, LocalDateTime endTime);

    boolean existsByReviewId(Long reviewId);

}