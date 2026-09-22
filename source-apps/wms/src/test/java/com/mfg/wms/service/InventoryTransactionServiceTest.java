package com.mfg.wms.service;

import com.mfg.common.exception.BizException;
import com.mfg.wms.dto.InventoryActionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryTransactionServiceTest {
    private JdbcTemplate db;
    private InventoryTransactionService service;

    @BeforeEach void init() { db = mock(JdbcTemplate.class); service = new InventoryTransactionService(db); }

    @Test void blocksNegativeInventory() {
        when(db.queryForList(contains("FROM src_wms.wms_inventory WHERE"), any(), any(), any(), any()))
                .thenReturn(List.of(stock("10", "8", "2")));
        when(db.queryForObject(contains("wms_inventory_ledger"), eq(Integer.class), any())).thenReturn(0);
        assertThrows(BizException.class, () -> service.action("SCRAP", command("NEG", new BigDecimal("11"))));
        verify(db, never()).update(startsWith("UPDATE src_wms.wms_inventory"), any(Object[].class));
    }

    @Test void returnsOriginalLedgerForRepeatedIdempotencyKey() {
        when(db.queryForList(contains("FROM src_wms.wms_inventory WHERE"), any(), any(), any(), any()))
                .thenReturn(List.of(stock("10", "10", "0")));
        when(db.queryForObject(contains("wms_inventory_ledger"), eq(Integer.class), any())).thenReturn(1);
        Map<String,Object> ledger = new HashMap<>(Map.of("id", 9L, "idempotency_key", "SAME", "action_type", "FREEZE", "material_code", "M1", "warehouse_code", "WH1", "location_code", "L1", "batch_no", "B1", "source_system", "TEST", "source_no", "S1", "available_delta", BigDecimal.ONE.negate()));
        doReturn(List.of(ledger)).when(db).queryForList(contains("FROM src_wms.wms_inventory_ledger"), any(Object[].class));
        assertEquals(9L, service.action("FREEZE", command("SAME", BigDecimal.ONE)).get("id"));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    private Map<String,Object> stock(String onHand, String available, String frozen) {
        return new HashMap<>(Map.of("id", 1L, "on_hand_qty", new BigDecimal(onHand), "available_qty", new BigDecimal(available), "frozen_qty", new BigDecimal(frozen), "quality_status", "AVAILABLE"));
    }
    @Test void rejectsNegativeQuantityBeforeAnyDatabaseAccess() {
        assertThrows(BizException.class, () -> service.action("FREEZE", command("BAD", BigDecimal.ONE.negate())));
        verifyNoInteractions(db);
    }
    @Test void rejectsExcessPrecisionBeforeAnyDatabaseAccess() {
        assertThrows(BizException.class, () -> service.action("FREEZE", command("BAD", new BigDecimal("0.00001"))));
        verifyNoInteractions(db);
    }
    @Test void rejectsReusedKeyWithDifferentQuantity() {
        when(db.queryForObject(contains("wms_inventory_ledger"), eq(Integer.class), any())).thenReturn(1);
        Map<String,Object> ledger=Map.of("action_type","FREEZE","material_code","M1","warehouse_code","WH1","location_code","L1","batch_no","B1","source_system","TEST","source_no","S1","available_delta",BigDecimal.ONE.negate());
        doReturn(List.of(ledger)).when(db).queryForList(contains("FROM src_wms.wms_inventory_ledger"), any(Object[].class));
        assertThrows(BizException.class, () -> service.action("FREEZE", command("SAME", new BigDecimal("2"))));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void rejectsEmptyCount() {
        reviewingCount(List.of());
        assertTrue(assertThrows(BizException.class, () -> service.postCount(1L,"COUNT")).getMessage().contains("没有明细"));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void rejectsUnreviewedCountLine() {
        reviewingCount(List.of(Map.of("review_status","PENDING")));
        assertTrue(assertThrows(BizException.class, () -> service.postCount(1L,"COUNT")).getMessage().contains("完成复核"));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void rejectsChangedStockSinceCount() {
        reviewingCount(List.of(Map.of("review_status","APPROVED","actual_qty",new BigDecimal("9"),"book_qty",BigDecimal.TEN,"difference_qty",BigDecimal.ONE.negate(),"material_code","M1","location_code","L1","batch_no","B1")));
        doReturn(List.of(stock("11","11","0"))).when(db).queryForList(contains("FROM src_wms.wms_inventory WHERE"), any(Object[].class));
        assertTrue(assertThrows(BizException.class, () -> service.postCount(1L,"COUNT")).getMessage().contains("库存已变化"));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    private void reviewingCount(List<Map<String,Object>> lines) {
        doReturn(List.of(Map.of("status","REVIEWING","warehouse_code","WH1","count_no","C1"))).when(db).queryForList(contains("FROM src_wms.wms_count_plan"), any(Object[].class));
        doReturn(lines).when(db).queryForList(contains("FROM src_wms.wms_count_line"), any(Object[].class));
    }
    private InventoryActionCommand command(String key, BigDecimal quantity) {
        return new InventoryActionCommand(key, "M1", "WH1", "L1", "B1", quantity, "TEST", "S1", "验收");
    }
}
