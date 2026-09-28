package com.mfg.mes.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mes.entity.AndonEvent;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.repo.AndonEventRepository;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AndonEventService {
    private final AndonEventRepository events;
    private final WorkOrderRepository workOrders;
    private final ScopedQueryService scope;

    @Transactional(readOnly = true)
    public Page<AndonEvent> page(int page, int size) {
        return scope.page(ScopedResource.ANDON_EVENT, AndonEvent.class, page, size, null, null);
    }

    @Transactional(readOnly = true)
    public AndonEvent get(Long id) {
        scope.requireVisible(ScopedResource.ANDON_EVENT, id);
        return events.findById(id).orElseThrow(() -> BizException.notFound("Andon异常单", id));
    }

    @Transactional
    public AndonEvent create(AndonEvent input) {
        validate(input);
        input.setEventNo(input.getEventNo().trim());
        if (events.existsByEventNo(input.getEventNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "Andon异常单号已存在");
        }
        fillWorkOrder(input);
        input.setReportedBy(CurrentUser.usernameOrSystem());
        input.setReportedAt(LocalDateTime.now());
        input.setCreatedAt(input.getReportedAt());
        input.setUpdatedAt(input.getReportedAt());
        input.setStatus("OPEN");
        input.setAffectedQty(input.getAffectedQty() == null ? null : input.getAffectedQty().stripTrailingZeros());
        input.setDowntimeMinutes(input.getDowntimeMinutes() == null ? 0 : input.getDowntimeMinutes());
        return events.save(input);
    }

    @Transactional
    public AndonEvent update(Long id, AndonEvent input) {
        AndonEvent current = lockVisible(id);
        if (!"OPEN".equals(current.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "已响应的 Andon 异常不能修改基础信息");
        }
        validate(input);
        String eventNo = input.getEventNo().trim();
        if (events.existsByEventNoAndIdNot(eventNo, id)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "Andon异常单号已被其他单据使用");
        }
        input.setEventNo(eventNo);
        fillWorkOrder(input);
        current.setEventNo(input.getEventNo());
        current.setEventType(input.getEventType().trim());
        current.setSeverity(input.getSeverity().trim());
        current.setWorkOrderNo(blankToNull(input.getWorkOrderNo()));
        current.setProdOrderNo(blankToNull(input.getProdOrderNo()));
        current.setEquipmentCode(blankToNull(input.getEquipmentCode()));
        current.setWorkshopCode(blankToNull(input.getWorkshopCode()));
        current.setDescription(input.getDescription().trim());
        current.setAffectedQty(input.getAffectedQty());
        current.setDowntimeMinutes(input.getDowntimeMinutes());
        current.setResponseDueAt(input.getResponseDueAt());
        current.setUpdatedAt(LocalDateTime.now());
        return events.save(current);
    }

    @Transactional
    public void delete(Long id) {
        AndonEvent event = lockVisible(id);
        if (!"OPEN".equals(event.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "已响应的 Andon 异常不能删除");
        }
        events.delete(event);
    }

    @Transactional
    public AndonEvent respond(Long id, String note) {
        AndonEvent event = lockVisible(id);
        requireStatus(event, "OPEN", "只有待响应异常可以响应");
        event.setResponseBy(CurrentUser.usernameOrSystem());
        event.setResponseAt(LocalDateTime.now());
        event.setResponseNote(blankToNull(note));
        event.setStatus("RESPONDED");
        event.setUpdatedAt(LocalDateTime.now());
        return events.save(event);
    }

    @Transactional
    public AndonEvent resolve(Long id, String resolution, Integer downtimeMinutes) {
        AndonEvent event = lockVisible(id);
        requireStatus(event, "RESPONDED", "请先响应异常，再登记处理结果");
        if (resolution == null || resolution.isBlank()) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "处理方案与结果不能为空");
        }
        if (downtimeMinutes != null && downtimeMinutes < 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "停线时长不能小于 0");
        }
        event.setResolution(resolution.trim());
        if (downtimeMinutes != null) event.setDowntimeMinutes(downtimeMinutes);
        event.setResolvedBy(CurrentUser.usernameOrSystem());
        event.setResolvedAt(LocalDateTime.now());
        event.setStatus("RESOLVED");
        event.setUpdatedAt(LocalDateTime.now());
        return events.save(event);
    }

    @Transactional
    public AndonEvent close(Long id) {
        AndonEvent event = lockVisible(id);
        requireStatus(event, "RESOLVED", "只有已处理异常可以关闭");
        event.setStatus("CLOSED");
        event.setUpdatedAt(LocalDateTime.now());
        return events.save(event);
    }

    @Transactional
    public AndonEvent lockVisible(Long id) {
        scope.requireVisible(ScopedResource.ANDON_EVENT, id);
        return events.findByIdForUpdate(id).orElseThrow(() -> BizException.notFound("Andon异常单", id));
    }

    private void validate(AndonEvent input) {
        if (input == null) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "Andon异常单内容不能为空");
        require(input.getEventNo(), "异常单号");
        require(input.getEventType(), "异常类型");
        require(input.getSeverity(), "异常等级");
        require(input.getDescription(), "异常描述");
        if (input.getAffectedQty() != null && input.getAffectedQty().signum() < 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "影响数量不能小于 0");
        }
        if (input.getDowntimeMinutes() != null && input.getDowntimeMinutes() < 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "停线时长不能小于 0");
        }
    }

    private void fillWorkOrder(AndonEvent event) {
        String workOrderNo = blankToNull(event.getWorkOrderNo());
        if (workOrderNo == null) return;
        WorkOrder order = workOrders.findByWorkOrderNo(workOrderNo)
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联 MES 工单不存在"));
        scope.requireVisible(ScopedResource.WORK_ORDER, order.getId());
        event.setProdOrderNo(event.getProdOrderNo() == null || event.getProdOrderNo().isBlank()
                ? order.getProdOrderNo() : event.getProdOrderNo().trim());
        event.setEquipmentCode(event.getEquipmentCode() == null || event.getEquipmentCode().isBlank()
                ? order.getEquipmentCode() : event.getEquipmentCode().trim());
        event.setWorkshopCode(event.getWorkshopCode() == null || event.getWorkshopCode().isBlank()
                ? order.getWorkshopCode() : event.getWorkshopCode().trim());
    }

    private void requireStatus(AndonEvent event, String expected, String message) {
        if (!expected.equals(event.getStatus())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, message);
    }

    private String require(String value, String label) {
        if (value == null || value.isBlank()) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, label + "不能为空");
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
