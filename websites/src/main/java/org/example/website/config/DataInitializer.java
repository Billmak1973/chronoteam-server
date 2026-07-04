package org.example.website.config;

import lombok.extern.slf4j.Slf4j;
import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.example.website.util.UidGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin-enabled:true}")
    private boolean adminEnabled;

    @Value("${app.init.admin-username:admin}")
    private String initUsername;

    @Value("${app.init.admin-password:ChangeMe@123}")
    private String initPassword;

    @Value("${app.init.admin-email:admin@chronoteam.com}")
    private String initEmail;

    @Value("${app.init.admin-phone:0000000000}")
    private String initPhone;

    @Value("${app.init.admin-name:系統超級管理員}")
    private String initName;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!adminEnabled) {
            log.info(" [系統初始化] 管理員初始化已禁用 (app.init.admin-enabled=false)");
            return;
        }

        // ==========================================
        // 核心修復：直接判斷該用戶名是否已存在
        // 如果已存在，直接 return，根本不執行 save()，絕對不會觸發 INSERT，不會消耗自增 ID！
        // ==========================================
        if (userRepository.existsByUsername(initUsername)) {
            log.info(" [系統初始化] 管理員帳號 [{}] 已存在，跳過創建。", initUsername);
            return;
        }

        try {
            User admin = new User();
            long currentDbCount = userRepository.count();
            String generatedUid = UidGenerator.nextUid(currentDbCount);

            admin.setUid(generatedUid);
            admin.setName(initName);
            admin.setUsername(initUsername);
            admin.setPassword(passwordEncoder.encode(initPassword));
            admin.setEmail(initEmail);
            admin.setPhone(initPhone);
            admin.setAddress(null);
            admin.setRole(User.Role.ADMIN);

            userRepository.save(admin);

            log.info("==================================================");
            log.info(" [系統初始化] 成功創建超級管理員帳號 (僅限首次無該用戶時)");
            log.info(" 用戶名: {}", initUsername);
            log.info(" 系統UID: {}", generatedUid);
            log.info(" 初始密碼: {}", initPassword);
            log.info("️ 請務必在首次登入後修改密碼！");
            log.info("==================================================");

        } catch (Exception e) {
            // 捕獲異常即可，不要拋出 RuntimeException，以免導致整個 Spring Boot 啟動失敗
            log.error(" [系統初始化] 創建管理員 [{}] 失敗: {}", initUsername, e.getMessage(), e);
        }
    }
}