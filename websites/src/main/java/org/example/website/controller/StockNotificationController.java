package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.Announcement;
import org.example.website.entity.AnnouncementReceipt;
import org.example.website.entity.Product;
import org.example.website.entity.StockNotification;
import org.example.website.entity.User;
import org.example.website.repository.AnnouncementReceiptRepository;
import org.example.website.repository.AnnouncementRepository;
import org.example.website.repository.ProductRepository;
import org.example.website.repository.StockNotificationRepository;
import org.example.website.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/stock-notification")
@Tag(name = "到貨通知管理", description = "商品到貨通知訂閱、取消及管理員批量發送相關接口")
public class StockNotificationController {

    private final StockNotificationRepository stockNotificationRepository;
    private final ProductRepository productRepository;
    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final AnnouncementReceiptRepository announcementReceiptRepository;

    public StockNotificationController(StockNotificationRepository stockNotificationRepository,
                                       ProductRepository productRepository,
                                       UserRepository userRepository,
                                       AnnouncementRepository announcementRepository,
                                       AnnouncementReceiptRepository announcementReceiptRepository) {
        this.stockNotificationRepository = stockNotificationRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.announcementRepository = announcementRepository;
        this.announcementReceiptRepository = announcementReceiptRepository;
    }

    /**
     * 獲取當前用戶的訂閱狀態 & 該商品的總訂閱人數
     */
    @Operation(
            summary = "獲取商品訂閱狀態",
            description = "獲取當前用戶是否已訂閱該商品，以及該商品的總訂閱人數。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "404", description = "商品不存在")
    })
    @GetMapping("/status/{productId}")
    public ResponseEntity<?> getStatus(
            @Parameter(description = "商品 ID", example = "1", required = true)
            @PathVariable Integer productId,
            @Parameter(hidden = true) Authentication authentication) {

        Map<String, Object> data = new HashMap<>();

        // 1. 【核心優化】直接從 Product 實體獲取總訂閱人數，避免昂貴的 COUNT 查詢
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        int totalSubscribers = product.getStockNotificationCount() != null ? product.getStockNotificationCount() : 0;
        data.put("totalSubscribers", totalSubscribers);

        // 2. 獲取當前用戶是否已訂閱
        boolean isSubscribed = false;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String username = authentication.getName();
            isSubscribed = stockNotificationRepository.findByProduct_ProductIdAndUser_Username(productId, username).isPresent();
        }
        data.put("isSubscribed", isSubscribed);

        return ResponseEntity.ok(Result.okWithData("成功", data));
    }

    /**
     * 訂閱到貨通知
     */
    @Operation(
            summary = "訂閱到貨通知",
            description = "用戶訂閱缺貨商品的到貨通知。若商品現貨充足則不允許訂閱。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "訂閱成功或已訂閱", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "商品現貨充足，請直接購買"),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "404", description = "商品不存在")
    })
    @PostMapping("/subscribe/{productId}")
    @Transactional // 確保保存記錄與原子更新在同一事務中
    public ResponseEntity<?> subscribe(
            @Parameter(description = "商品 ID", example = "1", required = true)
            @PathVariable Integer productId,
            @Parameter(hidden = true) Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        // 防呆：如果庫存大於0，不允許訂閱
        if (product.getStock() > 0) {
            return ResponseEntity.badRequest().body(Result.error("商品現貨充足，請直接購買"));
        }

        // 防止重複訂閱
        Optional<StockNotification> existing = stockNotificationRepository.findByProduct_ProductIdAndUser_Username(productId, username);
        if (existing.isPresent()) {
            return ResponseEntity.ok(Result.ok("您已訂閱過該商品"));
        }

        StockNotification notification = new StockNotification();
        notification.setProduct(product);
        notification.setUser(user);
        notification.setNotified(false);
        stockNotificationRepository.save(notification);

        // 【核心新增】：原子增加 Product 的訂閱人數
        productRepository.incrementStockNotificationCount(productId);

        return ResponseEntity.ok(Result.ok("訂閱成功"));
    }

    /**
     * 取消訂閱
     */
    @Operation(
            summary = "取消訂閱到貨通知",
            description = "用戶取消對某商品的到貨通知訂閱，並自動減少該商品的總訂閱計數。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "取消成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @DeleteMapping("/unsubscribe/{productId}")
    @Transactional // 確保刪除記錄與原子更新在同一事務中
    public ResponseEntity<?> unsubscribe(
            @Parameter(description = "商品 ID", example = "1", required = true)
            @PathVariable Integer productId,
            @Parameter(hidden = true) Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        String username = authentication.getName();

        // 先檢查是否存在，存在才執行刪除並減少計數
        Optional<StockNotification> existing = stockNotificationRepository.findByProduct_ProductIdAndUser_Username(productId, username);
        if (existing.isPresent()) {
            stockNotificationRepository.delete(existing.get());

            // 【核心新增】：原子減少 Product 的訂閱人數 (防止減到負數)
            productRepository.decrementStockNotificationCount(productId);
        }

        return ResponseEntity.ok(Result.ok("已取消訂閱"));
    }


    // ==========================================
    //  新增：管理員專屬接口
    // ==========================================

    /**
     * 1. 獲取等待名單 (僅管理員)
     */
    @Operation(
            summary = "獲取商品等待名單 (管理員)",
            description = "管理員獲取某個缺貨商品的等待通知用戶名單，以便評估補貨後的發送範圍。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "403", description = "無權訪問，僅限管理員 (Role: ADMIN)"),
            @ApiResponse(responseCode = "404", description = "商品不存在")
    })
    @GetMapping("/waitlist/{productId}")
    public ResponseEntity<?> getWaitlist(
            @Parameter(description = "商品 ID", example = "1", required = true)
            @PathVariable Integer productId,
            @Parameter(hidden = true) Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        // 2. 【核心修復】：使用 SecurityUtils 進行嚴格的 Role 權限校驗
        // 這會從 SecurityContext 中獲取 CustomUserDetails 並檢查 Role 枚舉
        if (!org.example.website.util.SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(Result.error("無權訪問，僅限管理員 (Role: ADMIN)"));
        }

        List<StockNotification> list = stockNotificationRepository.findByProduct_ProductIdAndNotifiedFalseOrderByCreatedAtAsc(productId);

        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (StockNotification notif : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("username", notif.getUser().getUsername());
            map.put("createdAt", notif.getCreatedAt().format(formatter));
            result.add(map);
        }

        return ResponseEntity.ok(Result.okWithData("成功", result));
    }


    /**
     * 2. 一鍵發送系統通知 (僅管理員) - 【核心重構版】
     */
    @Operation(
            summary = "一鍵發送到貨公告 (管理員)",
            description = "管理員為特定商品創建到貨公告，並批量發送給所有未通知的訂閱用戶，同時將訂閱狀態標記為已通知。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "發送成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "當前庫存為 0，無法發送通知！請先修改庫存。"),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "403", description = "無權訪問，僅限管理員 (Role: ADMIN)"),
            @ApiResponse(responseCode = "404", description = "商品不存在")
    })
    @PostMapping("/notify/{productId}")
    @Transactional
    public ResponseEntity<?> notifyWaitlist(
            @Parameter(description = "商品 ID", example = "1", required = true)
            @PathVariable Integer productId,
            @Parameter(hidden = true) Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        // 2. 【核心修復】：使用 SecurityUtils 進行嚴格的 Role 權限校驗
        // 這會從 SecurityContext 中獲取 CustomUserDetails 並檢查 Role 枚舉
        if (!org.example.website.util.SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(Result.error("無權訪問，僅限管理員 (Role: ADMIN)"));
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        // 防呆校驗：如果庫存依然為0，不允許發送
        if (product.getStock() <= 0) {
            return ResponseEntity.badRequest().body(Result.error("當前庫存為 0，無法發送通知！請先修改庫存。"));
        }

        List<StockNotification> waitlist = stockNotificationRepository.findByProduct_ProductIdAndNotifiedFalse(productId);

        if (waitlist.isEmpty()) {
            return ResponseEntity.ok(Result.ok("暫無需要通知的用戶"));
        }

        // ==========================================
        // 【核心變更】：使用 Announcement + AnnouncementReceipt 架構
        // ==========================================

        // 1. 創建 1 條公告主記錄 (Announcement)
        Announcement announcement = new Announcement();
        announcement.setTitle("🔔 您關注的商品已到貨！");
        announcement.setContent(String.format("您關注的 %s 已補貨到庫（當前庫存: %d 隻）。庫存緊張，請立即前往搶購！",
                product.getDescription(), product.getStock()));
        announcement.setType(Announcement.AnnouncementType.STOCK); // 使用 STOCK 類型
        announcement.setTargetType("SPECIFIC_PRODUCT");            // 標記為特定商品訂閱者
        announcement.setTargetId(productId.longValue());           // 關聯商品 ID
        announcement.setIsActive(true);

        Announcement savedAnnouncement = announcementRepository.save(announcement);

        // 2. 批量創建接收記錄 (AnnouncementReceipt) 並標記 StockNotification 為已通知
        List<AnnouncementReceipt> receipts = new ArrayList<>();
        for (StockNotification notif : waitlist) {
            // 創建接收記錄，實現一對多
            AnnouncementReceipt receipt = new AnnouncementReceipt();
            receipt.setAnnouncement(savedAnnouncement);
            receipt.setUser(notif.getUser());
            receipt.setIsRead(false); // 初始為未讀
            receipts.add(receipt);

            // 標記該訂閱記錄為已通知 (不減少 stockNotificationCount，因為用戶依然想要該商品)
            notif.setNotified(true);
        }

        // 3. 批量保存，提升資料庫效能
        announcementReceiptRepository.saveAll(receipts);
        stockNotificationRepository.saveAll(waitlist);

        product.setStockNotificationCount(0);
        productRepository.save(product);

        return ResponseEntity.ok(Result.ok(String.format("已成功向 %d 位用戶發送到貨公告！", waitlist.size())));
    }

    /**
     * 用戶點擊查看商品詳情時，自動清除該商品的到貨通知訂閱記錄
     */
    @Operation(
            summary = "查看商品時自動清除訂閱",
            description = "用戶點擊查看商品詳情時，後端自動清除該商品的到貨通知訂閱記錄並原子減少計數，不阻塞用戶跳轉。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "清除成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @DeleteMapping("/clear-on-view/{productId}")
    @Transactional
    public ResponseEntity<?> clearNotificationOnView(
            @Parameter(description = "商品 ID", example = "1", required = true)
            @PathVariable Integer productId,
            @Parameter(hidden = true) Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        String username = authentication.getName();

        // 1. 查找該用戶對該商品的訂閱記錄
        Optional<StockNotification> existing = stockNotificationRepository.findByProduct_ProductIdAndUser_Username(productId, username);

        if (existing.isPresent()) {
            // 2. 刪除記錄
            stockNotificationRepository.delete(existing.get());

            // 3. 原子減少商品的訂閱人數 (Repository 中已做防護，不會減到負數)
            productRepository.decrementStockNotificationCount(productId);
        }

        // 無論是否存在，都返回成功，不阻塞用戶跳轉
        return ResponseEntity.ok(Result.ok("已清除通知記錄"));
    }
}