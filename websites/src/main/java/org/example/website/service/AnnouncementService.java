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
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReceiptRepository announcementReceiptRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    @Transactional
    public void publishAnnouncement(AnnouncementRequest request,  Long publisherId) {

        // ==========================================
        // 【核心修復 1】在 entity 賦值與 save() 之前完成 targetRole 標準化
        // 規則（嚴格按你的要求）：
        //   ✅ 只有勾選「該店鋪全體工作人員」→ 寫 "ALL_STORE_STAFF"
        //   ❌ 其餘勾選（SALES / APPRAISER / STORE_MANAGER）→ 寫實際角色組合
        // ==========================================
        if (request.getTargetType() == Announcement.TargetAudience.SPECIFIC_STORE) {
            List<String> roles = request.getStoreRoles();

            if (roles != null && !roles.isEmpty()) {
                if (roles.contains("ALL_STORE_STAFF")) {
                    request.setTargetRole("ALL_STORE_STAFF");
                } else {
                    // 例如 "SALES,APPRAISER"，留給未來做角色篩選用
                    request.setTargetRole(String.join(",", roles));
                }
            } else {
                // 兜底：前端沒傳任何勾選值時，默認視為全體員工
                request.setTargetRole("ALL_STORE_STAFF");
            }
        }

        // 1. 保存 Announcement 記錄（此時 targetRole 已是正確值，不再會落 NULL）
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
        announcement.setTargetStoreId(request.getTargetStoreId());
        // 【核心修改】：記錄操作者 ID
        if (publisherId != null) {
            announcement.setPublisherId(publisherId);
        }
        announcement.setIsActive(true);

        announcement = announcementRepository.save(announcement);

        // 2. 根據 targetType 決定發送邏輯（保持不變）
        List<User> recipients = new ArrayList<>();

        if (request.getTargetType() == Announcement.TargetAudience.ALL) {
            recipients = userRepository.findAll();
        } else if (request.getTargetType() == Announcement.TargetAudience.ALL_STAFF) {
            recipients = userRepository.findByRoleNot(User.Role.CUSTOMER);
        } else if (request.getTargetType() == Announcement.TargetAudience.SPECIFIC_ROLE) {
            handleSpecificRoleAnnouncement(request, recipients);
        } else if (request.getTargetType() == Announcement.TargetAudience.SPECIFIC_STORE) {
            handleSpecificStoreAnnouncement(request, recipients);
        }else if (request.getTargetType() == Announcement.TargetAudience.SPECIFIC_USER) {
            // 【新增】處理指定單個用戶
            handleSpecificUserAnnouncement(request, recipients);
        } else {
            // 其餘選項（例如 SPECIFIC_PRODUCT）：僅保存公告，不執行批量發送
            return;
        }

        // 3. 批量構建並保存 AnnouncementReceipt（保持不變）
        if (!recipients.isEmpty()) {
            List<AnnouncementReceipt> receipts = new ArrayList<>();
            for (User user : recipients) {
                AnnouncementReceipt receipt = new AnnouncementReceipt();
                receipt.setAnnouncement(announcement);
                receipt.setUser(user);
                receipt.setIsRead(false);
                receipts.add(receipt);
            }
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
    // 按指定店鋪發送：匹配 store_id -> user_id -> 過濾 user_type
    // ==========================================
    private void handleSpecificStoreAnnouncement(AnnouncementRequest request, List<User> recipients) {
        Long storeId = request.getTargetStoreId();
        String targetRoleStr = request.getTargetRole(); // 例如 "ALL_STORE_STAFF" 或 "SALES,APPRAISER"

        if (storeId == null) {
            System.out.println("⚠️ 未指定門店 ID，跳過 SPECIFIC_STORE 發送");
            return;
        }

        // 1. 首先獲取 staff_profiles 裡符合 store_id 的記錄
        List<Staff> staffList = staffRepository.findByStore_StoreId(storeId);

        if (staffList == null || staffList.isEmpty()) {
            System.out.println("⚠️ 門店 " + storeId + " 下沒有找到任何員工記錄");
            return;
        }

        // 2. 判斷是否為全體員工 (保留你原有的正確邏輯)
        boolean isAllStoreStaff = "ALL_STORE_STAFF".equals(targetRoleStr);

        // 3. 如果不是全體員工，則解析目標角色列表 (例如 ["SALES", "APPRAISER"])
        List<String> targetRoles = new ArrayList<>();
        if (!isAllStoreStaff && targetRoleStr != null && !targetRoleStr.isEmpty()) {
            targetRoles = Arrays.asList(targetRoleStr.split(","));
        }

        // 4. 遍歷匹配並過濾符合資格的 user
        int matchedCount = 0;
        for (Staff staff : staffList) {
            User user = staff.getUser(); // 自動匹配 staff_profiles.user_id 與 users.user_id
            if (user == null) {
                continue;
            }

            // 如果是 ALL_STORE_STAFF，或者該用戶的角色在目標角色列表中，則加入發送名單
            if (isAllStoreStaff || targetRoles.contains(user.getRole().name())) {
                recipients.add(user);
                matchedCount++;
            }
        }

        System.out.println("🏪 門店 " + storeId + " 共篩選出 " + matchedCount + " 位符合資格的員工，已加入發送名單 (目標角色: " + targetRoleStr + ")");
    }

    /**
     * 【新增】處理指定單個用戶的公告發送
     */
    private void handleSpecificUserAnnouncement(AnnouncementRequest request, List<User> recipients) {
        Long targetId = request.getTargetId();

        if (targetId == null) {
            System.out.println("⚠️ 未指定目標用戶 ID，跳過 SPECIFIC_USER 發送");
            return;
        }

        // 根據 targetId (即 user_id) 查找用戶並加入接收者列表
        userRepository.findById(targetId).ifPresent(user -> {
            recipients.add(user);
            System.out.println("👤 為指定用戶 ID: " + targetId + " (" + user.getUsername() + ") 生成了公告發送任務");
        });
    }
}