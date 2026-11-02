import React from 'react'
import ReactDOM from 'react-dom/client'

// 組件導入 (確保這些文件已經改為 .tsx)
import FavoriteButton from './components/FavoriteButton'
import CartDropdown from './components/CartDropdown'
import ReviewsContainer from './components/ReviewsContainer'

// 樣式導入
import './Reviews.css'
import './index.css'

console.log("🟢🟢 【探針】React main.tsx 已成功加載！")

// ==========================================
// 定義從 DOM 讀取的原始屬性介面
// ==========================================
interface DomProps {
  productId: string | null
  currentUsername: string | null
  isAdmin: string | null
  canReview: string | null
  reviewOrderNo: string | null
  totalReviewCount: string | null
  totalScore: string | null
}

// ==========================================
// 定義傳遞給 React 組件的安全屬性介面
// (需與 ReviewsContainer.tsx 的 Props 保持一致)
// ==========================================
interface SafeProps {
  productId: number
  currentUsername: string
  isAdmin: boolean
  canReview: boolean
  reviewOrderNo: string
  initialTotalCount: number
  initialAvgRating: number
}

// ==========================================
// 1. 掛載「收藏按鈕」組件
// ==========================================
const favoriteMountNode = document.getElementById('favorite-react-root')
if (favoriteMountNode) {
  const productIdStr = favoriteMountNode.getAttribute('data-product-id')
  const isFavoriteStr = favoriteMountNode.getAttribute('data-is-favorite')

  ReactDOM.createRoot(favoriteMountNode).render(
      <React.StrictMode>
        <FavoriteButton
            productId={productIdStr ? Number(productIdStr) : 0}
            initialIsFavorite={isFavoriteStr === 'true'}
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
  console.log("✅ 找到評論區掛載點，正在讀取屬性...")

  // 1. 讀取原始字符串屬性
  const rawProps: DomProps = {
    productId: reviewsRootNode.getAttribute('data-product-id'),
    currentUsername: reviewsRootNode.getAttribute('data-current-username'),
    isAdmin: reviewsRootNode.getAttribute('data-is-admin'),
    canReview: reviewsRootNode.getAttribute('data-can-review'),
    reviewOrderNo: reviewsRootNode.getAttribute('data-review-order-no'),
    totalReviewCount: reviewsRootNode.getAttribute('data-total-review-count'),
    totalScore: reviewsRootNode.getAttribute('data-initial-avg-rating')
  }

  console.log("📦 讀取到的原始屬性:", rawProps)

  // 2. 安全的類型轉換與處理 (處理 null 的情況)
  const safeProps: SafeProps = {
    productId: rawProps.productId ? Number(rawProps.productId) : 0,
    currentUsername: rawProps.currentUsername || '',
    isAdmin: rawProps.isAdmin === 'true',
    canReview: rawProps.canReview === 'true',
    reviewOrderNo: rawProps.reviewOrderNo || '',
    initialTotalCount: rawProps.totalReviewCount ? Number(rawProps.totalReviewCount) : 0,
    initialAvgRating: rawProps.totalScore ? Number(rawProps.totalScore) : 0,
  }

  console.log("🚀 最終傳遞給 React 的 Props:", safeProps)

  // 3. 只有當 productId 有效時才渲染，防止報錯
  if (safeProps.productId > 0) {
    ReactDOM.createRoot(reviewsRootNode).render(
        <React.StrictMode>
          <ReviewsContainer {...safeProps} />
        </React.StrictMode>
    )
  } else {
    console.error("❌ 錯誤：productId 無效，無法渲染評論區！請檢查 Thymeleaf 是否正確輸出了 data-product-id")
    reviewsRootNode.innerHTML = '<div style="color:red; padding:2rem;">評論區加載失敗：缺少商品 ID</div>'
  }

} else {
  console.warn("⚠️ 找不到 id 為 reviews-react-root 的 HTML 節點！")
}