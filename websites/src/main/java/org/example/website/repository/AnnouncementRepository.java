package org.example.website.repository;

import org.example.website.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @Query("SELECT a FROM Announcement a WHERE " +
            "(:title IS NULL OR :title = '' OR LOWER(a.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
            "(:type IS NULL OR a.type = :type) AND " +
            "(:isActive IS NULL OR a.isActive = :isActive) AND " +
            "(:createdAtStart IS NULL OR a.createdAt >= :createdAtStart) AND " +
            "(:createdAtEnd IS NULL OR a.createdAt <= :createdAtEnd) AND " +
            // 【新增】變更狀態篩選：createdAt = updatedAt 代表原文，否則代表已編輯
            "(:modified IS NULL OR (:modified = true AND a.createdAt <> a.updatedAt) OR (:modified = false AND a.createdAt = a.updatedAt)) AND " +
            // 【新增】操作者精準搜索：通過 publisherId 關聯 User 表進行精確匹配
            "(:publisherName IS NULL OR (SELECT u.username FROM User u WHERE u.id = a.publisherId) = :publisherName)")
    Page<Announcement> findByFilters(
            @Param("title") String title,
            @Param("type") Announcement.AnnouncementType type,
            @Param("isActive") Boolean isActive,
            @Param("createdAtStart") LocalDateTime createdAtStart,
            @Param("createdAtEnd") LocalDateTime createdAtEnd,
            @Param("modified") Boolean modified,      // true=重新編輯, false=原文
            @Param("publisherName") String publisherName, // 操作者用戶名（精準）
            Pageable pageable
    );
}