package com.mfg.qms.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.common.service.P3CrudService;
import com.mfg.qms.service.QmsLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/qms")
@RequiredArgsConstructor
public class QmsP3Controller {
    private final P3CrudService records;
    private final QmsLifecycleService lifecycle;
    private static final Set<String> KINDS = Set.of("standards", "sampling-plans", "ncrs", "capas", "8d");

    private String requireKind(String kind) {
        if (!KINDS.contains(kind)) throw BizException.of(ErrorCode.PARAM_INVALID, "不支持的QMS单据类型");
        return kind;
    }

    @GetMapping("/{kind}")
    @PreAuthorize("hasAuthority('QMS:RECORD:VIEW') or hasRole('ADMIN')")
    public ApiResponse<Page<Map<String, Object>>> page(@PathVariable String kind,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(records.page(requireKind(kind), keyword, status, page, size));
    }

    @GetMapping("/{kind}/{id}")
    @PreAuthorize("hasAuthority('QMS:RECORD:VIEW') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String kind, @PathVariable long id) {
        return ApiResponse.ok(records.detail(requireKind(kind), id));
    }

    @PostMapping("/{kind}")
    @PreAuthorize("hasAuthority('QMS:RECORD:CREATE') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> create(@PathVariable String kind, @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(records.create(requireKind(kind), input));
    }

    @PutMapping("/{kind}/{id}")
    @PreAuthorize("hasAuthority('QMS:RECORD:UPDATE') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> update(@PathVariable String kind, @PathVariable long id,
            @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(records.update(requireKind(kind), id, input));
    }

    @DeleteMapping("/{kind}/{id}")
    @PreAuthorize("hasAuthority('QMS:RECORD:DELETE') or hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String kind, @PathVariable long id) {
        records.delete(requireKind(kind), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{kind}/{id}/status")
    @PreAuthorize("hasAuthority('QMS:RECORD:STATUS') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> changeStatus(@PathVariable String kind, @PathVariable long id,
            @RequestBody Map<String, String> input) {
        String safeKind = requireKind(kind);
        if (!Set.of("standards", "sampling-plans", "capas", "8d").contains(safeKind))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "该质量单据请使用专用评审或验证操作");
        String status = input == null ? null : input.get("status");
        if (status == null || status.isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, "请选择单据状态");
        if (Set.of("VERIFYING", "CLOSED").contains(status))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "验证和关闭请使用对应的闭环操作");
        return ApiResponse.ok(records.status(safeKind, id, status));
    }

    @PostMapping("/ncrs/{id}/dispose")
    @PreAuthorize("hasAuthority('QMS:NCR:DISPOSE') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> dispose(@PathVariable long id, @RequestBody Map<String, String> input) {
        return ApiResponse.ok(lifecycle.dispose(id, input.get("disposition"), input.get("reason")));
    }

    @PostMapping("/capas/{id}/verify")
    @PreAuthorize("hasAuthority('QMS:CAPA:VERIFY') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> verifyCapa(@PathVariable long id) {
        return ApiResponse.ok(lifecycle.verify("capas", id));
    }

    @PostMapping("/capas/{id}/close")
    @PreAuthorize("hasAuthority('QMS:CAPA:CLOSE') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> closeCapa(@PathVariable long id) {
        return ApiResponse.ok(lifecycle.close("capas", id));
    }

    @PostMapping("/8d/{id}/verify")
    @PreAuthorize("hasAuthority('QMS:8D:VERIFY') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> verify8d(@PathVariable long id) {
        return ApiResponse.ok(lifecycle.verify("8d", id));
    }

    @PostMapping("/8d/{id}/close")
    @PreAuthorize("hasAuthority('QMS:8D:CLOSE') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> close8d(@PathVariable long id) {
        return ApiResponse.ok(lifecycle.close("8d", id));
    }
}
