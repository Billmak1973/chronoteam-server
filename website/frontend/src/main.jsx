import React from 'react'
import ReactDOM from 'react-dom/client'

// 加上 ./components/ 路徑
import FavoriteButton from './components/FavoriteButton.jsx'
import CartDropdown from './components/CartDropdown.jsx'

//引入全新的評論區頂層組件與隔離後的 CSS
import ReviewsContainer from './components/ReviewsContainer'
import './Reviews.css'
import './index.css'

// 🟢 探針必須放在所有 import 之後！
console.log("🟢🟢🟢 【探針】React main.jsx 已成功加載並開始執行！");

// ==========================================
// 1. 掛載「收藏按鈕」組件 (僅在商品詳情頁生效)
// ==========================================
const favoriteMountNode = document.getElementById('favorite-react-root')
if (favoriteMountNode) {
  const productId = favoriteMountNode.getAttribute('data-product-id')
  const initialIsFavorite = favoriteMountNode.getAttribute('data-is-favorite') === 'true'

  ReactDOM.createRoot(favoriteMountNode).render(
    <React.StrictMode>
      <FavoriteButton
        productId={productId}
        initialIsFavorite={initialIsFavorite}
      />
    </React.StrictMode>
  )
}

// ==========================================
// 2. 掛載「購物車下拉菜單」組件 (全局導航欄生效)
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
// 3. 🟢 掛載「完整評論區」組件
// ==========================================
const reviewsRootNode = document.getElementById('reviews-react-root')

if (reviewsRootNode) {
  console.log("✅ 找到評論區掛載點，準備渲染 React..."); // 加這行

  const props = {
    productId: parseInt(reviewsRootNode.dataset.productId),
    currentUsername: reviewsRootNode.dataset.currentUsername || '',
    isAdmin: reviewsRootNode.dataset.isAdmin === 'true',
    canReview: reviewsRootNode.dataset.canReview === 'true',
    reviewOrderNo: reviewsRootNode.dataset.reviewOrderNo || '',
    initialTotalCount: parseInt(reviewsRootNode.dataset.totalReviewCount) || 0,
    initialAvgRating: parseFloat(reviewsRootNode.dataset.totalScore) || 0
  }

  ReactDOM.createRoot(reviewsRootNode).render(
    <React.StrictMode>
      <ReviewsContainer {...props} />
    </React.StrictMode>
  )
} else {
  console.warn("⚠️ 找不到 id 為 reviews-react-root 的 HTML 節點！");
}