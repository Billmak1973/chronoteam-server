package org.example.website.util;

import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * UID 生成器 - 隨機版本（動態長度）
 * 格式: C + YY(年份後兩位) + 動態長度隨機數字
 * 規則:
 * 1. 隨機生成，不可預測
 * 2. 數字部分長度動態增長（根據已生成數量）
 * 3. 不能出現連續兩個 '4'
 * 4. 不能出現連續三個或以上相同數字
 * 5. 確保唯一性（通過數據庫 UNIQUE 約束 + 重試機制）
 */
public class UidGenerator {

    private static final String PREFIX = "C";
    private static final SecureRandom secureRandom = new SecureRandom();

    // 記錄當前已生成的 UID 數量（用於計算最小位數）
    // 注意：這個計數器僅用於估算，實際唯一性由數據庫保證
    private static long estimatedCount = 0;

    /**
     * 獲取下一個合法的隨機 UID
     * @param currentDbCount 數據庫中現有的用戶數量（從 UserRepository.count() 獲取）
     */
    public static String nextUid(long currentDbCount) {
        String yearSuffix = String.valueOf(LocalDate.now().getYear()).substring(2);

        // 更新估算計數
        estimatedCount = Math.max(estimatedCount, currentDbCount);

        int maxRetries = 100;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            retryCount++;

            // 根據當前數量計算需要的最小位數
            int requiredDigits = getRequiredDigits(estimatedCount);

            // 計算該位數的最大值
            long maxValue = (long) Math.pow(10, requiredDigits) - 1;

            // 生成隨機數字
            long randomNum = secureRandom.nextLong(maxValue + 1);
            String numberPart = String.format("%0" + requiredDigits + "d", randomNum);

            // 驗證是否符合規則
            if (isValid(numberPart)) {
                estimatedCount++;
                return PREFIX + yearSuffix + numberPart;
            }
        }

        // 如果重試次數過多，增加位數
        int emergencyDigits = getRequiredDigits(estimatedCount) + 1;
        long maxValue = (long) Math.pow(10, emergencyDigits) - 1;
        long randomNum = secureRandom.nextLong(maxValue + 1);
        String numberPart = String.format("%0" + emergencyDigits + "d", randomNum);
        estimatedCount++;
        return PREFIX + yearSuffix + numberPart;
    }

    /**
     * 驗證數字字符串是否符合規則
     */
    private static boolean isValid(String numberPart) {
        char[] chars = numberPart.toCharArray();
        int len = chars.length;

        for (int i = 0; i < len; i++) {
            char c = chars[i];

            // 規則 1: 檢查連續兩個 '4'
            if (c == '4') {
                if (i > 0 && chars[i - 1] == '4') {
                    return false;
                }
            }

            // 規則 2: 檢查連續三個相同數字
            if (i >= 2) {
                if (chars[i] == chars[i - 1] && chars[i - 1] == chars[i - 2]) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 計算當前計數值需要的最小位數
     */
    private static int getRequiredDigits(long count) {
        if (count < 1000) return 3;      // 0-999: 3位
        if (count < 10000) return 4;     // 1000-9999: 4位
        if (count < 100000) return 5;    // 10000-99999: 5位
        if (count < 1000000) return 6;   // 100000-999999: 6位
        if (count < 10000000) return 7;  // 7位
        if (count < 100000000) return 8; // 8位
        if (count < 1000000000) return 9;// 9位
        return 10;                       // 10位及以上
    }
}