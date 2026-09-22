-- supplier_code already belongs to the original inspection model.  Only add the
-- P3 traceability fields here so this migration remains compatible with an
-- upgraded P2 database.
ALTER TABLE src_qms.qms_inspection ADD COLUMN source_type VARCHAR(16) NULL,ADD COLUMN source_no VARCHAR(32) NULL,ADD COLUMN standard_code VARCHAR(32) NULL;
CREATE TABLE src_qms.qms_ncr(id BIGINT AUTO_INCREMENT PRIMARY KEY,ncr_no VARCHAR(32) UNIQUE,inspection_no VARCHAR(32),source_no VARCHAR(32),material_code VARCHAR(32),supplier_code VARCHAR(32),defect_qty DECIMAL(18,4),severity VARCHAR(16),responsible_dept VARCHAR(32),status VARCHAR(20) DEFAULT 'OPEN',disposition VARCHAR(20),created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3));
CREATE TABLE src_qms.qms_capa(id BIGINT AUTO_INCREMENT PRIMARY KEY,capa_no VARCHAR(32) UNIQUE,ncr_no VARCHAR(32),root_cause VARCHAR(1000),corrective_action VARCHAR(1000),preventive_action VARCHAR(1000),owner_user VARCHAR(32),due_date DATE,status VARCHAR(20) DEFAULT 'DRAFT',verified_by VARCHAR(32),verified_at DATETIME(3));
