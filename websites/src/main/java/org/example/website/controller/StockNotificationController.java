package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Notification;
import org.example.website.entity.Product;
import org.example.website.entity.StockNotification;
import org.example.website.entity.User;
import org.example.website.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/stock-notification")
public class StockNotificationController {

    private final StockNotificationRepository stockNotificationRepository;
    private final ProductRepository productRepository;
    private final NotificationRepository notificationRepository; //  新增依賴
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

        // 1. 獲取總訂閱人數 (未通知狀態)
        long totalSubscribers = stockNotificationRepository.countByProduct_ProductIdAndNotifiedFalse(productId);
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
    public ResponseEntity<?> subscribe(@PathVariable Integer productId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String username = authentication.getName();

        // 核心修改 1：使用 UserRepository 獲取 User 實體
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

        // 核心修改 2：設置 User 關聯，取代原本的 setUsername
        notification.setUser(user);

        notification.setNotified(false);
        stockNotificationRepository.save(notification);

        return ResponseEntity.ok(ApiResponse.ok("訂閱成功"));
    }

    /**
     * 取消訂閱
     */
    @DeleteMapping("/unsubscribe/{productId}")
    public ResponseEntity<?> unsubscribe(@PathVariable Integer productId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String username = authentication.getName();
        stockNotificationRepository.findByProduct_ProductIdAndUser_Username(productId, username)
                .ifPresent(stockNotificationRepository::delete);

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

        //  確保 Repository 方法名也使用了下劃線導航
        List<StockNotification> list = stockNotificationRepository.findByProduct_ProductIdAndNotifiedFalseOrderByCreatedAtAsc(productId);

        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (StockNotification notif : list) {
            Map<String, Object> map = new HashMap<>();
            //  核心修復：透過 User 關聯獲取 username
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
    public ResponseEntity<?> notifyWaitlist(@PathVariable Integer productId, Authentication authentication) {
        if (authentication == null || !"admin".equals(authentication.getName())) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
        }

        Product product = productRepository.findById(productId).orElseThrow(() -> new RuntimeException("商品不存在"));

        //  防呆校驗：如果庫存依然為0，不允許發送
        if (product.getStock() <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("當前庫存為 0，無法發送通知！請先修改庫存。"));
        }

        //  注意：Product 的主鍵已改為 productId，所以 Repository 方法名需為 findByProduct_ProductId...
        List<StockNotification> waitlist = stockNotificationRepository.findByProduct_ProductIdAndNotifiedFalse(productId);

        if (waitlist.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok("暫無需要通知的用戶"));
        }

        // 核心修改 1：查詢 "admin" 用戶實體作為發送者
        User adminUser = userRepository.findByUsername("admin").orElse(null);

        // 構建通知內容
        String title = "🔔 您關注的商品已到貨！";
        String content = String.format("您關注的 %s 已補貨到庫（當前庫存: %d 隻）。庫存緊張，請立即前往搶購！",
                product.getDescription(), product.getStock());
        String targetUrl = "/product/" + productId;

        // 批量插入系統通知並更新訂閱狀態
        for (StockNotification notif : waitlist) {
            // 1. 創建系統通知 (使用剛才新增的 STOCK 類型)
            Notification sysNotif = new Notification();

            // 設置 User 關聯，取代原本的 setUsername
            sysNotif.setRecipient(notif.getUser()); // 接收者是訂閱該商品的用戶實體
            sysNotif.setSender(adminUser);          // 發送者是 admin 用戶實體

            sysNotif.setType(Notification.NotificationType.STOCK);
            sysNotif.setTitle(title);
            sysNotif.setContent(content);
            sysNotif.setTargetUrl(targetUrl);
            sysNotif.setRead(false);
            notificationRepository.save(sysNotif);

            // 2. 標記該訂閱記錄為已通知
            notif.setNotified(true);
            stockNotificationRepository.save(notif);
        }

        return ResponseEntity.ok(ApiResponse.ok(String.format("已成功向 %d 位用戶發送到貨通知！", waitlist.size())));
    }

}