package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_log", indexes = {
        @Index(name = "idx_login_username", columnList = "cust_username")
})
@Data
public class LoginLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "cust_username", length = 50, nullable = false)
    private String username;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "VARCHAR(500)")
    private String userAgent;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @CreationTimestamp
    @Column(name = "login_time")
    private LocalDateTime loginTime;

    //  輔助方法：解析設備名稱
    @Transient
    public String getDeviceName() {
        if (userAgent == null) return "未知設備";
        String ua = userAgent.toLowerCase();
        if (ua.contains("iphone")) return "iPhone";
        if (ua.contains("ipad")) return "iPad";
        if (ua.contains("macintosh")) return "Mac";
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("android")) return "Android";
        if (ua.contains("linux")) return "Linux";
        return "未知設備";
    }

    //  輔助方法：解析瀏覽器名稱
    @Transient
    public String getBrowserName() {
        if (userAgent == null) return "未知瀏覽器";
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("chrome/") && !ua.contains("edg")) return "Chrome";
        if (ua.contains("safari/") && !ua.contains("chrome")) return "Safari";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("opera") || ua.contains("opr/")) return "Opera";
        return "未知瀏覽器";
    }

    //  輔助方法：獲取對應的 FontAwesome 圖標
    @Transient
    public String getDeviceIcon() {
        String device = getDeviceName().toLowerCase();
        if (device.contains("iphone") || device.contains("android")) return "fas fa-mobile-alt";
        if (device.contains("ipad")) return "fas fa-tablet-alt";
        return "fas fa-desktop";
    }

    //  輔助方法：IP 地址脫敏 (例如: 192.168.1.100 -> 192.168.*.*)
    @Transient
    public String getMaskedIp() {
        if (ipAddress == null || "Unknown".equals(ipAddress)) {
            return "未知";
        }

        //  處理 IPv6 localhost (::1 或 0:0:0:0:0:0:0:1)
        if ("0:0:0:0:0:0:0:1".equals(ipAddress) || "::1".equals(ipAddress)) {
            return "127.0.0.1 (本地)";
        }

        // 處理 IPv4 localhost
        if ("127.0.0.1".equals(ipAddress)) {
            return "127.0.0.1 (本地)";
        }

        //  正常 IPv4 地址脫敏 (保留前兩段)
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }

        //  IPv6 或其他格式地址處理
        return ipAddress.substring(0, Math.min(ipAddress.length(), 6)) + "***";
    }
}