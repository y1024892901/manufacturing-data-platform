-- CRM 客户信息录入直接维护唯一权威表 src_mdm.md_customer，并保留 CRM 的创建权限。
-- 录入服务将记录设为 PUBLISHED，并在同一事务写入 MDM Outbox，无需审批。
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type)
VALUES ('CRM:CUSTOMER:CREATE','录入客户主数据','crm','客户信息录入','BUTTON');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p ON p.perm_code='CRM:CUSTOMER:CREATE'
WHERE r.role_code IN ('ADMIN','SALES_REP','SALES_ASSISTANT','SALES_SUPERVISOR','SALES_DIRECTOR');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p ON p.perm_code='CRM:CUSTOMER:VIEW'
WHERE r.role_code IN ('ADMIN','SALES_SUPERVISOR');
