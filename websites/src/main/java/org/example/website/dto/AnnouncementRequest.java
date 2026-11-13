package org.example.website.dto;

import lombok.Data;
import org.example.website.entity.Announcement;

@Data
public class AnnouncementRequest {
    private String title;
    private String titleAlignment;
    private String content;
    private Announcement.AnnouncementType type;
    private Announcement.TargetAudience targetType;
    private String targetStoreRoles;

    // 以下字段僅在 targetType 為特定值時使用，ALL 和 ALL_STAFF 時為 null
    private String targetRole;
    private Long targetId;

    private Long targetStoreId;
}