ALTER TABLE src_erp.erp_prod_order
    ADD COLUMN production_version_code VARCHAR(32) NULL,
    ADD COLUMN kit_status VARCHAR(16) NOT NULL DEFAULT 'UNCHECKED',
    ADD COLUMN kit_checked_at DATETIME(3) NULL;

