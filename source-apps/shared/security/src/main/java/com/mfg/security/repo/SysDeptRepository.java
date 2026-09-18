package com.mfg.security.repo;

import com.mfg.security.entity.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysDeptRepository extends JpaRepository<SysDept, Long> {

    Optional<SysDept> findByDeptCode(String deptCode);

    List<SysDept> findByEnabledTrueOrderBySortNoAsc();
}
