package org.example.website.repository;

import org.example.website.entity.SellApplicationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellApplicationAuditLogRepository extends JpaRepository<SellApplicationAuditLog, Long> {
}
