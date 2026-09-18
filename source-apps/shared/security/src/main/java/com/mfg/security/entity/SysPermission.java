package com.mfg.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 权限点（对应 mfg_auth.sys_permission）。
 *
 * <p>权限码形如 {@code MDM:BOM:CHANGE}，在 Controller 上用
 * {@code @PreAuthorize("hasAuthority('MDM:BOM:CHANGE')")} 声明式使用。
 */
@Entity
@Table(name = "sys_permission")
@Getter
@Setter
@NoArgsConstructor
public class SysPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perm_code", nullable = false, unique = true, length = 64)
    private String permCode;

    @Column(name = "perm_name", nullable = false, length = 100)
    private String permName;

    /** 所属系统：mdm / crm / erp / ... */
    @Column(name = "system_code", nullable = false, length = 16)
    private String systemCode;

    @Column(name = "perm_group", length = 50)
    private String permGroup;

    @Column(name = "perm_type", nullable = false, length = 16)
    private String permType = "API";

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
