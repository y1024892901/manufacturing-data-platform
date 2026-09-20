package com.mfg.security.repo;

import com.mfg.security.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByUsernameAndDeletedFalse(String username);

    boolean existsByUsername(String username);

    @Query("select u from SysUser u where u.deleted = false and " +
            "(:keyword is null or :keyword = '' or lower(u.username) like lower(concat('%', :keyword, '%')) " +
            "or lower(u.realName) like lower(concat('%', :keyword, '%')) or lower(coalesce(u.empCode, '')) like lower(concat('%', :keyword, '%'))) ")
    Page<SysUser> searchActive(@Param("keyword") String keyword, Pageable pageable);

    List<SysUser> findByEnabledTrueOrderByIdAsc();

    List<SysUser> findByDeletedFalseOrderByIdAsc();

    List<SysUser> findByDeptCodeAndEnabledTrueOrderByIdAsc(String deptCode);

    /** 演示用：列出全部演示账号，便于前端「账号切换」面板 */
    List<SysUser> findByDemoAccountTrueAndEnabledTrueOrderByIdAsc();
}
