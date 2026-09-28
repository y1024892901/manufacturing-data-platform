package com.mfg.erp.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.common.integration.BusinessEventService;
import com.mfg.erp.dto.ProductionOrderCommand;
import com.mfg.erp.entity.ProductionOrder;
import com.mfg.erp.repo.ProductionOrderRepository;
import com.mfg.erp.repo.SalesOrderRepository;
import com.mfg.mdm.entity.Bom;
import com.mfg.mdm.entity.Material;
import com.mfg.mdm.repo.BomRepository;
import com.mfg.mdm.repo.MaterialRepository;
import com.mfg.mdm.service.MasterDataService;
import com.mfg.security.config.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductionOrderService {
    private static final Set<String> PRIORITIES = Set.of("URGENT", "HIGH", "NORMAL", "LOW");

    private final ProductionOrderRepository repo;
    private final SalesOrderRepository salesOrders;
    private final MaterialRepository materials;
    private final BomRepository boms;
    private final MasterDataService master;
    private final JdbcTemplate db;
    private final BusinessEventService events;

    @Transactional
    public ProductionOrder create(ProductionOrderCommand command) {
        if (repo.existsByProdOrderNo(command.prodOrderNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "生产订单号已存在");
        }
        ProductionOrder order = new ProductionOrder();
        order.setCreatedBy(CurrentUser.usernameOrSystem());
        apply(order, command);
        return repo.save(order);
    }

    @Transactional
    public ProductionOrder update(Long id, ProductionOrderCommand command) {
        ProductionOrder order = repo.findById(id).orElseThrow(() -> BizException.notFound("生产订单", id));
        if (!"CREATED".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有尚未下达的生产订单可以修改");
        }
        if (!order.getProdOrderNo().equals(command.prodOrderNo().trim())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "生产订单号创建后不可修改");
        }
        if (repo.existsByProdOrderNoAndIdNot(command.prodOrderNo(), id)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "生产订单号已存在");
        }
        apply(order, command);
        order.setKitStatus("UNCHECKED");
        order.setKitCheckedAt(null);
        return repo.save(order);
    }

    @Transactional
    public void deleteDraft(Long id) {
        ProductionOrder order = repo.findById(id).orElseThrow(() -> BizException.notFound("生产订单", id));
        if (!"CREATED".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "已下达的生产订单不能删除");
        }
        Integer linked = db.queryForObject(
                "SELECT COUNT(*) FROM src_erp.erp_plan_suggestion WHERE converted_order_no=?",
                Integer.class, order.getProdOrderNo());
        if (linked != null && linked > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "生产订单由计划建议生成，请先处理关联的计划建议");
        }
        repo.delete(order);
    }

    @Transactional
    public ProductionOrder release(Long id) {
        ProductionOrder order = repo.findById(id).orElseThrow(() -> BizException.notFound("生产订单", id));
        if (!"CREATED".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有已创建的生产订单可以下达");
        }
        String kitStatus = db.queryForObject("SELECT kit_status FROM src_erp.erp_prod_order WHERE id=?", String.class, id);
        if (!"READY".equals(kitStatus)) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "生产订单必须先通过齐套检查");
        }
        order.setStatus("RELEASED");
        ProductionOrder saved = repo.save(order);
        events.publish("ERP.PRODUCTION_ORDER.RELEASED", "erp", "mes", "PRODUCTION_ORDER",
                order.getProdOrderNo(), Map.of("productCode", order.getProductCode(), "planQty", order.getPlanQty()));
        return saved;
    }

    private void apply(ProductionOrder order, ProductionOrderCommand command) {
        if (command.planFinishDate().isBefore(command.planStartDate())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "计划完工日期不能早于计划开工日期");
        }
        String priority = blankToNull(command.priorityLevel());
        if (priority != null && !PRIORITIES.contains(priority.toUpperCase())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "生产优先级仅支持紧急、高、普通、低");
        }

        String salesOrderNo = blankToNull(command.salesOrderNo());
        if (salesOrderNo != null && !salesOrders.existsBySalesOrderNo(salesOrderNo)) {
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联销售订单不存在");
        }
        Material product = materials.findByMaterialCode(command.productCode())
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "产品物料不存在"));
        master.assertConsumable(product, "ERP 生产订单");
        Bom bom = boms.findCurrentByProduct(command.productCode())
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_PUBLISHED, "该产品没有已发布的当前 BOM"));

        order.setProdOrderNo(command.prodOrderNo().trim());
        order.setSalesOrderNo(salesOrderNo);
        order.setProductCode(product.getMaterialCode());
        order.setProductName(product.getMaterialName());
        order.setPlanQty(command.planQty());
        order.setUnitCode(command.unitCode());
        order.setPlanStartDate(command.planStartDate());
        order.setPlanFinishDate(command.planFinishDate());
        order.setBomCode(bom.getBomCode());
        order.setBomVersion(bom.getBomVersion());
        order.setProductionVersionCode(blankToNull(command.productionVersionCode()));
        order.setPriorityLevel(priority == null ? "NORMAL" : priority.toUpperCase());
        order.setFactoryCode(blankToNull(command.factoryCode()));
        order.setWorkshopCode(blankToNull(command.workshopCode()));
        order.setCostCenterCode(blankToNull(command.costCenterCode()));
        order.setRoutingCode(blankToNull(command.routingCode()));
        order.setRoutingVersion(blankToNull(command.routingVersion()));
        order.setPlannerRemark(blankToNull(command.plannerRemark()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
