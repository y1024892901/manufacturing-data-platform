ALTER TABLE src_srm.srm_purchase_order ADD COLUMN source_requisition_no VARCHAR(32) NULL,ADD COLUMN confirmed_at DATETIME(3) NULL,ADD COLUMN change_reason VARCHAR(500) NULL;
ALTER TABLE src_srm.srm_delivery ADD COLUMN asn_no VARCHAR(32) NULL,ADD COLUMN receipt_no VARCHAR(32) NULL;
