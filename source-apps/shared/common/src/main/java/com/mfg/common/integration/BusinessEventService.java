package com.mfg.common.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/** 单进程演示环境中的业务 Outbox/Inbox，总线仍保留可观察、幂等和重试语义。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessEventService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    public String publish(String eventType, String sourceSystem, String targetSystem,
                          String aggregateType, Object aggregateId, Map<String, ?> payload) {
        String eventId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO mfg_ops.biz_outbox
                    (event_id,event_type,source_system,target_system,aggregate_type,aggregate_id,trace_id,payload)
                    VALUES(?,?,?,?,?,?,?,CAST(? AS JSON))
                    """, eventId, eventType, sourceSystem, targetSystem, aggregateType,
                    String.valueOf(aggregateId), traceId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("业务事件写入失败: " + eventType, e);
        }
        return eventId;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void dispatch() {
        for (Map<String, Object> event : jdbc.queryForList("""
                SELECT * FROM mfg_ops.biz_outbox
                WHERE status IN ('PENDING','RETRYING') AND retry_count < 3
                ORDER BY occurred_at LIMIT 50 FOR UPDATE
                """)) {
            try {
                jdbc.update("""
                        INSERT IGNORE INTO mfg_ops.biz_inbox
                        (event_id,event_type,source_system,target_system,aggregate_type,aggregate_id,trace_id,payload)
                        VALUES(?,?,?,?,?,?,?,CAST(? AS JSON))
                        """, event.get("event_id"), event.get("event_type"), event.get("source_system"),
                        event.get("target_system"), event.get("aggregate_type"), event.get("aggregate_id"),
                        event.get("trace_id"), event.get("payload"));
                jdbc.update("UPDATE mfg_ops.biz_outbox SET status='SUCCESS',processed_at=NOW(3),error_message=NULL WHERE id=?", event.get("id"));
            } catch (Exception ex) {
                jdbc.update("""
                        UPDATE mfg_ops.biz_outbox SET retry_count=retry_count+1,
                        status=IF(retry_count+1>=3,'DEAD','RETRYING'),error_message=? WHERE id=?
                        """, cut(ex.getMessage()), event.get("id"));
                log.warn("业务事件分发失败 eventId={}: {}", event.get("event_id"), ex.getMessage());
            }
        }
    }

    public Page<Map<String, Object>> page(String status, int page, int size) {
        int p = Math.max(0, page - 1);
        int s = Math.min(200, Math.max(1, size));
        boolean filtered = status != null && !status.isBlank();
        String where = filtered ? " WHERE status=?" : "";
        Object[] args = filtered ? new Object[]{status} : new Object[]{};
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mfg_ops.biz_outbox" + where, Long.class, args);
        List<Object> params = new ArrayList<>(Arrays.asList(args));
        params.add(s);
        params.add(p * s);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM mfg_ops.biz_outbox" + where + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                params.toArray());
        return new PageImpl<>(rows, PageRequest.of(p, s), total == null ? 0 : total);
    }

    public Map<String, Object> detail(String eventId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(jdbc.queryForMap(
                "SELECT * FROM mfg_ops.biz_outbox WHERE event_id=?", eventId));
        result.put("inbox", jdbc.queryForList(
                "SELECT * FROM mfg_ops.biz_inbox WHERE event_id=? ORDER BY target_system", eventId));
        return result;
    }

    @Transactional
    public void retry(String eventId) {
        int changed = jdbc.update("""
                UPDATE mfg_ops.biz_outbox SET status='RETRYING',retry_count=0,error_message=NULL
                WHERE event_id=? AND status IN ('FAILED','DEAD','RETRYING')
                """, eventId);
        if (changed == 0) {
            throw new IllegalStateException("只有失败或待人工处理的事件可以重试");
        }
    }

    private String cut(String value) {
        return value == null ? null : value.substring(0, Math.min(500, value.length()));
    }
}
