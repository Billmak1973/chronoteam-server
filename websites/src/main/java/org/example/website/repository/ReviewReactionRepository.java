package org.example.website.repository;

import org.example.website.entity.ReviewReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewReactionRepository extends JpaRepository<ReviewReaction, Long> {
    Optional<ReviewReaction> findByReviewIdAndUser_Username(Long reviewId, String username);

    List<ReviewReaction> findByReviewIdInAndUser_Username(List<Long> reviewIds, String username);
}
