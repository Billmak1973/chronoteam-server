/* ==========================================
   ChronoTeam 公共脚本 (common.js)
   ========================================== */

// ===== 购物车功能 =====
let cartDropdownOpen = false;

function toggleCartDropdown(event) {
    event.stopPropagation();
    const dropdown = document.getElementById('cartDropdown');
    if (!dropdown) return;
    cartDropdownOpen = !cartDropdownOpen;
    if (cartDropdownOpen) {
        dropdown.style.display = 'flex';
        dropdown.style.flexDirection = 'column';
        loadCartItems();
    } else {
        dropdown.style.display = 'none';
    }
}

document.addEventListener('click', function(e) {
    const cartDropdown = document.getElementById('cartDropdown');
    if (cartDropdown && !cartDropdown.contains(e.target)) {
        cartDropdown.style.display = 'none';
        cartDropdownOpen = false;
    }
});

const cartDropdownEl = document.getElementById('cartDropdown');
const cartNavContainer = cartDropdownEl ? cartDropdownEl.closest('.nav-dropdown') : null;
if (cartNavContainer) {
    cartNavContainer.addEventListener('mouseleave', function() {
        const dropdown = document.getElementById('cartDropdown');
        if (dropdown) {
            dropdown.style.display = '';
            dropdown.style.flexDirection = '';
            cartDropdownOpen = false;
        }
    });
}

async function loadCartItems() {
    try {
        const response = await fetch('/api/cart/list');
        const data = await response.json();
        if (data.success) {
            renderCartItems(data.cartItems, data.totalAmount);
            updateCartBadge(data.cartCount);
        }
    } catch (error) { console.error('加载购物车失败:', error); }
}

function renderCartItems(cartItems, totalAmount) {
    const container = document.getElementById('cartItemsContainer');
    if (!container) return;
    if (!cartItems || cartItems.length === 0) {
        container.innerHTML = `<div class="cart-empty"><i class="fas fa-shopping-basket" style="font-size: 3rem; color: #ddd; margin-bottom: 1rem;"></i><p>購物車是空的</p></div>`;
        const totalEl = document.getElementById('cartTotalAmount');
        if(totalEl) totalEl.textContent = 'HK$ 0';
        return;
    }
    let html = '';
    cartItems.forEach(item => {
        html += `<div class="cart-item" data-cart-id="${item.id}" data-product-id="${item.product.id}">
            <div class="cart-item-img"><img src="/images/products/${item.product.image}" alt="${item.product.description}" onerror="this.src='/images/placeholder.png'"></div>
            <div class="cart-item-info">
                <div class="cart-item-name">${item.product.description}</div>
                <div class="cart-item-price">HK$ ${formatPrice(item.price)}</div>
                <div class="cart-item-quantity">
                    <button class="qty-btn" onclick="updateCartQuantity(${item.id}, ${item.quantity - 1})" ${item.quantity <= 1 ? 'disabled' : ''}>-</button>
                    <span class="qty-value">${item.quantity}</span>
                    <button class="qty-btn" onclick="updateCartQuantity(${item.id}, ${item.quantity + 1})" ${item.quantity >= item.product.stock ? 'disabled' : ''}>+</button>
                </div>
            </div>
            <div class="cart-item-remove" onclick="removeFromCart(${item.product.id})"><i class="fas fa-trash-alt"></i></div>
        </div>`;
    });
    container.innerHTML = html;
    const totalEl = document.getElementById('cartTotalAmount');
    if(totalEl) totalEl.textContent = 'HK$ ' + formatPrice(totalAmount);
}

async function updateCartQuantity(cartId, newQuantity) {
    try {
        const response = await fetch(`/api/cart/update/${cartId}?quantity=${newQuantity}`, { method: 'PUT' });
        const data = await response.json();
        if (response.ok) loadCartItems();
        else alert(data.message || '更新失敗');
    } catch (error) { console.error('更新购物车失败:', error); alert('网络错误'); }
}

async function removeFromCart(productId) {
    if (!confirm('確定要移除這個商品嗎？')) return;
    try {
        const response = await fetch(`/api/cart/remove/${productId}`, { method: 'DELETE' });
        if (response.ok) loadCartItems();
    } catch (error) { console.error('移除商品失败:', error); }
}

function updateCartBadge(count) {
    const badge = document.getElementById('cartCountBadge');
    if (!badge) return;
    if (count > 0) { badge.textContent = count; badge.style.display = 'inline-flex'; }
    else { badge.style.display = 'none'; }
}

function formatPrice(price) {
    return new Intl.NumberFormat('zh-HK', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(price);
}

async function checkout() {
    if (!isLoggedIn) {
        showNotification('❌ 請先登入！', true);
        return;
    }

    // 顯示 Loading
    const btn = document.querySelector('.checkout-btn');
    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 處理中...';

    try {
        const response = await fetch('/checkout/api/create', { method: 'POST' });
        const result = await response.json();

        if (response.ok && result.success) {
            // 獲取生成的 orderNo，跳轉到結賬頁面
            window.location.href = `/checkout?orderNo=${result.data}`;
        } else {
            showNotification('❌ ' + (result.message || '創建訂單失敗'), true);
            btn.disabled = false;
            btn.innerHTML = originalText;
        }
    } catch (error) {
        console.error('結賬錯誤:', error);
        showNotification('❌ 網絡錯誤', true);
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

async function addToCart(productId) {
    // 检查是否登录 (兼容未定义的情况)
    if (typeof isLoggedIn === 'undefined' || !isLoggedIn) {
        showNotification('❌ 請先登入後再加入購物車！', true);
        return;
    }
    try {
        const response = await fetch(`/api/cart/add/${productId}`, { method: 'POST' });
        const data = await response.json();
        if (response.ok) { showNotification('✅ 已加入購物車'); loadCartItems(); }
        else { showNotification('❌ ' + (data.message || '加入失敗'), true); }
    } catch (error) { console.error('加入购物车失败:', error); showNotification('❌ 网络错误', true); }
}

function showNotification(message, isError = false) {
    const notification = document.createElement('div');
    notification.style.cssText = `position: fixed; top: 100px; right: 20px; background: ${isError ? 'var(--accent)' : 'var(--gold)'}; color: ${isError ? 'white' : 'var(--primary)'}; padding: 1rem 1.5rem; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.2); z-index: 9999; opacity: 0; transform: translateX(50px); transition: opacity 0.3s ease, transform 0.3s ease;`;
    notification.textContent = message;
    document.body.appendChild(notification);
    setTimeout(() => { notification.style.opacity = '1'; notification.style.transform = 'translateX(0)'; }, 10);
    setTimeout(() => { notification.style.opacity = '0'; notification.style.transform = 'translateX(50px)'; setTimeout(() => notification.remove(), 300); }, 2500);
}

// ===== 登录/注册弹窗控制 =====
function openRegisterModal() {
    const modal = document.getElementById('registerModal'); if (!modal) return;
    modal.style.display = 'flex';
    const msg = document.getElementById('registerMsg'); if (msg) msg.textContent = '';
    const form = document.getElementById('registerForm'); if (form) form.reset();
    document.body.style.overflow = 'hidden';
}
function closeRegisterModal() { const modal = document.getElementById('registerModal'); if (!modal) return; modal.style.display = 'none'; document.body.style.overflow = ''; }
function openLoginModal() {
    const modal = document.getElementById('loginModal'); if (!modal) return;
    modal.style.display = 'flex';
    const msg = document.getElementById('loginMsg'); if (msg) msg.textContent = '';
    const form = document.getElementById('loginForm'); if (form) form.reset();
    document.body.style.overflow = 'hidden';
}
function closeLoginModal() { const modal = document.getElementById('loginModal'); if (!modal) return; modal.style.display = 'none'; document.body.style.overflow = ''; }

document.addEventListener('DOMContentLoaded', function () {
    const registerModal = document.getElementById('registerModal');
    const loginModal = document.getElementById('loginModal');
    if (registerModal) registerModal.addEventListener('click', function (e) { if (e.target === this) closeRegisterModal(); });
    if (loginModal) loginModal.addEventListener('click', function (e) { if (e.target === this) closeLoginModal(); });
    if (document.querySelector('.nav-dropdown')) loadCartItems(); // 初始化购物车
});

document.addEventListener('keydown', function (e) { if (e.key === 'Escape') { closeRegisterModal(); closeLoginModal(); } });

function togglePassword(inputId, icon) {
    const input = document.getElementById(inputId); if (!input) return;
    if (input.type === 'password') { input.type = 'text'; icon.classList.replace('fa-eye', 'fa-eye-slash'); }
    else { input.type = 'password'; icon.classList.replace('fa-eye-slash', 'fa-eye'); }
}

function switchToRegister() { closeLoginModal(); setTimeout(openRegisterModal, 200); }
function switchToLogin() { closeRegisterModal(); setTimeout(openLoginModal, 200); }

async function handleRegister(e) {
    e.preventDefault();
    const form = e.target; const msg = document.getElementById('registerMsg'); const btn = form.querySelector('button[type="submit"]');
    if (form.password.value !== form.confirmPassword.value) { msg.textContent = '❌ 兩次密碼輸入不一致'; msg.style.color = 'var(--accent)'; form.confirmPassword.focus(); return; }
    btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 註冊中...'; msg.textContent = '';
    try {
        const formData = { username: form.username.value.trim(), name: form.name.value.trim(), email: form.email.value.trim(), password: form.password.value, phone: form.phone.value.trim() };
        const response = await fetch('/api/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(formData) });
        const result = await response.json();
        if (response.ok) {
            msg.textContent = '✅ 註冊成功！3秒後自動登入...'; msg.style.color = 'green';
            setTimeout(() => { closeRegisterModal(); openLoginModal(); const loginForm = document.getElementById('loginForm'); if (loginForm) { loginForm.username.value = formData.username; loginForm.password.focus(); } }, 2500);
        } else { msg.textContent = '❌ ' + (result.message || '註冊失敗，請重試'); msg.style.color = 'var(--accent)'; }
    } catch (error) { msg.textContent = '❌ 網絡錯誤，請檢查連接'; msg.style.color = 'var(--accent)'; }
    finally { btn.disabled = false; btn.innerHTML = '<i class="fas fa-user-check"></i> 立即註冊'; }
}

async function handleLogin(e) {
    e.preventDefault();
    const form = e.target; const msg = document.getElementById('loginMsg'); const btn = form.querySelector('button[type="submit"]');
    btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 登入中...'; msg.textContent = '';
    try {
        const response = await fetch('/api/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: form.username.value.trim(), password: form.password.value }) });
        const result = await response.json();
        if (response.ok) { msg.textContent = '✅ 登入成功！正在刷新...'; msg.style.color = 'green'; setTimeout(() => { closeLoginModal(); window.location.reload(); }, 1200); }
        else { msg.textContent = '❌ ' + (result.message || '用戶名或密碼錯誤'); msg.style.color = 'var(--accent)'; form.querySelector('input[name="password"]').classList.add('shake'); setTimeout(() => { form.querySelector('input[name="password"]').classList.remove('shake'); }, 500); }
    } catch (error) { msg.textContent = '❌ 網絡錯誤，請檢查連接'; msg.style.color = 'var(--accent)'; }
    finally { btn.disabled = false; btn.innerHTML = '<i class="fas fa-sign-in-alt"></i> 立即登入'; }
}

async function handleLogout() {
    if (!confirm('確定要登出嗎？')) return;
    try { await fetch('/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' } }); window.location.href = '/'; }
    catch (error) { console.error('登出失败:', error); window.location.href = '/'; }
}

// ===== 頁面跳轉登入攔截 =====
function requireLogin(url, message) {
    // 判斷是否未登入
    if (typeof isLoggedIn === 'undefined' || !isLoggedIn) {
        // 1. 彈出右上角提示
        showNotification(message || '❌ 請先登入！', true);

        // 2. 延遲 0.5 秒後自動彈出登入彈窗，讓用戶先看到提示
        setTimeout(() => {
            if (typeof openLoginModal === 'function') {
                openLoginModal();
            }
        }, 500);

        return false; // 阻止 <a> 標籤的默認跳轉行為
    }

    // 已登入則正常跳轉
    window.location.href = url;
    return true;
}