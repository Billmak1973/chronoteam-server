package org.example.website.repository;

import org.example.website.entity.UserInteractionStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserInteractionStatsRepository extends JpaRepository<UserInteractionStats, Long> {

    Optional<UserInteractionStats> findByUser_UsernameAndType(String username, String type);

    //  核心方法：更新最後查看時間為當前時間
    @Modifying
    @Transactional
    @Query("UPDATE UserInteractionStats u SET u.lastViewedAt = :now WHERE u.user.username = :username AND u.type = :type")
    int updateLastViewedAt(String username, String type, LocalDateTime now);


    // 如果不存在，則插入一條新記錄（初始化）
    @Modifying
    @Transactional
    @Query("INSERT INTO UserInteractionStats (user, type, lastViewedAt) " +
            "SELECT u, :type, :now FROM User u WHERE u.username = :username")
    void insertInitialRecord(String username, String type, LocalDateTime now);
}