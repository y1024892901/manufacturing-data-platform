CREATE TABLE src_wms.wms_inventory_ledger(
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 idempotency_key VARCHAR(80) NOT NULL,
 action_type VARCHAR(24) NOT NULL,
 source_system VARCHAR(16) NOT NULL,
 source_no VARCHAR(64) NULL,
 material_code VARCHAR(32) NOT NULL,
 warehouse_code VARCHAR(32) NOT NULL,
 location_code VARCHAR(32) NULL,
 batch_no VARCHAR(64) NULL,
 quality_status_before VARCHAR(16) NULL,
 quality_status_after VARCHAR(16) NULL,
 on_hand_delta DECIMAL(18,4) NOT NULL DEFAULT 0,
 available_delta DECIMAL(18,4) NOT NULL DEFAULT 0,
 frozen_delta DECIMAL(18,4) NOT NULL DEFAULT 0,
 operator_code VARCHAR(32) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_inventory_ledger_idempotency(idempotency_key),
 KEY idx_inventory_ledger_source(source_system,source_no),
 KEY idx_inventory_ledger_stock(material_code,warehouse_code,location_code,batch_no)
);

ALTER TABLE src_wms.wms_transfer ADD COLUMN idempotency_key VARCHAR(80) NULL, ADD UNIQUE KEY uk_transfer_idempotency(idempotency_key);
ALTER TABLE src_wms.wms_count_plan ADD COLUMN posted_at DATETIME(3) NULL, ADD COLUMN idempotency_key VARCHAR(80) NULL, ADD UNIQUE KEY uk_count_idempotency(idempotency_key);

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_type) VALUES
('WMS:INVENTORY:ACTION','库存状态操作','wms','BUTTON'),
('WMS:TRANSFER:EXECUTE','执行库存调拨','wms','BUTTON'),
('WMS:COUNT:POST','盘点差异过账','wms','BUTTON'),
('QMS:NCR:DISPOSE','不合格处置','qms','BUTTON'),
('QMS:CAPA:CLOSE','验证并关闭CAPA','qms','BUTTON'),
('QMS:8D:CLOSE','验证并关闭8D','qms','BUTTON');
