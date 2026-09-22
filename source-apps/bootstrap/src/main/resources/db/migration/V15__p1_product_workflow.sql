INSERT INTO mfg_auth.wf_definition(def_code,def_name,biz_type,description,is_enabled)
SELECT 'MDM_PRODUCT_NEW','产品主数据审批','PRODUCT','产品经理提交 → 研发经理 → 生产计划员',1
WHERE NOT EXISTS (SELECT 1 FROM mfg_auth.wf_definition WHERE biz_type='PRODUCT' AND is_enabled=1);

INSERT INTO mfg_auth.wf_node(definition_id,node_seq,node_name,approver_role,approve_mode,reject_action,remark)
SELECT d.id,10,'研发经理审核','RND_MANAGER','SINGLE','BACK','确认产品定义、型号与生命周期'
FROM mfg_auth.wf_definition d
WHERE d.biz_type='PRODUCT' AND d.is_enabled=1
  AND NOT EXISTS (SELECT 1 FROM mfg_auth.wf_node n WHERE n.definition_id=d.id AND n.node_seq=10);

INSERT INTO mfg_auth.wf_node(definition_id,node_seq,node_name,approver_role,approve_mode,reject_action,remark)
SELECT d.id,20,'生产计划审核','PROD_PLANNER','SINGLE','BACK','确认成品物料、上市日期与生产准备'
FROM mfg_auth.wf_definition d
WHERE d.biz_type='PRODUCT' AND d.is_enabled=1
  AND NOT EXISTS (SELECT 1 FROM mfg_auth.wf_node n WHERE n.definition_id=d.id AND n.node_seq=20);
