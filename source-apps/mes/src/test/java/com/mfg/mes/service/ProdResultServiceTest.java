package com.mfg.mes.service;

import com.mfg.mes.entity.ProdResult;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.entity.WorkReport;
import com.mfg.mes.repo.ProdResultRepository;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.mes.repo.WorkReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdResultServiceTest {
    @Mock private ProdResultRepository results;
    @Mock private WorkOrderRepository workOrders;
    @Mock private WorkReportRepository reports;
    @InjectMocks private ProdResultService service;

    @Test
    void lockCreatesAndLocksTheUniqueProductionOrderSummaryBeforeAggregation() {
        WorkOrder order = order("WO-001", "PO-001", 10, "10");
        ProdResult result = new ProdResult();
        result.setProdOrderNo("PO-001");
        when(results.findByProdOrderNoForUpdate("PO-001")).thenReturn(Optional.of(result));

        ProdResult locked = service.lockForOrder(order);

        assertSame(result, locked);
        var sequence = inOrder(results);
        sequence.verify(results).ensureExistsAndLock("PO-001", "P-001", new BigDecimal("10"));
        sequence.verify(results).findByProdOrderNoForUpdate("PO-001");
        verifyNoInteractions(workOrders, reports);
    }

    @Test
    void aggregationUsesTheLatestReportedOperationWithoutDoubleCountingEarlierSteps() {
        WorkOrder first = order("WO-001", "PO-001", 10, "10");
        WorkOrder last = order("WO-002", "PO-001", 20, "10");
        WorkReport firstOperation = report("WO-001", 10, "10", "9", "2026-09-29T08:00:00");
        WorkReport lastOperationOne = report("WO-002", 20, "5", "4", "2026-09-29T09:00:00");
        WorkReport lastOperationTwo = report("WO-002", 20, "2", "2", "2026-09-29T10:00:00");
        lastOperationTwo.setUpdatedAt(LocalDateTime.parse("2026-09-29T10:30:00"));
        when(workOrders.findAllByProdOrderNo("PO-001")).thenReturn(List.of(first, last));
        when(reports.findAllByProdOrderNo("PO-001"))
                .thenReturn(List.of(firstOperation, lastOperationOne, lastOperationTwo));
        when(results.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProdResult result = new ProdResult();
        result.setProdOrderNo("PO-001");
        service.synchronizeLocked("PO-001", result);

        assertEquals("P-001", result.getProductCode());
        assertEquals("10", result.getTotalPlanQty().toPlainString());
        assertEquals("7", result.getTotalCompletedQty().toPlainString());
        assertEquals("6", result.getTotalQualifiedQty().toPlainString());
        assertEquals(Integer.valueOf(20), result.getCurrentOpSeq());
        assertEquals(new BigDecimal("0.7000"), result.getProgressRate());
        assertEquals(LocalDateTime.parse("2026-09-29T10:30:00"), result.getLastReportAt());
        verify(results).save(result);
    }

    @Test
    void removingAllReportsResetsTheProductionOrderSummary() {
        WorkOrder order = order("WO-001", "PO-001", 10, "10");
        ProdResult result = new ProdResult();
        result.setProdOrderNo("PO-001");
        result.setTotalPlanQty(new BigDecimal("10"));
        result.setTotalCompletedQty(new BigDecimal("10"));
        result.setTotalQualifiedQty(new BigDecimal("9"));
        result.setProgressRate(BigDecimal.ONE);
        result.setCurrentOpSeq(10);
        result.setLastReportAt(LocalDateTime.parse("2026-09-29T08:00:00"));
        when(workOrders.findAllByProdOrderNo("PO-001")).thenReturn(List.of(order));
        when(reports.findAllByProdOrderNo("PO-001")).thenReturn(List.of());
        when(results.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.synchronizeLocked("PO-001", result);

        assertEquals(BigDecimal.ZERO, result.getTotalCompletedQty());
        assertEquals(BigDecimal.ZERO, result.getTotalQualifiedQty());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getProgressRate()));
        assertNull(result.getCurrentOpSeq());
        assertNull(result.getLastReportAt());
        verify(results).save(result);
    }

    private WorkOrder order(String workOrderNo, String prodOrderNo, int opSeq, String planQty) {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo(workOrderNo);
        order.setProdOrderNo(prodOrderNo);
        order.setProductCode("P-001");
        order.setOpSeq(opSeq);
        order.setPlanQty(new BigDecimal(planQty));
        return order;
    }

    private WorkReport report(String workOrderNo, int opSeq, String total, String qualified, String at) {
        WorkReport report = new WorkReport();
        report.setWorkOrderNo(workOrderNo);
        report.setOpSeq(opSeq);
        report.setReportQty(new BigDecimal(total));
        report.setQualifiedQty(new BigDecimal(qualified));
        LocalDateTime time = LocalDateTime.parse(at);
        report.setCreatedAt(time);
        report.setUpdatedAt(time);
        return report;
    }
}
