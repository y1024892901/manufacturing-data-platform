package com.mfg.energy.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.energy.entity.EquipmentUsage;
import com.mfg.energy.service.EquipmentUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/energy/usages")
@RequiredArgsConstructor
public class EquipmentUsageController {
    private final EquipmentUsageService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ENERGY:USAGE:VIEW')")
    public ApiResponse<List<EquipmentUsage>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:USAGE:VIEW')")
    public ApiResponse<EquipmentUsage> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('ENERGY:USAGE:CREATE')")
    public ApiResponse<EquipmentUsage> create(@RequestBody EquipmentUsage usage) { return ApiResponse.ok(service.create(usage)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:USAGE:UPDATE')")
    public ApiResponse<EquipmentUsage> update(@PathVariable Long id, @RequestBody EquipmentUsage usage) { return ApiResponse.ok(service.update(id, usage)); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:USAGE:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
