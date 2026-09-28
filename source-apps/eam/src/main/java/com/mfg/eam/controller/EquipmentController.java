package com.mfg.eam.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.eam.entity.Equipment;
import com.mfg.eam.entity.EquipmentFault;
import com.mfg.eam.entity.EquipmentInspection;
import com.mfg.eam.repo.EquipmentFaultRepository;
import com.mfg.eam.repo.EquipmentInspectionRepository;
import com.mfg.eam.repo.EquipmentRepairRepository;
import com.mfg.eam.repo.EquipmentRepository;
import com.mfg.eam.repo.EquipmentStatusLogRepository;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@RestController
@RequestMapping("/api/eam")
@RequiredArgsConstructor
public class EquipmentController {
    private static final Set<String> EQUIPMENT_STATUSES = Set.of("RUNNING", "IDLE", "FAULT", "MAINTENANCE", "SCRAPPED");
    private static final Set<String> INSPECTION_TYPES = Set.of("DAILY", "WEEKLY", "MONTHLY");
    private static final Set<String> INSPECTION_RESULTS = Set.of("NORMAL", "ABNORMAL");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScopedQueryService scope;
    private final EquipmentRepository equipments;
    private final EquipmentFaultRepository faults;
    private final EquipmentInspectionRepository inspections;
    private final EquipmentRepairRepository repairs;
    private final EquipmentStatusLogRepository statusLogs;

    @GetMapping("/equipments")
    @PreAuthorize("hasAuthority('EAM:EQUIPMENT:VIEW')")
    public ApiResponse<Page<Equipment>> equipmentPage(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(scope.page(ScopedResource.EQUIPMENT, Equipment.class, page, safeSize(size), null, null));
    }

    @GetMapping("/equipments/{id}")
    @PreAuthorize("hasAuthority('EAM:EQUIPMENT:VIEW')")
    public ApiResponse<Equipment> equipmentDetail(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.EQUIPMENT, id);
        return ApiResponse.ok(equipment(id));
    }

    @PostMapping("/equipments")
    @PreAuthorize("hasAuthority('EAM:EQUIPMENT:CREATE')")
    @Transactional
    public ApiResponse<Equipment> createEquipment(@RequestBody Equipment equipment) {
        if (equipments.existsByEquipmentCode(equipment.getEquipmentCode())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "设备编码已存在");
        }
        equipment.setStatus("IDLE");
        return ApiResponse.ok(equipments.save(equipment));
    }

    @PutMapping("/equipments/{id}")
    @PreAuthorize("hasAuthority('EAM:EQUIPMENT:UPDATE')")
    @Transactional
    public ApiResponse<Equipment> updateEquipment(@PathVariable Long id, @RequestBody Equipment incoming) {
        scope.requireVisible(ScopedResource.EQUIPMENT, id);
        Equipment equipment = equipment(id);
        if (incoming.getEquipmentCode() != null && !incoming.getEquipmentCode().equals(equipment.getEquipmentCode())) {
            throw invalid("设备编码不可修改，请删除未关联的设备后重新建档");
        }
        equipment.setEquipmentName(incoming.getEquipmentName());
        equipment.setEquipmentType(incoming.getEquipmentType());
        equipment.setEquipmentModel(incoming.getEquipmentModel());
        equipment.setWorkshopCode(incoming.getWorkshopCode());
        equipment.setWorkCenter(incoming.getWorkCenter());
        equipment.setLocationDesc(incoming.getLocationDesc());
        equipment.setCapacityPerHour(incoming.getCapacityPerHour());
        equipment.setHealthLevel(incoming.getHealthLevel());
        equipment.setPurchaseDate(incoming.getPurchaseDate());
        equipment.setPurchasePrice(incoming.getPurchasePrice());
        equipment.setWarrantyEndDate(incoming.getWarrantyEndDate());
        equipment.setMaintenanceCycleDays(incoming.getMaintenanceCycleDays());
        equipment.setLastMaintenanceDate(incoming.getLastMaintenanceDate());
        return ApiResponse.ok(equipments.save(equipment));
    }

    @DeleteMapping("/equipments/{id}")
    @PreAuthorize("hasAuthority('EAM:EQUIPMENT:DELETE')")
    @Transactional
    public ApiResponse<Void> deleteEquipment(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.EQUIPMENT, id);
        Equipment equipment = equipment(id);
        String code = equipment.getEquipmentCode();
        if (faults.existsByEquipmentCode(code) || inspections.existsByEquipmentCode(code)
                || repairs.existsByEquipmentCode(code) || statusLogs.existsByEquipmentCode(code)) {
            throw invalid("设备已有故障、维修、点检或状态履历，不能删除；请保留设备档案用于追溯");
        }
        equipments.delete(equipment);
        return ApiResponse.ok();
    }

    @PostMapping("/equipments/{id}/status")
    @PreAuthorize("hasAuthority('EAM:EQUIPMENT:UPDATE')")
    @Transactional
    public ApiResponse<Equipment> status(@PathVariable Long id, @RequestParam String status) {
        scope.requireVisible(ScopedResource.EQUIPMENT, id);
        if (!EQUIPMENT_STATUSES.contains(status)) throw invalid("设备状态无效");
        Equipment equipment = equipment(id);
        if ("SCRAPPED".equals(equipment.getStatus()) && !"SCRAPPED".equals(status)) {
            throw invalid("已报废设备不能恢复为运行状态");
        }
        equipment.setStatus(status);
        return ApiResponse.ok(equipments.save(equipment));
    }

    @GetMapping("/faults")
    @PreAuthorize("hasAuthority('EAM:FAULT:VIEW')")
    public ApiResponse<Page<EquipmentFault>> faultPage(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(faults.findAll(PageRequest.of(Math.max(0, page - 1), safeSize(size))));
    }

    @GetMapping("/faults/{id}")
    @PreAuthorize("hasAuthority('EAM:FAULT:VIEW')")
    public ApiResponse<EquipmentFault> faultDetail(@PathVariable Long id) {
        return ApiResponse.ok(fault(id));
    }

    @PostMapping("/faults")
    @PreAuthorize("hasAuthority('EAM:FAULT:CREATE')")
    @Transactional
    public ApiResponse<EquipmentFault> createFault(@RequestBody EquipmentFault fault) {
        if (faults.existsByFaultNo(fault.getFaultNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "故障单号已存在");
        }
        Equipment equipment = equipments.findByEquipmentCode(fault.getEquipmentCode())
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "设备不存在"));
        if ("SCRAPPED".equals(equipment.getStatus())) throw invalid("已报废设备不能新建故障单");
        fault.setEquipmentName(equipment.getEquipmentName());
        fault.setFaultTime(LocalDateTime.now());
        fault.setReportedBy(CurrentUser.usernameOrSystem());
        fault.setStatus("OPEN");
        equipment.setStatus("FAULT");
        equipments.save(equipment);
        return ApiResponse.ok(faults.save(fault));
    }

    @PutMapping("/faults/{id}")
    @PreAuthorize("hasAuthority('EAM:FAULT:UPDATE')")
    @Transactional
    public ApiResponse<EquipmentFault> updateFault(@PathVariable Long id, @RequestBody EquipmentFault incoming) {
        EquipmentFault fault = fault(id);
        requireOpenFault(fault);
        if (repairs.existsByFaultNoAndRepairResult(fault.getFaultNo(), "REPAIRING")
                || repairs.existsByFaultNoAndRepairResult(fault.getFaultNo(), "PENDING_PARTS")) {
            throw invalid("故障已有维修单，请通过维修单完工后关闭故障");
        }
        if (incoming.getFaultNo() != null && !incoming.getFaultNo().equals(fault.getFaultNo())) {
            throw invalid("故障单号不可修改");
        }
        fault.setFaultType(incoming.getFaultType());
        fault.setFaultLevel(incoming.getFaultLevel());
        fault.setFaultDesc(incoming.getFaultDesc());
        fault.setFaultCause(incoming.getFaultCause());
        fault.setWorkOrderNo(incoming.getWorkOrderNo());
        fault.setProdOrderNo(incoming.getProdOrderNo());
        fault.setOperationCode(incoming.getOperationCode());
        return ApiResponse.ok(faults.save(fault));
    }

    @DeleteMapping("/faults/{id}")
    @PreAuthorize("hasAuthority('EAM:FAULT:DELETE')")
    @Transactional
    public ApiResponse<Void> deleteFault(@PathVariable Long id) {
        EquipmentFault fault = fault(id);
        requireOpenFault(fault);
        if (repairs.existsByFaultNo(fault.getFaultNo())) throw invalid("故障单已有维修记录，不能删除");
        faults.delete(fault);
        if (!faults.existsByEquipmentCodeAndStatusAndIdNot(fault.getEquipmentCode(), "OPEN", id)) {
            equipments.findByEquipmentCode(fault.getEquipmentCode()).ifPresent(equipment -> {
                if ("FAULT".equals(equipment.getStatus())) {
                    equipment.setStatus("IDLE");
                    equipments.save(equipment);
                }
            });
        }
        return ApiResponse.ok();
    }

    @PostMapping("/faults/{id}/close")
    @PreAuthorize("hasAuthority('EAM:FAULT:CLOSE')")
    @Transactional
    public ApiResponse<EquipmentFault> closeFault(@PathVariable Long id) {
        EquipmentFault fault = fault(id);
        requireOpenFault(fault);
        if (repairs.existsByFaultNoAndRepairResult(fault.getFaultNo(), "REPAIRING")
                || repairs.existsByFaultNoAndRepairResult(fault.getFaultNo(), "PENDING_PARTS")) {
            throw invalid("故障已有维修单，请通过维修单完工后关闭故障");
        }
        fault.setStatus("CLOSED");
        Equipment savedEquipment = equipments.findByEquipmentCode(fault.getEquipmentCode()).orElse(null);
        if (savedEquipment != null && !faults.existsByEquipmentCodeAndStatusAndIdNot(fault.getEquipmentCode(), "OPEN", id)) {
            if (!"SCRAPPED".equals(savedEquipment.getStatus())) {
                savedEquipment.setStatus("IDLE");
                equipments.save(savedEquipment);
            }
        }
        return ApiResponse.ok(faults.save(fault));
    }

    @GetMapping("/inspections")
    @PreAuthorize("hasAuthority('EAM:INSPECTION:VIEW')")
    public ApiResponse<Page<EquipmentInspection>> inspectionPage(@RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inspections.findAll(PageRequest.of(Math.max(0, page - 1), safeSize(size))));
    }

    @GetMapping("/inspections/{id}")
    @PreAuthorize("hasAuthority('EAM:INSPECTION:VIEW')")
    public ApiResponse<EquipmentInspection> inspectionDetail(@PathVariable Long id) {
        return ApiResponse.ok(inspection(id));
    }

    @PostMapping("/inspections")
    @PreAuthorize("hasAuthority('EAM:INSPECTION:PLAN')")
    @Transactional
    public ApiResponse<EquipmentInspection> createInspection(@RequestBody EquipmentInspection inspection) {
        if (inspections.existsByInspectionNo(inspection.getInspectionNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "点检单号已存在");
        }
        validateInspection(inspection);
        requireEquipment(inspection.getEquipmentCode());
        inspection.setResult(null);
        inspection.setActualDate(null);
        inspection.setInspectorCode(null);
        inspection.setMissed(false);
        return ApiResponse.ok(inspections.save(inspection));
    }

    @PutMapping("/inspections/{id}")
    @PreAuthorize("hasAuthority('EAM:INSPECTION:UPDATE')")
    @Transactional
    public ApiResponse<EquipmentInspection> updateInspection(@PathVariable Long id,
                                                               @RequestBody EquipmentInspection incoming) {
        EquipmentInspection inspection = inspection(id);
        requirePlannedInspection(inspection);
        if (incoming.getInspectionNo() != null && !incoming.getInspectionNo().equals(inspection.getInspectionNo())) {
            throw invalid("点检单号不可修改");
        }
        validateInspection(incoming);
        inspection.setInspectionType(incoming.getInspectionType());
        inspection.setPlanDate(incoming.getPlanDate());
        inspection.setCheckItems(incoming.getCheckItems());
        return ApiResponse.ok(inspections.save(inspection));
    }

    @DeleteMapping("/inspections/{id}")
    @PreAuthorize("hasAuthority('EAM:INSPECTION:DELETE')")
    @Transactional
    public ApiResponse<Void> deleteInspection(@PathVariable Long id) {
        EquipmentInspection inspection = inspection(id);
        requirePlannedInspection(inspection);
        inspections.delete(inspection);
        return ApiResponse.ok();
    }

    @PostMapping("/inspections/{id}/complete")
    @PreAuthorize("hasAuthority('EAM:INSPECTION:CREATE')")
    @Transactional
    public ApiResponse<EquipmentInspection> completeInspection(@PathVariable Long id,
                                                                 @RequestParam String result,
                                                                 @RequestParam(required = false) String abnormalDesc) {
        if (!INSPECTION_RESULTS.contains(result)) throw invalid("点检结果只能选择正常或异常");
        EquipmentInspection inspection = inspection(id);
        requirePlannedInspection(inspection);
        inspection.setActualDate(LocalDate.now());
        inspection.setResult(result);
        inspection.setAbnormalDesc(abnormalDesc);
        inspection.setInspectorCode(CurrentUser.usernameOrSystem());
        return ApiResponse.ok(inspections.save(inspection));
    }

    private Equipment equipment(Long id) {
        return equipments.findById(id).orElseThrow(() -> BizException.notFound("设备", id));
    }

    private EquipmentFault fault(Long id) {
        return faults.findById(id).orElseThrow(() -> BizException.notFound("故障单", id));
    }

    private EquipmentInspection inspection(Long id) {
        return inspections.findById(id).orElseThrow(() -> BizException.notFound("点检单", id));
    }

    private Equipment requireEquipment(String code) {
        return equipments.findByEquipmentCode(code)
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "设备不存在"));
    }

    private void requireOpenFault(EquipmentFault fault) {
        if (!"OPEN".equals(fault.getStatus())) throw invalid("只有待处理故障单可以编辑、删除或关闭");
    }

    private void requirePlannedInspection(EquipmentInspection inspection) {
        if (inspection.getResult() != null || Boolean.TRUE.equals(inspection.getMissed())) {
            throw invalid("已执行或已漏检的点检单不能编辑或删除");
        }
    }

    private void validateInspection(EquipmentInspection inspection) {
        if (inspection.getInspectionType() == null || !INSPECTION_TYPES.contains(inspection.getInspectionType())) {
            throw invalid("点检周期只能选择日、周或月");
        }
        if (inspection.getPlanDate() == null) throw invalid("计划日期不能为空");
        if (inspection.getCheckItems() != null && !inspection.getCheckItems().isBlank()) {
            try {
                JsonNode node = JSON.readTree(inspection.getCheckItems());
                if (node == null || (!node.isArray() && !node.isObject())) throw new IllegalArgumentException();
            } catch (Exception e) {
                throw invalid("点检项目必须填写有效的 JSON 对象或数组");
            }
        }
    }

    private int safeSize(int size) {
        return Math.min(200, Math.max(1, size));
    }

    private BizException invalid(String message) {
        return BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, message);
    }
}
