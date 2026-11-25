package org.example.website.service;

import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OfflineStoreRepository offlineStoreRepository;
    private final StoreInventoryRepository storeInventoryRepository;

    public CartService(UserRepository userRepository,
                       CartRepository cartRepository,
                       ProductRepository productRepository, OfflineStoreRepository offlineStoreRepository, StoreInventoryRepository storeInventoryRepository) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.offlineStoreRepository = offlineStoreRepository;
        this.storeInventoryRepository = storeInventoryRepository;
    }

    // 添加商品到购物车
    @Transactional
    public Cart addToCart(String username, Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        //  核心修改 1：使用 UserRepository 獲取 User 實體
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 检查是否已在购物车中
        Cart existingCart = cartRepository.findByUser_UsernameAndProduct_ProductId(username, productId);

        if (existingCart != null) {
            // 如果已存在，数量+1（需要检查库存）
            if (existingCart.getQuantity() < product.getStock()) {
                existingCart.setQuantity(existingCart.getQuantity() + 1);
                return cartRepository.save(existingCart);
            } else {
                throw new RuntimeException("库存不足");
            }
        } else {
            // 新增购物车商品
            Cart cart = new Cart();

            //  核心修改 2：設置 User 關聯，不再是 setCustomer
            cart.setUser(user);

            cart.setProduct(product);
            cart.setQuantity(1);
            cart.setPrice(product.getPrice());
            cart.setOrderDate(LocalDate.now());
            return cartRepository.save(cart);
        }
    }

    // 更新购物车商品数量
    @Transactional
    public Cart updateQuantity(String username, Long cartId, Integer quantity) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("购物车商品不存在"));

        // 验证是否属于当前用户
        if (!cart.getUser().getUsername().equals(username)) {
            throw new RuntimeException("无权操作");
        }

        // 检查库存
        if (quantity > cart.getProduct().getStock()) {
            throw new RuntimeException("库存不足，当前库存: " + cart.getProduct().getStock());
        }

        if (quantity <= 0) {
            // 数量<=0时删除该商品
            cartRepository.delete(cart);
            return null;
        }

        cart.setQuantity(quantity);
        return cartRepository.save(cart);
    }

    // 从购物车移除商品
    @Transactional
    public void removeFromCart(String username, Integer productId) {
        cartRepository.deleteByUser_UsernameAndProduct_ProductId(username, productId);
    }

    // 获取用户购物车列表
    public List<Cart> getCartItems(String username) {
        return cartRepository.findByUser_UsernameOrderByCreatedAtDesc(username);
    }

    // 获取购物车商品数量
    public long getCartCount(String username) {
        return cartRepository.countByUser_Username(username);
    }

    @Transactional
    public void toggleSelection(String username, Long cartId, boolean isSelected) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("购物车商品不存在"));

        // 权限校验：确保这个购物车属于当前用户
        if (!cart.getUser().getUsername().equals(username)) {
            throw new RuntimeException("无权操作");
        }

        cart.setSelected(isSelected);
        cartRepository.save(cart);
    }

    /**
     * 添加線下門店商品到購物車
     * 特點：
     * 1. 設置 isOffline = true
     * 2. 設置 storeId
     * 3. 預留門店庫存 (reservedQuantity + 1)
     */
    @Transactional
    public Cart addStoreItemToCart(String username, Integer productId, Long storeId, Integer quantity) {
        // 1. 基礎校驗
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        OfflineStore store = offlineStoreRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("門店不存在"));

        // 2. 檢查門店庫存是否充足 (物理庫存 - 已預留庫存)
        StoreInventory storeInv = storeInventoryRepository
                .findByStore_StoreIdAndProduct_ProductId(storeId, productId)
                .orElseThrow(() -> new RuntimeException("該門店無此商品庫存記錄"));

        int availableStock = storeInv.getQuantity() - (storeInv.getReservedQuantity() != null ? storeInv.getReservedQuantity() : 0);

        if (availableStock < quantity) {
            throw new RuntimeException("門店庫存不足，當前可售: " + availableStock);
        }

        // 3. 檢查購物車是否已有該商品 (同一用戶、同一商品、同一門店)
        // 注意：你的 Cart 表結構可能需要支持 storeId 的唯一性約束，或者在查詢時加上 storeId 條件
        // 這裡假設 Cart 表有 store_id 字段，或者我們通過業務邏輯判斷
        Cart existingCart = cartRepository.findByUser_UsernameAndProduct_ProductIdAndStore_StoreId(
                username, productId, storeId);

        if (existingCart != null) {
            // 如果已存在，增加數量並重新校驗庫存
            int newQty = existingCart.getQuantity() + quantity;
            if (newQty > availableStock + existingCart.getQuantity()) { // 簡單校驗，實際應重新查庫存
                throw new RuntimeException("購物車中該商品數量加上本次添加超過門店庫存");
            }
            existingCart.setQuantity(newQty);
            return cartRepository.save(existingCart);
        } else {
            // 4. 創建新購物車記錄
            Cart cart = new Cart();
            cart.setUser(user);
            cart.setProduct(product);
            cart.setStore(store);          // 【關鍵】設置門店
            cart.setIsOffline(true);       // 【關鍵】標記為線下訂單
            cart.setQuantity(quantity);
            cart.setPrice(product.getPrice());
            cart.setOrderDate(LocalDate.now());
            cart.setSelected(true);

            Cart savedCart = cartRepository.save(cart);

            // 5. 【核心】預留門店庫存
            // 使用原子更新或樂觀鎖防止併發問題
            int updatedRows = storeInventoryRepository.reserveStoreStock(storeId, productId, quantity);
            if (updatedRows == 0) {
                // 如果預留失敗（可能是併發導致庫存不足），回滾購物車記錄
                cartRepository.delete(savedCart);
                throw new RuntimeException("庫存預留失敗，商品可能剛被其他人購買");
            }

            return savedCart;
        }
    }
}