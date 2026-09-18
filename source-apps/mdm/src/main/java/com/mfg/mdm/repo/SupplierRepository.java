package com.mfg.mdm.repo;

import com.mfg.mdm.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findBySupplierCode(String supplierCode);

    boolean existsBySupplierCode(String supplierCode);

    Page<Supplier> findBySupplierNameContainingOrSupplierCodeContaining(
            String name, String code, Pageable pageable);

    Page<Supplier> findByStatus(String status, Pageable pageable);

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.status IN ('PUBLISHED','CHANGING')
            ORDER BY s.supplierCode
            """)
    List<Supplier> findConsumable();
}
