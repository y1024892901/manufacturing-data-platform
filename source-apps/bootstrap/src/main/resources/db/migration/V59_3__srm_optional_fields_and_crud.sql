-- SRM optional supplier, sourcing, delivery, and quality collaboration fields.
ALTER TABLE src_srm.srm_onboarding
  ADD COLUMN contact_email VARCHAR(120) NULL,
  ADD COLUMN credit_code VARCHAR(32) NULL,
  ADD COLUMN registered_address VARCHAR(255) NULL,
  ADD COLUMN supplier_type VARCHAR(32) NULL,
  ADD COLUMN payment_terms VARCHAR(100) NULL,
  ADD COLUMN currency_code VARCHAR(3) NULL,
  ADD COLUMN website VARCHAR(255) NULL,
  ADD COLUMN qualification_note VARCHAR(500) NULL,
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_srm.srm_qualification
  ADD COLUMN issue_date DATE NULL,
  ADD COLUMN issued_by VARCHAR(120) NULL,
  ADD COLUMN note VARCHAR(500) NULL,
  ADD COLUMN created_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);
UPDATE src_srm.srm_qualification SET audit_result='PENDING' WHERE audit_result IS NULL OR TRIM(audit_result)='';

ALTER TABLE src_srm.srm_rfq
  ADD COLUMN currency_code VARCHAR(3) NULL,
  ADD COLUMN delivery_terms VARCHAR(200) NULL,
  ADD COLUMN ship_to_address VARCHAR(255) NULL,
  ADD COLUMN invited_supplier_count INT NULL,
  ADD COLUMN purchasing_group VARCHAR(64) NULL,
  ADD COLUMN technical_requirement VARCHAR(1000) NULL,
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_srm.srm_supplier_quote
  ADD COLUMN currency_code VARCHAR(3) NULL,
  ADD COLUMN tax_rate DECIMAL(6,3) NULL,
  ADD COLUMN minimum_order_qty DECIMAL(18,4) NULL,
  ADD COLUMN payment_term_days INT NULL,
  ADD COLUMN freight_amount DECIMAL(18,2) NULL,
  ADD COLUMN valid_until DATE NULL,
  ADD COLUMN warranty_months INT NULL,
  ADD COLUMN attachment_name VARCHAR(255) NULL,
  ADD COLUMN note VARCHAR(500) NULL,
  ADD COLUMN created_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_srm.srm_purchase_order
  ADD COLUMN currency_code VARCHAR(3) NULL,
  ADD COLUMN payment_terms VARCHAR(100) NULL,
  ADD COLUMN shipping_terms VARCHAR(200) NULL,
  ADD COLUMN ship_to_address VARCHAR(255) NULL,
  ADD COLUMN buyer_name VARCHAR(50) NULL,
  ADD COLUMN supplier_reference_no VARCHAR(64) NULL,
  ADD COLUMN supplier_note VARCHAR(500) NULL;

ALTER TABLE src_srm.srm_asn
  ADD COLUMN carrier_name VARCHAR(120) NULL,
  ADD COLUMN tracking_no VARCHAR(64) NULL,
  ADD COLUMN packing_slip_no VARCHAR(64) NULL,
  ADD COLUMN contact_name VARCHAR(50) NULL,
  ADD COLUMN note VARCHAR(500) NULL,
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_srm.srm_supplier_quality
  ADD COLUMN root_cause VARCHAR(1000) NULL,
  ADD COLUMN containment_action VARCHAR(1000) NULL,
  ADD COLUMN corrective_action VARCHAR(1000) NULL,
  ADD COLUMN preventive_action VARCHAR(1000) NULL,
  ADD COLUMN owner_user VARCHAR(50) NULL,
  ADD COLUMN close_note VARCHAR(1000) NULL,
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_srm.srm_delivery
  ADD COLUMN carrier_name VARCHAR(120) NULL,
  ADD COLUMN tracking_no VARCHAR(64) NULL,
  ADD COLUMN packing_slip_no VARCHAR(64) NULL,
  ADD COLUMN note VARCHAR(500) NULL;

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type)
VALUES ('SRM:RECORD:DELETE','删除供应商协同记录','srm','采购协同','BUTTON');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p ON p.perm_code='SRM:RECORD:DELETE'
WHERE r.role_code IN ('BUYER','SQE','PURCHASE_SUPERVISOR','PURCHASE_DIRECTOR');

-- The general update authority already exists; regrant it to the SRM operating roles.
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p ON p.perm_code='SRM:RECORD:UPDATE'
WHERE r.role_code IN ('BUYER','SQE','PURCHASE_SUPERVISOR','PURCHASE_DIRECTOR');
