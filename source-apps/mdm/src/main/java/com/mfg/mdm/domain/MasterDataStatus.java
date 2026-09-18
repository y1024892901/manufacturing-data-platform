package com.mfg.mdm.domain;

import lombok.Getter;

/**
 * 主数据状态机。
 *
 * <p><b>核心业务规则：只有 {@link #PUBLISHED} 状态的主数据才能被 9 个业务系统消费。</b>
 *
 * <pre>
 *             提交审批                审批通过              发布
 *   DRAFT ───────────► PENDING ───────────► APPROVED ─────────► PUBLISHED
 *     ▲                    │                                       │
 *     │                 驳回(退回)                                 │ 发起变更
 *     └────────────────────┘                                       ▼
 *                                                            CHANGING ──┐
 *                                                                  │    │ 审批通过
 *                                                                  └────┘
 *   任意状态 ──► DISABLED（停用，不再可被引用）
 * </pre>
 *
 * <p>演示价值：在 DRAFT 的物料，业务系统新建单据时<b>在物料下拉框里找不到</b>。
 * 这个前后对比最能证明主数据管控是真实生效的。
 */
@Getter
public enum MasterDataStatus {

    /** 草稿：可自由编辑，业务系统不可见 */
    DRAFT("草稿", "可编辑，下游系统不可见"),

    /** 审批中：已提交，等待审批，不可编辑 */
    PENDING("审批中", "已提交，等待审批，不可编辑"),

    /** 已批准：审批通过但尚未正式生效（预留中间态） */
    APPROVED("已批准", "审批通过，待发布"),

    /** 已发布：★ 唯一可被业务系统引用的状态 */
    PUBLISHED("已发布", "★ 下游系统可引用"),

    /** 变更中：已发布数据正在走变更审批，旧版本仍可用 */
    CHANGING("变更中", "变更审批中，当前版本仍可引用"),

    /** 已驳回：退回提交人，可重新编辑提交 */
    REJECTED("已驳回", "被驳回，可修改后重新提交"),

    /** 已停用：不再允许被引用 */
    DISABLED("已停用", "不可再被引用");

    private final String label;
    private final String description;

    MasterDataStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** 业务系统能否引用该状态的主数据 */
    public boolean isConsumable() {
        // 变更中的记录，其已发布版本仍然可用
        return this == PUBLISHED || this == CHANGING;
    }

    /** 是否可编辑 */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /** 是否正在审批流程中 */
    public boolean isInApproval() {
        return this == PENDING || this == CHANGING;
    }

    public static MasterDataStatus of(String name) {
        if (name == null || name.isBlank()) {
            return DRAFT;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DRAFT;
        }
    }
}
