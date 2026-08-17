package org.example.website.controller;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private final ReviewRepository reviewRepository;
    private final NotificationService notificationService;
    private final ReviewReactionRepository reviewReactionRepository;

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
                          AdminPenaltyService adminPenaltyService, SystemConfigService systemConfigService,
                          CartService cartService, ProductService productService, AnnouncementReceiptRepository announcementReceiptRepository,
                          UserRepository userRepository, SiteSettingService siteSettingService, OrderRepository orderRepository, ReviewRepository reviewRepository, NotificationService notificationService, ReviewReactionRepository reviewReactionRepository) {
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
        this.reviewRepository = reviewRepository;
        this.notificationService = notificationService;
        this.reviewReactionRepository = reviewReactionRepository;
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


    /**
     * 個人中心首頁 (本人訪問)
     */
    @GetMapping("/account/dashboard")
    public String dashboard(Model model) {
        // 1. 獲取當前登錄用戶信息
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用戶不存在，請重新登入"));

        // 2. 【核心修復】：顯式設置標記，告訴前端這是「本人」訪問
        model.addAttribute("user", user);
        model.addAttribute("isSelf", true);       // 強制為 true
        model.addAttribute("viewMode", "self");   // 設置模式為 self

        return "dashboard";
    }

    /**
     * 查看其他用戶主頁 (訪客或本人通過鏈接訪問)
     */
    @GetMapping("/user/{username}")
    public String viewUserProfile(@PathVariable String username, Model model, Authentication authentication) {
        // 1. 查詢目標用戶
        User targetUser = userService.findByUsername(username);
        if (targetUser == null) {
            return "redirect:/"; // 用戶不存在跳轉首頁
        }

        // 2. 判斷是否為本人
        boolean isSelf = false;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            // 忽略大小寫比較
            isSelf = authentication.getName().equalsIgnoreCase(username);
        }

        // 3. 傳遞數據
        model.addAttribute("user", targetUser);
        model.addAttribute("isSelf", isSelf);

        // 4. 設置視圖模式
        if (isSelf) {
            // 如果是本人通過 /user/xxx 訪問，視為 self 模式 (或者你可以重定向到 /account/dashboard)
            // 這裡為了統一模板，設置為 self
            model.addAttribute("viewMode", "self");
        } else {
            // 訪客模式
            model.addAttribute("viewMode", "guest");
        }

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
    public String about(Model model) {
        // 【新增】獲取並傳遞卡片邊框主題 (day 或 night) 到前端
        // 確保 about 頁面也能讀取全局主題設置，保持全站視覺一致性
        String cardTheme = siteSettingService.getCardBorderTheme();
        model.addAttribute("cardTheme", cardTheme);

        return "about";
    }

//    @GetMapping("/about")
//    public String about() {
//        return "about";
//    }

    @GetMapping("/authentication")
    public String authentication() {
        return "authentication";
    }


    /**
     * 頁面骨架渲染（僅傳遞配置參數，訂單數據由下方 API 異步加載）
     */
    @GetMapping("/account/orders")
    public String myOrders(Model model, Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);

        model.addAttribute("user", user);
        model.addAttribute("returnDays", systemConfigService.getReturnDays());
        model.addAttribute("exchangeDays", systemConfigService.getExchangeDays());

        return "orders";
    }

    /**
     * 【API】獲取待付款訂單（分頁 + 數據清洗）
     */
    @GetMapping("/api/account/orders/unpaid")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUnpaidOrders(
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication) {
        String username = authentication.getName();
        int zeroBasedPage = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(zeroBasedPage, 25);

        Page<Order> orderPage = orderRepository.findUnpaidOrders(username, pageable);

        // 核心修復：手動清洗數據，避免 LazyInitializationException 和循環引用
        List<Map<String, Object>> cleanOrders = orderPage.getContent().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("orderNo", order.getOrderNo());
            map.put("totalAmount", order.getTotalAmount());
            map.put("paymentMethod", order.getPaymentMethod());
            map.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
            map.put("status", order.getStatus() != null ? order.getStatus().name() : null);
            map.put("createdAt", order.getCreatedAt());
            map.put("paidAt", order.getPaidAt());
            map.put("receivedAt", order.getReceivedAt());

            // 安全提取關聯對象字段（避免 LAZY 代理序列化）
            if (order.getOfflineStore() != null) {
                Map<String, Object> storeMap = new HashMap<>();
                storeMap.put("name", order.getOfflineStore().getName());
                storeMap.put("address", order.getOfflineStore().getAddress());
                map.put("offlineStore", storeMap);
            } else {
                map.put("offlineStore", null);
            }

            return map;
        }).collect(Collectors.toList());

        //  使用清洗後的數據構建響應，而非原始 Entity
        Map<String, Object> response = PaginationUtils.buildPageResponse(orderPage, cleanOrders);
        return ResponseEntity.ok(response);
    }

    /**
     * 【API】獲取待線下付款訂單（分頁 + 數據清洗）
     */
    @GetMapping("/api/account/orders/pending-offline")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPendingOfflineOrders(
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication) {
        String username = authentication.getName();
        int zeroBasedPage = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(zeroBasedPage, 25);

        Page<Order> orderPage = orderRepository.findPendingOfflineOrders(username, pageable);

        //  核心修復：手動清洗數據
        List<Map<String, Object>> cleanOrders = orderPage.getContent().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("orderNo", order.getOrderNo());
            map.put("totalAmount", order.getTotalAmount());
            map.put("paymentMethod", order.getPaymentMethod());
            map.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
            map.put("status", order.getStatus() != null ? order.getStatus().name() : null);
            map.put("createdAt", order.getCreatedAt());
            map.put("paidAt", order.getPaidAt());
            map.put("receivedAt", order.getReceivedAt());

            // 待線下付款訂單必然關聯店鋪，但仍做 null 防護
            if (order.getOfflineStore() != null) {
                Map<String, Object> storeMap = new HashMap<>();
                storeMap.put("name", order.getOfflineStore().getName());
                storeMap.put("address", order.getOfflineStore().getAddress());
                map.put("offlineStore", storeMap);
            } else {
                map.put("offlineStore", null);
            }

            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = PaginationUtils.buildPageResponse(orderPage, cleanOrders);
        return ResponseEntity.ok(response);
    }

    /**
     * 【API】獲取已支付訂單（分頁 + 數據清洗）
     */
    @GetMapping("/api/account/orders/paid")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPaidOrders(
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication) {
        String username = authentication.getName();
        int zeroBasedPage = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(zeroBasedPage, 25);

        Page<Order> orderPage = orderRepository.findPaidOrders(username, pageable);

        //  核心修復：手動清洗數據
        List<Map<String, Object>> cleanOrders = orderPage.getContent().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("orderNo", order.getOrderNo());
            map.put("totalAmount", order.getTotalAmount());
            map.put("paymentMethod", order.getPaymentMethod());
            map.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
            map.put("status", order.getStatus() != null ? order.getStatus().name() : null);
            map.put("createdAt", order.getCreatedAt());
            map.put("paidAt", order.getPaidAt());
            map.put("receivedAt", order.getReceivedAt());

            // 已支付訂單可能來自線上或線下，安全提取店鋪信息
            if (order.getOfflineStore() != null) {
                Map<String, Object> storeMap = new HashMap<>();
                storeMap.put("name", order.getOfflineStore().getName());
                storeMap.put("address", order.getOfflineStore().getAddress());
                map.put("offlineStore", storeMap);
            } else {
                map.put("offlineStore", null);
            }

            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = PaginationUtils.buildPageResponse(orderPage, cleanOrders);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sell-guide")
    public String sellGuide(Model model) {
        String cardTheme = siteSettingService.getCardBorderTheme();
        model.addAttribute("cardTheme", cardTheme);
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
     * 修改：支持 1-based 分頁
     */
    @GetMapping("/api/notifications/stock")
    @ResponseBody
    public ResponseEntity<?> getStockNotifications(
            @RequestParam(defaultValue = "1") int page, // 改為默認 1
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 核心修改：前端傳入 1-based，JPA 需要 0-based，所以這裡 page - 1
        // 同時防止 page < 1 的情況
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 查詢到貨通知
        Page<AnnouncementReceipt> receiptPage = announcementReceiptRepository
                .findByUser_IdAndAnnouncement_TypeOrderByCreatedAtDesc(
                        user.getId(), Announcement.AnnouncementType.STOCK, pageable);

        // 進入該接口時，自動標記為已讀
        announcementReceiptRepository.markAllAsReadByUserAndType(user.getId(), Announcement.AnnouncementType.STOCK);

        // 使用 PaginationUtils 構建標準分頁響應
        // PaginationUtils 內部會處理 smartPages 的生成 (它通常期望 0-based 的 currentPage 來計算，但返回的 pageNumber 是 1-based)
        // 如果 PaginationUtils.buildPageResponse 內部使用的是 page.getNumber() (0-based)，則直接傳入 receiptPage 即可
        Map<String, Object> response = PaginationUtils.buildPageResponse(receiptPage, receiptPage.getContent());

        // 確保返回給前端的 currentPage 是 1-based (方便前端直接用)
        // 如果 PaginationUtils 已經處理了，這行可選；如果沒處理，手動覆蓋一下
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    /**
     * 【API 2】獲取管理通知列表 (獨立數據源: Notification + AdminPenalty + Appeal)
     * 修改：支持 1-based 分頁
     */
    @GetMapping("/api/notifications/admin")
    @ResponseBody
    public ResponseEntity<?> getAdminNotifications(
            @RequestParam(defaultValue = "1") int page, // 改為默認 1
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 核心修改：1-based 轉 0-based
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));

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

        // 確保返回給前端的 currentPage 是 1-based
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }


    @GetMapping("/account/history")
    public String myHistory(Model model,
                            Authentication authentication,
                            @RequestParam(defaultValue = "1") int page,
                            RedirectAttributes redirectAttributes) { // 【修改 1】注入 RedirectAttributes

        String username = authentication.getName();
        User user = userService.findByUsername(username);

        // 2. 獲取所有歷史記錄
        List<ViewHistory> allHistoryList = viewHistoryService.getUserHistory(username);

        // 3. 分頁參數計算
        int size = 15;
        int totalElements = allHistoryList.size();

        // 計算總頁數
        int totalPages = (int) Math.ceil((double) totalElements / size);
        if (totalPages == 0) totalPages = 1;

        // ==========================================
        // 【核心修復】：頁碼越界 -> 觸發 HTTP 重定向 (302)
        // ==========================================
        boolean needRedirect = false;
        int safePage = page;

        if (page > totalPages) {
            safePage = totalPages;
            needRedirect = true;
        } else if (page < 1) {
            safePage = 1;
            needRedirect = true;
        }

        // 如果頁碼不合法，直接重定向到合法的 URL
        // 例如：用戶訪問 ?page=2，但只有 1 頁 -> 重定向到 /account/history?page=1
        if (needRedirect) {
            return "redirect:/account/history?page=" + safePage;
        }

        // ==========================================
        // 以下是頁碼合法時的正常渲染邏輯
        // ==========================================

        // 轉換為 0-based index
        int pageIndex = safePage - 1;

        // 4. 截取當前頁數據
        int fromIndex = pageIndex * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<ViewHistory> pagedHistoryList = new ArrayList<>();
        if (fromIndex < totalElements) {
            pagedHistoryList = allHistoryList.subList(fromIndex, toIndex);
        }

        // 5. 生成智能分頁
        List<PaginationUtils.PageItem> smartPages = PaginationUtils.generateSmartPagination(pageIndex, totalPages);

        // 6. 傳遞數據到前端
        model.addAttribute("user", user);
        model.addAttribute("historyList", pagedHistoryList);

        // 這裡傳入 safePage (其實就是 page，因為如果不 redirect，page 本身就是合法的)
        model.addAttribute("currentPage", safePage);
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
    public String blockedUsers(Model model,
                               Authentication authentication,
                               @RequestParam(defaultValue = "1") int page) { // 1. 接收頁碼，默認第1頁

        String username = authentication.getName();

        // 【修改處 1】：使用 UserService 獲取 User 實體
        User user = userService.findByUsername(username);

        // 2. 構建分頁請求 (前端傳入 1-based，Spring Data 需要 0-based)
        int pageSize = 25;
        int pageIndex = Math.max(0, page - 1);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(pageIndex, pageSize);

        // 3. 調用 Repository 進行分頁查詢
        org.springframework.data.domain.Page<UserBlock> blockedUsersPage =
                userBlockRepository.findByBlocker_Username(username, pageable);

        // 4. 使用 PaginationUtils 構建標準分頁響應
        // 這裡不需要 extraData，所以調用重載方法或傳入 null
        Map<String, Object> pageData = PaginationUtils.buildPageResponse(blockedUsersPage, blockedUsersPage.getContent());

        // 5. 將數據傳遞給前端
        model.addAttribute("user", user);

        // 傳遞列表內容
        model.addAttribute("blockedUsers", pageData.get("content"));

        // 傳遞分頁相關變量 (供 Thymeleaf 渲染分頁組件使用)
        model.addAttribute("currentPage", page); // 保持 1-based 給前端顯示
        model.addAttribute("totalPages", pageData.get("totalPages"));
        model.addAttribute("totalElements", pageData.get("totalElements"));
        model.addAttribute("smartPages", pageData.get("smartPages"));

        return "blocked-users";
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