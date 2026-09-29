package com.mfg.eam.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.eam.entity.Equipment;
import com.mfg.eam.entity.EquipmentFault;
import com.mfg.eam.entity.EquipmentRepair;
import com.mfg.eam.repo.EquipmentFaultRepository;
import com.mfg.eam.repo.EquipmentRepairRepository;
import com.mfg.eam.repo.EquipmentRepository;
import com.mfg.security.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** 故障 -> 维修 -> 恢复设备状态的 EAM 闭环。 */
@RestController
@RequestMapping("/api/eam/repairs")
@RequiredArgsConstructor
public class EquipmentRepairController {
    private static final Set<String> REPAIR_RESULTS = Set.of("REPAIRED", "PENDING_PARTS", "SCRAPPED");

    private final EquipmentRepairRepository repairs;
    private final EquipmentFaultRepository faults;
    private final EquipmentRepository equipments;

    @GetMapping
    @PreAuthorize("hasAuthority('EAM:REPAIR:VIEW')")
    public ApiResponse<Page<EquipmentRepair>> page(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(repairs.findAll(PageRequest.of(Math.max(0, page - 1), safeSize(size))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EAM:REPAIR:VIEW')")
    public ApiResponse<EquipmentRepair> detail(@PathVariable Long id) {
        return ApiResponse.ok(repair(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EAM:REPAIR:CREATE')")
    @Transactional
    public ApiResponse<EquipmentRepair> create(@RequestBody EquipmentRepair repair) {
        requireText(repair.getRepairNo(), "维修单号");
        requireText(repair.getFaultNo(), "关联故障单号");
        requireText(repair.getEquipmentCode(), "设备编码");
        requireText(repair.getRepairContent(), "维修内容");
        requireNonNegative(repair.getRepairCost(), "维修成本");
        requireNonNegative(repair.getMaintenanceHours(), "维修工时");
        // Lock before any consistent-read query so a waiter sees the prior creator's committed repair.
        EquipmentFault fault = faults.findByFaultNoForUpdate(repair.getFaultNo())
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联故障单不存在"));
        if (repairs.existsByRepairNo(repair.getRepairNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "维修单号已存在");
        }
        if (!fault.getEquipmentCode().equals(repair.getEquipmentCode())) {
            throw invalid("维修设备必须与故障设备一致");
        }
        if (!"OPEN".equals(fault.getStatus())) throw invalid("只有待处理故障可创建维修单");
        if (hasActiveRepair(repair.getFaultNo())) {
            throw invalid("此故障已有进行中或待备件的维修单");
        }
        Equipment equipment = equipments.findByEquipmentCode(repair.getEquipmentCode())
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "设备不存在"));
        repair.setRepairStartTime(repair.getRepairStartTime() == null ? LocalDateTime.now() : repair.getRepairStartTime());
        repair.setRepairEndTime(null);
        repair.setDowntimeMinutes(0);
        repair.setRepairCost(repair.getRepairCost() == null ? BigDecimal.ZERO : repair.getRepairCost());
        repair.setRepairmanCode(CurrentUser.usernameOrSystem());
        repair.setRepairResult("REPAIRING");
        equipment.setStatus("MAINTENANCE");
        equipments.save(equipment);
        return ApiResponse.ok(repairs.save(repair));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EAM:REPAIR:UPDATE')")
    @Transactional
    public ApiResponse<EquipmentRepair> update(@PathVariable Long id, @RequestBody EquipmentRepair incoming) {
        EquipmentRepair repair = repair(id);
        requireInProgress(repair);
        requireText(incoming.getRepairContent(), "维修内容");
        if (incoming.getRepairNo() != null && !incoming.getRepairNo().equals(repair.getRepairNo())) {
            throw invalid("维修单号不可修改");
        }
        if ((incoming.getFaultNo() != null && !incoming.getFaultNo().equals(repair.getFaultNo()))
                || (incoming.getEquipmentCode() != null && !incoming.getEquipmentCode().equals(repair.getEquipmentCode()))) {
            throw invalid("维修单关联的故障和设备不可修改");
        }
        requireNonNegative(incoming.getRepairCost(), "维修成本");
        requireNonNegative(incoming.getMaintenanceHours(), "维修工时");
        repair.setRepairType(incoming.getRepairType());
        repair.setRepairContent(incoming.getRepairContent());
        repair.setReplacedParts(incoming.getReplacedParts());
        repair.setRepairCost(incoming.getRepairCost() == null ? BigDecimal.ZERO : incoming.getRepairCost());
        repair.setMaintenanceHours(incoming.getMaintenanceHours());
        repair.setRemark(incoming.getRemark());
        return ApiResponse.ok(repairs.save(repair));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EAM:REPAIR:DELETE')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        EquipmentRepair repair = repair(id);
        requireInProgress(repair);
        repairs.delete(repair);
        equipments.findByEquipmentCode(repair.getEquipmentCode()).ifPresent(equipment -> {
            if ("MAINTENANCE".equals(equipment.getStatus())) {
                equipment.setStatus("FAULT");
                equipments.save(equipment);
            }
        });
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('EAM:REPAIR:FINISH')")
    @Transactional
    public ApiResponse<EquipmentRepair> complete(@PathVariable Long id, @RequestParam String result) {
        if (!REPAIR_RESULTS.contains(result)) throw invalid("维修结论无效");
        EquipmentRepair repair = repair(id);
        requireInProgress(repair);
        LocalDateTime endedAt = LocalDateTime.now();
        repair.setRepairEndTime(endedAt);
        repair.setRepairResult(result);
        repair.setDowntimeMinutes((int) Math.max(0, ChronoUnit.MINUTES.between(repair.getRepairStartTime(), endedAt)));

        if (!"PENDING_PARTS".equals(result)) {
            EquipmentFault closedFault = faults.findByFaultNo(repair.getFaultNo())
                    .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联故障单不存在"));
            closedFault.setStatus("CLOSED");
            faults.save(closedFault);
            equipments.findByEquipmentCode(repair.getEquipmentCode()).ifPresent(equipment -> {
                boolean anotherOpenFault = faults.existsByEquipmentCodeAndStatusAndIdNot(repair.getEquipmentCode(), "OPEN", closedFault.getId());
                equipment.setStatus("SCRAPPED".equals(result) ? "SCRAPPED" : anotherOpenFault ? "FAULT" : "IDLE");
                equipments.save(equipment);
            });
        } else {
            equipments.findByEquipmentCode(repair.getEquipmentCode()).ifPresent(equipment -> {
                equipment.setStatus("FAULT");
                equipments.save(equipment);
            });
        }
        return ApiResponse.ok(repairs.save(repair));
    }

    private EquipmentRepair repair(Long id) {
        return repairs.findById(id).orElseThrow(() -> BizException.notFound("维修单", id));
    }

    private void requireInProgress(EquipmentRepair repair) {
        if (!Set.of("REPAIRING", "PENDING_PARTS").contains(repair.getRepairResult())) {
            throw invalid("只有进行中或待备件的维修单可以编辑、删除或完工");
        }
    }

    private void requireNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) throw invalid(label + "不能为负数");
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw invalid(label + "不能为空");
    }

    private boolean hasActiveRepair(String faultNo) {
        return repairs.existsByFaultNoAndRepairResult(faultNo, "REPAIRING")
                || repairs.existsByFaultNoAndRepairResult(faultNo, "PENDING_PARTS");
    }

    private int safeSize(int size) {
        return Math.min(200, Math.max(1, size));
    }

    private BizException invalid(String message) {
        return BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, message);
    }
}
