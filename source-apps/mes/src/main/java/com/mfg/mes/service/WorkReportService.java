package com.mfg.mes.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.entity.WorkReport;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.mes.repo.WorkReportRepository;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkReportService {
    private final WorkReportRepository reports;
    private final WorkOrderRepository workOrders;
    private final ScopedQueryService scope;

    @Transactional(readOnly = true)
    public Page<WorkReport> page(int page, int size) {
        return scope.page(ScopedResource.WORK_REPORT, WorkReport.class, page, size, null, null);
    }

    @Transactional(readOnly = true)
    public WorkReport get(Long id) {
        scope.requireVisible(ScopedResource.WORK_REPORT, id);
        return reports.findById(id).orElseThrow(() -> BizException.notFound("MES报工单", id));
    }

    @Transactional
    public WorkReport create(WorkReport input) {
        validateReportNo(input);
        WorkOrder order = lockOrder(input.getWorkOrderNo());
        if (!"STARTED".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有已开工工单可以登记新报工");
        }
        normalizeFromOrder(input, order, true);
        ensureWithinPlan(order, null, input);
        input.setCreatedBy(CurrentUser.usernameOrSystem());
        input.setOperatorCode(CurrentUser.usernameOrSystem());
        input.setCreatedAt(LocalDateTime.now());
        input.setUpdatedBy(input.getCreatedBy());
        input.setUpdatedAt(input.getCreatedAt());
        WorkReport saved = reports.saveAndFlush(input);
        synchronizeOrder(order);
        return saved;
    }

    @Transactional
    public WorkReport update(Long id, WorkReport input) {
        scope.requireVisible(ScopedResource.WORK_REPORT, id);
        WorkReport current = reports.findById(id).orElseThrow(() -> BizException.notFound("MES报工单", id));
        WorkOrder order = lockOrder(current.getWorkOrderNo());
        if (!"STARTED".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "当前工单状态不能修改报工");
        }
        if (input == null) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "报工单内容不能为空");
        String reportNo = required(input.getReportNo(), "报工单号");
        if (reports.existsByReportNoAndIdNot(reportNo, id)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "报工单号已被其他单据使用");
        }
        normalizeFromOrder(input, order, false);
        ensureWithinPlan(order, current, input);
        current.setReportNo(reportNo);
        current.setQualifiedQty(input.getQualifiedQty());
        current.setScrapQty(input.getScrapQty());
        current.setReportQty(input.getReportQty());
        current.setReportDate(input.getReportDate());
        current.setStartTime(input.getStartTime());
        current.setEndTime(input.getEndTime());
        current.setWorkHours(input.getWorkHours());
        current.setEquipmentCode(blankToNull(input.getEquipmentCode()));
        current.setShiftCode(blankToNull(input.getShiftCode()));
        current.setPaused(input.getPaused());
        current.setPauseReason(blankToNull(input.getPauseReason()));
        current.setPauseMinutes(input.getPauseMinutes());
        current.setRemark(blankToNull(input.getRemark()));
        current.setUpdatedBy(CurrentUser.usernameOrSystem());
        current.setUpdatedAt(LocalDateTime.now());
        WorkReport saved = reports.saveAndFlush(current);
        synchronizeOrder(order);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        scope.requireVisible(ScopedResource.WORK_REPORT, id);
        WorkReport current = reports.findById(id).orElseThrow(() -> BizException.notFound("MES报工单", id));
        WorkOrder order = lockOrder(current.getWorkOrderNo());
        if (!"STARTED".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "当前工单状态不能删除报工");
        }
        scope.requireVisible(ScopedResource.WORK_ORDER, order.getId());
        reports.delete(current);
        reports.flush();
        synchronizeOrder(order);
    }

    @Transactional
    public WorkReport createFromWorkOrder(Long workOrderId, BigDecimal qualifiedQty, BigDecimal scrapQty) {
        WorkOrder order = workOrders.findByIdForUpdate(workOrderId)
                .orElseThrow(() -> BizException.notFound("MES工单", workOrderId));
        scope.requireVisible(ScopedResource.WORK_ORDER, workOrderId);
        WorkReport report = new WorkReport();
        report.setReportNo("BG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        report.setWorkOrderNo(order.getWorkOrderNo());
        report.setQualifiedQty(qualifiedQty);
        report.setScrapQty(scrapQty);
        return create(report);
    }

    private WorkOrder lockOrder(String workOrderNo) {
        String key = required(workOrderNo, "MES工单号");
        WorkOrder order = workOrders.findByWorkOrderNoForUpdate(key)
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "MES工单不存在"));
        scope.requireVisible(ScopedResource.WORK_ORDER, order.getId());
        return order;
    }

    private void normalizeFromOrder(WorkReport report, WorkOrder order, boolean creating) {
        if (report == null) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "报工单内容不能为空");
        report.setReportNo(required(report.getReportNo(), "报工单号"));
        if (!creating && report.getWorkOrderNo() != null && !report.getWorkOrderNo().isBlank()
                && !order.getWorkOrderNo().equals(report.getWorkOrderNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "报工关联工单不可修改");
        }
        BigDecimal qualified = report.getQualifiedQty() == null ? BigDecimal.ZERO : report.getQualifiedQty();
        BigDecimal scrap = report.getScrapQty() == null ? BigDecimal.ZERO : report.getScrapQty();
        if (qualified.signum() < 0 || scrap.signum() < 0 || qualified.add(scrap).signum() <= 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "合格数量与报废数量不能为负，且报工总数必须大于 0");
        }
        report.setQualifiedQty(qualified);
        report.setScrapQty(scrap);
        report.setReportQty(qualified.add(scrap));
        report.setWorkOrderNo(order.getWorkOrderNo());
        report.setProdOrderNo(order.getProdOrderNo());
        report.setOpSeq(order.getOpSeq());
        report.setReportDate(report.getReportDate() == null ? LocalDate.now() : report.getReportDate());
        report.setEquipmentCode(report.getEquipmentCode() == null ? order.getEquipmentCode() : blankToNull(report.getEquipmentCode()));
        report.setPaused(report.getPaused() == null ? Boolean.FALSE : report.getPaused());
        report.setPauseMinutes(report.getPauseMinutes() == null ? 0 : report.getPauseMinutes());
        if (report.getPauseMinutes() < 0) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "停机分钟数不能小于 0");
        if (report.getWorkHours() != null && report.getWorkHours().signum() < 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "工时不能小于 0");
        }
        if (report.getStartTime() != null && report.getEndTime() != null && report.getEndTime().isBefore(report.getStartTime())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "结束时间不能早于开始时间");
        }
    }

    private void ensureWithinPlan(WorkOrder order, WorkReport old, WorkReport replacement) {
        BigDecimal otherQty = reports.findAllByWorkOrderNo(order.getWorkOrderNo()).stream()
                .filter(row -> old == null || !row.getId().equals(old.getId()))
                .map(row -> row.getReportQty() == null ? BigDecimal.ZERO : row.getReportQty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (otherQty.add(replacement.getReportQty()).compareTo(order.getPlanQty()) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "累计报工数量不能超过工单计划数量");
        }
    }

    private void synchronizeOrder(WorkOrder order) {
        var details = reports.findAllByWorkOrderNo(order.getWorkOrderNo());
        BigDecimal completed = details.stream().map(WorkReport::getReportQty)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal qualified = details.stream().map(WorkReport::getQualifiedQty)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal scrap = details.stream().map(WorkReport::getScrapQty)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal hours = details.stream().map(WorkReport::getWorkHours)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setCompletedQty(completed);
        order.setQualifiedQty(qualified);
        order.setScrapQty(scrap);
        order.setActualHours(details.stream().anyMatch(row -> row.getWorkHours() != null) ? hours : null);
        if (completed.compareTo(order.getPlanQty()) == 0) {
            order.setStatus("COMPLETED");
            if (order.getActualEndTime() == null) order.setActualEndTime(LocalDateTime.now());
        } else if ("COMPLETED".equals(order.getStatus())) {
            order.setStatus("STARTED");
            order.setActualEndTime(null);
        }
        order.setUpdatedAt(LocalDateTime.now());
        workOrders.save(order);
    }

    private void validateReportNo(WorkReport input) {
        if (input == null) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "报工单内容不能为空");
        String reportNo = required(input.getReportNo(), "报工单号");
        if (reports.existsByReportNo(reportNo)) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "报工单号已存在");
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, label + "不能为空");
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
