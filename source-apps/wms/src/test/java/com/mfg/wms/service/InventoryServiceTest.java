package com.mfg.wms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mfg.mdm.entity.Material;
import com.mfg.mdm.repo.MaterialRepository;
import com.mfg.mdm.service.MasterDataService;
import com.mfg.wms.dto.StockTransactionCommand;
import com.mfg.wms.entity.Inventory;
import com.mfg.wms.repo.InventoryRepository;
import com.mfg.wms.repo.StockTransactionRepository;
import com.mfg.wms.repo.WarehouseLocationRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {
    private InventoryRepository inventories;
    private StockTransactionRepository transactions;
    private MaterialRepository materials;
    private MasterDataService master;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        inventories = mock(InventoryRepository.class);
        transactions = mock(StockTransactionRepository.class);
        materials = mock(MaterialRepository.class);
        master = mock(MasterDataService.class);
        service = new InventoryService(inventories, transactions, mock(WarehouseLocationRepository.class), materials, master);
    }

    @Test
    void outboundUsesAvailableBalanceAndLocksTheInventoryRow() {
        Material material = mock(Material.class);
        when(material.getMaterialCode()).thenReturn("M-001");
        when(material.getMaterialName()).thenReturn("伺服驱动器");
        when(materials.findByMaterialCode("M-001")).thenReturn(Optional.of(material));
        doNothing().when(master).assertConsumable(any(), anyString());
        when(transactions.existsByTxnNo("TX-001")).thenReturn(false);

        Inventory stock = new Inventory();
        stock.setOnHandQty(new BigDecimal("10"));
        stock.setAllocatedQty(new BigDecimal("2"));
        stock.setAvailableQty(new BigDecimal("3"));
        stock.setFrozenQty(new BigDecimal("5"));
        stock.setUnitCost(new BigDecimal("12.50"));
        when(inventories.findForUpdate("M-001", "WH-01", null, "LOT-1"))
                .thenReturn(Optional.of(stock));

        StockTransactionCommand command = new StockTransactionCommand("TX-001", "M-001", "WH-01", null,
                "LOT-1", "OUT_ISSUE", new BigDecimal("2"), "PCS", new BigDecimal("12.50"),
                "WO-001", "PO-001", "WO-001");
        service.transact(command);

        assertEquals(new BigDecimal("8"), stock.getOnHandQty());
        assertEquals(new BigDecimal("1"), stock.getAvailableQty());
        assertEquals(new BigDecimal("5"), stock.getFrozenQty());
        verify(inventories).findForUpdate("M-001", "WH-01", null, "LOT-1");
    }
}
