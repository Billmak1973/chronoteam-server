package org.example.website.util; // 或者放在 security 包下

import org.example.website.entity.User;
import org.example.website.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    /**
     * 獲取當前登錄的自定義 UserDetails
     */
    public static CustomUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            return (CustomUserDetails) authentication.getPrincipal();
        }
        throw new RuntimeException("當前用戶未登錄或登錄狀態異常");
    }

    /**
     * 秒拿當前用戶 ID (零查庫開銷)
     */
    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * 秒拿當前用戶名
     */
    public static String getCurrentUsername() {
        return getCurrentUser().getUsername();
    }

    /**
     * 判斷當前用戶是否為管理員
     */
    public static boolean isAdmin() {
        return getCurrentUser().getRole() == User.Role.ADMIN;
    }
}