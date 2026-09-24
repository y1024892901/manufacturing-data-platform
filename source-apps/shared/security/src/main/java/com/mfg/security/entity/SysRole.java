package com.mfg.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * 角色（对应 mfg_auth.sys_role）。
 *
 * <p>本项目有 36 个角色，按部门分级（执行岗 → 主管 → 经理/总监），
 * 详见 {@code infra/db-init/README.md} 的组织架构章节。
 */
@Entity
@Table(name = "sys_role")
@Getter
@Setter
@NoArgsConstructor
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, unique = true, length = 32)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "is_system", nullable = false)
    private Boolean system = false;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "sys_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<SysPermission> permissions = new HashSet<>();

    /** 所有权限码，供 {@code @PreAuthorize} 判断使用 */
    public Set<String> permissionCodes() {
        Set<String> codes = new HashSet<>();
        permissions.forEach(p -> codes.add(p.getPermCode()));
        return codes;
    }
}
