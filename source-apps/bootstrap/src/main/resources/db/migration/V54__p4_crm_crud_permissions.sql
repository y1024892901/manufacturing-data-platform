INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('CRM:LEAD:UPDATE','修改线索','crm','线索管理','BUTTON'),('CRM:LEAD:DELETE','删除线索','crm','线索管理','BUTTON'),
('CRM:OPPORTUNITY:UPDATE','修改商机','crm','商机管理','BUTTON'),('CRM:OPPORTUNITY:DELETE','删除商机','crm','商机管理','BUTTON'),
('CRM:QUOTATION:UPDATE','修改报价','crm','报价管理','BUTTON'),('CRM:QUOTATION:DELETE','删除报价','crm','报价管理','BUTTON'),
('CRM:CONTRACT:UPDATE','修改合同','crm','合同管理','BUTTON'),('CRM:CONTRACT:DELETE','删除合同','crm','合同管理','BUTTON'),
('CRM:COMPLAINT:UPDATE','修改客诉','crm','客诉协同','BUTTON'),('CRM:COMPLAINT:DELETE','删除客诉','crm','客诉协同','BUTTON');

-- 销售员仅可维护本人创建或负责的记录；服务层同时校验数据归属。
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('SALES_REP','SALES_ASSISTANT')
  AND p.perm_code IN ('CRM:LEAD:UPDATE','CRM:LEAD:DELETE','CRM:OPPORTUNITY:UPDATE','CRM:OPPORTUNITY:DELETE',
                      'CRM:QUOTATION:UPDATE','CRM:QUOTATION:DELETE','CRM:CONTRACT:UPDATE','CRM:CONTRACT:DELETE',
                      'CRM:COMPLAINT:UPDATE','CRM:COMPLAINT:DELETE');

-- 销售主管和总监可维护本系统业务记录。
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code='SALES_SUPERVISOR'
  AND p.perm_code IN ('CRM:LEAD:UPDATE','CRM:LEAD:DELETE','CRM:OPPORTUNITY:UPDATE','CRM:OPPORTUNITY:DELETE',
                      'CRM:QUOTATION:UPDATE','CRM:QUOTATION:DELETE','CRM:CONTRACT:UPDATE','CRM:CONTRACT:DELETE',
                      'CRM:COMPLAINT:UPDATE','CRM:COMPLAINT:DELETE');
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code='SALES_DIRECTOR' AND p.perm_code LIKE 'CRM:%';
