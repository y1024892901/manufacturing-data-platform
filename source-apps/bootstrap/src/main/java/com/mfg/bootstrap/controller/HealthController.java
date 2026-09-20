package com.mfg.bootstrap.controller;

import com.mfg.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 健康检查与服务概览。
 *
 * <p>除了存活探测，还顺带统计 18 个库的表数量——
 * 演示开场时打开这个接口，一眼能看出"10 个系统 + 数仓四层都在"。
 */
@Tag(name = "00. 健康检查", description = "服务存活与数据库概览")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "健康检查")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("time", LocalDateTime.now());
        result.put("service", "mfg-source-apps");

        try {
            String db = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            result.put("database", db);
            result.put("mysqlVersion", version);
        } catch (Exception e) {
            result.put("status", "DEGRADED");
            result.put("dbError", e.getMessage());
        }
        return ApiResponse.ok(result);
    }

    @Operation(summary = "数据库概览", description = "列出全部 18 个库及其表数量，演示开场用")
    @GetMapping("/databases")
    public ApiResponse<Page<Map<String, Object>>> databases(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize = Math.min(100, Math.max(1, size));
        String sql = """
                SELECT
                    TABLE_SCHEMA AS dbName,
                    COUNT(*)     AS tableCount,
                    CASE
                        WHEN TABLE_SCHEMA = 'src_mdm' THEN '① 统一主数据平台'
                        WHEN TABLE_SCHEMA LIKE 'src\\_%' THEN '② 业务系统'
                        WHEN TABLE_SCHEMA IN ('mfg_ods','mfg_dwd','mfg_dws','mfg_ads') THEN '③ 数仓四层'
                        ELSE '④ 平台能力'
                    END AS layer
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA LIKE 'src\\_%' OR TABLE_SCHEMA LIKE 'mfg\\_%'
                GROUP BY TABLE_SCHEMA
                ORDER BY layer, dbName
                LIMIT ? OFFSET ?
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, pageSize, pageIndex * pageSize);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT TABLE_SCHEMA)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA LIKE 'src\\_%' OR TABLE_SCHEMA LIKE 'mfg\\_%'
                """, Long.class);
        return ApiResponse.ok(new PageImpl<>(rows, PageRequest.of(pageIndex, pageSize), total == null ? 0 : total));
    }
}
