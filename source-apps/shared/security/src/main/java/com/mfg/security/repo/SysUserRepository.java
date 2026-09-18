package com.mfg.security.repo;

import com.mfg.security.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<SysUser> findByEnabledTrueOrderByIdAsc();

    List<SysUser> findByDeptCodeAndEnabledTrueOrderByIdAsc(String deptCode);

    /** 演示用：列出全部演示账号，便于前端「账号切换」面板 */
    List<SysUser> findByDemoAccountTrueAndEnabledTrueOrderByIdAsc();
}
