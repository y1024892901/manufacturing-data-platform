package com.mfg.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_login_log")
@Getter @Setter @NoArgsConstructor
public class SysLoginLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "username", nullable = false, length = 32)
    private String username;
    @Column(name = "real_name", length = 50)
    private String realName;
    @Column(name = "login_status", nullable = false, length = 16)
    private String loginStatus;
    @Column(name = "fail_reason", length = 200)
    private String failReason;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;
}
