package org.example.website.config;

import jakarta.servlet.http.HttpServletRequest;
import org.example.website.entity.LoginLog;
import org.example.website.repository.LoginLogRepository;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime; //  新增導入
import java.util.Optional;       // 🟢 新增導入

@Component
public class LoginSuccessListener implements ApplicationListener<AuthenticationSuccessEvent> {
    private final LoginLogRepository loginLogRepository;

    public LoginSuccessListener(LoginLogRepository loginLogRepository) {
        this.loginLogRepository = loginLogRepository;
    }

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String username = authentication.getName();
        String ipAddress = "Unknown";
        String userAgent = "Unknown";
        String sessionId = null;

        // 1. 嘗試從 RequestContextHolder 獲取當前請求的詳細信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            //  關鍵修改：優先檢查各種代理頭部以獲取真實 IP
            String[] ipHeaders = {
                    "X-Real-IP", "X-Forwarded-For", "Proxy-Client-IP",
                    "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
            };

            for (String header : ipHeaders) {
                String ip = request.getHeader(header);
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    ipAddress = ip.split(",")[0].trim();
                    System.out.println("🔍 從頭部 " + header + " 獲取到 IP: " + ipAddress);
                    break;
                }
            }

            if ("Unknown".equals(ipAddress) || ipAddress == null) {
                ipAddress = request.getRemoteAddr();
                System.out.println("🔍 使用 getRemoteAddr(): " + ipAddress);
            }

            if ("0:0:0:0:0:0:0:1".equals(ipAddress) || "::1".equals(ipAddress)) {
                ipAddress = "127.0.0.1";
                System.out.println(" IPv6 localhost 轉換為: " + ipAddress);
            }

            userAgent = request.getHeader("User-Agent");
            if (request.getSession(false) != null) {
                sessionId = request.getSession().getId();
            }

            System.out.println("🔐 登錄監聽 - 用戶: " + username);
            System.out.println("🔐 最終 IP: " + ipAddress);
        }

        // 2. 備用：從 Event Source 獲取
        if (event.getSource() instanceof WebAuthenticationDetails) {
            WebAuthenticationDetails details = (WebAuthenticationDetails) event.getSource();
            if ("Unknown".equals(ipAddress) || ipAddress == null) {
                ipAddress = details.getRemoteAddress();
            }
            if (sessionId == null) {
                sessionId = details.getSessionId();
            }
        }

        //  3. 核心修改：同 IP 更新時間，不同 IP 創建新紀錄
        Optional<LoginLog> existingLogOpt = loginLogRepository.findTopByUsernameAndIpAddressOrderByLoginTimeDesc(username, ipAddress);

        if (existingLogOpt.isPresent()) {
            // 存在同 IP 記錄 -> 更新時間、UserAgent 和 SessionId
            LoginLog log = existingLogOpt.get();
            log.setLoginTime(LocalDateTime.now()); // 更新為當前時間

            // 必須更新 SessionId，否則前端無法判斷哪個是「當前設備」
            if (sessionId != null) log.setSessionId(sessionId);
            if (userAgent != null) log.setUserAgent(userAgent);

            loginLogRepository.save(log);
            System.out.println("💾 登錄記錄已更新 (同IP) - IP: " + ipAddress);
        } else {
            // 不存在同 IP 記錄 -> 創建新紀錄
            LoginLog log = new LoginLog();
            log.setUsername(username);
            log.setIpAddress(ipAddress);
            log.setUserAgent(userAgent);
            log.setSessionId(sessionId);
            loginLogRepository.save(log);
            System.out.println("💾 新登錄記錄已保存 (新IP) - IP: " + ipAddress);
        }
    }
}