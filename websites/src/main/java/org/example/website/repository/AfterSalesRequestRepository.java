package org.example.website.repository;

import org.example.website.entity.AfterSalesRequest;
import org.example.website.entity.AfterSalesRequestItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AfterSalesRequestRepository extends JpaRepository<AfterSalesRequest, Long> {

    /**
     * 根據原始訂單編號、申請類型和狀態查詢售後申請記錄
     *
     * 命名規則解析：
     * findBy -> 查詢
     * OriginalOrder_OrderNo -> 關聯的 originalOrder 實體中的 orderNo 屬性
     * AndRequestType -> 並且 requestType 屬性等於...
     * AndStatus -> 並且 status 屬性等於...
     *
     * @param orderNo 訂單編號
     * @param requestType 申請類型 (例如: RETURN)
     * @param status 狀態 (例如: PENDING)
     * @return 匹配的售後申請記錄 (使用 Optional 防止 NullPointerException)
     */
    Optional<AfterSalesRequest> findByOriginalOrder_OrderNoAndRequestTypeAndStatus(
            String orderNo,
            AfterSalesRequest.RequestType requestType,
            AfterSalesRequest.RequestStatus status
    );

    // 【新增】根據訂單號和狀態查詢所有售後申請
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

    Page<AfterSalesRequest> findByStatus(AfterSalesRequest.RequestStatus status, Pageable pageable);

    Page<AfterSalesRequest> findByRequestType(AfterSalesRequest.RequestType requestType, Pageable pageable);

    Page<AfterSalesRequest> findByStatusAndRequestType(
            AfterSalesRequest.RequestStatus status,
            AfterSalesRequest.RequestType requestType,
            Pageable pageable
    );
}