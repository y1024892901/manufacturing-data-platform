-- ERP 单据表单补充的可选业务字段及过账状态。历史凭证按已过账处理，避免重新开放修改。
ALTER TABLE src_erp.erp_sales_order
  ADD COLUMN customer_reference VARCHAR(64) NULL,
  ADD COLUMN payment_terms VARCHAR(128) NULL,
  ADD COLUMN shipping_terms VARCHAR(128) NULL;

ALTER TABLE src_erp.erp_sales_order_line
  ADD COLUMN customer_material_code VARCHAR(64) NULL,
  ADD COLUMN line_remark VARCHAR(255) NULL;

ALTER TABLE src_erp.erp_mrp_run
  ADD COLUMN planner VARCHAR(64) NULL,
  ADD COLUMN remark VARCHAR(500) NULL;

ALTER TABLE src_erp.erp_plan_suggestion
  ADD COLUMN priority_level VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  ADD COLUMN planner_note VARCHAR(500) NULL;

ALTER TABLE src_erp.erp_prod_order
  ADD COLUMN planner_remark VARCHAR(500) NULL;

ALTER TABLE src_erp.erp_invoice
  ADD COLUMN customer_reference VARCHAR(64) NULL,
  ADD COLUMN payment_terms VARCHAR(128) NULL,
  ADD COLUMN due_date DATE NULL,
  ADD COLUMN external_reference VARCHAR(100) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN issued_at DATETIME(3) NULL;

ALTER TABLE src_erp.erp_receivable
  ADD COLUMN payment_terms VARCHAR(128) NULL,
  ADD COLUMN customer_reference VARCHAR(64) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'INVOICE';

ALTER TABLE src_erp.erp_receipt
  ADD COLUMN payment_method VARCHAR(32) NULL,
  ADD COLUMN deposit_account VARCHAR(64) NULL,
  ADD COLUMN remittance_reference VARCHAR(100) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN posted_at DATETIME(3) NULL;

ALTER TABLE src_erp.erp_payable
  ADD COLUMN supplier_invoice_no VARCHAR(64) NULL,
  ADD COLUMN invoice_date DATE NULL,
  ADD COLUMN invoice_received_date DATE NULL,
  ADD COLUMN payment_terms VARCHAR(128) NULL,
  ADD COLUMN payment_method VARCHAR(32) NULL,
  ADD COLUMN cc_code VARCHAR(32) NULL,
  ADD COLUMN remark VARCHAR(500) NULL;

ALTER TABLE src_erp.erp_fin_voucher
  ADD COLUMN voucher_status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
  ADD COLUMN posted_at DATETIME(3) NULL,
  ADD COLUMN posted_by VARCHAR(32) NULL,
  ADD COLUMN attachment_url VARCHAR(500) NULL;

UPDATE src_erp.erp_fin_voucher
SET posted_at = COALESCE(posted_at, created_at),
    posted_by = COALESCE(posted_by, created_by);

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('ERP:SALES_ORDER:UPDATE','修改销售订单','erp','销售订单','BUTTON'),
('ERP:SALES_ORDER:DELETE','删除销售订单','erp','销售订单','BUTTON'),
('ERP:PROD_ORDER:UPDATE','修改生产订单','erp','生产订单','BUTTON'),
('ERP:PROD_ORDER:DELETE','删除生产订单','erp','生产订单','BUTTON'),
('ERP:MRP:UPDATE','修改计划运行','erp','物料需求计划','BUTTON'),
('ERP:MRP:DELETE','删除计划运行','erp','物料需求计划','BUTTON'),
('ERP:PLAN_SUGGESTION:UPDATE','修改计划建议','erp','计划建议','BUTTON'),
('ERP:PLAN_SUGGESTION:DELETE','删除计划建议','erp','计划建议','BUTTON'),
('ERP:INVOICE:UPDATE','修改销售发票','erp','应收管理','BUTTON'),
('ERP:INVOICE:DELETE','删除销售发票','erp','应收管理','BUTTON'),
('ERP:INVOICE:ISSUE','确认开具发票','erp','应收管理','BUTTON'),
('ERP:RECEIVABLE:CREATE','新增应收单','erp','应收管理','BUTTON'),
('ERP:RECEIVABLE:UPDATE','修改应收单','erp','应收管理','BUTTON'),
('ERP:RECEIVABLE:DELETE','删除应收单','erp','应收管理','BUTTON'),
('ERP:RECEIPT:UPDATE','修改回款单','erp','应收管理','BUTTON'),
('ERP:RECEIPT:DELETE','删除回款单','erp','应收管理','BUTTON'),
('ERP:RECEIPT:POST','登记回款','erp','应收管理','BUTTON'),
('ERP:PAYABLE:UPDATE','修改应付单','erp','应付管理','BUTTON'),
('ERP:PAYABLE:DELETE','删除应付单','erp','应付管理','BUTTON'),
('ERP:VOUCHER:UPDATE','修改财务凭证','erp','总账管理','BUTTON'),
('ERP:VOUCHER:DELETE','删除财务凭证','erp','总账管理','BUTTON'),
('ERP:VOUCHER:POST','过账财务凭证','erp','总账管理','BUTTON');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('SALES_REP','SALES_ASSISTANT','SALES_SUPERVISOR','SALES_DIRECTOR')
  AND p.perm_code IN ('ERP:SALES_ORDER:UPDATE','ERP:SALES_ORDER:DELETE');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PROD_PLANNER','PROD_SUPERVISOR','PROD_MANAGER','PROD_CLERK')
  AND p.perm_code IN ('ERP:PROD_ORDER:UPDATE','ERP:PROD_ORDER:DELETE','ERP:MRP:UPDATE','ERP:MRP:DELETE',
                      'ERP:PLAN_SUGGESTION:UPDATE','ERP:PLAN_SUGGESTION:DELETE');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('AR_ACCOUNTANT','FINANCE_DIRECTOR')
  AND p.perm_code IN ('ERP:INVOICE:UPDATE','ERP:INVOICE:DELETE','ERP:INVOICE:ISSUE',
                      'ERP:RECEIVABLE:CREATE','ERP:RECEIVABLE:UPDATE','ERP:RECEIVABLE:DELETE');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('CASHIER','AR_ACCOUNTANT','FINANCE_DIRECTOR')
  AND p.perm_code IN ('ERP:RECEIPT:UPDATE','ERP:RECEIPT:DELETE','ERP:RECEIPT:POST');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('AP_ACCOUNTANT','FINANCE_DIRECTOR')
  AND p.perm_code IN ('ERP:PAYABLE:UPDATE','ERP:PAYABLE:DELETE');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('GL_ACCOUNTANT','FIN_SUPERVISOR','FINANCE_DIRECTOR')
  AND p.perm_code IN ('ERP:VOUCHER:UPDATE','ERP:VOUCHER:DELETE','ERP:VOUCHER:POST');
