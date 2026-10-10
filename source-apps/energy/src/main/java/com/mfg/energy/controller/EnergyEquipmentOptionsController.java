package com.mfg.energy.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.energy.service.EnergyEquipmentDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/energy/equipment-options")
@RequiredArgsConstructor
public class EnergyEquipmentOptionsController {
    private final EnergyEquipmentDirectory equipmentDirectory;

    @GetMapping
    @PreAuthorize("hasAuthority('ENERGY:USAGE:VIEW') or hasAuthority('ENERGY:METER:VIEW')")
    public ApiResponse<List<EnergyEquipmentDirectory.EquipmentOption>> list() {
        return ApiResponse.ok(equipmentDirectory.listUsable());
    }
}
