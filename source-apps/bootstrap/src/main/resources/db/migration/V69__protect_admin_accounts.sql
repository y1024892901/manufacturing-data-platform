-- ADMIN 是系统最后的恢复入口：修复历史误锁定，并由服务层阻止后续锁定。
UPDATE mfg_auth.sys_user u
JOIN mfg_auth.sys_user_role ur ON ur.user_id = u.id
JOIN mfg_auth.sys_role r ON r.id = ur.role_id AND r.role_code = 'ADMIN'
SET u.is_locked = 0, u.failed_login_count = 0
WHERE u.is_deleted = 0;
