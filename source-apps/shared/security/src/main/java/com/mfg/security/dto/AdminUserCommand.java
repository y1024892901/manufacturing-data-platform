package com.mfg.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record AdminUserCommand(
        @NotBlank String username,
        String initialPassword,
        @NotBlank String realName,
        String empCode,
        String deptCode,
        String positionName,
        String email,
        String mobile,
        Boolean demoAccount,
        Set<Long> roleIds,
        Set<String> systems) {
}
