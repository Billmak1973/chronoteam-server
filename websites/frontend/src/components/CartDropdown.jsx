import React, { useState, useEffect, useCallback } from 'react'

const CartDropdown = () => {
  const [cartItems, setCartItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [isOpen, setIsOpen] = useState(false)
  const [optimisticUpdates, setOptimisticUpdates] = useState({})

  // 獲取購物車數據
  const fetchCart = useCallback(async () => {
    try {
      setLoading(true)
      const response = await fetch('/api/cart/list')
      const data = await response.json()
      if (data.success) {
        setCartItems(data.cartItems || [])
      }
    } catch (error) {
      console.error('獲取購物車失敗:', error)
    } finally {
      setLoading(false)
    }
  }, [])

  // 初始加載與打開時刷新
  useEffect(() => {
    if (isOpen) {
      fetchCart()
    }
  }, [isOpen, fetchCart])

  // 樂觀更新：修改數量
  const updateQuantity = async (cartId, newQuantity) => {
    if (newQuantity <= 0) return

    // 1. 立即更新 UI (樂觀更新)
    const originalItems = [...cartItems]
    setCartItems(items =>
      items.map(item =>
        item.cartId === cartId ? { ...item, quantity: newQuantity } : item
      )
    )

    // 2. 標記正在更新
    setOptimisticUpdates(prev => ({ ...prev, [cartId]: true }))

    try {
      // 3. 發送 API 請求
      const response = await fetch(`/api/cart/update/${cartId}?quantity=${newQuantity}`, {
        method: 'PUT'
      })

      const data = await response.json()

      if (!response.ok) {
        // 4. 如果失敗，回滾到原始狀態
        setCartItems(originalItems)
        alert(data.message || '更新失敗')
      } else {
        // 5. 成功後刷新數據確保一致性 (可選，因為樂觀更新已經改了UI)
        // await fetchCart()
      }
    } catch (error) {
      // 6. 網絡錯誤也回滾
      setCartItems(originalItems)
      alert('網絡錯誤')
    } finally {
      setOptimisticUpdates(prev => ({ ...prev, [cartId]: false }))
    }
  }

  // 樂觀更新：刪除商品
  const removeItem = async (productId) => {
    if (!confirm('確定要移除這個商品嗎？')) return

    // 1. 立即從 UI 移除
    const originalItems = [...cartItems]
    setCartItems(items => items.filter(item => item.product.productId !== productId))

    try {
      const response = await fetch(`/api/cart/remove/${productId}`, {
        method: 'DELETE'
      })

      if (!response.ok) {
        // 2. 失敗則回滾
        setCartItems(originalItems)
        alert('刪除失敗')
      }
    } catch (error) {
      setCartItems(originalItems)
      alert('網絡錯誤')
    }
  }

  //  新增：切換選中狀態 (樂觀更新)
  const toggleSelection = async (cartId, currentStatus) => {
    const newStatus = !currentStatus

    // 1. 立即更新 UI (樂觀更新)
    const originalItems = [...cartItems]
    setCartItems(items => items.map(item =>
        item.cartId === cartId ? { ...item, selected: newStatus } : item
    ))

    try {
        // 2. 發送 API 請求
        await fetch(`/api/cart/toggle-selection/${cartId}?isSelected=${newStatus}`, {
            method: 'PUT'
        })
    } catch (error) {
        console.error('更新狀態失敗', error)
        // 3. 失敗則回滾 UI
        setCartItems(originalItems)
    }
  }

  //  核心修復：處理結帳邏輯 (調用後端 API 創建訂單並跳轉)
  const handleCheckout = async () => {
    // 【新增檢查】至少選中一個商品
    const selectedItems = cartItems.filter(item => item.selected !== false)
    if (selectedItems.length === 0) {
      alert('請至少選擇一件商品進行結算！')
      return
    }

    try {
      // 調用後端創建訂單 API
      const response = await fetch('/checkout/api/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      })
      const result = await response.json()

      if (response.ok && result.success) {
        // 成功獲取 orderNo，跳轉到結帳頁面
        window.location.href = `/checkout?orderNo=${result.data}`
      } else {
        alert(result.message || '創建訂單失敗，請稍後重試')
      }
    } catch (error) {
      console.error('結帳錯誤:', error)
      alert('網絡錯誤，無法創建訂單')
    }
  }

  //  計算總價 (只計算選中的商品)
  const totalAmount = cartItems
    .filter(item => item.selected !== false) // 過濾掉未選中的
    .reduce((sum, item) => sum + (item.price * item.quantity), 0)

  // 購物車角標數量 (計算所有商品，保持原樣)
  const cartCount = cartItems.reduce((sum, item) => sum + item.quantity, 0)

  // 格式化價格
  const formatPrice = (price) => {
    return new Intl.NumberFormat('zh-HK', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(price)
  }

  return (
    <div className="nav-dropdown">
      {/* 購物車按鈕 */}
      <a
        href="javascript:void(0)"
        className="nav-dropdown-trigger"
        onClick={(e) => { e.stopPropagation(); setIsOpen(!isOpen); }}
        onMouseEnter={() => setIsOpen(true)}
      >
        <i className="fas fa-shopping-cart"></i> 購物車
        {cartCount > 0 && (
          <span id="cartCountBadge" className="cart-badge" style={{display: cartCount > 0 ? 'inline-flex' : 'none'}}>{cartCount}</span>
        )}
      </a>

      {/* 下拉菜單 */}
      {isOpen && (
        <div
          className="nav-dropdown-menu cart-dropdown-menu"
          onMouseLeave={() => setIsOpen(false)}
        >
          <div className="cart-header">
            <h3><i className="fas fa-shopping-cart"></i> 購物車</h3>
          </div>

          <div className="cart-body">
            {loading ? (
              <div className="cart-loading">
                <i className="fas fa-spinner fa-spin"></i>
                <p>加載中...</p>
              </div>
            ) : cartItems.length === 0 ? (
              <div className="cart-empty">
                <i className="fas fa-shopping-basket" style={{fontSize: '3rem', color: '#ddd', marginBottom: '1rem'}}></i>
                <p>購物車是空的</p>
              </div>
            ) : (
              <div className="cart-items">
                {cartItems.map(item => (
                  <CartItem
                    key={item.cartId}
                    item={item}
                    onUpdateQuantity={updateQuantity}
                    onRemove={removeItem}
                    onToggleSelection={toggleSelection} //  傳遞切換函數
                    formatPrice={formatPrice}
                    isUpdating={optimisticUpdates[item.cartId]}
                  />
                ))}
              </div>
            )}
          </div>

          {cartItems.length > 0 && (
            <div className="cart-footer">
              <div className="cart-total">
                <span>總計：</span>
                <span id="cartTotalAmount" className="total-amount">
                  HK$ {formatPrice(totalAmount)}
                </span>
              </div>
              <button onClick={handleCheckout} className="btn btn-primary checkout-btn">
                <i className="fas fa-credit-card"></i> 結賬
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// 購物車商品項目組件
const CartItem = ({ item, onUpdateQuantity, onRemove, onToggleSelection, formatPrice, isUpdating }) => {
  const { product, quantity, cartId, price, selected } = item // 解構 selected

  return (
    <div className={`cart-item ${isUpdating ? 'updating' : ''}`}>
      {/*Checkbox */}
      <input
        type="checkbox"
        checked={selected !== false}
        onChange={() => onToggleSelection(cartId, selected)}
        style={{ marginRight: '10px', cursor: 'pointer', transform: 'scale(1.2)', flexShrink: 0 }}
      />

      <div className="cart-item-image">
        <img
          src={`/images/products/${product.image}`}
          alt={product.description}
          onError={(e) => {
            e.target.src = '/images/placeholder.png'
          }}
        />
      </div>

      <div className="cart-item-info">
        <div className="cart-item-name">{product.description}</div>
        <div className="cart-item-price">
          HK$ {formatPrice(price)}
        </div>

        <div className="cart-item-quantity">
          <button
            className="qty-btn"
            onClick={() => onUpdateQuantity(cartId, quantity - 1)}
            disabled={quantity <= 1 || isUpdating}
          >
            <i className="fas fa-minus"></i>
          </button>
          <span className="qty-value">{quantity}</span>
          <button
            className="qty-btn"
            onClick={() => onUpdateQuantity(cartId, quantity + 1)}
            disabled={quantity >= product.stock || isUpdating}
          >
            <i className="fas fa-plus"></i>
          </button>
          <i
            className="fas fa-trash-alt cart-item-remove"
            onClick={() => onRemove(product.productId)}
            title="移除商品"
          ></i>
        </div>

        {quantity >= product.stock && (
          <div className="stock-warning">
            <i className="fas fa-exclamation-triangle"></i>
            庫存不足 (最多 {product.stock})
          </div>
        )}
      </div>
    </div>
  )
}

export default CartDropdown