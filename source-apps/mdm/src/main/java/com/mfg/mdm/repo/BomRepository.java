package com.mfg.mdm.repo;

import com.mfg.mdm.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BomRepository extends JpaRepository<Bom, Long> {

    Optional<Bom> findByBomCodeAndBomVersion(String bomCode, String bomVersion);

    boolean existsByBomCodeAndBomVersion(String bomCode, String bomVersion);

    Page<Bom> findByBomCodeContainingOrBomNameContaining(
            String code, String name, Pageable pageable);

    Page<Bom> findByStatus(String status, Pageable pageable);

    /** 某产品的当前生效 BOM */
    @Query("""
            SELECT b FROM Bom b
            WHERE b.productCode = :productCode
              AND b.current = true
              AND b.status IN ('PUBLISHED','CHANGING')
            """)
    Optional<Bom> findCurrentByProduct(@Param("productCode") String productCode);

    List<Bom> findByBomCodeOrderByBomVersionDesc(String bomCode);

    /** 业务系统可选用的 BOM */
    @Query("""
            SELECT b FROM Bom b
            WHERE b.status IN ('PUBLISHED','CHANGING') AND b.current = true
            ORDER BY b.bomCode
            """)
    List<Bom> findConsumable();
}
