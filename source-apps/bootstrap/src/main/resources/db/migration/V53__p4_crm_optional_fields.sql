-- CRM 页面常用的补充业务信息，全部可空，兼容现有数据。
ALTER TABLE src_crm.crm_lead
  ADD COLUMN contact_email VARCHAR(254) NULL,
  ADD COLUMN contact_title VARCHAR(128) NULL,
  ADD COLUMN industry VARCHAR(64) NULL,
  ADD COLUMN expected_budget DECIMAL(18,2) NULL,
  ADD COLUMN preferred_contact_method VARCHAR(32) NULL;

ALTER TABLE src_crm.crm_opportunity
  ADD COLUMN lead_source VARCHAR(32) NULL,
  ADD COLUMN next_step VARCHAR(255) NULL,
  ADD COLUMN description VARCHAR(1000) NULL,
  ADD COLUMN forecast_category VARCHAR(24) NULL,
  ADD COLUMN priority VARCHAR(16) NULL;

ALTER TABLE src_crm.crm_quotation
  ADD COLUMN ship_to_address VARCHAR(500) NULL,
  ADD COLUMN delivery_terms VARCHAR(500) NULL,
  ADD COLUMN notes VARCHAR(1000) NULL;

ALTER TABLE src_crm.crm_contract
  ADD COLUMN auto_renew TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN renewal_notice_days INT NULL,
  ADD COLUMN signing_date DATE NULL,
  ADD COLUMN special_terms VARCHAR(1000) NULL,
  ADD COLUMN attachment_url VARCHAR(500) NULL;

ALTER TABLE src_crm.crm_complaint
  ADD COLUMN contact_email VARCHAR(254) NULL,
  ADD COLUMN source_channel VARCHAR(32) NULL,
  ADD COLUMN response_due_at DATETIME(3) NULL,
  ADD COLUMN desired_resolution VARCHAR(255) NULL;
