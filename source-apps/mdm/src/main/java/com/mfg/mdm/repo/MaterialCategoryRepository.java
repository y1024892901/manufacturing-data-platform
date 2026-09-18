package com.mfg.mdm.repo;

import com.mfg.mdm.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialCategoryRepository extends JpaRepository<MaterialCategory, Long> {

    Optional<MaterialCategory> findByCategoryCode(String categoryCode);

    List<MaterialCategory> findByParentIdOrderByCategoryCode(Long parentId);

    List<MaterialCategory> findByIsLeafTrueOrderByCategoryCode();

    @Query("SELECT c FROM MaterialCategory c WHERE c.status = 'PUBLISHED' ORDER BY c.categoryCode")
    List<MaterialCategory> findAllPublished();
}
