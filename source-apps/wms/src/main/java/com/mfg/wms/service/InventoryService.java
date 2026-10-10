package com.mfg.wms.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mdm.entity.Material;
import com.mfg.mdm.repo.MaterialRepository;
import com.mfg.mdm.service.MasterDataService;
import com.mfg.security.config.CurrentUser;
import com.mfg.wms.dto.StockTransactionCommand;
import com.mfg.wms.entity.Inventory;
import com.mfg.wms.entity.StockTransaction;
import com.mfg.wms.entity.WarehouseLocation;
import com.mfg.wms.repo.InventoryRepository;
import com.mfg.wms.repo.StockTransactionRepository;
import com.mfg.wms.repo.WarehouseLocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 库存余额只能由事务驱动；禁止页面直接改数量。 */
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventories;
    private final StockTransactionRepository txns;
    private final WarehouseLocationRepository locations;
    private final MaterialRepository materials;
    private final MasterDataService master;

    @Transactional
    public StockTransaction transact(StockTransactionCommand command) {
        if (txns.existsByTxnNo(command.txnNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "库存流水号已存在");
        }

        Material material = materials.findByMaterialCode(command.materialCode())
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND,
                        "物料不存在：" + command.materialCode()));
        master.assertConsumable(material, "WMS 库存事务");

        if (command.locationCode() != null && !command.locationCode().isBlank()) {
            WarehouseLocation location = locations.findByLocationCode(command.locationCode())
                    .orElseThrow(() -> BizException.notFound("库位", command.locationCode()));
            if (!Boolean.TRUE.equals(location.getAvailable())) {
                throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "库位已禁用");
            }
            if (!Objects.equals(location.getWarehouseCode(), command.warehouseCode())) {
                throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "库位不属于所选仓库");
            }
        }

        boolean inbound = command.txnType().startsWith("IN_");
        if (!inbound && !command.txnType().startsWith("OUT_") && !"ADJUST".equals(command.txnType())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,
                    "库存类型必须为 IN_*、OUT_* 或 ADJUST");
        }

        Inventory inventory = inventories.findForUpdate(command.materialCode(), command.warehouseCode(),
                        command.locationCode(), command.batchNo())
                .orElseGet(() -> newInventory(material, command));
        BigDecimal signed = inbound ? command.txnQty() : command.txnQty().negate();
        BigDecimal currentAvailable = zero(inventory.getAvailableQty());
        if (signed.signum() < 0 && currentAvailable.compareTo(command.txnQty()) < 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "可用库存不足，禁止负库存出库");
        }

        inventory.setOnHandQty(zero(inventory.getOnHandQty()).add(signed));
        // 可用量单独维护；不能从在库量反推，否则会把冻结库存重新算成可用库存。
        inventory.setAvailableQty(currentAvailable.add(signed));
        inventory.setUnitCost(command.unitCost());
        inventory.setTotalCost(inventory.getOnHandQty().multiply(command.unitCost())
                .setScale(2, RoundingMode.HALF_UP));
        inventories.save(inventory);

        StockTransaction transaction = new StockTransaction();
        transaction.setTxnNo(command.txnNo());
        transaction.setMaterialCode(command.materialCode());
        transaction.setWarehouseCode(command.warehouseCode());
        transaction.setLocationCode(command.locationCode());
        transaction.setBatchNo(command.batchNo());
        transaction.setTxnType(command.txnType());
        transaction.setDirection(inbound ? "IN" : "OUT");
        transaction.setTxnQty(command.txnQty());
        transaction.setUnitCode(command.unitCode());
        transaction.setUnitCost(command.unitCost());
        transaction.setSourceNo(command.sourceNo());
        transaction.setProdOrderNo(command.prodOrderNo());
        transaction.setWorkOrderNo(command.workOrderNo());
        transaction.setTxnDate(LocalDate.now());
        transaction.setOperatorCode(CurrentUser.usernameOrSystem());
        return txns.save(transaction);
    }

    private Inventory newInventory(Material material, StockTransactionCommand command) {
        Inventory inventory = new Inventory();
        inventory.setMaterialCode(material.getMaterialCode());
        inventory.setMaterialName(material.getMaterialName());
        inventory.setWarehouseCode(command.warehouseCode());
        inventory.setLocationCode(command.locationCode());
        inventory.setBatchNo(command.batchNo());
        inventory.setUnitCode(command.unitCode());
        inventory.setOnHandQty(BigDecimal.ZERO);
        inventory.setAllocatedQty(BigDecimal.ZERO);
        inventory.setAvailableQty(BigDecimal.ZERO);
        inventory.setFrozenQty(BigDecimal.ZERO);
        return inventory;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
