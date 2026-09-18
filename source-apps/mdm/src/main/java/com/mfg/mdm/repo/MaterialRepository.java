package com.mfg.mdm.repo;

import com.mfg.mdm.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    Optional<Material> findByMaterialCode(String materialCode);

    boolean existsByMaterialCode(String materialCode);

    Page<Material> findByMaterialNameContainingOrMaterialCodeContaining(
            String name, String code, Pageable pageable);

    Page<Material> findByStatus(String status, Pageable pageable);

    Page<Material> findByMaterialType(String materialType, Pageable pageable);

    /**
     * 业务系统可选用的物料。
     *
     * <p>这个方法就是「草稿状态的物料在 ERP 里选不到」这条演示效果的实现点：
     * 只返回 PUBLISHED / CHANGING 的记录。
     */
    @Query("""
            SELECT m FROM Material m
            WHERE m.status IN ('PUBLISHED','CHANGING')
            ORDER BY m.materialCode
            """)
    List<Material> findConsumable();

    List<Material> findByCategoryId(Long categoryId);

    long countByStatus(String status);
}
