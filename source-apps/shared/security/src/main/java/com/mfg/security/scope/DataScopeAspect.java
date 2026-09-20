package com.mfg.security.scope;

import com.mfg.security.config.CurrentUser;
import com.mfg.security.principal.LoginUser;
import com.mfg.security.scope.DataScopeContext.ScopeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据范围切面。
 *
 * <p>在标注了 {@link DataScope} 的方法执行前，查询当前用户的角色对应的
 * 数据范围规则（{@code sys_data_scope} 表），取其中最宽松的一条，
 * 写入 {@link DataScopeContext}，执行后清理。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final JdbcTemplate jdbcTemplate;

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        Optional<LoginUser> maybeUser = CurrentUser.find();

        // 无登录上下文（定时任务、内部调用）→ 不受限
        if (maybeUser.isEmpty()) {
            return pjp.proceed();
        }

        LoginUser user = maybeUser.get();

        // 管理员豁免（除非显式要求对管理员也生效）
        if (user.isAdmin() && !dataScope.ignoreAdmin()) {
            DataScopeContext.set(DataScopeContext.builder()
                    .type(ScopeType.ALL)
                    .resource(dataScope.resource())
                    .userField(dataScope.userField())
                    .deptField(dataScope.deptField())
                    .build());
            try {
                return pjp.proceed();
            } finally {
                DataScopeContext.clear();
            }
        }

        ScopeType scope = resolveScope(user, dataScope.resource());

        DataScopeContext ctx = DataScopeContext.builder()
                .type(scope)
                .resource(dataScope.resource())
                .username(user.getUsername())
                .deptCode(user.getDeptCode())
                .userField(dataScope.userField())
                .deptField(dataScope.deptField())
                .build();

        DataScopeContext.set(ctx);
        log.debug("数据范围: user={} resource={} scope={}",
                user.getUsername(), dataScope.resource(), scope);
        try {
            return pjp.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    /**
     * 计算用户在指定资源上的最宽松范围。
     *
     * <p>「最宽松」而非「最严格」：一个用户可能身兼多职（如工艺主管兼研发经理），
     * 只要任一角色允许看全部，就应该能看全部，否则会出现
     * 「升职后反而看不到数据」的荒谬情况。
     */
    private ScopeType resolveScope(LoginUser user, String resource) {
        if (user.getDataScopeType() != null && !"ROLE".equals(user.getDataScopeType())) {
            try { return ScopeType.valueOf(user.getDataScopeType()); }
            catch (IllegalArgumentException ex) { return ScopeType.NONE; }
        }
        if (user.getRoleCodes().isEmpty()) {
            return ScopeType.NONE;
        }

        String placeholders = String.join(",",
                java.util.Collections.nCopies(user.getRoleCodes().size(), "?"));

        String sql = """
                SELECT s.scope_type
                FROM mfg_auth.sys_data_scope s
                JOIN mfg_auth.sys_role r ON r.id = s.role_id
                WHERE r.role_code IN (%s) AND s.resource_code = ?
                """.formatted(placeholders);

        List<String> params = new java.util.ArrayList<>(user.getRoleCodes());
        params.add(resource);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

        if (rows.isEmpty()) {
            // 未配置规则 → 不受限（宽松默认，便于新资源上线时不被卡住）
            return ScopeType.ALL;
        }

        boolean hasAll = false;
        boolean hasDept = false;
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("scope_type"));
            if ("ALL".equals(type)) {
                hasAll = true;
            } else if ("DEPT".equals(type)) {
                hasDept = true;
            }
        }

        if (hasAll) {
            return ScopeType.ALL;
        }
        return hasDept ? ScopeType.DEPT : ScopeType.SELF;
    }
}
