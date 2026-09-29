-- PLM catalog metadata is based on lifecycle/revision-controlled PLM records.
-- Nullable descriptive fields are intentionally optional and are surfaced as such in the UI.
ALTER TABLE src_plm.plm_product_family
    ADD COLUMN family_description VARCHAR(500) NULL AFTER owner_user;

ALTER TABLE src_plm.plm_product_version
    ADD COLUMN change_summary VARCHAR(1000) NULL AFTER effective_date,
    ADD COLUMN target_market VARCHAR(100) NULL AFTER change_summary;

ALTER TABLE src_plm.plm_document
    ADD COLUMN language_code VARCHAR(16) NOT NULL DEFAULT 'zh-CN' AFTER confidentiality,
    ADD COLUMN effective_date DATE NULL AFTER language_code,
    ADD COLUMN description VARCHAR(500) NULL AFTER effective_date;

ALTER TABLE src_plm.plm_engineering_baseline
    ADD COLUMN baseline_purpose VARCHAR(500) NULL AFTER routing_version,
    ADD COLUMN remark VARCHAR(500) NULL AFTER baseline_purpose;

INSERT IGNORE INTO mfg_auth.sys_permission(perm_code, perm_name, system_code, perm_group, perm_type) VALUES
('PLM:CATALOG:DELETE', '删除草稿产品与文档', 'plm', '产品工程', 'BUTTON'),
('PLM:ECR:DELETE', '删除草稿变更申请', 'plm', '工程变更', 'BUTTON'),
('PLM:ECO:UPDATE', '修改草稿变更命令', 'plm', '工程变更', 'BUTTON'),
('PLM:ECO:DELETE', '删除草稿变更命令', 'plm', '工程变更', 'BUTTON'),
('PLM:ECN:CREATE', '创建变更通知', 'plm', '工程变更', 'BUTTON'),
('PLM:ECN:UPDATE', '修改草稿变更通知', 'plm', '工程变更', 'BUTTON'),
('PLM:ECN:DELETE', '删除草稿变更通知', 'plm', '工程变更', 'BUTTON'),
('PLM:PRODUCT:VIEW', '查看MDM工程BOM副本', 'plm', '产品工程', 'BUTTON');

-- Product/process engineers maintain PLM-owned draft records and run the controlled lifecycle actions.
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PRODUCT_ENGINEER', 'PROCESS_ENGINEER')
  AND p.perm_code IN (
    'PLM:CATALOG:VIEW', 'PLM:CATALOG:CREATE', 'PLM:CATALOG:UPDATE', 'PLM:CATALOG:DELETE', 'PLM:CATALOG:PUBLISH', 'PLM:PRODUCT:VIEW',
    'PLM:CHANGE:VIEW', 'PLM:ECR:CREATE', 'PLM:ECR:UPDATE', 'PLM:ECR:DELETE',
    'PLM:ECO:CREATE', 'PLM:ECO:UPDATE', 'PLM:ECO:DELETE', 'PLM:ECO:SUBMIT',
    'PLM:ECN:VIEW', 'PLM:ECN:CREATE', 'PLM:ECN:UPDATE', 'PLM:ECN:DELETE', 'PLM:ECN:IMPLEMENT'
  );

-- Re-apply the historical PLM wildcard snapshot so newly defined PLM permissions reach managers too.
INSERT IGNORE INTO mfg_auth.sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM mfg_auth.sys_role r
JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PROCESS_SUPERVISOR', 'RND_MANAGER')
  AND p.perm_code LIKE 'PLM:%';
