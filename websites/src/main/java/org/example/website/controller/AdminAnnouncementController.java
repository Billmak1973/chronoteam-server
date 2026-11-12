package org.example.website.controller;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.AnnouncementRequest;
import org.example.website.entity.User;
import org.example.website.service.AnnouncementService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping("/announcements")
    public String manageAnnouncementsPage(Model model) {
        return "admin/admin-announcements";
    }

    @PostMapping("/announcements/publish")
    @ResponseBody
    public String publishAnnouncement(@ModelAttribute AnnouncementRequest request) {
        try {
            // 獲取當前登錄的管理員用戶作為發送者
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User sender = null;
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                sender = (User) authentication.getPrincipal();
            }

            // 調用 Service 執行發佈邏輯
            announcementService.publishAnnouncement(request, sender);

            return "success"; // 前端根據此返回值判斷成功
        } catch (Exception e) {
            e.printStackTrace();
            return "error: " + e.getMessage();
        }
    }
}