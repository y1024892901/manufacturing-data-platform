package com.mfg.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户账号（对应 mfg_auth.sys_user）。
 *
 * <p>权限模型是两层结构：
 * <ol>
 *   <li><b>系统访问权</b>（{@link #systems}）—— 能进哪几个系统</li>
 *   <li><b>角色</b>（{@link #roles}）—— 进去之后能干什么</li>
 * </ol>
 */
@Entity
@Table(name = "sys_user")
@Getter
@Setter
@NoArgsConstructor
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    @Column(name = "emp_code", length = 32)
    private String empCode;

    @Column(name = "org_id")
    private Long orgId;

    /** 所属部门编码，数据范围 DEPT 级过滤依据 */
    @Column(name = "dept_code", length = 32)
    private String deptCode;

    @Column(name = "position_name", length = 50)
    private String positionName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = true;

    /** 演示账号标记，便于演示时筛选 */
    @Column(name = "is_demo_account", nullable = false)
    private Boolean demoAccount = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // ---------- 关联 ----------

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<SysRole> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "sys_user_system",
            joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "system_code", length = 16)
    private Set<String> systems = new HashSet<>();

    /** 所有角色编码，供权限判断使用 */
    public Set<String> roleCodes() {
        Set<String> codes = new HashSet<>();
        roles.forEach(r -> codes.add(r.getRoleCode()));
        return codes;
    }
}
