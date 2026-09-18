package com.mfg.workflow.callback;

import com.mfg.workflow.entity.WfInstance;

/**
 * 审批回调 —— 业务模块接入审批引擎的扩展点。
 *
 * <p>审批引擎本身不认识「物料」「BOM」「客户」这些业务概念，
 * 它只负责推流程。业务后果（把状态改成 PUBLISHED、触发分发、发通知等）
 * 由各业务模块实现本接口来承接。
 *
 * <p>用 Spring 的依赖注入收集全部实现者（{@code List<ApprovalCallback>}），
 * 按 {@link #supports} 匹配业务类型后调用。这样新增业务类型时
 * 只需加一个实现类，引擎零改动。
 *
 * <p>实现示例（MDM 模块）：
 * <pre>{@code
 * @Component
 * public class BomApprovalCallback implements ApprovalCallback {
 *     public boolean supports(String bizType) { return "BOM".equals(bizType); }
 *
 *     public void onApproved(WfInstance inst) {
 *         bomService.publish(inst.getBizId());     // 状态 → PUBLISHED
 *         distributeService.distribute(bom);       // 分发到 ERP / MES / PLM
 *     }
 *
 *     public void onRejected(WfInstance inst, String reason) {
 *         bomService.markRejected(inst.getBizId(), reason);
 *     }
 * }
 * }</pre>
 */
public interface ApprovalCallback {

    /**
     * 是否处理该业务类型。
     *
     * @param bizType CUSTOMER / SUPPLIER / MATERIAL / BOM / ROUTING / PROD_ORDER
     */
    boolean supports(String bizType);

    /**
     * 流程全部通过。
     *
     * <p>调用时机：最后一个节点审批通过之后。
     * 此时 {@code instance.status} 已置为 APPROVED。
     */
    void onApproved(WfInstance instance);

    /**
     * 流程被驳回（退回到提交人）。
     *
     * <p>实现方应把业务单据状态改回可编辑（如 DRAFT），
     * 并把驳回原因记录到业务单据上，供提交人查看。
     *
     * @param reason 驳回意见（来自审批人，必填）
     */
    void onRejected(WfInstance instance, String reason);

    /**
     * 提交成功（可选实现）。
     *
     * <p>默认空实现 —— 多数业务在提交时自己已处理好单据状态，
     * 此回调用于需要额外动作的场景（如发通知给审批人）。
     */
    default void onSubmitted(WfInstance instance) {
        // 默认不做任何事
    }

    /**
     * 节点流转（可选实现）。
     *
     * <p>用于需要感知"走到了第几步"的场景，如把当前节点名
     * 回写到业务单据上展示给提交人看。
     *
     * @param nodeName 刚刚进入的节点名称
     */
    default void onNodeEntered(WfInstance instance, String nodeName) {
        // 默认不做任何事
    }
}
