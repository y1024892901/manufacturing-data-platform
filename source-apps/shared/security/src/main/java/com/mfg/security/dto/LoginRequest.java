package com.mfg.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求")
public record LoginRequest(
        @Schema(description = "用户名", example = "zhangwei")
        @NotBlank(message = "用户名不能为空")
        String username,

        @Schema(description = "密码", example = "Test@123456")
        @NotBlank(message = "密码不能为空")
        String password
) {
}
