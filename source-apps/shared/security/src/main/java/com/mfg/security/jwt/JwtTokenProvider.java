package com.mfg.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 令牌签发与解析。
 *
 * <p>令牌中携带三部分信息，让每次请求无需查库即可完成大部分权限判断：
 * <ul>
 *   <li>{@code sub} —— 用户名</li>
 *   <li>{@code roles} —— 角色编码列表（审批引擎靠它找待办）</li>
 *   <li>{@code systems} —— 可访问系统列表（第一层权限）</li>
 * </ul>
 *
 * <p>注意：权限点（permission）不放进令牌，因为数量多且可能变更，
 * 由 {@code UserDetailsService} 在认证时加载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties props;

    private SecretKey key;

    private SecretKey key() {
        if (key == null) {
            byte[] bytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException(
                        "mfg.jwt.secret 长度不足 32 字节，HS256 要求至少 256 位密钥");
            }
            key = Keys.hmacShaKeyFor(bytes);
        }
        return key;
    }

    /**
     * 签发令牌。
     *
     * @param username 用户名
     * @param realName 姓名（前端展示用，避免每次再查库）
     * @param roles    角色编码列表
     * @param systems  可访问系统列表
     */
    public String generate(String username, String realName,
                           List<String> roles, List<String> systems) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getExpireMinutes() * 60);
        return Jwts.builder()
                .subject(username)
                .issuer(props.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of(
                        "realName", realName == null ? "" : realName,
                        "roles", roles == null ? List.of() : roles,
                        "systems", systems == null ? List.of() : systems))
                .signWith(key())
                .compact();
    }

    /** 解析令牌，失败返回 null（由调用方决定如何处理） */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .requireIssuer(props.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public String getUsername(String token) {
        Claims c = parse(token);
        return c == null ? null : c.getSubject();
    }

    /** 从请求头原始值中剥离前缀，取出纯令牌 */
    public String resolveToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String prefix = props.getPrefix();
        if (prefix != null && !prefix.isBlank() && headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length()).trim();
        }
        return headerValue.trim();
    }

    public String getHeader() {
        return props.getHeader();
    }

    public long getExpireMinutes() {
        return props.getExpireMinutes();
    }
}
