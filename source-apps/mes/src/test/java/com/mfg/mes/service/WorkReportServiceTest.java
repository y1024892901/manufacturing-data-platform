package com.mfg.mes.service;

import com.mfg.common.exception.BizException;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.entity.WorkReport;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.mes.repo.WorkReportRepository;
import com.mfg.security.scope.ScopedQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkReportServiceTest {
    @Mock private WorkReportRepository reports;
    @Mock private WorkOrderRepository workOrders;
    @Mock private ScopedQueryService scope;
    @Mock private ProdResultService prodResults;
    @InjectMocks private WorkReportService service;

    @Test
    void createStoresReportAndRecalculatesWorkOrder() {
        WorkOrder order = startedOrder();
        List<WorkReport> orderReports = new ArrayList<>();
        when(reports.existsByReportNo("R-001")).thenReturn(false);
        when(workOrders.findByWorkOrderNoForUpdate("WO-001")).thenReturn(Optional.of(order));
        when(reports.findAllByWorkOrderNo("WO-001")).thenAnswer(invocation -> orderReports);
        when(reports.saveAndFlush(any())).thenAnswer(invocation -> {
            WorkReport saved = invocation.getArgument(0);
            saved.setId(1L);
            orderReports.add(saved);
            return saved;
        });
        when(workOrders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkReport input = report("R-001", "WO-001", "3", "1");
        WorkReport saved = service.create(input);

        assertEquals("4", saved.getReportQty().toPlainString());
        assertEquals("4", order.getCompletedQty().toPlainString());
        assertEquals("3", order.getQualifiedQty().toPlainString());
        assertEquals("1", order.getScrapQty().toPlainString());
        assertEquals("STARTED", order.getStatus());
        verify(reports).saveAndFlush(input);
        verify(workOrders).save(order);
        var sequence = inOrder(workOrders, prodResults, scope, reports);
        sequence.verify(workOrders).findByWorkOrderNoForUpdate("WO-001");
        sequence.verify(prodResults).lockForOrder(order);
        sequence.verify(scope).requireVisible(com.mfg.security.scope.ScopedResource.WORK_ORDER, 1L);
        sequence.verify(reports).existsByReportNo("R-001");
        verify(prodResults).synchronizeLocked("PO-001", null);
    }

    @Test
    void createRejectsCumulativeQuantityAbovePlan() {
        WorkOrder order = startedOrder();
        WorkReport existing = report("R-OLD", "WO-001", "8", "1");
        existing.setId(10L);
        existing.setReportQty(new BigDecimal("9"));
        when(reports.existsByReportNo("R-NEW")).thenReturn(false);
        when(workOrders.findByWorkOrderNoForUpdate("WO-001")).thenReturn(Optional.of(order));
        when(reports.findAllByWorkOrderNo("WO-001")).thenReturn(List.of(existing));

        assertThrows(BizException.class, () -> service.create(report("R-NEW", "WO-001", "1", "1")));
        verify(reports, never()).saveAndFlush(any());
        verify(workOrders, never()).save(any());
    }

    @Test
    void deletingLastReportReopensCompletedOrderAndClearsSummary() {
        WorkOrder order = startedOrder();
        order.setStatus("COMPLETED");
        order.setCompletedQty(new BigDecimal("10"));
        order.setQualifiedQty(new BigDecimal("8"));
        order.setScrapQty(new BigDecimal("2"));
        WorkReport current = report("R-010", "WO-001", "8", "2");
        current.setId(10L);
        current.setReportQty(new BigDecimal("10"));
        List<WorkReport> orderReports = new ArrayList<>(List.of(current));
        when(reports.findByIdForUpdate(10L)).thenReturn(Optional.of(current));
        when(workOrders.findByWorkOrderNoForUpdate("WO-001")).thenReturn(Optional.of(order));
        when(reports.findAllByWorkOrderNo("WO-001")).thenAnswer(invocation -> orderReports);
        doAnswer(invocation -> { orderReports.remove(current); return null; }).when(reports).delete(current);
        when(workOrders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(10L);

        assertEquals("STARTED", order.getStatus());
        assertEquals(BigDecimal.ZERO, order.getCompletedQty());
        assertEquals(BigDecimal.ZERO, order.getQualifiedQty());
        assertEquals(BigDecimal.ZERO, order.getScrapQty());
        assertNull(order.getActualEndTime());
        verify(reports).flush();
        verify(workOrders).save(order);
        verify(prodResults).synchronizeLocked("PO-001", null);
    }

    @Test
    void updatingReportRecalculatesWorkOrderAndProductionResult() {
        WorkOrder order = startedOrder();
        order.setStatus("COMPLETED");
        order.setCompletedQty(new BigDecimal("10"));
        order.setActualEndTime(LocalDateTime.parse("2026-09-28T10:00:00"));
        WorkReport current = report("R-011", "WO-001", "8", "2");
        current.setId(11L);
        current.setReportQty(new BigDecimal("10"));
        List<WorkReport> orderReports = new ArrayList<>(List.of(current));
        when(reports.findByIdForUpdate(11L)).thenReturn(Optional.of(current));
        when(workOrders.findByWorkOrderNoForUpdate("WO-001")).thenReturn(Optional.of(order));
        when(reports.existsByReportNoAndIdNot("R-011-EDIT", 11L)).thenReturn(false);
        when(reports.findAllByWorkOrderNo("WO-001")).thenAnswer(invocation -> orderReports);
        when(reports.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workOrders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkReport replacement = report("R-011-EDIT", "WO-001", "6", "0");
        replacement.setWorkHours(new BigDecimal("2.5"));
        service.update(11L, replacement);

        assertEquals("6", order.getCompletedQty().toPlainString());
        assertEquals("6", order.getQualifiedQty().toPlainString());
        assertEquals(BigDecimal.ZERO, order.getScrapQty());
        assertEquals("2.5", order.getActualHours().toPlainString());
        assertEquals("STARTED", order.getStatus());
        assertNull(order.getActualEndTime());
        verify(prodResults).synchronizeLocked("PO-001", null);
        var sequence = inOrder(reports, workOrders, prodResults, scope);
        sequence.verify(reports).findByIdForUpdate(11L);
        sequence.verify(workOrders).findByWorkOrderNoForUpdate("WO-001");
        sequence.verify(prodResults).lockForOrder(order);
        sequence.verify(scope).requireVisible(com.mfg.security.scope.ScopedResource.WORK_REPORT, 11L);
        sequence.verify(reports).existsByReportNoAndIdNot("R-011-EDIT", 11L);
    }

    private WorkOrder startedOrder() {
        WorkOrder order = new WorkOrder();
        order.setId(1L);
        order.setWorkOrderNo("WO-001");
        order.setProdOrderNo("PO-001");
        order.setOpSeq(10);
        order.setPlanQty(new BigDecimal("10"));
        order.setCompletedQty(BigDecimal.ZERO);
        order.setQualifiedQty(BigDecimal.ZERO);
        order.setScrapQty(BigDecimal.ZERO);
        order.setStatus("STARTED");
        return order;
    }

    private WorkReport report(String no, String workOrderNo, String qualified, String scrap) {
        WorkReport report = new WorkReport();
        report.setReportNo(no);
        report.setWorkOrderNo(workOrderNo);
        report.setQualifiedQty(new BigDecimal(qualified));
        report.setScrapQty(new BigDecimal(scrap));
        return report;
    }
}
