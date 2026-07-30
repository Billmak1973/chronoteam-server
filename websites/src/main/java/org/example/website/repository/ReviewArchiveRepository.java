package org.example.website.repository;
import org.example.website.entity.ReviewArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewArchiveRepository extends JpaRepository<ReviewArchive, Long> {

    Page<ReviewArchive> findByAuthor_UsernameOrderByDeletedAtDesc(String username, Pageable pageable);

    // 按执行删除者查询
    Page<ReviewArchive> findByDeletedByIdOrderByDeletedAtDesc(Long deletedById, Pageable pageable);

    // 按原作者和执行删除者同时查询
    Page<ReviewArchive> findByAuthor_UsernameAndDeletedByIdOrderByDeletedAtDesc(
            String username, Long deletedById, Pageable pageable);
}