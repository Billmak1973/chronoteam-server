package org.example.website.repository;

import org.example.website.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    List<Keyword> findAllByOrderByCreatedAtDesc();
    boolean existsByKeyword(String keyword);
    Optional<Keyword> findByKeyword(String keyword);
}