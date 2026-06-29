package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.*;
import org.example.website.repository.AdminPenaltyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller  // 關鍵：返回視圖名稱，不是 JSON
public class PageController {

    //  1. 聲明依賴變量
    private final CustomerService customerService;
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

    public PageController(CustomerService customerService,
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
                                  CartService cartService) {
        this.customerService = customerService;
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
    }

    @GetMapping("/")
    public String home() {
        return "home";  // 對應 templates/home.html
    }

    @GetMapping("/test")
    public String testPage() {
        return "test";  // 對應 templates/test.html
    }

    //  3. 全新的個人中心首頁路由 (帶有側邊欄的 Dashboard)
    @GetMapping("/account/dashboard")
    public String dashboard(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);
        return "dashboard";
    }

    //  4. 帳戶信息頁面路由
    @GetMapping("/account/profile")
    public String accountProfile(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);
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
        Customer customer = customerService.findByUsername(username);

        // 查詢當前用戶的訂單列表
        List<Order> orders = orderService.getUserOrders(username);


        // 分離待付款和已支付訂單
        List<Order> unpaidOrders = orders.stream()
                .filter(order -> order.getPaymentStatus() == Order.PaymentStatus.UNPAID ||
                        order.getPaymentStatus() == Order.PaymentStatus.PENDING_OFFLINE)
                .collect(Collectors.toList());

        List<Order> paidOrders = orders.stream()
                .filter(order -> order.getPaymentStatus() == Order.PaymentStatus.PAID_SIMULATED ||
                        order.getPaymentStatus() == Order.PaymentStatus.PAID_REAL ||
                        order.getPaymentStatus() == Order.PaymentStatus.PAID_OFFLINE)
                .collect(Collectors.toList());

        model.addAttribute("customer", customer);
        model.addAttribute("unpaidOrders", unpaidOrders);
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
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);
        return "password";
    }

    //  5. 修改：安全設置頁面路由 (加入真實登錄記錄查詢)
    @GetMapping("/account/security")
    public String securitySettings(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);

        //  獲取真實登錄記錄 (最近 10 條)
        List<LoginLog> loginLogs = loginLogRepository.findTop10ByUsernameOrderByLoginTimeDesc(username);

        //  獲取當前 Session ID，用於前端標記「當前設備」
        String currentSessionId = null;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getRequest().getSession(false) != null) {
            currentSessionId = attributes.getRequest().getSession().getId();
        }

        List<SecurityQuestion> securityQuestions =
                securityQuestionRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);

        //  將數據傳遞給 Thymeleaf 前端
        model.addAttribute("customer", customer);
        model.addAttribute("loginLogs", loginLogs);
        model.addAttribute("currentSessionId", currentSessionId);
        model.addAttribute("securityQuestions", securityQuestions); // 添加这行

        return "security"; // 對應 templates/security.html
    }

    /**
     * 管理中心首頁
     */
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        // 這裡可以添加數據統計邏輯
        return "admin-dashboard"; // 對應 templates/admin-dashboard.html
    }

    //  6. 修改：我的寄售商品頁面路由
    @GetMapping("/account/consignment")
    public String myConsignment(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);

        // 1. 查詢該用戶所有的出售申請，並過濾出 CONSIGNMENT (寄售) 模式
        List<SellApplication> allApps = sellApplicationRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);
        List<SellApplication> consignmentApps = allApps.stream()
                .filter(app -> app.getTransactionMode() == SellApplication.TransactionMode.CONSIGNMENT)
                .collect(Collectors.toList());

        // 2. 預處理數據：直接設置到實體的 @Transient 字段中
        BigDecimal totalProfit = BigDecimal.ZERO;
        int onSaleCount = 0;
        int soldCount = 0;

        for (SellApplication app : consignmentApps) {
            // 優先使用最終報價，如果沒有則使用預估價
            BigDecimal price = app.getFinalPrice() != null ? app.getFinalPrice() : app.getEstimatedPrice();
            app.setDisplayPrice(price);

            if (price != null) {
                // 計算 5% 服務費 (四捨五入到整數)
                BigDecimal fee = price.multiply(new BigDecimal("0.05")).setScale(0, RoundingMode.HALF_UP);
                // 賣家收益 = 總價 - 服務費
                BigDecimal profit = price.subtract(fee);

                app.setServiceFee(fee);
                app.setProfit(profit);

                // 如果已成交，累加到總收益
                if (app.getStatus() == SellApplication.ApplicationStatus.COMPLETED) {
                    totalProfit = totalProfit.add(profit);
                }
            }

            // 狀態文本映射
            String statusText = "";
            String statusClass = "";
            switch (app.getStatus()) {
                case ACCEPTED: statusText = "寄售中"; statusClass = "status-ACCEPTED"; onSaleCount++; break;
                case COMPLETED: statusText = "已售出"; statusClass = "status-COMPLETED"; soldCount++; break;
                case CANCELLED: case REJECTED: statusText = "已取消"; statusClass = "status-CANCELLED"; break;
                default: statusText = "鑑定/報價中"; statusClass = "status-PENDING"; break;
            }
            app.setStatusText(statusText);
            app.setStatusClass(statusClass);
        }

        // 3. 將數據放入 Model (直接傳遞實體列表)
        model.addAttribute("consignmentApps", consignmentApps);
        model.addAttribute("onSaleCount", onSaleCount);
        model.addAttribute("soldCount", soldCount);
        model.addAttribute("totalProfit", totalProfit);

        return "consignment";
    }

    /**
     *  新增：我的收藏頁面路由
     */
    @GetMapping("/account/favorites")
    public String myFavorites(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);

        // 查詢當前用戶的收藏列表（按時間倒序）
        List<Favorite> favorites = favoriteRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);
        model.addAttribute("favorites", favorites);

        return "favorites"; // 對應 templates/favorites.html
    }

    @GetMapping("/account/history")
    public String myHistory(Model model, Authentication authentication) {
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);

        List<ViewHistory> historyList = viewHistoryService.getUserHistory(username);
        model.addAttribute("historyList", historyList);

        return "history"; // 對應 templates/history.html
    }

    /**
     *  新增：我的結算記錄頁面路由 (財務中心)
     */
    @GetMapping("/account/settlement")
    public String mySettlement(Model model, Authentication authentication) {
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);

        // 1. 查詢該用戶所有「已完成」的出售申請（包含買斷和寄售）
        List<SellApplication> allApps = sellApplicationRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);
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
        model.addAttribute("settlements", completedApps);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalServiceFee", totalServiceFee);
        model.addAttribute("totalProfit", totalProfit);

        return "settlement"; // 對應 templates/settlement.html
    }

    @GetMapping("/account/reviews")
    public String myReviewsPage(Model model, Authentication authentication) {
        // 這裡不需要傳太多數據，因為前端會通過 AJAX 加載
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);
        return "reviews"; // 對應 templates/reviews.html
    }


    @GetMapping("/account/notifications")
    public String myNotifications(Model model, Authentication authentication) {
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);

        // 1. 獲取所有通知
        List<Notification> allNotifications = notificationRepository
                .findByRecipientUsernameOrderByCreatedAtDesc(username);

        // 2. 分類通知
        List<Notification> stockNotifications = allNotifications.stream()
                .filter(n -> n.getType() == Notification.NotificationType.STOCK)
                .collect(Collectors.toList());

        List<Notification> adminNotifications = allNotifications.stream()
                .filter(n -> n.getType() == Notification.NotificationType.SYSTEM)
                .collect(Collectors.toList());

        // ==========================================
        //  3. 核心重構：計算每條管理通知的「綜合狀態」
        // ==========================================
        Map<Long, String> notificationStatusMap = new HashMap<>();

        for (Notification notif : adminNotifications) {
            // 只處理「禁言相關」的通知 (根據標題包含"禁言"來判斷)
            if (notif.getTitle() != null && notif.getTitle().contains("禁言")) {

                // 1. 查找對應的處罰記錄 (透過 notificationId 精確綁定)
                Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByNotificationId(notif.getId());

                // 2. 查找對應的申訴記錄 (取最新的一條)
                Optional<Appeal> appealOpt = appealRepository.findTopByNotificationIdOrderByCreatedAtDesc(notif.getId());

                if (appealOpt.isPresent()) {
                    // 【情況 A：有申訴記錄】 -> 根據申訴狀態決定 UI 顯示
                    Appeal appeal = appealOpt.get();
                    if (appeal.getStatus() == Appeal.AppealStatus.PENDING) {
                        notificationStatusMap.put(notif.getId(), "APPEAL_PENDING"); // 審核中
                    } else if (appeal.getStatus() == Appeal.AppealStatus.APPROVED) {
                        notificationStatusMap.put(notif.getId(), "APPEAL_APPROVED"); // 申訴成功
                    } else if (appeal.getStatus() == Appeal.AppealStatus.REJECTED) {
                        notificationStatusMap.put(notif.getId(), "APPEAL_REJECTED"); // 申訴失敗
                    }
                } else {
                    // 【情況 B：無申訴記錄】 -> 根據處罰狀態決定 UI 顯示
                    if (penaltyOpt.isPresent()) {
                        AdminPenalty penalty = penaltyOpt.get();

                        //  觸發精確的懶檢查，確保過期的記錄在資料庫中被標記為 EXPIRED
                        adminPenaltyService.checkAndUpdatePenaltyStatus(penalty.getId());

                        // 重新獲取最新狀態 (因為懶檢查可能剛剛更新了資料庫)
                        penalty = adminPenaltyRepository.findById(penalty.getId()).get();

                        if (penalty.getStatus() == AdminPenalty.PenaltyStatus.ACTIVE) {
                            notificationStatusMap.put(notif.getId(), "SHOW_APPEAL_BTN"); // 生效中，可申訴
                        } else if (penalty.getStatus() == AdminPenalty.PenaltyStatus.EXPIRED) {
                            notificationStatusMap.put(notif.getId(), "EXPIRED_NO_APPEAL"); // 已過期
                        } else if (penalty.getStatus() == AdminPenalty.PenaltyStatus.REVOKED) {
                            notificationStatusMap.put(notif.getId(), "REVOKED_NO_APPEAL"); // 管理員手動解封
                        }
                    } else {
                        notificationStatusMap.put(notif.getId(), "NO_PENALTY_RECORD"); // 歷史髒數據 (沒有對應的處罰記錄)
                    }
                }
            }
        }

        // 4. 進入頁面後，自動將所有通知標記為已讀
        allNotifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(allNotifications);

        // 5. 傳遞數據到前端 ( 傳遞新的狀態 Map 替換原來的 appealStatusMap)
        model.addAttribute("customer", customer);
        model.addAttribute("stockNotifications", stockNotifications);
        model.addAttribute("adminNotifications", adminNotifications);
        model.addAttribute("notificationStatusMap", notificationStatusMap);

        return "notifications";
    }

    @GetMapping("/account/stock-notifications")
    public String stockNotifications(Model model,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "12") int size,
                                     Authentication authentication) {
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);

        //  修正 1：排序字段應為 createdAt (實體類中的字段名)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        //  修正 2：調用 Repository 中新增的分頁方法
        Page<StockNotification> notificationPage = stockNotificationRepository
                .findByUsernameOrderByCreatedAtDesc(username, pageable);

        model.addAttribute("notifications", notificationPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", notificationPage.getTotalPages());
        model.addAttribute("totalElements", notificationPage.getTotalElements());

        return "stock-notifications";
    }

    // 新增：根據 ID 取消訂閱 (用於前端 AJAX)
    @DeleteMapping("/api/stock-notification/unsubscribe-by-id/{id}")
    public ResponseEntity<?> unsubscribeById(@PathVariable Integer id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String username = authentication.getName();
        Optional<StockNotification> notification = stockNotificationRepository.findById(Long.valueOf(id));

        if (notification.isPresent() && notification.get().getUsername().equals(username)) {
            stockNotificationRepository.deleteById(Long.valueOf(id));
            return ResponseEntity.ok(ApiResponse.ok("已取消訂閱"));
        }

        return ResponseEntity.badRequest().body(ApiResponse.error("找不到該訂閱記錄"));
    }

    @GetMapping("/account/blocked-users")
    public String blockedUsers(Model model, Authentication authentication) {
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);

        // 查詢當前用戶禁言的所有記錄 (需要確保 UserBlockRepository 有這個方法)
        List<UserBlock> blockedUsers = userBlockRepository.findByBlockerUsername(username);

        model.addAttribute("customer", customer);
        model.addAttribute("blockedUsers", blockedUsers);
        return "blocked-users"; // 對應 templates/blocked-users.html
    }

    @GetMapping("/cart/view")
    public String viewCartPage(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "redirect:/";
        }

        String username = authentication.getName();

        // 獲取當前用戶信息 (側邊欄需要用來顯示頭像、姓名、郵箱)
        Customer customer = customerService.findByUsername(username);
        model.addAttribute("customer", customer);

        // 獲取購物車數據
        List<Cart> cartItems = cartService.getCartItems(username);
        long cartCount = cartService.getCartCount(username);

        // 計算總價
        double totalAmount = cartItems.stream()
                .mapToDouble(item -> item.getPrice().doubleValue() * item.getQuantity())
                .sum();

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("cartCount", cartCount);

        return "cart-detail";
    }
}