package com.mfg.energy.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.energy.entity.WorkshopUsage;
import com.mfg.energy.repo.WorkshopUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** 车间能耗以“车间 + 日期 + 介质”去重，单位能耗由能耗/产出自动生成。 */
@RestController @RequestMapping("/api/energy/workshop-usages") @RequiredArgsConstructor
public class WorkshopUsageController {
    private final WorkshopUsageRepository usages;
    @GetMapping @PreAuthorize("hasAuthority('ENERGY:WORKSHOP_USAGE:VIEW')") public ApiResponse<List<WorkshopUsage>> list() { return ApiResponse.ok(usages.findAll()); }
    @PostMapping @PreAuthorize("hasAuthority('ENERGY:WORKSHOP_USAGE:CREATE')") public ApiResponse<WorkshopUsage> create(@RequestBody WorkshopUsage usage) {
        usage.setStatDate(usage.getStatDate() == null ? LocalDate.now() : usage.getStatDate());
        usage.setEnergyType(usage.getEnergyType() == null ? "ELECTRIC" : usage.getEnergyType());
        usage.setUnitCode(usage.getUnitCode() == null ? "KWH" : usage.getUnitCode());
        if (usage.getEnergyValue() == null || usage.getEnergyValue().signum() < 0) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "能耗量必须为非负数");
        if (usages.existsByWorkshopCodeAndStatDateAndEnergyType(usage.getWorkshopCode(), usage.getStatDate(), usage.getEnergyType())) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "该车间日期与能源介质已有能耗记录");
        if (usage.getTotalOutput() != null && usage.getTotalOutput().signum() > 0) usage.setUnitConsumption(usage.getEnergyValue().divide(usage.getTotalOutput(), 6, RoundingMode.HALF_UP));
        return ApiResponse.ok(usages.save(usage));
    }
}
