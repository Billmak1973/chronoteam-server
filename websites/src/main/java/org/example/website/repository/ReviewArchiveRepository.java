package org.example.website.repository;
import org.example.website.entity.ReviewArchive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewArchiveRepository extends JpaRepository<ReviewArchive, Long> {
}