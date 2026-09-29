package com.mfg.mes.service;

import com.mfg.mes.entity.ProdResult;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.entity.WorkReport;
import com.mfg.mes.repo.ProdResultRepository;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.mes.repo.WorkReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Rebuilds the one-row-per-production-order progress summary from its work orders and reports. */
@Service
@RequiredArgsConstructor
public class ProdResultService {
    private final ProdResultRepository results;
    private final WorkOrderRepository workOrders;
    private final WorkReportRepository reports;

    @Transactional
    public ProdResult lockForOrder(WorkOrder order) {
        String orderNo = Objects.requireNonNull(order.getProdOrderNo(), "MES 工单必须关联生产订单").trim();
        results.ensureExistsAndLock(orderNo, order.getProductCode(), order.getPlanQty());
        return results.findByProdOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new IllegalStateException("无法锁定 MES 生产实绩行：" + orderNo));
    }

    @Transactional
    public void synchronizeLocked(String prodOrderNo, ProdResult result) {
        if (prodOrderNo == null || prodOrderNo.isBlank()) return;
        String orderNo = prodOrderNo.trim();
        rebuild(orderNo, workOrders.findAllByProdOrderNo(orderNo), result);
    }

    private void rebuild(String orderNo, List<WorkOrder> orders, ProdResult result) {
        if (orders.isEmpty()) {
            if (result != null) results.delete(result);
            return;
        }
        WorkOrder lastOperation = orders.stream()
                .max(Comparator.comparing(WorkOrder::getOpSeq, Comparator.nullsFirst(Integer::compareTo)))
                .orElseThrow();
        BigDecimal planQty = orders.stream()
                .map(WorkOrder::getPlanQty)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        List<WorkReport> orderReports = reports.findAllByProdOrderNo(orderNo);
        Integer currentOpSeq = orderReports.stream()
                .map(WorkReport::getOpSeq)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        BigDecimal completedQty = sum(orderReports, currentOpSeq, WorkReport::getReportQty);
        BigDecimal qualifiedQty = sum(orderReports, currentOpSeq, WorkReport::getQualifiedQty);

        result.setProductCode(lastOperation.getProductCode());
        result.setTotalPlanQty(planQty);
        result.setTotalCompletedQty(completedQty);
        result.setTotalQualifiedQty(qualifiedQty);
        result.setCurrentOpSeq(currentOpSeq);
        result.setProgressRate(planQty.signum() > 0
                ? completedQty.divide(planQty, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.setLastReportAt(orderReports.stream()
                .map(ProdResultService::reportTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        result.setUpdatedAt(LocalDateTime.now());
        results.save(result);
    }

    private BigDecimal sum(List<WorkReport> reports, Integer opSeq,
                           java.util.function.Function<WorkReport, BigDecimal> quantity) {
        if (opSeq == null) return BigDecimal.ZERO;
        return reports.stream()
                .filter(report -> opSeq.equals(report.getOpSeq()))
                .map(quantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDateTime reportTimestamp(WorkReport report) {
        return report.getUpdatedAt() != null ? report.getUpdatedAt() : report.getCreatedAt();
    }
}
