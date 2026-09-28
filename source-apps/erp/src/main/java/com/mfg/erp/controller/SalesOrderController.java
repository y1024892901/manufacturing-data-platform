package com.mfg.erp.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.exception.BizException;
import com.mfg.erp.dto.SalesOrderCommand;
import com.mfg.erp.entity.SalesOrder;
import com.mfg.erp.repo.SalesOrderLineRepository;
import com.mfg.erp.repo.SalesOrderRepository;
import com.mfg.erp.service.ErpP2Service;
import com.mfg.erp.service.SalesOrderService;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/erp/sales-orders")
@RequiredArgsConstructor
public class SalesOrderController {
    private final ScopedQueryService scope;
    private final SalesOrderRepository repo;
    private final SalesOrderLineRepository lines;
    private final SalesOrderService service;
    private final ErpP2Service p2Service;

    @GetMapping
    @PreAuthorize("hasAuthority('ERP:SALES_ORDER:VIEW')")
    public ApiResponse<Page<SalesOrder>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.ok(scope.page(ScopedResource.SALES_ORDER, SalesOrder.class, page, size, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:SALES_ORDER:VIEW')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.SALES_ORDER, id);
        SalesOrder order = repo.findById(id).orElseThrow(() -> BizException.notFound("销售订单", id));
        return ApiResponse.ok(Map.of("header", order, "lines", lines.findBySalesOrderNoOrderByLineNo(order.getSalesOrderNo())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ERP:SALES_ORDER:CREATE')")
    public ApiResponse<SalesOrder> create(@Valid @RequestBody SalesOrderCommand command) {
        return ApiResponse.ok(service.create(command));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:SALES_ORDER:UPDATE')")
    public ApiResponse<SalesOrder> update(@PathVariable Long id, @Valid @RequestBody SalesOrderCommand command) {
        scope.requireVisible(ScopedResource.SALES_ORDER, id);
        return ApiResponse.ok(service.update(id, command));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:SALES_ORDER:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.SALES_ORDER, id);
        service.deleteDraft(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('ERP:SALES_ORDER:CONFIRM')")
    public ApiResponse<SalesOrder> confirm(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.SALES_ORDER, id);
        p2Service.confirm(id);
        return ApiResponse.ok(repo.findById(id).orElseThrow());
    }
}
