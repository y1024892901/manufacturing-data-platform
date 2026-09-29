package com.mfg.plm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.workflow.dto.StartApprovalRequest;
import com.mfg.workflow.entity.WfInstance;
import com.mfg.workflow.service.ApprovalEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EngineeringChangeChainService {
    private final JdbcTemplate jdbc;
    private final ApprovalEngine workflow;

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> page(String type, String keyword, int page, int size) {
        String table = switch (type) {
            case "ecrs" -> "plm_ecr";
            case "ecos" -> "plm_eco";
            case "ecns" -> "plm_ecn";
            default -> throw invalid();
        };
        String no = type.substring(0, 3) + "_no";
        String title = type.substring(0, 3) + "_title";
        int p = Math.max(0, page - 1);
        int s = Math.min(200, Math.max(1, size));
        String where = keyword == null || keyword.isBlank() ? "" : " WHERE " + no + " LIKE ? OR " + title + " LIKE ?";
        Object[] args = where.isBlank() ? new Object[]{} : new Object[]{"%" + keyword.trim() + "%", "%" + keyword.trim() + "%"};
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM src_plm." + table + where, Long.class, args);
        List<Object> queryArgs = new ArrayList<>(Arrays.asList(args));
        queryArgs.add(s);
        queryArgs.add(p * s);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM src_plm." + table + where + " ORDER BY id DESC LIMIT ? OFFSET ?", queryArgs.toArray());
        return new PageImpl<>(rows, PageRequest.of(p, s), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String type, Long id) {
        return one(table(type), id);
    }

    @Transactional
    public Map<String, Object> createEcr(Map<String, Object> input) {
        String number = String.valueOf(req(input, "ecrNo"));
        jdbc.update("INSERT INTO src_plm.plm_ecr(ecr_no,ecr_title,product_code,problem_desc,change_reason,urgency,proposal,planned_effective_date,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                number, req(input, "ecrTitle"), req(input, "productCode"), req(input, "problemDesc"), req(input, "changeReason"),
                value(input, "urgency", "NORMAL"), text(input, "proposal"), date(input, "plannedEffectiveDate"), CurrentUser.usernameOrSystem());
        return byNumber("plm_ecr", "ecr_no", number);
    }

    @Transactional
    public Map<String, Object> updateEcr(Long id, Map<String, Object> input) {
        Map<String, Object> ecr = one("plm_ecr", id);
        requireDraft(ecr, "ECR");
        jdbc.update("UPDATE src_plm.plm_ecr SET ecr_title=?,product_code=?,problem_desc=?,change_reason=?,urgency=?,proposal=?,planned_effective_date=? WHERE id=?",
                req(input, "ecrTitle"), req(input, "productCode"), req(input, "problemDesc"), req(input, "changeReason"),
                value(input, "urgency", "NORMAL"), text(input, "proposal"), date(input, "plannedEffectiveDate"), id);
        return one("plm_ecr", id);
    }

    @Transactional
    public void deleteEcr(Long id) {
        Map<String, Object> ecr = one("plm_ecr", id);
        if (!List.of("DRAFT", "ANALYZED").contains(String.valueOf(ecr.get("status")))) {
            throw BizException.conflict("只有草稿或已分析的变更申请可以删除");
        }
        Long ecoCount = jdbc.queryForObject("SELECT COUNT(*) FROM src_plm.plm_eco WHERE ecr_id=?", Long.class, id);
        if (ecoCount != null && ecoCount > 0) throw BizException.conflict("变更申请已生成变更命令，不能删除");
        jdbc.update("DELETE FROM src_plm.plm_impact_analysis WHERE ecr_id=?", id);
        jdbc.update("DELETE FROM src_plm.plm_ecr WHERE id=?", id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> impactDetails(Long id) {
        one("plm_ecr", id);
        return jdbc.queryForList("SELECT * FROM src_plm.plm_impact_analysis WHERE ecr_id=? ORDER BY id", id);
    }

    @Transactional
    public List<Map<String, Object>> impacts(Long id, List<Map<String, Object>> items) {
        Map<String, Object> ecr = one("plm_ecr", id);
        if (!List.of("DRAFT", "ANALYZED").contains(String.valueOf(ecr.get("status")))) {
            throw BizException.conflict("已生成变更命令的申请不能再修改影响分析");
        }
        jdbc.update("DELETE FROM src_plm.plm_impact_analysis WHERE ecr_id=?", id);
        for (Map<String, Object> item : items) {
            jdbc.update("INSERT INTO src_plm.plm_impact_analysis(ecr_id,impact_type,object_code,impact_desc,risk_level,cost_impact,schedule_impact_days,owner_user) VALUES(?,?,?,?,?,?,?,?)",
                    id, req(item, "impactType"), req(item, "objectCode"), text(item, "impactDesc"), value(item, "riskLevel", "MEDIUM"),
                    item.get("costImpact"), item.get("scheduleImpactDays"), text(item, "ownerUser"));
        }
        jdbc.update("UPDATE src_plm.plm_ecr SET status='ANALYZED' WHERE id=?", id);
        return jdbc.queryForList("SELECT * FROM src_plm.plm_impact_analysis WHERE ecr_id=? ORDER BY id", id);
    }

    @Transactional
    public Map<String, Object> createEco(Long ecrId, Map<String, Object> input) {
        Map<String, Object> ecr = one("plm_ecr", ecrId);
        if (!"ANALYZED".equals(String.valueOf(ecr.get("status")))) throw BizException.conflict("ECR必须先完成影响分析");
        int claimed = jdbc.update("UPDATE src_plm.plm_ecr SET status='ECO_CREATED' WHERE id=? AND status='ANALYZED'", ecrId);
        if (claimed == 0) throw BizException.conflict("变更申请状态已变化，请刷新后重试");
        String number = String.valueOf(req(input, "ecoNo"));
        jdbc.update("INSERT INTO src_plm.plm_eco(eco_no,ecr_id,eco_title,product_code,change_scope,target_type,target_code,new_version,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                number, ecrId, req(input, "ecoTitle"), ecr.get("product_code"), text(input, "changeScope"),
                req(input, "targetType"), text(input, "targetCode"), req(input, "newVersion"), CurrentUser.usernameOrSystem());
        return byNumber("plm_eco", "eco_no", number);
    }

    @Transactional
    public Map<String, Object> updateEco(Long id, Map<String, Object> input) {
        Map<String, Object> eco = one("plm_eco", id);
        requireDraftOrRejected(eco, "ECO");
        jdbc.update("UPDATE src_plm.plm_eco SET eco_title=?,change_scope=?,target_type=?,target_code=?,new_version=?,status='DRAFT' WHERE id=?",
                req(input, "ecoTitle"), text(input, "changeScope"), req(input, "targetType"), text(input, "targetCode"), req(input, "newVersion"), id);
        return one("plm_eco", id);
    }

    @Transactional
    public void deleteEco(Long id) {
        Map<String, Object> eco = one("plm_eco", id);
        requireDraftOrRejected(eco, "ECO");
        jdbc.update("DELETE FROM src_plm.plm_eco WHERE id=?", id);
        jdbc.update("UPDATE src_plm.plm_ecr SET status='ANALYZED' WHERE id=? AND status='ECO_CREATED'", eco.get("ecr_id"));
    }

    @Transactional
    public WfInstance submitEco(Long id) {
        Map<String, Object> eco = one("plm_eco", id);
        requireDraftOrRejected(eco, "ECO");
        int claimed = jdbc.update("UPDATE src_plm.plm_eco SET status='REVIEWING' WHERE id=? AND status IN ('DRAFT','REJECTED')", id);
        if (claimed == 0) throw BizException.conflict("变更命令状态已变化，请刷新后重试");
        String context = "{\"targetType\":\"" + eco.get("target_type") + "\",\"targetCode\":\"" + (eco.get("target_code") == null ? "" : eco.get("target_code")) + "\"}";
        return workflow.start(new StartApprovalRequest("ECO", id, String.valueOf(eco.get("eco_no")), String.valueOf(eco.get("eco_title")), context));
    }

    @Transactional
    public Map<String, Object> createEcnFromEco(Long ecoId) {
        Map<String, Object> eco = one("plm_eco", ecoId);
        if (!"APPROVED".equals(String.valueOf(eco.get("status")))) throw BizException.conflict("ECO未批准");
        String number = "ECN-" + String.format("%05d", ecoId);
        jdbc.update("INSERT INTO src_plm.plm_ecn(ecr_id,eco_id,ecn_no,ecn_title,product_code,bom_code,change_type,target_type,target_version,change_content,change_reason,ecn_status,submitted_by) " +
                        "SELECT ecr_id,id,?,?,product_code,target_code,'DESIGN',target_type,new_version,change_scope,'ECO审批通过','APPROVED',? FROM src_plm.plm_eco WHERE id=? " +
                        "ON DUPLICATE KEY UPDATE eco_id=VALUES(eco_id)",
                number, eco.get("eco_title"), CurrentUser.usernameOrSystem(), ecoId);
        return jdbc.queryForMap("SELECT * FROM src_plm.plm_ecn WHERE eco_id=?", ecoId);
    }

    @Transactional
    public Map<String, Object> implement(Long ecnId) {
        Map<String, Object> ecn = one("plm_ecn", ecnId);
        if (!"APPROVED".equals(String.valueOf(ecn.get("ecn_status")))) throw BizException.conflict("ECN未批准");
        String type = String.valueOf(ecn.get("target_type"));
        String target = text(ecn, "bom_code");
        String version = text(ecn, "target_version");
        if (target == null || version == null) throw BizException.conflict("ECN缺少目标编码或目标版本");
        if ("BOM".equals(type)) {
            Map<String, Object> old = source("SELECT * FROM src_mdm.md_bom WHERE bom_code=? ORDER BY id DESC LIMIT 1", target);
            jdbc.update("INSERT INTO src_mdm.md_bom(bom_code,bom_name,product_id,product_code,bom_version,bom_type,effective_date,is_current,base_qty,base_unit_code,status,version_no,change_reason,change_ecn_no,created_by) VALUES(?,?,?,?,?,?,CURDATE(),0,?,?, 'DRAFT',1,?,?,?)",
                    target, old.get("bom_name"), old.get("product_id"), old.get("product_code"), version, old.get("bom_type"), old.get("base_qty"), old.get("base_unit_code"), ecn.get("change_reason"), ecn.get("ecn_no"), CurrentUser.usernameOrSystem());
            Long newId = ((Number) jdbc.queryForMap("SELECT LAST_INSERT_ID() id").get("id")).longValue();
            jdbc.update("INSERT INTO src_mdm.md_bom_line(bom_id,line_no,child_material_id,child_material_code,child_material_name,qty_per,unit_code,scrap_rate,seq_no,is_key_material,position_desc,substitute_group,remark) " +
                            "SELECT ?,line_no,child_material_id,child_material_code,child_material_name,qty_per,unit_code,scrap_rate,seq_no,is_key_material,position_desc,substitute_group,remark FROM src_mdm.md_bom_line WHERE bom_id=?",
                    newId, old.get("id"));
        } else if ("ROUTING".equals(type)) {
            Map<String, Object> old = source("SELECT * FROM src_mdm.md_routing WHERE routing_code=? ORDER BY id DESC LIMIT 1", target);
            jdbc.update("INSERT INTO src_mdm.md_routing(routing_code,routing_name,product_code,routing_version,effective_date,is_current,status,version_no,change_reason,created_by) VALUES(?,?,?,?,CURDATE(),0,'DRAFT',1,?,?)",
                    target, old.get("routing_name"), old.get("product_code"), version, ecn.get("change_reason"), CurrentUser.usernameOrSystem());
            Long newId = ((Number) jdbc.queryForMap("SELECT LAST_INSERT_ID() id").get("id")).longValue();
            jdbc.update("INSERT INTO src_mdm.md_routing_operation(routing_id,op_seq,operation_code,operation_name,work_center,setup_time_min,run_time_min,wait_time_min,default_equipment_code,required_skill,is_key_operation,is_inspection_op,inspection_required,remark) " +
                            "SELECT ?,op_seq,operation_code,operation_name,work_center,setup_time_min,run_time_min,wait_time_min,default_equipment_code,required_skill,is_key_operation,is_inspection_op,inspection_required,remark FROM src_mdm.md_routing_operation WHERE routing_id=?",
                    newId, old.get("id"));
        } else {
            throw BizException.conflict("ECN目标类型必须为BOM或工艺路线");
        }
        jdbc.update("UPDATE src_plm.plm_ecn SET ecn_status='IMPLEMENTED' WHERE id=?", ecnId);
        return one("plm_ecn", ecnId);
    }

    @Transactional
    public void approvedEco(Long id) {
        jdbc.update("UPDATE src_plm.plm_eco SET status='APPROVED' WHERE id=?", id);
        createEcnFromEco(id);
    }

    @Transactional
    public void rejectedEco(Long id) {
        jdbc.update("UPDATE src_plm.plm_eco SET status='REJECTED' WHERE id=?", id);
    }

    private String table(String type) {
        return switch (type) {
            case "ecrs" -> "plm_ecr";
            case "ecos" -> "plm_eco";
            default -> throw invalid();
        };
    }

    private Map<String, Object> one(String table, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM src_plm." + table + " WHERE id=?", id);
        if (rows.isEmpty()) throw BizException.notFound("工程变更", id);
        return rows.get(0);
    }

    private Map<String, Object> source(String sql, Object key) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, key);
        if (rows.isEmpty()) throw BizException.notFound("MDM主数据", key);
        return rows.get(0);
    }

    private Map<String, Object> byNumber(String table, String column, String number) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM src_plm." + table + " WHERE " + column + "=?", number);
        if (rows.isEmpty()) throw BizException.notFound("工程变更单", number);
        return rows.get(0);
    }

    private void requireDraft(Map<String, Object> row, String type) {
        if (!"DRAFT".equals(String.valueOf(row.get("status")))) throw BizException.conflict("只有草稿" + type + "可以修改或删除");
    }

    private void requireDraftOrRejected(Map<String, Object> row, String type) {
        if (!List.of("DRAFT", "REJECTED").contains(String.valueOf(row.get("status")))) {
            throw BizException.conflict("只有草稿或已驳回的" + type + "可以修改或删除");
        }
    }

    private BizException invalid() {
        return BizException.of(ErrorCode.PARAM_INVALID, "未知工程变更类型");
    }

    private Object req(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, key + "不能为空");
        return value;
    }

    private String text(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private String value(Map<String, Object> input, String key, String fallback) {
        String value = text(input, key);
        return value == null ? fallback : value;
    }

    private Date date(Map<String, Object> input, String key) {
        String value = text(input, key);
        return value == null ? null : Date.valueOf(value);
    }
}
