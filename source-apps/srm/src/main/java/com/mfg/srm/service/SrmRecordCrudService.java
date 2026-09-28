package com.mfg.srm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.common.service.P3CrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SrmRecordCrudService {
    private static final Map<String, String> TABLES = Map.of(
            "onboarding", "src_srm.srm_onboarding",
            "qualifications", "src_srm.srm_qualification",
            "rfqs", "src_srm.srm_rfq",
            "quotes", "src_srm.srm_supplier_quote",
            "purchase-orders", "src_srm.srm_purchase_order",
            "asns", "src_srm.srm_asn",
            "supplier-quality", "src_srm.srm_supplier_quality",
            "performance", "src_srm.srm_supplier_performance"
    );
    private static final Map<String, String> STATE_COLUMNS = Map.of(
            "onboarding", "status",
            "qualifications", "audit_result",
            "rfqs", "status",
            "quotes", "status",
            "purchase-orders", "order_status",
            "asns", "status",
            "supplier-quality", "eight_d_status"
    );
    private static final Map<String, Set<String>> REQUIRED = Map.of(
            "onboarding", Set.of("application_no", "supplier_name"),
            "qualifications", Set.of("onboarding_id", "qualification_type", "certificate_no"),
            "rfqs", Set.of("rfq_no", "title", "material_code", "quantity", "required_date"),
            "quotes", Set.of("rfq_id", "supplier_code", "unit_price", "delivery_date"),
            "purchase-orders", Set.of("purchase_order_no", "supplier_code", "supplier_name", "material_code",
                    "material_name", "order_qty", "unit_code", "unit_price", "expected_date"),
            "asns", Set.of("asn_no", "purchase_order_no", "supplier_code", "material_code", "ship_qty"),
            "supplier-quality", Set.of("issue_no", "supplier_code")
    );
    private static final Map<String, Set<String>> IMMUTABLE = Map.of(
            "onboarding", Set.of("application_no"),
            "rfqs", Set.of("rfq_no"),
            "quotes", Set.of("rfq_id", "supplier_code"),
            "purchase-orders", Set.of("purchase_order_no", "purchase_user", "received_qty", "total_amount"),
            "asns", Set.of("asn_no", "created_by"),
            "supplier-quality", Set.of("issue_no"),
            "performance", Set.of("*")
    );

    private final JdbcTemplate db;
    private final P3CrudService records;

    @Transactional
    public Map<String, Object> create(String kind, Map<String, Object> body) {
        requireWritable(kind);
        Map<String, Object> values = toColumns(body);
        validate(kind, values);
        checkBusinessNumberUnique(kind, values);
        if ("quotes".equals(kind) && exists(
                "SELECT COUNT(*) FROM src_srm.srm_supplier_quote WHERE rfq_id=? AND supplier_code=?",
                parseId(values.get("rfq_id"), "询价记录号"), values.get("supplier_code")))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "该供应商已提交此询价单的报价");
        Set<String> allowed = columns(kind);
        String stateColumn = STATE_COLUMNS.get(kind);
        Map<String, Object> clean = new LinkedHashMap<>();
        values.forEach((column, value) -> {
            if (allowed.contains(column) && !systemColumn(column) && !column.equals(stateColumn)) clean.put(column, value);
        });
        if ("qualifications".equals(kind)) clean.put("audit_result", "PENDING");
        if (clean.isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID, "没有可保存的业务字段");
        Map<String, Object> camelCase = new LinkedHashMap<>();
        clean.forEach((column, value) -> camelCase.put(toCamel(column), value));
        return records.create(kind, camelCase);
    }

    public void validateCreate(String kind, Map<String, Object> body) {
        requireWritable(kind);
        validate(kind, toColumns(body));
    }

    @Transactional
    public Map<String, Object> update(String kind, long id, Map<String, Object> body) {
        requireWritable(kind);
        Map<String, Object> before = records.detail(kind, id);
        ensureEditable(kind, before);
        Set<String> allowed = columns(kind);
        Set<String> immutable = IMMUTABLE.getOrDefault(kind, Set.of());
        String stateColumn = STATE_COLUMNS.get(kind);
        Map<String, Object> changes = new LinkedHashMap<>();

        toColumns(body).forEach((column, value) -> {
            if (column.equals(stateColumn)) {
                throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "单据状态请使用状态流转操作修改");
            }
            if (allowed.contains(column) && !systemColumn(column) && !immutable.contains(column)) {
                changes.put(column, value);
            }
        });
        if (changes.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "没有可修改的业务字段");
        }

        Map<String, Object> candidate = new HashMap<>(before);
        candidate.putAll(changes);
        validate(kind, candidate);
        if ("purchase-orders".equals(kind)
                && (changes.containsKey("order_qty") || changes.containsKey("unit_price"))) {
            java.math.BigDecimal quantity = new java.math.BigDecimal(String.valueOf(candidate.get("order_qty")));
            java.math.BigDecimal price = new java.math.BigDecimal(String.valueOf(candidate.get("unit_price")));
            changes.put("total_amount", quantity.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP));
        }

        List<Object> args = new ArrayList<>(changes.values());
        String assignments = String.join(",", changes.keySet().stream().map(column -> column + "=?").toList());
        if (allowed.contains("updated_at")) assignments += ",updated_at=CURRENT_TIMESTAMP(3)";
        args.add(id);
        db.update("UPDATE " + table(kind) + " SET " + assignments + " WHERE id=?", args.toArray());
        return records.detail(kind, id);
    }

    @Transactional
    public void delete(String kind, long id) {
        requireWritable(kind);
        Map<String, Object> record = records.detail(kind, id);
        ensureNotFinal(kind, record);
        ensureNotReferenced(kind, id, record);
        db.update("DELETE FROM " + table(kind) + " WHERE id=?", id);
    }

    private void validate(String kind, Map<String, Object> values) {
        Set<String> required = REQUIRED.getOrDefault(kind, Set.of());
        for (String column : required) {
            Object value = values.get(column);
            if (value == null || String.valueOf(value).isBlank()) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "请填写必填字段：" + columnLabel(column));
            }
        }
        for (String column : List.of("quantity", "unit_price", "order_qty", "ship_qty", "minimum_order_qty",
                "invited_supplier_count", "tax_rate", "payment_term_days", "freight_amount", "warranty_months", "defect_qty")) {
            Object value = values.get(column);
            if (value != null && !String.valueOf(value).isBlank()) {
                try {
                    int sign = new java.math.BigDecimal(String.valueOf(value)).signum();
                    boolean positive = Set.of("quantity", "order_qty", "ship_qty").contains(column);
                    if (sign < 0 || (positive && sign == 0)) {
                        throw BizException.of(ErrorCode.PARAM_INVALID, columnLabel(column) + (positive ? "必须大于零" : "不能小于零"));
                    }
                } catch (NumberFormatException ex) {
                    throw BizException.of(ErrorCode.PARAM_INVALID, columnLabel(column) + "必须是有效数字");
                }
            }
        }
        if ("qualifications".equals(kind)) {
            Object onboardingId = values.get("onboarding_id");
            if (onboardingId != null && !String.valueOf(onboardingId).isBlank()) {
                records.detail("onboarding", parseId(onboardingId, "准入申请记录号"));
            }
            if (compareDate(values.get("issue_date"), values.get("expire_date")) > 0)
                throw BizException.of(ErrorCode.PARAM_INVALID, "资质到期日期不能早于发证日期");
        }
        if ("quotes".equals(kind)) {
            Object rfqId = values.get("rfq_id");
            if (rfqId != null) {
                long id = parseId(rfqId, "询价记录号");
                Map<String, Object> rfq = records.detail("rfqs", id);
                if (!Set.of("PUBLISHED", "QUOTING").contains(String.valueOf(rfq.get("status"))))
                    throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有已发布或报价中的询价单可以登记报价");
            }
        }
        if ("asns".equals(kind)) {
            Object orderNo = values.get("purchase_order_no");
            if (orderNo != null) {
                List<String> orderStates = db.queryForList(
                        "SELECT order_status FROM src_srm.srm_purchase_order WHERE purchase_order_no=?",
                        String.class, String.valueOf(orderNo));
                if (orderStates.isEmpty()) throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "关联采购订单不存在");
                if (!Set.of("SENT", "PARTIAL").contains(orderStates.get(0)))
                    throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "采购订单下达后才能登记发货通知");
            }
        }
    }

    private long parseId(Object value, String label) {
        try {
            long id = Long.parseLong(String.valueOf(value));
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw BizException.of(ErrorCode.PARAM_INVALID, label + "必须是有效的正整数");
        }
    }

    private void checkBusinessNumberUnique(String kind, Map<String, Object> values) {
        String column = switch (kind) {
            case "onboarding" -> "application_no";
            case "rfqs" -> "rfq_no";
            case "asns" -> "asn_no";
            case "supplier-quality" -> "issue_no";
            default -> null;
        };
        if (column != null && values.get(column) != null
                && exists("SELECT COUNT(*) FROM " + table(kind) + " WHERE " + column + "=?", values.get(column))) {
            String label = switch (kind) {
                case "onboarding" -> "准入申请编号";
                case "rfqs" -> "询价单号";
                case "asns" -> "发货通知单号";
                default -> "供应商质量问题编号";
            };
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, label + "已存在");
        }
    }

    private int compareDate(Object first, Object second) {
        if (first == null || second == null) return 0;
        return String.valueOf(first).compareTo(String.valueOf(second));
    }

    private void ensureNotFinal(String kind, Map<String, Object> record) {
        String stateColumn = STATE_COLUMNS.get(kind);
        if (stateColumn == null) return;
        String state = String.valueOf(record.getOrDefault(stateColumn, ""));
        boolean removable = switch (kind) {
            case "onboarding" -> Set.of("DRAFT", "REJECTED").contains(state);
            case "qualifications" -> Set.of("PENDING", "FAILED").contains(state);
            case "rfqs" -> Set.of("DRAFT", "CANCELLED").contains(state);
            case "purchase-orders" -> "CREATED".equals(state);
            case "asns" -> Set.of("DRAFT", "CANCELLED").contains(state);
            case "supplier-quality" -> Set.of("OPEN", "EIGHT_D_REQUIRED").contains(state);
            default -> true;
        };
        if (!removable) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "已流转的单据不能删除");
    }

    private void ensureEditable(String kind, Map<String, Object> record) {
        String stateColumn = STATE_COLUMNS.get(kind);
        if (stateColumn == null) return;
        String state = String.valueOf(record.getOrDefault(stateColumn, ""));
        boolean editable = switch (kind) {
            case "onboarding" -> Set.of("DRAFT", "REJECTED").contains(state);
            case "qualifications" -> Set.of("PENDING", "FAILED").contains(state);
            case "rfqs" -> "DRAFT".equals(state);
            case "quotes" -> Set.of("SUBMITTED", "DRAFT").contains(state)
                    && exists("SELECT COUNT(*) FROM src_srm.srm_rfq WHERE id=? AND status IN ('PUBLISHED','QUOTING')", record.get("rfq_id"));
            case "purchase-orders" -> "CREATED".equals(state);
            case "asns" -> "DRAFT".equals(state);
            case "supplier-quality" -> Set.of("OPEN", "EIGHT_D_REQUIRED", "VERIFYING").contains(state);
            default -> true;
        };
        if (!editable) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "已流转的单据不能修改");
    }

    private void ensureNotReferenced(String kind, long id, Map<String, Object> record) {
        boolean referenced = switch (kind) {
            case "onboarding" -> exists("SELECT COUNT(*) FROM src_srm.srm_qualification WHERE onboarding_id=?", id);
            case "rfqs" -> exists("SELECT COUNT(*) FROM src_srm.srm_supplier_quote WHERE rfq_id=?", id);
            case "quotes" -> exists("SELECT COUNT(*) FROM src_srm.srm_rfq WHERE id=? AND status='AWARDED' AND winner_supplier_code=?",
                    record.get("rfq_id"), record.get("supplier_code"));
            case "purchase-orders" -> exists("SELECT COUNT(*) FROM src_srm.srm_asn WHERE purchase_order_no=?",
                            record.get("purchase_order_no"))
                    || exists("SELECT COUNT(*) FROM src_srm.srm_delivery WHERE purchase_order_no=?",
                            record.get("purchase_order_no"))
                    || exists("SELECT COUNT(*) FROM src_wms.wms_receipt WHERE purchase_order_no=?",
                            record.get("purchase_order_no"));
            case "asns" -> exists("SELECT COUNT(*) FROM src_wms.wms_receipt WHERE asn_no=?", record.get("asn_no"));
            default -> false;
        };
        if (referenced) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "该记录仍被下游单据引用，请先解除关联");
    }

    private boolean exists(String sql, Object... args) {
        Integer count = db.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private Set<String> columns(String kind) {
        String[] parts = table(kind).split("\\.");
        return new HashSet<>(db.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?",
                String.class, parts[0], parts[1]));
    }

    private Map<String, Object> toColumns(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        body.forEach((name, value) -> {
            String column = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            Object normalized = value;
            if (value instanceof String text && text.isBlank()) normalized = null;
            result.put(column, normalized);
        });
        return result;
    }

    private String toCamel(String column) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : column.toCharArray()) {
            if (character == '_') upper = true;
            else {
                result.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return result.toString();
    }

    private boolean systemColumn(String column) {
        return Set.of("id", "created_at", "updated_at", "created_by", "closed_at", "closed_by",
                "workflow_instance_id").contains(column);
    }

    private String table(String kind) {
        String table = TABLES.get(kind);
        if (table == null) throw BizException.of(ErrorCode.PARAM_INVALID, "不支持的SRM业务对象");
        return table;
    }

    private void requireWritable(String kind) {
        table(kind);
        if ("performance".equals(kind)) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "供应商绩效为统计台账，不能直接新增、修改或删除");
        }
    }

    private String columnLabel(String column) {
        return switch (column) {
            case "application_no" -> "申请编号";
            case "supplier_name" -> "供应商名称";
            case "qualification_type" -> "资质类别";
            case "certificate_no" -> "证书编号";
            case "rfq_no" -> "询价单号";
            case "title" -> "询价主题";
            case "material_code" -> "物料编码";
            case "quantity" -> "需求数量";
            case "required_date" -> "需求日期";
            case "rfq_id" -> "询价记录";
            case "supplier_code" -> "供应商编码";
            case "unit_price" -> "报价单价";
            case "delivery_date" -> "承诺交货日期";
            case "purchase_order_no" -> "采购订单号";
            case "material_name" -> "物料名称";
            case "order_qty" -> "订购数量";
            case "unit_code" -> "计量单位";
            case "expected_date" -> "要求交货日期";
            case "asn_no" -> "发货通知单号";
            case "ship_qty" -> "发运数量";
            case "issue_no" -> "问题编号";
            default -> column;
        };
    }
}
