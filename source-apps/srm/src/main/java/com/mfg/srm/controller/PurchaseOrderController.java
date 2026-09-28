package com.mfg.srm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import com.mfg.srm.entity.PurchaseOrder;
import com.mfg.srm.entity.SupplierDelivery;
import com.mfg.srm.repo.PurchaseOrderRepository;
import com.mfg.srm.repo.SupplierDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/srm")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final ScopedQueryService scope;
    private final PurchaseOrderRepository repo;
    private final SupplierDeliveryRepository deliveries;

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('SRM:PURCHASE:VIEW')")
    public ApiResponse<Page<PurchaseOrder>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(scope.page(ScopedResource.PURCHASE_ORDER, PurchaseOrder.class, page, size, keyword, status));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('SRM:PURCHASE:CREATE')")
    public ApiResponse<PurchaseOrder> create(@RequestBody PurchaseOrder order) {
        validatePurchaseOrder(order);
        if (repo.existsByPurchaseOrderNo(order.getPurchaseOrderNo().trim())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "采购订单号已存在");
        }
        order.setPurchaseOrderNo(order.getPurchaseOrderNo().trim());
        order.setSupplierCode(order.getSupplierCode().trim());
        order.setSupplierName(order.getSupplierName().trim());
        order.setMaterialCode(order.getMaterialCode().trim());
        order.setMaterialName(order.getMaterialName().trim());
        order.setUnitCode(order.getUnitCode().trim());
        order.setStatus("CREATED");
        order.setOrderDate(LocalDate.now());
        order.setPurchaseUser(CurrentUser.usernameOrSystem());
        order.setReceivedQty(BigDecimal.ZERO);
        order.setTotalAmount(order.getOrderQty().multiply(order.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
        return ApiResponse.ok(repo.save(order));
    }

    @PostMapping("/purchase-orders/{id}/send")
    @PreAuthorize("hasAuthority('SRM:PURCHASE:SEND')")
    public ApiResponse<PurchaseOrder> send(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.PURCHASE_ORDER, id);
        PurchaseOrder order = repo.findById(id).orElseThrow(() -> BizException.notFound("采购订单", id));
        if (!"CREATED".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有新建状态的采购订单可以下达");
        }
        order.setStatus("SENT");
        return ApiResponse.ok(repo.save(order));
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasAuthority('SRM:DELIVERY:VIEW')")
    public ApiResponse<Page<SupplierDelivery>> deliveryPage(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(deliveries.findAll(PageRequest.of(Math.max(0, page - 1), Math.min(size, 200))));
    }

    @PostMapping("/deliveries")
    @PreAuthorize("hasAuthority('SRM:DELIVERY:CREATE')")
    public ApiResponse<SupplierDelivery> delivery(@RequestBody SupplierDelivery delivery) {
        if (delivery.getDeliveryNo() == null || delivery.getDeliveryNo().isBlank()
                || delivery.getPurchaseOrderNo() == null || delivery.getPurchaseOrderNo().isBlank()
                || delivery.getDeliveryQty() == null || delivery.getDeliveryQty().signum() <= 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写到货单号、采购订单号和大于零的到货数量");
        }
        if (deliveries.existsByDeliveryNo(delivery.getDeliveryNo().trim())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "到货单号已存在");
        }
        PurchaseOrder order = repo.findAll().stream()
                .filter(item -> delivery.getPurchaseOrderNo().trim().equals(item.getPurchaseOrderNo()))
                .findFirst()
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "采购订单不存在"));
        if (!"SENT".equals(order.getStatus()) && !"PARTIAL".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "采购订单尚未下达");
        }
        delivery.setDeliveryNo(delivery.getDeliveryNo().trim());
        delivery.setSupplierCode(order.getSupplierCode());
        delivery.setMaterialCode(order.getMaterialCode());
        delivery.setExpectedDate(order.getExpectedDate());
        delivery.setActualDate(LocalDate.now());
        delivery.setDelayDays((int) Math.max(0, ChronoUnit.DAYS.between(order.getExpectedDate(), delivery.getActualDate())));
        delivery.setCreatedBy(CurrentUser.usernameOrSystem());
        return ApiResponse.ok(deliveries.save(delivery));
    }

    @PostMapping("/deliveries/{id}/inspect")
    @PreAuthorize("hasAuthority('SRM:DELIVERY:INSPECT')")
    public ApiResponse<SupplierDelivery> inspect(@PathVariable Long id, @RequestParam BigDecimal qualifiedQty) {
        SupplierDelivery delivery = deliveries.findById(id).orElseThrow(() -> BizException.notFound("到货单", id));
        if (qualifiedQty.signum() < 0 || qualifiedQty.compareTo(delivery.getDeliveryQty()) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "合格数量必须在0至到货数量之间");
        }
        delivery.setQualifiedQty(qualifiedQty);
        delivery.setRejectedQty(delivery.getDeliveryQty().subtract(qualifiedQty));
        delivery.setInspectionStatus(qualifiedQty.signum() == 0 ? "FAILED"
                : qualifiedQty.compareTo(delivery.getDeliveryQty()) == 0 ? "PASSED" : "PARTIAL");
        return ApiResponse.ok(deliveries.save(delivery));
    }

    private void validatePurchaseOrder(PurchaseOrder order) {
        if (order == null || blank(order.getPurchaseOrderNo()) || blank(order.getSupplierCode())
                || blank(order.getSupplierName()) || blank(order.getMaterialCode()) || blank(order.getMaterialName())
                || blank(order.getUnitCode()) || order.getOrderQty() == null || order.getUnitPrice() == null
                || order.getExpectedDate() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请完整填写采购订单号、供应商、物料、数量、单位、单价和要求交货日期");
        }
        if (order.getOrderQty().signum() <= 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "订购数量必须大于零");
        }
        if (order.getUnitPrice().signum() < 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "未税单价不能小于零");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
