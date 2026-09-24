-- ============================================================
-- V40 —— 事件消费框架（计划 00 · F2-02）
--
-- 背景：现有链路是 publish() 写 biz_outbox → 定时 dispatch() 用 INSERT IGNORE
-- 投递 biz_inbox 并立即把 outbox 置 SUCCESS。即「投递成功」= 「写进 inbox」，
-- 而 **inbox 无人读**——这是横向共性问题「事件只发不收」的根源。
--
-- V26 建的 biz_inbox 只有 event_id/event_type/.../status/received_at，
-- **没有**消费状态、消费者、消费时间、重试次数、错误信息、租约列。
-- 故消费机制必须新建字段，不能复用 status（它表示投递结果，不是消费结果）。
--
-- 幂等键复用既有的 uk_biz_inbox_event_target(event_id, target_system)，不重复造。
-- ============================================================

-- ------------------------------------------------------------
-- 一、biz_inbox 补消费字段
-- ------------------------------------------------------------
-- 失败迁移可能已提交部分 DDL；逐列判断，使修复后可安全重新运行。

SET @inbox_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mfg_ops' AND table_name='biz_inbox' AND column_name='consume_status')=0, 'ALTER TABLE mfg_ops.biz_inbox ADD COLUMN consume_status VARCHAR(16)  NOT NULL DEFAULT ''PENDING''
        COMMENT ''PENDING=待消费 / PROCESSING=消费中 / CONSUMED=已消费 / FAILED=可重试失败 / DEAD=死信''', 'SELECT 1');
PREPARE inbox_stmt FROM @inbox_ddl;
EXECUTE inbox_stmt;
DEALLOCATE PREPARE inbox_stmt;

SET @inbox_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mfg_ops' AND table_name='biz_inbox' AND column_name='consumer_name')=0, 'ALTER TABLE mfg_ops.biz_inbox ADD COLUMN consumer_name VARCHAR(64)  NULL     COMMENT ''消费方标识（BusinessEventConsumer 的 name）''', 'SELECT 1');
PREPARE inbox_stmt FROM @inbox_ddl;
EXECUTE inbox_stmt;
DEALLOCATE PREPARE inbox_stmt;

SET @inbox_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mfg_ops' AND table_name='biz_inbox' AND column_name='consumed_at')=0, 'ALTER TABLE mfg_ops.biz_inbox ADD COLUMN consumed_at DATETIME(3)  NULL     COMMENT ''消费成功时间''', 'SELECT 1');
PREPARE inbox_stmt FROM @inbox_ddl;
EXECUTE inbox_stmt;
DEALLOCATE PREPARE inbox_stmt;

SET @inbox_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mfg_ops' AND table_name='biz_inbox' AND column_name='retry_count')=0, 'ALTER TABLE mfg_ops.biz_inbox ADD COLUMN retry_count INT          NOT NULL DEFAULT 0 COMMENT ''已重试次数''', 'SELECT 1');
PREPARE inbox_stmt FROM @inbox_ddl;
EXECUTE inbox_stmt;
DEALLOCATE PREPARE inbox_stmt;

SET @inbox_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mfg_ops' AND table_name='biz_inbox' AND column_name='error_message')=0, 'ALTER TABLE mfg_ops.biz_inbox ADD COLUMN error_message VARCHAR(500) NULL     COMMENT ''最近一次失败原因''', 'SELECT 1');
PREPARE inbox_stmt FROM @inbox_ddl;
EXECUTE inbox_stmt;
DEALLOCATE PREPARE inbox_stmt;

SET @inbox_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mfg_ops' AND table_name='biz_inbox' AND column_name='next_retry_at')=0, 'ALTER TABLE mfg_ops.biz_inbox ADD COLUMN next_retry_at DATETIME(3)  NULL     COMMENT ''指数退避后的下次可领取时间''', 'SELECT 1');
PREPARE inbox_stmt FROM @inbox_ddl;
EXECUTE inbox_stmt;
DEALLOCATE PREPARE inbox_stmt;

SET @inbox_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mfg_ops' AND table_name='biz_inbox' AND column_name='lease_until')=0, 'ALTER TABLE mfg_ops.biz_inbox ADD COLUMN lease_until DATETIME(3)  NULL     COMMENT ''租约到期时间；领取时设置，防止多实例重复消费''', 'SELECT 1');
PREPARE inbox_stmt FROM @inbox_ddl;
EXECUTE inbox_stmt;
DEALLOCATE PREPARE inbox_stmt;

SET @inbox_ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='mfg_ops' AND table_name='biz_inbox' AND index_name='idx_biz_inbox_consume')=0, 'CREATE INDEX idx_biz_inbox_consume ON mfg_ops.biz_inbox(consume_status,next_retry_at)', 'SELECT 1');
PREPARE inbox_stmt FROM @inbox_ddl;
EXECUTE inbox_stmt;
DEALLOCATE PREPARE inbox_stmt;

-- ------------------------------------------------------------
-- 二、消费位点表
--
-- 记录每个消费方对某类事件的推进位置，便于排查「某消费方是否落后」。
-- 与 inbox 的逐条状态互为补充：inbox 记单条，offset 记进度。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mfg_ops.biz_consumer_offset (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    consumer_name VARCHAR(64) NOT NULL COMMENT '消费方标识',
    event_type    VARCHAR(80) NOT NULL COMMENT '事件类型',
    last_inbox_id BIGINT      NOT NULL DEFAULT 0 COMMENT '已推进到的 biz_inbox.id',
    consumed_rows BIGINT      NOT NULL DEFAULT 0 COMMENT '累计消费条数',
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_consumer_event (consumer_name, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='事件消费位点（F2-02）';

-- ------------------------------------------------------------
-- 三、死信视图
--
-- 只读视图，供人工重放端点与运维排查使用。
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW mfg_ops.v_biz_dead_letter AS
SELECT id, event_id, event_type, source_system, target_system,
       aggregate_type, aggregate_id, trace_id,
       consume_status, consumer_name, retry_count, error_message,
       received_at, next_retry_at
FROM mfg_ops.biz_inbox
WHERE consume_status IN ('FAILED', 'DEAD')
ORDER BY received_at DESC;

-- ------------------------------------------------------------
-- 四、历史数据归一化：系统编码统一为小写（对应缺陷 #14）
--
-- P3 链原先写 'SRM'/'WMS'/'QMS'（大写），P2 模块写小写。消费方按 target_system
-- 匹配，不统一会漏掉一半事件。与 sys_permission.system_code 的规范对齐（小写）。
-- ------------------------------------------------------------
UPDATE mfg_ops.biz_outbox SET source_system = LOWER(source_system), target_system = LOWER(target_system)
 WHERE BINARY source_system <> BINARY LOWER(source_system) OR BINARY target_system <> BINARY LOWER(target_system);
UPDATE mfg_ops.biz_inbox  SET source_system = LOWER(source_system), target_system = LOWER(target_system)
 WHERE BINARY source_system <> BINARY LOWER(source_system) OR BINARY target_system <> BINARY LOWER(target_system);
