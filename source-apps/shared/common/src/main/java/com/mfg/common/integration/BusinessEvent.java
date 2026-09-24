package com.mfg.common.integration;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 投递到 {@code mfg_ops.biz_inbox} 的一条业务事件。
 *
 * <p>由 {@link BusinessEventConsumerScheduler} 从 inbox 行构造后交给消费方，
 * 消费方不需要（也不应该）自己查表。
 *
 * @param inboxId      inbox 行主键，可用于消费方写位点
 * @param eventId      事件唯一标识；与 {@code targetSystem} 一起构成幂等键
 * @param eventType    事件类型，形如 {@code WMS.RECEIPT.PENDING_INSPECTION}
 * @param sourceSystem 源系统码（小写）
 * @param targetSystem 目标系统码（小写）
 * @param aggregateType 聚合类型，如 {@code RECEIPT}
 * @param aggregateId  聚合标识，如收货单号
 * @param traceId      链路标识
 * @param payload      载荷（自由 JSON，本阶段无 schema 校验）
 * @param receivedAt   投递到 inbox 的时间
 */
public record BusinessEvent(
        Long inboxId,
        String eventId,
        String eventType,
        String sourceSystem,
        String targetSystem,
        String aggregateType,
        String aggregateId,
        String traceId,
        Map<String, Object> payload,
        LocalDateTime receivedAt
) {

    /** 从载荷里取字符串字段，缺失时返回 null——避免消费方到处判空。 */
    public String text(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
