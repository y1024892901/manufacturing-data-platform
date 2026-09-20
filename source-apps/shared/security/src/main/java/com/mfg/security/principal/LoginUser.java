package com.mfg.security.principal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 当前登录用户。
 *
 * <p>由 {@code JwtAuthenticationFilter} 在每次请求时构造并放入 SecurityContext，
 * 业务代码通过 {@code CurrentUser.get()} 获取，避免到处传递用户参数。
 */
@Getter
@RequiredArgsConstructor
public class LoginUser {

    private final Long userId;
    private final String username;
    private final String realName;
    private final String deptCode;
    private final String positionName;
    private final String dataScopeType;
    private final String dataScopeValue;

    /** 角色编码集合，如 SALES_REP、PROCESS_SUPERVISOR */
    private final Set<String> roleCodes;

    /** 权限码集合，如 MDM:BOM:CHANGE */
    private final Set<String> permissions;

    /** 可访问系统集合，如 mdm / crm / erp */
    private final Set<String> systems;

    /** 是否为系统管理员（拥有全部权限，演示兜底） */
    public boolean isAdmin() {
        return roleCodes.contains("ADMIN");
    }

    /** 是否拥有某角色 */
    public boolean hasRole(String roleCode) {
        return isAdmin() || roleCodes.contains(roleCode);
    }

    /** 是否拥有某权限 */
    public boolean hasPermission(String permCode) {
        return isAdmin() || permissions.contains(permCode);
    }

    /** 能否访问某系统（第一层权限） */
    public boolean canAccessSystem(String systemCode) {
        return isAdmin() || systems.contains(systemCode);
    }
}
