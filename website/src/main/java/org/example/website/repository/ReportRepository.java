package org.example.website.repository;

import org.example.website.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterUsernameAndReviewId(String reporterUsername, Long reviewId);

}