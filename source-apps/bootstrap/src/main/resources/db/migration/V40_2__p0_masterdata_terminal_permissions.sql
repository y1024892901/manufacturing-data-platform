-- 计划 00 F3：停用权限与同批授权。
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('MDM:MATERIAL:DISABLE','停用物料','mdm','工程主数据','BUTTON'),
('MDM:PRODUCT:DISABLE','停用产品','mdm','工程主数据','BUTTON'),
('MDM:BOM:DISABLE','停用BOM','mdm','工程主数据','BUTTON'),
('MDM:ROUTING:DISABLE','停用工艺路线','mdm','工程主数据','BUTTON');
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('ADMIN','MDM_ADMIN','PRODUCT_ENGINEER','PROCESS_ENGINEER','PROCESS_SUPERVISOR','RND_MANAGER')
AND p.perm_code IN ('MDM:MATERIAL:DISABLE','MDM:PRODUCT:DISABLE','MDM:BOM:DISABLE','MDM:ROUTING:DISABLE');
