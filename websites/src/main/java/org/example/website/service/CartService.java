package org.example.website.service;

import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    @Transactional
    public Cart addToCart(String username, Integer productId) {
        // 1. 基礎數據校驗
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 2. 【核心修復】：只查找「線上」記錄 (store_id IS NULL)
        // 使用新定義的 Repository 方法，避免誤匹配到線下門店記錄
        Cart existingCart = cartRepository
                .findByUser_UsernameAndProduct_ProductIdAndStoreIsNull(username, productId)
                .orElse(null);

        if (existingCart != null) {
            // 3. 如果已存在線上記錄，檢查線上總倉庫存並增加數量
            if (existingCart.getQuantity() < product.getStock()) {
                existingCart.setQuantity(existingCart.getQuantity() + 1);
                return cartRepository.save(existingCart);
            } else {
                throw new RuntimeException("線上庫存不足，當前庫存: " + product.getStock());
            }
        } else {
            // 4. 如果不存在，創建新的一筆線上購物車記錄
            Cart cart = new Cart();
            cart.setUser(user);
            cart.setProduct(product);
            cart.setStore(null);       // 【關鍵】線上商品 store 設為 null
            cart.setIsOffline(false);  // 【關鍵】標記為線上訂單
            cart.setQuantity(1);
            cart.setPrice(product.getPrice());
            cart.setOrderDate(LocalDate.now());
            cart.setSelected(true);    // 默認勾選
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

        // 【核心修改】：根据 isOffline 判断库存校验逻辑
        if (Boolean.TRUE.equals(cart.getIsOffline())) {
            // === 线下门店商品逻辑 ===
            if (cart.getStore() == null) {
                throw new RuntimeException("线下商品未关联门店，数据异常");
            }

            // 查询该门店该商品的库存记录
            StoreInventory storeInv = storeInventoryRepository
                    .findByStoreAndProduct(cart.getStore(), cart.getProduct())
                    .orElse(null);

            int availableStock = 0;
            if (storeInv != null) {
                // 可售库存 = 物理库存 - 预留库存
                int physicalQty = storeInv.getQuantity() != null ? storeInv.getQuantity() : 0;
                int reservedQty = storeInv.getReservedQuantity() != null ? storeInv.getReservedQuantity() : 0;
                availableStock = physicalQty - reservedQty;
            }

            // 校验：请求数量不能大于可售库存
            if (quantity > availableStock) {
                throw new RuntimeException("门店库存不足，当前可售: " + Math.max(availableStock, 0));
            }

        } else {
            // === 线上总仓商品逻辑 (保持原样) ===
            Product product = cart.getProduct();
            // 检查库存
            if (quantity > product.getStock()) {
                throw new RuntimeException("库存不足，当前库存: " + product.getStock());
            }
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
     * 【新增】將線下門店商品加入購物車
     * 核心邏輯：
     * 1. 校驗用戶、商品、門店是否存在。
     * 2. 獲取該門店該商品的庫存記錄。
     * 3. 計算【可售庫存】 = 物理庫存 (quantity) - 已預留庫存 (reservedQuantity)。
     * 4. 檢查【可售庫存】是否 >= 請求數量。如果不足，拋出異常。
     * 5. 如果購物車中已存在該用戶+該商品+該門店的記錄，則累加數量並再次校驗總數是否超過可售庫存。
     * 6. 保存購物車記錄，標記 isOffline = true。
     *
     * 注意：此操作【不】更新 StoreInventory 的 reservedQuantity，因為尚未付款。
     */
    @Transactional
    public Cart addStoreItemToCart(String username, Integer productId, Long storeId, Integer quantity) {
        if (quantity <= 0) {
            throw new RuntimeException("加入購物車的數量必須大於 0");
        }

        // 1. 獲取用戶實體
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 2. 獲取商品實體
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        // 3. 獲取門店庫存記錄 (包含物理庫存和預留庫存)
        Optional<StoreInventory> inventoryOpt = storeInventoryRepository
                .findByStore_StoreIdAndProduct_ProductId(storeId, productId);

        if (inventoryOpt.isEmpty()) {
            throw new RuntimeException("該門店暫無此商品庫存");
        }

        StoreInventory inventory = inventoryOpt.get();

        // 4. 【核心校驗】計算可售庫存
        // getAvailableStock() 內部邏輯通常是: Math.max(0, quantity - reservedQuantity)
        int availableStock = inventory.getAvailableStock();

        // 5. 檢查購物車中是否已存在該用戶、該商品、該門店的記錄
        // 注意：Cart 表需要能區分線上和線下，或者通過 storeId 關聯來判斷
        // 假設 Cart 實體中有 storeId 字段，或者我們通過自定義查詢方法
        Cart existingCart = cartRepository.findByUser_UsernameAndProduct_ProductIdAndStore_StoreId(
                username, productId, storeId
        );

        int totalRequestedQty = quantity;
        if (existingCart != null) {
            totalRequestedQty = existingCart.getQuantity() + quantity;
        }

        // 6. 最終校驗：請求總數量不能超過門店的可售庫存
        if (totalRequestedQty > availableStock) {
            throw new RuntimeException(
                    String.format("庫存不足！門店 [%s] 的商品 [%s] 當前可售庫存僅為 %d 件，您購物車中已有 %d 件，試圖再添加 %d 件。",
                            inventory.getStore().getName(),
                            product.getDescription(),
                            availableStock,
                            existingCart != null ? existingCart.getQuantity() : 0,
                            quantity)
            );
        }

        // 7. 創建或更新購物車記錄
        if (existingCart != null) {
            existingCart.setQuantity(totalRequestedQty);
            existingCart.setUpdatedAt(java.time.LocalDateTime.now());
            return cartRepository.save(existingCart);
        } else {
            Cart newCart = new Cart();
            newCart.setUser(user);
            newCart.setProduct(product);
            newCart.setStore(inventory.getStore()); // 關聯門店
            newCart.setQuantity(quantity);
            newCart.setPrice(product.getPrice()); // 使用商品當前價格
            newCart.setOrderDate(LocalDate.now());
            newCart.setSelected(true);
            newCart.setIsOffline(true); // 【關鍵】標記為線下訂單
            return cartRepository.save(newCart);
        }
    }
}