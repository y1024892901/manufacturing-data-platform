package com.mfg.srm.controller;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;

import com.mfg.common.api.*;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.srm.entity.*;
import com.mfg.srm.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.*;
import java.time.*;

@RestController
@RequestMapping("/api/srm")
@RequiredArgsConstructor
public class PurchaseOrderController {private final ScopedQueryService scope;
    private final PurchaseOrderRepository repo;
    private final SupplierDeliveryRepository deliveries;

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('SRM:PURCHASE:VIEW')")
    public ApiResponse<Page<PurchaseOrder>> page(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(scope.page(ScopedResource.PURCHASE_ORDER,PurchaseOrder.class,page,size,null,null));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('SRM:PURCHASE:CREATE')")
    public ApiResponse<PurchaseOrder> create(@RequestBody PurchaseOrder p) {
        if (repo.existsByPurchaseOrderNo(p.getPurchaseOrderNo()))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "采购订单号已存在");
        p.setStatus("CREATED");
        p.setOrderDate(LocalDate.now());
        p.setPurchaseUser(CurrentUser.usernameOrSystem());
        p.setTotalAmount(p.getOrderQty().multiply(p.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
        return ApiResponse.ok(repo.save(p));
    }

    @PostMapping("/purchase-orders/{id}/send")
    @PreAuthorize("hasAuthority('SRM:PURCHASE:SEND')")
    public ApiResponse<PurchaseOrder> send(@PathVariable Long id) {scope.requireVisible(ScopedResource.PURCHASE_ORDER,id);
        PurchaseOrder p = repo.findById(id).orElseThrow(() -> BizException.notFound("采购订单", id));
        if (!"CREATED".equals(p.getStatus()))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有已创建采购订单可下达");
        p.setStatus("SENT");
        return ApiResponse.ok(repo.save(p));
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasAuthority('SRM:DELIVERY:VIEW')")
    public ApiResponse<Page<SupplierDelivery>> deliveryPage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(deliveries.findAll(PageRequest.of(Math.max(0, page - 1), Math.min(size, 200))));
    }

    @PostMapping("/deliveries")
    @PreAuthorize("hasAuthority('SRM:DELIVERY:CREATE')")
    public ApiResponse<SupplierDelivery> delivery(@RequestBody SupplierDelivery d) {
        if (deliveries.existsByDeliveryNo(d.getDeliveryNo()))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "到货单号已存在");
        PurchaseOrder p = repo.findAll().stream().filter(x -> d.getPurchaseOrderNo().equals(x.getPurchaseOrderNo())).findFirst().orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "采购订单不存在"));
        if (!"SENT".equals(p.getStatus()) && !"PARTIAL".equals(p.getStatus()))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "采购订单尚未下达");
        d.setSupplierCode(p.getSupplierCode());
        d.setMaterialCode(p.getMaterialCode());
        d.setExpectedDate(p.getExpectedDate());
        d.setActualDate(LocalDate.now());
        d.setDelayDays((int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(p.getExpectedDate(), d.getActualDate())));
        d.setCreatedBy(CurrentUser.usernameOrSystem());
        return ApiResponse.ok(deliveries.save(d));
    }

    @PostMapping("/deliveries/{id}/inspect")
    @PreAuthorize("hasAuthority('SRM:DELIVERY:INSPECT')")
    public ApiResponse<SupplierDelivery> inspect(@PathVariable Long id, @RequestParam BigDecimal qualifiedQty) {
        SupplierDelivery d = deliveries.findById(id).orElseThrow(() -> BizException.notFound("到货单", id));
        if (qualifiedQty.compareTo(d.getDeliveryQty()) > 0)
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "合格数量不能超过到货数量");
        d.setQualifiedQty(qualifiedQty);
        d.setRejectedQty(d.getDeliveryQty().subtract(qualifiedQty));
        d.setInspectionStatus(qualifiedQty.signum() == 0 ? "FAILED" : qualifiedQty.compareTo(d.getDeliveryQty()) == 0 ? "PASSED" : "PARTIAL");
        return ApiResponse.ok(deliveries.save(d));
    }
}
