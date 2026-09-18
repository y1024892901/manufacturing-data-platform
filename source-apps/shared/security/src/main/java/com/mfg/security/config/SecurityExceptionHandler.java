package com.mfg.security.config;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 安全相关异常处理。
 *
 * <p>为什么单独放在 security 模块而不是 common 模块的
 * {@code GlobalExceptionHandler}：
 * 「异常处理器必须在能看到异常类型的模块里」。{@code AccessDeniedException}
 * 来自 spring-security，而 common 模块不依赖 security（否则
 * common → security → common 会形成循环依赖）。
 *
 * <p>不处理会怎样：{@code @PreAuthorize} 拦截后抛出的 AccessDeniedException
 * 会被兜底的 {@code Exception} 处理器捕获，返回「90001 系统内部错误」，
 * 前端看到的是系统故障而非权限不足——**错误语义完全错了**。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SecurityExceptionHandler {

    /**
     * 方法级鉴权失败：{@code @PreAuthorize} 不通过。
     *
     * <p>返回 20002 而非 500，并给出"需要什么权限"的提示，
     * 便于演示时直观展示权限边界。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ApiResponse.fail(ErrorCode.FORBIDDEN,
                "无权限执行此操作，请联系管理员分配权限");
    }

    /**
     * 认证失败（令牌无效、过期等）。
     *
     * <p>多数情况下被 {@code SecurityConfig} 中的
     * {@code authenticationEntryPoint} 提前拦截，这里兜底。
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthentication(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return ApiResponse.fail(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
    }
}
