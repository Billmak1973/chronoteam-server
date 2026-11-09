package org.example.website.repository;

import org.example.website.entity.SellApplicationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellApplicationAuditLogRepository extends JpaRepository<SellApplicationAuditLog, Long> {
    /**
     * 根据出售申请 ID 查询所有的审计日志，并按创建时间降序排列
     *
     * Spring Data JPA 命名规则解析：
     * findAllBy (查询所有)
     * + Application (关联的实体属性名: application)
     * + _ApplicationId (关联实体的主键属性名: applicationId)
     * + OrderByCreatedAtDesc (按 createdAt 字段降序排列)
     *
     * @param applicationId 出售申请的 ID
     * @return 审计日志列表
     */
    List<SellApplicationAuditLog> findAllByApplication_ApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
