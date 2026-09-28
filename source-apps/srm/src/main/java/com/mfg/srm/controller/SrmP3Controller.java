package com.mfg.srm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.service.P3CrudService;
import com.mfg.common.service.P3FlowService;
import com.mfg.security.config.CurrentUser;
import com.mfg.srm.service.SrmRecordCrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/srm")
@RequiredArgsConstructor
public class SrmP3Controller {
    private static final Set<String> SUPPORTED_KINDS = Set.of(
            "onboarding", "qualifications", "rfqs", "quotes", "purchase-orders", "asns", "supplier-quality", "performance"
    );

    private final P3CrudService records;
    private final P3FlowService flow;
    private final SrmRecordCrudService recordCrud;

    private String kind(String value) {
        if (!SUPPORTED_KINDS.contains(value)) throw new IllegalArgumentException("不支持的SRM对象");
        return value;
    }

    @GetMapping("/{kind}")
    @PreAuthorize("hasAuthority('SRM:RECORD:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> page(
            @PathVariable String kind,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(records.page(kind(kind), keyword, status, page, size));
    }

    @GetMapping("/{kind}/{id}")
    @PreAuthorize("hasAuthority('SRM:RECORD:VIEW')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String kind, @PathVariable long id) {
        return ApiResponse.ok(records.detail(kind(kind), id));
    }

    @PostMapping("/{kind}")
    @PreAuthorize("hasAuthority('SRM:RECORD:CREATE')")
    public ApiResponse<Map<String, Object>> create(@PathVariable String kind, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(recordCrud.create(kind(kind), body));
    }

    @PutMapping("/{kind}/{id}")
    @PreAuthorize("hasAuthority('SRM:RECORD:UPDATE')")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(recordCrud.update(kind(kind), id, body));
    }

    @DeleteMapping("/{kind}/{id}")
    @PreAuthorize("hasAuthority('SRM:RECORD:DELETE')")
    public ApiResponse<Void> delete(@PathVariable String kind, @PathVariable long id) {
        recordCrud.delete(kind(kind), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{kind}/{id}/status")
    @PreAuthorize("hasAuthority('SRM:RECORD:UPDATE')")
    public ApiResponse<Map<String, Object>> status(
            @PathVariable String kind, @PathVariable long id, @RequestBody Map<String, Object> body) {
        Object value = body.get("status");
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("请提供目标状态");
        return ApiResponse.ok(records.status(kind(kind), id, String.valueOf(value)));
    }

    @PostMapping("/rfqs/{id}/award")
    @PreAuthorize("hasAuthority('SRM:RFQ:AWARD')")
    public ApiResponse<Map<String, Object>> award(@PathVariable long id, @RequestParam long quoteId) {
        records.detail("rfqs", id);
        return ApiResponse.ok(flow.awardRfq(id, quoteId, CurrentUser.usernameOrSystem()));
    }

    @PostMapping("/asns/{id}/arrive")
    @PreAuthorize("hasAuthority('SRM:ASN:ARRIVE')")
    public ApiResponse<Map<String, Object>> arrive(
            @PathVariable long id, @RequestParam(defaultValue = "WH01") String warehouseCode) {
        records.detail("asns", id);
        return ApiResponse.ok(flow.arriveAsn(id, warehouseCode));
    }
}
