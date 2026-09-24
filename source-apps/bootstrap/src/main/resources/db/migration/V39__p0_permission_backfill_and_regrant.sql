-- ============================================================
-- V39 —— 权限补齐与授权追认（计划 00 · F1-02 / F1-03）
--
-- 背景（两个独立成因，同一个后果「字典有码、无人有权」）：
--   1) 有 6 个权限码被 @PreAuthorize 引用，但任何 SQL 中都没有定义——它们
--      永远不可能被授予，相关端点只有带 or hasRole('ADMIN') 的才能被 ADMIN 调通；
--   2) 授权用的是 LIKE 'QMS:%' 形式的通配语句，位于 db-init 通道
--      （V1–V11，执行早于 Flyway）。V37/V38 之后新增的权限码赶不上那批通配，
--      于是「码在字典里、角色却没有」。
--
-- 本迁移做两件事：
--   1) 补齐 6 个未定义权限码；
--   2) 重跑各系统通配授权，把后加的码一并授出去。
--
-- 幂等：权限码用 INSERT IGNORE（uk_perm_code），授权依赖 uk_role_perm。
-- 命名规范见 docs/permission-conventions.md（F1-04）。
-- ============================================================

-- ------------------------------------------------------------
-- 一、补齐 6 个被引用但未定义的权限码
-- ------------------------------------------------------------
INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_group,perm_type) VALUES
('MDM:MATERIAL:PUBLISH','发布物料','mdm','主数据发布','BUTTON'),
('MDM:BOM:PUBLISH','发布BOM','mdm','主数据发布','BUTTON'),
('MDM:PRODUCT:CREATE','创建产品','mdm','产品主数据','BUTTON'),
('MDM:PRODUCT:UPDATE','修改产品','mdm','产品主数据','BUTTON'),
('PLM:PRODUCT:UPDATE','维护产品结构','plm','产品工程','BUTTON'),
('PLM:DOCUMENT:UPDATE','维护受控文档','plm','文档管理','BUTTON');

-- ------------------------------------------------------------
-- 二、新增码的显式授权（通配授权覆盖不到的窄口径角色）
-- ------------------------------------------------------------
-- 发布类：主数据管理员
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('MDM_ADMIN','ADMIN')
  AND p.perm_code IN ('MDM:MATERIAL:PUBLISH','MDM:BOM:PUBLISH');

-- 产品与文档维护：研发工艺侧（与 09_role_permission.sql 的 MDM 产品授权口径一致）
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PRODUCT_ENGINEER','PROCESS_ENGINEER')
  AND p.perm_code IN ('MDM:PRODUCT:CREATE','MDM:PRODUCT:UPDATE',
                      'PLM:PRODUCT:UPDATE','PLM:DOCUMENT:UPDATE');
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('PROCESS_SUPERVISOR','RND_MANAGER')
  AND p.perm_code IN ('MDM:PRODUCT:CREATE','MDM:PRODUCT:UPDATE',
                      'PLM:PRODUCT:UPDATE','PLM:DOCUMENT:UPDATE');

-- ------------------------------------------------------------
-- 三、通配授权追认
--
-- 与 09_role_permission.sql 的角色口径逐条对齐，只是重跑一遍以覆盖
-- V37/V38（及本次）之后新增的权限码。INSERT IGNORE + uk_role_perm 保证幂等。
-- ------------------------------------------------------------
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE (r.role_code = 'SALES_DIRECTOR'    AND p.perm_code LIKE 'CRM:%')
   OR (r.role_code = 'FINANCE_DIRECTOR'  AND p.perm_code LIKE 'ERP:%')
   OR (r.role_code IN ('QUALITY_ENGINEER','QUALITY_SUPERVISOR') AND p.perm_code LIKE 'QMS:%')
   OR (r.role_code IN ('WORKSHOP_CHIEF','PROD_SUPERVISOR','PROD_MANAGER') AND p.perm_code LIKE 'MES:%')
   OR (r.role_code = 'WAREHOUSE_SUPERVISOR' AND p.perm_code LIKE 'WMS:%')
   OR (r.role_code IN ('PURCHASE_SUPERVISOR','PURCHASE_DIRECTOR') AND p.perm_code LIKE 'SRM:%')
   OR (r.role_code = 'EQUIPMENT_SUPERVISOR' AND p.perm_code LIKE 'EAM:%')
   OR (r.role_code IN ('PROCESS_SUPERVISOR','RND_MANAGER') AND p.perm_code LIKE 'PLM:%')
   OR (r.role_code = 'ENERGY_ADMIN' AND p.perm_code LIKE 'ENERGY:%');

-- ------------------------------------------------------------
-- 四、补授：窄口径权限码给作业岗
--
-- 这些码由 V37/V38 定义，但既有通配语句只给到主管/经理级角色，
-- 作业岗（检验员、库管员、采购员等）拿不到，导致端点退化为「只有 ADMIN 能过」。
-- 口径与 09_role_permission.sql 中同名角色的既有授权保持一致。
-- ------------------------------------------------------------
-- 检验员：判定与不合格处置
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'QC_INSPECTOR'
  AND p.perm_code IN ('QMS:INSPECTION:JUDGE','QMS:DEFECT:JUDGE');

-- 质量工程师：不合格处置与 CAPA/8D 关闭
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'QUALITY_ENGINEER'
  AND p.perm_code IN ('QMS:NCR:DISPOSE','QMS:CAPA:CLOSE','QMS:8D:CLOSE','QMS:DEFECT:DISPOSE');

-- 库管员：库存状态操作与盘点过账（WMS:COUNT:POST / WMS:INVENTORY:ACTION / WMS:TRANSFER:EXECUTE）
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'WAREHOUSE_KEEPER'
  AND p.perm_code IN ('WMS:INVENTORY:ACTION','WMS:COUNT:POST',
                      'WMS:TRANSFER:EXECUTE','WMS:STOCK:ADJUST','WMS:RECEIPT:CREATE');

-- 采购员：询价定标与 ASN 创建
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'BUYER'
  AND p.perm_code IN ('SRM:RFQ:AWARD','SRM:ASN:CREATE');

-- 生产计划员：MRP 运行与生产订单下达/关闭（ERP 侧原先只授到 ERP:PROD_ORDER:%）
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'PROD_PLANNER'
  AND p.perm_code IN ('ERP:MRP:RUN','ERP:PRODUCTION_ORDER:RELEASE');

-- 销售主管/总监：信用例外与 ATP 承诺
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code IN ('SALES_SUPERVISOR','SALES_DIRECTOR')
  AND p.perm_code IN ('ERP:CREDIT:APPROVE','ERP:ATP:COMMIT','CRM:CONTRACT:ACTIVATE',
                      'CRM:QUOTATION:SUBMIT','CRM:OPPORTUNITY:PRICE','CRM:LEAD:CREATE',
                      'CRM:LEAD:ASSIGN');

-- 财务岗：收付款与结算
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE (r.role_code = 'AR_ACCOUNTANT'  AND p.perm_code = 'ERP:FINANCE:RECEIPT')
   OR (r.role_code = 'AP_ACCOUNTANT'  AND p.perm_code = 'ERP:FINANCE:SETTLE')
   OR (r.role_code = 'COST_ACCOUNTANT' AND p.perm_code = 'ERP:FINANCE:COST_VIEW');

-- 设备主管：故障审批与点检计划
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'EQUIPMENT_SUPERVISOR'
  AND p.perm_code IN ('EAM:FAULT:APPROVE','EAM:INSPECTION:PLAN');

-- 能源管理员：能耗录入
INSERT IGNORE INTO mfg_auth.sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM mfg_auth.sys_role r JOIN mfg_auth.sys_permission p
WHERE r.role_code = 'ENERGY_ADMIN'
  AND p.perm_code IN ('ENERGY:USAGE:CREATE','ENERGY:ANALYSIS:VIEW');
