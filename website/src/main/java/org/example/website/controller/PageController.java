package org.example.website.controller;

import org.example.website.entity.*;
import org.example.website.repository.LoginLogRepository;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.SellApplicationRepository;
import org.example.website.repository.FavoriteRepository;
import org.example.website.service.CustomerService;
import org.example.website.service.OrderService;
import org.example.website.service.ViewHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
    private final NotificationRepository notificationRepository; // 🟢 新增聲明

    //  2. 通過構造函數注入依賴 (加入了 SellApplicationRepository)
    public PageController(CustomerService customerService,
                          LoginLogRepository loginLogRepository,
                          SellApplicationRepository sellApplicationRepository,
                          FavoriteRepository favoriteRepository,
                          ViewHistoryService viewHistoryService,
                          OrderService orderService,
                          NotificationRepository notificationRepository) {
        this.customerService = customerService;
        this.loginLogRepository = loginLogRepository;
        this.sellApplicationRepository = sellApplicationRepository;
        this.favoriteRepository = favoriteRepository;
        this.viewHistoryService = viewHistoryService;
        this.orderService = orderService;
        this.notificationRepository = notificationRepository;
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

        // 🟢 獲取真實登錄記錄 (最近 10 條)
        List<LoginLog> loginLogs = loginLogRepository.findTop10ByUsernameOrderByLoginTimeDesc(username);

        // 🟢 獲取當前 Session ID，用於前端標記「當前設備」
        String currentSessionId = null;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getRequest().getSession(false) != null) {
            currentSessionId = attributes.getRequest().getSession().getId();
        }

        //  將數據傳遞給 Thymeleaf 前端
        model.addAttribute("customer", customer);
        model.addAttribute("loginLogs", loginLogs);
        model.addAttribute("currentSessionId", currentSessionId);

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

    //  新增：系統通知頁面路由
    @GetMapping("/account/notifications")
    public String myNotifications(Model model, Authentication authentication) {
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);

        // 1. 獲取所有通知
        List<Notification> notifications = notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc(username);

        // 2. 🟢 核心：進入頁面後，自動將所有通知標記為已讀
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);

        model.addAttribute("customer", customer);
        model.addAttribute("notifications", notifications);

        return "notifications"; // 對應 templates/notifications.html
    }
}