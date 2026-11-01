// ==========================================
// 1. 全局類型宣告 (讓 TS 認識 Thymeleaf 注入的變量與全局函數)
// ==========================================
declare global {
    interface Window {
        isLoggedIn: boolean;
        Cart: any;
        Auth: any;
        NotificationSys: any;
        addToCart: (productId: number) => Promise<void>;
        showNotification: (message: string, isError?: boolean) => void;
        updateCartBadge: (count: number) => void;
        formatPrice: (price: number) => string;
        updateCartTotal?: () => void;
        updateSelectedCount?: () => void;
        syncSelectAllCheckbox?: () => void;
    }
}

export {}; // 確保此文件被視為模塊

// ==========================================
// 2. Zustand Stores 定義 (內聯，避免模塊導入報錯)
// ==========================================
import { create } from 'zustand';

interface CartState {
    isOpen: boolean;
    pendingDeleteId: number | null;
    selectedItems: Set<number>;
    setIsOpen: (isOpen: boolean) => void;
    setPendingDeleteId: (id: number | null) => void;
    toggleSelection: (cartId: number, isChecked: boolean) => void;
}

export const useCartStore = create<CartState>((set, get) => ({
    isOpen: false,
    pendingDeleteId: null,
    selectedItems: new Set<number>(),
    setIsOpen: (isOpen) => set({ isOpen }),
    setPendingDeleteId: (id) => set({ pendingDeleteId: id }),
    toggleSelection: (cartId, isChecked) => {
        const selectedItems = new Set(get().selectedItems);
        if (isChecked) selectedItems.add(cartId);
        else selectedItems.delete(cartId);
        set({ selectedItems });
    },
}));

interface AuthState {
    isLoginOpen: boolean;
    isRegisterOpen: boolean;
    isLogoutOpen: boolean;
    setLoginOpen: (open: boolean) => void;
    setRegisterOpen: (open: boolean) => void;
    setLogoutOpen: (open: boolean) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
    isLoginOpen: false,
    isRegisterOpen: false,
    isLogoutOpen: false,
    setLoginOpen: (open) => set({ isLoginOpen: open }),
    setRegisterOpen: (open) => set({ isRegisterOpen: open }),
    setLogoutOpen: (open) => set({ isLogoutOpen: open }),
}));

// ==========================================
// 3. 模塊接口定義 (解決 TS2304 找不到內部方法的問題)
// ==========================================
interface CartModule {
    toggleDropdown: (event?: Event) => void;
    calculateSelectedTotal: () => void;
    loadItems: () => Promise<void>;
    renderItems: (cartItems: any[], totalAmount: number) => void;
    toggleSelection: (cartId: number, isChecked: boolean) => Promise<void>;
    updateQuantity: (cartId: number, newQuantity: number) => Promise<void>;
    removeFromCart: (productId: number) => void;
    closeDeleteModal: () => void;
    confirmDelete: () => Promise<void>;
    checkout: () => Promise<void>;
}

interface AuthModule {
    openRegisterModal: () => void;
    closeRegisterModal: () => void;
    openLoginModal: () => void;
    closeLoginModal: () => void;
    switchToRegister: () => void;
    switchToLogin: () => void;
    togglePassword: (inputId: string, icon: HTMLElement) => void;
    handleRegister: (e: SubmitEvent) => Promise<void>;
    handleLogin: (e: SubmitEvent) => Promise<void>;
    showLogoutModal: () => void;
    closeLogoutModal: () => void;
    performLogout: () => Promise<void>;
    handleLogout: () => void;
    requireLogin: (url: string, message?: string) => boolean;
}

interface NotificationModule {
    fetchUnreadCounts: () => Promise<void>;
}

// ==========================================
// 4. 全局輔助函數
// ==========================================
window.formatPrice = (price: number): string => {
    return new Intl.NumberFormat('zh-HK', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(price);
};

window.showNotification = (message: string, isError: boolean = false): void => {
    const notification = document.createElement('div');
    notification.style.cssText = `
        position: fixed; top: 50%; left: 50%; transform: translate(-50%, -60%);
        background: ${isError ? '#dc3545' : 'var(--gold)'};
        color: ${isError ? 'white' : 'var(--primary)'};
        padding: 1.2rem 2.5rem; border-radius: 12px;
        box-shadow: 0 10px 40px rgba(0,0,0,0.25); z-index: 99999;
        opacity: 0; font-weight: 600; font-size: 1.1rem;
        display: flex; align-items: center; gap: 0.8rem;
        transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    `;
    notification.innerHTML = `<span>${message}</span>`;
    document.body.appendChild(notification);
    setTimeout(() => {
        notification.style.opacity = '1';
        notification.style.transform = 'translate(-50%, -50%)';
    }, 10);
    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transform = 'translate(-50%, -70%)';
        setTimeout(() => notification.remove(), 400);
    }, 1500);
};

window.updateCartBadge = (count: number): void => {
    const badge = document.getElementById('cartCountBadge');
    if (!badge) return;
    if (count > 0) {
        badge.textContent = count.toString();
        badge.style.display = 'inline-flex';
    } else {
        badge.style.display = 'none';
    }
};

async function addToCart(productId: number) {
    if (typeof window.isLoggedIn === 'undefined' || !window.isLoggedIn) {
        window.showNotification('❌ 請先登入後再加入購物車！', true);
        return;
    }
    try {
        const response = await fetch(`/cart/api/add/${productId}`, { method: 'POST' });
        const data = await response.json();
        if (response.ok) {
            window.showNotification('✅ 已加入購物車');
            Cart.loadItems();
        } else {
            window.showNotification('❌ ' + (data.message || '加入失敗'), true);
        }
    } catch (error) {
        console.error('加入購物車失敗:', error);
        window.showNotification('❌ 網絡錯誤', true);
    }
}
window.addToCart = addToCart;

// ==========================================
// 5. 模塊 1: 購物車模塊
//在原本的 common.js 中，狀態是掛載在 window.ChronoTeam.cart 上的。現在，所有的狀態讀取和修改都通過 Zustand 的 getState() 和 set() 來完成：
// ==========================================
export const Cart: CartModule = {
    toggleDropdown: (event?: Event) => {
        if (event) event.stopPropagation();
        const dropdown = document.getElementById('cartDropdown');
        if (!dropdown) return;

        const isOpen = !useCartStore.getState().isOpen;
        useCartStore.getState().setIsOpen(isOpen);
        if (isOpen) {
            dropdown.style.display = 'flex';
            dropdown.style.flexDirection = 'column';
            Cart.loadItems();
        } else {
            dropdown.style.display = 'none';
        }
    },

    calculateSelectedTotal: () => {
        const checkboxes = document.querySelectorAll('.cart-item-select:checked');
        let total = 0;
        checkboxes.forEach((checkbox) => {
            const cartItem = checkbox.closest('.cart-item');
            if (cartItem) {
                const priceText = cartItem.querySelector('.cart-item-price')?.textContent?.replace('HK$ ', '').replace(/,/g, '') || '0';
                const quantity = parseInt(cartItem.querySelector('.qty-value')?.textContent || '1', 10);
                total += parseFloat(priceText) * quantity;
            }
        });
        const totalEl = document.getElementById('cartTotalAmount');
        if (totalEl) totalEl.textContent = 'HK$ ' + window.formatPrice(total);
    },

    loadItems: async () => {
        try {
            const response = await fetch('/cart/api/list');
            const data = await response.json();
            if (data.success) {
                Cart.renderItems(data.cartItems, data.totalAmount);
                window.updateCartBadge(data.cartCount);
                useCartStore.getState().selectedItems.clear();
                data.cartItems.forEach((item: any) => {
                    if (item.selected !== false) useCartStore.getState().selectedItems.add(item.cartId);
                });
                Cart.calculateSelectedTotal();
            }
        } catch (error) {
            console.error('加載購物車失敗:', error);
        }
    },

    renderItems: (cartItems: any[], totalAmount: number) => {
        const container = document.getElementById('cartItemsContainer');
        if (!container || !container.closest('.cart-dropdown-menu')) {
            console.warn('🛡️ [安全攔截] 阻止了 renderCartItems 覆蓋詳情頁 DOM！');
            return;
        }
        if (!container) return;

        useCartStore.getState().selectedItems.clear();

        if (!cartItems || cartItems.length === 0) {
            container.innerHTML = `<div class="cart-empty"><i class="fas fa-shopping-basket" style="font-size: 3rem; color: #ddd; margin-bottom: 1rem;"></i><p>購物車是空的</p></div>`;
            const totalEl = document.getElementById('cartTotalAmount');
            if (totalEl) totalEl.textContent = 'HK$ 0';
            return;
        }

        let html = '';
        cartItems.forEach((item) => {
            const isChecked = item.selected !== false;
            if (isChecked) useCartStore.getState().selectedItems.add(item.cartId);
            html += `<div class="cart-item" data-cart-id="${item.cartId}" data-product-id="${item.product.productId}">
                <div class="cart-item-checkbox">
                    <input type="checkbox" class="cart-item-select" data-cart-id="${item.cartId}" 
                           onchange="window.Cart.toggleSelection(${item.cartId}, this.checked)" ${isChecked ? 'checked' : ''}>
                </div>
                <div class="cart-item-img"><img src="/images/products/${item.product.image}" alt="${item.product.description}" onerror="this.src='/images/placeholder.png'"></div>
                <div class="cart-item-info">
                    <div class="cart-item-name">${item.product.description}</div>
                    <div class="cart-item-price">HK$ ${window.formatPrice(item.price)}</div>
                    <div class="cart-item-quantity">
                        <button class="qty-btn" onclick="window.Cart.updateQuantity(${item.cartId}, ${item.quantity - 1})" ${item.quantity <= 1 ? 'disabled' : ''}>-</button>
                        <span class="qty-value">${item.quantity}</span>
                        <button class="qty-btn" onclick="window.Cart.updateQuantity(${item.cartId}, ${item.quantity + 1})" ${item.quantity >= item.product.stock ? 'disabled' : ''}>+</button>
                    </div>
                </div>
                <div class="cart-item-remove" onclick="window.Cart.removeFromCart(${item.product.productId})"><i class="fas fa-trash-alt"></i></div>
            </div>`;
        });
        container.innerHTML = html;
        const totalEl = document.getElementById('cartTotalAmount');
        if (totalEl) totalEl.textContent = 'HK$ ' + window.formatPrice(totalAmount);
    },

    //在 toggleSelection (勾選/取消勾選商品) 中，Zustand 的價值體現得淋漓盡致。它讓狀態的修改變得可預測、可追蹤
    toggleSelection: async (cartId: number, isChecked: boolean) => {
        useCartStore.getState().toggleSelection(cartId, isChecked);
        try {
            await fetch(`/cart/api/toggle-selection/${cartId}?isSelected=${isChecked}`, { method: 'PUT' });
            if (window.location.pathname === '/cart/view') {
                const detailCheckbox = document.querySelector(`.cart-items-list input[data-cart-id="${cartId}"]`) as HTMLInputElement;
                if (detailCheckbox) {
                    detailCheckbox.checked = isChecked;
                    const cartItem = detailCheckbox.closest('.cart-item');
                    if (isChecked) cartItem?.classList.remove('not-selected');
                    else cartItem?.classList.add('not-selected');
                    if (typeof window.updateCartTotal === 'function') window.updateCartTotal();
                    if (typeof window.updateSelectedCount === 'function') window.updateSelectedCount();
                    if (typeof window.syncSelectAllCheckbox === 'function') window.syncSelectAllCheckbox();
                }
            }
        } catch (error) {
            console.error('同步選中狀態失敗:', error);
            useCartStore.getState().toggleSelection(cartId, !isChecked);
            if (window.location.pathname === '/cart/view') {
                const detailCheckbox = document.querySelector(`.cart-items-list input[data-cart-id="${cartId}"]`) as HTMLInputElement;
                if (detailCheckbox) {
                    detailCheckbox.checked = !isChecked;
                    const cartItem = detailCheckbox.closest('.cart-item');
                    if (isChecked) cartItem?.classList.add('not-selected');
                    else cartItem?.classList.remove('not-selected');
                    if (typeof window.updateCartTotal === 'function') window.updateCartTotal();
                    if (typeof window.updateSelectedCount === 'function') window.updateSelectedCount();
                    if (typeof window.syncSelectAllCheckbox === 'function') window.syncSelectAllCheckbox();
                }
            }
            window.showNotification('❌ 網絡錯誤，同步失敗', true);
        }
        Cart.calculateSelectedTotal();
    },

    updateQuantity: async (cartId: number, newQuantity: number) => {
        if (newQuantity <= 0) return;

        const navQtyElement = document.querySelector(`#cartItemsContainer [data-cart-id="${cartId}"] .qty-value`) as HTMLElement;
        const originalNavQty = navQtyElement ? parseInt(navQtyElement.textContent || '1', 10) : newQuantity;

        const detailQtyElement = document.querySelector(`.cart-items-list [data-cart-id="${cartId}"] .qty-value`) as HTMLElement;
        const originalDetailQty = detailQtyElement ? parseInt(detailQtyElement.textContent || '1', 10) : newQuantity;

        if (navQtyElement) navQtyElement.textContent = newQuantity.toString();
        if (detailQtyElement) detailQtyElement.textContent = newQuantity.toString();

        if (detailQtyElement) {
            // 【修復處 1】：添加 as HTMLElement | null 類型斷言
            const detailCartItem = detailQtyElement.closest('.cart-item') as HTMLElement | null;
            const price = parseFloat(detailCartItem?.dataset.price || '0');
            const subtotalSpan = detailCartItem?.querySelector('.item-subtotal span');

            if (subtotalSpan) subtotalSpan.textContent = (price * newQuantity).toLocaleString('zh-HK');
            if (typeof window.updateCartTotal === 'function') window.updateCartTotal();
            if (typeof window.updateSelectedCount === 'function') window.updateSelectedCount();
        }

        try {
            const response = await fetch(`/cart/api/update/${cartId}?quantity=${newQuantity}`, { method: 'PUT' });
            const data = await response.json();

            if (response.ok) {
                await Cart.loadItems();
                window.showNotification('✅ 數量更新成功');
            } else {
                // 回滾 UI
                if (navQtyElement) navQtyElement.textContent = originalNavQty.toString();
                if (detailQtyElement) detailQtyElement.textContent = originalDetailQty.toString();

                if (detailQtyElement) {
                    // 【修復處 2】：添加 as HTMLElement | null 類型斷言
                    const detailCartItem = detailQtyElement.closest('.cart-item') as HTMLElement | null;
                    const price = parseFloat(detailCartItem?.dataset.price || '0');
                    const subtotalSpan = detailCartItem?.querySelector('.item-subtotal span');

                    if (subtotalSpan) subtotalSpan.textContent = (price * originalDetailQty).toLocaleString('zh-HK');
                    if (typeof window.updateCartTotal === 'function') window.updateCartTotal();
                    if (typeof window.updateSelectedCount === 'function') window.updateSelectedCount();
                }
                window.showNotification('❌ ' + (data.message || '更新失敗'), true);
            }
        } catch (error) {
            console.error('更新購物車失敗:', error);

            // 網絡錯誤回滾 UI
            if (navQtyElement) navQtyElement.textContent = originalNavQty.toString();
            if (detailQtyElement) detailQtyElement.textContent = originalDetailQty.toString();

            if (detailQtyElement) {
                // 【修復處 3】：添加 as HTMLElement | null 類型斷言
                const detailCartItem = detailQtyElement.closest('.cart-item') as HTMLElement | null;
                const price = parseFloat(detailCartItem?.dataset.price || '0');
                const subtotalSpan = detailCartItem?.querySelector('.item-subtotal span');

                if (subtotalSpan) subtotalSpan.textContent = (price * originalDetailQty).toLocaleString('zh-HK');
                if (typeof window.updateCartTotal === 'function') window.updateCartTotal();
                if (typeof window.updateSelectedCount === 'function') window.updateSelectedCount();
            }
            window.showNotification('❌ 網絡錯誤，同步失敗', true);
        }
    },

    removeFromCart: (productId: number) => {
        useCartStore.getState().setPendingDeleteId(productId);
        const modal = document.getElementById('cartDeleteModal');
        if (modal) {
            modal.style.display = 'flex';
            document.body.style.overflow = 'hidden';
        }
    },

    closeDeleteModal: () => {
        const modal = document.getElementById('cartDeleteModal');
        if (modal) {
            modal.style.display = 'none';
            document.body.style.overflow = '';
        }
        useCartStore.getState().setPendingDeleteId(null);
    },

    confirmDelete: async () => {
        const pendingDeleteId = useCartStore.getState().pendingDeleteId;
        if (!pendingDeleteId) return;

        const detailCartItem = document.querySelector(`.cart-items-list [data-product-id="${pendingDeleteId}"]`);

        try {
            const response = await fetch(`/cart/api/remove/${pendingDeleteId}`, { method: 'DELETE' });
            if (response.ok) {
                Cart.closeDeleteModal();
                window.showNotification('✅ 商品已移除');
                await Cart.loadItems();

                if (window.location.pathname === '/cart/view' && detailCartItem) {
                    (detailCartItem as HTMLElement).style.transition = 'all 0.3s ease';
                    (detailCartItem as HTMLElement).style.opacity = '0';
                    (detailCartItem as HTMLElement).style.transform = 'translateX(-20px)';
                    setTimeout(() => {
                        detailCartItem?.remove();
                        const remainingItems = document.querySelectorAll('.cart-items-list .cart-item');
                        if (remainingItems.length === 0) {
                            location.reload();
                        } else {
                            if (typeof window.updateCartTotal === 'function') window.updateCartTotal();
                            if (typeof window.updateSelectedCount === 'function') window.updateSelectedCount();
                            if (typeof window.syncSelectAllCheckbox === 'function') window.syncSelectAllCheckbox();
                        }
                    }, 300);
                }
            } else {
                const data = await response.json();
                window.showNotification('❌ ' + (data.message || '移除失敗'), true);
            }
        } catch (error) {
            console.error('移除商品失敗:', error);
            window.showNotification('❌ 網絡錯誤，同步失敗', true);
            Cart.closeDeleteModal();
        }
    },

    checkout: async () => {
        if (!window.isLoggedIn) {
            window.showNotification('❌ 請先登入！', true);
            return;
        }
        // 直接從 Store 中讀取選中的商品數量，不需要去遍歷 DOM 節點
        if (useCartStore.getState().selectedItems.size === 0) {
            window.showNotification('❌ 請至少選擇一件商品！', true);
            return;
        }
        try {
            const response = await fetch('/checkout/api/create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ selectedCartIds: Array.from(useCartStore.getState().selectedItems) }),
            });
            const result = await response.json();
            if (response.ok && result.success) {
                window.location.href = `/checkout?orderNo=${result.data}`;
            } else {
                window.showNotification('❌ ' + (result.message || '創建訂單失敗'), true);
            }
        } catch (error) {
            console.error('結賬錯誤:', error);
            window.showNotification('❌ 網絡錯誤', true);
        }
    }
};
window.Cart = Cart;

// ==========================================
// 6. 模塊 2: 認證與權限模塊
// ==========================================
export const Auth: AuthModule = {
    openRegisterModal: () => {
        useAuthStore.getState().setRegisterOpen(true);
        const modal = document.getElementById('registerModal');
        if (modal) {
            modal.style.display = 'flex';
            document.body.style.overflow = 'hidden';
            const form = document.getElementById('registerForm') as HTMLFormElement;
            if (form) form.reset();
        }
    },
    closeRegisterModal: () => {
        useAuthStore.getState().setRegisterOpen(false);
        const modal = document.getElementById('registerModal');
        if (modal) {
            modal.style.display = 'none';
            document.body.style.overflow = '';
        }
    },
    openLoginModal: () => {
        useAuthStore.getState().setLoginOpen(true);
        const modal = document.getElementById('loginModal');
        if (modal) {
            modal.style.display = 'flex';
            document.body.style.overflow = 'hidden';
            const form = document.getElementById('loginForm') as HTMLFormElement;
            if (form) form.reset();
        }
    },
    closeLoginModal: () => {
        useAuthStore.getState().setLoginOpen(false);
        const modal = document.getElementById('loginModal');
        if (modal) {
            modal.style.display = 'none';
            document.body.style.overflow = '';
        }
    },
    switchToRegister: () => {
        Auth.closeLoginModal();
        setTimeout(Auth.openRegisterModal, 200);
    },
    switchToLogin: () => {
        Auth.closeRegisterModal();
        setTimeout(Auth.openLoginModal, 200);
    },
    togglePassword: (inputId: string, icon: HTMLElement) => {
        const input = document.getElementById(inputId) as HTMLInputElement;
        if (!input) return;
        if (input.type === 'password') {
            input.type = 'text';
            icon.classList.replace('fa-eye', 'fa-eye-slash');
        } else {
            input.type = 'password';
            icon.classList.replace('fa-eye-slash', 'fa-eye');
        }
    },
    handleRegister: async (e: SubmitEvent) => {
        e.preventDefault();
        const form = e.target as HTMLFormElement;
        const msg = document.getElementById('registerMsg');
        if (!msg) return;

        if ((form.elements.namedItem('password') as HTMLInputElement).value !== (form.elements.namedItem('confirmPassword') as HTMLInputElement).value) {
            msg.textContent = '❌ 兩次密碼輸入不一致';
            msg.style.color = 'var(--accent)';
            (form.elements.namedItem('confirmPassword') as HTMLInputElement).focus();
            return;
        }

        const addressInput = document.getElementById('regAddress') as HTMLInputElement;
        const fullAddress = addressInput ? addressInput.value.trim() : '';

        if (fullAddress && fullAddress.length < 3) {
            msg.textContent = '❌ 詳細地址不能為空！';
            msg.style.color = 'var(--accent)';
            if (addressInput) {
                addressInput.focus();
                addressInput.style.borderColor = '#e94560';
            }
            return;
        }

        msg.textContent = '';
        try {
            const formData = {
                username: (form.elements.namedItem('username') as HTMLInputElement).value.trim(),
                name: (form.elements.namedItem('name') as HTMLInputElement).value.trim(),
                email: (form.elements.namedItem('email') as HTMLInputElement).value.trim(),
                password: (form.elements.namedItem('password') as HTMLInputElement).value,
                phone: (form.elements.namedItem('phone') as HTMLInputElement).value.trim(),
                fullAddress: fullAddress
            };
            const response = await fetch('/api/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(formData),
            });
            const result = await response.json();
            if (response.ok) {
                msg.textContent = '✅ 註冊成功！3秒後自動登入...';
                msg.style.color = 'green';
                setTimeout(() => {
                    Auth.closeRegisterModal();
                    Auth.openLoginModal();
                    const loginForm = document.getElementById('loginForm') as HTMLFormElement;
                    if (loginForm) {
                        (loginForm.elements.namedItem('username') as HTMLInputElement).value = formData.username;
                        (loginForm.elements.namedItem('password') as HTMLInputElement).focus();
                    }
                }, 2500);
            } else {
                msg.textContent = '❌ ' + (result.message || '註冊失敗，請重試');
                msg.style.color = 'var(--accent)';
            }
        } catch (error) {
            msg.textContent = '❌ 網絡錯誤，請檢查連接';
            msg.style.color = 'var(--accent)';
            console.error('註冊錯誤:', error);
        }
    },
    handleLogin: async (e: SubmitEvent) => {
        e.preventDefault();
        const form = e.target as HTMLFormElement;
        const msg = document.getElementById('loginMsg');
        if (!msg) return;
        msg.textContent = '';
        msg.style.color = 'var(--accent)';

        try {
            const response = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: (form.elements.namedItem('username') as HTMLInputElement).value.trim(),
                    password: (form.elements.namedItem('password') as HTMLInputElement).value,
                }),
            });
            const result = await response.json();
            if (response.ok) {
                msg.textContent = '✅ 登入成功！正在刷新...';
                msg.style.color = 'green';
                setTimeout(() => {
                    Auth.closeLoginModal();
                    window.location.reload();
                }, 1200);
            } else {
                let errorMsg = result.message || '用戶名或密碼錯誤';
                if (errorMsg.startsWith('USER_NOT_FOUND:')) {
                    errorMsg = '❌ 該用戶名不存在';
                    (form.querySelector('input[name="username"]') as HTMLElement).classList.add('shake');
                    setTimeout(() => (form.querySelector('input[name="username"]') as HTMLElement).classList.remove('shake'), 500);
                } else if (errorMsg.startsWith('INVALID_PASSWORD:')) {
                    errorMsg = '❌ 輸入密碼不正確';
                    (form.querySelector('input[name="password"]') as HTMLElement).classList.add('shake');
                    setTimeout(() => (form.querySelector('input[name="password"]') as HTMLElement).classList.remove('shake'), 500);
                } else {
                    errorMsg = '❌ ' + errorMsg;
                }
                msg.textContent = errorMsg;
            }
        } catch (error) {
            msg.textContent = '❌ 網絡錯誤，請檢查連接';
        }
    },
    showLogoutModal: () => {
        useAuthStore.getState().setLogoutOpen(true);
        const modal = document.getElementById('logoutModal');
        if (modal) {
            modal.style.display = 'flex';
            document.body.style.overflow = 'hidden';
        }
    },
    closeLogoutModal: () => {
        useAuthStore.getState().setLogoutOpen(false);
        const modal = document.getElementById('logoutModal');
        if (modal) {
            modal.style.display = 'none';
            document.body.style.overflow = '';
        }
    },
    performLogout: async () => {
        Auth.closeLogoutModal();
        try {
            await fetch('/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' } });
            window.location.href = '/';
        } catch (error) {
            console.error('登出失敗:', error);
            window.location.href = '/';
        }
    },
    handleLogout: () => {
        Auth.showLogoutModal();
    },
    requireLogin: (url: string, message?: string) => {
        if (typeof window.isLoggedIn === 'undefined' || !window.isLoggedIn) {
            window.showNotification(message || '❌ 請先登入！', true);
            setTimeout(() => { Auth.openLoginModal(); }, 500);
            return false;
        }
        window.location.href = url;
        return true;
    }
};
window.Auth = Auth;

// ==========================================
// 7. 模塊 3: 通知系統模塊
// ==========================================
export const NotificationSys: NotificationModule = {
    fetchUnreadCounts: async () => {
        try {
            const response = await fetch('/api/notifications/unread-count');
            const data: any = await response.json();

            const msgBadge = document.getElementById('notificationBadge');
            if (msgBadge) {
                if (data.messageCount > 0) {
                    msgBadge.textContent = data.messageCount > 99 ? '99+' : data.messageCount.toString();
                    msgBadge.style.display = 'inline-flex';
                } else {
                    msgBadge.style.display = 'none';
                }
            }

            const sysBadge = document.getElementById('systemNotificationBadge');
            if (sysBadge) {
                if (data.systemCount > 0) {
                    sysBadge.textContent = data.systemCount > 99 ? '99+' : data.systemCount.toString();
                    sysBadge.style.display = 'inline-flex';
                } else {
                    sysBadge.style.display = 'none';
                }
            }

            let targetTab = 'MY';
            if (data.replyCount > 0) targetTab = 'REPLY';
            else if (data.mentionCount > 0) targetTab = 'MENTION';
            else if (data.likeCount > 0) targetTab = 'LIKED_ME';

            const navLinks = document.querySelectorAll('a[href^="/account/reviews"]');
            navLinks.forEach(link => {
                if (data.messageCount > 0) {
                    (link as HTMLAnchorElement).href = `/account/reviews?type=${targetTab}`;
                } else {
                    (link as HTMLAnchorElement).href = `/account/reviews`;
                }
            });
        } catch (error) {
            console.error('獲取通知失敗:', error);
        }
    }
};
window.NotificationSys = NotificationSys;

// ==========================================
// 8. 模塊 4: 註冊表單 - 區域級聯選擇與自動填充
// ==========================================
const RegionCascade = {
    init: () => {
        const regionSelect = document.getElementById('regionSelect') as HTMLSelectElement;
        const districtSelect = document.getElementById('districtSelect') as HTMLSelectElement;
        const addressInput = document.getElementById('regAddress') as HTMLInputElement;

        if (!regionSelect || !districtSelect || !addressInput) return;

        const districtData: Record<string, string[]> = {
            港岛: ['中西区', '湾仔区', '东区', '南区'],
            九龙: ['油尖旺区', '深水埗区', '九龙城区', '黄大仙区', '观塘区'],
            新界: ['荃湾区', '屯门区', '元朗区', '北区', '大埔区', '西贡区', '沙田区', '葵青区', '离岛区'],
        };

        regionSelect.addEventListener('change', function () {
            const selectedRegion = this.value;
            districtSelect.innerHTML = '<option value="">请选择行政区</option>';
            districtSelect.disabled = true;
            districtSelect.style.backgroundColor = '#f8f9fa';

            if (selectedRegion && districtData[selectedRegion]) {
                districtSelect.disabled = false;
                districtSelect.style.backgroundColor = 'white';
                districtData[selectedRegion].forEach((district) => {
                    const option = document.createElement('option');
                    option.value = district;
                    option.textContent = district;
                    districtSelect.appendChild(option);
                });
            }
        });

        districtSelect.addEventListener('change', function () {
            const region = regionSelect.value;
            const district = this.value;
            if (region && district) {
                if (!addressInput.value.startsWith(region)) {
                    addressInput.value = region + ' ' + district + ' ';
                } else {
                    if (addressInput.value.trim() === '') {
                        addressInput.value = region + ' ' + district + ' ';
                    }
                }
                addressInput.focus();
                addressInput.classList.remove('is-invalid');
            }
        });

        const registerForm = document.getElementById('registerForm');
        if (registerForm) {
            registerForm.addEventListener('submit', function (e: Event) {
                const region = regionSelect.value;
                const address = addressInput.value.trim();
                if (region && (!address || !address.includes(region))) {
                    e.preventDefault();
                    window.showNotification('❌ 既然選擇了區域，詳細地址不能為空，且必須包含所選區域！', true);
                    addressInput.focus();
                    addressInput.style.borderColor = '#e94560';
                }
            });
        }
    }
};

// ==========================================
// 9. 模塊 5: 滾動通知功能
// ==========================================
async function loadScrollingNotification() {
    try {
        const response = await fetch('/api/config/notification');
        const config: any = await response.json();
        const container = document.getElementById('notificationContainer');
        const notificationText = document.getElementById('scrollingNotification');

        if (!container || !notificationText) return;
        if (!config.notificationText || config.notificationText.trim() === '') {
            container.style.display = 'none';
            return;
        }

        const text = config.notificationText;
        const scrollEnabled = config.scrollEnabled !== 'false';
        const direction = config.scrollDirection || 'rtl';

        notificationText.innerHTML = '';
        notificationText.style.whiteSpace = 'nowrap';

        if (scrollEnabled && direction === 'ltr') {
            const chars = text.split('').reverse();
            for (const char of chars) {
                const span = document.createElement('span');
                span.textContent = char;
                notificationText.appendChild(span);
            }
        } else {
            for (const char of text) {
                const span = document.createElement('span');
                span.textContent = char;
                notificationText.appendChild(span);
            }
        }

        notificationText.style.color = config.textColor || '#FFFFFF';
        notificationText.style.fontWeight = config.fontWeight || 'normal';
        notificationText.style.fontSize = (config.fontSize || '14') + 'px';
        notificationText.style.fontStyle = (config.fontItalic === 'true') ? 'italic' : 'normal';

        if (scrollEnabled) {
            notificationText.classList.remove('is-scrolling-rtl', 'is-scrolling-ltr');
            const speed = config.scrollSpeed || 'normal';
            const intervalSeconds = parseFloat(config.scrollInterval) || 0;
            let secondsPerChar = 1.4;
            if (speed === 'slow') secondsPerChar = 1.7;
            else if (speed === 'fast') secondsPerChar = 1.2;

            const totalChars = text.length;
            const pureScrollDuration = totalChars * secondsPerChar;
            const totalCycleDuration = pureScrollDuration + intervalSeconds;
            const finalDuration = Math.max(5, totalCycleDuration);

            if (direction === 'ltr') {
                notificationText.classList.add('is-scrolling-ltr');
                notificationText.style.animationDuration = `${finalDuration}s`;
            } else {
                notificationText.classList.add('is-scrolling-rtl');
                notificationText.style.animationDuration = `${finalDuration}s`;
            }
        } else {
            notificationText.classList.remove('is-scrolling-rtl', 'is-scrolling-ltr');
            notificationText.style.animation = 'none';
        }
        container.style.display = 'flex';
    } catch (error) {
        console.error('加載滾動通知失敗:', error);
    }
}

// ==========================================
// 10. 全域事件綁定與初始化
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    RegionCascade.init();
    loadScrollingNotification();

    const loginModal = document.getElementById('loginModal');
    if (loginModal) {
        loginModal.addEventListener('click', (e) => {
            if (e.target === loginModal) Auth.closeLoginModal();
        });
    }
    const registerModal = document.getElementById('registerModal');
    if (registerModal) {
        registerModal.addEventListener('click', (e) => {
            if (e.target === registerModal) Auth.closeRegisterModal();
        });
    }

    if (document.querySelector('.nav-dropdown') && typeof Cart !== 'undefined') {
        Cart.loadItems();
    }

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            Auth.closeRegisterModal();
            Auth.closeLoginModal();
            Auth.closeLogoutModal();
            Cart.closeDeleteModal();
        }
    });

    if (typeof window.isLoggedIn !== 'undefined' && window.isLoggedIn) {
        NotificationSys.fetchUnreadCounts();
    }
});