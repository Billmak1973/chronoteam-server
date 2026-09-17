package org.example.website.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity//標記這個 Cart 類是一個 JPA 實體。它告訴 Hibernate：「請在數據庫中為這個類創建或映射一張表」。
@Table(name = "cart", indexes = {
       // 在 user_id 列上建立普通索引，加快「查詢某個用戶的購物車」的速度。
        @Index(name = "idx_cart_user", columnList = "user_id"),
        //建立唯一索引。這意味著同一個用戶 (user_id) 對同一個商品 (prod_id) 只能有一條購物車記錄，防止重複添加。
        @Index(name = "uk_user_product", columnList = "user_id, prod_id", unique = true)
})
@Data
public class Cart {

    @Id//標記 cartId 字段為數據庫表的主鍵 (Primary Key)。
    @GeneratedValue(strategy = GenerationType.IDENTITY)//指定主鍵的生成策略。IDENTITY 表示依賴數據庫底層的自增長機制（例如 MySQL 的 AUTO_INCREMENT），插入數據時數據庫會自動分配下一個 ID。
    @Column(name = "cart_id")//指定該 Java 屬性對應的數據庫列名為 cart_id。如果不寫，默認會用屬性名 cartId。
    private Long cartId;

   // @ManyToOne(fetch = FetchType.LAZY) 表示多對一的關聯關係（多個購物車記錄可以屬於同一個 User 或同一個 Product）。
    //fetch = FetchType.LAZY：表示延遲加載（懶加載）。當查詢購物車列表時，不會立即去數據庫查詢關聯的 User 和 Product 詳情，只有在代碼中真正調用 cart.getUser() 時才會觸發 SQL 查詢。這能極大提升查詢性能，避免 N+1 查詢問題
    @ManyToOne(fetch = FetchType.LAZY)
    //指定在數據庫中用來建立外鍵關聯的列名為 user_id。nullable = false 表示這個外鍵在數據庫中不能為空（購物車記錄必須屬於某個用戶）。
    @JoinColumn(name = "user_id", nullable = false)
    //這是 Jackson (JSON 序列化庫) 的註解。因為 Hibernate 的懶加載會生成代理對象 (Proxy)，在將對象轉換為 JSON 返回給前端時，這些代理對象的內部屬性會導致序列化錯誤或無限遞歸。這個註解告訴 Jackson：「忽略這些 Hibernate 內部的屬性」，防止報錯。
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", referencedColumnName = "prod_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    //將 Java 屬性映射到具體的數據庫列，並規定 nullable = false（不能為空）。
    @Column(name = "cart_qty", nullable = false)
    private Integer quantity = 1;

    @Column(name = "cart_price", nullable = false)
    private BigDecimal price;

    @Column(name = "cart_order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "selected", nullable = false)
    private Boolean selected = true;


    @UpdateTimestamp//Hibernate 提供的自動時間戳註解。每當這條購物車記錄被更新 (Update) 時，
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreationTimestamp//Hibernate 提供的自動時間戳註解。當這條記錄首次插入 (Insert) 數據庫時，自動填入當前時間。配合 @Column(updatable = false)，確保以後更新記錄時，創建時間不會被意外修改
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}