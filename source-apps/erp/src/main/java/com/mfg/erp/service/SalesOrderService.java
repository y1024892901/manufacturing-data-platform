package com.mfg.erp.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.erp.dto.SalesOrderCommand;
import com.mfg.erp.entity.SalesOrder;
import com.mfg.erp.entity.SalesOrderLine;
import com.mfg.erp.repo.SalesOrderLineRepository;
import com.mfg.erp.repo.SalesOrderRepository;
import com.mfg.mdm.entity.Customer;
import com.mfg.mdm.entity.Material;
import com.mfg.mdm.repo.CustomerRepository;
import com.mfg.mdm.repo.MaterialRepository;
import com.mfg.mdm.service.MasterDataService;
import com.mfg.security.config.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesOrderService {
    private final SalesOrderRepository orders;
    private final SalesOrderLineRepository lines;
    private final CustomerRepository customers;
    private final MaterialRepository materials;
    private final MasterDataService master;
    private final JdbcTemplate db;

    @Transactional
    public SalesOrder create(SalesOrderCommand command) {
        if (orders.existsBySalesOrderNo(command.salesOrderNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "销售订单号已存在");
        }
        String contractNo = blankToNull(command.sourceContractNo());
        if (contractNo != null && orders.existsBySourceContractNo(contractNo)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "来源合同已生成销售订单");
        }

        Customer customer = customer(command.customerCode());
        SalesOrder order = new SalesOrder();
        order.setOrderDate(LocalDate.now());
        order.setSalesUser(CurrentUser.usernameOrSystem());
        order.setDeptCode(CurrentUser.get().getDeptCode());
        order.setCreatedBy(CurrentUser.usernameOrSystem());
        applyHeader(order, command, customer);
        order.setTotalAmount(BigDecimal.ZERO);
        order = orders.save(order);
        return replaceLines(order, command.lines());
    }

    @Transactional
    public SalesOrder update(Long id, SalesOrderCommand command) {
        SalesOrder order = orders.findById(id).orElseThrow(() -> BizException.notFound("销售订单", id));
        if (!"DRAFT".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有草稿销售订单可以修改");
        }
        if (!order.getSalesOrderNo().equals(command.salesOrderNo().trim())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "销售订单号创建后不可修改");
        }
        if (orders.existsBySalesOrderNoAndIdNot(command.salesOrderNo(), id)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "销售订单号已存在");
        }
        String contractNo = blankToNull(command.sourceContractNo());
        if (contractNo != null && orders.existsBySourceContractNoAndIdNot(contractNo, id)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "来源合同已生成销售订单");
        }

        List<SalesOrderLine> existing = lines.findBySalesOrderNoOrderByLineNo(order.getSalesOrderNo());
        if (!existing.isEmpty()) lines.deleteAll(existing);
        lines.flush();
        applyHeader(order, command, customer(command.customerCode()));
        return replaceLines(order, command.lines());
    }

    @Transactional
    public void deleteDraft(Long id) {
        SalesOrder order = orders.findById(id).orElseThrow(() -> BizException.notFound("销售订单", id));
        if (!"DRAFT".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "已进入业务流程的销售订单不能删除，请使用取消流程");
        }
        String orderNo = order.getSalesOrderNo();
        if (count("SELECT COUNT(*) FROM src_erp.erp_mrp_run WHERE sales_order_no=?", orderNo) > 0
                || count("SELECT COUNT(*) FROM src_erp.erp_prod_order WHERE sales_order_no=?", orderNo) > 0
                || count("SELECT COUNT(*) FROM src_erp.erp_invoice WHERE sales_order_no=?", orderNo) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "销售订单已关联计划、生产或财务单据，不能删除");
        }
        lines.deleteAll(lines.findBySalesOrderNoOrderByLineNo(orderNo));
        lines.flush();
        orders.delete(order);
    }

    @Transactional
    public SalesOrder confirm(Long id) {
        SalesOrder order = orders.findById(id).orElseThrow(() -> BizException.notFound("销售订单", id));
        if (!"DRAFT".equals(order.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有草稿订单可确认");
        }
        if (lines.findBySalesOrderNoOrderByLineNo(order.getSalesOrderNo()).isEmpty()) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "订单没有明细");
        }
        order.setStatus("CONFIRMED");
        return orders.save(order);
    }

    private Customer customer(String customerCode) {
        Customer customer = customers.findByCustomerCode(customerCode)
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "客户不存在"));
        master.assertConsumable(customer, "ERP 销售订单");
        return customer;
    }

    private void applyHeader(SalesOrder order, SalesOrderCommand command, Customer customer) {
        order.setSalesOrderNo(command.salesOrderNo().trim());
        order.setCustomerCode(customer.getCustomerCode());
        order.setCustomerName(customer.getCustomerName());
        order.setDeliveryDate(command.deliveryDate());
        order.setSourceOpportunityNo(blankToNull(command.sourceOpportunityNo()));
        order.setCustomerReference(blankToNull(command.customerReference()));
        order.setPaymentTerms(blankToNull(command.paymentTerms()));
        order.setShippingTerms(blankToNull(command.shippingTerms()));
        order.setShipToAddress(blankToNull(command.shipToAddress()));
        order.setSourceContractNo(blankToNull(command.sourceContractNo()));
        order.setSourceType(defaultValue(command.sourceType(), "MANUAL"));
        order.setFactoryCode(blankToNull(command.factoryCode()));
        order.setCurrency(defaultValue(command.currency(), "CNY"));
        if (command.taxRate() != null) order.setTaxRate(command.taxRate());
        order.setRemark(blankToNull(command.remark()));
    }

    private SalesOrder replaceLines(SalesOrder order, List<SalesOrderCommand.Line> requestedLines) {
        List<SalesOrderLine> existing = lines.findBySalesOrderNoOrderByLineNo(order.getSalesOrderNo());
        if (!existing.isEmpty()) lines.deleteAll(existing);
        lines.flush();

        Set<Integer> lineNumbers = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderCommand.Line requested : requestedLines) {
            if (!lineNumbers.add(requested.lineNo())) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "订单明细序号不能重复");
            }
            Material material = materials.findByMaterialCode(requested.materialCode())
                    .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "物料不存在：" + requested.materialCode()));
            master.assertConsumable(material, "ERP 销售订单");

            SalesOrderLine line = new SalesOrderLine();
            line.setSalesOrderNo(order.getSalesOrderNo());
            line.setLineNo(requested.lineNo());
            line.setMaterialCode(material.getMaterialCode());
            line.setMaterialName(material.getMaterialName());
            line.setOrderQty(requested.orderQty());
            line.setUnitCode(defaultValue(requested.unitCode(), material.getBaseUnitCode()));
            line.setUnitPrice(requested.unitPrice());
            line.setLineAmount(requested.orderQty().multiply(requested.unitPrice()).setScale(2, RoundingMode.HALF_UP));
            line.setDeliveryDate(order.getDeliveryDate());
            line.setCustomerMaterialCode(blankToNull(requested.customerMaterialCode()));
            line.setRemark(blankToNull(requested.remark()));
            lines.save(line);
            total = total.add(line.getLineAmount());
        }

        order.setTotalAmount(total);
        BigDecimal rate = order.getTaxRate() == null ? BigDecimal.ZERO : order.getTaxRate();
        order.setTaxAmount(total.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        return orders.save(order);
    }

    private int count(String sql, Object value) {
        Integer result = db.queryForObject(sql, Integer.class, value);
        return result == null ? 0 : result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultValue(String value, String fallback) {
        String result = blankToNull(value);
        return result == null ? fallback : result;
    }
}
