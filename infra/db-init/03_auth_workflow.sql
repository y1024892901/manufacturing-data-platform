-- ============================================================
-- 03_auth_workflow.sql —— 权限与审批（库: mfg_auth）
--
-- 三块内容:
--   一、账号与权限（谁能进哪个系统、能做什么）
--   二、数据范围（能看哪些数据：本人/本部门/全部）
--   三、审批引擎（自研轻量引擎，约 300 行代码驱动）
--
-- 设计要点:
--   1. 账号分两层：系统访问权（进哪个系统） + 系统内角色（能干什么）
--   2. 审批引擎表结构通用，7 条审批链靠数据定义，不改代码
--   3. 全程留痕，可追溯"谁在什么时候审批了什么"
--
-- 幂等: 全部使用 IF NOT EXISTS
-- ============================================================

USE mfg_auth;

SET NAMES utf8mb4;

-- ============================================================
-- 一、账号与权限
-- ============================================================

-- 用户
CREATE TABLE IF NOT EXISTS sys_user (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    username          VARCHAR(32)  NOT NULL              COMMENT '登录账号: zhangsan',
    password_hash     VARCHAR(128) NOT NULL              COMMENT 'BCrypt 密码哈希',
    real_name         VARCHAR(50)  NOT NULL              COMMENT '姓名: 张三',
    emp_code          VARCHAR(32)  NULL                  COMMENT '关联员工工号',
    org_id            BIGINT       NULL                  COMMENT '所属组织（数据范围用）',
    dept_code         VARCHAR(32)  NULL                  COMMENT '所属部门编码',
    position_name     VARCHAR(50)  NULL                  COMMENT '岗位',
    email             VARCHAR(100) NULL,
    mobile            VARCHAR(20)  NULL,
    is_enabled        TINYINT(1)   NOT NULL DEFAULT 1    COMMENT '是否启用',
    is_demo_account   TINYINT(1)   NOT NULL DEFAULT 1    COMMENT '是否演示账号（便于演示时筛选）',
    last_login_at     DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_user_org (org_id),
    KEY idx_user_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户账号';

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    role_code         VARCHAR(32)  NOT NULL              COMMENT '角色编码: SALES_REP',
    role_name         VARCHAR(50)  NOT NULL              COMMENT '角色名称: 销售代表',
    description       VARCHAR(200) NULL,
    is_system         TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否系统内置角色',
    sort_no           INT          NOT NULL DEFAULT 0,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='角色定义';

-- 权限点（细粒度功能权限）
CREATE TABLE IF NOT EXISTS sys_permission (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    perm_code         VARCHAR(64)  NOT NULL              COMMENT '权限码: CUSTOMER:CREATE',
    perm_name         VARCHAR(100) NOT NULL              COMMENT '权限名称: 新增客户',
    system_code       VARCHAR(16)  NOT NULL              COMMENT '所属系统: mdm/crm/erp/...',
    perm_group        VARCHAR(50)  NULL                  COMMENT '权限分组: 基础数据/采购管理',
    perm_type         VARCHAR(16)  NOT NULL DEFAULT 'API'
                                COMMENT '类型: API / BUTTON / DATA',
    description       VARCHAR(200) NULL,
    sort_no           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    KEY idx_perm_system (system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='权限点定义';

-- 用户-角色（多对多）
CREATE TABLE IF NOT EXISTS sys_user_role (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    role_id           BIGINT       NOT NULL,
    granted_by        VARCHAR(32)  NULL,
    granted_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_ur_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户角色关联';

-- 角色-权限（多对多）
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    role_id           BIGINT       NOT NULL,
    permission_id     BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_rp_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='角色权限关联';

-- 用户-系统访问权（能进哪几个系统）
CREATE TABLE IF NOT EXISTS sys_user_system (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    system_code       VARCHAR(16)  NOT NULL              COMMENT '可访问的系统: mdm/crm/erp/...',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_system (user_id, system_code),
    KEY idx_us_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户可访问的系统（第一层权限：进哪个系统）';

-- 数据范围规则（第二层权限：能看哪些数据）
CREATE TABLE IF NOT EXISTS sys_data_scope (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    role_id           BIGINT       NOT NULL,
    resource_code     VARCHAR(64)  NOT NULL              COMMENT '资源: CUSTOMER / PROD_ORDER / WORK_ORDER',
    scope_type        VARCHAR(16)  NOT NULL              COMMENT '范围: SELF=本人 / DEPT=本部门 / ALL=全部',
    scope_field       VARCHAR(50)  NULL                  COMMENT '过滤字段: created_by / org_id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope (role_id, resource_code),
    KEY idx_scope_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='数据范围规则（销售只能看自己的客户、车间主任只能看自己车间的工单）';

-- ============================================================
-- 二、审批引擎（自研轻量引擎的表结构）
-- ============================================================

-- 流程定义（7 条审批链都用数据描述，不改代码）
CREATE TABLE IF NOT EXISTS wf_definition (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    def_code          VARCHAR(32)  NOT NULL              COMMENT '流程编码: MDM_BOM_CHANGE',
    def_name          VARCHAR(100) NOT NULL              COMMENT '流程名称: BOM变更审批',
    biz_type          VARCHAR(32)  NOT NULL              COMMENT '业务类型: MATERIAL/BOM/CUSTOMER/SUPPLIER/ROUTING',
    def_version       INT          NOT NULL DEFAULT 1,
    is_enabled        TINYINT(1)   NOT NULL DEFAULT 1,
    description       VARCHAR(300) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_def_code_ver (def_code, def_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批流程定义';

-- 流程节点（顺序链：NODE_SEQ 决定审批顺序）
CREATE TABLE IF NOT EXISTS wf_node (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    definition_id     BIGINT       NOT NULL,
    node_seq          INT          NOT NULL              COMMENT '节点顺序: 10/20/30',
    node_name         VARCHAR(50)  NOT NULL              COMMENT '节点名称: 工艺审核',
    approver_role     VARCHAR(32)  NOT NULL              COMMENT '审批角色编码: PROCESS_ENGINEER',
    approve_mode      VARCHAR(16)  NOT NULL DEFAULT 'SINGLE'
                                COMMENT '审批方式: SINGLE=单人 / ALL=会签(全同意) / ANY=或签(任一同意)',
    is_required       TINYINT(1)   NOT NULL DEFAULT 1    COMMENT '是否必经节点',
    reject_action     VARCHAR(16)  NOT NULL DEFAULT 'BACK'
                                COMMENT '驳回动作: BACK=退回提交人 / PREV=退上一节点',
    timeout_hours     INT          NULL                  COMMENT '超时小时数（0=不超时）',
    timeout_action    VARCHAR(16)  NULL                  COMMENT '超时动作: REMIND/AUTO_PASS/AUTO_REJECT',
    remark            VARCHAR(200) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_node (definition_id, node_seq),
    KEY idx_node_def (definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批节点定义（三级审批 = 三个 NODE_SEQ）';

-- 流程实例（一次审批走的记录）
CREATE TABLE IF NOT EXISTS wf_instance (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    instance_no       VARCHAR(32)  NOT NULL              COMMENT '实例号: WF-20260917-0001',
    definition_id     BIGINT       NOT NULL,
    biz_type          VARCHAR(32)  NOT NULL              COMMENT '业务类型',
    biz_id            BIGINT       NOT NULL              COMMENT '业务单据ID',
    biz_no            VARCHAR(64)  NULL                  COMMENT '业务单号: BOM编码等',
    biz_title         VARCHAR(200) NULL                  COMMENT '业务标题（列表展示用）',
    biz_snapshot      JSON         NULL                  COMMENT '提交时的业务数据快照（审批人看这个判断）',
    current_node_seq  INT          NULL                  COMMENT '当前节点顺序',
    status            VARCHAR(16)  NOT NULL DEFAULT 'RUNNING'
                                COMMENT 'RUNNING=审批中 / APPROVED=通过 / REJECTED=驳回 / CANCELED=撤回',
    submitter         VARCHAR(32)  NOT NULL              COMMENT '提交人',
    submitted_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at       DATETIME(3)  NULL,
    total_duration_min INT         NULL                  COMMENT '总耗时（分钟）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_instance_no (instance_no),
    KEY idx_inst_biz (biz_type, biz_id),
    KEY idx_inst_status (status),
    KEY idx_inst_submitter (submitter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批流程实例';

-- 审批任务（待办队列）
CREATE TABLE IF NOT EXISTS wf_task (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    instance_id       BIGINT       NOT NULL,
    node_seq          INT          NOT NULL,
    node_name         VARCHAR(50)  NOT NULL,
    approver_role     VARCHAR(32)  NOT NULL              COMMENT '应审批角色',
    approver_user     VARCHAR(32)  NULL                  COMMENT '实际审批人（会签时多人各一条）',
    task_status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                COMMENT 'PENDING=待办 / APPROVED=已同意 / REJECTED=已驳回 / SKIPPED=已跳过 / CANCELED=已取消',
    opinion           VARCHAR(500) NULL                  COMMENT '审批意见',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    claimed_at        DATETIME(3)  NULL                  COMMENT '领取时间',
    finished_at       DATETIME(3)  NULL,
    duration_min      INT          NULL                  COMMENT '本节点耗时（分钟）',
    PRIMARY KEY (id),
    KEY idx_task_inst (instance_id),
    KEY idx_task_role_status (approver_role, task_status),
    KEY idx_task_user_status (approver_user, task_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批任务（待办队列；"待办 3 条"就是查这张表）';

-- 审批操作日志（全程留痕）
CREATE TABLE IF NOT EXISTS wf_action_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    instance_id       BIGINT       NOT NULL,
    task_id           BIGINT       NULL,
    node_seq          INT          NULL,
    action            VARCHAR(16)  NOT NULL              COMMENT 'SUBMIT/APPROVE/REJECT/TRANSFER/CANCEL/ADD_SIGN',
    operator          VARCHAR(32)  NOT NULL,
    operator_name     VARCHAR(50)  NULL,
    opinion           VARCHAR(500) NULL                  COMMENT '操作意见',
    from_status       VARCHAR(16)  NULL,
    to_status         VARCHAR(16)  NULL,
    operated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_log_inst (instance_id),
    KEY idx_log_time (operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批操作日志（演示"审批时间轴"的数据源）';

-- ============================================================
-- 三、登录与操作审计
-- ============================================================

-- 登录日志
CREATE TABLE IF NOT EXISTS sys_login_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    username          VARCHAR(32)  NOT NULL,
    real_name         VARCHAR(50)  NULL,
    login_status      VARCHAR(16)  NOT NULL              COMMENT 'SUCCESS / FAILED',
    fail_reason       VARCHAR(200) NULL,
    ip_address        VARCHAR(64)  NULL,
    user_agent        VARCHAR(500) NULL,
    login_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_login_user (username),
    KEY idx_login_time (login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='登录日志';

-- 操作审计（业务操作的留痕）
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    operator          VARCHAR(32)  NOT NULL,
    operator_name     VARCHAR(50)  NULL,
    system_code       VARCHAR(16)  NOT NULL              COMMENT '在哪个系统操作',
    action            VARCHAR(32)  NOT NULL              COMMENT 'CREATE/UPDATE/DELETE/SUBMIT/APPROVE/EXPORT',
    object_type       VARCHAR(32)  NOT NULL              COMMENT '对象类型: CUSTOMER/PROD_ORDER/...',
    object_id         VARCHAR(64)  NULL,
    object_name       VARCHAR(200) NULL,
    before_value      JSON         NULL,
    after_value       JSON         NULL,
    ip_address        VARCHAR(64)  NULL,
    operated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_oper (operator),
    KEY idx_audit_obj (object_type, object_id),
    KEY idx_audit_time (operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='操作审计日志（合规演示点）';
