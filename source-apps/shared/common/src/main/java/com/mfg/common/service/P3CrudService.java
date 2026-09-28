package com.mfg.common.service;

import com.mfg.common.api.*;
import com.mfg.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class P3CrudService {
    private final JdbcTemplate db;
    private final List<DataVisibility> visibility;

    private static final Map<String, String> TABLES = Map.ofEntries(
        Map.entry("onboarding", "src_srm.srm_onboarding"), Map.entry("qualifications", "src_srm.srm_qualification"),
        Map.entry("rfqs", "src_srm.srm_rfq"), Map.entry("quotes", "src_srm.srm_supplier_quote"),
        Map.entry("purchase-orders", "src_srm.srm_purchase_order"), Map.entry("asns", "src_srm.srm_asn"),
        Map.entry("supplier-quality", "src_srm.srm_supplier_quality"), Map.entry("performance", "src_srm.srm_supplier_performance"),
        Map.entry("receipts", "src_wms.wms_receipt"), Map.entry("putaway", "src_wms.wms_putaway"),
        Map.entry("inventories", "src_wms.wms_inventory"), Map.entry("inventory-actions", "src_wms.wms_inventory_ledger"),
        Map.entry("transfers", "src_wms.wms_transfer"), Map.entry("counts", "src_wms.wms_count_plan"),
        Map.entry("standards", "src_qms.qms_standard"), Map.entry("sampling-plans", "src_qms.qms_sampling_plan"),
        Map.entry("inspections", "src_qms.qms_inspection"), Map.entry("ncrs", "src_qms.qms_ncr"),
        Map.entry("reworks", "src_qms.qms_rework"), Map.entry("capas", "src_qms.qms_capa"),
        Map.entry("8d", "src_qms.qms_eight_d")
    );
    private static final Set<String> QMS_KINDS = Set.of("standards", "sampling-plans", "ncrs", "capas", "8d");
    private static final Map<String, Set<String>> QMS_EDITABLE = Map.of(
        "standards", Set.of("standard_code", "standard_name", "inspection_type", "material_code", "version_no", "aql_level", "effective_date", "owner_user", "reference_doc", "remark"),
        "sampling-plans", Set.of("plan_code", "plan_name", "aql_level", "lot_min", "lot_max", "sample_size", "accept_qty", "reject_qty", "sampling_method", "inspection_level", "sample_unit", "effective_date", "reference_doc", "owner_user", "remark"),
        "ncrs", Set.of("severity", "responsible_dept", "defect_type", "defect_desc", "containment_action", "root_cause", "due_date", "owner_user", "remark"),
        "capas", Set.of("root_cause", "corrective_action", "preventive_action", "owner_user", "due_date", "effectiveness_criteria", "effectiveness_result", "effectiveness_notes", "remark"),
        "8d", Set.of("supplier_code", "team_members", "problem_desc", "containment_action", "root_cause", "corrective_action", "preventive_action", "owner_user", "due_date", "effectiveness_result", "effectiveness_notes", "remark")
    );
    private static final Set<String> WMS_DRAFT_KINDS = Set.of("putaway", "transfers", "counts");
    private static final Set<String> WMS_TRACE_KINDS = Set.of("receipts");
    private static final Map<String, Set<String>> WMS_EDITABLE = Map.of(
        "putaway", Set.of("putaway_no", "receipt_no", "material_code", "batch_no", "quantity", "suggested_location", "actual_location", "pallet_sscc", "expiry_date", "handling_note"),
        "transfers", Set.of("transfer_no", "material_code", "batch_no", "quantity", "from_warehouse", "from_location", "to_warehouse", "to_location", "reason", "source_reference", "requested_by", "remark"),
        "counts", Set.of("count_no", "warehouse_code", "scope_type", "planned_date", "count_reason", "owner_user", "remark")
    );
    private static final Set<String> WMS_TRACE_EDITABLE = Set.of("supplier_lot_no", "pallet_sscc", "production_date", "expiry_date", "note");

    private String table(String kind) {
        String name = TABLES.get(kind);
        if (name == null) throw BizException.of(ErrorCode.PARAM_INVALID, "不支持的业务对象");
        return name;
    }

    private String statusColumn(String kind) {
        return switch (kind) {
            case "purchase-orders" -> "order_status";
            case "inspections" -> "inspect_result";
            case "reworks" -> "rework_status";
            case "supplier-quality" -> "eight_d_status";
            case "qualifications" -> "audit_result";
            case "performance", "inventories", "inventory-actions" -> null;
            default -> "status";
        };
    }

    public Page<Map<String, Object>> page(String kind, String keyword, String status, int page, int size) {
        String tb = table(kind), kw = keyword == null ? "" : keyword, statusCol = statusColumn(kind);
        String where = " WHERE (?='' OR CAST(id AS CHAR) LIKE ? OR CONCAT_WS(' '," + columnsForSearch(kind) + ") LIKE ?)"
            + (statusCol == null ? "" : " AND (?='' OR " + statusCol + "=?)");
        List<Object> args = new ArrayList<>(List.of(kw, "%" + kw + "%", "%" + kw + "%"));
        if (statusCol != null) { String value = status == null ? "" : status; args.add(value); args.add(value); }
        for (DataVisibility rule : visibility) {
            var filter = rule.filter(tb, ""); where += " AND (" + filter.sql() + ")"; args.addAll(filter.parameters());
        }
        Long count = db.queryForObject("SELECT COUNT(*) FROM " + tb + where, Long.class, args.toArray());
        int p = Math.max(0, page - 1), z = Math.min(200, Math.max(1, size));
        args.add(z); args.add(p * z);
        return new PageImpl<>(db.queryForList("SELECT * FROM " + tb + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray()), PageRequest.of(p, z), count == null ? 0 : count);
    }

    public Map<String, Object> detail(String kind, long id) {
        String tb = table(kind), where = " WHERE id=?";
        List<Object> args = new ArrayList<>(); args.add(id);
        for (DataVisibility rule : visibility) {
            var filter = rule.filter(tb, ""); where += " AND (" + filter.sql() + ")"; args.addAll(filter.parameters());
        }
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM " + tb + where, args.toArray());
        if (rows.isEmpty()) throw BizException.of(ErrorCode.DATA_SCOPE_DENIED, "记录不存在或超出数据范围");
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> create(String kind, Map<String, Object> input) {
        String tb = table(kind);
        Map<String, Object> values = toExistingColumns(tb, input);
        if (QMS_KINDS.contains(kind)) validateQmsCreate(kind, values);
        if (WMS_DRAFT_KINDS.contains(kind)) {
            values.keySet().retainAll(WMS_EDITABLE.get(kind));
            validateWmsCreate(kind, values);
        }
        if (values.isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID, "没有可保存字段");
        String fields = String.join(",", values.keySet());
        String marks = String.join(",", Collections.nCopies(values.size(), "?"));
        db.update("INSERT INTO " + tb + "(" + fields + ") VALUES(" + marks + ")", values.values().toArray());
        Long id = Objects.requireNonNull(db.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
        return detail(kind, id);
    }

    @Transactional
    public Map<String, Object> update(String kind, long id, Map<String, Object> input) {
        if (!QMS_KINDS.contains(kind) && !WMS_DRAFT_KINDS.contains(kind) && !WMS_TRACE_KINDS.contains(kind)) throw BizException.of(ErrorCode.PARAM_INVALID, "该对象暂不支持直接修改");
        Map<String, Object> current = lockForEdit(kind, id);
        if (QMS_KINDS.contains(kind)) ensureQmsEditable(kind, current);
        else if (WMS_DRAFT_KINDS.contains(kind)) ensureWmsDraft(kind, current);
        else ensureWmsReceiptEditable(current);
        String tb = table(kind);
        Map<String, Object> values = toExistingColumns(tb, input);
        values.keySet().retainAll(QMS_KINDS.contains(kind) ? QMS_EDITABLE.get(kind) : WMS_DRAFT_KINDS.contains(kind) ? WMS_EDITABLE.get(kind) : WMS_TRACE_EDITABLE);
        if (values.isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID, "没有可修改的单据字段");
        if (QMS_KINDS.contains(kind)) validateQmsUpdate(kind, current, values);
        if (WMS_DRAFT_KINDS.contains(kind)) validateWmsDocument(kind, values, current);
        String setters = values.keySet().stream().map(c -> c + "=?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(values.values()); args.add(id);
        if (db.update("UPDATE " + tb + " SET " + setters + " WHERE id=?", args.toArray()) == 0) throw BizException.notFound("业务记录", id);
        return detail(kind, id);
    }

    @Transactional
    public void delete(String kind, long id) {
        if (!QMS_KINDS.contains(kind) && !WMS_DRAFT_KINDS.contains(kind)) throw BizException.of(ErrorCode.PARAM_INVALID, "该对象暂不支持删除");
        Map<String, Object> current = lockForEdit(kind, id);
        if (QMS_KINDS.contains(kind)) ensureQmsDeletable(kind, current);
        else ensureWmsDraft(kind, current);
        if ("counts".equals(kind) && count("SELECT COUNT(*) FROM src_wms.wms_count_line WHERE count_id=?", id) > 0)
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "盘点单已有明细，请先删除全部盘点明细");
        String tb = table(kind);
        List<Object> args = new ArrayList<>(); args.add(id);
        String where = " WHERE id=?";
        for (DataVisibility rule : visibility) {
            var filter = rule.filter(tb, ""); where += " AND (" + filter.sql() + ")"; args.addAll(filter.parameters());
        }
        if (db.update("DELETE FROM " + tb + where, args.toArray()) == 0) throw BizException.notFound("业务记录", id);
    }

    private Map<String, Object> lockForEdit(String kind, long id) {
        // 先套用数据范围，再加行锁，避免编辑入口扩大可见范围。
        detail(kind, id);
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM " + table(kind) + " WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw BizException.notFound("业务记录", id);
        return rows.get(0);
    }

    private void ensureWmsDraft(String kind, Map<String, Object> row) {
        String status = String.valueOf(row.getOrDefault("status", ""));
        boolean editable = switch (kind) {
            case "putaway" -> "CREATED".equals(status);
            case "transfers", "counts" -> "DRAFT".equals(status);
            default -> false;
        };
        if (!editable) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有草稿单据可以修改或删除");
    }

    private void ensureWmsReceiptEditable(Map<String, Object> row) {
        if (!"PENDING_INSPECTION".equals(row.get("status")))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有待检收货单可以补充追溯信息");
    }

    private void validateWmsCreate(String kind, Map<String, Object> value) {
        Set<String> required = switch (kind) {
            case "putaway" -> Set.of("putaway_no", "receipt_no", "material_code", "quantity", "actual_location");
            case "transfers" -> Set.of("transfer_no", "material_code", "quantity", "from_warehouse", "to_warehouse");
            case "counts" -> Set.of("count_no", "warehouse_code", "scope_type");
            default -> Set.of();
        };
        for (String field : required) if (value.get(field) == null || String.valueOf(value.get(field)).isBlank())
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写" + wmsFieldName(field));
        validateWmsQuantity(value.get("quantity"));
        if ("putaway".equals(kind)) {
            List<Map<String, Object>> receipts = db.queryForList("SELECT * FROM src_wms.wms_receipt WHERE receipt_no=? FOR UPDATE", value.get("receipt_no"));
            if (receipts.isEmpty()) throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联收货单不存在");
            Map<String, Object> receipt = receipts.get(0);
            if (!"AVAILABLE".equals(receipt.get("status"))) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "收货单尚未通过质量检验，不能创建上架单");
            if (!Objects.equals(String.valueOf(receipt.get("material_code")), String.valueOf(value.get("material_code"))))
                throw BizException.of(ErrorCode.PARAM_INVALID, "上架物料必须与收货单一致");
            String batch = value.get("batch_no") == null ? null : String.valueOf(value.get("batch_no"));
            if (batch == null || batch.isBlank()) { batch = (String) receipt.get("batch_no"); value.put("batch_no", batch); }
            else if (!Objects.equals(batch, receipt.get("batch_no"))) throw BizException.of(ErrorCode.PARAM_INVALID, "上架批次必须与收货单一致");
            BigDecimal available = db.queryForObject("SELECT COALESCE(SUM(available_qty),0) FROM src_wms.wms_inventory WHERE material_code=? AND warehouse_code=? AND COALESCE(location_code,'')='' AND COALESCE(batch_no,'')=COALESCE(?,'')", BigDecimal.class, value.get("material_code"), receipt.get("warehouse_code"), batch);
            if (available == null || new BigDecimal(String.valueOf(value.get("quantity"))).compareTo(available) > 0)
                throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "待上架数量不能超过该批次可用库存");
        }
        if ("transfers".equals(kind) && Objects.equals(value.get("from_warehouse"), value.get("to_warehouse")))
            throw BizException.of(ErrorCode.PARAM_INVALID, "调出仓库和调入仓库不能相同");
    }

    private void validateWmsDocument(String kind, Map<String, Object> values, Map<String, Object> current) {
        if (values.containsKey("quantity")) validateWmsQuantity(values.get("quantity"));
        if ("transfers".equals(kind)) {
            String from = String.valueOf(values.getOrDefault("from_warehouse", current.get("from_warehouse")));
            String to = String.valueOf(values.getOrDefault("to_warehouse", current.get("to_warehouse")));
            if (from.equals(to)) throw BizException.of(ErrorCode.PARAM_INVALID, "调出仓库和调入仓库不能相同");
        }
    }

    private void validateWmsQuantity(Object value) {
        if (value == null) return;
        try {
            if (new java.math.BigDecimal(String.valueOf(value)).signum() <= 0)
                throw BizException.of(ErrorCode.PARAM_INVALID, "数量必须大于零");
        } catch (NumberFormatException ex) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "数量格式不正确");
        }
    }

    private String wmsFieldName(String field) {
        return switch (field) {
            case "putaway_no" -> "上架单号"; case "receipt_no" -> "收货单号"; case "material_code" -> "物料编码";
            case "quantity" -> "数量"; case "transfer_no" -> "调拨单号"; case "from_warehouse" -> "调出仓库";
            case "to_warehouse" -> "调入仓库"; case "count_no" -> "盘点单号"; case "warehouse_code" -> "仓库编码";
            case "scope_type" -> "盘点范围"; default -> field;
        };
    }

    @Transactional
    public Map<String, Object> status(String kind, long id, String value) {
        String column = statusColumn(kind);
        if (column == null) throw BizException.of(ErrorCode.PARAM_INVALID, "对象没有状态字段");
        Map<String, Object> current = detail(kind, id);
        String from = String.valueOf(current.get(column));
        if (from.equals(value)) return current;
        Map<String, Set<String>> transitions = statusRules(kind);
        if (!transitions.getOrDefault(from, Set.of()).contains(value))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "当前业务状态不允许此流转，请刷新后重试");
        if (db.update("UPDATE " + table(kind) + " SET " + column + "=? WHERE id=?", value, id) == 0) throw BizException.notFound("业务记录", id);
        return detail(kind, id);
    }

    private Map<String, Object> toExistingColumns(String tb, Map<String, Object> input) {
        String[] name = tb.split("\\.");
        Set<String> existing = new HashSet<>(db.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND EXTRA NOT LIKE '%auto_increment%'",
            String.class, name[0], name[1]));
        Map<String, Object> values = new LinkedHashMap<>();
        if (input != null) input.forEach((key, value) -> {
            String column = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            if (existing.contains(column)) values.put(column, value);
        });
        return values;
    }

    private void validateQmsCreate(String kind, Map<String, Object> value) {
        Set<String> required = switch (kind) {
            case "standards" -> Set.of("standard_code", "standard_name", "inspection_type");
            case "sampling-plans" -> Set.of("plan_code", "plan_name", "sample_size");
            case "ncrs" -> Set.of("ncr_no", "inspection_no", "material_code", "defect_qty");
            case "capas" -> Set.of("capa_no", "ncr_no");
            case "8d" -> Set.of("eight_d_no", "ncr_no", "supplier_code");
            default -> Set.of();
        };
        for (String column : required) if (value.get(column) == null || String.valueOf(value.get(column)).isBlank())
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写" + qmsFieldName(column));
        if ("sampling-plans".equals(kind)) {
            Integer sample = integer(value.get("sample_size"));
            if (sample == null || sample <= 0) throw BizException.of(ErrorCode.PARAM_INVALID, "抽样数量必须大于0");
            Integer accept = integer(value.get("accept_qty")), reject = integer(value.get("reject_qty"));
            if (accept != null && reject != null && reject <= accept) throw BizException.of(ErrorCode.PARAM_INVALID, "拒收数必须大于接收数");
        }
        if ("ncrs".equals(kind) && count("SELECT COUNT(*) FROM src_qms.qms_inspection WHERE inspection_no=?", value.get("inspection_no")) == 0)
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联检验单不存在");
        if (("capas".equals(kind) || "8d".equals(kind)) && count("SELECT COUNT(*) FROM src_qms.qms_ncr WHERE ncr_no=?", value.get("ncr_no")) == 0)
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联NCR不存在");
    }

    private void validateQmsUpdate(String kind, Map<String, Object> current, Map<String, Object> value) {
        if ("sampling-plans".equals(kind)) {
            Integer sample = integer(value.getOrDefault("sample_size", current.get("sample_size")));
            Integer accept = integer(value.getOrDefault("accept_qty", current.get("accept_qty")));
            Integer reject = integer(value.getOrDefault("reject_qty", current.get("reject_qty")));
            if (sample != null && sample <= 0) throw BizException.of(ErrorCode.PARAM_INVALID, "抽样数量必须大于0");
            if (accept != null && reject != null && reject <= accept) throw BizException.of(ErrorCode.PARAM_INVALID, "拒收数必须大于接收数");
        }
    }

    private void ensureQmsEditable(String kind, Map<String, Object> row) {
        String status = String.valueOf(row.getOrDefault("status", ""));
        boolean allowed = switch (kind) {
            case "standards" -> Set.of("DRAFT", "DISABLED").contains(status);
            case "sampling-plans" -> Set.of("ACTIVE", "ENABLED", "DISABLED").contains(status);
            case "ncrs" -> Set.of("OPEN", "REVIEWING").contains(status);
            case "capas" -> Set.of("DRAFT", "IMPLEMENTING").contains(status);
            case "8d" -> Set.of("OPEN", "SUBMITTED").contains(status);
            default -> false;
        };
        if (!allowed) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "当前质量单据状态不允许修改");
    }

    private void ensureQmsDeletable(String kind, Map<String, Object> row) {
        String status = String.valueOf(row.getOrDefault("status", ""));
        boolean allowed = switch (kind) {
            case "standards" -> Set.of("DRAFT", "DISABLED").contains(status)
                && count("SELECT COUNT(*) FROM src_qms.qms_inspection WHERE standard_code=?", row.get("standard_code")) == 0;
            case "sampling-plans" -> Set.of("DISABLED", "DRAFT").contains(status);
            case "ncrs" -> "OPEN".equals(status)
                && count("SELECT COUNT(*) FROM src_qms.qms_capa WHERE ncr_no=?", row.get("ncr_no")) == 0
                && count("SELECT COUNT(*) FROM src_qms.qms_eight_d WHERE ncr_no=?", row.get("ncr_no")) == 0;
            case "capas" -> "DRAFT".equals(status);
            case "8d" -> "OPEN".equals(status);
            default -> false;
        };
        if (!allowed) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "当前质量单据已生效、已流转或被引用，不能删除");
    }

    private long count(String sql, Object value) { return Objects.requireNonNullElse(db.queryForObject(sql, Long.class, value), 0L); }
    private Integer integer(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; return value instanceof Number n ? n.intValue() : Integer.valueOf(String.valueOf(value)); }
    private String qmsFieldName(String column) {
        return switch (column) {
            case "standard_code" -> "标准编码"; case "standard_name" -> "标准名称"; case "inspection_type" -> "检验类型";
            case "plan_code" -> "方案编码"; case "plan_name" -> "方案名称"; case "sample_size" -> "抽样数量";
            case "ncr_no" -> "NCR编号"; case "inspection_no" -> "检验单号"; case "material_code" -> "物料编码";
            case "defect_qty" -> "不合格数量"; case "capa_no" -> "CAPA编号"; case "eight_d_no" -> "8D编号";
            case "supplier_code" -> "供应商编码"; default -> column;
        };
    }

    private Map<String, Set<String>> statusRules(String kind) {
        return switch (kind) {
            case "onboarding" -> Map.of("DRAFT", Set.of("APPROVING"), "APPROVING", Set.of("APPROVED", "REJECTED"), "APPROVED", Set.of("CERTIFIED", "BLACKLISTED"), "CERTIFIED", Set.of("BLACKLISTED"));
            case "qualifications" -> Map.of("PENDING", Set.of("PASSED", "FAILED"), "FAILED", Set.of("PENDING"));
            case "rfqs" -> Map.of("DRAFT", Set.of("PUBLISHED", "CANCELLED"), "PUBLISHED", Set.of("QUOTING", "CLOSED", "CANCELLED"), "QUOTING", Set.of("CLOSED", "CANCELLED"), "CLOSED", Set.of("CANCELLED"));
            case "asns" -> Map.of("DRAFT", Set.of("SHIPPED", "CANCELLED"), "SHIPPED", Set.of("CANCELLED"), "ARRIVED", Set.of("RECEIVED", "RETURNED"));
            case "supplier-quality" -> Map.of("OPEN", Set.of("EIGHT_D_REQUIRED"), "EIGHT_D_REQUIRED", Set.of("VERIFYING"), "VERIFYING", Set.of("CLOSED", "EIGHT_D_REQUIRED"));
            case "putaway" -> Map.of("CREATED", Set.of("PROCESSING", "CANCELLED"), "PROCESSING", Set.of("CANCELLED"));
            case "transfers" -> Map.of("DRAFT", Set.of("CANCELLED"), "CONFIRMED", Set.of("CANCELLED"));
            case "counts" -> Map.of("DRAFT", Set.of("CANCELLED"));
            case "standards" -> Map.of("DRAFT", Set.of("PUBLISHED"), "PUBLISHED", Set.of("DISABLED"), "DISABLED", Set.of("PUBLISHED"));
            case "sampling-plans" -> Map.of("ACTIVE", Set.of("DISABLED"), "ENABLED", Set.of("DISABLED"), "DISABLED", Set.of("ACTIVE"));
            case "ncrs" -> Map.of("OPEN", Set.of("REVIEWING"), "REVIEWING", Set.of("REWORK", "SCRAPPED", "CONCESSION", "RETURNED"), "REWORK", Set.of("CLOSED"), "SCRAPPED", Set.of("CLOSED"), "CONCESSION", Set.of("CLOSED"), "RETURNED", Set.of("CLOSED"));
            case "capas" -> Map.of("DRAFT", Set.of("IMPLEMENTING"), "IMPLEMENTING", Set.of("VERIFYING"), "VERIFYING", Set.of("CLOSED", "IMPLEMENTING"));
            case "8d" -> Map.of("OPEN", Set.of("SUBMITTED"), "SUBMITTED", Set.of("VERIFYING"), "VERIFYING", Set.of("CLOSED", "SUBMITTED"));
            default -> Map.of();
        };
    }

    private String columnsForSearch(String kind) {
        return switch (kind) {
            case "onboarding" -> "application_no,supplier_name,contact_name,contact_phone";
            case "qualifications" -> "certificate_no,qualification_type";
            case "rfqs" -> "rfq_no,title,material_code,source_requisition_no";
            case "quotes" -> "supplier_code,rfq_id,status";
            case "purchase-orders" -> "purchase_order_no,supplier_code,material_code,source_requisition_no";
            case "asns" -> "asn_no,purchase_order_no,supplier_code";
            case "supplier-quality" -> "issue_no,supplier_code,source_no";
            case "performance" -> "supplier_code,period_code";
            case "receipts" -> "receipt_no,asn_no,material_code";
            case "putaway" -> "putaway_no,receipt_no,material_code";
            case "inventories" -> "material_code,warehouse_code,location_code,batch_no";
            case "inventory-actions" -> "idempotency_key,action_type,material_code,source_no,operator_code";
            case "transfers" -> "transfer_no,material_code";
            case "counts" -> "count_no,warehouse_code";
            case "standards" -> "standard_code,standard_name,material_code";
            case "sampling-plans" -> "plan_code,plan_name";
            case "inspections" -> "inspection_no,material_code,source_no";
            case "ncrs" -> "ncr_no,inspection_no,material_code";
            case "reworks" -> "rework_no,defect_no,material_code";
            case "capas" -> "capa_no,ncr_no,owner_user";
            case "8d" -> "eight_d_no,ncr_no,supplier_code";
            default -> "id";
        };
    }
}
