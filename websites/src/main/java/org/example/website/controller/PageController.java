package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.*;
import org.example.website.repository.AdminPenaltyRepository;
import org.example.website.util.PaginationUtils;
import org.example.website.util.SecurityUtils;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller  // 關鍵：返回視圖名稱，不是 JSON
public class PageController {

    //  1. 聲明依賴變量
    private final UserService userService;
    private final UserRepository userRepository;
    private final LoginLogRepository loginLogRepository;
    private final SellApplicationRepository sellApplicationRepository;
    private final FavoriteRepository favoriteRepository;
    private final ViewHistoryService viewHistoryService;
    private final OrderService orderService;
    private final NotificationRepository notificationRepository;
    private final StockNotificationRepository stockNotificationRepository;
    private final UserBlockRepository userBlockRepository;
    private final AppealRepository appealRepository;
    private final SecurityQuestionRepository securityQuestionRepository;
    private final AdminPenaltyRepository adminPenaltyRepository;
    private final AdminPenaltyService adminPenaltyService;
    private final CartService cartService;
    private final ProductService productService;
    private final SiteSettingService siteSettingService;
    private final AnnouncementReceiptRepository announcementReceiptRepository;
    private final OrderRepository orderRepository;
    private final SystemConfigService systemConfigService;

    public PageController(UserService userService,
                          LoginLogRepository loginLogRepository,
                          SellApplicationRepository sellApplicationRepository,
                          FavoriteRepository favoriteRepository,
                          ViewHistoryService viewHistoryService,
                          OrderService orderService,
                          NotificationRepository notificationRepository,
                          StockNotificationRepository stockNotificationRepository,
                          UserBlockRepository userBlockRepository,
                          AppealRepository appealRepository,
                          SecurityQuestionRepository securityQuestionRepository,
                          AdminPenaltyRepository adminPenaltyRepository,
                          AdminPenaltyService adminPenaltyService,SystemConfigService systemConfigService,
                          CartService cartService, ProductService productService, AnnouncementReceiptRepository announcementReceiptRepository,
                          UserRepository userRepository, SiteSettingService siteSettingService, OrderRepository orderRepository) {
        this.userService = userService;
        this.loginLogRepository = loginLogRepository;
        this.sellApplicationRepository = sellApplicationRepository;
        this.favoriteRepository = favoriteRepository;
        this.viewHistoryService = viewHistoryService;
        this.orderService = orderService;
        this.notificationRepository = notificationRepository;
        this.stockNotificationRepository = stockNotificationRepository;
        this.userBlockRepository = userBlockRepository;
        this.appealRepository = appealRepository;
        this.securityQuestionRepository = securityQuestionRepository;
        this.adminPenaltyRepository = adminPenaltyRepository;
        this.adminPenaltyService = adminPenaltyService;
        this.cartService = cartService;
        this.userRepository = userRepository;
        this.productService = productService;
        this.siteSettingService = siteSettingService;
        this.announcementReceiptRepository = announcementReceiptRepository;
        this.orderRepository = orderRepository;
        this.systemConfigService = systemConfigService; // 新增賦值
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Product> allProducts = productService.getAllProducts();

        // 過濾出 homeDisplayOrder > 0 的商品，按數字升序排列 (已移除 limit(8) 限制)
        List<Product> featuredProducts = allProducts.stream()
                .filter(p -> p.getHomeDisplayOrder() != null && p.getHomeDisplayOrder() > 0)
                .sorted(Comparator.comparing(Product::getHomeDisplayOrder))
                .collect(Collectors.toList());

        model.addAttribute("featuredProducts", featuredProducts);

        // 【新增】獲取並傳遞卡片邊框主題 (day 或 night) 到前端
        String cardTheme = siteSettingService.getCardBorderTheme();
        model.addAttribute("cardTheme", cardTheme);

        return "home";
    }

    @GetMapping("/test")
    public String testPage() {
        return "test";  // 對應 templates/test.html
    }


    @GetMapping("/account/dashboard")
    public String dashboard(Model model) {
        // 零開銷秒拿 ID 和 Username
        Long userId = SecurityUtils.getCurrentUserId();
        String username = SecurityUtils.getCurrentUsername();

        System.out.println("當前登錄用戶 ID: " + userId + ", 用戶名: " + username);

        // 為了渲染左側側邊欄 (sidebar.html) 的用戶名和郵箱，
        // 我們使用主鍵 ID 查詢 User 實體 (主鍵查詢極快，且徹底告別 findByUsername)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用戶不存在，請重新登入"));

        //傳遞給前端
        model.addAttribute("user", user);

        return "dashboard";
    }

    @GetMapping("/account/profile")
    public String accountProfile(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 將屬性名從 customer 改為 user，以匹配側邊欄 fragment 的需求
        model.addAttribute("user", user);
        return "profile";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/authentication")
    public String authentication() {
        return "authentication";
    }

    @GetMapping("/account/orders")
    public String myOrders(Model model, Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        List<Order> orders = orderService.getUserOrders(username);

        // 【修改点 1】：待付款（仅包含线上支付 PayPal/信用卡，且状态为 UNPAID）
        List<Order> unpaidOrders = orders.stream()
                .filter(order -> "PAYPAL_SIM".equals(order.getPaymentMethod()) &&
                        order.getPaymentStatus() == Order.PaymentStatus.UNPAID)
                .collect(Collectors.toList());

        // 【修改点 2】：新增待线下付款列表（支付方式为 OFFLINE_STORE 且状态为 PENDING_OFFLINE）
        List<Order> pendingOfflineOrders = orders.stream()
                .filter(order -> "OFFLINE_STORE".equals(order.getPaymentMethod()) &&
                        order.getPaymentStatus() == Order.PaymentStatus.PENDING_OFFLINE)
                .collect(Collectors.toList());

        // 已支付订单逻辑保持不变
        List<Order> paidOrders = orders.stream()
                .filter(order -> order.getPaymentStatus() == Order.PaymentStatus.PAID_SIMULATED ||
                        order.getPaymentStatus() == Order.PaymentStatus.PAID_REAL ||
                        order.getPaymentStatus() == Order.PaymentStatus.PAID_OFFLINE)
                .collect(Collectors.toList());

        model.addAttribute("user", user);
        model.addAttribute("unpaidOrders", unpaidOrders);
        model.addAttribute("pendingOfflineOrders", pendingOfflineOrders); // 【修改点 3】：传入新列表
        model.addAttribute("paidOrders", paidOrders);
        model.addAttribute("returnDays", systemConfigService.getReturnDays());
        model.addAttribute("exchangeDays", systemConfigService.getExchangeDays());

        return "orders";
    }

    @GetMapping("/sell-guide")
    public String sellGuide() {
        return "sell-guide";
    }

    @GetMapping("/buyer-protection")
    public String buyerProtection() {
        return "buyer-protection";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @GetMapping("/faq")
    public String faq() {
        return "faq";
    }

    @GetMapping("/account/password")
    public String changePassword(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userService.findByUsername(username);

        model.addAttribute("user", user);
        return "password";
    }

    //  5. 修改：安全設置頁面路由 (加入真實登錄記錄查詢)
    @GetMapping("/account/security")
    public String securitySettings(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 【修改處 1】：使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 獲取真實登錄記錄 (最近 10 條)
        List<LoginLog> loginLogs = loginLogRepository.findTop10ByUser_UsernameOrderByLoginTimeDesc(username);

        // 獲取當前 Session ID，用於前端標記「當前設備」
        String currentSessionId = null;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getRequest().getSession(false) != null) {
            currentSessionId = attributes.getRequest().getSession().getId();
        }

        List<SecurityQuestion> securityQuestions =
                securityQuestionRepository.findByUser_UsernameOrderByCreatedAtDesc(username);

        // 【修改處 2】：將屬性名從 customer 改為 user，以匹配側邊欄 fragment 的需求
        model.addAttribute("user", user);
        model.addAttribute("loginLogs", loginLogs);
        model.addAttribute("currentSessionId", currentSessionId);
        model.addAttribute("securityQuestions", securityQuestions);

        return "security";
    }

    /**
     * 管理中心首頁
     */
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        // 這裡可以添加數據統計邏輯
        return "admin/admin-dashboard";
    }


    /**
     *  新增：我的收藏頁面路由 (支援分頁) - 重構版
     *  利用 PaginationUtils 統一處理分頁邏輯
     */
    @GetMapping("/account/favorites")
    public String myFavorites(Model model,
                              Authentication authentication,
                              @RequestParam(defaultValue = "1") int page) { // 前端傳入 1-based (1, 2, 3...)
        String username = authentication.getName();

        // 1. 獲取 User 實體
        User user = userService.findByUsername(username);

        // 2. 查詢所有收藏 (按時間倒序)
        List<Favorite> allFavorites = favoriteRepository.findByUser_UsernameOrderByCreatedAtDesc(username);

        // 3. 分頁參數計算
        int size = 15; // 每頁 15 條
        int totalElements = allFavorites.size();

        // 防止除零錯誤
        int totalPages = (totalElements == 0) ? 1 : (int) Math.ceil((double) totalElements / size);

        // 邊界檢查 (確保 page 在合法範圍內)
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // 4. 截取當前頁數據 (內存分頁)
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Favorite> pagedContent = (fromIndex < totalElements)
                ? allFavorites.subList(fromIndex, toIndex)
                : new ArrayList<>();

        // 5. 【核心修改】利用 PaginationUtils 生成智能分頁
        // 注意：PaginationUtils 內部邏輯是基於 0-based 索引的 (Spring Data 標準)
        // 所以這裡傳入 (page - 1) 將 1-based 轉換為 0-based
        List<PaginationUtils.PageItem> smartPages = PaginationUtils.generateSmartPagination(page - 1, totalPages);

        // 6. 傳遞數據給 Thymeleaf
        model.addAttribute("user", user);
        model.addAttribute("favorites", pagedContent);

        // 傳遞分頁變量
        model.addAttribute("currentPage", page);       // 保持 1-based 給前端顯示
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("smartPages", smartPages);  // 使用工具類生成的列表

        return "favorites";
    }

    /**
     *  新增：我的結算記錄頁面路由 (財務中心)
     */
    @GetMapping("/account/settlement")
    public String mySettlement(Model model, Authentication authentication) {
        String username = authentication.getName();

        // 【修改處 1】：使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 1. 查詢該用戶所有「已完成」的出售申請（包含買斷和寄售）
        List<SellApplication> allApps = sellApplicationRepository.findByUser_UsernameOrderByCreatedAtDesc(username);
        List<SellApplication> completedApps = allApps.stream()
                .filter(app -> app.getStatus() == SellApplication.ApplicationStatus.COMPLETED)
                .collect(Collectors.toList());

        // 2. 計算財務統計數據
        BigDecimal totalRevenue = BigDecimal.ZERO;   // 總成交額
        BigDecimal totalServiceFee = BigDecimal.ZERO; // 總服務費
        BigDecimal totalProfit = BigDecimal.ZERO;     // 實際淨收益

        for (SellApplication app : completedApps) {
            // 優先使用最終報價，如果沒有則使用預估價
            BigDecimal price = app.getFinalPrice() != null ? app.getFinalPrice() : app.getEstimatedPrice();
            app.setDisplayPrice(price);

            if (price != null) {
                totalRevenue = totalRevenue.add(price);

                // 計算 5% 服務費 (四捨五入到整數)
                BigDecimal fee = price.multiply(new BigDecimal("0.05")).setScale(0, RoundingMode.HALF_UP);
                BigDecimal profit = price.subtract(fee);

                totalServiceFee = totalServiceFee.add(fee);
                totalProfit = totalProfit.add(profit);

                // 設置到 Transient 字段供前端顯示
                app.setServiceFee(fee);
                app.setProfit(profit);

                // 設置交易模式文本
                app.setStatusText(app.getTransactionMode() == SellApplication.TransactionMode.BUYOUT ? "平台買斷" : "寄售成交");
            }
        }

        // 3. 將數據傳遞給前端
        // 【修改處 2】：將屬性名從 customer 改為 user，以匹配側邊欄 fragment 的需求
        model.addAttribute("user", user);
        model.addAttribute("settlements", completedApps);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalServiceFee", totalServiceFee);
        model.addAttribute("totalProfit", totalProfit);

        return "settlement";
    }

    @GetMapping("/account/reviews")
    public String myReviewsPage(Model model, Authentication authentication) {
        // 這裡不需要傳太多數據，因為前端會通過 AJAX 加載
        String username = authentication.getName();

        // 【修改處 1】：使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 【修改處 2】：將屬性名從 customer 改為 user，以匹配側邊欄 fragment 的需求
        model.addAttribute("user", user);
        return "reviews"; // 對應 templates/reviews.html
    }


    /**
     * 渲染通知頁面骨架 (不再加載任何數據，交由前端 AJAX 處理)
     */
    @GetMapping("/account/notifications")
    public String myNotificationsPage(Model model, Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        model.addAttribute("user", user);
        return "notifications"; // 對應 templates/notifications.html
    }

    /**
     * 【API 1】獲取到貨通知列表 (獨立數據源: AnnouncementReceipt)
     */
    @GetMapping("/api/notifications/stock")
    @ResponseBody
    public ResponseEntity<?> getStockNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 查詢到貨通知
        Page<AnnouncementReceipt> receiptPage = announcementReceiptRepository
                .findByUser_IdAndAnnouncement_TypeOrderByCreatedAtDesc(
                        user.getId(), Announcement.AnnouncementType.STOCK, pageable);

        // 進入該接口時，自動標記為已讀
        announcementReceiptRepository.markAllAsReadByUserAndType(user.getId(), Announcement.AnnouncementType.STOCK);

        // 使用 PaginationUtils 構建標準分頁響應
        Map<String, Object> response = PaginationUtils.buildPageResponse(receiptPage, receiptPage.getContent());

        return ResponseEntity.ok(response);
    }

    /**
     * 【API 2】獲取管理通知列表 (獨立數據源: Notification + AdminPenalty + Appeal)
     */
    @GetMapping("/api/notifications/admin")
    @ResponseBody
    public ResponseEntity<?> getAdminNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 查詢系統管理通知
        Page<Notification> notifPage = notificationRepository.findByTypeAndRecipient_UsernameOrderByCreatedAtDesc(
                Notification.NotificationType.SYSTEM, username, pageable);

        // 進入該接口時，自動標記為已讀
        notificationRepository.markAllAsReadByRecipientUserId(user.getId());

        List<Notification> adminNotifications = notifPage.getContent();

        // ==========================================
        // 計算每條管理通知的「綜合狀態」(支援禁言 + 永久拉黑 + 申訴)
        // ==========================================
        Map<Long, String> notificationStatusMap = new HashMap<>();
        Map<Long, Appeal> appealDataMap = new HashMap<>();
        Map<Long, Integer> appealCountMap = new HashMap<>();

        for (Notification notif : adminNotifications) {
            if (notif.getTitle() != null &&
                    (notif.getTitle().contains("禁言") || notif.getTitle().contains("永久拉黑"))) {

                Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByNotificationId(notif.getNotificationId());
                List<Appeal> appeals = appealRepository.findByNotificationIdOrderByCreatedAtDesc(notif.getNotificationId());

                appealCountMap.put(notif.getNotificationId(), appeals.size());

                if (!appeals.isEmpty()) {
                    Appeal latestAppeal = appeals.get(0);
                    appealDataMap.put(notif.getNotificationId(), latestAppeal);

                    switch (latestAppeal.getStatus()) {
                        case PENDING -> notificationStatusMap.put(notif.getNotificationId(), "APPEAL_PENDING");
                        case APPROVED -> notificationStatusMap.put(notif.getNotificationId(), "APPEAL_APPROVED");
                        case REJECTED -> notificationStatusMap.put(notif.getNotificationId(), "APPEAL_REJECTED");
                        case EXPIRED -> notificationStatusMap.put(notif.getNotificationId(), "APPEAL_EXPIRED");
                    }
                } else {
                    if (penaltyOpt.isPresent()) {
                        AdminPenalty penalty = penaltyOpt.get();
                        adminPenaltyService.checkAndUpdatePenaltyStatus(penalty.getPenaltyId());
                        // 重新獲取最新狀態
                        penalty = adminPenaltyRepository.findById(penalty.getPenaltyId()).orElse(null);

                        if (penalty != null) {
                            switch (penalty.getStatus()) {
                                case ACTIVE -> notificationStatusMap.put(notif.getNotificationId(), "SHOW_APPEAL_BTN");
                                case EXPIRED -> notificationStatusMap.put(notif.getNotificationId(), "EXPIRED_NO_APPEAL");
                                case REVOKED -> notificationStatusMap.put(notif.getNotificationId(), "REVOKED_NO_APPEAL");
                            }
                        }
                    } else {
                        notificationStatusMap.put(notif.getNotificationId(), "NO_PENALTY_RECORD");
                    }
                }
            }
        }

        // ==========================================
        // 使用 PaginationUtils 構建響應並合併額外狀態數據
        // ==========================================
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("notificationStatusMap", notificationStatusMap);
        extraData.put("appealDataMap", appealDataMap);
        extraData.put("appealCountMap", appealCountMap);

        Map<String, Object> response = PaginationUtils.buildPageResponse(notifPage, adminNotifications, extraData);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/history")
    public String myHistory(Model model,
                            Authentication authentication,
                            @RequestParam(defaultValue = "1") int page) { // 【修復 1】默認值改為 1 (1-based)
        String username = authentication.getName();

        // 1. 獲取 User 實體
        User user = userService.findByUsername(username);

        // 2. 獲取所有歷史記錄
        List<ViewHistory> allHistoryList = viewHistoryService.getUserHistory(username);

        // 3. 分頁參數計算
        int size = 15;
        int totalElements = allHistoryList.size();

        // 【修復 2】將 1-based 頁碼轉換為 0-based 索引進行計算
        int pageIndex = page - 1;

        // 邊界檢查 (防止用戶手動輸入 page=0 或負數)
        if (pageIndex < 0) pageIndex = 0;

        int totalPages = (int) Math.ceil((double) totalElements / size);
        if (totalPages == 0) totalPages = 1; // 至少保持 1 頁
        if (pageIndex >= totalPages) pageIndex = totalPages - 1; // 防止越界

        // 4. 截取當前頁數據
        int fromIndex = pageIndex * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<ViewHistory> pagedHistoryList = new ArrayList<>();
        if (fromIndex < totalElements) {
            pagedHistoryList = allHistoryList.subList(fromIndex, toIndex);
        }

        // 5. 生成智能分頁 (注意：這裡傳入的是 0-based 的 pageIndex，因為工具類內部邏輯是 0-based)
        List<PaginationUtils.PageItem> smartPages = PaginationUtils.generateSmartPagination(pageIndex, totalPages);

        // 6. 傳遞數據到前端
        model.addAttribute("user", user);
        model.addAttribute("historyList", pagedHistoryList);

        // 【修復 3】關鍵！傳遞給前端的 currentPage 必須是 1-based 的 page
        // 這樣前端 URL 才是 ?page=1，且 th:class="${currentPage == 1}" 才能正確生效
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("smartPages", smartPages);

        return "history";
    }

    @GetMapping("/account/stock-notifications")
    public String stockNotifications(Model model,
                                     @RequestParam(defaultValue = "1") int page, // 【修復 1】默認值改為 1 (1-based)
                                     @RequestParam(defaultValue = "12") int size,
                                     Authentication authentication) {
        String username = authentication.getName();

        // 1. 獲取 User 實體
        User user = userService.findByUsername(username);

        // 【修復 2】將 1-based 頁碼轉換為 0-based 索引供 Spring Data JPA 使用
        int pageIndex = page - 1;
        if (pageIndex < 0) pageIndex = 0;

        // 2. 構建分頁請求 (Spring Data JPA 需要 0-based)
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 3. 查詢數據
        Page<StockNotification> notificationPage = stockNotificationRepository
                .findByUser_UsernameOrderByCreatedAtDesc(username, pageable);

        // 4. 使用 PaginationUtils 構建標準分頁響應
        // 注意：buildPageResponse 內部會處理 smartPages 的生成，它接收的是 Page 對象 (0-based)
        Map<String, Object> pageResponse = PaginationUtils.buildPageResponse(notificationPage, notificationPage.getContent());

        // 5. 傳遞數據到前端
        model.addAttribute("user", user);
        model.addAttribute("notifications", pageResponse.get("content"));

        // 【修復 3】關鍵！從 Page 對象獲取 0-based 頁碼，然後 +1 轉為 1-based 傳給前端
        // 或者直接使用我們剛才計算的 page 變量 (已經做了邊界檢查)
        // 為了確保與 smartPages 一致，我們重新計算一下安全的 1-based currentPage
        int safePageIndex = notificationPage.getNumber(); // 0-based
        int safeCurrentPage = safePageIndex + 1;        // 1-based

        model.addAttribute("currentPage", safeCurrentPage);
        model.addAttribute("totalPages", notificationPage.getTotalPages());
        model.addAttribute("totalElements", notificationPage.getTotalElements());
        model.addAttribute("smartPages", pageResponse.get("smartPages"));

        return "stock-notifications";
    }
    @DeleteMapping("/api/stock-notification/unsubscribe-by-id/{id}")
    public ResponseEntity<?> unsubscribeById(@PathVariable Long id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String username = authentication.getName();
        Optional<StockNotification> notification = stockNotificationRepository.findById(id);

        if (notification.isPresent() && notification.get().getUser().getUsername().equals(username)) {
            stockNotificationRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.ok("已取消訂閱"));
        }

        return ResponseEntity.badRequest().body(ApiResponse.error("找不到該訂閱記錄"));
    }

    @GetMapping("/account/blocked-users")
    public String blockedUsers(Model model, Authentication authentication) {
        String username = authentication.getName();

        // 【修改處 1】：使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 查詢當前用戶禁言的所有記錄
        List<UserBlock> blockedUsers = userBlockRepository.findByBlocker_Username(username);

        // 【修改處 2】：將屬性名從 customer 改為 user，以匹配側邊欄 fragment 的需求
        model.addAttribute("user", user);
        model.addAttribute("blockedUsers", blockedUsers);
        return "blocked-users";
    }


    /**
     * 購物車頁面 (支援分頁 + 日期分組)
     * 【重構】：利用 PaginationUtils 統一生成智能分頁列表
     */
    @GetMapping("/cart/view")
    public String viewCartPage(
            @RequestParam(defaultValue = "1") int page, // 當前頁碼 (1-based)
            Model model,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "redirect:/";
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username);
        model.addAttribute("user", user);

        // 1. 獲取所有購物車數據
        List<Cart> allCartItems = cartService.getCartItems(username);
        long cartCount = cartService.getCartCount(username);

        // 2. 計算總價 (基於所有選中商品，不受分頁影響，符合電商常規邏輯)
        double totalAmount = allCartItems.stream()
                .filter(Cart::getSelected)
                .mapToDouble(item -> item.getPrice().doubleValue() * item.getQuantity())
                .sum();

        // ================= 核心修正：分頁 + 按日期分組 =================
        int size = 20; // 每頁只加載 20 條數據
        int totalElements = allCartItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // 防止頁碼越界 (確保 page 在 1 到 totalPages 之間)
        if (page < 1) page = 1;
        if (totalPages > 0 && page > totalPages) page = totalPages;

        // 3. 先對所有商品按時間倒序排序
        allCartItems.sort(Comparator.comparing(Cart::getCreatedAt).reversed());

        // 4. 截取當前頁的數據
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Cart> pagedCartItems = (totalElements > 0) ? allCartItems.subList(fromIndex, toIndex) : new ArrayList<>();

        // 5. 【關鍵】僅對【當前頁的數據】進行日期分組
        // 這樣即使分頁切斷了同一天的商品，當前頁也能正確顯示它所包含的日期標題，不會錯亂
        Map<String, List<Cart>> groupedPagedCartItems = pagedCartItems.stream()
                .collect(Collectors.groupingBy(
                        item -> {
                            LocalDate date = item.getCreatedAt().toLocalDate();
                            LocalDate today = LocalDate.now();
                            if (date.getYear() == today.getYear() && date.getMonth() == today.getMonth()) {
                                return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                            } else if (date.getYear() == today.getYear()) {
                                return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                            } else {
                                return String.valueOf(date.getYear());
                            }
                        },
                        () -> new TreeMap<String, List<Cart>>(Comparator.reverseOrder()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                (List<Cart> list) -> {
                                    list.sort(Comparator.comparing(Cart::getCreatedAt).reversed());
                                    return list;
                                }
                        )
                ));

        // 6. 【核心重構】：利用 PaginationUtils 生成智能分頁列表
        // 注意：PaginationUtils.generateSmartPagination 接收的是 0-based 的 currentPage
        // 而這裡的 page 變量是 1-based，所以需要傳入 page - 1
        List<PaginationUtils.PageItem> smartPages = PaginationUtils.generateSmartPagination(page - 1, totalPages);

        // 傳遞分頁後的數據給前端
        model.addAttribute("cartItems", pagedCartItems);
        model.addAttribute("groupedCartItems", groupedPagedCartItems);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("cartCount", cartCount);

        // 傳遞分頁相關變量
        model.addAttribute("currentPage", page); // 保持 1-based 給前端 Thymeleaf 使用
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("smartPages", smartPages); // 傳遞智能分頁列表

        return "cart-detail";
    }

    /**
     * 系統配置管理頁面
     */
    @GetMapping("/admin/config")
    public String adminConfigPage(Model model) {
        return "admin/admin-config";
    }


    /**
     * 新增：取消與退貨訂單頁面路由
     */
    @GetMapping("/account/cancelled-orders")
    public String cancelledAndReturnedOrders(Model model, Authentication authentication) {
        String username = authentication.getName();

        // 1. 獲取 User 實體 (用於側邊欄渲染)
        User user = userService.findByUsername(username);
        model.addAttribute("user", user);

        // 2.  【核心修改】：直接在數據庫層面加載「可見」且「已取消」的訂單
        // 這樣就不會加載到那些已經被用戶隱藏 (is_visible = false) 的訂單
        List<Order> cancelledOrders = orderRepository.findByUser_UsernameAndStatusAndIsVisibleTrue(
                username, Order.OrderStatus.CANCELLED);

        // 3. 篩選出已退貨的訂單 (同樣只加載可見的)
        // 若未來 OrderStatus 枚舉中增加了 RETURNED 狀態，可直接替換下方的 CANCELLED
        List<Order> returnedOrders = orderRepository.findByUser_UsernameAndStatusAndIsVisibleTrue(
                username, Order.OrderStatus.CANCELLED); // 暫時用 CANCELLED 佔位，未來改為 RETURNED

        // 4. 傳遞數據到前端
        model.addAttribute("cancelledOrders", cancelledOrders);
        model.addAttribute("cancelledCount", cancelledOrders.size());

        model.addAttribute("returnedOrders", returnedOrders);
        model.addAttribute("returnedCount", returnedOrders.size());

        // 5. 返回視圖名稱
        return "cancelled-orders";
    }
}