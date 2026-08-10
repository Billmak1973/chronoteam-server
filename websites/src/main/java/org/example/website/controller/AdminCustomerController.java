package org.example.website.controller;

import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.example.website.util.PaginationUtils; // 引入分頁工具類
import org.example.website.util.UidGenerator;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminCustomerController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminCustomerController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 1. 頁面骨架渲染
     */
    @GetMapping("/customers")
    public String manageCustomersPage(Model model) {
        return "admin/admin-customers";
    }

    /**
     * 2. AJAX API: 獲取用戶列表 (修正版：支持 1-based 頁碼)
     */
    @GetMapping("/api/customers/list")
    @ResponseBody
    public ResponseEntity<?> getCustomers(
            @RequestParam(defaultValue = "1") int page, // 前端傳入 1-based (1, 2, 3...)
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {

        // 1. 轉換為 0-based 索引供 Spring Data JPA 使用
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> usersPage;

        try {
            // 動態篩選邏輯
            if ((keyword != null && !keyword.isEmpty()) && (role != null && !role.isEmpty())) {
                usersPage = userRepository.findByKeywordAndRole(keyword, User.Role.valueOf(role), pageable);
            } else if (keyword != null && !keyword.isEmpty()) {
                usersPage = userRepository.findByKeyword(keyword, pageable);
            } else if (role != null && !role.isEmpty()) {
                usersPage = userRepository.findByRole(User.Role.valueOf(role), pageable);
            } else {
                usersPage = userRepository.findAll(pageable);
            }
        } catch (Exception e) {
            // 防止 Repository 方法未定義導致崩潰，降級為查詢全部
            usersPage = userRepository.findAll(pageable);
        }

        // 數據清洗
        List<Map<String, Object>> cleanUsers = usersPage.getContent().stream().map(user -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("uid", user.getUid());
            item.put("name", user.getName());
            item.put("username", user.getUsername());
            item.put("email", user.getEmail());
            item.put("phone", user.getPhone());
            item.put("address", user.getAddress());
            item.put("backupAddress", user.getBackupAddress());
            item.put("role", user.getRole().name());
            item.put("createdAt", user.getCreatedAt());
            item.put("updatedAt", user.getUpdatedAt());
            return item;
        }).collect(Collectors.toList());

        // 2. 使用 PaginationUtils 構建基礎響應 (包含 smartPages, totalPages 等)
        Map<String, Object> response = PaginationUtils.buildPageResponse(usersPage, cleanUsers);

        // 3. 【關鍵修正】：覆蓋 currentPage，將 0-based 轉回 1-based 返回給前端
        // PaginationUtils 內部存的是 usersPage.getNumber() (即 0)，前端需要 1
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    /**
     * 創建新手動賬號 (管理員專用)
     */
    @PostMapping("/api/customers/create")
    @ResponseBody
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String roleStr = payload.get("role");

        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用戶名不能為空"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用戶名已存在"));
        }

        try {
            User user = new User();
            user.setUsername(username);
            user.setName(username); // 默認姓名
            user.setEmail(username + "@chronoteam.internal"); // 默認郵箱
            user.setPhone("0000000000"); // 默認電話

            // 使用注入的 passwordEncoder
            user.setPassword(passwordEncoder.encode("123456"));

            // 設置角色
            try {
                user.setRole(User.Role.valueOf(roleStr));
            } catch (IllegalArgumentException e) {
                user.setRole(User.Role.CUSTOMER);
            }

            // 自動生成 UID (帶重試機制確保唯一)
            String uid;
            int retries = 0;
            do {
                uid = UidGenerator.nextUid(userRepository.count());
                retries++;
            } while (userRepository.existsByUid(uid) && retries < 5);

            if (userRepository.existsByUid(uid)) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "UID生成衝突，請重試"));
            }
            user.setUid(uid);

            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "創建成功，默認密碼為 123456");
            response.put("uid", uid);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "服務器錯誤: " + e.getMessage()));
        }
    }

    /**
     * 修改用戶角色
     */
    @PutMapping("/api/customers/{id}/role")
    @ResponseBody
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String roleStr = payload.get("role");

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用戶不存在"));
        }

        try {
            User.Role newRole = User.Role.valueOf(roleStr);
            user.setRole(newRole);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("success", true, "message", "權限更新成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "無效的角色類型"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "更新失敗"));
        }
    }

}