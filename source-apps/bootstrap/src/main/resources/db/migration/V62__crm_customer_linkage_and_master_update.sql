-- CRM customer maintenance updates the shared MDM record by primary key.
-- Leads and opportunities keep a foreign-key-like id plus a display-code snapshot.
ALTER TABLE src_crm.crm_lead
  ADD COLUMN customer_id BIGINT NULL AFTER lead_no,
  ADD COLUMN customer_code VARCHAR(32) NULL AFTER customer_id,
  ADD KEY idx_crm_lead_customer_id (customer_id),
  ADD KEY idx_crm_lead_customer_code (customer_code);

ALTER TABLE src_crm.crm_opportunity
  ADD COLUMN customer_id BIGINT NULL AFTER opportunity_name,
  ADD KEY idx_crm_opportunity_customer_id (customer_id);

-- Backfill only unambiguous lead matches; unresolved legacy leads remain readable
-- and can be linked to an MDM customer the next time they are edited.
UPDATE src_crm.crm_lead l
JOIN (
  SELECT customer_name, MIN(id) AS customer_id, MIN(customer_code) AS customer_code
  FROM src_mdm.md_customer
  WHERE status = 'PUBLISHED'
  GROUP BY customer_name
  HAVING COUNT(*) = 1
) c ON c.customer_name = l.customer_name
SET l.customer_id = c.customer_id,
    l.customer_code = c.customer_code
WHERE l.customer_id IS NULL;

UPDATE src_crm.crm_opportunity o
JOIN src_mdm.md_customer c ON c.customer_code = o.customer_code
SET o.customer_id = c.id
WHERE o.customer_id IS NULL;

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code, perm_name, system_code, perm_group, perm_type)
VALUES ('CRM:CUSTOMER:UPDATE', '修改客户主数据', 'crm', '客户信息录入', 'BUTTON');

-- Sales staff can pick shared MDM customers for lead/opportunity entry and
-- maintain their CRM customer master through the ID-based endpoint.
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p
  ON p.perm_code IN ('CRM:CUSTOMER:VIEW', 'CRM:CUSTOMER:UPDATE')
WHERE r.role_code IN ('ADMIN', 'SALES_REP', 'SALES_ASSISTANT', 'SALES_SUPERVISOR', 'SALES_DIRECTOR');

INSERT IGNORE INTO mfg_auth.sys_data_scope(role_id, resource_code, scope_type, scope_field)
SELECT DISTINCT rp.role_id, 'CUSTOMER', 'ALL', NULL
FROM mfg_auth.sys_role_permission rp
JOIN mfg_auth.sys_role r ON r.id = rp.role_id
JOIN mfg_auth.sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN', 'SALES_REP', 'SALES_ASSISTANT', 'SALES_SUPERVISOR', 'SALES_DIRECTOR')
  AND p.perm_code = 'CRM:CUSTOMER:VIEW'
  AND NOT EXISTS (
    SELECT 1 FROM mfg_auth.sys_data_scope s
    WHERE s.role_id = rp.role_id AND s.resource_code = 'CUSTOMER'
  );
