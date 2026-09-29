package com.mfg.plm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.plm.service.EngineeringChangeChainService;
import com.mfg.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plm")
@RequiredArgsConstructor
public class EngineeringChangeChainController {
    private final EngineeringChangeChainService service;

    @GetMapping("/{type:ecrs|ecos}")
    @PreAuthorize("hasAuthority('PLM:CHANGE:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> page(
            @PathVariable String type, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.page(type, keyword, page, size));
    }

    @GetMapping("/{type:ecrs|ecos}/{id}")
    @PreAuthorize("hasAuthority('PLM:CHANGE:VIEW')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String type, @PathVariable Long id) {
        return ApiResponse.ok(service.detail(type, id));
    }

    @GetMapping("/ecrs/{id}/impacts")
    @PreAuthorize("hasAuthority('PLM:CHANGE:VIEW')")
    public ApiResponse<List<Map<String, Object>>> impactDetails(@PathVariable Long id) {
        return ApiResponse.ok(service.impactDetails(id));
    }

    @PostMapping("/ecrs")
    @PreAuthorize("hasAuthority('PLM:ECR:CREATE')")
    public ApiResponse<Map<String, Object>> createEcr(@RequestBody Map<String, Object> input) {
        return ApiResponse.ok(service.createEcr(input));
    }

    @PutMapping("/ecrs/{id}")
    @PreAuthorize("hasAuthority('PLM:ECR:UPDATE')")
    public ApiResponse<Map<String, Object>> updateEcr(@PathVariable Long id, @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(service.updateEcr(id, input));
    }

    @DeleteMapping("/ecrs/{id}")
    @PreAuthorize("hasAuthority('PLM:ECR:DELETE')")
    public ApiResponse<Void> deleteEcr(@PathVariable Long id) {
        service.deleteEcr(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/ecrs/{id}/impact-analysis")
    @PreAuthorize("hasAuthority('PLM:ECR:UPDATE')")
    public ApiResponse<List<Map<String, Object>>> impacts(@PathVariable Long id, @RequestBody List<Map<String, Object>> input) {
        return ApiResponse.ok(service.impacts(id, input));
    }

    @PostMapping("/ecrs/{id}/create-eco")
    @PreAuthorize("hasAuthority('PLM:ECO:CREATE')")
    public ApiResponse<Map<String, Object>> createEco(@PathVariable Long id, @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(service.createEco(id, input));
    }

    @PutMapping("/ecos/{id}")
    @PreAuthorize("hasAuthority('PLM:ECO:UPDATE')")
    public ApiResponse<Map<String, Object>> updateEco(@PathVariable Long id, @RequestBody Map<String, Object> input) {
        return ApiResponse.ok(service.updateEco(id, input));
    }

    @DeleteMapping("/ecos/{id}")
    @PreAuthorize("hasAuthority('PLM:ECO:DELETE')")
    public ApiResponse<Void> deleteEco(@PathVariable Long id) {
        service.deleteEco(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/ecos/{id}/submit")
    @PreAuthorize("hasAuthority('PLM:ECO:SUBMIT')")
    public ApiResponse<WfInstance> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submitEco(id));
    }

    @PostMapping("/ecos/{id}/create-ecn")
    @PreAuthorize("hasAuthority('PLM:ECN:CREATE')")
    public ApiResponse<Map<String, Object>> ecn(@PathVariable Long id) {
        return ApiResponse.ok(service.createEcnFromEco(id));
    }

    @PostMapping("/ecns/{id}/implement")
    @PreAuthorize("hasAuthority('PLM:ECN:IMPLEMENT')")
    public ApiResponse<Map<String, Object>> implement(@PathVariable Long id) {
        return ApiResponse.ok(service.implement(id));
    }
}
