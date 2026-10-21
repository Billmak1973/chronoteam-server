package org.example.website.repository;

import org.example.website.entity.AfterSalesRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AfterSalesRequestItemRepository extends JpaRepository<AfterSalesRequestItem, Long> {

    /**
     * 計算特定訂單明細 (OrderItem) 已經完成 (COMPLETED) 的售後退回/換貨總數量
     *
     * @param orderItemId 訂單明細 ID
     * @return 已完成的售後數量總和
     */
    @Query("SELECT COALESCE(SUM(asri.returnQuantity), 0) " +
            "FROM AfterSalesRequestItem asri " +
            "JOIN asri.afterSalesRequest asr " +
            "WHERE asri.orderItem.orderItemId = :orderItemId " +
            "AND asr.status = 'COMPLETED'")
    Integer sumCompletedReturnQuantityByOrderItemId(@Param("orderItemId") Long orderItemId);
}
