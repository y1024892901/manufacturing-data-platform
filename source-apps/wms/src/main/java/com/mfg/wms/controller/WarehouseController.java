package com.mfg.wms.controller;

import com.mfg.common.api.*;
import com.mfg.common.exception.BizException;
import com.mfg.wms.dto.StockTransactionCommand;
import com.mfg.wms.entity.*;
import com.mfg.wms.repo.*;
import com.mfg.wms.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/wms")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseLocationRepository locations;
    private final InventoryRepository inventories;
    private final StockTransactionRepository txns;
    private final InventoryService service;
    private final JdbcTemplate db;

    @GetMapping("/locations")
    @PreAuthorize("hasAuthority('WMS:LOCATION:VIEW')")
    public ApiResponse<Page<WarehouseLocation>> locations(@RequestParam(defaultValue="1") int page,
                                                            @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(locations.findAll(PageRequest.of(Math.max(0,page-1), Math.min(200,Math.max(1,size)))));
    }

    @GetMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('WMS:LOCATION:VIEW')")
    public ApiResponse<WarehouseLocation> location(@PathVariable Long id) {
        return ApiResponse.ok(locations.findById(id).orElseThrow(()->BizException.notFound("库位",id)));
    }

    @PostMapping("/locations")
    @PreAuthorize("hasAuthority('WMS:LOCATION:CREATE')")
    public ApiResponse<WarehouseLocation> createLocation(@Valid @RequestBody WarehouseLocation l) {
        validateLocation(l);
        if (locations.existsByLocationCode(l.getLocationCode())) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS,"库位编码已存在");
        l.setAvailable(true);
        if (l.getLocationType()==null || l.getLocationType().isBlank()) l.setLocationType("GENERAL");
        return ApiResponse.ok(locations.save(l));
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('WMS:LOCATION:UPDATE')")
    public ApiResponse<WarehouseLocation> updateLocation(@PathVariable Long id, @Valid @RequestBody WarehouseLocation input) {
        WarehouseLocation current=locations.findById(id).orElseThrow(()->BizException.notFound("库位",id));
        validateLocation(input);
        if (!current.getLocationCode().equals(input.getLocationCode()) && locations.existsByLocationCode(input.getLocationCode()))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS,"库位编码已存在");
        if (!current.getLocationCode().equals(input.getLocationCode()) && hasStock(current.getLocationCode()))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"库位已有库存，不能修改库位编码");
        current.setLocationCode(input.getLocationCode());
        current.setLocationName(input.getLocationName());
        current.setWarehouseCode(input.getWarehouseCode());
        current.setWarehouseName(input.getWarehouseName());
        current.setLocationType(input.getLocationType()==null || input.getLocationType().isBlank() ? "GENERAL" : input.getLocationType());
        current.setZoneCode(input.getZoneCode()); current.setAisleCode(input.getAisleCode());
        current.setRackCode(input.getRackCode()); current.setBinCode(input.getBinCode());
        current.setMaxWeightKg(input.getMaxWeightKg()); current.setNote(input.getNote());
        return ApiResponse.ok(locations.save(current));
    }

    @DeleteMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('WMS:LOCATION:DELETE')")
    public ApiResponse<Void> deleteLocation(@PathVariable Long id) {
        WarehouseLocation location=locations.findById(id).orElseThrow(()->BizException.notFound("库位",id));
        if (hasStock(location.getLocationCode())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"库位仍有库存，不能删除");
        Long references=db.queryForObject("SELECT (SELECT COUNT(*) FROM src_wms.wms_transfer WHERE from_location=? OR to_location=?)+(SELECT COUNT(*) FROM src_wms.wms_putaway WHERE actual_location=?)",Long.class,location.getLocationCode(),location.getLocationCode(),location.getLocationCode());
        if (references!=null && references>0) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"库位已被历史单据引用，请停用库位而不要删除");
        locations.delete(location);
        return ApiResponse.ok();
    }

    @PatchMapping("/locations/{id}/availability")
    @PreAuthorize("hasAuthority('WMS:LOCATION:UPDATE')")
    public ApiResponse<WarehouseLocation> availability(@PathVariable Long id,@RequestParam boolean enabled) {
        WarehouseLocation l=locations.findById(id).orElseThrow(()->BizException.notFound("库位",id));
        if (!enabled && hasStock(l.getLocationCode())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"库位仍有库存，不能停用");
        l.setAvailable(enabled);
        return ApiResponse.ok(locations.save(l));
    }

    @GetMapping("/inventories")
    @PreAuthorize("hasAuthority('WMS:INVENTORY:VIEW')")
    public ApiResponse<Page<Inventory>> inventories(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(inventories.findAll(PageRequest.of(Math.max(0,page-1),Math.min(200,Math.max(1,size)))));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('WMS:STOCK:VIEW')")
    public ApiResponse<Page<StockTransaction>> transactions(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(txns.findAll(PageRequest.of(Math.max(0,page-1),Math.min(200,Math.max(1,size)))));
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyAuthority('WMS:STOCK:IN','WMS:STOCK:OUT','WMS:STOCK:ADJUST')")
    public ApiResponse<StockTransaction> transaction(@Valid @RequestBody StockTransactionCommand c) { return ApiResponse.ok(service.transact(c)); }

    private boolean hasStock(String locationCode) {
        Long count=db.queryForObject("SELECT COUNT(*) FROM src_wms.wms_inventory WHERE location_code=? AND (on_hand_qty<>0 OR allocated_qty<>0 OR frozen_qty<>0)",Long.class,locationCode);
        return count!=null && count>0;
    }

    private void validateLocation(WarehouseLocation l) {
        if (l.getLocationCode()==null || l.getLocationCode().isBlank() || l.getLocationName()==null || l.getLocationName().isBlank()
            || l.getWarehouseCode()==null || l.getWarehouseCode().isBlank())
            throw BizException.of(ErrorCode.PARAM_INVALID,"库位编码、名称和仓库编码不能为空");
        if (l.getMaxWeightKg()!=null && l.getMaxWeightKg().compareTo(BigDecimal.ZERO)<=0)
            throw BizException.of(ErrorCode.PARAM_INVALID,"额定承重必须大于零");
    }
}
