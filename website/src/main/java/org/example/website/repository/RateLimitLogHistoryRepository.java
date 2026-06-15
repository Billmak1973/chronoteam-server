package org.example.website.repository;

import org.example.website.entity.RateLimitLogHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateLimitLogHistoryRepository extends JpaRepository<RateLimitLogHistory, Long> {
}