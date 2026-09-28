-- Optional quality context fields based on common inspection-plan, sampling,
-- result-recording, nonconformance, and corrective-action practices.
ALTER TABLE src_qms.qms_standard
  ADD COLUMN effective_date DATE NULL,
  ADD COLUMN owner_user VARCHAR(80) NULL,
  ADD COLUMN reference_doc VARCHAR(255) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_qms.qms_sampling_plan
  ADD COLUMN sampling_method VARCHAR(32) NULL,
  ADD COLUMN inspection_level VARCHAR(32) NULL,
  ADD COLUMN sample_unit VARCHAR(16) NULL,
  ADD COLUMN effective_date DATE NULL,
  ADD COLUMN reference_doc VARCHAR(255) NULL,
  ADD COLUMN owner_user VARCHAR(80) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_qms.qms_inspection
  ADD COLUMN sample_qty DECIMAL(18,4) NULL,
  ADD COLUMN inspection_basis VARCHAR(255) NULL,
  ADD COLUMN inspection_method VARCHAR(255) NULL,
  ADD COLUMN equipment_code VARCHAR(64) NULL,
  ADD COLUMN environment_temp DECIMAL(6,2) NULL,
  ADD COLUMN environment_humidity DECIMAL(6,2) NULL,
  ADD COLUMN remark VARCHAR(500) NULL;

ALTER TABLE src_qms.qms_defect
  ADD COLUMN containment_action VARCHAR(1000) NULL,
  ADD COLUMN location_desc VARCHAR(255) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_qms.qms_rework
  ADD COLUMN owner_user VARCHAR(80) NULL,
  ADD COLUMN rework_method VARCHAR(1000) NULL,
  ADD COLUMN verification_result VARCHAR(32) NULL,
  ADD COLUMN qualified_qty DECIMAL(18,4) NULL,
  ADD COLUMN scrap_qty DECIMAL(18,4) NULL,
  ADD COLUMN rework_remark VARCHAR(500) NULL,
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_qms.qms_ncr
  ADD COLUMN defect_type VARCHAR(32) NULL,
  ADD COLUMN defect_desc VARCHAR(1000) NULL,
  ADD COLUMN containment_action VARCHAR(1000) NULL,
  ADD COLUMN root_cause VARCHAR(1000) NULL,
  ADD COLUMN due_date DATE NULL,
  ADD COLUMN owner_user VARCHAR(80) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN updated_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_qms.qms_capa
  ADD COLUMN effectiveness_criteria VARCHAR(1000) NULL,
  ADD COLUMN effectiveness_result VARCHAR(32) NULL,
  ADD COLUMN effectiveness_notes VARCHAR(1000) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN closed_at DATETIME(3) NULL,
  ADD COLUMN closed_by VARCHAR(80) NULL,
  ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE src_qms.qms_eight_d
  ADD COLUMN owner_user VARCHAR(80) NULL,
  ADD COLUMN effectiveness_result VARCHAR(32) NULL,
  ADD COLUMN effectiveness_notes VARCHAR(1000) NULL,
  ADD COLUMN remark VARCHAR(500) NULL,
  ADD COLUMN verified_by VARCHAR(80) NULL,
  ADD COLUMN verified_at DATETIME(3) NULL,
  ADD COLUMN closed_by VARCHAR(80) NULL,
  ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('QMS:RECORD:UPDATE','修改质量记录','qms','质量基础','BUTTON'),
('QMS:RECORD:DELETE','删除质量记录','qms','质量基础','BUTTON'),
('QMS:RECORD:STATUS','变更质量记录状态','qms','质量基础','BUTTON'),
('QMS:INSPECTION:UPDATE','修改检验单','qms','质量检验','BUTTON'),
('QMS:INSPECTION:DELETE','删除检验单','qms','质量检验','BUTTON'),
('QMS:DEFECT:UPDATE','修改不合格品记录','qms','不合格管理','BUTTON'),
('QMS:DEFECT:DELETE','删除不合格品记录','qms','不合格管理','BUTTON'),
('QMS:DEFECT:DISPOSE','处置不合格品','qms','不合格管理','BUTTON'),
('QMS:REWORK:UPDATE','修改返工单','qms','返工报废','BUTTON'),
('QMS:REWORK:DELETE','删除返工单','qms','返工报废','BUTTON'),
('QMS:8D:CLOSE','关闭供应商8D','qms','CAPA','BUTTON');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code='ADMIN' AND p.perm_code LIKE 'QMS:%';

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('QUALITY_ENGINEER','QUALITY_SUPERVISOR')
  AND p.perm_code LIKE 'QMS:%';

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code='QC_INSPECTOR'
  AND p.perm_code IN ('QMS:INSPECTION:VIEW','QMS:INSPECTION:CREATE','QMS:INSPECTION:UPDATE',
                      'QMS:INSPECTION:JUDGE','QMS:DEFECT:VIEW','QMS:DEFECT:CREATE','QMS:DEFECT:UPDATE',
                      'QMS:REWORK:VIEW','QMS:RECORD:VIEW','QMS:RECORD:CREATE');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code='WORKSHOP_CHIEF'
  AND p.perm_code IN ('QMS:REWORK:VIEW','QMS:REWORK:START','QMS:REWORK:COMPLETE','QMS:REWORK:UPDATE');
