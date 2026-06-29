package org.example.website.service;

import org.example.website.entity.Cart;
import org.example.website.entity.Customer;
import org.example.website.entity.Product;
import org.example.website.repository.CartRepository;
import org.example.website.repository.CustomerRepository;
import org.example.website.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository,
                       CustomerRepository customerRepository,
                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    // 添加商品到购物车
    @Transactional
    public Cart addToCart(String username, Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 检查是否已在购物车中
        Cart existingCart = cartRepository.findByCustomer_UsernameAndProduct_Id(username, productId);

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
            cart.setCustomer(customer);
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
        if (!cart.getCustomer().getUsername().equals(username)) {
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
        cartRepository.deleteByCustomer_UsernameAndProduct_Id(username, productId);
    }

    // 获取用户购物车列表
    public List<Cart> getCartItems(String username) {
        return cartRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);
    }

    // 获取购物车商品数量
    public long getCartCount(String username) {
        return cartRepository.countByCustomer_Username(username);
    }

    @Transactional
    public void toggleSelection(String username, Long cartId, boolean isSelected) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("购物车商品不存在"));

        // 权限校验：确保这个购物车属于当前用户
        if (!cart.getCustomer().getUsername().equals(username)) {
            throw new RuntimeException("无权操作");
        }

        cart.setSelected(isSelected);
        cartRepository.save(cart);
    }
}