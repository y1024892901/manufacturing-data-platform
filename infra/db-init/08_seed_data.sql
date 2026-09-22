-- ============================================================
-- 08_seed_data.sql —— 种子数据
--   1. 组织架构（部门树）
--   2. 36 个岗位角色（模拟大厂分部门分级）
--   3. 权限点
--   4. 36 个演示账号
--   5. 用户角色 / 系统访问权 / 数据范围
--   6. 7 条审批流程（含主管/经理分级）
--   7. 15 条治理规则
--   8. 指标口径
--   9. 来源系统登记
--
-- 密码统一 Test@123456（BCrypt $2a$ 前缀，Spring Security 原生支持）
-- 幂等: 使用 ON DUPLICATE KEY UPDATE
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 一、组织架构（部门树，供数据范围使用）
-- ============================================================
USE mfg_auth;

CREATE TABLE IF NOT EXISTS sys_dept (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    dept_code     VARCHAR(32)  NOT NULL COMMENT '部门编码: SALES/PURCHASE/...',
    dept_name     VARCHAR(100) NOT NULL COMMENT '部门名称',
    parent_code   VARCHAR(32)  NULL     COMMENT '上级部门',
    dept_level    TINYINT      NOT NULL DEFAULT 1,
    manager_user  VARCHAR(32)  NULL     COMMENT '部门负责人账号',
    sort_no       INT          NOT NULL DEFAULT 0,
    is_enabled    TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_code (dept_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='部门（组织架构；数据范围 DEPT 级过滤依据）';

INSERT INTO sys_dept (dept_code, dept_name, parent_code, dept_level, manager_user, sort_no) VALUES
('HQ',        '集团总部',       NULL,   1, 'admin',         0),
('SALES',     '销售与市场中心', 'HQ',   2, 'lifang',       10),
('FINANCE',   '财务与成本中心', 'HQ',   2, 'zhouba',       20),
('PURCHASE',  '供应链与采购中心','HQ',  2, 'wuban',        30),
('RND',       '研发与工艺中心', 'HQ',   2, 'zhaoliu',      40),
('PROD',      '生产制造中心',   'HQ',   2, 'sunqi',        50),
('QUALITY',   '质量管理中心',   'HQ',   2, 'zhengshi',     60),
('EQUIPMENT', '设备与能源中心', 'HQ',   2, 'fengyi',       70),
('WAREHOUSE', '仓储物流中心',   'HQ',   2, 'chener',       80),
('IT',        '信息技术中心',   'HQ',   2, 'admin',        90)
ON DUPLICATE KEY UPDATE dept_name=VALUES(dept_name), manager_user=VALUES(manager_user);

-- ============================================================
-- 二、36 个岗位角色（分部门分级）
--     设计原则: 每个部门都有「执行岗 → 主管 → 经理」三级
--               审批链按金额与影响面分级，模拟真实企业的内控
-- ============================================================
INSERT INTO sys_role (role_code, role_name, description, is_system, sort_no) VALUES
-- ---------- 销售与市场中心 ----------
('SALES_REP',          '销售代表',   '建商机、建客户申请；只能看自己负责的客户',           1, 10),
('SALES_ASSISTANT',    '销售助理',   '订单录入与跟单，无审批权',                          1, 11),
('SALES_SUPERVISOR',   '销售主管',   '审批客户新增、商机转化；看本部门客户',              1, 12),
('SALES_DIRECTOR',     '销售总监',   '审批价格折扣与重大商机；看全部客户',                1, 13),
-- ---------- 财务与成本中心 ----------
('COST_ACCOUNTANT',    '成本会计',   'BOM成本审核、成本核算',                             1, 20),
('AP_ACCOUNTANT',      '应付会计',   '供应商付款、采购对账',                              1, 21),
('AR_ACCOUNTANT',      '应收会计',   '客户信用核查、回款核销',                            1, 22),
('GL_ACCOUNTANT',      '总账会计',   '总账核算、凭证审核',                                1, 23),
('CASHIER',            '出纳',       '回款登记、银行对账',                                1, 24),
('FIN_SUPERVISOR',     '财务主管',   '一般财务审批（小额）',                              1, 25),
('FINANCE_DIRECTOR',   '财务总监',   '信用额度、重大财务审批',                            1, 26),
-- ---------- 供应链与采购中心 ----------
('BUYER',              '采购员',     '采购订单、到货登记；只能看自己下的单',              1, 30),
('SQE',                '供应商质量工程师','供应商资质审核、来料质量',                      1, 31),
('PURCHASE_SUPERVISOR','采购主管',   '审批采购订单、供应商准入',                          1, 32),
('PURCHASE_DIRECTOR',  '采购总监',   '战略供应商、大额采购审批',                          1, 33),
-- ---------- 研发与工艺中心 ----------
('PRODUCT_ENGINEER',   '产品工程师', '产品数据、EBOM 维护',                               1, 40),
('PROCESS_ENGINEER',   '工艺工程师', '工艺路线、MBOM 提交',                               1, 41),
('PROCESS_SUPERVISOR', '工艺主管',   '审批 BOM 变更、工艺路线',                           1, 42),
('RND_MANAGER',        '研发经理',   '新产品立项、物料新增批准',                          1, 43),
-- ---------- 生产制造中心 ----------
('PROD_CLERK',         '生产文员',   '数据录入，无审批权',                                1, 50),
('PROD_PLANNER',       '生产计划员', '排产、下达生产订单',                                1, 51),
('PROD_SUPERVISOR',    '生产主管',   '审批生产订单下达、BOM 生产可行性',                  1, 52),
('PROD_MANAGER',       '生产经理',   '产能规划、交期承诺、重大生产决策',                  1, 53),
('WORKSHOP_CHIEF',     '车间主任',   '报工、报故障、异常处理；只看本车间',                1, 54),
('TEAM_LEADER',        '班组长',     '班组报工、执行任务',                                1, 55),
-- ---------- 质量管理中心 ----------
('QC_INSPECTOR',       '质检员',     '登记检验结果',                                      1, 60),
('QUALITY_ENGINEER',   '质量工程师', '不合格品判定、质量改进',                            1, 61),
('QUALITY_SUPERVISOR', '质量主管',   '供应商资质审批、重大质量判定',                      1, 62),
-- ---------- 设备与能源中心 ----------
('EQUIPMENT_REPAIRMAN','设备维修工', '维修记录、点检',                                    1, 70),
('EQUIPMENT_SUPERVISOR','设备主管',  '审批维修工单、点检计划',                            1, 71),
('ENERGY_ADMIN',       '能源管理员', '能耗数据、节能分析',                                1, 72),
-- ---------- 仓储物流中心 ----------
('WAREHOUSE_KEEPER',   '库管员',     '出入库、盘点',                                      1, 80),
('WAREHOUSE_SUPERVISOR','仓储主管',  '审批盘盈亏、库存调拨',                              1, 81),
-- ---------- 平台与系统 ----------
('MDM_ADMIN',          '主数据管理员','主数据录入与维护，不参与审批',                      1, 90),
('DATA_ANALYST',       '数据分析师', '数仓分析、报表开发、AI 分析',                       1, 91),
('ADMIN',              '系统管理员', '平台全部权限（演示兜底）',                          1, 99)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name), description=VALUES(description), sort_no=VALUES(sort_no);

-- ============================================================
-- 三、权限点
-- ============================================================
INSERT INTO sys_permission (perm_code, perm_name, system_code, perm_group) VALUES
-- 主数据平台
('MDM:CUSTOMER:VIEW','查看客户','mdm','主数据-客户'),
('MDM:CUSTOMER:CREATE','新增客户','mdm','主数据-客户'),
('MDM:CUSTOMER:UPDATE','修改客户','mdm','主数据-客户'),
('MDM:SUPPLIER:VIEW','查看供应商','mdm','主数据-供应商'),
('MDM:SUPPLIER:CREATE','新增供应商','mdm','主数据-供应商'),
('MDM:MATERIAL:VIEW','查看物料','mdm','主数据-物料'),
('MDM:MATERIAL:CREATE','新增物料','mdm','主数据-物料'),
('MDM:MATERIAL:UPDATE','修改物料','mdm','主数据-物料'),
('MDM:BOM:VIEW','查看BOM','mdm','主数据-BOM'),
('MDM:BOM:CREATE','新增BOM','mdm','主数据-BOM'),
('MDM:BOM:CHANGE','BOM变更申请','mdm','主数据-BOM'),
('MDM:ROUTING:VIEW','查看工艺路线','mdm','主数据-工艺'),
('MDM:ROUTING:UPDATE','维护工艺路线','mdm','主数据-工艺'),
('MDM:DISTRIBUTE:VIEW','查看分发状态','mdm','主数据-分发'),
('MDM:DISTRIBUTE:RETRY','重新分发','mdm','主数据-分发'),
-- 审批中心
('WF:TASK:VIEW','查看待办','wf','审批中心'),
('WF:TASK:APPROVE','审批操作','wf','审批中心'),
-- CRM
('CRM:OPPORTUNITY:VIEW','查看商机','crm','商机管理'),
('CRM:OPPORTUNITY:CREATE','新增商机','crm','商机管理'),
('CRM:OPPORTUNITY:STAGE','推进商机阶段','crm','商机管理'),
('CRM:OPPORTUNITY:CONVERT','商机转化订单','crm','商机管理'),
('CRM:OPPORTUNITY:PRICE','价格折扣审批','crm','商机管理'),
-- ERP
('ERP:SALES_ORDER:VIEW','查看销售订单','erp','销售管理'),
('ERP:SALES_ORDER:CREATE','创建销售订单','erp','销售管理'),
('ERP:SALES_ORDER:CONFIRM','确认销售订单','erp','销售管理'),
('ERP:PROD_ORDER:VIEW','查看生产订单','erp','生产管理'),
('ERP:PROD_ORDER:CREATE','创建生产订单','erp','生产管理'),
('ERP:PROD_ORDER:RELEASE','下达生产','erp','生产管理'),
('ERP:PROD_ORDER:CLOSE','关闭生产订单','erp','生产管理'),
('ERP:VOUCHER:VIEW','查看财务凭证','erp','财务核算'),
('ERP:VOUCHER:CREATE','录入凭证','erp','财务核算'),
('ERP:VOUCHER:AUDIT','凭证审核','erp','财务核算'),
('ERP:RECEIVABLE:VIEW','查看应收','erp','财务核算'),
('ERP:RECEIVABLE:RECEIVE','登记回款','erp','财务核算'),
('ERP:PAYABLE:VIEW','查看应付','erp','财务核算'),
('ERP:PAYABLE:PAY','付款处理','erp','财务核算'),
-- MES
('MES:WORK_ORDER:VIEW','查看工单','mes','生产执行'),
('MES:WORK_ORDER:START','开工','mes','生产执行'),
('MES:WORK_REPORT:CREATE','报工','mes','生产执行'),
('MES:WORK_ORDER:FINISH','完工确认','mes','生产执行'),
('MES:WORK_ORDER:PAUSE','暂停工单','mes','生产执行'),
-- WMS
('WMS:INVENTORY:VIEW','查看库存','wms','仓储管理'),
('WMS:STOCK:IN','入库','wms','仓储管理'),
('WMS:STOCK:OUT','出库','wms','仓储管理'),
('WMS:STOCK:CHECK','盘点','wms','仓储管理'),
('WMS:STOCK:ADJUST','库存调整审批','wms','仓储管理'),
-- SRM
('SRM:SUPPLIER:VIEW','查看供应商','srm','供应商管理'),
('SRM:SUPPLIER:EVALUATE','供应商评估','srm','供应商管理'),
('SRM:PURCHASE:VIEW','查看采购订单','srm','采购管理'),
('SRM:PURCHASE:CREATE','创建采购订单','srm','采购管理'),
('SRM:PURCHASE:APPROVE','审批采购订单','srm','采购管理'),
('SRM:DELIVERY:CREATE','登记到货','srm','采购管理'),
-- QMS
('QMS:INSPECTION:VIEW','查看检验单','qms','质量管理'),
('QMS:INSPECTION:CREATE','登记检验结果','qms','质量管理'),
('QMS:DEFECT:JUDGE','不合格品判定','qms','质量管理'),
('QMS:DEFECT:DISPOSE','不合格品处置','qms','质量管理'),
-- EAM
('EAM:EQUIPMENT:VIEW','查看设备','eam','设备管理'),
('EAM:FAULT:CREATE','报故障','eam','设备管理'),
('EAM:FAULT:APPROVE','审批维修工单','eam','设备管理'),
('EAM:REPAIR:FINISH','维修完工','eam','设备管理'),
('EAM:INSPECTION:CREATE','点检','eam','设备管理'),
('EAM:INSPECTION:PLAN','点检计划审批','eam','设备管理'),
-- PLM
('PLM:PRODUCT:VIEW','查看产品','plm','产品管理'),
('PLM:PRODUCT:CREATE','新增产品','plm','产品管理'),
('PLM:ECN:CREATE','创建工程变更单','plm','产品管理'),
('PLM:ECN:APPROVE','审批工程变更','plm','产品管理'),
-- 能源
('ENERGY:USAGE:VIEW','查看能耗','energy','能源管理'),
('ENERGY:USAGE:CREATE','录入能耗','energy','能源管理'),
('ENERGY:ANALYSIS:VIEW','能耗分析','energy','能源管理')
ON DUPLICATE KEY UPDATE perm_name=VALUES(perm_name), perm_group=VALUES(perm_group);

-- ============================================================
-- 四、36 个演示账号
--     命名: 姓全拼 + 名首字母（重名自动区分），模拟大厂工号风格
--     密码: Test@123456
-- ============================================================
SET @pwd := '$2a$10$wMGsPx.8vZQdPRB46K3G4.dfFjIafjLx2hhhOyaTbB3y7fBId/AsK';

INSERT INTO sys_user (username, password_hash, real_name, dept_code, position_name, is_enabled, is_demo_account) VALUES
-- 平台
('admin',      @pwd, '系统管理员', 'IT',        '平台管理员',     1, 1),
('yangming',   @pwd, '杨明',      'IT',        '主数据管理员',   1, 1),
('xujing',     @pwd, '徐静',      'IT',        '数据分析师',     1, 1),
-- 销售与市场中心
('zhangwei',   @pwd, '张伟',      'SALES',     '销售代表',       1, 1),
('liuyang',    @pwd, '刘洋',      'SALES',     '销售代表',       1, 1),
('chenjing',   @pwd, '陈静',      'SALES',     '销售助理',       1, 1),
('wangfang',   @pwd, '王芳',      'SALES',     '销售主管',       1, 1),
('lifang',     @pwd, '李芳',      'SALES',     '销售总监',       1, 1),
-- 财务与成本中心
('zhouba',     @pwd, '周八',      'FINANCE',   '财务总监',       1, 1),
('wuqian',     @pwd, '吴倩',      'FINANCE',   '财务主管',       1, 1),
('zhengshuang',@pwd, '郑爽',      'FINANCE',   '成本会计',       1, 1),
('fenglin',    @pwd, '冯琳',      'FINANCE',   '应收会计',       1, 1),
('jiangnan',   @pwd, '蒋楠',      'FINANCE',   '应付会计',       1, 1),
('hexin',      @pwd, '何欣',      'FINANCE',   '总账会计',       1, 1),
('shenlu',     @pwd, '沈璐',      'FINANCE',   '出纳',           1, 1),
-- 供应链与采购中心
('wuban',      @pwd, '吴班',      'PURCHASE',  '采购总监',       1, 1),
('xuqiang',    @pwd, '徐强',      'PURCHASE',  '采购主管',       1, 1),
('sunli',      @pwd, '孙丽',      'PURCHASE',  '采购员',         1, 1),
('machao',     @pwd, '马超',      'PURCHASE',  '供应商质量工程师',1, 1),
-- 研发与工艺中心
('zhaoliu',    @pwd, '赵六',      'RND',       '工艺工程师',     1, 1),
('zhoutao',    @pwd, '周涛',      'RND',       '工艺主管',       1, 1),
('guolei',     @pwd, '郭磊',      'RND',       '产品工程师',     1, 1),
-- 生产制造中心
('linfeng',    @pwd, '林峰',      'PROD',      '生产经理',       1, 1),
('sunqi',      @pwd, '孙七',      'PROD',      '生产计划员',     1, 1),
('yangfan',    @pwd, '杨帆',      'PROD',      '生产主管',       1, 1),
('fengyi',     @pwd, '冯一',      'PROD',      '车间主任',       1, 1),
('dengchao',   @pwd, '邓超',      'PROD',      '班组长',         1, 1),
-- 质量管理中心
('zhengshi',   @pwd, '郑十',      'QUALITY',   '质量工程师',     1, 1),
('daili',      @pwd, '戴丽',      'QUALITY',   '质量主管',       1, 1),
('qianduo',    @pwd, '钱多',      'QUALITY',   '质检员',         1, 1),
-- 设备与能源中心
('chenfeng',   @pwd, '陈峰',      'EQUIPMENT', '设备主管',       1, 1),
('chujian',    @pwd, '褚健',      'EQUIPMENT', '设备维修工',     1, 1),
('weilan',     @pwd, '卫兰',      'EQUIPMENT', '能源管理员',     1, 1),
-- 仓储物流中心
('chener',     @pwd, '陈二',      'WAREHOUSE', '库管员',         1, 1),
('youtao',     @pwd, '游涛',      'WAREHOUSE', '仓储主管',       1, 1),
-- 补足销售/研发缺口
('qianyi',     @pwd, '钱一',      'SALES',     '销售代表',       1, 1)
ON DUPLICATE KEY UPDATE real_name=VALUES(real_name), dept_code=VALUES(dept_code), position_name=VALUES(position_name);

-- ============================================================
-- 五、用户 ↔ 角色
-- ============================================================
DELETE FROM sys_user_role;
INSERT INTO sys_user_role (user_id, role_id, granted_by)
SELECT u.id, r.id, 'system'
FROM sys_user u JOIN sys_role r ON (
    (u.username='admin'        AND r.role_code='ADMIN') OR
    (u.username='yangming'     AND r.role_code='MDM_ADMIN') OR
    (u.username='xujing'       AND r.role_code='DATA_ANALYST') OR
    -- 销售
    (u.username IN ('zhangwei','liuyang','qianyi') AND r.role_code='SALES_REP') OR
    (u.username='chenjing'     AND r.role_code='SALES_ASSISTANT') OR
    (u.username='wangfang'     AND r.role_code='SALES_SUPERVISOR') OR
    (u.username='lifang'       AND r.role_code='SALES_DIRECTOR') OR
    -- 财务
    (u.username='zhouba'       AND r.role_code='FINANCE_DIRECTOR') OR
    (u.username='wuqian'       AND r.role_code='FIN_SUPERVISOR') OR
    (u.username='zhengshuang'  AND r.role_code='COST_ACCOUNTANT') OR
    (u.username='fenglin'      AND r.role_code='AR_ACCOUNTANT') OR
    (u.username='jiangnan'     AND r.role_code='AP_ACCOUNTANT') OR
    (u.username='hexin'        AND r.role_code='GL_ACCOUNTANT') OR
    (u.username='shenlu'       AND r.role_code='CASHIER') OR
    -- 采购
    (u.username='wuban'        AND r.role_code='PURCHASE_DIRECTOR') OR
    (u.username='xuqiang'      AND r.role_code='PURCHASE_SUPERVISOR') OR
    (u.username='sunli'        AND r.role_code='BUYER') OR
    (u.username='machao'       AND r.role_code='SQE') OR
    -- 研发工艺
    (u.username='zhaoliu'      AND r.role_code='PROCESS_ENGINEER') OR
    (u.username='zhoutao'      AND r.role_code='PROCESS_SUPERVISOR') OR
    (u.username='guolei'       AND r.role_code='PRODUCT_ENGINEER') OR
    -- 生产
    (u.username='linfeng'      AND r.role_code='PROD_MANAGER') OR
    (u.username='sunqi'        AND r.role_code='PROD_PLANNER') OR
    (u.username='yangfan'      AND r.role_code='PROD_SUPERVISOR') OR
    (u.username='fengyi'       AND r.role_code='WORKSHOP_CHIEF') OR
    (u.username='dengchao'     AND r.role_code='TEAM_LEADER') OR
    -- 质量
    (u.username='zhengshi'     AND r.role_code='QUALITY_ENGINEER') OR
    (u.username='daili'        AND r.role_code='QUALITY_SUPERVISOR') OR
    (u.username='qianduo'      AND r.role_code='QC_INSPECTOR') OR
    -- 设备能源
    (u.username='chenfeng'     AND r.role_code='EQUIPMENT_SUPERVISOR') OR
    (u.username='chujian'      AND r.role_code='EQUIPMENT_REPAIRMAN') OR
    (u.username='weilan'       AND r.role_code='ENERGY_ADMIN') OR
    -- 仓储
    (u.username='chener'       AND r.role_code='WAREHOUSE_KEEPER') OR
    (u.username='youtao'       AND r.role_code='WAREHOUSE_SUPERVISOR')
);
-- 一人多岗（模拟大厂「兼岗」）
INSERT INTO sys_user_role (user_id, role_id, granted_by)
SELECT u.id, r.id, 'system'
FROM sys_user u JOIN sys_role r ON (
    (u.username='zhoutao'  AND r.role_code='RND_MANAGER') OR      -- 工艺主管兼研发经理
    (u.username='wuqian'   AND r.role_code='GL_ACCOUNTANT') OR    -- 财务主管兼总账
    (u.username='yangfan'  AND r.role_code='PROD_CLERK') OR       -- 生产主管兼文员
    (u.username='guolei'   AND r.role_code='MDM_ADMIN')           -- 产品工程师兼主数据
)
ON DUPLICATE KEY UPDATE granted_by=VALUES(granted_by);

-- ============================================================
-- 六、用户可访问系统（第一层权限）
-- ============================================================
DELETE FROM sys_user_system;
INSERT INTO sys_user_system (user_id, system_code)
SELECT u.id, s.system_code
FROM sys_user u
JOIN (
    -- 平台
    SELECT 'admin' AS username,'mdm' AS system_code UNION ALL
    SELECT 'admin','crm' UNION ALL SELECT 'admin','erp' UNION ALL
    SELECT 'admin','mes' UNION ALL SELECT 'admin','wms' UNION ALL
    SELECT 'admin','eam' UNION ALL SELECT 'admin','qms' UNION ALL
    SELECT 'admin','srm' UNION ALL SELECT 'admin','plm' UNION ALL
    SELECT 'admin','energy' UNION ALL SELECT 'admin','wf' UNION ALL
    SELECT 'yangming','mdm' UNION ALL SELECT 'yangming','wf' UNION ALL
    SELECT 'xujing','mdm' UNION ALL SELECT 'xujing','crm' UNION ALL
    SELECT 'xujing','erp' UNION ALL SELECT 'xujing','mes' UNION ALL
    SELECT 'xujing','wms' UNION ALL SELECT 'xujing','eam' UNION ALL
    SELECT 'xujing','qms' UNION ALL SELECT 'xujing','srm' UNION ALL
    SELECT 'xujing','plm' UNION ALL SELECT 'xujing','energy' UNION ALL
    -- 销售
    SELECT 'zhangwei','crm' UNION ALL SELECT 'zhangwei','wf' UNION ALL
    SELECT 'liuyang','crm' UNION ALL SELECT 'liuyang','wf' UNION ALL
    SELECT 'qianyi','crm' UNION ALL SELECT 'qianyi','wf' UNION ALL
    SELECT 'chenjing','crm' UNION ALL SELECT 'chenjing','erp' UNION ALL
    SELECT 'wangfang','crm' UNION ALL SELECT 'wangfang','erp' UNION ALL
    SELECT 'wangfang','wf' UNION ALL
    SELECT 'lifang','crm' UNION ALL SELECT 'lifang','erp' UNION ALL
    SELECT 'lifang','wf' UNION ALL
    -- 财务
    SELECT 'zhouba','erp' UNION ALL SELECT 'zhouba','mdm' UNION ALL
    SELECT 'zhouba','wf' UNION ALL
    SELECT 'wuqian','erp' UNION ALL SELECT 'wuqian','wf' UNION ALL
    SELECT 'zhengshuang','erp' UNION ALL SELECT 'zhengshuang','mdm' UNION ALL
    SELECT 'zhengshuang','wf' UNION ALL
    SELECT 'fenglin','erp' UNION ALL SELECT 'fenglin','crm' UNION ALL
    SELECT 'fenglin','wf' UNION ALL
    SELECT 'jiangnan','erp' UNION ALL SELECT 'jiangnan','srm' UNION ALL
    SELECT 'hexin','erp' UNION ALL SELECT 'hexin','wf' UNION ALL
    SELECT 'shenlu','erp' UNION ALL
    -- 采购
    SELECT 'wuban','srm' UNION ALL SELECT 'wuban','erp' UNION ALL
    SELECT 'wuban','wf' UNION ALL
    SELECT 'xuqiang','srm' UNION ALL SELECT 'xuqiang','wf' UNION ALL
    SELECT 'sunli','srm' UNION ALL SELECT 'sunli','wf' UNION ALL
    SELECT 'machao','srm' UNION ALL SELECT 'machao','qms' UNION ALL
    SELECT 'machao','wf' UNION ALL
    -- 研发工艺
    SELECT 'zhaoliu','mdm' UNION ALL SELECT 'zhaoliu','plm' UNION ALL
    SELECT 'zhaoliu','wf' UNION ALL
    SELECT 'zhoutao','mdm' UNION ALL SELECT 'zhoutao','plm' UNION ALL
    SELECT 'zhoutao','mes' UNION ALL SELECT 'zhoutao','wf' UNION ALL
    SELECT 'guolei','mdm' UNION ALL SELECT 'guolei','plm' UNION ALL
    -- 生产
    SELECT 'linfeng','erp' UNION ALL SELECT 'linfeng','mes' UNION ALL
    SELECT 'linfeng','mdm' UNION ALL SELECT 'linfeng','wf' UNION ALL
    SELECT 'sunqi','erp' UNION ALL SELECT 'sunqi','mdm' UNION ALL
    SELECT 'sunqi','mes' UNION ALL SELECT 'sunqi','wf' UNION ALL
    SELECT 'yangfan','erp' UNION ALL SELECT 'yangfan','mes' UNION ALL
    SELECT 'yangfan','mdm' UNION ALL SELECT 'yangfan','wf' UNION ALL
    SELECT 'fengyi','mes' UNION ALL SELECT 'fengyi','eam' UNION ALL
    SELECT 'fengyi','wf' UNION ALL
    SELECT 'dengchao','mes' UNION ALL
    -- 质量
    SELECT 'zhengshi','qms' UNION ALL SELECT 'zhengshi','srm' UNION ALL
    SELECT 'zhengshi','mdm' UNION ALL SELECT 'zhengshi','wf' UNION ALL
    SELECT 'daili','qms' UNION ALL SELECT 'daili','srm' UNION ALL
    SELECT 'daili','wf' UNION ALL
    SELECT 'qianduo','qms' UNION ALL
    -- 设备能源
    SELECT 'chenfeng','eam' UNION ALL SELECT 'chenfeng','wf' UNION ALL
    SELECT 'chujian','eam' UNION ALL
    SELECT 'weilan','energy' UNION ALL SELECT 'weilan','eam' UNION ALL
    -- 仓储
    SELECT 'chener','wms' UNION ALL SELECT 'chener','wf' UNION ALL
    SELECT 'youtao','wms' UNION ALL SELECT 'youtao','wf'
) s ON s.username = u.username
ON DUPLICATE KEY UPDATE system_code=VALUES(system_code);

-- ============================================================
-- 七、数据范围（第二层权限）
-- ============================================================
DELETE FROM sys_data_scope;
INSERT INTO sys_data_scope (role_id, resource_code, scope_type, scope_field)
SELECT r.id, s.resource_code, s.scope_type, s.scope_field
FROM sys_role r
JOIN (
    -- 销售代表：只看自己负责的
    SELECT 'SALES_REP' AS role_code,'CUSTOMER' AS resource_code,'SELF' AS scope_type,'created_by' AS scope_field UNION ALL
    SELECT 'SALES_REP','OPPORTUNITY','SELF','owner_user' UNION ALL
    SELECT 'SALES_REP','SALES_ORDER','SELF','sales_user' UNION ALL
    -- 销售助理：看本部门
    SELECT 'SALES_ASSISTANT','CUSTOMER','DEPT','dept_code' UNION ALL
    SELECT 'SALES_ASSISTANT','SALES_ORDER','DEPT','dept_code' UNION ALL
    -- 销售主管：本部门
    SELECT 'SALES_SUPERVISOR','CUSTOMER','DEPT','dept_code' UNION ALL
    SELECT 'SALES_SUPERVISOR','OPPORTUNITY','DEPT','dept_code' UNION ALL
    SELECT 'SALES_SUPERVISOR','SALES_ORDER','DEPT','dept_code' UNION ALL
    -- 销售总监：全部
    SELECT 'SALES_DIRECTOR','CUSTOMER','ALL',NULL UNION ALL
    SELECT 'SALES_DIRECTOR','OPPORTUNITY','ALL',NULL UNION ALL
    SELECT 'SALES_DIRECTOR','SALES_ORDER','ALL',NULL UNION ALL
    -- 应收会计：只看自己负责的客户
    SELECT 'AR_ACCOUNTANT','CUSTOMER','SELF','owner_user' UNION ALL
    -- 车间主任 / 班组长：只看本车间
    SELECT 'WORKSHOP_CHIEF','WORK_ORDER','DEPT','workshop_code' UNION ALL
    SELECT 'WORKSHOP_CHIEF','EQUIPMENT','DEPT','workshop_code' UNION ALL
    SELECT 'WORKSHOP_CHIEF','INSPECTION','DEPT','workshop_code' UNION ALL
    SELECT 'TEAM_LEADER','WORK_ORDER','DEPT','workshop_code' UNION ALL
    -- 采购员：只看自己下的单
    SELECT 'BUYER','PURCHASE_ORDER','SELF','purchase_user' UNION ALL
    SELECT 'BUYER','SUPPLIER','DEPT','owner_dept' UNION ALL
    -- 库管员：全部库存（仓库是不分人的）
    SELECT 'WAREHOUSE_KEEPER','INVENTORY','ALL',NULL UNION ALL
    SELECT 'WAREHOUSE_SUPERVISOR','INVENTORY','ALL',NULL
) s ON s.role_code = r.role_code
ON DUPLICATE KEY UPDATE scope_type=VALUES(scope_type), scope_field=VALUES(scope_field);

-- ============================================================
-- 八、7 条审批流程（含主管/经理分级）
-- ============================================================
DELETE FROM wf_node;
DELETE FROM wf_definition;

INSERT INTO wf_definition (def_code, def_name, biz_type, description) VALUES
('MDM_CUSTOMER_NEW',      '客户新增审批',     'CUSTOMER',   '销售代表提交 → 销售主管 → 应收会计（信用核查）'),
('MDM_SUPPLIER_NEW',      '供应商新增审批',   'SUPPLIER',   '采购员提交 → 供应商质量工程师 → 采购主管'),
('MDM_MATERIAL_NEW',      '物料新增审批',     'MATERIAL',   '工艺工程师提交 → 工艺主管 → 生产计划员'),
('MDM_BOM_CHANGE',        'BOM变更审批',      'BOM',        '工艺工程师提交 → 工艺主管 → 生产主管 → 成本会计'),
('MDM_ROUTING_CHANGE',    '工艺路线变更审批', 'ROUTING',    '工艺工程师提交 → 生产主管'),
('MDM_CREDIT_CHANGE',     '客户信用额度变更', 'CUSTOMER',   '销售主管提交 → 财务主管 → 财务总监'),
('ERP_PROD_ORDER_RELEASE','生产订单下达审批', 'PROD_ORDER', '生产计划员提交 → 生产主管');

INSERT INTO wf_node (definition_id, node_seq, node_name, approver_role, approve_mode, reject_action, remark)
SELECT d.id, n.node_seq, n.node_name, n.approver_role, n.approve_mode, n.reject_action, n.remark
FROM wf_definition d
JOIN (
    -- 客户新增：销售主管 → 应收会计
    SELECT 'MDM_CUSTOMER_NEW' AS def_code, 10 AS node_seq,'销售主管审批' AS node_name,'SALES_SUPERVISOR' AS approver_role,'SINGLE' AS approve_mode,'BACK' AS reject_action,'确认客户归属与资质' AS remark UNION ALL
    SELECT 'MDM_CUSTOMER_NEW', 20,'信用核查','AR_ACCOUNTANT','SINGLE','BACK','核定信用额度与账期' UNION ALL
    -- 供应商新增：SQE → 采购主管
    SELECT 'MDM_SUPPLIER_NEW', 10,'供应商资质审核','SQE','SINGLE','BACK','审核资质有效期与质量体系' UNION ALL
    SELECT 'MDM_SUPPLIER_NEW', 20,'采购主管批准','PURCHASE_SUPERVISOR','SINGLE','BACK','确认供应商等级与配额' UNION ALL
    -- 物料新增：工艺主管 → 生产计划员
    SELECT 'MDM_MATERIAL_NEW', 10,'工艺主管审核','PROCESS_SUPERVISOR','SINGLE','BACK','确认工艺可制造性与料号规范' UNION ALL
    SELECT 'MDM_MATERIAL_NEW', 20,'生产计划审核','PROD_PLANNER','SINGLE','BACK','确认生产可行性、安全库存与采购策略' UNION ALL
    -- ★ BOM 变更：工艺主管 → 生产主管 → 成本会计（三级，演示重点）
    SELECT 'MDM_BOM_CHANGE', 10,'工艺主管审核','PROCESS_SUPERVISOR','SINGLE','BACK','确认 BOM 结构、用量与损耗率正确' UNION ALL
    SELECT 'MDM_BOM_CHANGE', 20,'生产主管审核','PROD_SUPERVISOR','SINGLE','BACK','确认对排产、齐套与交期的影响' UNION ALL
    SELECT 'MDM_BOM_CHANGE', 30,'成本审核','COST_ACCOUNTANT','SINGLE','BACK','确认对标准成本与订单成本的影响' UNION ALL
    -- 工艺路线变更：生产主管
    SELECT 'MDM_ROUTING_CHANGE', 10,'生产主管确认','PROD_SUPERVISOR','SINGLE','BACK','确认工序顺序、工时与设备可行性' UNION ALL
    -- 信用额度变更：财务主管 → 财务总监（两级）
    SELECT 'MDM_CREDIT_CHANGE', 10,'财务主管复核','FIN_SUPERVISOR','SINGLE','BACK','复核账龄与回款记录' UNION ALL
    SELECT 'MDM_CREDIT_CHANGE', 20,'财务总监批准','FINANCE_DIRECTOR','SINGLE','BACK','批准新信用额度' UNION ALL
    -- 生产订单下达：生产主管
    SELECT 'ERP_PROD_ORDER_RELEASE', 10,'生产主管确认','PROD_SUPERVISOR','SINGLE','BACK','确认产能、物料与交期'
) n ON n.def_code = d.def_code;

-- ============================================================
-- 九、15 条治理规则
-- ============================================================
USE mfg_meta;

INSERT INTO meta_rule (rule_id, rule_name, business_domain, target_table, rule_type, severity, expression, action_desc) VALUES
('F01','凭证号+公司+期间唯一','FINANCE','fct_fin_voucher','unique','error',
 'voucher_no + company_code + fiscal_period','重复凭证进入问题清单'),
('F02','借贷金额平衡','FINANCE','fct_fin_voucher','expression','error',
 'debit_amount = credit_amount','不平衡凭证不进入汇总层'),
('F03','成本中心、科目编码有效','FINANCE','fct_fin_voucher','relationship','error',
 'cc_code IN dim_cost_center AND subject_code IN dim_account_subject','无效主数据标记并追溯来源'),
('F04','应收金额、回款金额及到期日期逻辑合理','FINANCE','fct_receivable','range','warn',
 'invoice_amount >= 0 AND outstanding_amount >= 0 AND due_date >= invoice_date','生成逾期与回款状态标签'),
('F05','订单金额与财务金额按口径对账','FINANCE','dws_fin_order_recon','recon','warn',
 'order_amount = finance_amount','输出差异金额及差异订单'),
('P01','生产订单号唯一且关键字段完整','PRODUCTION','fct_prod_order','unique','error',
 'prod_order_no','重复或缺失订单阻断发布'),
('P02','计划量、完工量、合格量关系合理','PRODUCTION','fct_prod_order','expression','error',
 'completed_qty <= plan_qty AND qualified_qty <= completed_qty AND plan_qty >= 0','超量及负数记录进入异常清单'),
('P03','计划、开工、完工日期顺序正确','PRODUCTION','fct_prod_order','singular','error',
 'plan_start_date <= plan_finish_date AND (actual_start_date IS NULL OR actual_start_date >= plan_start_date)','日期倒置记录标记异常'),
('P04','工序必须属于有效工艺路线','PRODUCTION','fct_work_order','relationship','error',
 'operation_code IN brg_routing','无效工序不参与进度计算'),
('P05','BOM需求、库存及采购到货口径一致','PRODUCTION','dws_prod_kitting','recon','warn',
 'bom_demand = available_stock + incoming_qty + shortage_qty','形成订单齐套率与缺料清单'),
('E01','设备编码在台账中唯一有效','EQUIPMENT','fct_equip_fault','relationship','error',
 'equipment_code IN dim_equipment','孤立设备记录进入问题清单'),
('E02','运行、停机、维修状态时间不重叠','EQUIPMENT','dws_equip_daily_availability','singular','error',
 'no_interval_overlap(equipment_code, start_time, end_time)','冲突时段不进入可用率计算'),
('E03','故障必须关联设备、工单及维修结果','EQUIPMENT','fct_equip_fault','relationship','warn',
 'equipment_code IS NOT NULL AND work_order_no IS NOT NULL AND repair_result IS NOT NULL','缺失关联记录标记待补充'),
('E04','点检周期和点检结果完整','EQUIPMENT','fct_equip_inspection','singular','warn',
 'inspection_interval_gaps <= 1','形成漏检和异常点检标签'),
('E05','能耗为非负且异常波动可识别','EQUIPMENT','fct_energy','expression','warn',
 'energy_value >= 0 AND ABS(deviation_rate) < 0.15','形成设备能耗异常评分')
ON DUPLICATE KEY UPDATE rule_name=VALUES(rule_name), expression=VALUES(expression);

-- ============================================================
-- 十、指标口径（★ MCP query_metric 的唯一数据源）
-- ============================================================
USE mfg_dws;

INSERT INTO metric_definition
(metric_code, metric_name, business_domain, definition, numerator_expr, denominator_expr,
 filter_condition, source_table, dim_grain, unit, value_range_min, value_range_max, owner_dept, refresh_freq) VALUES
('on_time_delivery_rate','按期交付率','PRODUCTION',
 '计划完工日期前实际完工的生产订单数 / 有效生产订单数。分母排除已取消订单；延期判定以计划完工日期当日 23:59:59 为界。',
 'SUM(CASE WHEN actual_finish_date <= plan_finish_date THEN 1 ELSE 0 END)','COUNT(*)',
 "order_status <> 'CANCELED'",'metric_order_delivery','month,factory,workshop,product','%',0,1,'生产制造中心','daily'),
('completion_rate','完工率','PRODUCTION',
 '已完成数量 / 计划数量，以 MES 报工累计为准。',
 'SUM(completed_qty)','SUM(plan_qty)',NULL,
 'metric_production','month,workshop,product','%',0,1.2,'生产制造中心','daily'),
('qualified_rate','合格率','PRODUCTION',
 '合格数量 / 检验数量，以 QMS 检验记录为准（非 MES 自报）。',
 'SUM(qualified_qty)','SUM(inspected_qty)',NULL,
 'metric_production','month,product,equipment','%',0,1,'质量管理中心','daily'),
('kitting_rate','齐套率','PRODUCTION',
 '已齐套物料种类数 / 应齐套物料种类数。齐套判定须逐层展开 BOM：某物料可用量 >= BOM 需求量则视为齐套。',
 'SUM(CASE WHEN shortage_qty <= 0 THEN 1 ELSE 0 END)','COUNT(*)',NULL,
 'metric_inventory','order,material,supplier','%',0,1,'生产制造中心','daily'),
('order_delay_count','延期订单数','PRODUCTION',
 '实际完工日期晚于计划完工日期（含宽限 1 天）的生产订单数量。',
 'SUM(CASE WHEN actual_finish_date > DATE_ADD(plan_finish_date, INTERVAL 1 DAY) THEN 1 ELSE 0 END)','1',
 "order_status <> 'CANCELED'",'metric_order_delivery','month,workshop,risk_level','单',0,NULL,'生产制造中心','daily'),
('equipment_availability','设备可用率','EQUIPMENT',
 '设备运行时长 / (运行时长 + 停机时长)，排除已报废设备。',
 'SUM(run_hours)','SUM(run_hours + downtime_hours)',
 "equipment_status <> 'SCRAPPED'",'metric_equipment','day,workshop,equipment','%',0,1,'设备与能源中心','daily'),
('mtbf','平均故障间隔时间','EQUIPMENT',
 '统计期内设备总运行时长 / 故障次数，衡量设备可靠性。',
 'SUM(run_hours)','COUNT(fault_no)',"fault_no IS NOT NULL",
 'metric_equipment','month,equipment','小时',0,NULL,'设备与能源中心','weekly'),
('mttr','平均修复时间','EQUIPMENT',
 '统计期内设备总维修时长 / 维修次数，衡量维修效率。',
 'SUM(downtime_minutes)','COUNT(repair_no)',"repair_no IS NOT NULL",
 'metric_equipment','month,equipment','分钟',0,NULL,'设备与能源中心','weekly'),
('receivable_overdue_amount','逾期应收金额','FINANCE',
 '到期日已过且尚未回款的金额合计，以财务凭证为准（非业务单据）。',
 'SUM(outstanding_amount)','1','due_date < CURDATE()',
 'metric_finance','month,customer','元',0,NULL,'财务与成本中心','daily'),
('collection_rate','回款率','FINANCE',
 '已回款金额 / 应收金额，衡量回款效率。',
 'SUM(received_amount)','SUM(invoice_amount)',NULL,
 'metric_finance','month,customer','%',0,1.2,'财务与成本中心','daily')
ON DUPLICATE KEY UPDATE
  definition=VALUES(definition), numerator_expr=VALUES(numerator_expr),
  denominator_expr=VALUES(denominator_expr), owner_dept=VALUES(owner_dept);

-- ============================================================
-- 十一、来源系统登记
-- ============================================================
USE mfg_meta;

INSERT INTO meta_system (system_code, system_name, system_type, business_domain, db_name, owner_dept, is_master_data, description) VALUES
('mdm',   '统一主数据管理平台','mysql','财务域,生产域,设备域','src_mdm',   '信息技术中心',1,'12类主数据唯一真实来源，带审批流程'),
('crm',   '客户关系管理',     'mysql','财务域,生产域',    'src_crm',   '销售与市场中心',0,'客户信息、销售商机'),
('erp',   '企业资源计划',     'mysql','财务域,生产域',    'src_erp',   '财务与成本中心',0,'销售订单、生产订单、财务凭证、应收'),
('mes',   '制造执行系统',     'mysql','生产域',           'src_mes',   '生产制造中心',0,'工单、工序报工、生产实绩'),
('wms',   '仓储管理系统',     'mysql','生产域',           'src_wms',   '仓储物流中心',0,'库存余额、出入库流水、库位'),
('eam',   '设备资产管理系统', 'mysql','设备域',           'src_eam',   '设备与能源中心',0,'设备台账、故障维修、点检'),
('qms',   '质量管理系统',     'mysql','生产域,设备域',    'src_qms',   '质量管理中心',0,'质量检验、不合格品、返工'),
('srm',   '供应商关系管理',   'mysql','财务域,生产域',    'src_srm',   '供应链与采购中心',0,'供应商、采购交付'),
('plm',   '产品生命周期管理', 'mysql','生产域',           'src_plm',   '研发与工艺中心',0,'物料清单、工艺路线、工程变更'),
('energy','能源管理系统',     'mysql','设备域',           'src_energy','设备与能源中心',0,'设备能耗、车间能耗')
ON DUPLICATE KEY UPDATE system_name=VALUES(system_name), owner_dept=VALUES(owner_dept);

-- ============================================================
-- 十二、验证输出
-- ============================================================
SELECT '部门' AS 项目, COUNT(*) AS 数量 FROM mfg_auth.sys_dept
UNION ALL SELECT '角色',         COUNT(*) FROM mfg_auth.sys_role
UNION ALL SELECT '权限点',       COUNT(*) FROM mfg_auth.sys_permission
UNION ALL SELECT '演示账号',     COUNT(*) FROM mfg_auth.sys_user
UNION ALL SELECT '角色分配',     COUNT(*) FROM mfg_auth.sys_user_role
UNION ALL SELECT '系统访问权',   COUNT(*) FROM mfg_auth.sys_user_system
UNION ALL SELECT '数据范围规则', COUNT(*) FROM mfg_auth.sys_data_scope
UNION ALL SELECT '审批流程',     COUNT(*) FROM mfg_auth.wf_definition
UNION ALL SELECT '审批节点',     COUNT(*) FROM mfg_auth.wf_node
UNION ALL SELECT '治理规则',     COUNT(*) FROM mfg_meta.meta_rule
UNION ALL SELECT '指标口径',     COUNT(*) FROM mfg_dws.metric_definition
UNION ALL SELECT '来源系统',     COUNT(*) FROM mfg_meta.meta_system;
