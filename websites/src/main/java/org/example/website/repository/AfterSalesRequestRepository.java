package org.example.website.repository;

import org.example.website.entity.AfterSalesRequest;
import org.example.website.entity.AfterSalesRequestItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 售後申請數據訪問層 (Repository) 介面
 *
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並繼承 {@link JpaSpecificationExecutor} 以支援<strong>動態多條件查詢</strong>。
 * </p>
 *
 * <p>
 * <strong>為什麼需要繼承 JpaSpecificationExecutor？</strong><br>
 * 當查詢條件是動態組合的（例如：管理員在後台同時勾選了「特定用戶」+「特定狀態」+「特定類型」），
 * 傳統的 `findBy...` 方法名命名規則無法滿足這種靈活組合的需求。<br>
 * 繼承此介面後，Repository 即可使用 {@code findAll(Specification<T> spec, Pageable pageable)} 方法，
 * 允許在 Service 或 Controller 層通過 {@code Specification} 動態拼接複雜的 SQL WHERE 子句，同時保持分頁與排序功能。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface AfterSalesRequestRepository extends JpaRepository<AfterSalesRequest, Long>, JpaSpecificationExecutor<AfterSalesRequest> {

    /**
     * 根據原始訂單編號、申請類型和狀態查詢售後申請記錄
     *
     * @param orderNo     訂單編號
     * @param requestType 申請類型 (例如: RETURN)
     * @param status      狀態 (例如: PENDING)
     * @return 匹配的售後申請記錄 (使用 Optional 防止 NullPointerException)
     */
    Optional<AfterSalesRequest> findByOriginalOrder_OrderNoAndRequestTypeAndStatus(
            String orderNo,
            AfterSalesRequest.RequestType requestType,
            AfterSalesRequest.RequestStatus status
    );

    /**
     * 根據訂單號和狀態查詢所有售後申請
     */
    List<AfterSalesRequest> findByOriginalOrder_OrderNoAndStatus(
            String orderNo,
            AfterSalesRequest.RequestStatus status);

    /**
     * 根據訂單 ID 查詢所有關聯的售後申請記錄
     */
    List<AfterSalesRequest> findByOriginalOrder_OrderId(Long orderId);

    /**
     * 查詢指定訂單中，指定商品的所有「待處理」(PENDING) 狀態的售後申請明細
     * 用於校驗申請數量是否超過訂單原始數量
     */
    @Query("SELECT item FROM AfterSalesRequestItem item " +
            "WHERE item.afterSalesRequest.originalOrder.orderNo = :orderNo " +
            "AND item.product.productId = :productId " +
            "AND item.afterSalesRequest.status = :status")
    List<AfterSalesRequestItem> findAllByOrderNoAndProductIdAndStatus(
            @Param("orderNo") String orderNo,
            @Param("productId") Integer productId,
            @Param("status") AfterSalesRequest.RequestStatus status
    );

    /**
     * 查詢指定訂單下，所有狀態為「已完成」的售後申請記錄
     * 用於聚合統計該訂單是否已經被 100% 售後處理
     */
    List<AfterSalesRequest> findByOriginalOrder_OrderIdAndStatus(
            Long orderId,
            AfterSalesRequest.RequestStatus status
    );

    /**
     * 檢查是否存在關聯該訂單 ID 的售後申請記錄
     * 用於區分「普通取消訂單」與「售後退貨訂單」
     */
    boolean existsByOriginalOrder_OrderId(Long orderId);

    /**
     * 獲取所有出現過的非空門店 ID 列表
     * 用於後台篩選下拉框的數據源
     */
    @Query("SELECT DISTINCT a.storeId FROM AfterSalesRequest a WHERE a.storeId IS NOT NULL AND a.storeId != ''")
    List<String> findDistinctStoreIds();

    @Query("SELECT COALESCE(SUM(i.returnQuantity), 0) FROM AfterSalesRequestItem i " +
            "JOIN i.afterSalesRequest r " +
            "WHERE i.orderItem.orderItemId = :orderItemId " +
            "AND r.status IN ('PENDING', 'COMPLETED')")
    Integer sumValidReturnQuantityByOrderItemId(@Param("orderItemId") Long orderItemId);


}