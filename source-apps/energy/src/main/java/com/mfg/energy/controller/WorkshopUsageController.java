package com.mfg.energy.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.energy.entity.WorkshopUsage;
import com.mfg.energy.service.WorkshopUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/energy/workshop-usages")
@RequiredArgsConstructor
public class WorkshopUsageController {
    private final WorkshopUsageService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ENERGY:WORKSHOP_USAGE:VIEW')")
    public ApiResponse<List<WorkshopUsage>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:WORKSHOP_USAGE:VIEW')")
    public ApiResponse<WorkshopUsage> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('ENERGY:WORKSHOP_USAGE:CREATE')")
    public ApiResponse<WorkshopUsage> create(@RequestBody WorkshopUsage usage) { return ApiResponse.ok(service.create(usage)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:WORKSHOP_USAGE:UPDATE')")
    public ApiResponse<WorkshopUsage> update(@PathVariable Long id, @RequestBody WorkshopUsage usage) { return ApiResponse.ok(service.update(id, usage)); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:WORKSHOP_USAGE:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
