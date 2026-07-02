package org.example.website.repository;

import org.example.website.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    List<LoginLog> findTop10ByUser_UsernameOrderByLoginTimeDesc(String username);
    Optional<LoginLog> findTopByUser_UsernameAndIpAddressOrderByLoginTimeDesc(String username, String ipAddress);

}