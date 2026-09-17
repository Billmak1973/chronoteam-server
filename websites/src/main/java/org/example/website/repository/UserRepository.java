package org.example.website.repository;

import org.example.website.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用戶數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，提供針對 {@link User} 實體的基礎查詢、權限篩選及關鍵字搜索功能。
 * </p>
 *
 * @author ChronoTeam
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根據電子郵件地址查找用戶。
     *
     * @param email 電子郵件地址
     * @return 包含用戶實體的 {@link Optional}
     */
    Optional<User> findByEmail(String email);

    /**
     * 根據用戶名查找用戶。
     * <p>
     * 透過非聚簇索引找到主鍵值後，需要拿著主鍵值再去聚簇索引中查詢完整的行數據。
     * </p>
     *
     * @param username 用戶名
     * @return 包含用戶實體的 {@link Optional}
     */
    Optional<User> findByUsername(String username);

    /**
     * 檢查用戶名是否已經存在於數據庫中。
     * <p>
     * 通常用於用戶註冊或修改用戶名時的防重複校驗。
     * </p>
     *
     * @param username 用戶名
     * @return 若存在則返回 true，否則返回 false
     */
    boolean existsByUsername(String username);

    /**
     * 根據手機號碼查找用戶。
     *
     * @param phone 手機號碼
     * @return 包含用戶實體的 {@link Optional}
     */
    Optional<User> findByPhone(String phone);

    /**
     * 根據角色查詢所有匹配的用戶列表。
     *
     * @param role 用戶角色枚舉 (例如: {@link User.Role#ADMIN})
     * @return 符合該角色的用戶列表
     */
    List<User> findByRole(User.Role role);

    /**
     * 檢查指定的 UID 是否已經存在於數據庫中。
     * <p>
     * 用於確保系統生成的唯一標識符 (UID) 不會發生衝突。
     * </p>
     *
     * @param uid 用戶唯一標識符
     * @return 若存在則返回 true，否則返回 false
     */
    boolean existsByUid(String uid);

    /**
     * 根據關鍵字（用戶名 / 姓名 / 郵箱 / 手機）進行模糊搜索，並支持分頁。
     * <p>
     * 通常用於後台管理系統的「用戶管理」頁面，提供全局搜索功能。
     * </p>
     *
     * @param keyword  搜索關鍵字
     * @param pageable Spring Data 的分頁與排序參數對象
     * @return 符合關鍵字條件的用戶分頁結果
     */
    @Query("SELECT u FROM User u WHERE u.username LIKE %:kw% OR u.name LIKE %:kw% OR u.email LIKE %:kw% OR u.phone LIKE %:kw%")
    Page<User> findByKeyword(@Param("kw") String keyword, Pageable pageable);

    /**
     * 根據角色分頁查詢用戶。
     *
     * @param role     用戶角色枚舉
     * @param pageable Spring Data 的分頁與排序參數對象
     * @return 符合該角色的用戶分頁結果
     */
    Page<User> findByRole(User.Role role, Pageable pageable);

    /**
     * 根據關鍵字和角色進行組合篩選，並支持分頁。
     * <p>
     * 通常用於後台管理系統中，管理員先選擇了特定角色（如「銷售人員」），然後再輸入關鍵字進行精確搜索。
     * </p>
     *
     * @param keyword  搜索關鍵字
     * @param role     用戶角色枚舉
     * @param pageable Spring Data 的分頁與 Eskort參數對象
     * @return 符合關鍵字與角色雙重條件的用戶分頁結果
     */
    @Query("SELECT u FROM User u WHERE (u.username LIKE %:kw% OR u.name LIKE %:kw% OR u.email LIKE %:kw% OR u.phone LIKE %:kw%) AND u.role = :role")
    Page<User> findByKeywordAndRole(@Param("kw") String keyword, @Param("role") User.Role role, Pageable pageable);

    /**
     * 根據用戶名列表批量查詢用戶實體。
     * <p>
     * 通常用於評論區或通知系統中，根據一組用戶名快速加載用戶信息，避免 N+1 查詢問題。
     * </p>
     *
     * @param usernames 用戶名列表
     * @return 匹配的用戶實體列表
     */
    List<User> findByUsernameIn(List<String> usernames);
}