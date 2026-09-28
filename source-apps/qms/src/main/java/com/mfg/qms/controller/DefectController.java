package com.mfg.qms.controller;

import com.mfg.common.api.*;
import com.mfg.common.exception.BizException;
import com.mfg.qms.entity.DefectRecord;
import com.mfg.qms.repo.DefectRecordRepository;
import com.mfg.qms.repo.InspectionRepository;
import com.mfg.qms.repo.ReworkOrderRepository;
import com.mfg.security.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@RestController
@RequestMapping("/api/qms/defects")
@RequiredArgsConstructor
public class DefectController {
    private final DefectRecordRepository defects;
    private final InspectionRepository inspections;
    private final ReworkOrderRepository reworks;

    @GetMapping
    @PreAuthorize("hasAuthority('QMS:DEFECT:VIEW') or hasRole('ADMIN')")
    public ApiResponse<Page<DefectRecord>> page(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(defects.search(keyword, status, PageRequest.of(Math.max(0, page - 1), Math.min(200, Math.max(1, size)))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QMS:DEFECT:VIEW') or hasRole('ADMIN')")
    public ApiResponse<DefectRecord> detail(@PathVariable Long id) { return ApiResponse.ok(find(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('QMS:DEFECT:CREATE') or hasRole('ADMIN')")
    public ApiResponse<DefectRecord> create(@RequestBody DefectRecord defect) {
        validateRequired(defect);
        if (defects.existsByDefectNo(defect.getDefectNo())) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "不合格品单号已存在");
        var inspection = inspections.findByInspectionNo(defect.getInspectionNo())
            .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "检验单不存在"));
        if (inspection.getDefectQty() == null || inspection.getDefectQty().signum() <= 0)
            throw invalidState("关联检验单尚未判定不合格数量");
        if (defect.getDefectQty().compareTo(inspection.getDefectQty()) > 0)
            throw invalidState("不合格数量不能超过检验不良数量");
        defect.setId(null); defect.setMaterialCode(inspection.getMaterialCode());
        defect.setDisposition(null); defect.setDispositionQty(BigDecimal.ZERO);
        return ApiResponse.ok(defects.save(defect));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('QMS:DEFECT:UPDATE') or hasRole('ADMIN')")
    public ApiResponse<DefectRecord> update(@PathVariable Long id, @RequestBody DefectRecord input) {
        DefectRecord current = find(id);
        if (current.getDisposition() != null) throw invalidState("已处置的不合格品记录不能修改");
        if (input.getDefectNo() != null && defects.existsByDefectNoAndIdNot(input.getDefectNo(), id))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "不合格品单号已被使用");
        if (input.getInspectionNo() != null && !input.getInspectionNo().equals(current.getInspectionNo()))
            throw invalidState("不合格品来源检验单创建后不能更换");
        current.setDefectNo(input.getDefectNo()); current.setDefectQty(input.getDefectQty()); current.setDefectType(input.getDefectType());
        current.setDefectDesc(input.getDefectDesc()); current.setDefectLevel(input.getDefectLevel()); current.setResponsibleDept(input.getResponsibleDept());
        current.setRootCause(input.getRootCause()); current.setContainmentAction(input.getContainmentAction());
        current.setLocationDesc(input.getLocationDesc()); current.setRemark(input.getRemark());
        validateRequired(current);
        var inspection = inspections.findByInspectionNo(current.getInspectionNo())
            .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联检验单不存在"));
        if (inspection.getDefectQty() == null || current.getDefectQty().compareTo(inspection.getDefectQty()) > 0)
            throw invalidState("不合格数量不能超过检验不良数量");
        current.setMaterialCode(inspection.getMaterialCode());
        return ApiResponse.ok(defects.save(current));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('QMS:DEFECT:DELETE') or hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        DefectRecord current = find(id);
        if (current.getDisposition() != null || reworks.existsByDefectNo(current.getDefectNo()))
            throw invalidState("已处置或已生成返工单的不合格品记录不能删除");
        defects.delete(current);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/disposition")
    @PreAuthorize("hasAuthority('QMS:DEFECT:DISPOSE') or hasRole('ADMIN')")
    public ApiResponse<DefectRecord> disposition(@PathVariable Long id, @RequestParam String action,
            @RequestParam BigDecimal qty, @RequestParam(required = false) String reason) {
        DefectRecord defect = find(id);
        if (!Set.of("REWORK", "SCRAP", "CONCESSION", "RETURN").contains(action)) throw invalidState("非法处置方式");
        if (defect.getDisposition() != null) throw invalidState("该记录已完成处置");
        if (qty == null || qty.signum() <= 0 || qty.compareTo(defect.getDefectQty()) > 0)
            throw invalidState("处置数量必须大于0且不能超过不合格数量");
        defect.setDisposition(action); defect.setDispositionQty(qty); defect.setDispositionAt(LocalDateTime.now());
        defect.setDispositionBy(CurrentUser.usernameOrSystem());
        if (reason != null && !reason.isBlank()) defect.setRemark(reason);
        return ApiResponse.ok(defects.save(defect));
    }

    private DefectRecord find(Long id) { return defects.findById(id).orElseThrow(() -> BizException.notFound("不合格品记录", id)); }
    private void validateRequired(DefectRecord defect) {
        if (defect.getDefectNo() == null || defect.getDefectNo().isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, "请填写不合格品单号");
        if (defect.getInspectionNo() == null || defect.getInspectionNo().isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, "请填写检验单号");
        if (defect.getDefectQty() == null || defect.getDefectQty().signum() <= 0) throw BizException.of(ErrorCode.PARAM_INVALID, "不合格数量必须大于0");
    }
    private BizException invalidState(String message) { return BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, message); }
}
