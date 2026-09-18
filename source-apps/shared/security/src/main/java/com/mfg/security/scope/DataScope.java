package com.mfg.security.scope;

import java.lang.annotation.*;

/**
 * 数据范围声明（第二层权限）。
 *
 * <p>标在查询方法上，配合 {@link DataScopeAspect} 自动把
 * 「当前用户只能看哪些数据」翻译成 SQL 的 WHERE 条件。
 *
 * <p>用法：
 * <pre>{@code
 * @DataScope(resource = "CUSTOMER", userField = "created_by", deptField = "dept_code")
 * public List<Customer> listAll() { ... }
 * }</pre>
 *
 * <p>三种范围（由 {@code sys_data_scope} 表配置，按角色生效）：
 * <ul>
 *   <li>{@code ALL} —— 不加条件</li>
 *   <li>{@code DEPT} —— 限定 {@code deptField = 当前用户部门}</li>
 *   <li>{@code SELF} —— 限定 {@code userField = 当前用户}</li>
 * </ul>
 *
 * <p>取最宽松的范围：用户有多个角色时，任一角色是 ALL 则看全部。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 资源编码，对应 sys_data_scope.resource_code，如 CUSTOMER、WORK_ORDER */
    String resource();

    /** 用户维度字段名（SELF 范围用），对应实体中的属性名或列名 */
    String userField() default "created_by";

    /** 部门维度字段名（DEPT 范围用） */
    String deptField() default "dept_code";

    /** 是否对当前用户禁用范围过滤（如管理员查看） */
    boolean ignoreAdmin() default false;
}
