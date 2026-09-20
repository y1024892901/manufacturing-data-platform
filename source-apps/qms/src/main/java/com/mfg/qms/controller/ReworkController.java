package com.mfg.qms.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.qms.entity.ReworkOrder;
import com.mfg.qms.repo.DefectRecordRepository;
import com.mfg.qms.repo.ReworkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** QMS 返工闭环：仅从已决定返工的不合格品生成，防止质量数据脱离来源。 */
@RestController @RequestMapping("/api/qms/reworks") @RequiredArgsConstructor
public class ReworkController {
    private final ReworkOrderRepository reworks;
    private final DefectRecordRepository defects;

    @GetMapping public ApiResponse<Page<ReworkOrder>> page(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reworks.findAll(PageRequest.of(Math.max(0, page - 1), Math.min(200, size))));
    }
    @PostMapping public ApiResponse<ReworkOrder> create(@RequestBody ReworkOrder order) {
        if (reworks.existsByReworkNo(order.getReworkNo())) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "返工单号已存在");
        var defect = defects.findAll().stream().filter(d -> order.getDefectNo().equals(d.getDefectNo())).findFirst()
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "不合格品记录不存在"));
        if (!"REWORK".equals(defect.getDisposition())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有处置方式为 REWORK 的不合格品可创建返工单");
        if (order.getReworkQty() == null || order.getReworkQty().compareTo(BigDecimal.ZERO) <= 0 || order.getReworkQty().compareTo(defect.getDispositionQty()) > 0) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "返工数量必须大于 0 且不超过已处置数量");
        order.setMaterialCode(defect.getMaterialCode()); order.setStatus("PENDING");
        return ApiResponse.ok(reworks.save(order));
    }
    @PostMapping("/{id}/start") public ApiResponse<ReworkOrder> start(@PathVariable Long id) {
        ReworkOrder order = find(id); if (!"PENDING".equals(order.getStatus())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "当前返工单不可开工");
        order.setStatus("DOING"); order.setStartTime(LocalDateTime.now()); return ApiResponse.ok(reworks.save(order));
    }
    @PostMapping("/{id}/complete") public ApiResponse<ReworkOrder> complete(@PathVariable Long id, @RequestParam(defaultValue = "DONE") String status) {
        ReworkOrder order = find(id); if (!"DOING".equals(order.getStatus())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有进行中的返工单可完工");
        if (!"DONE".equals(status) && !"SCRAPPED".equals(status)) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "返工结论只能为 DONE 或 SCRAPPED");
        order.setStatus(status); order.setEndTime(LocalDateTime.now()); return ApiResponse.ok(reworks.save(order));
    }
    private ReworkOrder find(Long id) { return reworks.findById(id).orElseThrow(() -> BizException.notFound("返工单", id)); }
}
