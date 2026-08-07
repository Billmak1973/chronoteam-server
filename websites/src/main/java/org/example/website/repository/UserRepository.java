package org.example.website.repository;

import org.example.website.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByPhone(String phone);

    // 新增：根據角色查詢用戶 (可選)
    List<User> findByRole(User.Role role);
    boolean existsByUid(String uid);

    // 關鍵字搜索 (用户名/姓名/邮箱/手机)
    @Query("SELECT u FROM User u WHERE u.username LIKE %:kw% OR u.name LIKE %:kw% OR u.email LIKE %:kw% OR u.phone LIKE %:kw%")
    Page<User> findByKeyword(@Param("kw") String keyword, Pageable pageable);

    // 按角色篩選
    Page<User> findByRole(User.Role role, Pageable pageable);

    // 關鍵字 + 角色組合篩選
    @Query("SELECT u FROM User u WHERE (u.username LIKE %:kw% OR u.name LIKE %:kw% OR u.email LIKE %:kw% OR u.phone LIKE %:kw%) AND u.role = :role")
    Page<User> findByKeywordAndRole(@Param("kw") String keyword, @Param("role") User.Role role, Pageable pageable);
}