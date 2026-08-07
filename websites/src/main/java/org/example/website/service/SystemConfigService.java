package org.example.website.service;

import org.example.website.entity.SystemConfig;
import org.example.website.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Map;

@Service
public class SystemConfigService {
    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    // 獲取基礎快遞費 (默認 50)
    public BigDecimal getShippingFee() {
        return repository.findById("SHIPPING_FEE")
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(new BigDecimal("50"));
    }

    // 獲取免郵費門檻 (默認 50000，即 5 萬)
    public BigDecimal getFreeShippingThreshold() {
        return repository.findById("FREE_SHIPPING_THRESHOLD")
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(new BigDecimal("50000"));
    }

    /**
     * 獲取退貨天數
     * @return 若無配置或值為 0，則返回 null (代表未開啟退貨功能)
     */
    public Integer getReturnDays() {
        return repository.findById("RETURN_DAYS")
                .map(c -> {
                    int days = Integer.parseInt(c.getConfigValue());
                    return days > 0 ? days : null; // 大於 0 才返回天數，否則返回 null
                })
                .orElse(null); // 數據庫中找不到該配置時也返回 null
    }

    /**
     * 獲取換貨天數
     * @return 若無配置或值為 0，則返回 null (代表未開啟換貨功能)
     */
    public Integer getExchangeDays() {
        return repository.findById("EXCHANGE_DAYS")
                .map(c -> {
                    int days = Integer.parseInt(c.getConfigValue());
                    return days > 0 ? days : null; // 大於 0 才返回天數，否則返回 null
                })
                .orElse(null); // 數據庫中找不到該配置時也返回 null
    }

    // 批量更新配置 (供管理員後台調用)
    public void updateConfigs(Map<String, String> configs) {
        configs.forEach((key, value) -> {
            SystemConfig config = repository.findById(key).orElse(new SystemConfig());
            config.setConfigKey(key);
            config.setConfigValue(value);
            repository.save(config);
        });
    }
}