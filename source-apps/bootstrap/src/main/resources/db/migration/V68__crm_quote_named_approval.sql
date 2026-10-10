-- 既有报价风险流和合同流引用了不存在的 FINANCE_SUPERVISOR 角色。
UPDATE mfg_auth.wf_node SET approver_role='FIN_SUPERVISOR'
WHERE approver_role='FINANCE_SUPERVISOR';
UPDATE mfg_auth.wf_task SET approver_role='FIN_SUPERVISOR'
WHERE approver_role='FINANCE_SUPERVISOR' AND task_status IN ('PENDING','WAITING');

-- 销售作业岗可提交报价；报价审批岗可在 CRM 查看单据及进度。
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE (r.role_code IN ('SALES_REP','SALES_ASSISTANT','SALES_SUPERVISOR','SALES_DIRECTOR')
       AND p.perm_code='CRM:QUOTATION:SUBMIT')
   OR (r.role_code IN ('SALES_SUPERVISOR','SALES_DIRECTOR','FIN_SUPERVISOR','FINANCE_DIRECTOR')
       AND p.perm_code='CRM:QUOTATION:VIEW');

INSERT IGNORE INTO mfg_auth.sys_user_system(user_id,system_code)
SELECT DISTINCT ur.user_id,'crm' FROM mfg_auth.sys_user_role ur
JOIN mfg_auth.sys_role r ON r.id=ur.role_id
WHERE r.role_code IN ('FIN_SUPERVISOR','FINANCE_DIRECTOR');
