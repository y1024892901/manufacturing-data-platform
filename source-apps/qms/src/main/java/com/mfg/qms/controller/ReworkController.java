package com.mfg.qms.controller;

import com.mfg.common.api.*;
import com.mfg.common.exception.BizException;
import com.mfg.qms.entity.ReworkOrder;
import com.mfg.qms.repo.DefectRecordRepository;
import com.mfg.qms.repo.ReworkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** QMS 返工闭环：仅从已决定返工的不合格品生成。 */
@RestController
@RequestMapping("/api/qms/reworks")
@RequiredArgsConstructor
public class ReworkController {
    private final ReworkOrderRepository reworks;
    private final DefectRecordRepository defects;

    @GetMapping
    @PreAuthorize("hasAuthority('QMS:REWORK:VIEW') or hasRole('ADMIN')")
    public ApiResponse<Page<ReworkOrder>> page(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(reworks.search(keyword, status, PageRequest.of(Math.max(0, page - 1), Math.min(200, Math.max(1, size)))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QMS:REWORK:VIEW') or hasRole('ADMIN')")
    public ApiResponse<ReworkOrder> detail(@PathVariable Long id) { return ApiResponse.ok(find(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('QMS:REWORK:CREATE') or hasRole('ADMIN')")
    public ApiResponse<ReworkOrder> create(@RequestBody ReworkOrder order) {
        validateRequired(order);
        if (reworks.existsByReworkNo(order.getReworkNo())) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "返工单号已存在");
        var defect = defects.findByDefectNo(order.getDefectNo())
            .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "不合格品记录不存在"));
        if (!"REWORK".equals(defect.getDisposition())) throw invalidState("只有处置方式为返工的不合格品可创建返工单");
        if (order.getReworkQty().compareTo(defect.getDispositionQty()) > 0)
            throw invalidState("返工数量不能超过不合格品的返工处置数量");
        order.setId(null); order.setMaterialCode(defect.getMaterialCode()); order.setStatus("PENDING");
        order.setStartTime(null); order.setEndTime(null);
        return ApiResponse.ok(reworks.save(order));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('QMS:REWORK:UPDATE') or hasRole('ADMIN')")
    public ApiResponse<ReworkOrder> update(@PathVariable Long id, @RequestBody ReworkOrder input) {
        ReworkOrder current = find(id);
        if (!"PENDING".equals(current.getStatus())) throw invalidState("只有待返工的返工单可以修改");
        if (input.getReworkNo() != null && reworks.existsByReworkNoAndIdNot(input.getReworkNo(), id))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "返工单号已被使用");
        if (input.getDefectNo() != null && !input.getDefectNo().equals(current.getDefectNo()))
            throw invalidState("返工单来源不合格品创建后不能更换");
        if (input.getReworkNo() != null) current.setReworkNo(input.getReworkNo());
        if (input.getProdOrderNo() != null) current.setProdOrderNo(input.getProdOrderNo());
        current.setWorkOrderNo(input.getWorkOrderNo());
        if (input.getReworkQty() != null) current.setReworkQty(input.getReworkQty());
        current.setReworkHours(input.getReworkHours()); current.setDelayDays(input.getDelayDays());
        current.setOwnerUser(input.getOwnerUser()); current.setReworkMethod(input.getReworkMethod());
        current.setVerificationNote(input.getVerificationNote()); current.setRemark(input.getRemark());
        validateRequired(current);
        var defect = defects.findByDefectNo(current.getDefectNo())
            .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "不合格品记录不存在"));
        if (!"REWORK".equals(defect.getDisposition()) || current.getReworkQty().compareTo(defect.getDispositionQty()) > 0)
            throw invalidState("返工数量不能超过不合格品的返工处置数量");
        current.setMaterialCode(defect.getMaterialCode());
        return ApiResponse.ok(reworks.save(current));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('QMS:REWORK:DELETE') or hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ReworkOrder current = find(id);
        if (!"PENDING".equals(current.getStatus())) throw invalidState("已开工的返工单不能删除");
        reworks.delete(current);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('QMS:REWORK:START') or hasRole('ADMIN')")
    public ApiResponse<ReworkOrder> start(@PathVariable Long id) {
        ReworkOrder order = find(id);
        if (!"PENDING".equals(order.getStatus())) throw invalidState("当前返工单不可开工");
        order.setStatus("DOING"); order.setStartTime(LocalDateTime.now());
        return ApiResponse.ok(reworks.save(order));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('QMS:REWORK:COMPLETE') or hasRole('ADMIN')")
    public ApiResponse<ReworkOrder> complete(@PathVariable Long id, @RequestParam(defaultValue = "DONE") String status) {
        ReworkOrder order = find(id);
        if (!"DOING".equals(order.getStatus())) throw invalidState("只有进行中的返工单可完工");
        if (!"DONE".equals(status) && !"SCRAPPED".equals(status)) throw invalidState("返工结论只能为完成或转为报废");
        order.setStatus(status); order.setEndTime(LocalDateTime.now());
        order.setVerificationResult("DONE".equals(status) ? "PASSED" : "FAILED");
        order.setQualifiedQty("DONE".equals(status) ? order.getReworkQty() : BigDecimal.ZERO);
        order.setScrapQty("SCRAPPED".equals(status) ? order.getReworkQty() : BigDecimal.ZERO);
        return ApiResponse.ok(reworks.save(order));
    }

    private ReworkOrder find(Long id) { return reworks.findById(id).orElseThrow(() -> BizException.notFound("返工单", id)); }
    private void validateRequired(ReworkOrder order) {
        if (order.getReworkNo() == null || order.getReworkNo().isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, "请填写返工单号");
        if (order.getDefectNo() == null || order.getDefectNo().isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, "请填写不合格品单号");
        if (order.getProdOrderNo() == null || order.getProdOrderNo().isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, "请填写关联生产订单");
        if (order.getReworkQty() == null || order.getReworkQty().signum() <= 0) throw BizException.of(ErrorCode.PARAM_INVALID, "返工数量必须大于0");
    }
    private BizException invalidState(String message) { return BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, message); }
}
