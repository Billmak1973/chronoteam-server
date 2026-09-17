package org.example.website.controller;

import org.example.website.service.SiteSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller// 標記為 Spring MVC 的控制器，用於處理 Web 請求並返回視圖名稱（Thymeleaf 模板）
@RequestMapping("/admin/settings")
public class AdminSettingsController {

    @Autowired // 自動注入站點設置的業務邏輯層物件
    private SiteSettingService siteSettingService;

    @GetMapping    // 處理 GET 請求，用於渲染後台站點設置頁面
    public String settingsPage(Model model) {
        String currentTheme = siteSettingService.getCardBorderTheme();
        // 將當前主題資料放入 Model 中，供前端 Thymeleaf 模板渲染使用（例如：回顯目前選中的單選框）
        model.addAttribute("currentTheme", currentTheme);
        return "admin/settings";
    }

    // 處理 POST 請求，用於接收表單提交並更新卡片邊框主題
    @PostMapping("/update-theme")
    // 權限校驗註解：限制只有擁有 ADMIN 角色的用戶才能存取此接口，防止越權操作
    @PreAuthorize("hasRole('ADMIN')")
    public String updateCardTheme(@RequestParam String theme, RedirectAttributes redirectAttributes) {
        try {
            // 呼叫 Service 層執行更新資料庫的操作
            siteSettingService.updateCardBorderTheme(theme);
            // 更新成功後，添加一筆快閃（Flash）屬性，用於在重定向後的頁面顯示成功提示訊息
            redirectAttributes.addFlashAttribute("successMessage", "主题更新成功！");
        } catch (Exception e) {
            // 如果更新過程中發生異常，捕獲並添加錯誤提示訊息
            redirectAttributes.addFlashAttribute("errorMessage", "更新失败：" + e.getMessage());
        }
        // 重定向回設置頁面（觸發 GET 請求重新載入頁面，並顯示剛才設置的提示消息）
        return "redirect:/admin/settings";
    }
}