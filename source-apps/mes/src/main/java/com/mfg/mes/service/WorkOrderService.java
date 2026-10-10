package com.mfg.mes.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mes.entity.ProdResult;
import com.mfg.mes.entity.WorkOrder;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.mes.repo.WorkReportRepository;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WorkOrderService {
    private final WorkOrderRepository workOrders;
    private final WorkReportRepository reports;
    private final ScopedQueryService scope;
    private final ProdResultService prodResults;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<WorkOrder> page(int page, int size) {
        return scope.page(ScopedResource.WORK_ORDER, WorkOrder.class, page, size, null, null);
    }

    @Transactional(readOnly = true)
    public WorkOrder get(Long id) {
        scope.requireVisible(ScopedResource.WORK_ORDER, id);
        return workOrders.findById(id).orElseThrow(() -> BizException.notFound("MES工单", id));
    }

    @Transactional
    public WorkOrder create(WorkOrder input) {
        validateHeader(input);
        ProdResult lockedResult = prodResults.lockForOrder(input);
        if (workOrders.existsByWorkOrderNo(input.getWorkOrderNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "工单号已存在");
        }
        input.setStatus("CREATED");
        input.setCompletedQty(BigDecimal.ZERO);
        input.setQualifiedQty(BigDecimal.ZERO);
        input.setScrapQty(BigDecimal.ZERO);
        input.setActualStartTime(null);
        input.setActualEndTime(null);
        input.setActualHours(null);
        input.setWorkshopUser(CurrentUser.usernameOrSystem());
        input.setCreatedAt(LocalDateTime.now());
        input.setUpdatedAt(input.getCreatedAt());
        WorkOrder saved = workOrders.save(input);
        prodResults.synchronizeLocked(saved.getProdOrderNo(), lockedResult);
        return saved;
    }

    @Transactional
    public WorkOrder update(Long id, WorkOrder input) {
        WorkOrder current = lockVisible(id);
        if (!"CREATED".equals(current.getStatus()) && !"RELEASED".equals(current.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "工单开工后不可修改工艺与派工信息");
        }
        validateHeader(input);
        if (workOrders.existsByWorkOrderNoAndIdNot(input.getWorkOrderNo(), id)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "工单号已被其他单据使用");
        }
        if (input.getPlanQty().compareTo(current.getCompletedQty()) < 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "计划数量不能小于已报工数量");
        }
        String previousProdOrderNo = current.getProdOrderNo();
        ProdResult oldResult = prodResults.lockForOrder(current);
        ProdResult newResult = previousProdOrderNo.equals(input.getProdOrderNo())
                ? oldResult : prodResults.lockForOrder(input);
        copyEditableFields(input, current);
        current.setUpdatedAt(LocalDateTime.now());
        WorkOrder saved = workOrders.save(current);
        if (!saved.getProdOrderNo().equals(previousProdOrderNo)) {
            prodResults.synchronizeLocked(previousProdOrderNo, oldResult);
        }
        prodResults.synchronizeLocked(saved.getProdOrderNo(), newResult);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        WorkOrder current = lockVisible(id);
        if (!"CREATED".equals(current.getStatus()) && !"RELEASED".equals(current.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "已开工或已完工工单不能删除");
        }
        ProdResult lockedResult = prodResults.lockForOrder(current);
        if (reports.existsByWorkOrderNo(current.getWorkOrderNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "工单已有报工记录，不能删除");
        }
        String prodOrderNo = current.getProdOrderNo();
        workOrders.delete(current);
        workOrders.flush();
        prodResults.synchronizeLocked(prodOrderNo, lockedResult);
    }

    @Transactional
    public WorkOrder start(Long id) {
        WorkOrder current = lockVisible(id);
        if (!"CREATED".equals(current.getStatus()) && !"RELEASED".equals(current.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "当前工单状态不能开工");
        }
        current.setStatus("STARTED");
        current.setActualStartTime(LocalDateTime.now());
        current.setUpdatedAt(LocalDateTime.now());
        return workOrders.save(current);
    }

    @Transactional
    public WorkOrder lockVisible(Long id) {
        scope.requireVisible(ScopedResource.WORK_ORDER, id);
        return workOrders.findByIdForUpdate(id).orElseThrow(() -> BizException.notFound("MES工单", id));
    }

    private void validateHeader(WorkOrder input) {
        if (input == null) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "工单内容不能为空");
        input.setWorkOrderNo(required(input.getWorkOrderNo(), "工单号"));
        input.setProdOrderNo(required(input.getProdOrderNo(), "生产订单号"));
        input.setProductCode(required(input.getProductCode(), "产品编码"));
        if (input.getOpSeq() == null || input.getOpSeq() <= 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "工序序号必须大于 0");
        }
        input.setOperationCode(required(input.getOperationCode(), "工序编码"));
        input.setOperationName(required(input.getOperationName(), "工序名称"));
        if (input.getPlanQty() == null || input.getPlanQty().signum() <= 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "计划数量必须大于 0");
        }
        if (input.getPlanHours() != null && input.getPlanHours().signum() < 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "计划工时不能小于 0");
        }
        if (input.getPlanStartTime() != null && input.getPlanEndTime() != null
                && input.getPlanEndTime().isBefore(input.getPlanStartTime())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "计划完工时间不能早于计划开工时间");
        }
        validateProductionOrder(input);
        validatePublishedRoutingOperation(input);
    }

    /** MES工单必须来自已下达的ERP生产订单，不能使用自由填写或错配的订单号。 */
    private void validateProductionOrder(WorkOrder input) {
        var orders = jdbc.queryForList(
                "SELECT product_code, plan_qty, order_status FROM src_erp.erp_prod_order WHERE prod_order_no=?",
                input.getProdOrderNo());
        if (orders.isEmpty()) {
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联的 ERP 生产订单不存在");
        }
        var order = orders.get(0);
        if (!"RELEASED".equals(String.valueOf(order.get("order_status")))) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "ERP 生产订单尚未下达，不能创建或修改 MES 工单");
        }
        if (!input.getProductCode().equals(String.valueOf(order.get("product_code")))) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "MES 工单产品必须与 ERP 生产订单一致");
        }
        Object plan = order.get("plan_qty");
        if (plan instanceof BigDecimal orderQty && input.getPlanQty().compareTo(orderQty) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "工单计划数量不能超过 ERP 生产订单数量");
        }
        Integer materialCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM src_mes.mes_md_material WHERE material_code=?", Integer.class, input.getProductCode());
        if (materialCount == null || materialCount == 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_PUBLISHED, "产品尚未同步到 MES 主数据副本，请先在 MDM 发布并分发");
        }
    }

    /** 指定工艺路线时，工序必须来自 MDM 已发布并分发到 MES 的路线版本。 */
    private void validatePublishedRoutingOperation(WorkOrder input) {
        String routingCode = blankToNull(input.getRoutingCode());
        String routingVersion = blankToNull(input.getRoutingVersion());
        if (routingCode == null && routingVersion == null) return;
        if (routingCode == null || routingVersion == null) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "选择工艺路线时，路线编码和版本必须同时填写");
        }
        Integer operationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM src_mes.mes_md_routing_operation " +
                        "WHERE routing_code=? AND routing_version=? AND op_seq=? AND operation_code=?",
                Integer.class, routingCode, routingVersion, input.getOpSeq(), input.getOperationCode());
        if (operationCount == null || operationCount == 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_PUBLISHED, "所选工序不在 MDM 已分发的工艺路线版本中");
        }
    }

    private void copyEditableFields(WorkOrder from, WorkOrder to) {
        to.setWorkOrderNo(from.getWorkOrderNo());
        to.setProdOrderNo(blankToNull(from.getProdOrderNo()));
        to.setProductCode(from.getProductCode());
        to.setOpSeq(from.getOpSeq());
        to.setOperationCode(from.getOperationCode());
        to.setOperationName(from.getOperationName());
        to.setEquipmentCode(blankToNull(from.getEquipmentCode()));
        to.setPlanQty(from.getPlanQty());
        to.setRoutingCode(blankToNull(from.getRoutingCode()));
        to.setRoutingVersion(blankToNull(from.getRoutingVersion()));
        to.setWorkCenter(blankToNull(from.getWorkCenter()));
        to.setWorkshopCode(blankToNull(from.getWorkshopCode()));
        to.setPlanStartTime(from.getPlanStartTime());
        to.setPlanEndTime(from.getPlanEndTime());
        to.setPlanHours(from.getPlanHours());
        if (from.getWorkshopUser() != null && !from.getWorkshopUser().isBlank()) {
            to.setWorkshopUser(from.getWorkshopUser().trim());
        }
        to.setPriorityLevel(blankToNull(from.getPriorityLevel()));
        to.setBatchNo(blankToNull(from.getBatchNo()));
        to.setShiftCode(blankToNull(from.getShiftCode()));
        to.setRemark(blankToNull(from.getRemark()));
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, label + "不能为空");
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
