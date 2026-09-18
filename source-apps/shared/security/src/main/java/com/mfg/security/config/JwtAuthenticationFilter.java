package com.mfg.security.config;

import com.mfg.security.entity.SysUser;
import com.mfg.security.jwt.JwtTokenProvider;
import com.mfg.security.principal.LoginUser;
import com.mfg.security.repo.SysUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>流程：请求头取令牌 → 解析 → 查库加载用户权限 → 写入 SecurityContext。
 *
 * <p>为什么每次都查库而不是只用令牌里的信息：
 * 权限点（permission）可能被管理员变更，若全部塞进令牌，
 * 变更后需要等令牌过期才生效。角色与系统访问权放在令牌里用于快速判断，
 * 权限点实时查库保证变更即时生效——演示时改权限能立刻看到效果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final SysUserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String token = tokenProvider.resolveToken(request.getHeader(tokenProvider.getHeader()));

        // 已认证则跳过（避免重复构造）
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims = tokenProvider.parse(token);
        if (claims == null) {
            chain.doFilter(request, response);
            return;
        }

        String username = claims.getSubject();
        SysUser user = userRepository.findByUsername(username).orElse(null);

        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            log.debug("用户不存在或已停用: {}", username);
            chain.doFilter(request, response);
            return;
        }

        LoginUser principal = toPrincipal(user);

        // 权限点 → Spring Security 的 authority，供 @PreAuthorize 使用
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        principal.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        // 角色加 ROLE_ 前缀，供 hasRole() 使用
        principal.getRoleCodes().forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    private LoginUser toPrincipal(SysUser user) {
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptCode(),
                user.getPositionName(),
                user.roleCodes(),
                user.getRoles().stream()
                        .flatMap(r -> r.permissionCodes().stream())
                        .collect(java.util.stream.Collectors.toSet()),
                user.getSystems());
    }
}
