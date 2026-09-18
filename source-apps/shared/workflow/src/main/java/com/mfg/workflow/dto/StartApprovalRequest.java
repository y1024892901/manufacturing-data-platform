package com.mfg.workflow.dto;

/**
 * 发起审批的入参。
 *
 * <p>由业务模块在「提交」动作中构造并交给 {@code ApprovalEngine}。
 *
 * @param bizType     业务类型，决定走哪条审批链（CUSTOMER / SUPPLIER / MATERIAL / BOM / ROUTING）
 * @param bizId       业务单据 ID
 * @param bizNo       业务单号（物料编码、BOM 编码等），列表展示用
 * @param bizTitle    业务标题，如「M-2099 高速轴承 · 新增申请」
 * @param bizSnapshot 提交时的业务数据快照（JSON 字符串）。
 *                    审批人看到的是提交那一刻的数据，避免提交后篡改。
 */
public record StartApprovalRequest(
        String bizType,
        Long bizId,
        String bizNo,
        String bizTitle,
        String bizSnapshot
) {

    public static StartApprovalRequest of(String bizType, Long bizId,
                                          String bizNo, String bizTitle) {
        return new StartApprovalRequest(bizType, bizId, bizNo, bizTitle, null);
    }
}
