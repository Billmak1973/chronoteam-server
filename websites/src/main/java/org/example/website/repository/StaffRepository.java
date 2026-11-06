package org.example.website.repository;

import org.example.website.entity.Staff;
import org.example.website.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    // 根據 userId 列表批量查詢 (避免 N+1 問題)
    List<Staff> findByUserIdIn(List<Long> userIds);

    // 根據單個 userId 查詢
    Optional<Staff> findByUserId(Long userId);

    Optional<Staff> findByUser(User user);

    List<Staff> findByUser_IdIn(List<Long> userIds);

}