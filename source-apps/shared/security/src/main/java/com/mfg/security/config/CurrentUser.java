package com.mfg.security.config;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.principal.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 当前登录用户的静态访问入口。
 *
 * <p>用法：
 * <pre>{@code
 * LoginUser me = CurrentUser.get();              // 未登录抛异常
 * Optional<LoginUser> maybe = CurrentUser.find(); // 未登录返回空
 * }</pre>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<LoginUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        return principal instanceof LoginUser u ? Optional.of(u) : Optional.empty();
    }

    public static LoginUser get() {
        return find().orElseThrow(() -> BizException.of(ErrorCode.UNAUTHORIZED));
    }

    /** 当前用户名，未登录时返回 {@code system}（供定时任务等无上下文场景使用） */
    public static String usernameOrSystem() {
        return find().map(LoginUser::getUsername).orElse("system");
    }
}
