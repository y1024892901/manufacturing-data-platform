package com.mfg.security.repo;

import com.mfg.security.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {
    List<SysPermission> findBySystemCodeOrderBySortNoAsc(String systemCode);
    List<SysPermission> findAllByOrderBySystemCodeAscSortNoAsc();
}
