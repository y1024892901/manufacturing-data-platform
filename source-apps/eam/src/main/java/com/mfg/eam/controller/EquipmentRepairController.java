package com.mfg.eam.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.eam.entity.EquipmentRepair;
import com.mfg.eam.repo.EquipmentFaultRepository;
import com.mfg.eam.repo.EquipmentRepairRepository;
import com.mfg.eam.repo.EquipmentRepository;
import com.mfg.security.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** 故障 -> 维修 -> 恢复设备状态的 EAM 闭环。 */
@RestController @RequestMapping("/api/eam/repairs") @RequiredArgsConstructor
public class EquipmentRepairController {
    private final EquipmentRepairRepository repairs;
    private final EquipmentFaultRepository faults;
    private final EquipmentRepository equipments;

    @GetMapping @PreAuthorize("hasAuthority('EAM:REPAIR:VIEW')") public ApiResponse<Page<EquipmentRepair>> page(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(repairs.findAll(PageRequest.of(Math.max(0, page - 1), Math.min(200, size))));
    }

    @PostMapping @PreAuthorize("hasAuthority('EAM:REPAIR:CREATE')") public ApiResponse<EquipmentRepair> create(@RequestBody EquipmentRepair repair) {
        if (repairs.existsByRepairNo(repair.getRepairNo())) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "维修单号已存在");
        var fault = faults.findAll().stream().filter(f -> repair.getFaultNo().equals(f.getFaultNo())).findFirst()
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联故障单不存在"));
        if (!fault.getEquipmentCode().equals(repair.getEquipmentCode())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "维修设备必须与故障设备一致");
        if (!"OPEN".equals(fault.getStatus())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有未关闭故障可创建维修单");
        repair.setRepairStartTime(repair.getRepairStartTime() == null ? LocalDateTime.now() : repair.getRepairStartTime());
        repair.setRepairmanCode(CurrentUser.usernameOrSystem());
        repair.setRepairResult("REPAIRING");
        return ApiResponse.ok(repairs.save(repair));
    }

    @PostMapping("/{id}/complete") @PreAuthorize("hasAuthority('EAM:REPAIR:FINISH')") public ApiResponse<EquipmentRepair> complete(@PathVariable Long id, @RequestParam String result) {
        if (!Set.of("REPAIRED", "PENDING_PARTS", "SCRAPPED").contains(result)) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "非法维修结论");
        EquipmentRepair repair = repairs.findById(id).orElseThrow(() -> BizException.notFound("维修单", id));
        repair.setRepairEndTime(LocalDateTime.now()); repair.setRepairResult(result);
        repair.setDowntimeMinutes((int) Math.max(0, ChronoUnit.MINUTES.between(repair.getRepairStartTime(), repair.getRepairEndTime())));
        if ("REPAIRED".equals(result)) {
            faults.findAll().stream().filter(f -> repair.getFaultNo().equals(f.getFaultNo())).findFirst().ifPresent(f -> { f.setStatus("CLOSED"); faults.save(f); });
            equipments.findByEquipmentCode(repair.getEquipmentCode()).ifPresent(e -> { e.setStatus("IDLE"); equipments.save(e); });
        }
        return ApiResponse.ok(repairs.save(repair));
    }
}
