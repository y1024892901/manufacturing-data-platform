package com.mfg.mdm.service;

import com.mfg.mdm.domain.BizType;
import com.mfg.mdm.domain.MasterDataEntity;
import com.mfg.mdm.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 主数据分发服务 —— 把已发布的主数据推送到 9 个业务系统的只读副本表。
 *
 * <p><b>实现方式</b>：应用主数据源连的是 {@code mfg_auth} 库，但 MySQL 支持
 * 跨库限定名（{@code src_erp.erp_md_customer}），所以用 {@link JdbcTemplate}
 * 直接写目标库即可，<b>无需配置多数据源</b>。这是本项目在
 * 「一个 MySQL 实例、18 个库」架构下最省事的方案。
 *
 * <p><b>分发语义</b>：只新增或更新，<b>绝不删除</b>。
 * 若主数据被停用，下游副本保留但不再参与新业务——因为历史单据
 * 还引用着它，删掉会导致历史单据查不到物料名。
 *
 * <p><b>失败隔离</b>：每个目标系统的写入用 try-catch 包住，
 * 失败只记录日志、不向上抛异常。
 *
 * <p>为什么<b>不</b>用 {@code @Transactional(REQUIRES_NEW)} 做隔离：
 * 内层事务失败时会被标记 rollback-only，即使异常在外层被捕获，
 * 外层提交时仍会抛 {@code UnexpectedRollbackException}
 * （"Transaction silently rolled back because it has been marked as rollback-only"）。
 * 结果是「ERP 分发失败」导致「整个审批动作回滚」——与设计意图完全相反。
 * 因为分发的所有异常都被捕获，事务本身不会因分发而失败，无需嵌套事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDataDistributor {

    private final JdbcTemplate jdbc;

    /**
     * 分发一条主数据到全部目标系统。
     *
     * @return 每个目标系统的分发结果
     */
    public List<DistResult> distribute(MasterDataEntity entity) {
        BizType type = entity.bizType();
        List<DistResult> results = new ArrayList<>();

        for (String system : type.systemArray()) {
            DistResult r = distributeTo(entity, system.trim());
            results.add(r);
        }

        long ok = results.stream().filter(DistResult::success).count();
        log.info("主数据分发完成: {} [{}] 版本={} → {}/{} 个系统成功",
                type.getLabel(), entity.getBusinessCode(),
                entity.getVersionNo(), ok, results.size());

        return results;
    }

    /** 分发到指定系统。异常一律内部消化，绝不上抛污染调用方的事务。 */
    public DistResult distributeTo(MasterDataEntity entity, String system) {
        try {
            int rows = switch (system) {
                case "crm" -> writeCustomerToCrm((Customer) entity);
                case "erp" -> writeToErp(entity);
                case "srm" -> writeToSrm(entity);
                case "plm" -> writeToPlm(entity);
                case "mes" -> writeToMes(entity);
                case "wms" -> writeToWms(entity);
                case "qms" -> writeToQms(entity);
                case "eam" -> writeToEam(entity);
                case "energy" -> writeToEnergy(entity);
                default -> throw new IllegalArgumentException("未知目标系统: " + system);
            };

            logDistribution(entity, system, rows, "SUCCESS", null);
            return DistResult.ok(system, rows);

        } catch (Exception e) {
            log.error("分发失败: {} [{}] → {}", entity.bizType().getLabel(),
                    entity.getBusinessCode(), system, e);
            logDistribution(entity, system, 0, "FAILED", e.getMessage());
            return DistResult.fail(system, e.getMessage());
        }
    }

    // ============================================================
    // 各目标系统的写入
    // ============================================================

    /** CRM：只接收客户 */
    private int writeCustomerToCrm(Customer c) {
        return jdbc.update("""
                INSERT INTO src_crm.crm_md_customer
                    (customer_code, customer_name, short_name, customer_level,
                     region, credit_limit, payment_terms, contact_person,
                     contact_phone, master_version, synced_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    customer_name=VALUES(customer_name), short_name=VALUES(short_name),
                    customer_level=VALUES(customer_level), region=VALUES(region),
                    credit_limit=VALUES(credit_limit), payment_terms=VALUES(payment_terms),
                    contact_person=VALUES(contact_person), contact_phone=VALUES(contact_phone),
                    master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                """, c.getCustomerCode(), c.getCustomerName(), c.getShortName(),
                c.getCustomerLevel(), c.getRegion(), c.getCreditLimit(),
                c.getPaymentTerms(), c.getContactPerson(), maskPhone(c.getContactPhone()),
                c.getVersionNo(), LocalDateTime.now());
    }

    /** ERP：接收客户 / 供应商 / 物料 / 成本中心 */
    private int writeToErp(MasterDataEntity e) {
        if (e instanceof Customer c) {
            return jdbc.update("""
                    INSERT INTO src_erp.erp_md_customer
                        (customer_code, customer_name, credit_limit, credit_used,
                         payment_terms, master_version, synced_at)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        customer_name=VALUES(customer_name), credit_limit=VALUES(credit_limit),
                        payment_terms=VALUES(payment_terms),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, c.getCustomerCode(), c.getCustomerName(), c.getCreditLimit(),
                    c.getCreditUsed(), c.getPaymentTerms(), c.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Supplier s) {
            return jdbc.update("""
                    INSERT INTO src_erp.erp_md_supplier
                        (supplier_code, supplier_name, lead_time_days, master_version, synced_at)
                    VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        supplier_name=VALUES(supplier_name),
                        lead_time_days=VALUES(lead_time_days),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, s.getSupplierCode(), s.getSupplierName(), s.getLeadTimeDays(),
                    s.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Material m) {
            return jdbc.update("""
                    INSERT INTO src_erp.erp_md_material
                        (material_code, material_name, material_spec, material_type,
                         base_unit_code, standard_price, safety_stock,
                         master_version, synced_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        material_name=VALUES(material_name), material_spec=VALUES(material_spec),
                        material_type=VALUES(material_type), base_unit_code=VALUES(base_unit_code),
                        standard_price=VALUES(standard_price), safety_stock=VALUES(safety_stock),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, m.getMaterialCode(), m.getMaterialName(), m.getMaterialSpec(),
                    m.getMaterialType(), m.getBaseUnitCode(), m.getStandardPrice(),
                    m.getSafetyStock(), m.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Product p) {
            return jdbc.update("""
                    INSERT INTO src_erp.erp_md_product
                        (product_code, product_name, product_model, lifecycle_status, master_version, synced_at)
                    VALUES (?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE product_name=VALUES(product_name),product_model=VALUES(product_model),
                        lifecycle_status=VALUES(lifecycle_status),master_version=VALUES(master_version),synced_at=VALUES(synced_at)
                    """, p.getProductCode(), p.getProductName(), p.getProductModel(), p.getLifecycleStatus(), p.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Bom b) {
            // ERP 的 BOM 副本：头 + 行都要写，因为 ERP 要用它算料
            int rows = jdbc.update("""
                    INSERT INTO src_erp.erp_md_bom
                        (bom_code, bom_version, product_code, is_current,
                         base_qty, master_version, synced_at)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        is_current=VALUES(is_current), base_qty=VALUES(base_qty),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, b.getBomCode(), b.getBomVersion(), b.getProductCode(),
                    b.getCurrent(), b.getBaseQty(), b.getVersionNo(), LocalDateTime.now());
            rows += writeErpBomLines(b);
            return rows;
        }
        return 0;
    }

    private int writeErpBomLines(Bom b) {
        if (b.getLines() == null || b.getLines().isEmpty()) {
            return 0;
        }
        // 先清掉旧行再写入，避免行号变动后残留
        jdbc.update("DELETE FROM src_erp.erp_md_bom_line WHERE bom_code=? AND bom_version=?",
                b.getBomCode(), b.getBomVersion());

        int rows = 0;
        for (BomLine line : b.getLines()) {
            rows += jdbc.update("""
                    INSERT INTO src_erp.erp_md_bom_line
                        (bom_code, bom_version, line_no, child_material_code,
                         qty_per, unit_code, scrap_rate, synced_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, b.getBomCode(), b.getBomVersion(), line.getLineNo(),
                    line.getChildMaterialCode(), line.getQtyPer(), line.getUnitCode(),
                    line.getScrapRate(), LocalDateTime.now());
        }
        return rows;
    }

    /** SRM：接收供应商与物料 */
    private int writeToSrm(MasterDataEntity e) {
        if (e instanceof Supplier s) {
            return jdbc.update("""
                    INSERT INTO src_srm.srm_md_supplier
                        (supplier_code, supplier_name, supplier_level, lead_time_days,
                         qual_status, master_version, synced_at)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        supplier_name=VALUES(supplier_name), supplier_level=VALUES(supplier_level),
                        lead_time_days=VALUES(lead_time_days), qual_status=VALUES(qual_status),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, s.getSupplierCode(), s.getSupplierName(), s.getSupplierLevel(),
                    s.getLeadTimeDays(), s.getQualStatus(), s.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Material m) {
            return jdbc.update("""
                    INSERT INTO src_srm.srm_md_material
                        (material_code, material_name, base_unit_code, purchase_unit_code,
                         conversion_rate, standard_price, master_version, synced_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        material_name=VALUES(material_name), base_unit_code=VALUES(base_unit_code),
                        purchase_unit_code=VALUES(purchase_unit_code),
                        conversion_rate=VALUES(conversion_rate),
                        standard_price=VALUES(standard_price),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, m.getMaterialCode(), m.getMaterialName(), m.getBaseUnitCode(),
                    m.getPurchaseUnitCode(), m.getConversionRate(), m.getStandardPrice(),
                    m.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Product p) {
            return jdbc.update("""
                    INSERT INTO src_plm.plm_md_product
                        (product_code, product_name, product_model, lifecycle_status, master_version, synced_at)
                    VALUES (?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE product_name=VALUES(product_name),product_model=VALUES(product_model),
                        lifecycle_status=VALUES(lifecycle_status),master_version=VALUES(master_version),synced_at=VALUES(synced_at)
                    """, p.getProductCode(), p.getProductName(), p.getProductModel(), p.getLifecycleStatus(), p.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Routing r) {
            int rows = jdbc.update("""
                    INSERT INTO src_plm.plm_md_routing
                        (routing_code,routing_version,routing_name,product_code,effective_date,master_version,synced_at)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE routing_name=VALUES(routing_name),product_code=VALUES(product_code),
                        effective_date=VALUES(effective_date),master_version=VALUES(master_version),synced_at=VALUES(synced_at)
                    """, r.getRoutingCode(), r.getRoutingVersion(), r.getRoutingName(), r.getProductCode(), r.getEffectiveDate(), r.getVersionNo(), LocalDateTime.now());
            rows += writeRoutingOperations(r, "src_plm.plm_md_routing_operation");
            return rows;
        }
        return 0;
    }

    /** PLM：接收物料 / 产品 / BOM（BOM 的工程视角）*/
    private int writeToPlm(MasterDataEntity e) {
        if (e instanceof Material m) {
            return jdbc.update("""
                    INSERT INTO src_plm.plm_md_material
                        (material_code, material_name, material_spec, master_version, synced_at)
                    VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        material_name=VALUES(material_name), material_spec=VALUES(material_spec),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, m.getMaterialCode(), m.getMaterialName(), m.getMaterialSpec(),
                    m.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Bom b) {
            int rows = jdbc.update("""
                    INSERT INTO src_plm.plm_md_bom
                        (bom_code, bom_version, product_code, bom_type,
                         effective_date, master_version, synced_at)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        product_code=VALUES(product_code), bom_type=VALUES(bom_type),
                        effective_date=VALUES(effective_date),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, b.getBomCode(), b.getBomVersion(), b.getProductCode(),
                    b.getBomType(), b.getEffectiveDate(), b.getVersionNo(), LocalDateTime.now());

            if (b.getLines() != null) {
                // PLM 用于展示工程结构，先清后写
                jdbc.update("DELETE FROM src_plm.plm_md_bom_line WHERE bom_code=? AND bom_version=?",
                        b.getBomCode(), b.getBomVersion());
                for (BomLine line : b.getLines()) {
                    rows += jdbc.update("""
                            INSERT INTO src_plm.plm_md_bom_line
                                (bom_code, bom_version, line_no, child_material_code,
                                 qty_per, unit_code, scrap_rate, synced_at)
                            VALUES (?,?,?,?,?,?,?,?)
                            """, b.getBomCode(), b.getBomVersion(), line.getLineNo(),
                            line.getChildMaterialCode(), line.getQtyPer(), line.getUnitCode(),
                            line.getScrapRate(), LocalDateTime.now());
                }
            }
            return rows;
        }
        return 0;
    }

    /** MES：接收物料与工艺路线工序 */
    private int writeToMes(MasterDataEntity e) {
        if (e instanceof Material m) {
            return jdbc.update("""
                    INSERT INTO src_mes.mes_md_material
                        (material_code, material_name, base_unit_code, master_version, synced_at)
                    VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        material_name=VALUES(material_name),
                        base_unit_code=VALUES(base_unit_code),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, m.getMaterialCode(), m.getMaterialName(), m.getBaseUnitCode(),
                    m.getVersionNo(), LocalDateTime.now());
        }
        if (e instanceof Routing r) {
            return writeRoutingOperations(r, "src_mes.mes_md_routing_operation");
        }
        return 0;
    }

    private int writeRoutingOperations(Routing routing, String targetTable) {
        List<java.util.Map<String, Object>> operations = jdbc.queryForList(
                "SELECT * FROM src_mdm.md_routing_operation WHERE routing_id=? ORDER BY op_seq", routing.getId());
        jdbc.update("DELETE FROM " + targetTable + " WHERE routing_code=? AND routing_version=?", routing.getRoutingCode(), routing.getRoutingVersion());
        int rows = 0;
        for (var op : operations) {
            rows += jdbc.update("INSERT INTO " + targetTable + " (routing_code,routing_version,op_seq,operation_code,operation_name,work_center,setup_time_min,run_time_min,default_equipment_code,is_key_operation,is_inspection_op,master_version,synced_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    routing.getRoutingCode(), routing.getRoutingVersion(), op.get("op_seq"), op.get("operation_code"), op.get("operation_name"), op.get("work_center"), op.get("setup_time_min"), op.get("run_time_min"), op.get("default_equipment_code"), op.get("is_key_operation"), op.get("is_inspection_op"), routing.getVersionNo(), LocalDateTime.now());
        }
        return rows;
    }

    /** WMS：接收物料（库存与齐套）*/
    private int writeToWms(MasterDataEntity e) {
        if (e instanceof Material m) {
            return jdbc.update("""
                    INSERT INTO src_wms.wms_md_material
                        (material_code, material_name, base_unit_code,
                         safety_stock, is_batch_managed, master_version, synced_at)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        material_name=VALUES(material_name),
                        base_unit_code=VALUES(base_unit_code),
                        safety_stock=VALUES(safety_stock),
                        is_batch_managed=VALUES(is_batch_managed),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, m.getMaterialCode(), m.getMaterialName(), m.getBaseUnitCode(),
                    m.getSafetyStock(), m.getBatchManaged(), m.getVersionNo(), LocalDateTime.now());
        }
        return 0;
    }

    /** QMS：接收物料（含检验标准）*/
    private int writeToQms(MasterDataEntity e) {
        if (e instanceof Material m) {
            return jdbc.update("""
                    INSERT INTO src_qms.qms_md_material
                        (material_code, material_name, is_inspection_required,
                         inspection_standard, master_version, synced_at)
                    VALUES (?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        material_name=VALUES(material_name),
                        is_inspection_required=VALUES(is_inspection_required),
                        inspection_standard=VALUES(inspection_standard),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, m.getMaterialCode(), m.getMaterialName(), m.getInspectionRequired(),
                    m.getInspectionStandard(), m.getVersionNo(), LocalDateTime.now());
        }
        return 0;
    }

    /** EAM：接收物料（备件）*/
    private int writeToEam(MasterDataEntity e) {
        if (e instanceof Material m) {
            return jdbc.update("""
                    INSERT INTO src_eam.eam_md_material
                        (material_code, material_name, base_unit_code, master_version, synced_at)
                    VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        material_name=VALUES(material_name),
                        base_unit_code=VALUES(base_unit_code),
                        master_version=VALUES(master_version), synced_at=VALUES(synced_at)
                    """, m.getMaterialCode(), m.getMaterialName(), m.getBaseUnitCode(),
                    m.getVersionNo(), LocalDateTime.now());
        }
        return 0;
    }

    /** 能源：本类型暂无分发目标 */
    private int writeToEnergy(MasterDataEntity e) {
        return 0;
    }

    // ============================================================
    // 分发日志
    // ============================================================

    private void logDistribution(MasterDataEntity e, String system,
                                 int rows, String status, String error) {
        try {
            jdbc.update("""
                    INSERT INTO src_mdm.md_distribution_log
                        (entity_type, entity_code, entity_version, target_system,
                         distribute_mode, status, row_count, error_message, distributed_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        status=VALUES(status), row_count=VALUES(row_count),
                        error_message=VALUES(error_message), distributed_at=VALUES(distributed_at)
                    """, e.bizType().name(), e.getBusinessCode(),
                    e.getVersionNo(), system, "AUTO", status, rows,
                    error == null ? null : truncate(error, 490), LocalDateTime.now());
        } catch (Exception ex) {
            // 日志写入失败不能影响分发结果
            log.warn("分发日志写入失败: {}", ex.getMessage());
        }
    }

    /** 手机号脱敏：138****8888 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 单个目标系统的分发结果。
     */
    public record DistResult(String system, boolean success, int rows, String error) {
        public static DistResult ok(String system, int rows) {
            return new DistResult(system, true, rows, null);
        }

        public static DistResult fail(String system, String error) {
            return new DistResult(system, false, 0, error);
        }
    }
}
