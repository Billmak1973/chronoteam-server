package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.AnnouncementRequest;
import org.example.website.entity.Announcement;
import org.example.website.entity.AnnouncementReceipt; // 引入新的實體
import org.example.website.entity.User;
import org.example.website.repository.AnnouncementReceiptRepository; // 引入新的 Repository
import org.example.website.repository.AnnouncementRepository;
import org.example.website.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReceiptRepository announcementReceiptRepository; // 替換為 Receipt Repository
    private final UserRepository userRepository;

    @Transactional
    public void publishAnnouncement(AnnouncementRequest request, User sender) {
        // 1. 保存 Announcement 記錄
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setType(request.getType());
        announcement.setTargetType(request.getTargetType());
        announcement.setTargetRole(request.getTargetRole());
        announcement.setTargetId(request.getTargetId());
        announcement.setIsActive(true);

        // 先保存公告，這樣 JPA 會為它生成 ID，方便後續關聯
        announcement = announcementRepository.save(announcement);

        // 2. 根據 targetType 決定發送邏輯 (嚴格遵守：僅處理 ALL 和 ALL_STAFF)
        List<User> recipients = new ArrayList<>();

        if (request.getTargetType() == Announcement.TargetAudience.ALL) {
            // 全體用戶：獲取所有用戶
            recipients = userRepository.findAll();
        } else if (request.getTargetType() == Announcement.TargetAudience.ALL_STAFF) {
            // 全體工作人員：排除 CUSTOMER
            recipients = userRepository.findByRoleNot(User.Role.CUSTOMER);
        } else {
            // 其餘選項不能碰：僅保存了 Announcement，不執行任何批量發送邏輯
            return;
        }

        // 3. 批量構建並保存 AnnouncementReceipt (公告回執)
        if (!recipients.isEmpty()) {
            List<AnnouncementReceipt> receipts = new ArrayList<>();
            for (User user : recipients) {
                AnnouncementReceipt receipt = new AnnouncementReceipt();
                receipt.setAnnouncement(announcement); // 關聯剛剛保存的公告主體
                receipt.setUser(user);                 // 關聯接收的用戶
                receipt.setIsRead(false);              // 初始狀態為未讀
                // readAt 和 createdAt 會由實體類的默認值或 @CreationTimestamp 自動處理

                receipts.add(receipt);
            }

            // 批量保存回執記錄，大幅提升數據庫性能
            announcementReceiptRepository.saveAll(receipts);

            System.out.println("✅ 成功為 " + receipts.size() + " 位用戶生成了公告回執記錄。");
        } else {
            System.out.println("⚠️ 未找到符合條件的接收者，僅保存了公告記錄。");
        }
    }
}