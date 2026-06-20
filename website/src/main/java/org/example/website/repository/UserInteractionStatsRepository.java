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

    Optional<UserInteractionStats> findByUsernameAndType(String username, String type);

    //  核心方法：更新最後查看時間為當前時間
    @Modifying
    @Transactional
    @Query("UPDATE UserInteractionStats u SET u.lastViewedAt = :now WHERE u.username = :username AND u.type = :type")
    int updateLastViewedAt(String username, String type, LocalDateTime now);

    // 如果不存在，則插入一條新記錄（初始化）
    @Modifying
    @Transactional
    @Query("INSERT INTO UserInteractionStats (username, type, lastViewedAt) VALUES (:username, :type, :now)")
    void insertInitialRecord(String username, String type, LocalDateTime now);
}