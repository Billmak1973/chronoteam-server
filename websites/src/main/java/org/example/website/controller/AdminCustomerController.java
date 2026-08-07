package org.example.website.controller;

import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.example.website.util.UidGenerator;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder; // 1. 導入 PasswordEncoder
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminCustomerController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // 2. 聲明變量

    // 3. 修改構造函數，注入 PasswordEncoder
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
     * 2. AJAX API: 獲取用戶列表
     */
    @GetMapping("/api/customers/list")
    @ResponseBody
    public ResponseEntity<?> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
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

        int totalPages = usersPage.getTotalPages() == 0 ? 1 : usersPage.getTotalPages();

        Map<String, Object> response = new HashMap<>();
        response.put("content", cleanUsers);
        response.put("currentPage", usersPage.getNumber());
        response.put("totalPages", totalPages);
        response.put("totalElements", usersPage.getTotalElements());
        response.put("smartPages", generateSmartPagination(usersPage.getNumber(), totalPages));

        return ResponseEntity.ok(response);
    }

    /**
     * 創建新手動賬號 (管理員專用)
     */
    @PostMapping("/api/customers/create") // 建議路徑加上 /api 前綴以匹配前端 fetch
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

            // 【修復點】使用注入的 passwordEncoder
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
    @PutMapping("/api/customers/{id}/role") // 建議路徑加上 /api 前綴
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

    // ==========================================
    // 智能分頁算法
    // ==========================================
    private List<PageItem> generateSmartPagination(int currentPage, int totalPages) {
        List<PageItem> pages = new ArrayList<>();
        if (totalPages <= 0) {
            pages.add(new PageItem(false, 1));
            return pages;
        }
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) pages.add(new PageItem(false, i));
            return pages;
        }
        pages.add(new PageItem(false, 1));
        int current1Based = currentPage + 1;
        if (current1Based <= 3) {
            for (int i = 2; i <= 4; i++) pages.add(new PageItem(false, i));
            pages.add(new PageItem(true, null));
        } else if (current1Based >= totalPages - 2) {
            pages.add(new PageItem(true, null));
            for (int i = totalPages - 3; i <= totalPages - 1; i++) pages.add(new PageItem(false, i));
        } else {
            pages.add(new PageItem(true, null));
            pages.add(new PageItem(false, current1Based - 1));
            pages.add(new PageItem(false, current1Based));
            pages.add(new PageItem(false, current1Based + 1));
            pages.add(new PageItem(true, null));
        }
        pages.add(new PageItem(false, totalPages));
        return pages;
    }

    public static class PageItem {
        private boolean isEllipsis;
        private Integer pageNumber;
        public PageItem(boolean isEllipsis, Integer pageNumber) {
            this.isEllipsis = isEllipsis;
            this.pageNumber = pageNumber;
        }
        public boolean isEllipsis() { return isEllipsis; }
        public Integer getPageNumber() { return pageNumber; }
    }
}