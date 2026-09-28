-- MES CRUD、派工扩展字段、报工审计字段与 Andon 异常单。
ALTER TABLE src_mes.mes_work_order
    ADD COLUMN priority_level VARCHAR(16) NULL COMMENT '派工优先级',
    ADD COLUMN batch_no VARCHAR(64) NULL COMMENT '生产批次',
    ADD COLUMN shift_code VARCHAR(16) NULL COMMENT '计划班次',
    ADD COLUMN remark VARCHAR(500) NULL COMMENT '工单补充说明';

ALTER TABLE src_mes.mes_work_report
    ADD COLUMN updated_by VARCHAR(32) NULL COMMENT '最后修改人',
    ADD COLUMN updated_at DATETIME(3) NULL COMMENT '最后修改时间',
    ADD COLUMN remark VARCHAR(500) NULL COMMENT '报工补充说明';

CREATE TABLE IF NOT EXISTS src_mes.mes_andon_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_no VARCHAR(32) NOT NULL COMMENT 'Andon异常单号',
    event_type VARCHAR(20) NOT NULL COMMENT '异常类型',
    severity VARCHAR(16) NOT NULL COMMENT '异常等级',
    work_order_no VARCHAR(32) NULL COMMENT '关联MES工单',
    prod_order_no VARCHAR(32) NULL COMMENT '关联ERP生产订单',
    equipment_code VARCHAR(32) NULL COMMENT '关联设备',
    workshop_code VARCHAR(32) NULL COMMENT '所属车间',
    description VARCHAR(1000) NOT NULL COMMENT '异常描述',
    affected_qty DECIMAL(18,4) NULL COMMENT '影响数量',
    downtime_minutes INT NULL COMMENT '停线时长（分钟）',
    response_due_at DATETIME(3) NULL COMMENT '期望响应时间',
    reported_by VARCHAR(32) NOT NULL COMMENT '提报人',
    reported_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提报时间',
    response_by VARCHAR(32) NULL COMMENT '响应人',
    response_at DATETIME(3) NULL COMMENT '响应时间',
    response_note VARCHAR(500) NULL COMMENT '响应说明',
    resolution VARCHAR(1000) NULL COMMENT '处理方案与结果',
    resolved_by VARCHAR(32) NULL COMMENT '关闭人',
    resolved_at DATETIME(3) NULL COMMENT '关闭时间',
    event_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESPONDED/RESOLVED/CLOSED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_andon_event_no (event_no),
    KEY idx_andon_status_time (event_status, reported_at),
    KEY idx_andon_workshop (workshop_code),
    KEY idx_andon_equipment (equipment_code),
    KEY idx_andon_work_order (work_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车间Andon异常与响应闭环';

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('MES:WORK_ORDER:UPDATE','修改工单与派工','mes','工单与派工','BUTTON'),
('MES:WORK_ORDER:DELETE','删除未执行工单','mes','工单与派工','BUTTON'),
('MES:WORK_REPORT:CREATE','新增报工记录','mes','工序执行','BUTTON'),
('MES:WORK_REPORT:UPDATE','修改报工记录','mes','工序执行','BUTTON'),
('MES:WORK_REPORT:DELETE','删除报工记录','mes','工序执行','BUTTON'),
('MES:ANDON:VIEW','查看Andon异常','mes','异常与Andon','BUTTON'),
('MES:ANDON:CREATE','提报Andon异常','mes','异常与Andon','BUTTON'),
('MES:ANDON:UPDATE','修改未响应异常','mes','异常与Andon','BUTTON'),
('MES:ANDON:DELETE','删除未响应异常','mes','异常与Andon','BUTTON'),
('MES:ANDON:RESPOND','响应Andon异常','mes','异常与Andon','BUTTON'),
('MES:ANDON:RESOLVE','处理Andon异常','mes','异常与Andon','BUTTON'),
('MES:ANDON:CLOSE','关闭Andon异常','mes','异常与Andon','BUTTON');

-- 计划员可维护工单和派工；班组长可登记并修订本班报工和异常。
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code='PROD_PLANNER' AND p.perm_code IN ('MES:WORK_ORDER:UPDATE','MES:WORK_ORDER:DELETE');
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code='TEAM_LEADER' AND p.perm_code IN
 ('MES:WORK_REPORT:CREATE','MES:WORK_REPORT:UPDATE','MES:WORK_REPORT:DELETE',
  'MES:ANDON:VIEW','MES:ANDON:CREATE','MES:ANDON:RESPOND');
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('WORKSHOP_CHIEF','PROD_SUPERVISOR','PROD_MANAGER')
 AND (p.perm_code IN ('MES:WORK_ORDER:UPDATE','MES:WORK_ORDER:DELETE','MES:WORK_REPORT:CREATE','MES:WORK_REPORT:UPDATE','MES:WORK_REPORT:DELETE')
      OR p.perm_code LIKE 'MES:ANDON:%');

-- 新范围无既有规则时默认不隐式放开；为拥有查看权限的 MES 角色补明确数据范围。
INSERT IGNORE INTO mfg_auth.sys_data_scope(role_id,resource_code,scope_type,scope_field)
SELECT DISTINCT rp.role_id,m.resource_code,'ALL',NULL
FROM mfg_auth.sys_role_permission rp
JOIN mfg_auth.sys_permission p ON p.id=rp.permission_id
JOIN (
 SELECT 'WORK_REPORT' resource_code,'MES:WORK_REPORT:VIEW' perm_code UNION ALL
 SELECT 'ANDON_EVENT','MES:ANDON:VIEW'
) m ON m.perm_code=p.perm_code;
