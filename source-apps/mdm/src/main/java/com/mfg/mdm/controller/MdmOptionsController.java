package com.mfg.mdm.controller;

import com.mfg.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Read-only MDM selections shared by every authenticated business system. */
@RestController
@RequestMapping("/api/mdm/options")
@RequiredArgsConstructor
public class MdmOptionsController {
    private final JdbcTemplate jdbc;

    @GetMapping("/materials")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> materials() {
        return ApiResponse.ok(jdbc.queryForList("""
                SELECT m.material_code, m.material_name, m.material_spec, m.material_type,
                       m.category_id, m.base_unit_code, m.purchase_unit_code,
                       m.conversion_rate, m.standard_price, m.version_no,
                       c.category_code, c.category_name, c.category_path
                FROM src_mdm.md_material m
                LEFT JOIN src_mdm.md_material_category c ON c.id=m.category_id
                WHERE m.status IN ('PUBLISHED','CHANGING')
                ORDER BY m.material_code
                LIMIT 2000
                """));
    }

    @GetMapping("/units")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> units() {
        return ApiResponse.ok(jdbc.queryForList("""
                SELECT id, unit_code, unit_name, unit_type, base_unit_code, convert_rate, version_no
                FROM src_mdm.md_unit
                WHERE status='PUBLISHED'
                ORDER BY unit_type, unit_code
                LIMIT 1000
                """));
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(jdbc.queryForList("""
                SELECT id, category_code, category_name, parent_id, category_level,
                       is_leaf, category_path, version_no
                FROM src_mdm.md_material_category
                WHERE status='PUBLISHED' AND is_leaf=1
                ORDER BY category_path, category_code
                LIMIT 1000
                """));
    }
}
