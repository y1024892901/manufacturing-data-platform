package com.mfg.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record RoleCommand(
        @NotBlank @Size(max = 32) String roleCode,
        @NotBlank @Size(max = 50) String roleName,
        @Size(max = 200) String description,
        Integer sortNo,
        Set<Long> permissionIds) {}
