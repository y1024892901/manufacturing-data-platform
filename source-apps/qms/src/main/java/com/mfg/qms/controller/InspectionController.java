package com.mfg.qms.controller;

import com.mfg.common.api.*;
import com.mfg.common.exception.BizException;
import com.mfg.qms.entity.Inspection;
import com.mfg.qms.repo.InspectionRepository;
import com.mfg.qms.service.QmsLifecycleService;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/api/qms/inspections")
@RequiredArgsConstructor
public class InspectionController {
    private static final Set<String> TYPES = Set.of("IQC", "IPQC", "FQC", "OQC");
    private final ScopedQueryService scope;
    private final InspectionRepository inspections;
    private final QmsLifecycleService lifecycle;
    private final JdbcTemplate db;

    @GetMapping
    @PreAuthorize("hasAuthority('QMS:INSPECTION:VIEW') or hasRole('ADMIN')")
    public ApiResponse<Page<Inspection>> page(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(scope.page(ScopedResource.INSPECTION, Inspection.class, page, size, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QMS:INSPECTION:VIEW') or hasRole('ADMIN')")
    public ApiResponse<Inspection> detail(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.INSPECTION, id);
        return ApiResponse.ok(find(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('QMS:INSPECTION:CREATE') or hasRole('ADMIN')")
    public ApiResponse<Inspection> create(@RequestBody Inspection inspection) {
        validate(inspection);
        if (inspections.existsByInspectionNo(inspection.getInspectionNo()))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "检验单号已存在");
        inspection.setId(null);
        inspection.setResult("PENDING");
        inspection.setInspectDate(LocalDate.now());
        inspection.setInspectorCode(CurrentUser.usernameOrSystem());
        return ApiResponse.ok(inspections.save(inspection));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('QMS:INSPECTION:UPDATE') or hasRole('ADMIN')")
    public ApiResponse<Inspection> update(@PathVariable Long id, @RequestBody Inspection input) {
        scope.requireVisible(ScopedResource.INSPECTION, id);
        Inspection current = find(id);
        if (!"PENDING".equals(current.getResult()))
            throw invalidState("只有待判定的检验单可以修改");
        if (input.getInspectionNo() != null && inspections.existsByInspectionNoAndIdNot(input.getInspectionNo(), id))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "检验单号已被使用");
        copyEditable(input, current);
        validate(current);
        return ApiResponse.ok(inspections.save(current));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('QMS:INSPECTION:DELETE') or hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.INSPECTION, id);
        Inspection current = find(id);
        if (!"PENDING".equals(current.getResult())) throw invalidState("已判定的检验单不能删除");
        Long defectCount = db.queryForObject("SELECT COUNT(*) FROM src_qms.qms_defect WHERE inspection_no=?", Long.class, current.getInspectionNo());
        Long ncrCount = db.queryForObject("SELECT COUNT(*) FROM src_qms.qms_ncr WHERE inspection_no=?", Long.class, current.getInspectionNo());
        if ((defectCount != null && defectCount > 0) || (ncrCount != null && ncrCount > 0))
            throw invalidState("检验单已关联不合格记录，不能删除");
        inspections.delete(current);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/judge")
    @PreAuthorize("hasAuthority('QMS:INSPECTION:JUDGE') or hasRole('ADMIN')")
    public ApiResponse<?> judge(@PathVariable Long id, @RequestParam BigDecimal qualifiedQty,
            @RequestParam BigDecimal defectQty, @RequestParam String result) {
        scope.requireVisible(ScopedResource.INSPECTION, id);
        return ApiResponse.ok(lifecycle.judge(id, qualifiedQty, defectQty, result));
    }

    private Inspection find(Long id) { return inspections.findById(id).orElseThrow(() -> BizException.notFound("检验单", id)); }

    private void validate(Inspection inspection) {
        if (inspection.getInspectionNo() == null || inspection.getInspectionNo().isBlank())
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写检验单号");
        if (!TYPES.contains(inspection.getInspectionType())) throw BizException.of(ErrorCode.PARAM_INVALID, "请选择有效的检验类型");
        if (inspection.getMaterialCode() == null || inspection.getMaterialCode().isBlank())
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写物料编码");
        if (inspection.getInspectedQty() == null || inspection.getInspectedQty().compareTo(BigDecimal.ZERO) <= 0)
            throw BizException.of(ErrorCode.PARAM_INVALID, "送检数量必须大于0");
        if (inspection.getSampleQty() != null && inspection.getSampleQty().compareTo(inspection.getInspectedQty()) > 0)
            throw BizException.of(ErrorCode.PARAM_INVALID, "抽样数量不能超过送检数量");
    }

    private void copyEditable(Inspection from, Inspection to) {
        if (from.getInspectionNo() != null) to.setInspectionNo(from.getInspectionNo());
        if (from.getInspectionType() != null) to.setInspectionType(from.getInspectionType());
        if (from.getMaterialCode() != null) to.setMaterialCode(from.getMaterialCode());
        to.setMaterialName(from.getMaterialName()); to.setBatchNo(from.getBatchNo());
        to.setProdOrderNo(from.getProdOrderNo()); to.setWorkOrderNo(from.getWorkOrderNo());
        if (from.getInspectedQty() != null) to.setInspectedQty(from.getInspectedQty());
        to.setOperationCode(from.getOperationCode()); to.setSupplierCode(from.getSupplierCode()); to.setDeliveryNo(from.getDeliveryNo());
        to.setSourceType(from.getSourceType()); to.setSourceNo(from.getSourceNo()); to.setStandardCode(from.getStandardCode());
        to.setWorkshopCode(from.getWorkshopCode()); to.setSampleQty(from.getSampleQty()); to.setInspectionBasis(from.getInspectionBasis());
        to.setInspectionMethod(from.getInspectionMethod()); to.setInspectionPoint(from.getInspectionPoint()); to.setEquipmentCode(from.getEquipmentCode());
        to.setEnvironmentTemp(from.getEnvironmentTemp()); to.setEnvironmentHumidity(from.getEnvironmentHumidity()); to.setRemark(from.getRemark());
    }

    private BizException invalidState(String message) { return BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, message); }
}
