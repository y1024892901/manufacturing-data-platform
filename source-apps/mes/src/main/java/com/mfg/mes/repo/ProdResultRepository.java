package com.mfg.mes.repo;

import com.mfg.mes.entity.ProdResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** 生产实绩按生产订单唯一（DDL 的 {@code uk_pr_prod}）。 */
public interface ProdResultRepository extends JpaRepository<ProdResult, Long> {
    Optional<ProdResult> findByProdOrderNo(String prodOrderNo);
}
