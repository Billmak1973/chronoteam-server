

# ChronoTeam - 專業二手名錶交易平台

## 📖 項目簡介
ChronoTeam 是一個基於 Spring Boot 構建的專業二手名錶交易生態圈平台。平台致力於提供安全、透明的交易環境，支持用戶瀏覽商品、購物車管理、收藏互動，並為賣家提供「平台直接買斷」與「平台代為寄售」雙重出售模式，同時具備完善的安全認證與登入追蹤機制。

## 🛠️ 技術棧
- **後端框架**：Java, Spring Boot, Spring Security, Spring Data JPA
- **前端模板**：Thymeleaf, HTML5, CSS3, JavaScript (Vanilla)
- **數據庫**：關係型數據庫 (透過 JPA/Hibernate 映射，如 MySQL)
- **工具庫**：Lombok, Jackson (JSON 處理), Jakarta Validation

## ✨ 核心功能模塊

### 1. 用戶認證與帳戶安全
- **註冊與登入**：支援表單註冊與登入，登入後自動綁定 Session。
- **安全驗證**：支援「當前密碼驗證」或「安全問題驗證」來修改密碼。
- **安全問答管理**：用戶可設置、修改安全問題（答案統一轉小寫存儲，驗證時不區分大小寫）。
- **登入日誌追蹤**：`LoginSuccessListener` 自動記錄登入 IP（支援 `X-Forwarded-For` 等代理頭解析）、User-Agent 和 Session ID。同 IP 登入會更新時間，不同 IP 會創建新記錄。前端可查看近期登入設備並標記「當前設備」。
- **帳戶管理**：查看帳戶信息、信用額度，支援申請註銷帳戶。

### 2. 商品瀏覽與互動
- **多維度篩選與排序**：支援按分類、品牌（含「其他品牌」特殊邏輯處理）、價格區間、關鍵字進行篩選；支援按最新上架、價格升/降序動態排序與分頁。
- **商品詳情與評論**：展示商品詳細介紹，已登入用戶可對商品進行 1-5 星評分並發表評論。
- **收藏功能**：支援一鍵切換商品收藏狀態（收藏/取消收藏），並提供專屬的「我的收藏」頁面。
- **瀏覽歷史**：自動記錄已登入用戶的商品瀏覽行為。支援按 1天、1週、1個月 或 全部 清除歷史記錄；後台設有定時任務自動清理半年前的記錄，且單用戶最多保留 200 條記錄。

### 3. 購物車系統
- 支援添加商品、更新數量、移除商品。
- 自動計算購物車總價與商品總數。
- **未登入相容**：未登入用戶訪問購物車接口會安全返回空數據，避免 NullPointerException。

### 4. 賣家出售服務 (Sell Application)
- **雙模式交易**：
  - **BUYOUT (平台直接買斷)**：快速收款，申請記錄在「我的出售申請」中查看。
  - **CONSIGNMENT (平台代為寄售)**：價格更優，平台收取 5% 服務費，申請記錄在「我的寄售商品」中查看，並自動計算預估收益。
- **表單與圖片上傳**：支援填寫手錶基礎資訊、附件狀態、成色，並透過 `FileStorageService` 上傳多張手錶照片（錶面、錶背、保卡、瑕疵等），圖片路徑以 JSON 格式存儲。
- **申請管理**：支援取消處於 `PENDING` (待鑑定) 狀態的申請，或徹底物理刪除申請記錄。

### 5. 靜態資訊與引導頁面
平台已完整實現以下靜態展示頁面，提升用戶信任度：
- 關於我們 (`/about`)
- 專業鑑定流程 (`/authentication`)
- 買家保障計劃 (`/buyer-protection`)
- 賣家指南 (`/sell-guide`)
- 聯絡我們 (`/contact`)
- 常見問題 FAQ (`/faq`)

### 6. 管理後台 (Admin Dashboard)
- 已搭建深色奢華風格的管理後台 UI 框架 (`/admin/dashboard`)。
- 包含側邊欄導航（數據儀表板、核心業務管理、名錶平台特色、系統與權限管理）及統計卡片 UI。

---

## 📂 項目結構概覽
```text
org.example.website
├── config/          # 安全配置 (SecurityConfig)、資源映射 (WebConfig)、登入監聽 (LoginSuccessListener)
├── controller/      # REST API 與 Thymeleaf 頁面路由控制 (Api, Browse, Cart, Favorite, Page, Sell, Product 等)
├── dto/             # 數據傳輸對象 (ApiResponse, LoginRequest, RegisterRequest, SellApplicationDTO)
├── entity/          # JPA 實體類 (Customer, Product, Cart, Favorite, SellApplication, LoginLog, ViewHistory 等)
├── repository/      # Spring Data JPA 數據訪問接口
└── service/         # 核心業務邏輯 (CustomerService, CartService, ViewHistoryService, FileStorageService 等)
```

---

## ⚠️ 已知限制與當前狀態 (基於現有代碼)
1. **訂單與支付**：`Order` 實體已定義，但支付狀態目前僅支援模擬 (`PAID_SIMULATED`)，尚未對接真實的第三方支付網關（如 PayPal/Stripe）。
2. **管理後台功能**：後台頁面 (`admin-dashboard.html`) 目前為 **UI 佔位符 (Placeholder)**，商品管理、訂單管理、用戶管理等具體業務邏輯尚未對接後端 API。
3. **部分帳戶修改功能**：前端「修改郵箱」與「修改手機」按鈕目前綁定的是開發中提示 (`showNotification`)，尚未實現完整的驗證與修改流程。
4. **評價系統**：前端已實現評論提交 UI 與邏輯，但對應的後端 `ReviewController` (處理 `/api/review/{id}`) 在提供的代碼片段中未完整展示，需確保後端有對應實現。

---

## 🚀 快速啟動
1. 確保已安裝 JDK 17+ 及關係型數據庫。
2. 配置 `application.properties` (或 `application.yml`)，設置數據庫連接及 `upload.path` (圖片上傳目錄，預設為 `./uploads`)。
3. 運行 `WebsiteApplication.java` 啟動 Spring Boot 應用。
4. 訪問 `http://localhost:8080` 即可瀏覽平台首頁。

--- 
