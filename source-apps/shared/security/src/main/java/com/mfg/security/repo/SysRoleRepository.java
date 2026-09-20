package com.mfg.security.repo;

import com.mfg.security.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SysRoleRepository extends JpaRepository<SysRole, Long> {
    Optional<SysRole> findByRoleCode(String roleCode);
    List<SysRole> findAllByOrderBySortNoAsc();
    Page<SysRole> findAllByOrderBySortNoAsc(Pageable pageable);
}
