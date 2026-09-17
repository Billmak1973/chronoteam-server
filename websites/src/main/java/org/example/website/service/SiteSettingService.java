package org.example.website.service;

import org.example.website.entity.SiteSetting;
import org.example.website.repository.SiteSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service // 標記為 Spring 的業務邏輯層（Service）元件，封裝核心業務邏輯
public class SiteSettingService {

    @Autowired    // 自動注入站點設置的資料訪問層物件，用於操作資料庫
    private SiteSettingRepository settingRepository;

    /** 獲取當前配置的卡片邊框主題，如果資料庫中沒有配置，則預設返回 "day"（白天主題）*/
    public String getCardBorderTheme() {
        return settingRepository.findByKey("card_border_theme")
                .map(SiteSetting::getValue)
                .orElse("day");
    }

    /** 更新卡片邊框主題的配置（包含新增與修改邏輯）*/
    public void updateCardBorderTheme(String theme) {

        SiteSetting setting = settingRepository.findByKey("card_border_theme")
                .orElse(new SiteSetting());

        setting.setKey("card_border_theme");
        setting.setValue(theme);
        setting.setDescription("产品卡片边框主题");
        setting.setUpdatedAt(LocalDateTime.now());

        settingRepository.save(setting);
    }
}