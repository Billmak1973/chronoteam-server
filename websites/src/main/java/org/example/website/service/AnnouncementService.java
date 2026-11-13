package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.AnnouncementRequest;
import org.example.website.entity.Announcement;
import org.example.website.entity.AnnouncementReceipt;
import org.example.website.entity.Staff;
import org.example.website.entity.User;
import org.example.website.repository.AnnouncementReceiptRepository;
import org.example.website.repository.AnnouncementRepository;
import org.example.website.repository.StaffRepository;
import org.example.website.repository.UserRepository;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReceiptRepository announcementReceiptRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    @Transactional
    public void publishAnnouncement(AnnouncementRequest request, User sender) {
        // 1. 保存 Announcement 記錄
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setTitleAlignment(request.getTitleAlignment() != null ? request.getTitleAlignment() : "LEFT");

        // 使用 Jsoup 清理 HTML，防止 XSS 攻擊
        String safeContent = Jsoup.clean(request.getContent(), Safelist.relaxed());
        announcement.setContent(safeContent);

        announcement.setType(request.getType());
        announcement.setTargetType(request.getTargetType());
        announcement.setTargetRole(request.getTargetRole());
        announcement.setTargetId(request.getTargetId());
        announcement.setIsActive(true);

        // 先保存公告，這樣 JPA 會為它生成 ID，方便後續關聯
        announcement = announcementRepository.save(announcement);

        // 2. 根據 targetType 決定發送邏輯
        List<User> recipients = new ArrayList<>();

        if (request.getTargetType() == Announcement.TargetAudience.ALL) {
            // 全體用戶：獲取所有用戶
            recipients = userRepository.findAll();
        } else if (request.getTargetType() == Announcement.TargetAudience.ALL_STAFF) {
            // 全體工作人員：排除 CUSTOMER
            recipients = userRepository.findByRoleNot(User.Role.CUSTOMER);
        } else if (request.getTargetType() == Announcement.TargetAudience.SPECIFIC_ROLE) {
            // 指定特定角色：根據 targetRole 查找對應用戶
            handleSpecificRoleAnnouncement(request, recipients);
        } else if (request.getTargetType() == Announcement.TargetAudience.SPECIFIC_STORE) {
            // 指定店鋪：處理店鋪角色
            handleSpecificStoreAnnouncement(request, recipients);

        } else {
            // 其餘選項（SPECIFIC_USER, SPECIFIC_PRODUCT）：僅保存公告，不執行批量發送
            return;
        }

        // 3. 批量構建並保存 AnnouncementReceipt (公告回執)
        if (!recipients.isEmpty()) {
            List<AnnouncementReceipt> receipts = new ArrayList<>();
            for (User user : recipients) {
                AnnouncementReceipt receipt = new AnnouncementReceipt();
                receipt.setAnnouncement(announcement);
                receipt.setUser(user);
                receipt.setIsRead(false);
                receipts.add(receipt);
            }

            // 批量保存回執記錄，大幅提升數據庫性能
            announcementReceiptRepository.saveAll(receipts);

            System.out.println("✅ 成功為 " + receipts.size() + " 位用戶生成了公告回執記錄。");
        } else {
            System.out.println("⚠️ 未找到符合條件的接收者，僅保存了公告記錄。");
        }
    }

    /**
     * 處理指定特定角色的公告發送
     */
    private void handleSpecificRoleAnnouncement(AnnouncementRequest request, List<User> recipients) {
        String targetRole = request.getTargetRole();

        if (targetRole == null || targetRole.isEmpty()) {
            System.out.println("⚠️ 未指定目標角色，跳過發送");
            return;
        }

        try {
            User.Role role = User.Role.valueOf(targetRole.toUpperCase());
            recipients.addAll(userRepository.findByRole(role));
            System.out.println("📢 為角色 " + targetRole + " 找到 " + recipients.size() + " 位用戶");
        } catch (IllegalArgumentException e) {
            System.out.println("❌ 無效的角色名稱: " + targetRole);
        }
    }

    // ==========================================
    // 【極簡版】按指定店鋪發送：直接匹配 staff_profiles.store_id
    // ==========================================
    private void handleSpecificStoreAnnouncement(AnnouncementRequest request, List<User> recipients) {
        Long storeId = request.getTargetStoreId();

        if (storeId == null) {
            System.out.println("⚠️ 未指定門店 ID，跳過 SPECIFIC_STORE 發送");
            return;
        }

        // 【情況 A 唯一邏輯】：直接查詢該門店下的所有員工記錄
        List<Staff> staffList = staffRepository.findByStore_StoreId(storeId);

        if (staffList == null || staffList.isEmpty()) {
            System.out.println("⚠️ 門店 " + storeId + " 下沒有找到任何員工記錄");
            return;
        }

        // 將 Staff 關聯的 User 實體加入接收者列表
        for (Staff staff : staffList) {
            if (staff.getUser() != null) {
                recipients.add(staff.getUser());
            }
        }

        System.out.println("🏪 門店 " + storeId + " 共找到 " + staffList.size() + " 位全體員工，已加入發送名單");
    }
}