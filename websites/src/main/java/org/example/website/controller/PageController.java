package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.*;
import org.example.website.repository.AdminPenaltyRepository;
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
    private final FavoriteRepository favoriteRepository; // 新增聲明
    private final ViewHistoryService viewHistoryService; //  新增依賴
    private final OrderService orderService; //  新增
    private final NotificationRepository notificationRepository; //  新增聲明
    private final StockNotificationRepository stockNotificationRepository;
    private final UserBlockRepository userBlockRepository;
    private final AppealRepository appealRepository;
    private final SecurityQuestionRepository securityQuestionRepository;
    private final AdminPenaltyRepository adminPenaltyRepository;
    private final AdminPenaltyService adminPenaltyService;
    private final CartService cartService;
    private final ProductService productService;
    private final SiteSettingService siteSettingService;

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
                          AdminPenaltyService adminPenaltyService,
                          CartService cartService, ProductService productService,
                          UserRepository userRepository, SiteSettingService siteSettingService) {
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
        return "admin-dashboard"; // 對應 templates/admin-dashboard.html
    }


    /**
     *  新增：我的收藏頁面路由 (支援分頁)
     */
    @GetMapping("/account/favorites")
    public String myFavorites(Model model,
                              Authentication authentication,
                              @RequestParam(defaultValue = "1") int page) { // Default to page 1
        String username = authentication.getName();

        // 【修改處 1】：使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 查詢當前用戶的收藏列表（按時間倒序）
        List<Favorite> allFavorites = favoriteRepository.findByUser_UsernameOrderByCreatedAtDesc(username);

        // ================= 核心修正：內存分頁邏輯 =================
        int size = 15; // 【需求】每頁顯示 15 條 (3列 * 5行)
        int totalElements = allFavorites.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // 防止頁碼越界
        if (page < 1) page = 1;
        if (totalPages == 0) totalPages = 1; // 如果沒有數據，至少保持1頁防止報錯
        if (page > totalPages) page = totalPages;

        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        // 截取當前頁的數據
        List<Favorite> pagedFavorites = new ArrayList<>();
        if (fromIndex < totalElements) {
            pagedFavorites = allFavorites.subList(fromIndex, toIndex);
        }

        // 6. 生成智能分頁列表 (1 ... 3, 4, 5 ... 10)
        List<PageItem> smartPages = generateSmartPagination(page, totalPages);
        // ================================================================

        // 【修改處 2】：將屬性名從 customer 改為 user，以匹配側邊欄 fragment 的需求
        model.addAttribute("user", user);

        // 傳遞分頁後的數據
        model.addAttribute("favorites", pagedFavorites);

        // 傳遞分頁相關變量
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("smartPages", smartPages);

        return "favorites";
    }

    @GetMapping("/account/history")
    public String myHistory(Model model,
                            Authentication authentication,
                            @RequestParam(defaultValue = "1") int page) { // Default to page 1
        String username = authentication.getName();

        // 【修改處 1】：使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 获取所有历史记录 (假设 Service 返回的是 List)
        List<ViewHistory> allHistoryList = viewHistoryService.getUserHistory(username);

        // ================= 核心修正：手动分页逻辑 =================
        int size = 15; // 【需求】每页显示 15 条 (3列 * 5行)
        int totalElements = allHistoryList.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // 防止页码越界
        if (page < 1) page = 1;
        if (totalPages == 0) totalPages = 1; // 如果没有数据，至少保持1页防止报错
        if (page > totalPages) page = totalPages;

        // 截取当前页的数据
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        // 确保索引有效
        List<ViewHistory> pagedHistoryList = new ArrayList<>();
        if (fromIndex < totalElements) {
            pagedHistoryList = allHistoryList.subList(fromIndex, toIndex);
        }

        // 6. 生成智能分页列表 (1 ... 3, 4, 5 ... 10)
        List<PageItem> smartPages = generateSmartPagination(page, totalPages);
        // ================================================================

        // 【修改處 2】：將屬性名從 customer 改為 user，以匹配側邊欄 fragment 的需求
        model.addAttribute("user", user);

        // 传递分页后的数据
        model.addAttribute("historyList", pagedHistoryList);

        // 传递分页相关变量
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("smartPages", smartPages);

        return "history";
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

@GetMapping("/account/notifications")
public String myNotifications(
        @RequestParam(defaultValue = "0") int stockPage,   // 到貨通知當前頁
        @RequestParam(defaultValue = "0") int adminPage,   // 管理通知當前頁
        @RequestParam(defaultValue = "25") int size,       // 每頁 25 條
        Model model, Authentication authentication) {

    String username = authentication.getName();
    User user = userService.findByUsername(username);

    // 1. 構建分頁對象
    Pageable stockPageable = org.springframework.data.domain.PageRequest.of(stockPage, size);
    Pageable adminPageable = org.springframework.data.domain.PageRequest.of(adminPage, size);

    // 2. 分別進行分頁查詢
    Page<Notification> stockPageResult = notificationRepository.findByTypeAndRecipient_UsernameOrderByCreatedAtDesc(
            Notification.NotificationType.STOCK, username, stockPageable);
    Page<Notification> adminPageResult = notificationRepository.findByTypeAndRecipient_UsernameOrderByCreatedAtDesc(
            Notification.NotificationType.SYSTEM, username, adminPageable);

    List<Notification> stockNotifications = stockPageResult.getContent();
    List<Notification> adminNotifications = adminPageResult.getContent();

    // 3. 核心重構：計算每條管理通知的「綜合狀態」(支援禁言 + 永久拉黑)
    Map<Long, String> notificationStatusMap = new HashMap<>();
    Map<Long, Appeal> appealDataMap = new HashMap<>();
    Map<Long, Integer> appealCountMap = new HashMap<>(); // 【新增】記錄申訴次數

    for (Notification notif : adminNotifications) {
        // 只處理「禁言相關」或「永久拉黑相關」的通知
        if (notif.getTitle() != null &&
                (notif.getTitle().contains("禁言") || notif.getTitle().contains("永久拉黑"))) {

            Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByNotificationId(notif.getNotificationId());

            // 【修改】獲取該通知的所有申訴記錄 (按時間倒序)
            List<Appeal> appeals = appealRepository.findByNotificationIdOrderByCreatedAtDesc(notif.getNotificationId());

            // 【新增】記錄申訴總次數
            appealCountMap.put(notif.getNotificationId(), appeals.size());

            if (!appeals.isEmpty()) {
                Appeal latestAppeal = appeals.get(0); // 獲取最新的一條申訴記錄
                appealDataMap.put(notif.getNotificationId(), latestAppeal);

                if (latestAppeal.getStatus() == Appeal.AppealStatus.PENDING) {
                    notificationStatusMap.put(notif.getNotificationId(), "APPEAL_PENDING");
                } else if (latestAppeal.getStatus() == Appeal.AppealStatus.APPROVED) {
                    notificationStatusMap.put(notif.getNotificationId(), "APPEAL_APPROVED");
                } else if (latestAppeal.getStatus() == Appeal.AppealStatus.REJECTED) {
                    notificationStatusMap.put(notif.getNotificationId(), "APPEAL_REJECTED");
                } else if (latestAppeal.getStatus() == Appeal.AppealStatus.EXPIRED) {
                    notificationStatusMap.put(notif.getNotificationId(), "APPEAL_EXPIRED");
                }
            } else {
                // 無申訴記錄 -> 根據處罰狀態決定 UI 顯示
                if (penaltyOpt.isPresent()) {
                    AdminPenalty penalty = penaltyOpt.get();

                    // 觸發精確的懶檢查 (確保過期狀態被正確更新)
                    adminPenaltyService.checkAndUpdatePenaltyStatus(penalty.getPenaltyId());

                    // 重新獲取最新狀態
                    penalty = adminPenaltyRepository.findById(penalty.getPenaltyId()).get();

                    if (penalty.getStatus() == AdminPenalty.PenaltyStatus.ACTIVE) {
                        notificationStatusMap.put(notif.getNotificationId(), "SHOW_APPEAL_BTN");
                    } else if (penalty.getStatus() == AdminPenalty.PenaltyStatus.EXPIRED) {
                        notificationStatusMap.put(notif.getNotificationId(), "EXPIRED_NO_APPEAL");
                    } else if (penalty.getStatus() == AdminPenalty.PenaltyStatus.REVOKED) {
                        notificationStatusMap.put(notif.getNotificationId(), "REVOKED_NO_APPEAL");
                    }
                } else {
                    notificationStatusMap.put(notif.getNotificationId(), "NO_PENALTY_RECORD");
                }
            }
        }
    }

    // 4. 進入頁面後，自動將所有通知標記為已讀 (優化：只查詢並更新未讀的，減少資料庫壓力)
    List<Notification> unreadNotifications = notificationRepository.findByRecipient_UsernameAndIsReadFalse(username);
    if (!unreadNotifications.isEmpty()) {
        unreadNotifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unreadNotifications);
    }

    // 5. 傳遞數據到前端
    model.addAttribute("user", user);
    model.addAttribute("stockNotifications", stockNotifications);
    model.addAttribute("adminNotifications", adminNotifications);
    model.addAttribute("notificationStatusMap", notificationStatusMap);
    model.addAttribute("appealDataMap", appealDataMap);
    model.addAttribute("appealCountMap", appealCountMap); // 【新增】傳遞申訴次數 Map

    // 傳遞分頁相關數據
    model.addAttribute("stockPage", stockPage);
    model.addAttribute("stockTotalPages", stockPageResult.getTotalPages());
    model.addAttribute("stockSmartPages", generateSmartPagination(stockPage, stockPageResult.getTotalPages()));

    model.addAttribute("adminPage", adminPage);
    model.addAttribute("adminTotalPages", adminPageResult.getTotalPages());
    model.addAttribute("adminSmartPages", generateSmartPagination(adminPage, adminPageResult.getTotalPages()));

    return "notifications";
}


    @GetMapping("/account/stock-notifications")
    public String stockNotifications(Model model,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "12") int size,
                                     Authentication authentication) {
        String username = authentication.getName();

        // 使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 排序字段應為 createdAt (實體類中的字段名)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        //調用 Repository 中新增的分頁方法
        Page<StockNotification> notificationPage = stockNotificationRepository
                .findByUser_UsernameOrderByCreatedAtDesc(username, pageable);

        // 將屬性名從 customer 改為 user，以匹配側邊欄 fragment 的需求
        model.addAttribute("user", user);
        model.addAttribute("notifications", notificationPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", notificationPage.getTotalPages());
        model.addAttribute("totalElements", notificationPage.getTotalElements());

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

    // ==========================================
    // 內部類：用於智能分頁渲染 (如果之前沒加在 PageController，請加上)
    // ==========================================
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

    /**
     * 生成智能分頁列表的核心算法
     */
    private List<PageItem> generateSmartPagination(int currentPage, int totalPages) {
        List<PageItem> pages = new ArrayList<>();
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) {
                pages.add(new PageItem(false, i));
            }
        } else {
            pages.add(new PageItem(false, 1)); // 始終顯示第一頁
            if (currentPage > 3) {
                pages.add(new PageItem(true, null)); // 省略號
            }
            int start = Math.max(2, currentPage - 1);
            int end = Math.min(totalPages - 1, currentPage + 1);
            for (int i = start; i <= end; i++) {
                pages.add(new PageItem(false, i));
            }
            if (currentPage < totalPages - 2) {
                pages.add(new PageItem(true, null)); // 省略號
            }
            pages.add(new PageItem(false, totalPages)); // 始終顯示最後一頁
        }
        return pages;
    }

    /**
     * 購物車頁面 (支援分頁 + 日期分組)
     */
    @GetMapping("/cart/view")
    public String viewCartPage(
            @RequestParam(defaultValue = "1") int page, // 【新增】當前頁碼
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
        int size = 20; // 【需求】每頁只加載 20 條數據
        int totalElements = allCartItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // 防止頁碼越界
        if (page < 1) page = 1;
        if (page > totalPages && totalPages > 0) page = totalPages;

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

        // 6. 生成智能分頁列表 (1 ... 3, 4, 5 ... 10)
        List<PageItem> smartPages = generateSmartPagination(page, totalPages);
        // ================================================================

        // 傳遞分頁後的數據給前端
        model.addAttribute("cartItems", pagedCartItems);
        model.addAttribute("groupedCartItems", groupedPagedCartItems);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("cartCount", cartCount);

        // 傳遞分頁相關變量
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("smartPages", smartPages);

        return "cart-detail";
    }

    /**
     * 系統配置管理頁面
     */
    @GetMapping("/admin/config")
    public String adminConfigPage(Model model) {
        return "admin-config";
    }
}