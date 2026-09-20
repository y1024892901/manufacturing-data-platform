package com.mfg.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_audit_log")
@Getter @Setter @NoArgsConstructor
public class SysAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "operator", nullable = false, length = 32) private String operator;
    @Column(name = "operator_name", length = 50) private String operatorName;
    @Column(name = "system_code", nullable = false, length = 16) private String systemCode;
    @Column(name = "action", nullable = false, length = 32) private String action;
    @Column(name = "object_type", nullable = false, length = 32) private String objectType;
    @Column(name = "object_id", length = 64) private String objectId;
    @Column(name = "object_name", length = 200) private String objectName;
    @Column(name = "before_value", columnDefinition = "json") private String beforeValue;
    @Column(name = "after_value", columnDefinition = "json") private String afterValue;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "operated_at", nullable = false) private LocalDateTime operatedAt;
}
