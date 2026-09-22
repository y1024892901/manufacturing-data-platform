package com.mfg.mdm.repo;

import com.mfg.mdm.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByProductCode(String productCode);
    Optional<Product> findByProductCode(String productCode);
    Page<Product> findByStatus(String status, Pageable pageable);
    Page<Product> findByProductCodeContainingOrProductNameContaining(String code, String name, Pageable pageable);
    List<Product> findByStatusInOrderByProductCode(java.util.Collection<String> statuses);
}
