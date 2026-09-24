package com.mfg.security.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.dto.DemoAccount;
import com.mfg.security.dto.LoginRequest;
import com.mfg.security.dto.LoginResponse;
import com.mfg.security.principal.LoginUser;
import com.mfg.security.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证接口。
 *
 * <p>演示时先访问 {@code /api/auth/demo-accounts} 拿到账号清单，
 * 在登录页做成一排「身份卡片」，点谁就切谁。
 */
@Tag(name = "01. 认证", description = "登录、当前用户、演示账号清单")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "返回 JWT 与用户完整的角色/权限/可访问系统信息")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String clientIp = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return ApiResponse.ok(authService.login(req, clientIp, request.getHeader("User-Agent")));
    }

    @Operation(summary = "演示账号清单", description = "按部门列出全部演示账号，供登录页一键切换身份")
    @GetMapping("/demo-accounts")
    public ApiResponse<List<DemoAccount>> demoAccounts() {
        return ApiResponse.ok(authService.demoAccounts());
    }

    @Operation(summary = "当前登录用户", description = "返回当前身份的角色、权限与可访问系统")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<LoginUser> me() {
        return ApiResponse.ok(CurrentUser.get());
    }

    @Operation(summary = "登出", description = "无状态 JWT，服务端仅记录日志，前端丢弃令牌即可")
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> logout() {
        // 无状态令牌无需服务端注销；此处保留接口以便前端统一调用
        return ApiResponse.ok();
    }
}
