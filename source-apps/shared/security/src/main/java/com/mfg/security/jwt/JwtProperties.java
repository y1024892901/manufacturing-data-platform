package com.mfg.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项（前缀 {@code mfg.jwt}）。
 *
 * <pre>
 * mfg:
 *   jwt:
 *     secret: 至少 32 字节的随机串
 *     expire-minutes: 480        # 演示用 8 小时，避免演示中途掉登录
 *     header: Authorization
 *     prefix: "Bearer "
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mfg.jwt")
public class JwtProperties {

    /** 签名密钥。生产环境必须从环境变量注入，不可硬编码。 */
    private String secret = "mfg-data-platform-demo-secret-key-change-me-in-production-2026";

    /** 令牌有效期（分钟）。演示场景设长一些，避免中途掉登录。 */
    private long expireMinutes = 480;

    private String header = "Authorization";

    private String prefix = "Bearer ";

    /** 签发者标识 */
    private String issuer = "mfg-source-apps";
}
