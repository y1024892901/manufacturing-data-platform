package com.mfg.mes.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.mes.entity.WorkReport;
import com.mfg.mes.service.WorkReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mes/reports")
@RequiredArgsConstructor
public class WorkReportController {
    private final WorkReportService reports;

    @GetMapping
    @PreAuthorize("hasAuthority('MES:WORK_REPORT:VIEW')")
    public ApiResponse<Page<WorkReport>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reports.page(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:WORK_REPORT:VIEW')")
    public ApiResponse<WorkReport> get(@PathVariable Long id) {
        return ApiResponse.ok(reports.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MES:WORK_REPORT:CREATE')")
    public ApiResponse<WorkReport> create(@RequestBody WorkReport input) {
        return ApiResponse.ok(reports.create(input));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:WORK_REPORT:UPDATE')")
    public ApiResponse<WorkReport> update(@PathVariable Long id, @RequestBody WorkReport input) {
        return ApiResponse.ok(reports.update(id, input));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:WORK_REPORT:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        reports.delete(id);
        return ApiResponse.ok();
    }
}
