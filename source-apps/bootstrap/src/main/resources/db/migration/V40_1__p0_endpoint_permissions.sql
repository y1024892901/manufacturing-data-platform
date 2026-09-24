-- ============================================================
-- V40.1 —— 端点级权限码定义与授予（计划 00 · F1-06…F1-11 配套）
--
-- 背景：F1 为十系统 260 个端点补方法级 @PreAuthorize，其中 130 个端点所需的
-- 权限码在字典中**不存在**。若不落地本迁移，这些端点会从「原先无鉴权（人人可调）」
-- 变成「码不存在（谁都调不到）」——比修复前更糟。特别注意 /api/admin/**：
-- 其类级 hasRole('ADMIN') 兜底已按规范移除，改为 29 个方法级码。
--
-- 顺序要求（务必遵守，否则又是「字典有码、无人有权」）：
--   ① 先 INSERT 权限码 → ② 再跑通配授权（LIKE 'ERP:%' 等）→ ③ 最后补窄口径角色
-- 通配授权是「授予时快照」，跑在码插入之前就覆盖不到新码。
--
-- 另修补 V39 的一处遗漏：未重跑 DATA_ANALYST 的 '%:VIEW' 通配，
-- 导致新加的 VIEW 码到不了数据分析师。本迁移一并补上。
-- ============================================================

-- ------------------------------------------------------------
-- ① 权限码定义（130 个）
-- ------------------------------------------------------------

-- MDM（20）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('MDM:BASE_DATA:VIEW','查看基础字典','mdm','基础数据','BUTTON'),
('MDM:BASE_DATA:CREATE','新增基础字典','mdm','基础数据','BUTTON'),
('MDM:BASE_DATA:UPDATE','修改基础字典','mdm','基础数据','BUTTON'),
('MDM:BASE_DATA:DELETE','删除基础字典','mdm','基础数据','BUTTON'),
('MDM:PRODUCT:VIEW','查看产品','mdm','产品主数据','BUTTON'),
('MDM:PRODUCT:SUBMIT','提交产品审批','mdm','产品主数据','BUTTON'),
('MDM:BOM:UPDATE','修改BOM','mdm','工程主数据','BUTTON'),
('MDM:BOM:DELETE','删除BOM行与替代料','mdm','工程主数据','BUTTON'),
('MDM:CUSTOMER:SUBMIT','提交客户审批','mdm','业务伙伴','BUTTON'),
('MDM:CUSTOMER:DELETE','删除客户','mdm','业务伙伴','BUTTON'),
('MDM:SUPPLIER:UPDATE','修改供应商','mdm','业务伙伴','BUTTON'),
('MDM:SUPPLIER:SUBMIT','提交供应商审批','mdm','业务伙伴','BUTTON'),
('MDM:SUPPLIER:DELETE','删除供应商','mdm','业务伙伴','BUTTON'),
('MDM:PARTNER_CONTACT:VIEW','查看伙伴联系人','mdm','业务伙伴','BUTTON'),
('MDM:PARTNER_CONTACT:CREATE','新增伙伴联系人','mdm','业务伙伴','BUTTON'),
('MDM:PARTNER_CONTACT:UPDATE','修改伙伴联系人','mdm','业务伙伴','BUTTON'),
('MDM:PARTNER_CONTACT:DELETE','删除伙伴联系人','mdm','业务伙伴','BUTTON'),
('MDM:GOVERNANCE:VIEW','查看治理工作台','mdm','数据治理','BUTTON'),
('MDM:GOVERNANCE:MERGE','执行主数据合并','mdm','数据治理','BUTTON'),
('MDM:DISTRIBUTE:EXECUTE','执行分发对账','mdm','分发中心','BUTTON');

-- PLM（11）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('PLM:CATALOG:VIEW','查看产品与文档目录','plm','产品工程','BUTTON'),
('PLM:CATALOG:CREATE','新增产品与文档','plm','产品工程','BUTTON'),
('PLM:CATALOG:UPDATE','修改产品与文档','plm','产品工程','BUTTON'),
('PLM:CATALOG:PUBLISH','发布产品与文档','plm','产品工程','BUTTON'),
('PLM:CHANGE:VIEW','查看变更链','plm','工程变更','BUTTON'),
('PLM:ECR:CREATE','创建变更申请','plm','工程变更','BUTTON'),
('PLM:ECR:UPDATE','维护变更影响分析','plm','工程变更','BUTTON'),
('PLM:ECO:CREATE','创建变更命令','plm','工程变更','BUTTON'),
('PLM:ECO:SUBMIT','提交变更命令审批','plm','工程变更','BUTTON'),
('PLM:ECN:VIEW','查看变更通知','plm','工程变更','BUTTON'),
('PLM:ECN:IMPLEMENT','实施变更通知','plm','工程变更','BUTTON');

-- CRM（17）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('CRM:LEAD:VIEW','查看线索','crm','线索管理','BUTTON'),
('CRM:LEAD:FOLLOW','登记线索跟进','crm','线索管理','BUTTON'),
('CRM:LEAD:CONVERT','线索转商机','crm','线索管理','BUTTON'),
('CRM:CUSTOMER:VIEW','查看客户360','crm','客户360','BUTTON'),
('CRM:OPPORTUNITY:FOLLOW','登记商机跟进','crm','商机管理','BUTTON'),
('CRM:QUOTATION:VIEW','查看报价','crm','报价管理','BUTTON'),
('CRM:QUOTATION:CREATE','新增报价','crm','报价管理','BUTTON'),
('CRM:QUOTATION:NEW_VERSION','报价新版本','crm','报价管理','BUTTON'),
('CRM:QUOTATION:ACTIVATE','报价生效','crm','报价管理','BUTTON'),
('CRM:CONTRACT:VIEW','查看合同','crm','合同管理','BUTTON'),
('CRM:CONTRACT:CREATE','新增合同','crm','合同管理','BUTTON'),
('CRM:CONTRACT:SUBMIT','提交合同审批','crm','合同管理','BUTTON'),
('CRM:CONTRACT:CONVERT','合同转订单','crm','合同管理','BUTTON'),
('CRM:CONTRACT:CHANGE','合同变更','crm','合同管理','BUTTON'),
('CRM:COMPLAINT:VIEW','查看客户投诉','crm','客诉协同','BUTTON'),
('CRM:COMPLAINT:CREATE','登记客户投诉','crm','客诉协同','BUTTON'),
('CRM:FORECAST:VIEW','查看销售预测','crm','销售预测','BUTTON');

-- ERP（13）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('ERP:CREDIT:CHECK','客户信用检查','erp','信用与交期','BUTTON'),
('ERP:CREDIT:VIEW','查看客户信用','erp','信用与交期','BUTTON'),
('ERP:ATP:CHECK','交期模拟计算','erp','信用与交期','BUTTON'),
('ERP:SALES_ORDER:CHANGE','销售订单变更','erp','销售订单','BUTTON'),
('ERP:SALES_ORDER:CANCEL','取消销售订单','erp','销售订单','BUTTON'),
('ERP:MRP:VIEW','查看MRP运算','erp','生产计划','BUTTON'),
('ERP:MRP:CREATE','创建MRP运算','erp','生产计划','BUTTON'),
('ERP:PLAN_SUGGESTION:VIEW','查看计划建议','erp','生产计划','BUTTON'),
('ERP:PLAN_SUGGESTION:CONFIRM','确认计划建议','erp','生产计划','BUTTON'),
('ERP:INVOICE:VIEW','查看发票','erp','应收管理','BUTTON'),
('ERP:INVOICE:CREATE','开具发票','erp','应收管理','BUTTON'),
('ERP:RECEIPT:VIEW','查看收款单','erp','应收管理','BUTTON'),
('ERP:PAYABLE:CREATE','登记应付','erp','应付管理','BUTTON');

-- SRM（7）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('SRM:PURCHASE:SEND','下达采购订单','srm','采购协同','BUTTON'),
('SRM:DELIVERY:VIEW','查看到货单','srm','采购协同','BUTTON'),
('SRM:DELIVERY:INSPECT','到货检验','srm','质量协同','BUTTON'),
('SRM:RECORD:VIEW','查看供应商协同记录','srm','采购协同','BUTTON'),
('SRM:RECORD:CREATE','新建供应商协同记录','srm','采购协同','BUTTON'),
('SRM:RECORD:UPDATE','推进协同记录状态','srm','采购协同','BUTTON'),
('SRM:ASN:ARRIVE','ASN到货确认','srm','采购协同','BUTTON');

-- WMS（8）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('WMS:LOCATION:VIEW','查看库位','wms','基础设置','BUTTON'),
('WMS:LOCATION:CREATE','创建库位','wms','基础设置','BUTTON'),
('WMS:LOCATION:UPDATE','启停库位','wms','基础设置','BUTTON'),
('WMS:STOCK:VIEW','查看库存流水','wms','出库','BUTTON'),
('WMS:RECORD:VIEW','查看仓储记录','wms','上架与库存','BUTTON'),
('WMS:RECORD:CREATE','新建仓储记录','wms','上架与库存','BUTTON'),
('WMS:TRANSFER:CONFIRM','确认调拨','wms','库内作业','BUTTON'),
('WMS:COUNT:REVIEW','盘点复核','wms','库内作业','BUTTON');

-- QMS（9）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('QMS:DEFECT:VIEW','查看不合格品记录','qms','不合格管理','BUTTON'),
('QMS:DEFECT:CREATE','登记不合格品记录','qms','不合格管理','BUTTON'),
('QMS:REWORK:VIEW','查看返工单','qms','返工报废','BUTTON'),
('QMS:REWORK:CREATE','创建返工单','qms','返工报废','BUTTON'),
('QMS:REWORK:START','返工开工','qms','返工报废','BUTTON'),
('QMS:REWORK:COMPLETE','返工完工','qms','返工报废','BUTTON'),
('QMS:CAPA:VERIFY','验证CAPA','qms','CAPA','BUTTON'),
('QMS:8D:VERIFY','验证8D','qms','CAPA','BUTTON'),
('QMS:RECORD:VIEW','查看质量记录','qms','质量基础','BUTTON'),
('QMS:RECORD:CREATE','新建质量记录','qms','质量基础','BUTTON');

-- MES（2）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('MES:WORK_ORDER:CREATE','创建工单','mes','工单与派工','BUTTON'),
('MES:WORK_REPORT:VIEW','查看报工记录','mes','工序执行','BUTTON');

-- EAM（7）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('EAM:EQUIPMENT:CREATE','新增设备台账','eam','资产台账','BUTTON'),
('EAM:EQUIPMENT:UPDATE','修改设备台账与状态','eam','资产台账','BUTTON'),
('EAM:FAULT:VIEW','查看故障单','eam','维修管理','BUTTON'),
('EAM:FAULT:CLOSE','关闭故障单','eam','维修管理','BUTTON'),
('EAM:INSPECTION:VIEW','查看点检单','eam','点检保养','BUTTON'),
('EAM:REPAIR:VIEW','查看维修单','eam','维修管理','BUTTON'),
('EAM:REPAIR:CREATE','创建维修单','eam','维修管理','BUTTON');

-- 能源（2）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('ENERGY:WORKSHOP_USAGE:VIEW','查看车间能耗','energy','能源台账','BUTTON'),
('ENERGY:WORKSHOP_USAGE:CREATE','录入车间能耗','energy','能源台账','BUTTON');

-- SYS 平台治理（27）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('SYS:USER:VIEW','查看用户','sys','用户管理','BUTTON'),
('SYS:USER:CREATE','新建用户','sys','用户管理','BUTTON'),
('SYS:USER:UPDATE','修改用户','sys','用户管理','BUTTON'),
('SYS:USER:DELETE','删除用户','sys','用户管理','BUTTON'),
('SYS:USER:ENABLE','启用用户','sys','用户管理','BUTTON'),
('SYS:USER:DISABLE','停用用户','sys','用户管理','BUTTON'),
('SYS:USER:LOCK','锁定用户','sys','用户管理','BUTTON'),
('SYS:USER:UNLOCK','解锁用户','sys','用户管理','BUTTON'),
('SYS:USER:RESET_PASSWORD','重置用户密码','sys','用户管理','BUTTON'),
('SYS:USER:ASSIGN_ROLE','分配用户角色','sys','用户管理','BUTTON'),
('SYS:USER:ASSIGN_SYSTEM','分配可访问系统','sys','用户管理','BUTTON'),
('SYS:USER:ASSIGN_DATA_SCOPE','设置用户数据范围','sys','用户管理','BUTTON'),
('SYS:ROLE:VIEW','查看角色','sys','角色权限','BUTTON'),
('SYS:ROLE:CREATE','新建角色','sys','角色权限','BUTTON'),
('SYS:ROLE:UPDATE','修改角色','sys','角色权限','BUTTON'),
('SYS:ROLE:DELETE','删除角色','sys','角色权限','BUTTON'),
('SYS:ROLE:ASSIGN_PERMISSION','配置角色权限','sys','角色权限','BUTTON'),
('SYS:PERMISSION:VIEW','查看权限字典','sys','角色权限','BUTTON'),
('SYS:DEPT:VIEW','查看部门','sys','组织管理','BUTTON'),
('SYS:DEPT:CREATE','新建部门','sys','组织管理','BUTTON'),
('SYS:DEPT:UPDATE','修改部门','sys','组织管理','BUTTON'),
('SYS:DEPT:DELETE','删除部门','sys','组织管理','BUTTON'),
('SYS:LOGIN_LOG:VIEW','查看登录日志','sys','审计','BUTTON'),
('SYS:AUDIT_LOG:VIEW','查看审计日志','sys','审计','BUTTON'),
('SYS:NAV:VIEW','查看导航菜单','sys','平台基础','BUTTON'),
('SYS:EVENT:VIEW','查看业务事件','sys','平台基础','BUTTON'),
('SYS:EVENT:EXECUTE','重投业务事件','sys','平台基础','BUTTON');

-- WF 审批（6）
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('WF:INSTANCE:VIEW','查看流程实例','wf','审批','BUTTON'),
('WF:DEFINITION:VIEW','查看流程定义','wf','审批','BUTTON'),
('WF:INSTANCE:CANCEL','撤回流程实例','wf','审批','BUTTON'),
('WF:INSTANCE:CC','抄送流程','wf','审批','BUTTON'),
('WF:TASK:TRANSFER','转办任务','wf','审批','BUTTON'),
('WF:TASK:ADD_SIGN','加签任务','wf','审批','BUTTON');

-- ------------------------------------------------------------
-- ② 通配授权追认（必须在 ① 之后）
--
-- 与 09_role_permission.sql 的角色口径一致，重跑以覆盖刚插入的新码。
-- ------------------------------------------------------------
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE (r.role_code IN ('MDM_ADMIN','ADMIN') AND p.perm_code LIKE 'MDM:%')
   OR (r.role_code = 'SALES_DIRECTOR'   AND p.perm_code LIKE 'CRM:%')
   OR (r.role_code = 'FINANCE_DIRECTOR' AND p.perm_code LIKE 'ERP:%')
   OR (r.role_code IN ('QUALITY_ENGINEER','QUALITY_SUPERVISOR') AND p.perm_code LIKE 'QMS:%')
   OR (r.role_code IN ('WORKSHOP_CHIEF','PROD_SUPERVISOR','PROD_MANAGER') AND p.perm_code LIKE 'MES:%')
   OR (r.role_code = 'WAREHOUSE_SUPERVISOR' AND p.perm_code LIKE 'WMS:%')
   OR (r.role_code IN ('PURCHASE_SUPERVISOR','PURCHASE_DIRECTOR') AND p.perm_code LIKE 'SRM:%')
   OR (r.role_code = 'EQUIPMENT_SUPERVISOR' AND p.perm_code LIKE 'EAM:%')
   OR (r.role_code IN ('PROCESS_SUPERVISOR','RND_MANAGER') AND p.perm_code LIKE 'PLM:%')
   OR (r.role_code = 'ENERGY_ADMIN' AND p.perm_code LIKE 'ENERGY:%')
   OR (r.role_code = 'ADMIN' AND p.perm_code LIKE 'SYS:%');

-- 数据分析师：全系统只读。V39 漏跑了这条，新 VIEW 码到不了 DATA_ANALYST，此处补上。
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'DATA_ANALYST'
  AND (p.perm_code LIKE '%:VIEW' OR p.perm_code IN
       ('MDM:DISTRIBUTE:VIEW','ENERGY:ANALYSIS:VIEW','WF:TASK:VIEW'));

-- ------------------------------------------------------------
-- ③ 窄口径角色补授（通配覆盖不到的作业岗与特定角色）
-- ------------------------------------------------------------

-- MDM：产品与工程主数据给研发工艺
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PRODUCT_ENGINEER','PROCESS_ENGINEER')
  AND p.perm_code IN ('MDM:PRODUCT:VIEW','MDM:PRODUCT:SUBMIT','MDM:BOM:UPDATE','MDM:BOM:DELETE',
                      'MDM:PARTNER_CONTACT:VIEW','MDM:GOVERNANCE:VIEW');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PROCESS_SUPERVISOR','RND_MANAGER')
  AND p.perm_code IN ('MDM:PRODUCT:VIEW','MDM:BOM:UPDATE','MDM:PARTNER_CONTACT:VIEW','MDM:GOVERNANCE:VIEW');

-- PLM：变更链给研发工艺与主管
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PRODUCT_ENGINEER','PROCESS_ENGINEER')
  AND p.perm_code IN ('PLM:CATALOG:VIEW','PLM:CATALOG:CREATE','PLM:CATALOG:UPDATE',
                      'PLM:CHANGE:VIEW','PLM:ECR:CREATE','PLM:ECR:UPDATE',
                      'PLM:ECO:CREATE','PLM:ECO:SUBMIT','PLM:ECN:VIEW','PLM:ECN:IMPLEMENT');

-- CRM：销售侧作业岗
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('SALES_REP','SALES_ASSISTANT')
  AND p.perm_code IN ('CRM:LEAD:VIEW','CRM:LEAD:FOLLOW','CRM:LEAD:CONVERT','CRM:CUSTOMER:VIEW',
                      'CRM:OPPORTUNITY:FOLLOW','CRM:QUOTATION:VIEW','CRM:QUOTATION:CREATE',
                      'CRM:CONTRACT:VIEW','CRM:CONTRACT:CREATE','CRM:CONTRACT:SUBMIT',
                      'CRM:CONTRACT:CONVERT','CRM:COMPLAINT:VIEW','CRM:COMPLAINT:CREATE');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'SALES_SUPERVISOR'
  AND p.perm_code IN ('CRM:QUOTATION:NEW_VERSION','CRM:QUOTATION:ACTIVATE','CRM:CONTRACT:CHANGE',
                      'CRM:FORECAST:VIEW','CRM:LEAD:CONVERT');

-- ERP：销售、计划、财务各岗
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('SALES_REP','SALES_ASSISTANT','SALES_SUPERVISOR')
  AND p.perm_code IN ('ERP:CREDIT:CHECK','ERP:CREDIT:VIEW','ERP:ATP:CHECK');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'SALES_SUPERVISOR'
  AND p.perm_code IN ('ERP:SALES_ORDER:CHANGE','ERP:SALES_ORDER:CANCEL');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'PROD_PLANNER'
  AND p.perm_code IN ('ERP:MRP:VIEW','ERP:MRP:CREATE','ERP:PLAN_SUGGESTION:VIEW',
                      'ERP:PLAN_SUGGESTION:CONFIRM','ERP:ATP:CHECK');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PROD_SUPERVISOR','PROD_MANAGER')
  AND p.perm_code IN ('ERP:MRP:VIEW','ERP:PLAN_SUGGESTION:VIEW');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE (r.role_code = 'AR_ACCOUNTANT' AND p.perm_code IN ('ERP:INVOICE:VIEW','ERP:INVOICE:CREATE','ERP:RECEIPT:VIEW','ERP:CREDIT:VIEW'))
   OR (r.role_code = 'AP_ACCOUNTANT' AND p.perm_code IN ('ERP:INVOICE:VIEW','ERP:PAYABLE:CREATE'))
   OR (r.role_code = 'GL_ACCOUNTANT' AND p.perm_code IN ('ERP:INVOICE:VIEW','ERP:INVOICE:CREATE'))
   OR (r.role_code = 'CASHIER' AND p.perm_code IN ('ERP:INVOICE:VIEW','ERP:RECEIPT:VIEW'))
   OR (r.role_code = 'FIN_SUPERVISOR' AND p.perm_code = 'ERP:RECEIPT:VIEW')
   OR (r.role_code = 'BUYER' AND p.perm_code = 'ERP:PLAN_SUGGESTION:VIEW');

-- SRM：采购作业岗
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'BUYER'
  AND p.perm_code IN ('SRM:PURCHASE:SEND','SRM:DELIVERY:VIEW','SRM:RECORD:VIEW',
                      'SRM:RECORD:CREATE','SRM:RECORD:UPDATE','SRM:ASN:ARRIVE');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'SQE'
  AND p.perm_code IN ('SRM:DELIVERY:VIEW','SRM:DELIVERY:INSPECT','SRM:RECORD:VIEW','SRM:RECORD:UPDATE');

-- WMS：库管员与主管（复核权与盘点权分离，避免自盘自审）
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'WAREHOUSE_KEEPER'
  AND p.perm_code IN ('WMS:LOCATION:VIEW','WMS:STOCK:VIEW','WMS:RECORD:VIEW',
                      'WMS:RECORD:CREATE','WMS:TRANSFER:CONFIRM');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'WAREHOUSE_SUPERVISOR'
  AND p.perm_code IN ('WMS:LOCATION:CREATE','WMS:LOCATION:UPDATE','WMS:COUNT:REVIEW');

-- QMS：质量作业岗
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'QC_INSPECTOR'
  AND p.perm_code IN ('QMS:DEFECT:VIEW','QMS:DEFECT:CREATE','QMS:REWORK:VIEW','QMS:RECORD:VIEW');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'QUALITY_ENGINEER'
  AND p.perm_code IN ('QMS:DEFECT:CREATE','QMS:REWORK:CREATE','QMS:REWORK:START',
                      'QMS:REWORK:COMPLETE','QMS:CAPA:VERIFY','QMS:8D:VERIFY','QMS:RECORD:CREATE');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'WORKSHOP_CHIEF'
  AND p.perm_code IN ('QMS:REWORK:START','QMS:REWORK:COMPLETE');

-- MES：工单创建与报工查看
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'PROD_PLANNER' AND p.perm_code = 'MES:WORK_ORDER:CREATE';
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('TEAM_LEADER','WORKSHOP_CHIEF','PROD_SUPERVISOR','PROD_MANAGER')
  AND p.perm_code = 'MES:WORK_REPORT:VIEW';

-- EAM：设备与维修作业岗
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'EQUIPMENT_REPAIRMAN'
  AND p.perm_code IN ('EAM:EQUIPMENT:UPDATE','EAM:FAULT:VIEW','EAM:FAULT:CLOSE',
                      'EAM:INSPECTION:VIEW','EAM:REPAIR:VIEW','EAM:REPAIR:CREATE');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'EQUIPMENT_SUPERVISOR'
  AND p.perm_code IN ('EAM:EQUIPMENT:CREATE','EAM:FAULT:CLOSE');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'WORKSHOP_CHIEF'
  AND p.perm_code IN ('EAM:FAULT:VIEW','ENERGY:WORKSHOP_USAGE:VIEW','ENERGY:WORKSHOP_USAGE:CREATE');

-- 能源：车间能耗
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'ENERGY_ADMIN'
  AND p.perm_code IN ('ENERGY:WORKSHOP_USAGE:VIEW','ENERGY:WORKSHOP_USAGE:CREATE');

-- SYS：导航菜单必须给全部角色（否则所有用户侧边栏 403，影响面比 admin 控制台更大）
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE p.perm_code = 'SYS:NAV:VIEW';

-- WF：审批相关码给现有审批岗与执行岗
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('SALES_SUPERVISOR','SALES_DIRECTOR','COST_ACCOUNTANT','AR_ACCOUNTANT',
                      'AP_ACCOUNTANT','GL_ACCOUNTANT','FIN_SUPERVISOR','FINANCE_DIRECTOR',
                      'PURCHASE_SUPERVISOR','PURCHASE_DIRECTOR','SQE','PROCESS_SUPERVISOR',
                      'RND_MANAGER','PROD_PLANNER','PROD_SUPERVISOR','PROD_MANAGER',
                      'QUALITY_SUPERVISOR','EQUIPMENT_SUPERVISOR','WAREHOUSE_SUPERVISOR','ADMIN')
  AND p.perm_code IN ('WF:INSTANCE:VIEW','WF:DEFINITION:VIEW','WF:INSTANCE:CANCEL',
                      'WF:INSTANCE:CC','WF:TASK:TRANSFER','WF:TASK:ADD_SIGN');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('SALES_REP','SALES_ASSISTANT','BUYER','PROCESS_ENGINEER',
                      'PRODUCT_ENGINEER','MDM_ADMIN','QC_INSPECTOR')
  AND p.perm_code IN ('WF:INSTANCE:VIEW','WF:DEFINITION:VIEW','WF:INSTANCE:CANCEL');
