package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.PushBuilder;
import lombok.RequiredArgsConstructor;
import org.example.website.dto.AnnouncementRequest;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.AnnouncementService;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.website.util.SecurityUtils.isAdmin;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;
    private final OfflineStoreRepository offlineStoreRepository;
    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReceiptRepository announcementReceiptRepository;
    private final StaffRepository staffRepository;

    @GetMapping("/announcements")
    public String manageAnnouncementsPage(Model model) {
        return "admin/admin-announcements";
    }

    @Operation(summary = "獲取公告分頁列表")
    @GetMapping("/api/announcements/list")
    @ResponseBody
    public ResponseEntity<?> getAnnouncementsList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size, PushBuilder pushBuilder) {

        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Announcement> pageResult = announcementRepository.findAll(pageable);

        // 1. 批量獲取關聯數據，避免 N+1 查詢性能問題 (嚴格按 targetType 分流)
        List<Long> storeIds = pageResult.getContent().stream()
                .filter(a -> a.getTargetType() == Announcement.TargetAudience.SPECIFIC_STORE)
                .map(Announcement::getTargetStoreId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<Long> userIds = pageResult.getContent().stream()
                .filter(a -> a.getTargetType() == Announcement.TargetAudience.SPECIFIC_USER) // ← 核心修復：只收集 SPECIFIC_USER 的 ID
                .map(Announcement::getTargetId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 收集所有非空的 publisher_id
        List<Long> publisherIds = pageResult.getContent().stream()
                .map(Announcement::getPublisherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> storeNameMap = new HashMap<>();
        if (!storeIds.isEmpty()) {
            offlineStoreRepository.findAllById(storeIds).forEach(store -> {
                storeNameMap.put(store.getStoreId(), store.getName());
            });
        }

        Map<Long, String> usernameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> {
                usernameMap.put(user.getId(), user.getUsername());
            });
        }

        Map<Long, String> publisherNameMap = new HashMap<>();
        if (!publisherIds.isEmpty()) {
            userRepository.findAllById(publisherIds).forEach(u -> {
                publisherNameMap.put(u.getId(), u.getUsername());
            });
        }

        // 2. 數據清洗與格式化
        List<Map<String, Object>> cleanData = pageResult.getContent().stream().map(ann -> {
            Map<String, Object> map = new HashMap<>();
            map.put("announcementId", ann.getAnnouncementId());
            map.put("title", ann.getTitle());
            map.put("type", ann.getType() != null ? ann.getType().name() : "UNKNOWN");
            map.put("createdAt", ann.getCreatedAt());
            map.put("updatedAt", ann.getUpdatedAt());
            map.put("isActive", ann.getIsActive());

            // 3. 【核心修復】發送對象顯示邏輯：直接基於枚舉判斷，並加入中文翻譯與 SPECIFIC_PRODUCT 防護
            String targetDisplay = "-";
            Announcement.TargetAudience tt = ann.getTargetType();

            if (tt == Announcement.TargetAudience.ALL) {
                targetDisplay = "全體用戶";
            } else if (tt == Announcement.TargetAudience.ALL_STAFF) {
                targetDisplay = "全體工作人員";
            } else if (tt == Announcement.TargetAudience.SPECIFIC_ROLE) {
                targetDisplay = translateRole(ann.getTargetRole());
            } else if (tt == Announcement.TargetAudience.SPECIFIC_STORE) {
                String storeName = storeNameMap.getOrDefault(ann.getTargetStoreId(), "未知門店");
                String roles = translateRole(ann.getTargetRole());
                targetDisplay = storeName + " (" + (roles.isEmpty() ? "全體員工" : roles) + ")";
            } else if (tt == Announcement.TargetAudience.SPECIFIC_USER) {
                targetDisplay = usernameMap.getOrDefault(ann.getTargetId(), "未知用戶(ID:" + ann.getTargetId() + ")");
            } else if (tt == Announcement.TargetAudience.SPECIFIC_PRODUCT) {
                targetDisplay = "商品訂閱";
            }

            map.put("targetDisplay", targetDisplay);
            map.put("targetType",       ann.getTargetType() != null ? ann.getTargetType().name() : "ALL");
            map.put("targetRole",       ann.getTargetRole());      // 可能是 "SALES,APPRAISER" 或單角色
            map.put("targetStoreId",    ann.getTargetStoreId());
            map.put("targetId",         ann.getTargetId());        // SPECIFIC_USER 的用戶ID
            map.put("content", ann.getContent()); // 返回富文本內容
            map.put("titleAlignment", ann.getTitleAlignment() != null ? ann.getTitleAlignment() : "LEFT"); // 返回標題對齊方式

            String publisherName = ann.getPublisherId()!=null
                    ?publisherNameMap.getOrDefault(ann.getPublisherId(), "未知(ID:" + ann.getPublisherId() + ")")
                    : "-";
            map.put("publisherName", publisherName);
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = PaginationUtils.buildPageResponse(pageResult, cleanData);
        response.put("currentPage", page); // 確保返回 1-based 頁碼
        return ResponseEntity.ok(response);
    }

    // ==========================================
    // 輔助方法：將英文角色枚舉字符串翻譯為中文，並支持逗號分隔的多角色
    // ==========================================
    private static final Map<String, String> ROLE_NAMES = Map.of(
            "ALL_STORE_STAFF", "全體工作人員",
            "SALES", "銷售員",
            "APPRAISER", "鑒賞師",
            "STORE_MANAGER", "店鋪經理",
            "COURIER", "快遞人員",
            "ONLINE_STAFF", "線上員工",
            "ADMIN", "管理員",
            "CUSTOMER", "顧客"
    );

    private String translateRole(String roleStr) {
        if (roleStr == null || roleStr.isEmpty()) return "";
        return Arrays.stream(roleStr.split(","))
                .map(String::trim)
                .map(r -> ROLE_NAMES.getOrDefault(r, r))
                .collect(Collectors.joining("、"));
    }

    @PostMapping("/announcements/publish")
    @ResponseBody
    public String publishAnnouncement(@ModelAttribute AnnouncementRequest request) {
        try {
            // 【核心修改】：使用 SecurityUtils 直接獲取當前登錄用戶的 ID
            Long publisherId = org.example.website.util.SecurityUtils.getCurrentUserId();

            // 將 publisherId 傳入 Service
            announcementService.publishAnnouncement(request, publisherId);

            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "error: " + e.getMessage();
        }
    }

    // 提供一個極簡的 API 供前端 JS 調用，直接返回 Entity 列表 (無需新建 DTO)
    @GetMapping("/api/stores/active")
    @ResponseBody
    public List<OfflineStore> getActiveStores() {
        // 只返回 isActive = true 的門店，Jackson 會自動將其序列化為 JSON
        return offlineStoreRepository.findByIsActiveTrue();
    }

    /**
     * 權限校驗：嚴格基於 Role == ADMIN（而非用戶名等於 "admin"）
     */
    @Operation(summary = "搜索用戶 (用戶名/手機模糊匹配，忽略大小寫，最多返回 10 條)")
    @GetMapping("/api/announcements/search")
    @ResponseBody
    public ResponseEntity<?> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "username") String type,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "請先登入"));
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)
                || userDetails.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權操作，僅限管理員"));
        }

        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
        }

        Pageable limit = PageRequest.of(0, 10); // 最多 10 條，防止下拉過長
        List<User> users = "phone".equalsIgnoreCase(type)
                ? userRepository.searchByPhoneLike(kw, limit)
                : userRepository.searchByUsernameLike(kw, limit);

        // 只返回前端需要的字段，避免洩露密碼等敏感信息
        List<Map<String, Object>> data = users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("name", u.getName());
            m.put("phone", u.getPhone());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @Operation(summary = "切換公告發布/隱藏狀態", description = "管理員切換公告的 isActive 狀態 (1=發布, 0=隱藏)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "切換成功"),
            @ApiResponse(responseCode = "400", description = "公告不存在"),
            @ApiResponse(responseCode = "403", description = "無權操作")
    })
    @PutMapping("/api/announcements/{id}/toggle-status")
    @ResponseBody
    public ResponseEntity<?> toggleAnnouncementStatus(
            @Parameter(description = "公告 ID", required = true) @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload,
            Authentication authentication) {

        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "無權操作"));
        }

        Boolean newIsActive = payload.get("isActive");
        if (newIsActive == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "狀態參數不能為空"));
        }

        try {
            Announcement announcement = announcementRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("公告不存在"));

            announcement.setIsActive(newIsActive);
            announcementRepository.save(announcement);

            String msg = newIsActive ? "公告已成功發布" : "公告已成功隱藏";
            return ResponseEntity.ok(Map.of("success", true, "message", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/api/announcements/{id}")
    @ResponseBody
    public String updateAnnouncement(@PathVariable Long id, @ModelAttribute AnnouncementRequest request) {
        try {
            Announcement ann = announcementRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("公告不存在"));

            ann.setTitle(request.getTitle());
            ann.setTitleAlignment(request.getTitleAlignment());
            ann.setContent(org.jsoup.Jsoup.clean(request.getContent(), org.jsoup.safety.Safelist.relaxed()));
            ann.setType(request.getType());
            announcementRepository.save(ann);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "error: " + e.getMessage();
        }
    }

    @GetMapping("/api/announcements/{id}/recipients")
    @ResponseBody
    public ResponseEntity<?> getAnnouncementRecipients(@PathVariable Long id) {
        try {
            Announcement ann = announcementRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("公告不存在"));

            if (ann.getTargetType() == Announcement.TargetAudience.ALL) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "isAllUsers", true,
                        "message", "此公告發送給了全體用戶，不展示具體名單"
                ));
            }

            List<AnnouncementReceipt> receipts = announcementReceiptRepository
                    .findByAnnouncement_AnnouncementIdOrderByCreatedAtDesc(id);

            // 【新增】收集所有用戶ID，用於批量查詢 Staff 信息
            List<Long> userIds = receipts.stream()
                    .map(r -> r.getUser().getId())
                    .distinct()
                    .collect(Collectors.toList());

            // 【新增】批量查詢 Staff 及其關聯的 Store，避免 N+1 問題
            // 假設你有 StaffRepository，如果沒有請注入
            Map<Long, String> userStoreNameMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                // 查詢這些用戶對應的員工檔案及門店
                List<Staff> staffList = staffRepository.findByUserIdIn(userIds);
                for (Staff s : staffList) {
                    if (s.getStore() != null && s.getStore().getName() != null) {
                        userStoreNameMap.put(s.getUser().getId(), s.getStore().getName());
                    }
                }
            }

            List<Map<String, Object>> userList = receipts.stream().map(receipt -> {
                User u = receipt.getUser();
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("username", u.getUsername());
                m.put("name", u.getName());
                m.put("phone", u.getPhone());
                m.put("role", u.getRole());
                m.put("isRead", receipt.getIsRead());
                m.put("readAt", receipt.getReadAt());

                // 【新增】將店鋪名稱放入 Map
                m.put("storeName", userStoreNameMap.getOrDefault(u.getId(), null));

                return m;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "isAllUsers", false,
                    "total", userList.size(),
                    "data", userList
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}