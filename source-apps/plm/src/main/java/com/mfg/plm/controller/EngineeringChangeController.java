package com.mfg.plm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.plm.entity.EngineeringChange;
import com.mfg.plm.repo.EngineeringChangeRepository;
import com.mfg.plm.service.EngineeringChangeService;
import com.mfg.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plm/ecns")
@RequiredArgsConstructor
public class EngineeringChangeController {
    private final EngineeringChangeRepository repo;
    private final EngineeringChangeService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PLM:ECN:VIEW')")
    public ApiResponse<Page<EngineeringChange>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 200));
        Page<EngineeringChange> result = keyword == null || keyword.isBlank()
                ? repo.findAll(pageable)
                : repo.findByEcnNoContainingIgnoreCaseOrEcnTitleContainingIgnoreCase(keyword.trim(), keyword.trim(), pageable);
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PLM:ECN:VIEW')")
    public ApiResponse<EngineeringChange> detail(@PathVariable Long id) {
        return ApiResponse.ok(repo.findById(id).orElseThrow(() -> com.mfg.common.exception.BizException.notFound("变更通知", id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PLM:ECN:CREATE')")
    public ApiResponse<EngineeringChange> create(@RequestBody EngineeringChange e) {
        return ApiResponse.ok(service.create(e));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PLM:ECN:UPDATE')")
    public ApiResponse<EngineeringChange> update(@PathVariable Long id, @RequestBody EngineeringChange e) {
        return ApiResponse.ok(service.update(id, e));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PLM:ECN:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('PLM:ECN:CREATE')")
    public ApiResponse<WfInstance> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }
}
