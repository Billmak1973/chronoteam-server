package org.example.website.repository;

import org.example.website.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    // 查詢當前用戶最近的 10 條登錄記錄
    List<LoginLog> findTop10ByUsernameOrderByLoginTimeDesc(String username);

    Optional<LoginLog> findTopByUsernameAndIpAddressOrderByLoginTimeDesc(String username, String ipAddress);

}