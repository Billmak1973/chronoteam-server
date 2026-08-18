// @ts-check
/* ==========================================
   ChronoTeam 公共脚本 (common.js) - 完整修復版
   ========================================== */

/* ==========================================
  1. 購物車模塊 (Namespace Refactored)
   ========================================== */

// ===== 第一步：建立唯一的命名空間容器 =====
window.ChronoTeam = window.ChronoTeam || {};
window.ChronoTeam.cart = window.ChronoTeam.cart || {
  // ===== 第二步：遷移並封裝狀態變量 =====
  isOpen: false,
  pendingDeleteId: null,
  // 確保 selectedItems 在頁面切換時不丟失
  selectedItems: new Set(),
};

// 內部引用簡寫 (直接使用 Cart，更簡潔)
const Cart = window.ChronoTeam.cart;

// ===== 第三步：將函數掛載到命名空間下 =====

/**
 * 切換購物車下拉菜單的顯示與隱藏
 * @param {Event} [event] - 點擊事件對象 (用於阻止事件冒泡)
 * @returns {void}
 */
Cart.toggleDropdown = function (event) {
  if (event) event.stopPropagation();
  const dropdown = document.getElementById("cartDropdown");
  if (!dropdown) return;

  Cart.isOpen = !Cart.isOpen;
  if (Cart.isOpen) {
    dropdown.style.display = "flex";
    dropdown.style.flexDirection = "column";
    Cart.loadItems();
  } else {
    dropdown.style.display = "none";
  }
};

/**
 * 遍歷 DOM 中勾選的商品，計算總價並更新底部金額顯示
 * @returns {void}
 */
Cart.calculateSelectedTotal = function () {
  const checkboxes = document.querySelectorAll(".cart-item-select:checked");
  let total = 0;

  checkboxes.forEach((checkbox) => {
    const cartItem = checkbox.closest(".cart-item");
    if (cartItem) {
      const priceText = cartItem
        .querySelector(".cart-item-price")
        .textContent.replace("HK$ ", "")
        .replace(/,/g, "");
      const quantity = parseInt(
        cartItem.querySelector(".qty-value").textContent,
      );
      total += parseFloat(priceText) * quantity;
    }
  });

  const totalEl = document.getElementById("cartTotalAmount");
  if (totalEl) totalEl.textContent = "HK$ " + formatPrice(total);
};

/**
 * 異步請求後端 API 獲取購物車數據，並觸發渲染與角標更新
 * @returns {Promise<void>}
 */
Cart.loadItems = async function () {
  try {
    const response = await fetch("/cart/api/list");
    const data = await response.json();

    if (data.success) {
      Cart.renderItems(data.cartItems, data.totalAmount);
      updateCartBadge(data.cartCount);

      // 替換內部引用 -> Cart.selectedItems
      Cart.selectedItems.clear();
      data.cartItems.forEach((item) => {
        if (item.selected !== false) {
          Cart.selectedItems.add(item.cartId);
        }
      });

      Cart.calculateSelectedTotal();
      return Promise.resolve();
    }
  } catch (error) {
    console.error("加载购物车失败:", error);
    return Promise.reject(error);
  }
};

/**
 * 根據後端返回的數據生成購物車列表 DOM
 * @param {Array<Object>} cartItems - 購物車商品數據數組
 * @param {number} totalAmount - 選中商品的總金額
 * @returns {void}
 */
Cart.renderItems = function (cartItems, totalAmount) {
  const container = document.getElementById("cartItemsContainer");

  // ️ 終極防禦：防止覆蓋詳情頁 DOM
  if (!container || !container.closest(".cart-dropdown-menu")) {
    console.warn("🛡️ [安全攔截] 阻止了 renderCartItems 覆蓋詳情頁 DOM！");
    return;
  }

  if (!container) return;

  Cart.selectedItems.clear();

  if (!cartItems || cartItems.length === 0) {
    container.innerHTML = `<div class="cart-empty"><i class="fas fa-shopping-basket" style="font-size: 3rem; color: #ddd; margin-bottom: 1rem;"></i><p>購物車是空的</p></div>`;
    const totalEl = document.getElementById("cartTotalAmount");
    if (totalEl) totalEl.textContent = "HK$ 0";
    return;
  }

  let html = "";
  cartItems.forEach((item) => {
    const isChecked = item.selected !== false;
    if (isChecked) {
      Cart.selectedItems.add(item.cartId);
    }

    html += `<div class="cart-item" data-cart-id="${item.cartId}" data-product-id="${item.product.productId}">
            <div class="cart-item-checkbox">
                <input type="checkbox"
                       class="cart-item-select"
                       data-cart-id="${item.cartId}"
                       onchange="Cart.toggleSelection(${item.cartId}, this.checked)"
                       ${isChecked ? "checked" : ""}>
            </div>
            <div class="cart-item-img"><img src="/images/products/${item.product.image}" alt="${item.product.description}" onerror="this.src='/images/placeholder.png'"></div>
            <div class="cart-item-info">
                <div class="cart-item-name">${item.product.description}</div>
                <div class="cart-item-price">HK$ ${formatPrice(item.price)}</div>
                <div class="cart-item-quantity">
                    <button class="qty-btn" onclick="Cart.updateQuantity(${item.cartId}, ${item.quantity - 1})" ${item.quantity <= 1 ? "disabled" : ""}>-</button>
                    <span class="qty-value">${item.quantity}</span>
                    <button class="qty-btn" onclick="Cart.updateQuantity(${item.cartId}, ${item.quantity + 1})" ${item.quantity >= item.product.stock ? "disabled" : ""}>+</button>
                </div>
            </div>
            <div class="cart-item-remove" onclick="Cart.removeFromCart(${item.product.productId})"><i class="fas fa-trash-alt"></i></div>
        </div>`;
  });
  container.innerHTML = html;

  const totalEl = document.getElementById("cartTotalAmount");
  if (totalEl) totalEl.textContent = "HK$ " + formatPrice(totalAmount);
};

/**
 * 切換單個購物車項的選中狀態，並同步後端與詳情頁 UI
 * @param {number} cartId - 購物車記錄 ID
 * @param {boolean} isChecked - 當前是否被勾選
 * @returns {Promise<void>}
 */
Cart.toggleSelection = async function (cartId, isChecked) {
  // 替換內部引用
  if (isChecked) {
    Cart.selectedItems.add(cartId);
  } else {
    Cart.selectedItems.delete(cartId);
  }

  try {
    await fetch(
      `/cart/api/toggle-selection/${cartId}?isSelected=${isChecked}`,
      { method: "PUT" },
    );

    // 跨頁面同步邏輯保持不變
    if (window.location.pathname === "/cart/view") {
      const detailCheckbox = document.querySelector(
        `.cart-items-list input[data-cart-id="${cartId}"]`,
      );
      if (detailCheckbox) {
        detailCheckbox.checked = isChecked;
        const cartItem = detailCheckbox.closest(".cart-item");
        if (isChecked) {
          cartItem.classList.remove("not-selected");
        } else {
          cartItem.classList.add("not-selected");
        }
        if (typeof window.updateCartTotal === "function")
          window.updateCartTotal();
        if (typeof window.updateSelectedCount === "function")
          window.updateSelectedCount();
        if (typeof window.syncSelectAllCheckbox === "function")
          window.syncSelectAllCheckbox();
      }
    }
  } catch (error) {
    console.error("同步选中状态失败:", error);
    // 【失敗回滾】
    if (isChecked) {
      Cart.selectedItems.delete(cartId);
    } else {
      Cart.selectedItems.add(cartId);
    }
    // 詳情頁 UI 回滾...
    if (window.location.pathname === "/cart/view") {
      const detailCheckbox = document.querySelector(
        `.cart-items-list input[data-cart-id="${cartId}"]`,
      );
      if (detailCheckbox) {
        detailCheckbox.checked = !isChecked;
        const cartItem = detailCheckbox.closest(".cart-item");
        if (isChecked) cartItem.classList.add("not-selected");
        else cartItem.classList.remove("not-selected");
        if (typeof window.updateCartTotal === "function")
          window.updateCartTotal();
        if (typeof window.updateSelectedCount === "function")
          window.updateSelectedCount();
        if (typeof window.syncSelectAllCheckbox === "function")
          window.syncSelectAllCheckbox();
      }
    }
    showNotification("❌ 網絡錯誤，同步失敗", true);
    return;
  }

  Cart.calculateSelectedTotal();
};

/**
 * 更新購物車商品數量 (包含樂觀更新 UI 與失敗回滾機制)
 * @param {number} cartId - 購物車記錄 ID
 * @param {number} newQuantity - 目標數量
 * @returns {Promise<void>}
 */
Cart.updateQuantity = async function (cartId, newQuantity) {
  if (newQuantity <= 0) return;

  const navQtyElement = document.querySelector(
    `#cartItemsContainer [data-cart-id="${cartId}"] .qty-value`,
  );
  const originalNavQty = navQtyElement
    ? parseInt(navQtyElement.textContent)
    : newQuantity;
  const detailQtyElement = document.querySelector(
    `.cart-items-list [data-cart-id="${cartId}"] .qty-value`,
  );
  const originalDetailQty = detailQtyElement
    ? parseInt(detailQtyElement.textContent)
    : newQuantity;

  // 樂觀更新 UI
  if (navQtyElement) navQtyElement.textContent = newQuantity;
  if (detailQtyElement) detailQtyElement.textContent = newQuantity;

  if (detailQtyElement) {
    const detailCartItem = detailQtyElement.closest(".cart-item");
    const price = parseFloat(detailCartItem.dataset.price);
    const subtotalSpan = detailCartItem.querySelector(".item-subtotal span");
    if (subtotalSpan)
      subtotalSpan.textContent = (price * newQuantity).toLocaleString("zh-HK");
    if (typeof window.updateCartTotal === "function") window.updateCartTotal();
    if (typeof window.updateSelectedCount === "function")
      window.updateSelectedCount();
  }

  try {
    const response = await fetch(
      `/cart/api/update/${cartId}?quantity=${newQuantity}`,
      { method: "PUT" },
    );
    const data = await response.json();

    if (response.ok) {
      if (typeof Cart.loadItems === "function") await Cart.loadItems();
      showNotification("✅ 數量更新成功");
    } else {
      // 回滾
      if (navQtyElement) navQtyElement.textContent = originalNavQty;
      if (detailQtyElement) detailQtyElement.textContent = originalDetailQty;
      if (detailQtyElement) {
        const detailCartItem = detailQtyElement.closest(".cart-item");
        const price = parseFloat(detailCartItem.dataset.price);
        const subtotalSpan = detailCartItem.querySelector(
          ".item-subtotal span",
        );
        if (subtotalSpan)
          subtotalSpan.textContent = (price * originalDetailQty).toLocaleString(
            "zh-HK",
          );
        if (typeof window.updateCartTotal === "function")
          window.updateCartTotal();
        if (typeof window.updateSelectedCount === "function")
          window.updateSelectedCount();
      }
      showNotification("❌ " + (data.message || "更新失敗"), true);
    }
  } catch (error) {
    console.error("更新購物車失敗:", error);
    // 網絡錯誤回滾...
    if (navQtyElement) navQtyElement.textContent = originalNavQty;
    if (detailQtyElement) detailQtyElement.textContent = originalDetailQty;
    if (detailQtyElement) {
      const detailCartItem = detailQtyElement.closest(".cart-item");
      const price = parseFloat(detailCartItem.dataset.price);
      const subtotalSpan = detailCartItem.querySelector(".item-subtotal span");
      if (subtotalSpan)
        subtotalSpan.textContent = (price * originalDetailQty).toLocaleString(
          "zh-HK",
        );
      if (typeof window.updateCartTotal === "function")
        window.updateCartTotal();
      if (typeof window.updateSelectedCount === "function")
        window.updateSelectedCount();
    }
    showNotification("❌ 網絡錯誤，同步失敗", true);
  }
};

/**
 * 攔截刪除操作，打開確認彈窗並暫存待刪除的商品 ID
 * @param {number} productId - 準備移除的商品 ID
 * @returns {void}
 */
Cart.removeFromCart = function (productId) {
  Cart.pendingDeleteId = productId;
  document.getElementById("cartDeleteModal").style.display = "flex";
  document.body.style.overflow = "hidden";
};

/**
 * 關閉刪除確認彈窗，並清空暫存的 pendingDeleteId
 * @returns {void}
 */
Cart.closeDeleteModal = function () {
  document.getElementById("cartDeleteModal").style.display = "none";
  document.body.style.overflow = "";
  Cart.pendingDeleteId = null;
};

/**
 * 確認執行刪除操作，調用 API 並處理列表移除動畫與狀態重算
 * @returns {Promise<void>}
 */
Cart.confirmDelete = async function () {
  if (!Cart.pendingDeleteId) return;

  const detailCartItem = document.querySelector(
    `.cart-items-list [data-product-id="${Cart.pendingDeleteId}"]`,
  );

  try {
    const response = await fetch(`/cart/api/remove/${Cart.pendingDeleteId}`, {
      method: "DELETE",
    });

    if (response.ok) {
      Cart.closeDeleteModal();
      showNotification("✅ 商品已移除");

      if (typeof Cart.loadItems === "function") await Cart.loadItems();

      if (window.location.pathname === "/cart/view" && detailCartItem) {
        detailCartItem.style.transition = "all 0.3s ease";
        detailCartItem.style.opacity = "0";
        detailCartItem.style.transform = "translateX(-20px)";

        setTimeout(() => {
          detailCartItem.remove();
          const remainingItems = document.querySelectorAll(
            ".cart-items-list .cart-item",
          );
          if (remainingItems.length === 0) {
            location.reload();
          } else {
            if (typeof window.updateCartTotal === "function")
              window.updateCartTotal();
            if (typeof window.updateSelectedCount === "function")
              window.updateSelectedCount();
            if (typeof window.syncSelectAllCheckbox === "function")
              window.syncSelectAllCheckbox();
          }
        }, 300);
      }
    } else {
      const data = await response.json();
      showNotification("❌ " + (data.message || "移除失敗"), true);
    }
  } catch (error) {
    console.error("移除商品失敗:", error);
    showNotification("❌ 網絡錯誤，同步失敗", true);
    Cart.closeDeleteModal();
  }
};

/**
 * 執行結帳流程：校驗登入與選中狀態，創建訂單並跳轉至結帳頁
 * @returns {Promise<void>}
 */
Cart.checkout = async function () {
  if (!isLoggedIn) {
    showNotification("❌ 請先登入！", true);
    return;
  }

  // 替換內部引用
  if (Cart.selectedItems.size === 0) {
    showNotification("❌ 請至少選擇一件商品！", true);
    return;
  }

  const btn = document.querySelector(".checkout-btn");
  const originalText = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 處理中...';

  try {
    const response = await fetch("/checkout/api/create", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        selectedCartIds: Array.from(Cart.selectedItems),
      }),
    });
    const result = await response.json();

    if (response.ok && result.success) {
      window.location.href = `/checkout?orderNo=${result.data}`;
    } else {
      showNotification("❌ " + (result.message || "創建訂單失敗"), true);
      btn.disabled = false;
      btn.innerHTML = originalText;
    }
  } catch (error) {
    console.error("結賬錯誤:", error);
    showNotification("❌ 網絡錯誤", true);
    btn.disabled = false;
    btn.innerHTML = originalText;
  }
};

// ===== 事件綁定 (使用命名空間內的函數) =====
document.addEventListener("click", function (e) {
  const cartDropdown = document.getElementById("cartDropdown");
  if (cartDropdown && !cartDropdown.contains(e.target)) {
    cartDropdown.style.display = "none";
    Cart.isOpen = false;
  }
});

const cartDropdownEl = document.getElementById("cartDropdown");
const cartNavContainer = cartDropdownEl
  ? cartDropdownEl.closest(".nav-dropdown")
  : null;
if (cartNavContainer) {
  cartNavContainer.addEventListener("mouseleave", function () {
    const dropdown = document.getElementById("cartDropdown");
    if (dropdown) {
      dropdown.style.display = "";
      dropdown.style.flexDirection = "";
      Cart.isOpen = false;
    }
  });
}

document
  .getElementById("cartDeleteModal")
  ?.addEventListener("click", function (e) {
    if (e.target === this) Cart.closeDeleteModal();
  });

document.addEventListener("keydown", function (e) {
  if (e.key === "Escape") {
    const modal = document.getElementById("cartDeleteModal");
    if (modal && modal.style.display === "flex") Cart.closeDeleteModal();
  }
});

/**
 * 格式化價格數字 (自動添加千分位逗號)
 * @param {number} price - 原始價格數值
 * @returns {string} 格式化後的價格字符串 (例如: "12,345")
 */
function formatPrice(price) {
  return new Intl.NumberFormat("zh-HK", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(price);
}

/**
 * 將指定商品加入購物車 (帶有登入狀態攔截)
 * @param {number} productId - 商品 ID
 * @returns {Promise<void>}
 */
async function addToCart(productId) {
  if (typeof isLoggedIn === "undefined" || !isLoggedIn) {
    showNotification("❌ 請先登入後再加入購物車！", true);
    return;
  }
  try {
    const response = await fetch(`/cart/api/add/${productId}`, {
      method: "POST",
    });
    const data = await response.json();
    if (response.ok) {
      showNotification("✅ 已加入購物車");
      Cart.loadItems();
    } else {
      showNotification("❌ " + (data.message || "加入失敗"), true);
    }
  } catch (error) {
    console.error("加入购物车失败:", error);
    showNotification("❌ 网络错误", true);
  }
}

/**
 * 在頁面中央顯示 Toast 提示通知 (自動消失)
 * @param {string} message - 提示文字內容
 * @param {boolean} [isError=false] - 是否為錯誤提示 (true 為紅色背景，false 為金色背景)
 * @returns {void}
 */
function showNotification(message, isError = false) {
  const notification = document.createElement("div");
  notification.style.cssText = `
        position: fixed; top: 50%; left: 50%; transform: translate(-50%, -60%);
        background: ${isError ? "#dc3545" : "var(--gold)"};
        color: ${isError ? "white" : "var(--primary)"};
        padding: 1.2rem 2.5rem; border-radius: 12px;
        box-shadow: 0 10px 40px rgba(0,0,0,0.25); z-index: 99999;
        opacity: 0; font-weight: 600; font-size: 1.1rem;
        display: flex; align-items: center; gap: 0.8rem;
        transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    `;
  notification.innerHTML = `<span>${message}</span>`;
  document.body.appendChild(notification);
  setTimeout(() => {
    notification.style.opacity = "1";
    notification.style.transform = "translate(-50%, -50%)";
  }, 10);
  setTimeout(() => {
    notification.style.opacity = "0";
    notification.style.transform = "translate(-50%, -70%)";
    setTimeout(() => notification.remove(), 400);
  }, 1500);
}

/**
 * 更新導航欄購物車圖標右上角的數量角標 (Badge)
 * @param {number} count - 購物車內的商品總件數
 * @returns {void}
 */
function updateCartBadge(count) {
  const badge = document.getElementById("cartCountBadge");
  if (!badge) return;

  if (count > 0) {
    badge.textContent = count;
    badge.style.display = "inline-flex";
  } else {
    badge.style.display = "none";
  }
}
// ===== 登录/注册弹窗控制 =====
/* ==========================================
  2. 認證與權限模塊 (Namespace Refactored)
   ========================================== */

// ===== 第一步：建立命名空間 =====
window.ChronoTeam.auth = window.ChronoTeam.auth || {};
/**
 * @namespace Auth
 * @description 處理用戶身份驗證相關的 UI 控制與 API 互動 (登入、註冊、登出、權限攔截)。
 */
const Auth = window.ChronoTeam.auth;

// ===== 第二步：UI 控制函數 =====

/**
 * 打開註冊彈窗，重置表單並清空提示訊息，同時鎖定背景滾動。
 * @returns {void}
 */
Auth.openRegisterModal = function () {
    const modal = document.getElementById("registerModal");
    if (!modal) return;
    modal.style.display = "flex";
    const msg = document.getElementById("registerMsg");
    if (msg) msg.textContent = "";
    const form = document.getElementById("registerForm");
    if (form) form.reset();
    document.body.style.overflow = "hidden";
};

/**
 * 關閉註冊彈窗並恢復背景滾動。
 * @returns {void}
 */
Auth.closeRegisterModal = function () {
    const modal = document.getElementById("registerModal");
    if (!modal) return;
    modal.style.display = "none";
    document.body.style.overflow = "";
};

/**
 * 打開登入彈窗，重置表單並清空提示訊息，同時鎖定背景滾動。
 * @returns {void}
 */
Auth.openLoginModal = function () {
    const modal = document.getElementById("loginModal");
    if (!modal) return;
    modal.style.display = "flex";
    const msg = document.getElementById("loginMsg");
    if (msg) msg.textContent = "";
    const form = document.getElementById("loginForm");
    if (form) form.reset();
    document.body.style.overflow = "hidden";
};

/**
 * 關閉登入彈窗並恢復背景滾動。
 * @returns {void}
 */
Auth.closeLoginModal = function () {
    const modal = document.getElementById("loginModal");
    if (!modal) return;
    modal.style.display = "none";
    document.body.style.overflow = "";
};

/**
 * 從登入彈窗切換到註冊彈窗（帶有 200ms 的延遲過渡動畫）。
 * @returns {void}
 */
Auth.switchToRegister = function () {
    Auth.closeLoginModal();
    setTimeout(Auth.openRegisterModal, 200);
};

/**
 * 從註冊彈窗切換到登入彈窗（帶有 200ms 的延遲過渡動畫）。
 * @returns {void}
 */
Auth.switchToLogin = function () {
    Auth.closeRegisterModal();
    setTimeout(Auth.openLoginModal, 200);
};

/**
 * 切換密碼輸入框的可見性（明文/密文），並同步切換 FontAwesome 圖標。
 * @param {string} inputId - 密碼輸入框的 DOM ID (例如: 'regPassword', 'loginPassword')。
 * @param {HTMLElement} icon - 觸發切換的圖標元素 (需包含 fa-eye / fa-eye-slash 類名)。
 * @returns {void}
 */
Auth.togglePassword = function (inputId, icon) {
    const input = document.getElementById(inputId);
    if (!input) return;
    if (input.type === "password") {
        input.type = "text";
        icon.classList.replace("fa-eye", "fa-eye-slash");
    } else {
        input.type = "password";
        icon.classList.replace("fa-eye-slash", "fa-eye");
    }
};

// ===== 第三步：業務邏輯函數 =====

/**
 * 處理註冊表單的異步提交邏輯。
 * 包含前端密碼一致性校驗、API 請求、成功後的自動跳轉登入以及錯誤處理。
 * @async
 * @param {SubmitEvent} e - 表單提交事件對象。
 * @returns {Promise<void>}
 */
Auth.handleRegister = async function (e) {
    e.preventDefault();
    const form = e.target;
    const msg = document.getElementById("registerMsg");
    const btn = form.querySelector('button[type="submit"]');

    if (form.password.value !== form.confirmPassword.value) {
        msg.textContent = "❌ 兩次密碼輸入不一致";
        msg.style.color = "var(--accent)";
        form.confirmPassword.focus();
        return;
    }

    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 註冊中...';
    msg.textContent = "";

    try {
        const formData = {
            username: form.username.value.trim(),
            name: form.name.value.trim(),
            email: form.email.value.trim(),
            password: form.password.value,
            phone: form.phone.value.trim(),
            address: form.address ? form.address.value.trim() : "",
        };

        const response = await fetch("/api/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(formData),
        });
        const result = await response.json();

        if (response.ok) {
            msg.textContent = "✅ 註冊成功！3秒後自動登入...";
            msg.style.color = "green";
            setTimeout(() => {
                Auth.closeRegisterModal();
                Auth.openLoginModal();
                const loginForm = document.getElementById("loginForm");
                if (loginForm) {
                    loginForm.username.value = formData.username;
                    loginForm.password.focus();
                }
            }, 2500);
        } else {
            msg.textContent = "❌ " + (result.message || "註冊失敗，請重試");
            msg.style.color = "var(--accent)";
        }
    } catch (error) {
        msg.textContent = "❌ 網絡錯誤，請檢查連接";
        msg.style.color = "var(--accent)";
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-user-check"></i> 立即註冊';
    }
};

/**
 * 處理登入表單的異步提交邏輯。
 * 包含 API 請求、成功後的頁面刷新、針對用戶名不存在或密碼錯誤的特定 UI 反饋 (shake 動畫)。
 * @async
 * @param {SubmitEvent} e - 表單提交事件對象。
 * @returns {Promise<void>}
 */
Auth.handleLogin = async function (e) {
    e.preventDefault();
    const form = e.target;
    const msg = document.getElementById("loginMsg");
    const btn = form.querySelector('button[type="submit"]');

    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 登入中...';
    msg.textContent = "";
    msg.style.color = "var(--accent)";

    try {
        const response = await fetch("/api/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                username: form.username.value.trim(),
                password: form.password.value,
            }),
        });
        const result = await response.json();

        if (response.ok) {
            msg.textContent = "✅ 登入成功！正在刷新...";
            msg.style.color = "green";
            setTimeout(() => {
                Auth.closeLoginModal();
                window.location.reload();
            }, 1200);
        } else {
            let errorMsg = result.message || "用戶名或密碼錯誤";

            if (errorMsg.startsWith("USER_NOT_FOUND:")) {
                errorMsg = "❌ 該用戶名不存在";
                form.querySelector('input[name="username"]').classList.add("shake");
                setTimeout(
                    () =>
                        form
                            .querySelector('input[name="username"]')
                            .classList.remove("shake"),
                    500,
                );
            } else if (errorMsg.startsWith("INVALID_PASSWORD:")) {
                errorMsg = "❌ 輸入密碼不正確";
                form.querySelector('input[name="password"]').classList.add("shake");
                setTimeout(
                    () =>
                        form
                            .querySelector('input[name="password"]')
                            .classList.remove("shake"),
                    500,
                );
            } else {
                errorMsg = "❌ " + errorMsg;
            }
            msg.textContent = errorMsg;
        }
    } catch (error) {
        msg.textContent = "❌ 網絡錯誤，請檢查連接";
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-sign-in-alt"></i> 立即登入';
    }
};

// ===== 第四步：登出與權限攔截 =====

/**
 * 顯示自定義的登出確認彈窗，並鎖定背景滾動。
 * @returns {void}
 */
Auth.showLogoutModal = function () {
    const modal = document.getElementById("logoutModal");
    if (modal) {
        modal.style.display = "flex";
        document.body.style.overflow = "hidden";
    }
};

/**
 * 關閉登出確認彈窗，並恢復背景滾動。
 * @returns {void}
 */
Auth.closeLogoutModal = function () {
    const modal = document.getElementById("logoutModal");
    if (modal) {
        modal.style.display = "none";
        document.body.style.overflow = "";
    }
};

/**
 * 執行實際的登出 API 請求，成功或失敗後均強制重定向至首頁 ("/")。
 * @async
 * @returns {Promise<void>}
 */
Auth.performLogout = async function () {
    Auth.closeLogoutModal();
    try {
        await fetch("/logout", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
        });
        window.location.href = "/";
    } catch (error) {
        console.error("登出失败:", error);
        window.location.href = "/";
    }
};

/**
 * 頁面跳轉前的登入狀態攔截器。
 * 若用戶未登入，則彈出提示並喚起登入彈窗；若已登入，則直接跳轉至目標 URL。
 *
 * ⚠️ 依賴全局變量 `isLoggedIn` (由 Thymeleaf 後端注入)
 * ⚠️ 依賴全局函數 `showNotification(message, isError)`
 *
 * @param {string} url - 嘗試跳轉的目標 URL。
 * @param {string} [message="❌ 請先登入！"] - 未登入時顯示的提示消息。
 * @returns {boolean} 若已登入並執行跳轉返回 `true`；若未登入被攔截返回 `false`。
 */
Auth.handleLogout = function () {
    Auth.showLogoutModal();
};

/**
 * 頁面跳轉登入攔截
 * @param {string} url - 目標 URL
 * @param {string} message - 提示消息
 */
Auth.requireLogin = function (url, message) {
    // 注意：這裡依賴全局變量 isLoggedIn，它由 Thymeleaf 注入
    if (typeof isLoggedIn === "undefined" || !isLoggedIn) {
        showNotification(message || "❌ 請先登入！", true);
        setTimeout(() => {
            Auth.openLoginModal();
        }, 500);
        return false;
    }
    window.location.href = url;
    return true;
};

// ===== 第五步：事件綁定與初始化 =====

document.addEventListener("DOMContentLoaded", function () {
    const registerModal = document.getElementById("registerModal");
    const loginModal = document.getElementById("loginModal");

    // 點擊遮罩層關閉登入彈窗
    if (loginModal) {
        loginModal.addEventListener("click", function (e) {
            if (e.target === this) Auth.closeLoginModal();
        });
    }

    // 點擊遮罩層關閉註冊彈窗 (補充原有邏輯缺失的部分)
    if (registerModal) {
        registerModal.addEventListener("click", function (e) {
            if (e.target === this) Auth.closeRegisterModal();
        });
    }

    // 購物車初始化 (保持原有邏輯，但在 Auth 模塊加載後執行更安全)
    if (
        document.querySelector(".nav-dropdown") &&
        typeof Cart !== "undefined" &&
        typeof Cart.loadItems === "function"
    ) {
        Cart.loadItems();
    }
});

// ESC 鍵關閉彈窗 (整合 Auth 相關)
document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") {
        Auth.closeRegisterModal();
        Auth.closeLoginModal();
        Auth.closeLogoutModal(); // 新增：ESC 關閉登出彈窗
    }
});

// 點擊遮罩層關閉登出彈窗
document.addEventListener("click", function (e) {
    const logoutModal = document.getElementById("logoutModal");
    if (logoutModal && e.target === logoutModal) {
        Auth.closeLogoutModal();
    }
});

/* ==========================================
  3. 通知系統模塊 (Namespace Refactored)
   ========================================== */

// ===== 第一步：建立命名空間 =====
window.ChronoTeam.notification = window.ChronoTeam.notification || {};
const NotificationSys = window.ChronoTeam.notification;

/**
 * 獲取未讀通知數量並更新導航欄 UI (紅點與智能跳轉連結)
 * @async
 * @returns {Promise<void>}
 */
NotificationSys.fetchUnreadCounts = async function () {
  try {
    const response = await fetch("/api/notifications/unread-count");
    const data = await response.json();

    // 1. 更新消息通知紅點
    const msgBadge = document.getElementById("notificationBadge");
    if (msgBadge) {
      if (data.messageCount > 0) {
        msgBadge.textContent = data.messageCount > 99 ? "99+" : data.messageCount;
        msgBadge.style.display = "inline-flex";
      } else {
        msgBadge.style.display = "none";
      }
    }

    // 2. 更新系統通知紅點
    const sysBadge = document.getElementById("systemNotificationBadge");
    if (sysBadge) {
      if (data.systemCount > 0) {
        sysBadge.textContent = data.systemCount > 99 ? "99+" : data.systemCount;
        sysBadge.style.display = "inline-flex";
      } else {
        sysBadge.style.display = "none";
      }
    }

    // ==========================================
    // 【核心功能】：智能跳轉邏輯
    // ==========================================
    // 優先級規則：回復 > @提及 > 點贊
    let targetTab = 'MY'; // 默認

    // 注意：後端返回的字段名需與 NotificationController 一致
    // data.replyCount, data.mentionCount, data.likeCount (這是別人點贊我)
    if (data.replyCount > 0) {
      targetTab = 'REPLY';
    } else if (data.mentionCount > 0) {
      targetTab = 'MENTION';
    } else if (data.likeCount > 0) {
      targetTab = 'LIKED_ME';
    }

    // 找到導航欄的消息鏈接並更新 href
    // 假設你的 navbar 中該鏈接沒有特定 ID，我們通過文本內容或 href 查找
    // 推薦在 HTML 中給該 <a> 標籤加上 id="nav-msg-link" 以提高性能
    const navLinks = document.querySelectorAll('a[href^="/account/reviews"]');

    navLinks.forEach(link => {
      // 只有當有未讀消息時，才強制跳轉到特定 Tab
      // 如果沒有未讀消息，保持默認 (MY) 或原樣
      if (data.messageCount > 0) {
        // 構建新 URL，保留可能的其他參數 (這裡簡單處理，直接覆蓋)
        // 如果原鏈接有其他參數，可以使用 URL API 處理，這裡為了簡單直接拼接
        link.href = `/account/reviews?type=${targetTab}`;
      } else {
        // 如果沒有未讀消息，重置為默認頁面 (不帶 type 參數，後端默認 MY)
        link.href = `/account/reviews`;
      }
    });

  } catch (error) {
    console.error("獲取通知失敗:", error);
  }
};

// ===== 第二步：全局橋接 (保持向後兼容) =====
// 其他頁面 (如 React 組件或內聯 JS) 可能依賴 window.refreshNotificationBadge
//window.refreshNotificationBadge = NotificationSys.fetchUnreadCounts;

// ===== 第三步：初始化與事件綁定 =====
document.addEventListener("DOMContentLoaded", function () {
  // 僅在用戶已登入時獲取通知
  if (typeof isLoggedIn !== "undefined" && isLoggedIn) {
    NotificationSys.fetchUnreadCounts();
  }
});

// ==========================================
// 4.註冊表單 - 區域級聯選擇與自動填充邏輯 (完整修復版)
// ==========================================
document.addEventListener("DOMContentLoaded", function () {
  const regionSelect = document.getElementById("regionSelect");
  const districtSelect = document.getElementById("districtSelect");
  const addressInput = document.getElementById("regAddress"); //  修正 ID，與 HTML 中的 id="regAddress" 保持一致

  // 核心修復 1：安全護欄！
  // 因為 common.js 是全局加載的，在首頁、商品頁等沒有註冊彈窗的頁面，這三個元素是 null。
  // 如果不加判斷直接 addEventListener，會導致 JS 報錯崩潰，阻斷後續所有功能！
  if (!regionSelect || !districtSelect || !addressInput) return;

  // 定义区域和行政区的映射关系
  const districtData = {
    港岛: ["中西区", "湾仔区", "东区", "南区"],
    九龙: ["油尖旺区", "深水埗区", "九龙城区", "黄大仙区", "观塘区"],
    新界: [
      "荃湾区",
      "屯门区",
      "元朗区",
      "北区",
      "大埔区",
      "西贡区",
      "沙田区",
      "葵青区",
      "离岛区",
    ],
  };

  // 1. 监听第一级下拉框变化
  regionSelect.addEventListener("change", function () {
    const selectedRegion = this.value;

    // 清空并重置第二级下拉框
    districtSelect.innerHTML = '<option value="">请选择行政区</option>';
    districtSelect.disabled = true;
    districtSelect.style.backgroundColor = "#f8f9fa"; // 恢復禁用時的灰色背景

    if (selectedRegion && districtData[selectedRegion]) {
      districtSelect.disabled = false;
      districtSelect.style.backgroundColor = "white"; // 啟用時變為白色

      // 填充第二级选项
      districtData[selectedRegion].forEach((district) => {
        const option = document.createElement("option");
        option.value = district;
        option.textContent = district;
        districtSelect.appendChild(option);
      });
    }
  });

  // 2. 监听第二级下拉框变化 -> 自动填入地址栏
  districtSelect.addEventListener("change", function () {
    const region = regionSelect.value;
    const district = this.value;

    if (region && district) {
      // 核心逻辑：将区域和行政区填入地址框，并加一个空格，聚焦光标让用户继续输入
      if (!addressInput.value.startsWith(region)) {
        addressInput.value = region + " " + district + " ";
      } else {
        if (addressInput.value.trim() === "") {
          addressInput.value = region + " " + district + " ";
        }
      }

      // 自动聚焦到地址框，方便用户接着输入街道
      addressInput.focus();
      addressInput.classList.remove("is-invalid");
    }
  });

  //  核心修復 2：必須指定 registerForm！
  // 原本使用 document.querySelector('form') 會抓到頁面上的第一個表單（通常是登入表單），導致驗證綁定錯誤。
  const registerForm = document.getElementById("registerForm");
  if (registerForm) {
    registerForm.addEventListener("submit", function (e) {
      const region = regionSelect.value;
      const address = addressInput.value.trim();

      // 如果选了区域，但地址栏被删空了，或者地址栏里没有包含选中的区域
      if (region && (!address || !address.includes(region))) {
        e.preventDefault(); // 阻止提交
        //  核心修復 3：使用項目統一的 showNotification 代替醜陋的 alert
        showNotification(
            "❌ 既然選擇了區域，詳細地址不能為空，且必須包含所選區域！",
            true,
        );
        addressInput.focus();
        addressInput.style.borderColor = "#e94560";
      }
    });
  }
});
