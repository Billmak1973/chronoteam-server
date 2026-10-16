# ⌚ ChronoTeam 專業二手名錶交易平台

## 📖 項目簡介
**ChronoTeam** 是一個致力於提供安全、透明、專業的二奢名錶交易生態圈平台。系統涵蓋了完整的買賣家交易閉環、豐富的社區互動機制、嚴格的帳號安全與處罰體系，以及高度定制化的管理員後台。前端採用 **React (Hooks) + Thymeleaf** 混合渲染架構，後端基於 **Spring Boot 3 + Spring Security + JPA**，並引入 **Redis** 進行分佈式限流與緩存，確保高併發下的數據一致性與系統安全性。

---

## 🛠️ 技術棧 (Tech Stack)

### 後端 (Backend)
*   **語言與框架**：Java 17+, Spring Boot 3.x
*   **安全與認證**：Spring Security, BCrypt, 自定義 `CustomUserDetails` (零查庫權限校驗)
*   **數據持久化**：Spring Data JPA, Hibernate, MySQL 8.0+ (JPQL, Native Query, 樂觀鎖)
*   **緩存與中間件**：Redis (ZSet 滑動窗口限流, JSON 序列化, 分佈式封禁標記)
*   **安全防護**：Jsoup (HTML 白名單過濾, 嚴格 XSS 防禦)
*   **API 文檔**：Swagger / OpenAPI 3 (`springdoc-openapi`)
*   **其他**：Lombok, Jakarta Validation, Spring Scheduling (`@Scheduled`)

### 前端 (Frontend)
*   **核心架構**：React 18 (Hooks, 狀態管理, 樂觀更新 UI), Thymeleaf (SSR 首屏渲染)
*   **基礎技術**：HTML5, CSS3 (CSS Variables 動態主題), Vanilla JavaScript (ES6+)
*   **UI 與圖標**：FontAwesome 6, 自定義 CSS 動畫 (星空/霓虹/心跳/平滑過渡)
*   **富文本編輯**：`contentEditable` + `document.execCommand` (自研輕量級工具欄，支持 IME 輸入法防抖)

### 工具與環境 (Tools & Environment)
*   **構建與依賴**：Maven
*   **版本控制**：Git
*   **開發環境**：IntelliJ IDEA

---

## 🚀 核心功能模塊

### 🛒 買家與商品中心
*   **多維度商品瀏覽**：支持按分類、品牌、價格區間、成色進行動態篩選與多字段排序。
「同款變體切換」：支持同款不同成色一鍵切換，動態加載庫存與價格。
*   **智能購物車 (React)**：
    *   導航欄下拉菜單與購物車詳情頁**雙向無刷新同步**。
    *   支持**樂觀更新**（數量調整、單選/全選結算、刪除），網絡失敗自動回滾 UI。
    *   庫存實時校驗與缺貨攔截提示。
*   **訂單與支付閉環**：
    *   支持**線上模擬支付**與**線下門店支付**。
    *   結賬頁支持動態計算運費（後台可配置免郵門檻）、主/備用地址切換與防篡改金額校驗。
    *   訂單狀態追蹤，支持訂單明細展開、取消與軟刪除（隱藏）。
*   **到貨通知系統**：缺貨商品一鍵訂閱，管理員補貨後一鍵批量發送系統公告通知。

### 💰 賣家與財務中心
*   **發佈出售申請**：支持多圖上傳、附件勾選、成色評估。主打 **「平台直接買斷」** 模式。
*   **財務結算看板**：自動計算並展示累計總成交額、平台服務費扣除及實際淨收益。

### 💬 社區互動與評論系統 (React 深度定制)
*   **富文本評論與回覆**：支持加粗、顏色、列表、連結等富文本編輯，支持根評論與樓中樓回覆。
*   **多層級互動**：支持點贊/踩、分頁、多種排序（最新、最早、最多點贊）。
*   **防刷與速率限制 (Rate Limit)**：互動操作綁定速率限制，1分鐘內頻繁操作觸發**自動封禁**。
*   **評論管理與歸檔**：作者可修改/刪除評論；管理員刪除評論需填寫原因，系統自動發送通知並將原評論**歸檔審計**（保留原始 ID 與互動數據）。
*   **兩級舉報系統**：分類選擇，防重複舉報攔截，舉報成功自動觸發系統通知紅點。

### 🛡️ 帳號安全與處罰體系
*   **多維度安全設置**：支持原密碼或 **「安全問答」** 驗證修改密碼；帳戶安全等級綜合評分。
*   **登錄日誌追蹤**：記錄真實 IP（支持多層代理頭解析）、設備類型、瀏覽器及 Session ID。
*   **禁言與拉黑機制**：
    *   **普通用戶**：雙向禁言（互相無法回覆，前端實時攔截）。
    *   **管理員**：全局禁言（自定義時長）、永久拉黑（剝奪所有互動權限）。
*   **申訴系統閉環**：用戶可針對處罰提交申訴，管理員審核後自動解除或維持處罰，前端實時展示多種申訴狀態。

---

## 🛠️ 管理員後台功能

*   **商品微調與推薦管理**：管理員可在詳情頁或後台直接修改商品資訊；支持設置**首頁精選熱賣權重**（智能排序算法自動調整前後權重）。
*   **評論區執法**：一鍵置頂評論、刪除違規評論並選擇/填寫刪除原因（系統自動發送通知並歸檔）。
*   **用戶處罰與申訴審核**：執行全局禁言、永久拉黑操作；查看並審核用戶的申訴請求。
*   **到貨通知管理**：查看缺貨商品的等待名單，一鍵批量發送補貨系統通知。
*   **系統動態配置**：實時調整基礎快遞費、免郵門檻、網絡訂單保留天數等，顧客結賬時立即生效。
*   **站點外觀設置**：支持一鍵切換「白天（金色奢華）」與「晚上（科技霓虹/星空）」主題。
*   **每日業務報表**：定時任務自動生成每日 GMV、訂單數、退款、新註冊用戶等統計數據，並精準區分線上/線下業績。

---

## 🏗️ 核心架構與技術深度解析 (Technical Highlights)

1. **高併發防超賣設計 (Optimistic Locking)**  
   線下門店庫存扣減採用 JPQL 原子更新語句：`UPDATE StoreInventory ... WHERE quantity >= :deductQty`。利用數據庫行鎖與條件判斷，徹底杜絕高併發下的超賣問題，無需引入重量級分佈式鎖。
2. **Redis ZSet 滑動窗口限流**  
   評論點贊/踩等高频操作，使用 Redis `ZSet` 記錄毫秒級時間戳。通過 `removeRangeByScore` 清理過期數據並統計窗口內操作次數。觸發閾值後自動寫入封禁記錄並設置 Redis 過期鍵，實現高性能、低延遲的防刷機制。
【雙重保險】：即使 Redis 數據丟失，Service 層也會通過 DB 查詢 `bannedUntil` 進行兜底校驗。
3. **JPA 極致性能優化**  
   * **解決 N+1 查詢**：後台訂單列表使用 `@Query("SELECT o FROM Order o LEFT JOIN FETCH o.user...")` 一次性加載關聯實體。
   * **高效聚合統計**：未讀消息數量統計直接使用 `COUNT(r)` 的 JPQL 查詢，避免將大量數據加載到內存中過濾。
   * **原生 SQL 特殊場景**：恢復被刪除評論時，使用 `nativeQuery = true` 的 `INSERT` 語句強制復用原始 `review_id`，確保前端歷史跳轉鏈接與互動數據的完整性。
4. **零查庫鑒權設計 (Zero-DB-Query Auth)**  
   自定義 `CustomUserDetails` 在用戶登入時，一次性將 `User` 的 `ID` 和 `Role` 枚舉載入 SecurityContext。後續所有接口（如 `SecurityUtils.isAdmin()`）直接從內存中讀取 Role 進行 O(1) 校驗，徹底消除權限驗證時的數據庫開銷。
5. **懶加載狀態自愈 (Lazy Self-Healing)**  
   針對用戶封禁狀態的檢查 (`isUserBanned`)，採用「查詢時順便修正過期狀態」的懶加載模式。若發現記錄已過期，立即更新 DB 狀態為 `EXPIRED` 並返回 false，取代了高耗時且易出錯的全表掃描定時任務。
6. **漸進式前端架構 (Progressive Enhancement)**  
   利用 Thymeleaf 處理 SEO 友好的靜態頁面和權限渲染，同時針對評論區 (`ReviewsContainer`)、購物車 (`CartDropdown`) 等需要複雜狀態管理的模塊，使用 React 18 Hooks 進行組件化開發，並通過 `ReactDOM.createRoot` 精準掛載到預留 DOM 節點，完美平衡首屏加載速度與用戶交互體驗。
7. **事務原子性保證**  
   訂單創建、庫存扣減、財務報表更新，以及售後換貨（生成新訂單 + 舊訂單退款 + 報表更新）等複雜業務，均嚴格使用 `@Transactional` 確保 ACID 特性，任何環節失敗均整體回滾。

---

## 📂 項目目錄結構

```text
├── src/main/java/org/example/website
│   ├── config/         # 安全配置、Redis配置、定時任務、數據初始化
│   ├── controller/     # RESTful API 與 Thymeleaf 頁面路由
│   ├── dto/            # 數據傳輸對象 (RegisterRequest, ApiResponse 等)
│   ├── entity/         # JPA 實體 (User, Product, Order, Review, AdminPenalty 等)
│   ├── repository/     # Spring Data JPA 接口與自定義 JPQL/Native 查詢
│   ├── security/       # 自定義 UserDetails 實現
│   ├── service/        # 核心業務邏輯 (訂單、評論、處罰、速率限制等)
│   └── util/           # 工具類 (UID生成器、SecurityUtils、分頁工具)
├── src/main/resources
│   ├── static/css/     # 全局樣式、側邊欄、管理後台樣式
│   ├── static/js/      # 全局腳本 (common.js, admin.js 等)
│   ├── static/react-assets/ # React 編譯後的評論區/購物車組件
│   └── templates/      # Thymeleaf HTML 模板 (首頁、詳情、後台、個人中心等)
```

---

## 📜 開發與運行說明

1. **環境準備**：JDK 17+, MySQL 8.0+, Redis, Node.js (用於前端構建)
2. **數據庫初始化**：啟動項目後，`DataInitializer` 會自動檢查並創建超級管理員帳號及基礎站點設置。
3. **前端構建**：React 組件通過本地構建引入，與 Thymeleaf 無縫融合。
4. **管理員登入**：默認帳號 `admin`（系統初始化日誌中會打印初始密碼，請在首次登入後修改）。

---

## 📸 系統截圖

<img width="1918" height="988" alt="image" src="https://github.com/user-attachments/assets/9661f92d-9aba-4a78-9028-806ad1f8d341" />
<img width="1885" height="822" alt="image" src="https://github.com/user-attachments/assets/584517e4-b075-4255-a38b-e2545278c859" />
<img width="1761" height="857" alt="image" src="https://github.com/user-attachments/assets/1eca248e-b7d8-462d-8bd8-4fe4102b4260" />
<img width="1913" height="906" alt="image" src="https://github.com/user-attachments/assets/c88ea245-bf38-4999-9424-8c01fb03396a" />

--- 

> **💡 提示**：本 README 嚴格基於當前代碼庫實際實現的功能編寫，所有技術亮點均可在對應的 Service/Repository 層找到源碼支撐。

