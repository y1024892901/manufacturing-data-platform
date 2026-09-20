package com.mfg.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentCommand(
        @NotBlank @Size(max = 32) String deptCode,
        @NotBlank @Size(max = 100) String deptName,
        @Size(max = 32) String parentCode,
        Integer deptLevel,
        @Size(max = 32) String managerUser,
        Integer sortNo,
        Boolean enabled) {}
