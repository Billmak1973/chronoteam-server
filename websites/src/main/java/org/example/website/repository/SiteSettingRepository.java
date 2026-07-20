package org.example.website.repository;

import org.example.website.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {
    Optional<SiteSetting> findByKey(String key);
    boolean existsByKey(String key);
}