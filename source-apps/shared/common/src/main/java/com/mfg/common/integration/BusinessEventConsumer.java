package com.mfg.common.integration;

import java.util.Set;

/**
 * 业务事件消费方（计划 00 · F2-03）。
 *
 * <p>实现类放在各系统的 {@code integration/} 包下，由 Spring 自动收集。
 * 消费调度器 {@link BusinessEventConsumerScheduler} 按 {@link #supports()} 匹配
 * inbox 中的事件并调用 {@link #consume}。
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li><b>必须幂等</b>：同一条事件重复投递只产生一次业务效果。
 *       幂等键可用 {@code event.eventId()}，或用业务自身的幂等号。</li>
 *   <li><b>抛异常即失败</b>：不要吞掉异常后返回——调度器靠异常判定失败并重试。</li>
 *   <li><b>不要自己查 inbox</b>：事件内容由参数传入，消费方只管业务效果。</li>
 *   <li><b>一个消费方一类事件</b>：{@link #supports()} 返回的事件类型应与
 *       <a href="file:../../../../../../../../docs/event-contract.md">事件路由表</a>一致。</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class WmsInspectionResultConsumer implements BusinessEventConsumer {
 *     private final InventoryTransactionService inventory;
 *
 *     public String name() { return "wms.inspection-result"; }
 *     public Set<String> supports() { return Set.of("QMS.INSPECTION.JUDGED"); }
 *
 *     public void consume(BusinessEvent event) {
 *         String key = "QMS:" + event.text("inspectionNo") + ":JUDGED";
 *         inventory.action(..., key);   // 幂等键保证重复投递不产生二次效果
 *     }
 * }
 * }</pre>
 */
public interface BusinessEventConsumer {

    /** 本消费方负责的事件类型，取值与事件路由表的 {@code event_type} 一致。 */
    Set<String> supports();

    /** 消费方标识，写入 {@code mfg_ops.biz_inbox.consumer_name}，用于排查与位点记录。 */
    String name();

    /** inbox 的目标系统码；与事件类型共同确定唯一消费方。 */
    String targetSystem();

    /**
     * 消费动作。抛出异常即视为失败，由调度器按指数退避重试，超过上限转死信。
     *
     * @param event 事件内容；实现方**不需要**自己查 inbox
     */
    void consume(BusinessEvent event);
}
