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

@Controller
@RequestMapping("/admin/settings")
public class AdminSettingsController {

    @Autowired
    private SiteSettingService siteSettingService;

    @GetMapping
    public String settingsPage(Model model) {
        String currentTheme = siteSettingService.getCardBorderTheme();
        model.addAttribute("currentTheme", currentTheme);
        return "admin/settings";
    }

    @PostMapping("/update-theme")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateCardTheme(@RequestParam String theme, RedirectAttributes redirectAttributes) {
        try {
            siteSettingService.updateCardBorderTheme(theme);
            redirectAttributes.addFlashAttribute("successMessage", "主题更新成功！");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "更新失败：" + e.getMessage());
        }
        return "redirect:/admin/settings";
    }
}