package org.example.website.service;

import org.example.website.entity.SystemConfig;
import org.example.website.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * 獲取線下付款/取貨保留天數
     * @return 若無配置，則默認返回 3 天
     */
    public Integer getOfflinePaymentDays() {
        return repository.findById("OFFLINE_PAYMENT_DAYS")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return 3;
                    }
                })
                .orElse(3); // 默認 3 天
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

    // 獲取配送模式 (默認為次日達)
    public String getDeliveryMode() {
        return repository.findById("DELIVERY_MODE")
                .map(SystemConfig::getConfigValue)
                .orElse("NEXT_DAY");
    }

    // 【新增】獲取指定配送星期 (返回 List<Integer>，例如 [1, 2] 代表週一、週二)
    public List<Integer> getDeliverySpecificDays() {
        return repository.findById("DELIVERY_SPECIFIC_DAYS")
                .map(c -> {
                    try {
                        return Arrays.stream(c.getConfigValue().split(","))
                                .map(Integer::parseInt)
                                .collect(Collectors.toList());
                    } catch (NumberFormatException e) {
                        return Arrays.asList(6, 7); // 異常時默認週末
                    }
                })
                .orElse(Arrays.asList(6, 7)); // 數據庫無配置時，默認週末 (6=週六, 7=週日)
    }

    // 獲取自定義配送天數 (默認為 3 天)
    public Integer getDeliveryCustomDays() {
        return repository.findById("DELIVERY_CUSTOM_DAYS")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return 3;
                    }
                })
                .orElse(3);
    }


    /**
     * 获取全局最晚下单时间 (截单时间)
     * @return 默认 "16:00"
     */
    public String getGlobalCutoffTime() {
        return repository.findById("GLOBAL_CUTOFF_TIME")
                .map(SystemConfig::getConfigValue)
                .orElse("16:00");
    }

    /**
     * 获取截单日偏移量 (天)
     * @return 默认 -1 (即送货日的前一天)
     */
    public Integer getCutoffDayOffset() {
        return repository.findById("CUTOFF_DAY_OFFSET")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .orElse(-1);
    }

    // 獲取連續日子處理模式 (默認為 GROUP)
    public String getContinuousDaysMode() {
        return repository.findById("CONTINUOUS_DAYS_MODE")
                .map(SystemConfig::getConfigValue)
                .orElse("GROUP");
    }

    /**
     * 獲取網絡訂單(待付款)保留天數
     * @return 若無配置，則默認返回 1 天 (24小時)
     */
    public Integer getOnlineOrderRetentionDays() {
        return repository.findById("ONLINE_ORDER_RETENTION_DAYS")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                })
                .orElse(1); // 默認 1 天
    }

}