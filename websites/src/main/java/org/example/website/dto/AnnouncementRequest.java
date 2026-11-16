package org.example.website.dto;

import lombok.Data;
import org.example.website.entity.Announcement;

import java.util.List;

@Data
public class AnnouncementRequest {

    private String title;
    private String titleAlignment;
    private String content;
    private Announcement.AnnouncementType type;
    private Announcement.TargetAudience targetType;

    /**
     * 【核心修改】對應前端 <input type="checkbox" name="storeRoles">
     * Spring 會把多個勾選值自動綁定成 List：
     *   勾選「該店鋪全體工作人員」 → ["ALL_STORE_STAFF"]
     *   勾選銷售+經理             → ["SALES", "STORE_MANAGER"]
     * 原 targetStoreRoles (String) 因名字對不上前端，永遠綁不到值，已移除。
     */
    private List<String> storeRoles;

    // 以下字段僅在 targetType 為特定值時使用
    private String targetRole;
    private Long targetId;
    private Long targetStoreId;
    private Integer productId;
}