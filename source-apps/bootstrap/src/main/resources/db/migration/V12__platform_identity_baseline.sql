ALTER TABLE sys_user
    ADD COLUMN is_locked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '账号是否锁定' AFTER is_enabled,
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数' AFTER is_locked,
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志' AFTER is_demo_account,
    ADD COLUMN deleted_at DATETIME(3) NULL AFTER is_deleted,
    ADD COLUMN password_changed_at DATETIME(3) NULL AFTER last_login_at,
    ADD COLUMN data_scope_type VARCHAR(16) NOT NULL DEFAULT 'ROLE' COMMENT 'ROLE/ALL/DEPT/SELF/NONE' AFTER dept_code,
    ADD COLUMN data_scope_value VARCHAR(500) NULL COMMENT '自定义数据范围值' AFTER data_scope_type;
