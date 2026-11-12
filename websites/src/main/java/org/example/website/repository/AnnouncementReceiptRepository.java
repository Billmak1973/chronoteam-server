package org.example.website.repository;

import org.example.website.entity.Announcement;
import org.example.website.entity.AnnouncementReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AnnouncementReceiptRepository extends JpaRepository<AnnouncementReceipt, Long> {

    /**
     * 分頁查詢用戶的特定類型公告 (例如：到貨通知 STOCK)
     */
    Page<AnnouncementReceipt> findByUser_IdAndAnnouncement_TypeOrderByCreatedAtDesc(
            Long userId,
            Announcement.AnnouncementType type,
            Pageable pageable
    );

    /**
     * 統計用戶某類型的未讀公告數量 (用於導航欄紅點)
     */
    long countByUser_UsernameAndAnnouncement_TypeAndIsReadFalse(
            String username,
            Announcement.AnnouncementType type
    );

    /**
     * 批量將用戶的某類公告標記為已讀
     * 【核心修復】：UPDATE 語句必須使用子查詢來關聯 announcement 表
     */
    @Modifying
    @Transactional
    @Query("UPDATE AnnouncementReceipt ar SET ar.isRead = true " +
            "WHERE ar.user.id = :userId " +
            "AND ar.announcement.id IN (SELECT a.id FROM Announcement a WHERE a.type = :type) " +
            "AND ar.isRead = false")
    int markAllAsReadByUserAndType(@Param("userId") Long userId, @Param("type") Announcement.AnnouncementType type);

    /**
     * 分頁查詢用戶的特定類型公告 (例如：到貨通知 STOCK)
     * 【修復】：使用 @EntityGraph 替代 JOIN FETCH，解決 Hibernate 6 Count Query 報錯
     */
    @EntityGraph(attributePaths = {"announcement"})
    @Query("SELECT ar FROM AnnouncementReceipt ar WHERE ar.user.id = :userId AND ar.announcement.type IN ('SYSTEM','MAINTENANCE', 'PROMOTION')")
    Page<AnnouncementReceipt> findPlatformAnnouncementsByUserId(@Param("userId") Long userId, Pageable pageable);


    /**
     * 【新增】批量將用戶的特定類型公告標記為已讀
     */
    @Modifying
    @Transactional
    @Query("UPDATE AnnouncementReceipt ar SET ar.isRead = true " +
            "WHERE ar.user.id = :userId " +
            "AND ar.announcement.id IN (SELECT a.id FROM Announcement a WHERE a.type IN :types) " +
            "AND ar.isRead = false")
    int markAllAsReadByUserIdAndTypes(@Param("userId") Long userId, @Param("types") List<Announcement.AnnouncementType> types);
}