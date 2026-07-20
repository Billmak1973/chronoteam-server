package org.example.website.service;

import org.example.website.entity.SiteSetting;
import org.example.website.repository.SiteSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SiteSettingService {

    @Autowired
    private SiteSettingRepository settingRepository;

    public String getCardBorderTheme() {
        return settingRepository.findByKey("card_border_theme")
                .map(SiteSetting::getValue)
                .orElse("day");
    }

    public void updateCardBorderTheme(String theme) {
        if (!"day".equals(theme) && !"night".equals(theme)) {
            throw new IllegalArgumentException("主题必须是 day 或 night");
        }

        SiteSetting setting = settingRepository.findByKey("card_border_theme")
                .orElse(new SiteSetting());

        setting.setKey("card_border_theme");
        setting.setValue(theme);
        setting.setDescription("产品卡片边框主题");
        setting.setUpdatedAt(LocalDateTime.now());

        settingRepository.save(setting);
    }
}