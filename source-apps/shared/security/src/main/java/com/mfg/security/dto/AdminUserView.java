package com.mfg.security.dto;

import com.mfg.security.entity.SysUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record AdminUserView(
        Long id, String username, String realName, String empCode, String deptCode,
        String positionName, String email, String mobile, Boolean enabled, Boolean locked,
        Boolean demoAccount, LocalDateTime lastLoginAt, List<Option> roles, Set<String> systems,
        String dataScopeType, String dataScopeValue) {

    public record Option(Long id, String code, String name) {}

    public static AdminUserView from(SysUser user) {
        List<Option> roles = user.getRoles().stream()
                .map(r -> new Option(r.getId(), r.getRoleCode(), r.getRoleName()))
                .sorted(java.util.Comparator.comparing(Option::code))
                .toList();
        return new AdminUserView(user.getId(), user.getUsername(), user.getRealName(),
                user.getEmpCode(), user.getDeptCode(), user.getPositionName(), user.getEmail(),
                user.getMobile(), user.getEnabled(), user.getLocked(), user.getDemoAccount(),
                user.getLastLoginAt(), roles, user.getSystems(), user.getDataScopeType(), user.getDataScopeValue());
    }
}
