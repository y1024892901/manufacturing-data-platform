package com.mfg.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 部门（对应 mfg_auth.sys_dept）。
 *
 * <p>9 大中心构成组织架构，是数据范围 DEPT 级过滤的依据：
 * 销售代表看本部门客户、车间主任看本车间工单。
 */
@Entity
@Table(name = "sys_dept")
@Getter
@Setter
@NoArgsConstructor
public class SysDept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dept_code", nullable = false, unique = true, length = 32)
    private String deptCode;

    @Column(name = "dept_name", nullable = false, length = 100)
    private String deptName;

    @Column(name = "parent_code", length = 32)
    private String parentCode;

    @Column(name = "dept_level", nullable = false)
    private Integer deptLevel = 1;

    @Column(name = "manager_user", length = 32)
    private String managerUser;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = true;
}
