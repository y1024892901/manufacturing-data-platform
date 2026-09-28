package com.mfg.erp.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.exception.BizException;
import com.mfg.erp.dto.ProductionOrderCommand;
import com.mfg.erp.entity.ProductionOrder;
import com.mfg.erp.repo.ProductionOrderRepository;
import com.mfg.erp.service.ProductionOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
@RequestMapping("/api/erp/production-orders")
@RequiredArgsConstructor
public class ProductionOrderController {
    private final ProductionOrderRepository repo;
    private final ProductionOrderService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ERP:PROD_ORDER:VIEW')")
    public ApiResponse<Page<ProductionOrder>> page(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(repo.findAll(PageRequest.of(Math.max(0, page - 1), Math.min(200, size))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:PROD_ORDER:VIEW')")
    public ApiResponse<ProductionOrder> detail(@PathVariable Long id) {
        return ApiResponse.ok(repo.findById(id).orElseThrow(() -> BizException.notFound("生产订单", id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ERP:PROD_ORDER:CREATE')")
    public ApiResponse<ProductionOrder> create(@Valid @RequestBody ProductionOrderCommand command) {
        return ApiResponse.ok(service.create(command));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:PROD_ORDER:UPDATE')")
    public ApiResponse<ProductionOrder> update(@PathVariable Long id, @Valid @RequestBody ProductionOrderCommand command) {
        return ApiResponse.ok(service.update(id, command));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:PROD_ORDER:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteDraft(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('ERP:PROD_ORDER:RELEASE')")
    public ApiResponse<ProductionOrder> release(@PathVariable Long id) {
        return ApiResponse.ok(service.release(id));
    }
}
