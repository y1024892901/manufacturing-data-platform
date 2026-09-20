package com.mfg.security.repo;

import com.mfg.security.entity.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SysDeptRepository extends JpaRepository<SysDept, Long> {

    Optional<SysDept> findByDeptCode(String deptCode);

    List<SysDept> findByEnabledTrueOrderBySortNoAsc();
    Page<SysDept> findAllByOrderBySortNoAsc(Pageable pageable);
}
