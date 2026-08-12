import React from 'react'
import ReactDOM from 'react-dom/client'

// 組件導入
import FavoriteButton from './components/FavoriteButton.jsx'
import CartDropdown from './components/CartDropdown.jsx'
import ReviewsContainer from './components/ReviewsContainer'

// 樣式導入
import './Reviews.css'
import './index.css'

console.log("🟢🟢 【探針】React main.jsx 已成功加載！");

// ==========================================
// 1. 掛載「收藏按鈕」組件
// ==========================================
const favoriteMountNode = document.getElementById('favorite-react-root')
if (favoriteMountNode) {
  // 直接讀取分散的 data 屬性
  const productId = favoriteMountNode.getAttribute('data-product-id')
  const isFavorite = favoriteMountNode.getAttribute('data-is-favorite') === 'true'

  ReactDOM.createRoot(favoriteMountNode).render(
      <React.StrictMode>
        <FavoriteButton
            productId={productId}
            initialIsFavorite={isFavorite}
        />
      </React.StrictMode>
  )
}

// ==========================================
// 2. 掛載「購物車下拉菜單」組件
// ==========================================
const cartMountNode = document.getElementById('react-cart-dropdown')
if (cartMountNode) {
  ReactDOM.createRoot(cartMountNode).render(
      <React.StrictMode>
        <CartDropdown />
      </React.StrictMode>
  )
}

// ==========================================
// 3. 掛載「完整評論區」組件 (核心修復)
// ==========================================
const reviewsRootNode = document.getElementById('reviews-react-root')
if (reviewsRootNode) {
  console.log("✅ 找到評論區掛載點，正在讀取屬性...");

  // 🔥 關鍵修復：直接從 DOM 讀取 Thymeleaf 渲染的分散屬性，而不是解析 JSON
  const rawProps = {
    productId: reviewsRootNode.getAttribute('data-product-id'),
    currentUsername: reviewsRootNode.getAttribute('data-current-username'),
    isAdmin: reviewsRootNode.getAttribute('data-is-admin'),
    canReview: reviewsRootNode.getAttribute('data-can-review'),
    reviewOrderNo: reviewsRootNode.getAttribute('data-review-order-no'),
    totalReviewCount: reviewsRootNode.getAttribute('data-total-review-count'),
    totalScore: reviewsRootNode.getAttribute('data-initial-avg-rating') // 注意這裡對應 HTML 中的屬性名
  };

  console.log("📦 讀取到的原始屬性:", rawProps);

  // 類型轉換與安全處理
  const safeProps = {
    productId: Number(rawProps.productId) || 0,
    currentUsername: rawProps.currentUsername || '',
    isAdmin: rawProps.isAdmin === 'true',
    canReview: rawProps.canReview === 'true',
    reviewOrderNo: rawProps.reviewOrderNo || '',
    initialTotalCount: Number(rawProps.totalReviewCount) || 0,
    initialAvgRating: Number(rawProps.totalScore) || 0,
  };

  console.log("🚀 最終傳遞給 React 的 Props:", safeProps);

  // 只有當 productId 有效時才渲染，防止報錯
  if (safeProps.productId > 0) {
    ReactDOM.createRoot(reviewsRootNode).render(
        <React.StrictMode>
          <ReviewsContainer {...safeProps} />
        </React.StrictMode>
    );
  } else {
    console.error("❌ 錯誤：productId 無效，無法渲染評論區！請檢查 Thymeleaf 是否正確輸出了 data-product-id");
    reviewsRootNode.innerHTML = '<div style="color:red; padding:2rem;">評論區加載失敗：缺少商品 ID</div>';
  }

} else {
  console.warn("⚠️ 找不到 id 為 reviews-react-root 的 HTML 節點！");
}