-- PLM 工程变更审批：研发经理确认工程合理性，再由工艺主管确认制造可执行性。
USE mfg_auth;
INSERT INTO wf_definition (def_code,def_name,biz_type,def_version,is_enabled,description)
SELECT 'PLM_ECN_CHANGE','工程变更单审批','ECN',1,1,'研发经理 → 工艺主管；通过后方可实施'
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE def_code='PLM_ECN_CHANGE' AND def_version=1);

INSERT INTO wf_node (definition_id,node_seq,node_name,approver_role,approve_mode,is_required,reject_action,remark)
SELECT d.id,10,'研发经理评审','RND_MANAGER','SINGLE',1,'BACK','确认设计变更的必要性和风险'
FROM wf_definition d WHERE d.def_code='PLM_ECN_CHANGE' AND d.def_version=1
AND NOT EXISTS (SELECT 1 FROM wf_node n WHERE n.definition_id=d.id AND n.node_seq=10);
INSERT INTO wf_node (definition_id,node_seq,node_name,approver_role,approve_mode,is_required,reject_action,remark)
SELECT d.id,20,'工艺主管评审','PROCESS_SUPERVISOR','SINGLE',1,'BACK','确认工艺、产能和制造影响'
FROM wf_definition d WHERE d.def_code='PLM_ECN_CHANGE' AND d.def_version=1
AND NOT EXISTS (SELECT 1 FROM wf_node n WHERE n.definition_id=d.id AND n.node_seq=20);
