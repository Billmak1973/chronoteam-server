package org.example.website.repository;

import org.example.website.entity.AdminPenalty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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



    Optional<AdminPenalty> findTopByTargetUser_UsernameAndTypeOrderByStartTimeDesc(
            String username,
            AdminPenalty.PenaltyType type
    );

    Optional<AdminPenalty> findByNotificationId(Long notificationId);

    Optional<AdminPenalty> findByAppealId(Long appealId);

    // 查找所有状态为 ACTIVE 且 结束时间早于当前时间 的记录
    List<AdminPenalty> findByStatusAndEndTimeBefore(AdminPenalty.PenaltyStatus status, LocalDateTime endTime);

    boolean existsByReviewId(Long reviewId);

    // 根據類型分頁查詢
    Page<AdminPenalty> findByType(AdminPenalty.PenaltyType type, Pageable pageable);

    // 根據狀態分頁查詢
    Page<AdminPenalty> findByStatus(AdminPenalty.PenaltyStatus status, Pageable pageable);

    // 根據類型和狀態組合分頁查詢
    Page<AdminPenalty> findByTypeAndStatus(AdminPenalty.PenaltyType type, AdminPenalty.PenaltyStatus status, Pageable pageable);

}