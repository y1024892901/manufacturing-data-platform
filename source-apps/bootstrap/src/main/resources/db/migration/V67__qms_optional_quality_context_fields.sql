-- Optional context fields for QMS records, aligned with inspection planning,
-- defect catalog, risk review, and effectiveness evidence practices.
ALTER TABLE src_qms.qms_standard
  ADD COLUMN inspection_scope VARCHAR(500) NULL;

ALTER TABLE src_qms.qms_sampling_plan
  ADD COLUMN sampling_frequency VARCHAR(255) NULL;

ALTER TABLE src_qms.qms_inspection
  ADD COLUMN inspection_point VARCHAR(255) NULL;

ALTER TABLE src_qms.qms_defect
  ADD COLUMN defect_code VARCHAR(32) NULL;

ALTER TABLE src_qms.qms_ncr
  ADD COLUMN risk_evaluation VARCHAR(1000) NULL;

ALTER TABLE src_qms.qms_rework
  ADD COLUMN verification_note VARCHAR(1000) NULL;

ALTER TABLE src_qms.qms_capa
  ADD COLUMN effectiveness_evidence VARCHAR(1000) NULL;

ALTER TABLE src_qms.qms_eight_d
  ADD COLUMN supplier_reference_no VARCHAR(64) NULL;
