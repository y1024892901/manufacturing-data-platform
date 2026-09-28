-- EAM 台账、故障、维修和点检单的更新/删除权限。
INSERT IGNORE INTO mfg_auth.sys_permission (perm_code, perm_name, system_code, perm_group, perm_type) VALUES
('EAM:EQUIPMENT:DELETE', '删除未关联设备台账', 'eam', '资产台账', 'BUTTON'),
('EAM:FAULT:UPDATE', '修改待处理故障单', 'eam', '维修管理', 'BUTTON'),
('EAM:FAULT:DELETE', '删除未进入维修的故障单', 'eam', '维修管理', 'BUTTON'),
('EAM:INSPECTION:UPDATE', '修改未执行点检单', 'eam', '点检保养', 'BUTTON'),
('EAM:INSPECTION:DELETE', '删除未执行点检单', 'eam', '点检保养', 'BUTTON'),
('EAM:REPAIR:UPDATE', '修改进行中维修单', 'eam', '维修管理', 'BUTTON'),
('EAM:REPAIR:DELETE', '删除进行中维修单', 'eam', '维修管理', 'BUTTON');

-- 管理员与设备主管负责完整维护；维修人员可维护本职业务单据，但不能删除设备档案。
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p ON p.perm_code IN (
  'EAM:EQUIPMENT:VIEW', 'EAM:EQUIPMENT:CREATE', 'EAM:EQUIPMENT:UPDATE', 'EAM:EQUIPMENT:DELETE',
  'EAM:FAULT:CREATE', 'EAM:FAULT:UPDATE', 'EAM:FAULT:DELETE', 'EAM:FAULT:CLOSE',
  'EAM:INSPECTION:PLAN', 'EAM:INSPECTION:CREATE', 'EAM:INSPECTION:UPDATE', 'EAM:INSPECTION:DELETE',
  'EAM:REPAIR:CREATE', 'EAM:REPAIR:UPDATE', 'EAM:REPAIR:DELETE', 'EAM:REPAIR:FINISH'
)
WHERE r.role_code IN ('ADMIN', 'EQUIPMENT_SUPERVISOR');

INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p ON p.perm_code IN (
  'EAM:EQUIPMENT:VIEW', 'EAM:EQUIPMENT:UPDATE', 'EAM:FAULT:CREATE', 'EAM:FAULT:UPDATE', 'EAM:FAULT:DELETE', 'EAM:FAULT:CLOSE',
  'EAM:INSPECTION:PLAN', 'EAM:INSPECTION:CREATE', 'EAM:INSPECTION:UPDATE', 'EAM:INSPECTION:DELETE',
  'EAM:REPAIR:CREATE', 'EAM:REPAIR:UPDATE', 'EAM:REPAIR:DELETE', 'EAM:REPAIR:FINISH'
)
WHERE r.role_code = 'EQUIPMENT_REPAIRMAN';
