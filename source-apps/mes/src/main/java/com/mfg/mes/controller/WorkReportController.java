package com.mfg.mes.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.entity.WorkReport;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.mes.repo.WorkReportRepository;
import com.mfg.security.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 明细报工会同步汇总到 MES 工单，避免“报工台账”和“工单进度”两套口径。 */
@RestController @RequestMapping("/api/mes/reports") @RequiredArgsConstructor
public class WorkReportController {
    private final WorkReportRepository reports;
    private final WorkOrderRepository workOrders;
    @GetMapping @PreAuthorize("hasAuthority('MES:WORK_REPORT:VIEW')") public ApiResponse<Page<WorkReport>> page(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(reports.findAll(PageRequest.of(Math.max(0, page - 1), Math.min(200, size)))); }
    @PostMapping @PreAuthorize("hasAuthority('MES:WORK_REPORT:CREATE')") public ApiResponse<WorkReport> create(@RequestBody WorkReport report) {
        if (reports.existsByReportNo(report.getReportNo())) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "报工单号已存在");
        WorkOrder workOrder = workOrders.findAll().stream().filter(w -> report.getWorkOrderNo().equals(w.getWorkOrderNo())).findFirst().orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "MES 工单不存在"));
        if (!"STARTED".equals(workOrder.getStatus())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有开工工单可登记报工");
        BigDecimal qualified = report.getQualifiedQty() == null ? BigDecimal.ZERO : report.getQualifiedQty(); BigDecimal scrap = report.getScrapQty() == null ? BigDecimal.ZERO : report.getScrapQty(); BigDecimal quantity = qualified.add(scrap);
        if (quantity.signum() <= 0 || workOrder.getCompletedQty().add(quantity).compareTo(workOrder.getPlanQty()) > 0) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "累计报工数量必须大于 0 且不超过工单计划数");
        report.setReportQty(quantity); report.setProdOrderNo(workOrder.getProdOrderNo()); report.setOpSeq(workOrder.getOpSeq()); report.setEquipmentCode(report.getEquipmentCode() == null ? workOrder.getEquipmentCode() : report.getEquipmentCode()); report.setReportDate(report.getReportDate() == null ? LocalDate.now() : report.getReportDate()); report.setOperatorCode(CurrentUser.usernameOrSystem()); report.setCreatedBy(CurrentUser.usernameOrSystem());
        workOrder.setCompletedQty(workOrder.getCompletedQty().add(quantity)); workOrder.setQualifiedQty(workOrder.getQualifiedQty().add(qualified)); workOrder.setScrapQty(workOrder.getScrapQty().add(scrap)); if (workOrder.getCompletedQty().compareTo(workOrder.getPlanQty()) == 0) workOrder.setStatus("COMPLETED"); workOrders.save(workOrder);
        return ApiResponse.ok(reports.save(report));
    }
}
