package com.mfg.mes.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.service.WorkOrderService;
import com.mfg.mes.service.WorkReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/mes/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {
    private final WorkOrderService workOrders;
    private final WorkReportService workReports;

    @GetMapping
    @PreAuthorize("hasAuthority('MES:WORK_ORDER:VIEW')")
    public ApiResponse<Page<WorkOrder>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(workOrders.page(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:WORK_ORDER:VIEW')")
    public ApiResponse<WorkOrder> get(@PathVariable Long id) {
        return ApiResponse.ok(workOrders.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MES:WORK_ORDER:CREATE')")
    public ApiResponse<WorkOrder> create(@RequestBody WorkOrder input) {
        return ApiResponse.ok(workOrders.create(input));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:WORK_ORDER:UPDATE')")
    public ApiResponse<WorkOrder> update(@PathVariable Long id, @RequestBody WorkOrder input) {
        return ApiResponse.ok(workOrders.update(id, input));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:WORK_ORDER:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workOrders.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('MES:WORK_ORDER:START')")
    public ApiResponse<WorkOrder> start(@PathVariable Long id) {
        return ApiResponse.ok(workOrders.start(id));
    }

    /** 兼容工单列表的快捷报工入口，但所有产量仍写入报工明细，再统一汇总。 */
    @PostMapping("/{id}/report")
    @PreAuthorize("hasAuthority('MES:WORK_REPORT:CREATE')")
    public ApiResponse<WorkOrder> report(@PathVariable Long id,
                                         @RequestParam BigDecimal qualifiedQty,
                                         @RequestParam(defaultValue = "0") BigDecimal scrapQty) {
        workReports.createFromWorkOrder(id, qualifiedQty, scrapQty);
        return ApiResponse.ok(workOrders.get(id));
    }
}
