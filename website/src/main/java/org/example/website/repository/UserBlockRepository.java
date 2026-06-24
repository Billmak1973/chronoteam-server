package org.example.website.repository;

import org.example.website.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    // 查询 A 是否禁言了 B
    Optional<UserBlock> findByBlockerUsernameAndBlockedUsername(String blocker, String blocked);

    // 查询用户禁言的所有人
    List<UserBlock> findByBlockerUsername(String blocker);

    // 查询禁言某用户的所有人
    List<UserBlock> findByBlockedUsername(String blocked);

    // 核心：检查 A 和 B 之间是否存在双向禁言（任意一方禁言都算）
    @Query("SELECT COUNT(ub) > 0 FROM UserBlock ub WHERE " +
            "(ub.blockerUsername = ?1 AND ub.blockedUsername = ?2) OR " +
            "(ub.blockerUsername = ?2 AND ub.blockedUsername = ?1)")
    boolean existsMutualBlock(String user1, String user2);

    // 删除禁言记录
    void deleteByBlockerUsernameAndBlockedUsername(String blocker, String blocked);

    // 查询当前用户禁言了哪些人
    @Query("SELECT ub.blockedUsername FROM UserBlock ub WHERE ub.blockerUsername = :username")
    List<String> findBlockedUsernamesByBlockerUsername(@Param("username") String username);

}