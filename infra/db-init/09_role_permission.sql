-- ============================================================
-- 09_role_permission.sql —— 角色-权限映射
--
-- 补上 08_seed_data.sql 遗漏的一环：角色与权限点的关联。
-- 没有这张表，所有角色的权限数为 0，@PreAuthorize 全部拦截。
--
-- 设计要点：权限按「部门 + 职级」分配
--   执行岗  —— 本部门业务数据的查看与录入
--   主管岗  —— 额外获得审批权、部分主数据维护权
--   经理/总监 —— 额外获得全局查看与主数据批准权
--
-- 幂等: 先清空再插入（这是配置表，非业务数据）
-- ============================================================

SET NAMES utf8mb4;
USE mfg_auth;

-- 配置表，可安全重建
DELETE FROM sys_role_permission;

-- ============================================================
-- 一、主数据平台权限（MDM）
-- ============================================================

-- 主数据管理员：全部主数据的查看与维护（不可审批）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('MDM_ADMIN','ADMIN')
  AND p.perm_code IN (
    'MDM:CUSTOMER:VIEW','MDM:CUSTOMER:CREATE','MDM:CUSTOMER:UPDATE',
    'MDM:SUPPLIER:VIEW','MDM:SUPPLIER:CREATE',
    'MDM:MATERIAL:VIEW','MDM:MATERIAL:CREATE','MDM:MATERIAL:UPDATE',
    'MDM:BOM:VIEW','MDM:BOM:CREATE','MDM:BOM:CHANGE',
    'MDM:ROUTING:VIEW','MDM:ROUTING:UPDATE',
    'MDM:DISTRIBUTE:VIEW','MDM:DISTRIBUTE:RETRY');

-- 销售：客户主数据
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('SALES_REP','SALES_ASSISTANT')
  AND p.perm_code IN ('MDM:CUSTOMER:VIEW','MDM:CUSTOMER:CREATE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('SALES_SUPERVISOR','SALES_DIRECTOR')
  AND p.perm_code IN ('MDM:CUSTOMER:VIEW','MDM:CUSTOMER:CREATE','MDM:CUSTOMER:UPDATE');

-- 采购：供应商主数据
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('BUYER','SQE')
  AND p.perm_code IN ('MDM:SUPPLIER:VIEW','MDM:SUPPLIER:CREATE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('PURCHASE_SUPERVISOR','PURCHASE_DIRECTOR')
  AND p.perm_code IN ('MDM:SUPPLIER:VIEW','MDM:SUPPLIER:CREATE');

-- 研发工艺：物料与 BOM 的核心使用者
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('PRODUCT_ENGINEER','PROCESS_ENGINEER')
  AND p.perm_code IN ('MDM:MATERIAL:VIEW','MDM:MATERIAL:CREATE',
                      'MDM:BOM:VIEW','MDM:BOM:CREATE','MDM:BOM:CHANGE',
                      'MDM:ROUTING:VIEW','MDM:ROUTING:UPDATE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('PROCESS_SUPERVISOR','RND_MANAGER')
  AND p.perm_code IN ('MDM:MATERIAL:VIEW','MDM:MATERIAL:CREATE',
                      'MDM:BOM:VIEW','MDM:BOM:CREATE',
                      'MDM:ROUTING:VIEW','MDM:ROUTING:UPDATE');

-- 生产：物料查看 + BOM 查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('PROD_PLANNER','PROD_SUPERVISOR','PROD_MANAGER','PROD_CLERK')
  AND p.perm_code IN ('MDM:MATERIAL:VIEW','MDM:BOM:VIEW','MDM:ROUTING:VIEW',
                      'MDM:DISTRIBUTE:VIEW');

-- 财务：物料查看（成本核算需要）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('COST_ACCOUNTANT','FIN_SUPERVISOR','FINANCE_DIRECTOR')
  AND p.perm_code IN ('MDM:MATERIAL:VIEW','MDM:BOM:VIEW');

-- 质量：物料查看（检验标准）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('QC_INSPECTOR','QUALITY_ENGINEER','QUALITY_SUPERVISOR')
  AND p.perm_code IN ('MDM:MATERIAL:VIEW');

-- ============================================================
-- 二、审批中心权限
--   规则：主管及以上可审批；执行岗只能看待办列表
-- ============================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN (
        'SALES_SUPERVISOR','SALES_DIRECTOR',
        'COST_ACCOUNTANT','AR_ACCOUNTANT','AP_ACCOUNTANT','GL_ACCOUNTANT',
        'FIN_SUPERVISOR','FINANCE_DIRECTOR',
        'PURCHASE_SUPERVISOR','PURCHASE_DIRECTOR','SQE',
        'PROCESS_SUPERVISOR','RND_MANAGER',
        'PROD_PLANNER','PROD_SUPERVISOR','PROD_MANAGER',
        'QUALITY_SUPERVISOR','EQUIPMENT_SUPERVISOR','WAREHOUSE_SUPERVISOR',
        'ADMIN')
  AND p.perm_code IN ('WF:TASK:VIEW','WF:TASK:APPROVE');

-- 执行岗：只看得见自己的待办（如被退回的申请）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('SALES_REP','SALES_ASSISTANT','BUYER','PROCESS_ENGINEER',
                      'PRODUCT_ENGINEER','MDM_ADMIN','QC_INSPECTOR')
  AND p.perm_code = 'WF:TASK:VIEW';

-- ============================================================
-- 三、CRM 权限
-- ============================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'SALES_REP'
  AND p.perm_code IN ('CRM:OPPORTUNITY:VIEW','CRM:OPPORTUNITY:CREATE','CRM:OPPORTUNITY:STAGE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'SALES_ASSISTANT'
  AND p.perm_code IN ('CRM:OPPORTUNITY:VIEW');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'SALES_SUPERVISOR'
  AND p.perm_code IN ('CRM:OPPORTUNITY:VIEW','CRM:OPPORTUNITY:CREATE',
                      'CRM:OPPORTUNITY:STAGE','CRM:OPPORTUNITY:CONVERT');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'SALES_DIRECTOR'
  AND p.perm_code LIKE 'CRM:%';

-- ============================================================
-- 四、ERP 权限
-- ============================================================
-- 销售侧：订单查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('SALES_REP','SALES_SUPERVISOR','SALES_DIRECTOR')
  AND p.perm_code IN ('ERP:SALES_ORDER:VIEW','ERP:SALES_ORDER:CREATE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('SALES_SUPERVISOR','SALES_DIRECTOR')
  AND p.perm_code = 'ERP:SALES_ORDER:CONFIRM';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'SALES_ASSISTANT'
  AND p.perm_code IN ('ERP:SALES_ORDER:VIEW','ERP:SALES_ORDER:CREATE');

-- 生产侧
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'PROD_CLERK'
  AND p.perm_code IN ('ERP:PROD_ORDER:VIEW','ERP:PROD_ORDER:CREATE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'PROD_PLANNER'
  AND p.perm_code IN ('ERP:SALES_ORDER:VIEW','ERP:PROD_ORDER:VIEW',
                      'ERP:PROD_ORDER:CREATE','ERP:PROD_ORDER:RELEASE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('PROD_SUPERVISOR','PROD_MANAGER')
  AND p.perm_code LIKE 'ERP:PROD_ORDER:%' OR
      (r.role_code IN ('PROD_SUPERVISOR','PROD_MANAGER') AND p.perm_code = 'ERP:SALES_ORDER:VIEW');

-- 财务侧
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'CASHIER'
  AND p.perm_code IN ('ERP:RECEIVABLE:VIEW','ERP:RECEIVABLE:RECEIVE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'AR_ACCOUNTANT'
  AND p.perm_code IN ('ERP:RECEIVABLE:VIEW','ERP:RECEIVABLE:RECEIVE',
                      'ERP:SALES_ORDER:VIEW','ERP:VOUCHER:VIEW');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'AP_ACCOUNTANT'
  AND p.perm_code IN ('ERP:PAYABLE:VIEW','ERP:PAYABLE:PAY',
                      'ERP:VOUCHER:VIEW','ERP:VOUCHER:CREATE');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'COST_ACCOUNTANT'
  AND p.perm_code IN ('ERP:VOUCHER:VIEW','ERP:PROD_ORDER:VIEW','ERP:SALES_ORDER:VIEW');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code IN ('GL_ACCOUNTANT','FIN_SUPERVISOR')
  AND p.perm_code IN ('ERP:VOUCHER:VIEW','ERP:VOUCHER:CREATE','ERP:VOUCHER:AUDIT');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'FINANCE_DIRECTOR'
  AND p.perm_code LIKE 'ERP:%';

-- ============================================================
-- 五、MES / WMS / SRM / QMS / EAM / PLM / 能源
-- ============================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE (r.role_code = 'TEAM_LEADER' AND p.perm_code IN
        ('MES:WORK_ORDER:VIEW','MES:WORK_ORDER:START','MES:WORK_REPORT:CREATE'))
   OR (r.role_code = 'WORKSHOP_CHIEF' AND p.perm_code LIKE 'MES:%')
   OR (r.role_code IN ('PROD_SUPERVISOR','PROD_MANAGER') AND p.perm_code LIKE 'MES:%');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE (r.role_code = 'WAREHOUSE_KEEPER' AND p.perm_code IN
        ('WMS:INVENTORY:VIEW','WMS:STOCK:IN','WMS:STOCK:OUT','WMS:STOCK:CHECK'))
   OR (r.role_code = 'WAREHOUSE_SUPERVISOR' AND p.perm_code LIKE 'WMS:%');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE (r.role_code = 'BUYER' AND p.perm_code IN
        ('SRM:SUPPLIER:VIEW','SRM:PURCHASE:VIEW','SRM:PURCHASE:CREATE','SRM:DELIVERY:CREATE'))
   OR (r.role_code = 'SQE' AND p.perm_code IN
        ('SRM:SUPPLIER:VIEW','SRM:SUPPLIER:EVALUATE'))
   OR (r.role_code = 'PURCHASE_SUPERVISOR' AND p.perm_code LIKE 'SRM:%')
   OR (r.role_code = 'PURCHASE_DIRECTOR' AND p.perm_code LIKE 'SRM:%');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE (r.role_code = 'QC_INSPECTOR' AND p.perm_code IN
        ('QMS:INSPECTION:VIEW','QMS:INSPECTION:CREATE'))
   OR (r.role_code = 'QUALITY_ENGINEER' AND p.perm_code LIKE 'QMS:%')
   OR (r.role_code = 'QUALITY_SUPERVISOR' AND p.perm_code LIKE 'QMS:%');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE (r.role_code = 'EQUIPMENT_REPAIRMAN' AND p.perm_code IN
        ('EAM:EQUIPMENT:VIEW','EAM:FAULT:CREATE','EAM:REPAIR:FINISH','EAM:INSPECTION:CREATE'))
   OR (r.role_code = 'EQUIPMENT_SUPERVISOR' AND p.perm_code LIKE 'EAM:%')
   OR (r.role_code = 'WORKSHOP_CHIEF' AND p.perm_code IN
        ('EAM:EQUIPMENT:VIEW','EAM:FAULT:CREATE'));

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE (r.role_code IN ('PRODUCT_ENGINEER','PROCESS_ENGINEER') AND p.perm_code IN
        ('PLM:PRODUCT:VIEW','PLM:PRODUCT:CREATE','PLM:ECN:CREATE'))
   OR (r.role_code IN ('PROCESS_SUPERVISOR','RND_MANAGER') AND p.perm_code LIKE 'PLM:%');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE (r.role_code = 'ENERGY_ADMIN' AND p.perm_code LIKE 'ENERGY:%')
   OR (r.role_code = 'EQUIPMENT_SUPERVISOR' AND p.perm_code = 'ENERGY:USAGE:VIEW');

-- ============================================================
-- 六、数据分析师：全系统只读（演示 AI 分析时用）
-- ============================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'DATA_ANALYST'
  AND (p.perm_code LIKE '%:VIEW' OR p.perm_code IN
       ('MDM:DISTRIBUTE:VIEW','ENERGY:ANALYSIS:VIEW','WF:TASK:VIEW'));

-- ============================================================
-- 七、验证输出
-- ============================================================
SELECT r.role_code AS 角色, r.role_name AS 名称, COUNT(rp.id) AS 权限数
FROM sys_role r
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
GROUP BY r.id ORDER BY r.sort_no;

SELECT CONCAT('角色-权限映射总数: ', COUNT(*)) AS 汇总 FROM sys_role_permission;
