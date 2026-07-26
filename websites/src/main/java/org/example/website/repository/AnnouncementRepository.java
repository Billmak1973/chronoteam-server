package org.example.website.repository;

import org.example.website.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

}
