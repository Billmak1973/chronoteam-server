
---

# ChronoTeam 專業二手名錶交易平台

ChronoTeam 是一個基於 Spring Boot 構建的專業二手名錶交易生態圈平台。平台提供嚴格的鑑定流程、安全的擔保交易機制，支持用戶瀏覽商品、購物車下單、線上/線下支付，以及手錶的「平台買斷」與「平台寄售」出售服務。

## 🛠️ 技術棧

- **後端框架**: Java, Spring Boot, Spring Security, Spring Data JPA
- **緩存服務**: Redis (用於用戶信息緩存及解決 `LocalDateTime` 序列化問題)
- **前端視圖**: Thymeleaf, HTML5, CSS3, Vanilla JavaScript (Fetch API)
- **工具庫**: Lombok, Jackson, FontAwesome (圖標庫)
- **數據庫**: 關係型數據庫 (通過 JPA/Hibernate 映射，具體類型由 `application.properties` 決定)

---

## ✨ 核心功能模塊

### 1. 用戶認證與安全 (Security & Account)
- **註冊與登錄**: 支持用戶註冊，登錄時自動記錄真實 IP（優先解析 `X-Forwarded-For` 等代理頭部）、User-Agent 和 Session ID。
- **會話管理**: 啟用單點登錄保護 (`maximumSessions(1)`)，新設備登錄會使舊設備失效。
- **密碼管理**: 支持修改密碼，提供兩種驗證方式：「當前密碼驗證」或「安全問題驗證」。
- **安全問答**: 用戶可設置、修改安全問答（答案統一轉為小寫存儲，前端脫敏顯示），用於帳戶找回和敏感操作驗證。
- **登錄日誌**: 個人中心可查看最近 10 條登錄記錄，自動識別設備名稱（iPhone, Windows, Mac 等）、瀏覽器，並對 IP 進行脫敏處理，標記「當前設備」。

### 2. 商品瀏覽與搜索 (Browse & Product)
- **多維度篩選**: 支持按分類 (正裝錶、潛水錶等)、品牌 (支持勾選「其他品牌」邏輯)、價格區間、關鍵詞進行組合篩選。
- **排序功能**: 支持按最新上架、價格從低到高、價格從高到低排序。
- **商品詳情**: 展示商品圖片、價格、庫存、詳細介紹，並支持已登錄用戶發表評論。
- **瀏覽歷史**: 自動記錄已登錄用戶的瀏覽歷史（最多保留 200 條），支持按「1天內」、「1週內」、「1個月內」或「全部」手動清除，並配有定時任務自動清理半年前的記錄。

### 3. 購物車與訂單 (Cart & Order)
- **購物車管理**: 支持添加商品、更新數量（校驗庫存）、移除商品，未登錄用戶訪問返回空數據避免異常。
- **訂單創建**: 從購物車一鍵生成訂單，生成唯一訂單號，並同步扣減商品庫存。
- **支付方式**:
  - **線上模擬支付**: 模擬支付流程，校驗金額防篡改，支付成功後更新訂單狀態。
  - **線下店鋪支付**: 支持選擇具體線下門店（中環店、尖沙咀店、銅鑼灣店），訂單狀態變更為 `PENDING_OFFLINE`，並保留 7 天。
- **訂單管理**: 用戶可查看個人訂單列表、訂單狀態（待付款、已付款、已發貨、已完成、已取消）及支付方式詳情。

### 4. 出售與寄售服務 (Sell & Consignment)
- **發佈出售申請**: 支持填寫手錶基礎資訊、勾選附件狀態、描述瑕疵，並上傳多張圖片（錶面、錶背、保卡、瑕疵特寫等，單文件限制 10MB）。
- **交易模式選擇**:
  - **平台直接買斷 (BUYOUT)**: 鑑定後平台直接報價收購。
  - **平台代為寄售 (CONSIGNMENT)**: 平台代為上架銷售，成交後扣除 5% 服務費。
- **我的出售申請**: 專門展示「買斷 (BUYOUT)」模式的申請記錄，支持在 `PENDING` 狀態下取消申請，或徹底物理刪除記錄。
- **我的寄售商品**: 專門展示「寄售 (CONSIGNMENT)」模式的申請，自動計算並展示寄售報價、平台服務費 (5%) 及預估賣家收益，支持申請下架。
- **財務結算記錄**: 統計已完成交易的「累計總成交額」、「累計平台服務費」及「實際淨收益」，並提供明細列表。

### 5. 收藏與互動 (Favorite & Review)
- **商品收藏**: 支持一鍵切換收藏/取消收藏狀態，並帶有前端動畫反饋。
- **我的收藏**: 獨立頁面展示用戶收藏的所有商品，支持快速取消收藏。
- **商品評論**: 已登錄用戶可在商品詳情頁發表評分 (1-5星) 和文字評論。

### 6. 靜態資訊頁面
- 首頁 (`/`)、關於我們 (`/about`)、專業鑑定流程 (`/authentication`)、賣家指南 (`/sell-guide`)、買家保障計劃 (`/buyer-protection`)、常見問題 FAQ (`/faq`)、聯絡我們 (`/contact`)。

### 7. 管理後台 (Admin Dashboard)
- 提供完整的管理中心前端 UI 框架 (`/admin/dashboard`)，包含側邊欄菜單切換動畫。
- *註：根據代碼，目前後台頁面（數據儀表板、商品管理、訂單管理、用戶管理、評論審核、出售申請管理、鑑定師工作台、財務與結算等）主要為前端佔位符 (Placeholder)，需後續對接後端 API。*

---

## 📂 項目結構 (推斷)

```text
org.example.website
├── config/             # 配置類 (SecurityConfig, RedisConfig, WebConfig, LoginSuccessListener)
├── controller/         # 控制器 (Api, Browse, Cart, Checkout, Favorite, Page, Password, Product, Security, Sell, ViewHistory)
├── dto/                # 數據傳輸對象 (ApiResponse, LoginRequest, RegisterRequest, SellApplicationDTO)
├── entity/             # JPA 實體類 (Cart, Customer, Favorite, LoginLog, Order, Product, Review, SecurityQuestion, SellApplication, ViewHistory)
├── repository/         # JPA 數據訪問接口
└── service/            # 業務邏輯層 (CartService, CustomerService, CustomUserDetailsService, FileStorageService, OrderService, ViewHistoryService)
```

---

## ⚙️ 配置說明

### 1. 文件上傳配置 (`application.properties`)
```properties
# 文件上傳路徑 (默認為 ./uploads 或 /tmp/chronoteam/uploads)
upload.path=./uploads
# 單個文件最大限制 (默認 10MB)
upload.max-size=10485760
```
*註：`FileStorageService` 會在應用啟動時 (`@PostConstruct`) 自動創建該目錄。*

### 2. Redis 配置
- 項目使用 Redis 緩存用戶信息 (`user:info:{username}`，過期時間 1 小時) 以減輕數據庫壓力。
- `RedisConfig` 中已配置 `ObjectMapper` 啟用默認類型序列化 (`@class`)，解決 `LocalDateTime` 和自定義實體反序列化為 `LinkedHashMap` 的問題。
- **清理緩存命令**: `docker exec my-redis redis-cli FLUSHDB`

### 3. Spring Security 配置
- 禁用 CSRF (`csrf.disable()`)。
- 放行 `/api/**`, `/css/**`, `/js/**`, `/images/**` 等靜態與 API 資源。
- 登出配置：監聽 `POST /logout`，銷毀 Session，清除認證信息，刪除 `JSESSIONID` Cookie，並重定向至首頁。

---

## 🚀 待開發/需注意事項 (基於代碼現狀)

1. **管理後台 API**: `admin-dashboard.html` 中的各項管理功能目前僅為前端 UI 佔位，尚未實現對應的後端 Controller 和 Service。
2. **評論功能**: 前端 (`product-detail.html`) 已實現評論表單提交邏輯 (`/api/review/{id}`)，但提供的文檔片段中未包含完整的 `ReviewController` 實現，需確保後端有對應接口。
3. **定時任務**: `ViewHistoryService` 中包含 `@Scheduled(cron = "0 0 2 * * ?")` 定時清理半年前瀏覽記錄的任務，需確保主類 `WebsiteApplication` 已標註 `@EnableScheduling` (代碼中已包含)。
4. **線下支付狀態流轉**: 線下支付訂單創建後狀態為 `PENDING_OFFLINE`，文檔中未包含店員後台確認收款並流轉為 `PAID` 的具體實現邏輯。

--- 
