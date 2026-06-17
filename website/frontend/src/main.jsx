import React from 'react'
import ReactDOM from 'react-dom/client'
import FavoriteButton from './FavoriteButton.jsx'
import CartDropdown from './CartDropdown.jsx'
import ReplySection from './ReplySection.jsx' //  引入樓中樓回覆組件
import './index.css'

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
// 3. 批量掛載「樓中樓回覆區」組件 (商品詳情頁多處生效)
// ==========================================
// 因為一頁有多條根評論，所以用 querySelectorAll 找出所有掛載點
const replyMountNodes = document.querySelectorAll('.react-reply-mount')

replyMountNodes.forEach(node => {
  // 從 Thymeleaf 渲染的 data-* 屬性中提取數據，傳遞給 React 組件
  const props = {
    reviewId: node.getAttribute('data-review-id'),
    initialReplyCount: parseInt(node.getAttribute('data-reply-count')) || 0,
    productId: node.getAttribute('data-product-id'),
    currentUsername: node.getAttribute('data-current-username'),
    isAdmin: node.getAttribute('data-is-admin') === 'true'
  }

  // 為每一個根評論的掛載點，渲染一個獨立的 ReplySection 組件
  ReactDOM.createRoot(node).render(
    <React.StrictMode>
      <ReplySection {...props} />
    </React.StrictMode>
  )
})