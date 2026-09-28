-- EMS meters, editable usage ledgers, and actionable energy alerts.
ALTER TABLE src_energy.energy_equipment_usage
    ADD COLUMN meter_code VARCHAR(32) NULL,
    ADD COLUMN shift_code VARCHAR(32) NULL,
    ADD COLUMN production_order_no VARCHAR(32) NULL,
    ADD COLUMN warning_threshold_percent DECIMAL(8,4) NULL,
    ADD COLUMN remark VARCHAR(1000) NULL,
    ADD COLUMN updated_at DATETIME(3) NULL;

ALTER TABLE src_energy.energy_workshop_usage
    ADD COLUMN shift_code VARCHAR(32) NULL,
    ADD COLUMN cost_center_code VARCHAR(32) NULL,
    ADD COLUMN baseline_value DECIMAL(18,4) NULL,
    ADD COLUMN deviation_rate DECIMAL(8,4) NULL,
    ADD COLUMN warning_threshold_percent DECIMAL(8,4) NULL,
    ADD COLUMN is_abnormal TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN remark VARCHAR(1000) NULL,
    ADD COLUMN updated_at DATETIME(3) NULL;

CREATE TABLE src_energy.energy_meter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    meter_code VARCHAR(32) NOT NULL,
    meter_name VARCHAR(120) NOT NULL,
    energy_type VARCHAR(16) NOT NULL,
    unit_code VARCHAR(16) NOT NULL,
    meter_kind VARCHAR(16) NOT NULL DEFAULT 'SUBMETER',
    workshop_code VARCHAR(32) NULL,
    equipment_code VARCHAR(32) NULL,
    parent_meter_code VARCHAR(32) NULL,
    install_location VARCHAR(200) NULL,
    multiplier DECIMAL(12,4) NULL,
    read_interval_minutes INT NULL,
    calibration_date DATE NULL,
    warning_threshold_percent DECIMAL(8,4) NULL,
    data_source VARCHAR(80) NULL,
    responsible_person VARCHAR(80) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_meter_code (meter_code),
    KEY idx_energy_meter_type_status (energy_type, status),
    KEY idx_energy_meter_workshop (workshop_code),
    KEY idx_energy_meter_equipment (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='能源仪表与采集点台账';

CREATE TABLE src_energy.energy_alert (
    id BIGINT NOT NULL AUTO_INCREMENT,
    alert_no VARCHAR(32) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_id BIGINT NULL,
    object_code VARCHAR(32) NOT NULL,
    energy_type VARCHAR(16) NOT NULL,
    stat_date DATE NOT NULL,
    actual_value DECIMAL(18,4) NOT NULL,
    threshold_value DECIMAL(18,4) NOT NULL,
    deviation_rate DECIMAL(8,4) NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(80) NULL,
    due_date DATE NULL,
    cause VARCHAR(1000) NULL,
    corrective_action VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_alert_no (alert_no),
    KEY idx_energy_alert_status_date (status, stat_date),
    KEY idx_energy_alert_source (source_type, source_id),
    KEY idx_energy_alert_object (object_code, energy_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='能源偏差告警及处置记录';

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('ENERGY:USAGE:UPDATE','修改设备能耗','energy','能源台账','BUTTON'),
('ENERGY:USAGE:DELETE','删除设备能耗','energy','能源台账','BUTTON'),
('ENERGY:WORKSHOP_USAGE:UPDATE','修改车间能耗','energy','能源台账','BUTTON'),
('ENERGY:WORKSHOP_USAGE:DELETE','删除车间能耗','energy','能源台账','BUTTON'),
('ENERGY:METER:VIEW','查看能源仪表','energy','能源计量','BUTTON'),
('ENERGY:METER:CREATE','新增能源仪表','energy','能源计量','BUTTON'),
('ENERGY:METER:UPDATE','修改能源仪表','energy','能源计量','BUTTON'),
('ENERGY:METER:DELETE','删除能源仪表','energy','能源计量','BUTTON'),
('ENERGY:ALERT:VIEW','查看能耗告警','energy','异常处置','BUTTON'),
('ENERGY:ALERT:CREATE','新建能耗告警','energy','异常处置','BUTTON'),
('ENERGY:ALERT:UPDATE','修改能耗告警','energy','异常处置','BUTTON'),
('ENERGY:ALERT:DELETE','删除能耗告警','energy','异常处置','BUTTON');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('ADMIN','ENERGY_ADMIN') AND p.perm_code LIKE 'ENERGY:%';

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'DATA_ANALYST' AND p.perm_code LIKE 'ENERGY:%:VIEW';

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'EQUIPMENT_SUPERVISOR'
  AND p.perm_code IN ('ENERGY:USAGE:CREATE','ENERGY:USAGE:UPDATE');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'WORKSHOP_CHIEF'
  AND p.perm_code IN ('ENERGY:WORKSHOP_USAGE:UPDATE');
