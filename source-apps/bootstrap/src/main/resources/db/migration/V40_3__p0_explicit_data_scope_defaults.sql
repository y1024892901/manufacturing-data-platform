-- 计划 01 #10：给已有资源查看角色显式补 ALL；原有 SELF/DEPT 等窄规则保持不变。
-- 新角色/新资源若未登记规则则默认 NONE，避免规则漏配时隐式全开。
INSERT INTO mfg_auth.sys_data_scope(role_id,resource_code,scope_type,scope_field)
SELECT DISTINCT rp.role_id, m.resource_code, 'ALL', NULL
FROM mfg_auth.sys_role_permission rp
JOIN mfg_auth.sys_permission p ON p.id=rp.permission_id
JOIN (
 SELECT 'CUSTOMER' resource_code,'MDM:CUSTOMER:VIEW' perm_code UNION ALL
 SELECT 'CUSTOMER','CRM:CUSTOMER:VIEW' UNION ALL
 SELECT 'SUPPLIER','MDM:SUPPLIER:VIEW' UNION ALL
 SELECT 'OPPORTUNITY','CRM:OPPORTUNITY:VIEW' UNION ALL
 SELECT 'SALES_ORDER','ERP:SALES_ORDER:VIEW' UNION ALL
 SELECT 'WORK_ORDER','MES:WORK_ORDER:VIEW' UNION ALL
 SELECT 'EQUIPMENT','EAM:EQUIPMENT:VIEW' UNION ALL
 SELECT 'INSPECTION','QMS:INSPECTION:VIEW' UNION ALL
 SELECT 'PURCHASE_ORDER','SRM:PURCHASE:VIEW'
) m ON m.perm_code=p.perm_code
WHERE NOT EXISTS(SELECT 1 FROM mfg_auth.sys_data_scope s WHERE s.role_id=rp.role_id AND s.resource_code=m.resource_code);
