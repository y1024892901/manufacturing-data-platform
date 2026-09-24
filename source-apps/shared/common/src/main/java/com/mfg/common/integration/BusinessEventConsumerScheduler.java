package com.mfg.common.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** Inbox 消费：领取、业务提交、失败记录各有明确事务边界。面向单进程演示环境。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessEventConsumerScheduler {
    private static final int BATCH = 50;
    private static final int MAX_RETRY = 5;
    private static final int LEASE_SECONDS = 60;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final List<BusinessEventConsumer> consumers;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelay = 2000, initialDelay = 8000)
    public void drain() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        for (int n = 0; n < BATCH; n++) {
            String owner = "lease:" + UUID.randomUUID();
            Long id = tx.execute(status -> claim(owner));
            if (id == null) return;
            try {
                // 行锁覆盖业务效果与消费位点提交；失败时一起回滚。
                tx.executeWithoutResult(status -> consume(id, owner));
            } catch (Exception ex) {
                tx.executeWithoutResult(status -> recordFailure(id, owner, ex));
                log.warn("事件消费失败 inbox={}: {}", id, ex.getMessage());
            }
        }
    }

    private Long claim(String owner) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id FROM mfg_ops.biz_inbox
                 WHERE (consume_status IN ('PENDING','FAILED') AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)))
                    OR (consume_status = 'PROCESSING' AND lease_until <= NOW(3))
                 ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                """);
        if (rows.isEmpty()) return null;
        Long id = ((Number) rows.get(0).get("id")).longValue();
        jdbc.update("""
                UPDATE mfg_ops.biz_inbox SET consume_status='PROCESSING', consumer_name=?,
                    lease_until=DATE_ADD(NOW(3), INTERVAL ? SECOND) WHERE id=?
                """, owner, LEASE_SECONDS, id);
        return id;
    }

    private Map<String, Object> ownedRow(Long id, String owner) {
        var rows = jdbc.queryForList("SELECT * FROM mfg_ops.biz_inbox WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        return "PROCESSING".equals(row.get("consume_status")) && owner.equals(row.get("consumer_name")) ? row : null;
    }

    private void consume(Long id, String owner) {
        var row = ownedRow(id, owner);
        if (row == null) return; // 租约已由其他 worker 接管，旧 worker 不得写业务或状态。
        String type = String.valueOf(row.get("event_type"));
        String target = String.valueOf(row.get("target_system"));
        var handlers = consumers.stream().filter(c -> c.supports().contains(type)
                && c.targetSystem().equalsIgnoreCase(target)).toList();
        if (handlers.size() != 1) {
            markDead(id, handlers.isEmpty() ? "无消费方注册：" + type + " -> " + target : "同一事件目标注册了多个消费方：" + type);
            return;
        }
        BusinessEventConsumer handler = handlers.get(0);
        handler.consume(toEvent(row));
        jdbc.update("""
                UPDATE mfg_ops.biz_inbox SET consume_status='CONSUMED', consumer_name=?,
                    consumed_at=NOW(3), lease_until=NULL, next_retry_at=NULL, error_message=NULL WHERE id=?
                """, handler.name(), id);
        jdbc.update("""
                INSERT INTO mfg_ops.biz_consumer_offset(consumer_name,event_type,last_inbox_id,consumed_rows)
                VALUES(?,?,?,1) ON DUPLICATE KEY UPDATE
                    last_inbox_id=GREATEST(last_inbox_id,VALUES(last_inbox_id)), consumed_rows=consumed_rows+1
                """, handler.name(), type, id);
    }

    private BusinessEvent toEvent(Map<String, Object> row) {
        Object raw = row.get("payload");
        Map<String, Object> payload;
        try {
            if (raw == null) throw new IllegalArgumentException("payload is null");
            String json = raw instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : raw.toString();
            payload = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (payload == null) throw new IllegalArgumentException("payload must be an object");
        } catch (Exception ex) {
            throw new IllegalArgumentException("事件载荷不是合法 JSON 对象", ex);
        }
        Object received = row.get("received_at");
        LocalDateTime receivedAt = received instanceof Timestamp t ? t.toLocalDateTime()
                : received instanceof LocalDateTime t ? t : null;
        return new BusinessEvent(((Number) row.get("id")).longValue(), (String) row.get("event_id"),
                (String) row.get("event_type"), (String) row.get("source_system"), (String) row.get("target_system"),
                (String) row.get("aggregate_type"), String.valueOf(row.get("aggregate_id")),
                (String) row.get("trace_id"), payload, receivedAt);
    }

    private void recordFailure(Long id, String owner, Exception ex) {
        var row = ownedRow(id, owner);
        if (row == null) return;
        int retry = ((Number) row.getOrDefault("retry_count", 0)).intValue() + 1;
        String message = cut(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        jdbc.update("""
                UPDATE mfg_ops.biz_inbox SET consume_status=?, retry_count=?, error_message=?,
                    next_retry_at=IF(?='DEAD',NULL,DATE_ADD(NOW(3), INTERVAL ? SECOND)),
                    lease_until=NULL,consumer_name=NULL WHERE id=?
                """, retry > MAX_RETRY ? "DEAD" : "FAILED", retry, message,
                retry > MAX_RETRY ? "DEAD" : "FAILED", 1 << Math.min(retry, MAX_RETRY), id);
    }

    private void markDead(Long id, String reason) {
        jdbc.update("UPDATE mfg_ops.biz_inbox SET consume_status='DEAD',error_message=?,lease_until=NULL,consumer_name=NULL WHERE id=?", cut(reason), id);
    }

    private String cut(String text) { return text.substring(0, Math.min(500, text.length())); }

    /** 全部请求必须处于失败态；任一非法状态使整个重放请求回滚。 */
    public int replay(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 200 || ids.stream().anyMatch(x -> x == null || x <= 0))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "请提供 1 至 200 个有效事件 ID");
        List<Long> unique = ids.stream().distinct().sorted().toList();
        return new TransactionTemplate(transactionManager).execute(status -> {
            for (Long id : unique) {
                int changed = jdbc.update("""
                        UPDATE mfg_ops.biz_inbox SET consume_status='PENDING',retry_count=0,next_retry_at=NULL,
                            lease_until=NULL,error_message=NULL,consumer_name=NULL,consumed_at=NULL
                         WHERE id=? AND consume_status IN ('FAILED','DEAD')
                        """, id);
                if (changed != 1) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "事件不存在或不处于失败态：" + id);
            }
            return unique.size();
        });
    }

    public List<Map<String, Object>> deadLetters(int limit) {
        return jdbc.queryForList("SELECT * FROM mfg_ops.v_biz_dead_letter LIMIT ?", Math.max(1, Math.min(limit, 200)));
    }

    public Set<String> registeredEventTypes() {
        Set<String> result = new TreeSet<>();
        consumers.forEach(c -> result.addAll(c.supports()));
        return result;
    }
}
