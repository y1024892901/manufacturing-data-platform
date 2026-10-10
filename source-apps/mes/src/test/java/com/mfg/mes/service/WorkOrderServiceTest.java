package com.mfg.mes.service;

import com.mfg.common.exception.BizException;
import com.mfg.mes.entity.ProdResult;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.mes.repo.WorkReportRepository;
import com.mfg.security.scope.ScopedQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {
    @Mock private WorkOrderRepository workOrders;
    @Mock private WorkReportRepository reports;
    @Mock private ScopedQueryService scope;
    @Mock private ProdResultService prodResults;
    @Mock private JdbcTemplate jdbc;
    @InjectMocks private WorkOrderService service;

    @Test
    void createRejectsUnknownErpProductionOrder() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThrows(BizException.class, () -> service.create(validOrder()));
        verify(workOrders, never()).save(any());
        verify(prodResults, never()).lockForOrder(any());
    }

    @Test
    void createRejectsProductMismatchWithErpProductionOrder() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "product_code", "P-OTHER", "plan_qty", new BigDecimal("100"), "order_status", "RELEASED")));

        assertThrows(BizException.class, () -> service.create(validOrder()));
        verify(workOrders, never()).save(any());
    }

    @Test
    void createRejectsErpProductionOrderThatHasNotBeenReleased() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "product_code", "P-001", "plan_qty", new BigDecimal("100"), "order_status", "CREATED")));

        assertThrows(BizException.class, () -> service.create(validOrder()));
        verify(workOrders, never()).save(any());
    }

    private WorkOrder validOrder() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-001");
        order.setProdOrderNo("MO-001");
        order.setProductCode("P-001");
        order.setOpSeq(10);
        order.setOperationCode("OP-10");
        order.setOperationName("总装");
        order.setPlanQty(new BigDecimal("80"));
        return order;
    }
}
