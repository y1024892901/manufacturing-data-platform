package com.mfg.energy.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.energy.entity.EnergyMeter;
import com.mfg.energy.service.EnergyMeterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/energy/meters")
@RequiredArgsConstructor
public class EnergyMeterController {
    private final EnergyMeterService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ENERGY:METER:VIEW')")
    public ApiResponse<List<EnergyMeter>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:METER:VIEW')")
    public ApiResponse<EnergyMeter> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('ENERGY:METER:CREATE')")
    public ApiResponse<EnergyMeter> create(@RequestBody EnergyMeter meter) { return ApiResponse.ok(service.create(meter)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:METER:UPDATE')")
    public ApiResponse<EnergyMeter> update(@PathVariable Long id, @RequestBody EnergyMeter meter) { return ApiResponse.ok(service.update(id, meter)); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ENERGY:METER:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
