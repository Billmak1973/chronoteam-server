package org.example.website.service;

import org.example.website.entity.Product;
import org.example.website.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public void updateHomeDisplayOrder(Integer productId, Integer newOrder) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        Integer oldOrder = product.getHomeDisplayOrder();

        // 如果新舊排序相同，不需要處理
        if (Objects.equals(oldOrder, newOrder)) {
            return;
        }

        // 情況1：取消推薦（newOrder 為 null 或 <= 0）
        if (newOrder == null || newOrder <= 0) {
            if (oldOrder != null) {
                // 該商品之後的所有商品排序 -1
                List<Product> productsAfter = productRepository.findAllByHomeDisplayOrderGreaterThan(oldOrder);
                for (Product p : productsAfter) {
                    p.setHomeDisplayOrder(p.getHomeDisplayOrder() - 1);
                }
                productRepository.saveAll(productsAfter);
            }
            product.setHomeDisplayOrder(null);
            productRepository.save(product);
            return;
        }

        // 情況2：從不推薦變為推薦（oldOrder 為 null）
        if (oldOrder == null) {
            // 所有 >= newOrder 的商品排序 +1
            List<Product> productsAtOrAfter = productRepository.findAllByHomeDisplayOrderGreaterThanEqual(newOrder);
            for (Product p : productsAtOrAfter) {
                p.setHomeDisplayOrder(p.getHomeDisplayOrder() + 1);
            }
            productRepository.saveAll(productsAtOrAfter);
            product.setHomeDisplayOrder(newOrder);
            productRepository.save(product);
            return;
        }

        // 情況3：排序向前移動（例如從 5 移到 2）
        if (newOrder < oldOrder) {
            // 所有在 [newOrder, oldOrder - 1] 範圍內的商品排序 +1
            List<Product> productsInRange = productRepository.findAllByHomeDisplayOrderBetween(newOrder, oldOrder - 1);
            for (Product p : productsInRange) {
                p.setHomeDisplayOrder(p.getHomeDisplayOrder() + 1);
            }
            productRepository.saveAll(productsInRange);
        }
        // 情況4：排序向後移動（例如從 2 移到 5）
        else if (newOrder > oldOrder) {
            // 所有在 [oldOrder + 1, newOrder] 範圍內的商品排序 -1
            List<Product> productsInRange = productRepository.findAllByHomeDisplayOrderBetween(oldOrder + 1, newOrder);
            for (Product p : productsInRange) {
                p.setHomeDisplayOrder(p.getHomeDisplayOrder() - 1);
            }
            productRepository.saveAll(productsInRange);
        }

        // 更新當前商品的排序
        product.setHomeDisplayOrder(newOrder);
        productRepository.save(product);
    }
}