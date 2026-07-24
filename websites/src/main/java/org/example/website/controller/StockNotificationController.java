package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Notification;
import org.example.website.entity.Product;
import org.example.website.entity.StockNotification;
import org.example.website.entity.User;
import org.example.website.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/stock-notification")
public class StockNotificationController {

    private final StockNotificationRepository stockNotificationRepository;
    private final ProductRepository productRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public StockNotificationController(StockNotificationRepository stockNotificationRepository,
                                       ProductRepository productRepository,
                                       UserRepository userRepository,
                                       NotificationRepository notificationRepository) {
        this.stockNotificationRepository = stockNotificationRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * 獲取當前用戶的訂閱狀態 & 該商品的總訂閱人數
     */
    @GetMapping("/status/{productId}")
    public ResponseEntity<?> getStatus(@PathVariable Integer productId, Authentication authentication) {
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

        return ResponseEntity.ok(ApiResponse.okWithData("成功", data));
    }

    /**
     * 訂閱到貨通知
     */
    @PostMapping("/subscribe/{productId}")
    @Transactional // 確保保存記錄與原子更新在同一事務中
    public ResponseEntity<?> subscribe(@PathVariable Integer productId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        // 防呆：如果庫存大於0，不允許訂閱
        if (product.getStock() > 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("商品現貨充足，請直接購買"));
        }

        // 防止重複訂閱
        Optional<StockNotification> existing = stockNotificationRepository.findByProduct_ProductIdAndUser_Username(productId, username);
        if (existing.isPresent()) {
            return ResponseEntity.ok(ApiResponse.ok("您已訂閱過該商品"));
        }

        StockNotification notification = new StockNotification();
        notification.setProduct(product);
        notification.setUser(user);
        notification.setNotified(false);
        stockNotificationRepository.save(notification);

        // 【核心新增】：原子增加 Product 的訂閱人數
        productRepository.incrementStockNotificationCount(productId);

        return ResponseEntity.ok(ApiResponse.ok("訂閱成功"));
    }

    /**
     * 取消訂閱
     */
    @DeleteMapping("/unsubscribe/{productId}")
    @Transactional // 確保刪除記錄與原子更新在同一事務中
    public ResponseEntity<?> unsubscribe(@PathVariable Integer productId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String username = authentication.getName();

        // 先檢查是否存在，存在才執行刪除並減少計數
        Optional<StockNotification> existing = stockNotificationRepository.findByProduct_ProductIdAndUser_Username(productId, username);
        if (existing.isPresent()) {
            stockNotificationRepository.delete(existing.get());

            // 【核心新增】：原子減少 Product 的訂閱人數 (防止減到負數)
            productRepository.decrementStockNotificationCount(productId);
        }

        return ResponseEntity.ok(ApiResponse.ok("已取消訂閱"));
    }


    // ==========================================
    //  新增：管理員專屬接口
    // ==========================================

    /**
     * 1. 獲取等待名單 (僅管理員)
     */
    @GetMapping("/waitlist/{productId}")
    public ResponseEntity<?> getWaitlist(@PathVariable Integer productId, Authentication authentication) {
        if (authentication == null || !"admin".equals(authentication.getName())) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權訪問"));
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

        return ResponseEntity.ok(ApiResponse.okWithData("成功", result));
    }

    /**
     * 2. 一鍵發送系統通知 (僅管理員)
     */
    @PostMapping("/notify/{productId}")
    @Transactional
    public ResponseEntity<?> notifyWaitlist(@PathVariable Integer productId, Authentication authentication) {
        if (authentication == null || !"admin".equals(authentication.getName())) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
        }

        Product product = productRepository.findById(productId).orElseThrow(() -> new RuntimeException("商品不存在"));

        // 防呆校驗：如果庫存依然為0，不允許發送
        if (product.getStock() <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("當前庫存為 0，無法發送通知！請先修改庫存。"));
        }

        List<StockNotification> waitlist = stockNotificationRepository.findByProduct_ProductIdAndNotifiedFalse(productId);

        if (waitlist.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok("暫無需要通知的用戶"));
        }

        User adminUser = userRepository.findByUsername("admin").orElse(null);

        String title = "🔔 您關注的商品已到貨！";
        String content = String.format("您關注的 %s 已補貨到庫（當前庫存: %d 隻）。庫存緊張，請立即前往搶購！",
                product.getDescription(), product.getStock());
        String targetUrl = "/product/" + productId;

        // 批量插入系統通知並更新訂閱狀態
        for (StockNotification notif : waitlist) {
            Notification sysNotif = new Notification();
            sysNotif.setRecipient(notif.getUser());
            sysNotif.setSender(adminUser);
            sysNotif.setType(Notification.NotificationType.STOCK);
            sysNotif.setTitle(title);
            sysNotif.setContent(content);
            sysNotif.setTargetUrl(targetUrl);
            sysNotif.setRead(false);
            notificationRepository.save(sysNotif);

            // 標記該訂閱記錄為已通知 (注意：這裡不減少 stockNotificationCount，因為用戶依然想要該商品，只是已收到通知)
            notif.setNotified(true);
            stockNotificationRepository.save(notif);
        }

        return ResponseEntity.ok(ApiResponse.ok(String.format("已成功向 %d 位用戶發送到貨通知！", waitlist.size())));
    }
}