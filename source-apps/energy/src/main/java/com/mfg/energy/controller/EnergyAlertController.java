package com.mfg.energy.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.energy.entity.EnergyAlert;
import com.mfg.energy.service.EnergyAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/energy/alerts")
@RequiredArgsConstructor
public class EnergyAlertController {
    private final EnergyAlertService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ENERGY:ALERT:VIEW')")
    public ApiResponse<List<EnergyAlert>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:ALERT:VIEW')")
    public ApiResponse<EnergyAlert> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('ENERGY:ALERT:CREATE')")
    public ApiResponse<EnergyAlert> create(@RequestBody EnergyAlert alert) { return ApiResponse.ok(service.create(alert)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:ALERT:UPDATE')")
    public ApiResponse<EnergyAlert> update(@PathVariable Long id, @RequestBody EnergyAlert alert) { return ApiResponse.ok(service.update(id, alert)); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:ALERT:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
