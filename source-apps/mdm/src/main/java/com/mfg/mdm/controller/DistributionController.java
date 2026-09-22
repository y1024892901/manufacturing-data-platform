package com.mfg.mdm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.mdm.service.DistributionMonitorService;
import com.mfg.mdm.service.MasterDataDistributor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/mdm/distributions")
@RequiredArgsConstructor
public class DistributionController {
    private final DistributionMonitorService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MDM:MATERIAL:VIEW','MDM:BOM:VIEW') or hasRole('ADMIN')")
    public ApiResponse<Page<Map<String, Object>>> latest(@RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.latest(status, page, size));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyAuthority('MDM:MATERIAL:PUBLISH','MDM:BOM:PUBLISH') or hasRole('ADMIN')")
    public ApiResponse<MasterDataDistributor.DistResult> retry(@PathVariable Long id) {
        return ApiResponse.ok(service.retry(id));
    }

    private final com.mfg.mdm.service.MdmOutboxService outbox;
    @GetMapping("/events") public ApiResponse<org.springframework.data.domain.Page<Map<String,Object>>> events(@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(outbox.page(status,page,size));}
    @GetMapping("/events/{eventId}") public ApiResponse<Map<String,Object>> event(@PathVariable String eventId){return ApiResponse.ok(outbox.detail(eventId));}
    @PostMapping("/events/{eventId}/retry") public ApiResponse<Void> retryEvent(@PathVariable String eventId){outbox.retry(eventId);return ApiResponse.ok();}
    @PostMapping("/events/{eventId}/reconcile") public ApiResponse<Map<String,Object>> reconcile(@PathVariable String eventId){return ApiResponse.ok(outbox.reconcileEvent(eventId));}
}
