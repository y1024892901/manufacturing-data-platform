package com.mfg.security.repo;

import com.mfg.security.entity.SysAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysAuditLogRepository extends JpaRepository<SysAuditLog, Long> {
    Page<SysAuditLog> findAllByOrderByOperatedAtDesc(Pageable pageable);
}
