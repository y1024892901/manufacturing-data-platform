package com.mfg.erp.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ErpDocumentService {
    private record Spec(String table, String permissionPrefix, Map<String, String> editableFields) { }

    private static final Map<String, Spec> SPECS = Map.of(
            "mrp", new Spec("erp_mrp_run", "ERP:MRP", Map.ofEntries(
                    Map.entry("salesOrderNo", "sales_order_no"), Map.entry("factoryCode", "factory_code"),
                    Map.entry("planDate", "plan_date"), Map.entry("planner", "planner"), Map.entry("remark", "remark"))),
            "suggestions", new Spec("erp_plan_suggestion", "ERP:PLAN_SUGGESTION", Map.ofEntries(
                    Map.entry("quantity", "quantity"), Map.entry("unitCode", "unit_code"),
                    Map.entry("requiredDate", "required_date"), Map.entry("priorityLevel", "priority_level"),
                    Map.entry("plannerNote", "planner_note"))),
            "invoices", new Spec("erp_invoice", "ERP:INVOICE", Map.ofEntries(
                    Map.entry("invoiceDate", "invoice_date"), Map.entry("amount", "amount"),
                    Map.entry("taxAmount", "tax_amount"), Map.entry("dueDate", "due_date"),
                    Map.entry("customerReference", "customer_reference"), Map.entry("paymentTerms", "payment_terms"),
                    Map.entry("externalReference", "external_reference"), Map.entry("remark", "remark"))),
            "receipts", new Spec("erp_receipt", "ERP:RECEIPT", Map.ofEntries(
                    Map.entry("bankReference", "bank_reference"), Map.entry("receiptDate", "receipt_date"),
                    Map.entry("amount", "amount"), Map.entry("paymentMethod", "payment_method"),
                    Map.entry("depositAccount", "deposit_account"), Map.entry("remittanceReference", "remittance_reference"),
                    Map.entry("remark", "remark"))),
            "payables", new Spec("erp_payable", "ERP:PAYABLE", Map.ofEntries(
                    Map.entry("supplierCode", "supplier_code"), Map.entry("sourceOrderNo", "source_order_no"),
                    Map.entry("amount", "amount"), Map.entry("dueDate", "due_date"),
                    Map.entry("supplierInvoiceNo", "supplier_invoice_no"), Map.entry("invoiceDate", "invoice_date"),
                    Map.entry("invoiceReceivedDate", "invoice_received_date"), Map.entry("paymentTerms", "payment_terms"),
                    Map.entry("paymentMethod", "payment_method"), Map.entry("costCenterCode", "cc_code"),
                    Map.entry("remark", "remark"))),
            "receivables", new Spec("erp_receivable", "ERP:RECEIVABLE", Map.ofEntries(
                    Map.entry("customerCode", "customer_code"), Map.entry("salesOrderNo", "sales_order_no"),
                    Map.entry("invoiceDate", "invoice_date"), Map.entry("dueDate", "due_date"),
                    Map.entry("amount", "invoice_amount"), Map.entry("paymentTerms", "payment_terms"),
                    Map.entry("customerReference", "customer_reference"), Map.entry("remark", "remark"))),
            "credits", new Spec("erp_customer_credit", "ERP:CREDIT", Map.of())
    );

    private static final Set<String> DELETEABLE = Set.of("mrp", "suggestions", "invoices", "receipts", "payables", "receivables");

    private final JdbcTemplate db;
    private final com.mfg.security.scope.DataScopePolicy scope;

    @Transactional(readOnly = true)
    public Map<String, Object> get(String kind, long id) {
        Spec spec = spec(kind);
        requirePermission(spec.permissionPrefix() + ":VIEW");
        return one(spec.table(), "id", id);
    }

    @Transactional
    public Map<String, Object> createMrp(Map<String, Object> body) {
        requirePermission("ERP:MRP:CREATE");
        String salesOrderNo = required(body, "salesOrderNo", "销售订单号");
        one("erp_sales_order", "sales_order_no", salesOrderNo);
        String runNo = value(body, "runNo", "MRP-" + System.currentTimeMillis());
        if (count("SELECT COUNT(*) FROM src_erp.erp_mrp_run WHERE run_no=?", runNo) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "计划运行编号已存在");
        }
        Date planDate = date(body, "planDate");
        if (planDate == null) planDate = Date.valueOf(LocalDate.now());
        String runType = value(body, "runType", "ORDER");
        if (!"ORDER".equals(runType)) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "当前计划运行仅支持按销售订单计算");
        }
        db.update("INSERT INTO src_erp.erp_mrp_run(run_no,run_type,sales_order_no,factory_code,plan_date,status,created_by,planner,remark) VALUES(?,?,?,?,?,'CREATED',?,?,?)",
                runNo, runType, salesOrderNo, value(body, "factoryCode", "F001"), planDate,
                CurrentUser.usernameOrSystem(), optional(body, "planner"), optional(body, "remark"));
        return one("erp_mrp_run", "run_no", runNo);
    }

    @Transactional
    public Map<String, Object> createInvoice(Map<String, Object> body) {
        requirePermission("ERP:INVOICE:CREATE");
        String salesOrderNo = required(body, "salesOrderNo", "销售订单号");
        Map<String, Object> order = one("erp_sales_order", "sales_order_no", salesOrderNo);
        String invoiceNo = value(body, "invoiceNo", "INV-" + System.currentTimeMillis());
        if (count("SELECT COUNT(*) FROM src_erp.erp_invoice WHERE invoice_no=?", invoiceNo) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "发票号已存在");
        }
        BigDecimal amount = positive(body, "amount", "开票金额");
        BigDecimal taxAmount = nonNegative(body, "taxAmount", "税额");
        Date invoiceDate = date(body, "invoiceDate");
        if (invoiceDate == null) invoiceDate = Date.valueOf(LocalDate.now());
        Date dueDate = date(body, "dueDate");
        if (dueDate == null) dueDate = Date.valueOf(invoiceDate.toLocalDate().plusDays(30));
        if (dueDate.before(invoiceDate)) throw BizException.of(ErrorCode.PARAM_INVALID, "应收到期日不能早于开票日期");

        db.update("INSERT INTO src_erp.erp_invoice(invoice_no,sales_order_no,customer_code,invoice_date,amount,tax_amount,status,created_by,customer_reference,payment_terms,due_date,external_reference,remark) VALUES(?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?)",
                invoiceNo, salesOrderNo, order.get("customer_code"), invoiceDate, amount, taxAmount,
                CurrentUser.usernameOrSystem(), optional(body, "customerReference"), optional(body, "paymentTerms"),
                dueDate, optional(body, "externalReference"), optional(body, "remark"));
        Map<String, Object> invoice = one("erp_invoice", "invoice_no", invoiceNo);
        if (!flag(body.get("draft"))) return issueInvoiceInternal(number(invoice.get("id")));
        return invoice;
    }

    @Transactional
    public Map<String, Object> issueInvoice(long id) {
        requirePermission("ERP:INVOICE:ISSUE");
        return issueInvoiceInternal(id);
    }

    @Transactional
    public Map<String, Object> createReceipt(Map<String, Object> body) {
        requirePermission("ERP:FINANCE:RECEIPT");
        String customerCode = required(body, "customerCode", "客户编码");
        one("md_customer", "customer_code", customerCode);
        String receiptNo = value(body, "receiptNo", "RC-" + System.currentTimeMillis());
        if (count("SELECT COUNT(*) FROM src_erp.erp_receipt WHERE receipt_no=?", receiptNo) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "回款单号已存在");
        }
        BigDecimal amount = positive(body, "amount", "回款金额");
        Date receiptDate = date(body, "receiptDate");
        if (receiptDate == null) receiptDate = Date.valueOf(LocalDate.now());
        db.update("INSERT INTO src_erp.erp_receipt(receipt_no,customer_code,bank_reference,receipt_date,amount,unapplied_amount,status,created_by,payment_method,deposit_account,remittance_reference,remark) VALUES(?,?,?,?,?,?,'DRAFT',?,?,?,?,?)",
                receiptNo, customerCode, optional(body, "bankReference"), receiptDate, amount, amount,
                CurrentUser.usernameOrSystem(), optional(body, "paymentMethod"), optional(body, "depositAccount"),
                optional(body, "remittanceReference"), optional(body, "remark"));
        Map<String, Object> receipt = one("erp_receipt", "receipt_no", receiptNo);
        if (!flag(body.get("draft"))) return postReceiptInternal(number(receipt.get("id")));
        return receipt;
    }

    @Transactional
    public Map<String, Object> createReceivable(Map<String, Object> body) {
        requirePermission("ERP:RECEIVABLE:CREATE");
        String customerCode = required(body, "customerCode", "客户编码");
        Map<String, Object> customer = one("md_customer", "customer_code", customerCode);
        String salesOrderNo = optional(body, "salesOrderNo");
        if (salesOrderNo != null) {
            Map<String, Object> order = one("erp_sales_order", "sales_order_no", salesOrderNo);
            if (!customerCode.equals(String.valueOf(order.get("customer_code")))) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "销售订单与客户不匹配");
            }
        }
        Date invoiceDate = date(body, "invoiceDate");
        if (invoiceDate == null) invoiceDate = Date.valueOf(LocalDate.now());
        Date dueDate = date(body, "dueDate");
        if (dueDate == null) dueDate = Date.valueOf(invoiceDate.toLocalDate().plusDays(30));
        if (dueDate.before(invoiceDate)) throw BizException.of(ErrorCode.PARAM_INVALID, "应收到期日不能早于业务日期");
        BigDecimal amount = positive(body, "amount", "应收金额");
        String receivableNo = value(body, "receivableNo", "AR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4));
        if (count("SELECT COUNT(*) FROM src_erp.erp_receivable WHERE receivable_no=?", receivableNo) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "应收单号已存在");
        }
        db.update("INSERT INTO src_erp.erp_receivable(receivable_no,customer_code,customer_name,sales_order_no,invoice_no,invoice_date,due_date,invoice_amount,received_amount,outstanding_amount,settle_status,created_by,settled_amount,status,payment_terms,customer_reference,remark,source_type) VALUES(?,?,?,?,NULL,?,?,?,0,?,'OPEN',?,0,'OPEN',?,?,?,'MANUAL')",
                receivableNo, customerCode, customer.get("customer_name"), salesOrderNo, invoiceDate, dueDate, amount,
                amount, CurrentUser.usernameOrSystem(), optional(body, "paymentTerms"),
                optional(body, "customerReference"), optional(body, "remark"));
        return one("erp_receivable", "receivable_no", receivableNo);
    }

    @Transactional
    public Map<String, Object> postReceipt(long id) {
        requirePermission("ERP:RECEIPT:POST");
        return postReceiptInternal(id);
    }

    @Transactional
    public Map<String, Object> createPayable(Map<String, Object> body) {
        requirePermission("ERP:PAYABLE:CREATE");
        String supplierCode = required(body, "supplierCode", "供应商编码");
        one("md_supplier", "supplier_code", supplierCode);
        String payableNo = value(body, "payableNo", "AP-" + System.currentTimeMillis());
        if (count("SELECT COUNT(*) FROM src_erp.erp_payable WHERE payable_no=?", payableNo) > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "应付单号已存在");
        }
        BigDecimal amount = positive(body, "amount", "应付金额");
        db.update("INSERT INTO src_erp.erp_payable(payable_no,supplier_code,source_order_no,amount,paid_amount,due_date,status,supplier_invoice_no,invoice_date,invoice_received_date,payment_terms,payment_method,cc_code,remark) VALUES(?,?,?, ?,0,?,'OPEN',?,?,?,?,?,?,?)",
                payableNo, supplierCode, optional(body, "sourceOrderNo"), amount, date(body, "dueDate"),
                optional(body, "supplierInvoiceNo"), date(body, "invoiceDate"), date(body, "invoiceReceivedDate"),
                optional(body, "paymentTerms"), optional(body, "paymentMethod"), optional(body, "costCenterCode"),
                optional(body, "remark"));
        return one("erp_payable", "payable_no", payableNo);
    }

    @Transactional
    public Map<String, Object> update(String kind, long id, Map<String, Object> body) {
        Spec spec = spec(kind);
        requirePermission(spec.permissionPrefix() + ":UPDATE");
        Map<String, Object> row = one(spec.table(), "id", id);
        ensureEditable(kind, row);
        if (body == null || body.isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID, "没有可保存的字段");
        validateAmounts(kind, body);
        validateDateOrder(kind, row, body);

        if ("receivables".equals(kind)) {
            String customerCode = body.containsKey("customerCode")
                    ? required(body, "customerCode", "客户编码") : String.valueOf(row.get("customer_code"));
            one("md_customer", "customer_code", customerCode);
            String salesOrderNo = body.containsKey("salesOrderNo") ? optional(body, "salesOrderNo")
                    : row.get("sales_order_no") == null ? null : String.valueOf(row.get("sales_order_no"));
            if (salesOrderNo != null) {
                Map<String, Object> order = one("erp_sales_order", "sales_order_no", salesOrderNo);
                if (!customerCode.equals(String.valueOf(order.get("customer_code")))) {
                    throw BizException.of(ErrorCode.PARAM_INVALID, "销售订单与客户不匹配");
                }
            }
        }
        if ("payables".equals(kind) && body.containsKey("supplierCode")) {
            one("md_supplier", "supplier_code", required(body, "supplierCode", "供应商编码"));
        }

        List<String> assignments = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        for (var field : spec.editableFields().entrySet()) {
            if (!body.containsKey(field.getKey())) continue;
            assignments.add(field.getValue() + "=?");
            parameters.add(convert(field.getValue(), body.get(field.getKey())));
        }
        if (assignments.isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID, "没有可保存的字段");
        if ("receipts".equals(kind) && body.containsKey("amount")) assignments.add("unapplied_amount=amount");
        if ("receivables".equals(kind) && body.containsKey("amount")) assignments.add("outstanding_amount=invoice_amount");
        if ("receivables".equals(kind) && body.containsKey("customerCode")) {
            assignments.add("customer_name=(SELECT customer_name FROM src_mdm.md_customer WHERE customer_code=?)");
            parameters.add(body.get("customerCode"));
        }
        parameters.add(id);
        db.update("UPDATE src_erp." + spec.table() + " SET " + String.join(",", assignments) + " WHERE id=?", parameters.toArray());
        return one(spec.table(), "id", id);
    }

    @Transactional
    public void delete(String kind, long id) {
        Spec spec = spec(kind);
        requirePermission(spec.permissionPrefix() + ":DELETE");
        if (!DELETEABLE.contains(kind)) throw BizException.of(ErrorCode.PARAM_INVALID, "当前单据不支持删除");
        Map<String, Object> row = one(spec.table(), "id", id);
        ensureEditable(kind, row);
        switch (kind) {
            case "mrp" -> {
                if (count("SELECT COUNT(*) FROM src_erp.erp_mrp_requirement WHERE run_id=?", id) > 0
                        || count("SELECT COUNT(*) FROM src_erp.erp_plan_suggestion WHERE run_id=?", id) > 0) locked();
            }
            case "suggestions" -> {
                if (row.get("converted_order_no") != null) locked();
            }
            case "invoices" -> {
                if (count("SELECT COUNT(*) FROM src_erp.erp_receivable WHERE invoice_no=?", row.get("invoice_no")) > 0) locked();
            }
            case "receivables" -> {
                if (!"MANUAL".equals(row.get("source_type"))
                        || count("SELECT COUNT(*) FROM src_erp.erp_settlement WHERE receivable_id=?", id) > 0) locked();
            }
            case "receipts" -> {
                if (count("SELECT COUNT(*) FROM src_erp.erp_settlement WHERE receipt_id=?", id) > 0) locked();
            }
            case "payables" -> {
                if (decimal(row.get("paid_amount")).signum() != 0) locked();
            }
            default -> locked();
        }
        db.update("DELETE FROM src_erp." + spec.table() + " WHERE id=?", id);
    }

    private Map<String, Object> issueInvoiceInternal(long id) {
        Map<String, Object> invoice = one("erp_invoice", "id", id);
        if (!"DRAFT".equals(invoice.get("status"))) locked();
        Map<String, Object> order = one("erp_sales_order", "sales_order_no", invoice.get("sales_order_no"));
        BigDecimal amount = decimal(invoice.get("amount"));
        if (amount.signum() <= 0) throw BizException.of(ErrorCode.PARAM_INVALID, "开票金额必须大于0");
        String invoiceNo = String.valueOf(invoice.get("invoice_no"));
        String salesOrderNo = String.valueOf(invoice.get("sales_order_no"));
        String voucherNo = createVoucher("SALES", invoiceNo, salesOrderNo, amount, "1122", "销售开票确认应收");
        Date invoiceDate = asDate(invoice.get("invoice_date"));
        Date dueDate = asDate(invoice.get("due_date"));
        if (invoiceDate == null) invoiceDate = Date.valueOf(LocalDate.now());
        if (dueDate == null) dueDate = Date.valueOf(invoiceDate.toLocalDate().plusDays(30));
        String receivableNo = "AR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);
        db.update("INSERT INTO src_erp.erp_receivable(receivable_no,customer_code,customer_name,sales_order_no,invoice_no,invoice_date,due_date,invoice_amount,received_amount,outstanding_amount,settle_status,created_by,settled_amount,status,payment_terms,customer_reference,remark) VALUES(?,?,?,?,?,?,?,?,0,?,'OPEN',?,0,'OPEN',?,?,?)",
                receivableNo, order.get("customer_code"), order.get("customer_name"), salesOrderNo, invoiceNo,
                invoiceDate, dueDate, amount, amount, CurrentUser.usernameOrSystem(), invoice.get("payment_terms"),
                invoice.get("customer_reference"), invoice.get("remark"));
        db.update("UPDATE src_erp.erp_invoice SET status='ISSUED',voucher_no=?,issued_at=NOW(3) WHERE id=?", voucherNo, id);
        return one("erp_invoice", "id", id);
    }

    private Map<String, Object> postReceiptInternal(long id) {
        Map<String, Object> receipt = one("erp_receipt", "id", id);
        if (!"DRAFT".equals(receipt.get("status"))) locked();
        BigDecimal amount = decimal(receipt.get("amount"));
        if (amount.signum() <= 0) throw BizException.of(ErrorCode.PARAM_INVALID, "回款金额必须大于0");
        String receiptNo = String.valueOf(receipt.get("receipt_no"));
        String voucherNo = createVoucher("RECEIPT", receiptNo, null, amount, "1002", "客户回款");
        db.update("UPDATE src_erp.erp_receipt SET status='UNAPPLIED',voucher_no=?,posted_at=NOW(3) WHERE id=?", voucherNo, id);
        return one("erp_receipt", "id", id);
    }

    private String createVoucher(String sourceType, String sourceNo, String salesOrderNo,
                                 BigDecimal amount, String subject, String summary) {
        String voucherNo = "V-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);
        String period = LocalDate.now().toString().substring(0, 7);
        String user = CurrentUser.usernameOrSystem();
        db.update("INSERT INTO src_erp.erp_fin_voucher(voucher_no,company_code,fiscal_period,voucher_date,debit_amount,credit_amount,subject_code,source_type,source_no,sales_order_no,summary,created_by,voucher_status,posted_at,posted_by) VALUES(?,'C001',?,CURRENT_DATE,?,?,?,?,?,?,?,?, 'POSTED',NOW(3),?)",
                voucherNo, period, amount, amount, subject, sourceType, sourceNo, salesOrderNo, summary, user, user);
        return voucherNo;
    }

    private void ensureEditable(String kind, Map<String, Object> row) {
        String status = String.valueOf(row.getOrDefault("status", ""));
        boolean editable = switch (kind) {
            case "mrp" -> "CREATED".equals(status);
            case "suggestions" -> "PROPOSED".equals(status) && row.get("converted_order_no") == null;
            case "invoices", "receipts" -> "DRAFT".equals(status);
            case "receivables" -> "MANUAL".equals(row.get("source_type")) && "OPEN".equals(status)
                    && decimal(row.get("received_amount")).signum() == 0;
            case "payables" -> "OPEN".equals(status) && decimal(row.get("paid_amount")).signum() == 0;
            default -> false;
        };
        if (!editable) locked();
    }

    private void validateAmounts(String kind, Map<String, Object> body) {
        if (body.containsKey("amount")) positive(body, "amount", "金额");
        if (body.containsKey("taxAmount")) nonNegative(body, "taxAmount", "税额");
        if ("suggestions".equals(kind) && body.containsKey("quantity")) positive(body, "quantity", "建议数量");
    }

    private void validateDateOrder(String kind, Map<String, Object> row, Map<String, Object> body) {
        if (!Set.of("invoices", "receivables").contains(kind)
                || (!body.containsKey("invoiceDate") && !body.containsKey("dueDate"))) return;
        Date invoiceDate = body.containsKey("invoiceDate") ? date(body, "invoiceDate") : asDate(row.get("invoice_date"));
        Date dueDate = body.containsKey("dueDate") ? date(body, "dueDate") : asDate(row.get("due_date"));
        if (invoiceDate != null && dueDate != null && dueDate.before(invoiceDate)) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "到期日不能早于业务日期");
        }
    }

    private Object convert(String column, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) return null;
        if (column.endsWith("_date")) return dateValue(value);
        if (Set.of("amount", "tax_amount", "quantity", "invoice_amount").contains(column)) return decimal(value);
        if (column.equals("priority_level")) return String.valueOf(value).toUpperCase();
        return value;
    }

    private Map<String, Object> one(String table, String column, Object value) {
        String catalogTable = switch (table) {
            case "md_customer", "md_supplier" -> "src_mdm." + table;
            default -> "src_erp." + table;
        };
        var filter = scope.filter(catalogTable, "");
        List<Object> parameters = new ArrayList<>();
        parameters.add(value);
        parameters.addAll(filter.parameters());
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT * FROM " + catalogTable + " WHERE " + column + "=? AND (" + filter.sql() + ")",
                parameters.toArray());
        if (rows.isEmpty()) throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "单据不存在或超出可访问范围");
        return new LinkedHashMap<>(rows.get(0));
    }

    private Spec spec(String kind) {
        Spec value = SPECS.get(kind);
        if (value == null) throw BizException.of(ErrorCode.PARAM_INVALID, "不支持的 ERP 单据类型");
        return value;
    }

    private void requirePermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()))) {
            throw new AccessDeniedException("暂无该单据操作权限");
        }
    }

    private String required(Map<String, Object> body, String key, String label) {
        String value = optional(body, key);
        if (value == null) throw BizException.of(ErrorCode.PARAM_INVALID, label + "不能为空");
        return value;
    }

    private String optional(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) return null;
        String value = String.valueOf(body.get(key)).trim();
        return value.isEmpty() ? null : value;
    }

    private String value(Map<String, Object> body, String key, String fallback) {
        String value = optional(body, key);
        return value == null ? fallback : value;
    }

    private Date date(Map<String, Object> body, String key) {
        String value = optional(body, key);
        if (value == null) return null;
        try {
            return Date.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw BizException.of(ErrorCode.PARAM_INVALID, key + "日期格式应为 yyyy-MM-dd");
        }
    }

    private Date dateValue(Object value) {
        try {
            return Date.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException error) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "日期格式应为 yyyy-MM-dd");
        }
    }

    private BigDecimal positive(Map<String, Object> body, String key, String label) {
        BigDecimal value = decimal(body == null ? null : body.get(key));
        if (value.signum() <= 0) throw BizException.of(ErrorCode.PARAM_INVALID, label + "必须大于0");
        return value;
    }

    private BigDecimal nonNegative(Map<String, Object> body, String key, String label) {
        BigDecimal value = decimal(body == null ? null : body.get(key));
        if (value.signum() < 0) throw BizException.of(ErrorCode.PARAM_INVALID, label + "不能小于0");
        return value;
    }

    private BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "金额或数量格式错误");
        }
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Date asDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return date;
        return dateValue(value);
    }

    private boolean flag(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private int count(String sql, Object... parameters) {
        Integer value = db.queryForObject(sql, Integer.class, parameters);
        return value == null ? 0 : value;
    }

    private void locked() {
        throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,
                "单据已执行、过账或关联下游业务，不能编辑或删除");
    }
}
