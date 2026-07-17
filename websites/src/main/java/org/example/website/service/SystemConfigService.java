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