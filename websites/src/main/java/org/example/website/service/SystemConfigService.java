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

    // 獲取基礎快遞費
    public BigDecimal getShippingFee() {
        return repository.findById("SHIPPING_FEE")
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(null);
    }

    // 獲取免郵費門檻
    public BigDecimal getFreeShippingThreshold() {
        return repository.findById("FREE_SHIPPING_THRESHOLD")
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(null);
    }

    /**
     * 獲取退貨天數
     * @return 若無配置、值為 0 或解析失敗，則返回 null
     */
    public Integer getReturnDays() {
        return repository.findById("RETURN_DAYS")
                .map(c -> {
                    try {
                        int days = Integer.parseInt(c.getConfigValue());
                        return days > 0 ? days : null;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * 獲取換貨天數
     * @return 若無配置、值為 0 或解析失敗，則返回 null
     */
    public Integer getExchangeDays() {
        return repository.findById("EXCHANGE_DAYS")
                .map(c -> {
                    try {
                        int days = Integer.parseInt(c.getConfigValue());
                        return days > 0 ? days : null;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * 獲取線下付款/取貨保留天數
     * @return 若無配置或解析失敗，則返回 null
     */
    public Integer getOfflinePaymentDays() {
        return repository.findById("OFFLINE_PAYMENT_DAYS")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
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

    // 獲取配送模式
    public String getDeliveryMode() {
        return repository.findById("DELIVERY_MODE")
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    // 獲取指定配送星期 (返回 List<Integer>，例如 [1, 2] 代表週一、週二)
    public List<Integer> getDeliverySpecificDays() {
        return repository.findById("DELIVERY_SPECIFIC_DAYS")
                .map(c -> {
                    try {
                        return Arrays.stream(c.getConfigValue().split(","))
                                .map(Integer::parseInt)
                                .collect(Collectors.toList());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    // 獲取自定義配送天數
    public Integer getDeliveryCustomDays() {
        return repository.findById("DELIVERY_CUSTOM_DAYS")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * 獲取全局最晚下單時間 (截單時間)
     * @return 若無配置，則返回 null
     */
    public String getGlobalCutoffTime() {
        return repository.findById("GLOBAL_CUTOFF_TIME")
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    /**
     * 獲取截單日偏移量 (天)
     * @return 若無配置或解析失敗，則返回 null
     */
    public Integer getCutoffDayOffset() {
        return repository.findById("CUTOFF_DAY_OFFSET")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    // 獲取連續日子處理模式
    public String getContinuousDaysMode() {
        return repository.findById("CONTINUOUS_DAYS_MODE")
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    /**
     * 獲取網絡訂單(待付款)保留天數
     * @return 若無配置或解析失敗，則返回 null
     */
    public Integer getOnlineOrderRetentionDays() {
        return repository.findById("ONLINE_ORDER_RETENTION_DAYS")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * 獲取每日送貨開始時間
     * @return 若無配置，則返回 null
     */
    public String getDeliveryStartTime() {
        return repository.findById("DELIVERY_START_TIME")
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    /**
     * 獲取每日送貨結束時間
     * @return 若無配置，則返回 null
     */
    public String getDeliveryEndTime() {
        return repository.findById("DELIVERY_END_TIME")
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    /**
     * 獲取用戶最大允許的收貨地址數量
     * @return 若無配置或解析失敗，則返回 null
     */
    public Integer getMaxUserAddresses() {
        return repository.findById("MAX_USER_ADDRESSES")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * 獲取結帳時最大可選配送日期數量
     * @return 若無配置，則默認null
     */
    public Integer getMaxDeliveryDateOptions() {
        return repository.findById("MAX_DELIVERY_DATE_OPTIONS")
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }
}