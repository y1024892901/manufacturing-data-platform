CREATE TABLE IF NOT EXISTS mfg_ops.biz_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    source_system VARCHAR(16) NOT NULL,
    target_system VARCHAR(16) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    event_version INT NOT NULL DEFAULT 1,
    payload JSON,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3),
    UNIQUE KEY uk_biz_event_target(event_id, target_system),
    KEY idx_biz_outbox_status(status, occurred_at)
);

CREATE TABLE IF NOT EXISTS mfg_ops.biz_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    source_system VARCHAR(16) NOT NULL,
    target_system VARCHAR(16) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    payload JSON,
    status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_biz_inbox_event_target(event_id, target_system)
);

ALTER TABLE src_crm.crm_contract
    ADD COLUMN previous_contract_id BIGINT NULL,
    ADD COLUMN change_reason VARCHAR(500) NULL;

ALTER TABLE src_erp.erp_credit_check
    ADD COLUMN exception_reason VARCHAR(500) NULL,
    ADD COLUMN exception_status VARCHAR(20) NULL;

ALTER TABLE src_erp.erp_atp_snapshot
    ADD COLUMN exception_reason VARCHAR(500) NULL,
    ADD COLUMN exception_status VARCHAR(20) NULL;

ALTER TABLE src_erp.erp_invoice
    ADD COLUMN voucher_no VARCHAR(32) NULL;

ALTER TABLE src_erp.erp_receipt
    ADD COLUMN voucher_no VARCHAR(32) NULL;

ALTER TABLE src_erp.erp_settlement
    ADD COLUMN voucher_no VARCHAR(32) NULL;

INSERT INTO mfg_auth.wf_definition(def_code,def_name,biz_type,def_version,is_enabled,description)
SELECT 'CRM_QUOTATION_RISK_APPROVAL','高风险销售报价审批','QUOTATION_RISK',1,1,'大额、高折扣或低毛利报价的销售与财务联合审批'
WHERE NOT EXISTS(SELECT 1 FROM mfg_auth.wf_definition WHERE biz_type='QUOTATION_RISK' AND is_enabled=1);

INSERT INTO mfg_auth.wf_node(definition_id,node_seq,node_name,approver_role,approve_mode,is_required,reject_action)
SELECT id,10,'销售总监审批','SALES_DIRECTOR','SINGLE',1,'BACK' FROM mfg_auth.wf_definition d
WHERE d.biz_type='QUOTATION_RISK' AND NOT EXISTS(SELECT 1 FROM mfg_auth.wf_node n WHERE n.definition_id=d.id AND n.node_seq=10);
INSERT INTO mfg_auth.wf_node(definition_id,node_seq,node_name,approver_role,approve_mode,is_required,reject_action)
SELECT id,20,'财务主管审批','FINANCE_SUPERVISOR','SINGLE',1,'BACK' FROM mfg_auth.wf_definition d
WHERE d.biz_type='QUOTATION_RISK' AND NOT EXISTS(SELECT 1 FROM mfg_auth.wf_node n WHERE n.definition_id=d.id AND n.node_seq=20);

INSERT INTO mfg_auth.wf_definition(def_code,def_name,biz_type,def_version,is_enabled,description)
SELECT 'ERP_CREDIT_EXCEPTION_APPROVAL','客户信用例外审批','CREDIT_EXCEPTION',1,1,'超额度、逾期或高风险客户订单信用放行'
WHERE NOT EXISTS(SELECT 1 FROM mfg_auth.wf_definition WHERE biz_type='CREDIT_EXCEPTION' AND is_enabled=1);
INSERT INTO mfg_auth.wf_node(definition_id,node_seq,node_name,approver_role,approve_mode,is_required,reject_action)
SELECT id,10,'财务主管审批','FINANCE_SUPERVISOR','SINGLE',1,'BACK' FROM mfg_auth.wf_definition d
WHERE d.biz_type='CREDIT_EXCEPTION' AND NOT EXISTS(SELECT 1 FROM mfg_auth.wf_node n WHERE n.definition_id=d.id AND n.node_seq=10);

