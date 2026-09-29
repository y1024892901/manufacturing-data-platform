package com.mfg.mes.repo;

import com.mfg.mes.entity.ProdResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** 生产实绩按生产订单唯一（DDL 的 {@code uk_pr_prod}）。 */
public interface ProdResultRepository extends JpaRepository<ProdResult, Long> {
    Optional<ProdResult> findByProdOrderNo(String prodOrderNo);

    @Modifying
    @Query(value = "INSERT INTO src_mes.mes_prod_result " +
            "(prod_order_no, product_code, total_plan_qty, total_completed_qty, total_qualified_qty, progress_rate) " +
            "VALUES (:prodOrderNo, :productCode, :planQty, 0, 0, 0) " +
            "ON DUPLICATE KEY UPDATE prod_order_no = VALUES(prod_order_no)", nativeQuery = true)
    int ensureExistsAndLock(@Param("prodOrderNo") String prodOrderNo,
                            @Param("productCode") String productCode,
                            @Param("planQty") java.math.BigDecimal planQty);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProdResult p where p.prodOrderNo = :prodOrderNo")
    Optional<ProdResult> findByProdOrderNoForUpdate(@Param("prodOrderNo") String prodOrderNo);
}
