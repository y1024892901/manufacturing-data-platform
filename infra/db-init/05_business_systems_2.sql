-- ============================================================
-- 05_business_systems_2.sql —— 剩余 5 类业务系统
--   MES 制造执行 / WMS 仓储 / QMS 质量 / EAM 设备 / 能源管理
--
-- 同样遵循「只持有主数据只读副本」的设计约定。
--
-- 幂等: 全部使用 IF NOT EXISTS
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 五、MES —— 制造执行系统
--     消费主数据: 物料 / BOM / 工艺路线 / 产品
-- ============================================================
USE src_mes;

CREATE TABLE IF NOT EXISTS mes_md_material (
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NOT NULL,
    base_unit_code    VARCHAR(16)  NOT NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】物料主数据';

CREATE TABLE IF NOT EXISTS mes_md_routing_operation (
    routing_code      VARCHAR(32)  NOT NULL,
    routing_version   VARCHAR(16)  NOT NULL,
    op_seq            INT          NOT NULL,
    operation_code    VARCHAR(32)  NOT NULL,
    operation_name    VARCHAR(100) NOT NULL,
    work_center       VARCHAR(50)  NULL,
    run_time_min      DECIMAL(10,4) NOT NULL DEFAULT 0,
    default_equipment_code VARCHAR(32) NULL              COMMENT '默认设备（停机传导的起点）',
    is_inspection_op  TINYINT(1)   NOT NULL DEFAULT 0,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (routing_code, routing_version, op_seq),
    KEY idx_mes_op_code (operation_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】工艺路线工序（治理规则 P04 的校验基准）';

-- 工单（★ 设备停机传导路径的中间环节：设备→工序→工单→订单）
CREATE TABLE IF NOT EXISTS mes_work_order (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    work_order_no     VARCHAR(32)  NOT NULL              COMMENT '工单号: WO-20260901-001-10',
    prod_order_no     VARCHAR(32)  NOT NULL              COMMENT '关联生产订单（ERP）',
    product_code      VARCHAR(32)  NOT NULL,
    -- 工序信息
    op_seq            INT          NOT NULL              COMMENT '工序顺序号',
    operation_code    VARCHAR(32)  NOT NULL              COMMENT '工序编码',
    operation_name    VARCHAR(100) NULL,
    routing_code      VARCHAR(32)  NULL,
    routing_version   VARCHAR(16)  NULL,
    -- 设备与工作中心（停机影响的落点）
    equipment_code    VARCHAR(32)  NULL                  COMMENT '执行设备',
    work_center       VARCHAR(50)  NULL,
    workshop_code     VARCHAR(32)  NULL,
    -- 数量
    plan_qty          DECIMAL(18,4) NOT NULL,
    completed_qty     DECIMAL(18,4) NOT NULL DEFAULT 0,
    qualified_qty     DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrap_qty         DECIMAL(18,4) NOT NULL DEFAULT 0,
    -- 日期（治理规则 P03 的检测对象）
    plan_start_time   DATETIME(3)  NULL,
    plan_end_time     DATETIME(3)  NULL,
    actual_start_time DATETIME(3)  NULL,
    actual_end_time   DATETIME(3)  NULL,
    -- 状态
    wo_status         VARCHAR(16)  NOT NULL DEFAULT 'CREATED'
                                COMMENT 'CREATED=已创建 / RELEASED=已下达 / STARTED=已开工 / PAUSED=已暂停 / COMPLETED=已完工 / CANCELED=已取消',
    -- 工时
    plan_hours        DECIMAL(10,2) NULL,
    actual_hours      DECIMAL(10,2) NULL,
    -- 数据范围
    workshop_user     VARCHAR(32)  NULL                  COMMENT '车间负责人（数据范围）',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wo_no (work_order_no),
    KEY idx_wo_prod (prod_order_no),
    KEY idx_wo_equipment (equipment_code),
    KEY idx_wo_status (wo_status),
    KEY idx_wo_workshop (workshop_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工单（设备→工序→工单→订单 传导链的中间环节）';

-- 报工记录
CREATE TABLE IF NOT EXISTS mes_work_report (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    report_no         VARCHAR(32)  NOT NULL              COMMENT '报工单号',
    work_order_no     VARCHAR(32)  NOT NULL,
    prod_order_no     VARCHAR(32)  NOT NULL,
    op_seq            INT          NOT NULL,
    report_qty        DECIMAL(18,4) NOT NULL             COMMENT '报工数量',
    qualified_qty     DECIMAL(18,4) NOT NULL DEFAULT 0,
    scrap_qty         DECIMAL(18,4) NOT NULL DEFAULT 0,
    -- 时间
    report_date       DATE         NOT NULL,
    start_time        DATETIME(3)  NULL,
    end_time          DATETIME(3)  NULL,
    work_hours        DECIMAL(10,2) NULL                 COMMENT '工时',
    -- 资源
    equipment_code    VARCHAR(32)  NULL,
    operator_code     VARCHAR(32)  NULL                  COMMENT '操作工',
    shift_code        VARCHAR(16)  NULL                  COMMENT '班次',
    -- 异常（设备停机的影响落点）
    is_paused         TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否因异常暂停',
    pause_reason      VARCHAR(300) NULL                  COMMENT '暂停原因（如设备故障）',
    pause_minutes     INT          NOT NULL DEFAULT 0    COMMENT '暂停时长',
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_no (report_no),
    KEY idx_wr_wo (work_order_no),
    KEY idx_wr_prod (prod_order_no),
    KEY idx_wr_date (report_date),
    KEY idx_wr_equip (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='报工记录（订单进度与停机影响的原始数据）';

-- 生产实绩（工序级汇总）
CREATE TABLE IF NOT EXISTS mes_prod_result (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    prod_order_no     VARCHAR(32)  NOT NULL,
    product_code      VARCHAR(32)  NOT NULL,
    total_plan_qty    DECIMAL(18,4) NOT NULL DEFAULT 0,
    total_completed_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    total_qualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    progress_rate     DECIMAL(5,4) NULL                  COMMENT '完工率 0~1',
    current_op_seq    INT          NULL                  COMMENT '当前工序',
    last_report_at    DATETIME(3)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_pr_prod (prod_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='生产实绩汇总（订单进度）';

-- ============================================================
-- 六、WMS —— 仓储管理系统
--     消费主数据: 物料 / 计量单位 / 组织
-- ============================================================
USE src_wms;

CREATE TABLE IF NOT EXISTS wms_md_material (
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NOT NULL,
    base_unit_code    VARCHAR(16)  NOT NULL,
    safety_stock      DECIMAL(18,4) NOT NULL DEFAULT 0,
    is_batch_managed  TINYINT(1)   NOT NULL DEFAULT 0,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】物料主数据';

-- 库位
CREATE TABLE IF NOT EXISTS wms_location (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    location_code     VARCHAR(32)  NOT NULL              COMMENT '库位编码',
    location_name     VARCHAR(100) NULL,
    warehouse_code    VARCHAR(32)  NOT NULL              COMMENT '仓库编码',
    warehouse_name    VARCHAR(100) NULL,
    location_type     VARCHAR(16)  NULL                  COMMENT '类型: RAW/FINISHED/SPARE/QUARANTINE',
    is_available      TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_location (location_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='库位';

-- 库存余额（★ 齐套率计算的输入）
CREATE TABLE IF NOT EXISTS wms_inventory (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NULL,
    warehouse_code    VARCHAR(32)  NOT NULL,
    location_code     VARCHAR(32)  NULL,
    batch_no          VARCHAR(32)  NULL                  COMMENT '批次号',
    -- 数量（库存守恒：期初+入库-出库=期末）
    on_hand_qty       DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '在库数量',
    allocated_qty     DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '已分配（已锁定）数量',
    available_qty     DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '可用数量 = 在库 - 已分配',
    in_transit_qty    DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '在途数量（已下单未到货）',
    unit_code         VARCHAR(16)  NOT NULL,
    -- 成本
    unit_cost         DECIMAL(18,4) NOT NULL DEFAULT 0,
    total_cost        DECIMAL(18,2) NOT NULL DEFAULT 0,
    -- 日期
    last_in_date      DATE         NULL,
    last_out_date     DATE         NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv (material_code, warehouse_code, location_code, batch_no),
    KEY idx_inv_material (material_code),
    KEY idx_inv_warehouse (warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='库存余额（★ 齐套率计算的输入；available_qty 是可用量）';

-- 出入库流水
CREATE TABLE IF NOT EXISTS wms_stock_txn (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    txn_no            VARCHAR(32)  NOT NULL              COMMENT '流水号',
    material_code     VARCHAR(32)  NOT NULL,
    warehouse_code    VARCHAR(32)  NOT NULL,
    location_code     VARCHAR(32)  NULL,
    batch_no          VARCHAR(32)  NULL,
    -- 类型
    txn_type          VARCHAR(16)  NOT NULL
                                COMMENT 'IN_PURCHASE=采购入库 / IN_PROD=生产入库 / IN_RETURN=退货入库 / OUT_PROD=生产领料 / OUT_SALES=销售出库 / OUT_SCRAP=报废出库 / ADJUST=盘点调整',
    txn_direction     VARCHAR(4)   NOT NULL              COMMENT 'IN / OUT',
    txn_qty           DECIMAL(18,4) NOT NULL,
    unit_code         VARCHAR(16)  NOT NULL,
    unit_cost         DECIMAL(18,4) NOT NULL DEFAULT 0,
    -- 关联单据
    source_no         VARCHAR(32)  NULL                  COMMENT '来源单号（采购单/生产订单/发货单）',
    prod_order_no     VARCHAR(32)  NULL,
    work_order_no     VARCHAR(32)  NULL,
    purchase_order_no VARCHAR(32)  NULL,
    -- 时间与人员
    txn_date          DATE         NOT NULL,
    txn_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    operator_code     VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_txn_no (txn_no),
    KEY idx_txn_material (material_code),
    KEY idx_txn_date (txn_date),
    KEY idx_txn_type (txn_type),
    KEY idx_txn_prod (prod_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='出入库流水（库存守恒校验的对象）';

-- ============================================================
-- 七、QMS —— 质量管理系统
--     消费主数据: 物料 / 产品 / 组织
-- ============================================================
USE src_qms;

CREATE TABLE IF NOT EXISTS qms_md_material (
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NOT NULL,
    is_inspection_required TINYINT(1) NOT NULL DEFAULT 1,
    inspection_standard VARCHAR(500) NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】物料主数据（含检验标准）';

-- 检验单
CREATE TABLE IF NOT EXISTS qms_inspection (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    inspection_no     VARCHAR(32)  NOT NULL              COMMENT '检验单号: QC-20260901-001',
    inspection_type   VARCHAR(16)  NOT NULL
                                COMMENT 'IQC=来料检验 / IPQC=过程检验 / FQC=成品检验 / OQC=出货检验',
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NULL,
    batch_no          VARCHAR(32)  NULL,
    -- 关联单据
    prod_order_no     VARCHAR(32)  NULL,
    work_order_no     VARCHAR(32)  NULL,
    operation_code    VARCHAR(32)  NULL                  COMMENT '检验工序',
    supplier_code     VARCHAR(32)  NULL                  COMMENT '供应商（IQC时）',
    delivery_no       VARCHAR(32)  NULL,
    -- 数量（★ 不良率计算的输入）
    inspected_qty     DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '检验数量',
    qualified_qty     DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '合格数量',
    defect_qty        DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '不合格数量',
    defect_rate       DECIMAL(5,4) NULL                  COMMENT '不良率 0~1',
    -- 结论
    inspect_result    VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                COMMENT 'PENDING=待检 / PASSED=合格 / FAILED=不合格 / CONCESSION=让步接收 / REWORK=返工',
    inspect_date      DATE         NOT NULL,
    inspector_code    VARCHAR(32)  NULL                  COMMENT '检验员',
    -- 数据范围
    workshop_code     VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_no (inspection_no),
    KEY idx_insp_material (material_code),
    KEY idx_insp_prod (prod_order_no),
    KEY idx_insp_result (inspect_result),
    KEY idx_insp_date (inspect_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='检验单（★ 不良率是延期风险模型的输入特征）';

-- 不合格品记录
CREATE TABLE IF NOT EXISTS qms_defect (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    defect_no         VARCHAR(32)  NOT NULL              COMMENT '不合格品单号',
    inspection_no     VARCHAR(32)  NOT NULL,
    material_code     VARCHAR(32)  NOT NULL,
    defect_qty        DECIMAL(18,4) NOT NULL,
    -- 缺陷信息
    defect_type       VARCHAR(32)  NULL                  COMMENT '缺陷类型: 尺寸/外观/性能/材质',
    defect_desc       VARCHAR(500) NULL,
    defect_level      VARCHAR(8)   NULL                  COMMENT '严重程度: CRITICAL/MAJOR/MINOR',
    -- 处置（触发返工/报废）
    disposition       VARCHAR(16)  NULL
                                COMMENT 'REWORK=返工 / SCRAP=报废 / CONCESSION=让步接收 / RETURN=退货',
    disposition_qty   DECIMAL(18,4) NOT NULL DEFAULT 0,
    disposition_at    DATETIME(3)  NULL,
    disposition_by    VARCHAR(32)  NULL,
    -- 责任
    responsible_dept  VARCHAR(50)  NULL,
    root_cause        VARCHAR(500) NULL                  COMMENT '根本原因',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_defect_no (defect_no),
    KEY idx_defect_insp (inspection_no),
    KEY idx_defect_material (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='不合格品记录（处置为返工时回写 MES）';

-- 返工记录
CREATE TABLE IF NOT EXISTS qms_rework (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    rework_no         VARCHAR(32)  NOT NULL,
    defect_no         VARCHAR(32)  NOT NULL,
    prod_order_no     VARCHAR(32)  NOT NULL              COMMENT '关联生产订单',
    work_order_no     VARCHAR(32)  NULL,
    material_code     VARCHAR(32)  NOT NULL,
    rework_qty        DECIMAL(18,4) NOT NULL,
    -- 返工影响（工期延后的原因）
    rework_hours      DECIMAL(10,2) NULL,
    delay_days        INT          NOT NULL DEFAULT 0    COMMENT '因返工导致的工期延后天数',
    rework_status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                COMMENT 'PENDING=待返工 / DOING=返工中 / DONE=已完成 / SCRAPPED=转为报废',
    start_time        DATETIME(3)  NULL,
    end_time          DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rework_no (rework_no),
    KEY idx_rework_prod (prod_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='返工记录（质量→工期的传导）';

-- ============================================================
-- 八、EAM —— 设备资产管理系统
--     消费主数据: 物料（备件）/ 组织
-- ============================================================
USE src_eam;

CREATE TABLE IF NOT EXISTS eam_md_material (
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NOT NULL,
    base_unit_code    VARCHAR(16)  NOT NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】物料主数据（备件）';

-- 设备台账
CREATE TABLE IF NOT EXISTS eam_equipment (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_code    VARCHAR(32)  NOT NULL              COMMENT '设备编码（治理规则 E01 的检测对象）',
    equipment_name    VARCHAR(200) NOT NULL,
    equipment_model   VARCHAR(100) NULL,
    equipment_type    VARCHAR(32)  NULL                  COMMENT '设备类型: 钻孔/装配/焊接/检测',
    -- 归属
    workshop_code     VARCHAR(32)  NULL,
    work_center       VARCHAR(50)  NULL,
    location_desc     VARCHAR(200) NULL,
    -- 能力参数
    capacity_per_hour DECIMAL(18,4) NULL                 COMMENT '额定产能/小时',
    -- 状态
    equipment_status  VARCHAR(16)  NOT NULL DEFAULT 'IDLE'
                                COMMENT 'RUNNING=运行 / IDLE=闲置 / FAULT=故障 / MAINTENANCE=维修 / SCRAPPED=已报废',
    -- 维护
    purchase_date     DATE         NULL,
    purchase_price    DECIMAL(18,2) NULL,
    warranty_end_date DATE         NULL,
    maintenance_cycle_days INT     NULL                  COMMENT '保养周期（天）',
    last_maintenance_date DATE     NULL,
    -- 健康（由数仓回写）
    health_score      DECIMAL(5,2) NULL                  COMMENT '健康评分 0~100（数仓回写）',
    health_level      VARCHAR(16)  NULL                  COMMENT '健康等级: 优/良/关注/重点',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_equip_code (equipment_code),
    KEY idx_equip_status (equipment_status),
    KEY idx_equip_workshop (workshop_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='设备台账（★ 停机影响的起点）';

-- 设备状态时段（治理规则 E02 的检测对象：时段不可重叠）
CREATE TABLE IF NOT EXISTS eam_equipment_status_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_code    VARCHAR(32)  NOT NULL,
    status_code       VARCHAR(16)  NOT NULL              COMMENT 'RUNNING/IDLE/FAULT/MAINTENANCE',
    start_time        DATETIME(3)  NOT NULL,
    end_time          DATETIME(3)  NULL                  COMMENT '空=仍在进行中',
    duration_minutes  INT          NULL                  COMMENT '时长（分钟）',
    -- 关联
    fault_no          VARCHAR(32)  NULL                  COMMENT '若为故障，关联故障单',
    work_order_no     VARCHAR(32)  NULL                  COMMENT '若因工单停机，关联工单',
    remark            VARCHAR(300) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_esl_equip (equipment_code),
    KEY idx_esl_start (start_time),
    KEY idx_esl_status (status_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='设备状态时段（E02 时段重叠检测的对象；可用率计算基础）';

-- 故障工单
CREATE TABLE IF NOT EXISTS eam_fault (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    fault_no          VARCHAR(32)  NOT NULL              COMMENT '故障单号: F-20260901-001',
    equipment_code    VARCHAR(32)  NOT NULL,
    equipment_name    VARCHAR(200) NULL,
    -- 故障信息
    fault_type        VARCHAR(32)  NULL                  COMMENT '故障类型: 机械/电气/液压/程序',
    fault_level       VARCHAR(8)   NULL                  COMMENT '级别: HIGH/MEDIUM/LOW',
    fault_desc        VARCHAR(500) NULL,
    fault_cause       VARCHAR(500) NULL                  COMMENT '故障原因',
    -- 时间（★ 停机时长计算的输入）
    fault_time        DATETIME(3)  NOT NULL              COMMENT '故障发生时间',
    -- 关联生产（设备→工序→工单→订单 传导链）
    work_order_no     VARCHAR(32)  NULL                  COMMENT '受影响工单',
    prod_order_no     VARCHAR(32)  NULL                  COMMENT '受影响生产订单',
    operation_code    VARCHAR(32)  NULL                  COMMENT '受影响工序',
    -- 状态
    fault_status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN'
                                COMMENT 'OPEN=待处理 / PROCESSING=维修中 / REPAIRED=已修复 / CLOSED=已关闭',
    reported_by       VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fault_no (fault_no),
    KEY idx_fault_equip (equipment_code),
    KEY idx_fault_status (fault_status),
    KEY idx_fault_time (fault_time),
    KEY idx_fault_prod (prod_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='设备故障（★ 停机时长是延期风险模型的输入特征）';

-- 维修记录
CREATE TABLE IF NOT EXISTS eam_repair (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    repair_no         VARCHAR(32)  NOT NULL              COMMENT '维修单号',
    fault_no          VARCHAR(32)  NOT NULL,
    equipment_code    VARCHAR(32)  NOT NULL,
    -- 时间（★ 停机时长的组成部分）
    repair_start_time DATETIME(3)  NOT NULL,
    repair_end_time   DATETIME(3)  NULL,
    downtime_minutes  INT          NOT NULL DEFAULT 0    COMMENT '停机时长（分钟）',
    -- 维修内容
    repair_type       VARCHAR(16)  NULL                  COMMENT '类型: 抢修/计划维修/大修',
    repair_content    VARCHAR(1000) NULL,
    replaced_parts    VARCHAR(500) NULL                  COMMENT '更换备件',
    repair_cost       DECIMAL(18,2) NOT NULL DEFAULT 0,
    maintenance_hours DECIMAL(10,2) NULL,
    -- 人员
    repairman_code    VARCHAR(32)  NULL,
    repair_result     VARCHAR(16)  NULL                  COMMENT 'REPAIRED=已修复 / PENDING_PARTS=待备件 / SCRAPPED=转报废',
    remark            VARCHAR(300) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_repair_no (repair_no),
    KEY idx_repair_fault (fault_no),
    KEY idx_repair_equip (equipment_code),
    KEY idx_repair_start (repair_start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='维修记录（治理规则 E03 的检测对象：必须关联设备/工单/结果）';

-- 点检记录（治理规则 E04 的检测对象：周期与结果完整性）
CREATE TABLE IF NOT EXISTS eam_inspection (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    inspection_no     VARCHAR(32)  NOT NULL              COMMENT '点检单号',
    equipment_code    VARCHAR(32)  NOT NULL,
    inspection_type   VARCHAR(16)  NOT NULL              COMMENT 'DAILY=日常点检 / WEEKLY=周点检 / MONTHLY=月点检',
    plan_date         DATE         NOT NULL              COMMENT '计划点检日',
    actual_date       DATE         NULL                  COMMENT '实际点检日（空=漏检）',
    -- 结果
    inspection_result VARCHAR(16)  NULL                  COMMENT 'NORMAL=正常 / ABNORMAL=异常 / NA=未点检',
    abnormal_desc     VARCHAR(500) NULL,
    -- 点检项（JSON 便于灵活扩展）
    check_items       JSON         NULL                  COMMENT '点检项与结果',
    inspector_code    VARCHAR(32)  NULL,
    -- 是否漏检（数仓计算）
    is_missed         TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否漏检（E04 检测）',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_eam_insp_no (inspection_no),
    KEY idx_eam_insp_equip (equipment_code),
    KEY idx_eam_insp_plan (plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='点检记录（E04 点检周期完整性的检测对象）';

-- ============================================================
-- 九、能源管理系统
--     消费主数据: 设备 / 组织
-- ============================================================
USE src_energy;

CREATE TABLE IF NOT EXISTS energy_md_equipment (
    equipment_code    VARCHAR(32)  NOT NULL,
    equipment_name    VARCHAR(200) NOT NULL,
    workshop_code     VARCHAR(32)  NULL,
    capacity_per_hour DECIMAL(18,4) NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】设备主数据';

-- 设备能耗（治理规则 E05 的检测对象：非负且波动可识别）
CREATE TABLE IF NOT EXISTS energy_equipment_usage (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_code    VARCHAR(32)  NOT NULL,
    stat_date         DATE         NOT NULL,
    stat_hour         TINYINT      NULL                  COMMENT '统计小时（空=按日汇总）',
    -- 能耗量（★ 必须非负，异常波动是设备劣化信号）
    energy_type       VARCHAR(16)  NOT NULL DEFAULT 'ELECTRIC'
                                COMMENT 'ELECTRIC=电 / GAS=气 / WATER=水 / STEAM=蒸汽',
    energy_value      DECIMAL(18,4) NOT NULL             COMMENT '能耗量',
    unit_code         VARCHAR(16)  NOT NULL DEFAULT 'KWH',
    -- 运行关联
    run_hours         DECIMAL(10,2) NULL                 COMMENT '运行时长（小时）',
    unit_consumption  DECIMAL(18,6) NULL                 COMMENT '单位能耗 = 能耗/产量',
    output_qty        DECIMAL(18,4) NULL                 COMMENT '产出数量',
    -- 基线对比（数仓计算）
    baseline_value    DECIMAL(18,4) NULL                 COMMENT '能耗基线',
    deviation_rate    DECIMAL(8,4) NULL                  COMMENT '偏离率 %',
    is_abnormal       TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否异常（E05 检测）',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_equip (equipment_code, stat_date, stat_hour, energy_type),
    KEY idx_energy_date (stat_date),
    KEY idx_energy_equip (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='设备能耗（E05 非负与异常波动的检测对象）';

-- 车间能耗
CREATE TABLE IF NOT EXISTS energy_workshop_usage (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    workshop_code     VARCHAR(32)  NOT NULL,
    stat_date         DATE         NOT NULL,
    energy_type       VARCHAR(16)  NOT NULL DEFAULT 'ELECTRIC',
    energy_value      DECIMAL(18,4) NOT NULL,
    unit_code         VARCHAR(16)  NOT NULL DEFAULT 'KWH',
    total_output      DECIMAL(18,4) NULL                 COMMENT '车间总产出',
    unit_consumption  DECIMAL(18,6) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ws_energy (workshop_code, stat_date, energy_type),
    KEY idx_ws_energy_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='车间能耗';
