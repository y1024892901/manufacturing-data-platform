package com.mfg.security.repo;

import com.mfg.security.entity.SysLoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysLoginLogRepository extends JpaRepository<SysLoginLog, Long> {
    Page<SysLoginLog> findAllByOrderByLoginAtDesc(Pageable pageable);
}
