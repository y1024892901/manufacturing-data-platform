-- Optional warehouse context fields based on pallet, batch and date traceability
-- practices, plus fields commonly used to locate and control storage positions.
ALTER TABLE src_wms.wms_location
  ADD COLUMN zone_code VARCHAR(32) NULL,
  ADD COLUMN aisle_code VARCHAR(32) NULL,
  ADD COLUMN rack_code VARCHAR(32) NULL,
  ADD COLUMN bin_code VARCHAR(32) NULL,
  ADD COLUMN max_weight_kg DECIMAL(12,3) NULL,
  ADD COLUMN note VARCHAR(500) NULL;

ALTER TABLE src_wms.wms_receipt
  ADD COLUMN supplier_lot_no VARCHAR(64) NULL,
  ADD COLUMN pallet_sscc VARCHAR(18) NULL,
  ADD COLUMN production_date DATE NULL,
  ADD COLUMN expiry_date DATE NULL,
  ADD COLUMN note VARCHAR(500) NULL;

ALTER TABLE src_wms.wms_inventory
  ADD COLUMN expiry_date DATE NULL;

ALTER TABLE src_wms.wms_putaway
  ADD COLUMN pallet_sscc VARCHAR(18) NULL,
  ADD COLUMN expiry_date DATE NULL,
  ADD COLUMN handling_note VARCHAR(500) NULL;

ALTER TABLE src_wms.wms_transfer
  ADD COLUMN reason VARCHAR(255) NULL,
  ADD COLUMN source_reference VARCHAR(64) NULL,
  ADD COLUMN requested_by VARCHAR(80) NULL,
  ADD COLUMN remark VARCHAR(500) NULL;

ALTER TABLE src_wms.wms_count_plan
  ADD COLUMN count_reason VARCHAR(255) NULL,
  ADD COLUMN owner_user VARCHAR(80) NULL,
  ADD COLUMN remark VARCHAR(500) NULL;

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('WMS:RECORD:UPDATE','修改仓储草稿单据','wms','仓储作业','BUTTON'),
('WMS:RECORD:DELETE','删除仓储草稿单据','wms','仓储作业','BUTTON'),
('WMS:LOCATION:DELETE','删除未使用库位','wms','基础设置','BUTTON');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code='WAREHOUSE_SUPERVISOR'
  AND p.perm_code IN ('WMS:RECORD:UPDATE','WMS:RECORD:DELETE','WMS:LOCATION:DELETE');

INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('WAREHOUSE_KEEPER','WAREHOUSE_SUPERVISOR')
  AND p.perm_code='WMS:RECORD:UPDATE';
