package com.mfg.common.api;

import lombok.Getter;

/**
 * 业务错误码。
 *
 * <p>分段约定，便于前端与 AI 按区间判断错误类型：
 * <ul>
 *   <li>10xxx —— 通用参数与请求错误</li>
 *   <li>20xxx —— 认证与权限</li>
 *   <li>30xxx —— 主数据相关（状态机、审批）</li>
 *   <li>40xxx —— 业务单据相关</li>
 *   <li>50xxx —— 审批流程相关</li>
 *   <li>90xxx —— 系统内部错误</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    // ---------- 通用 ----------
    PARAM_INVALID(10001, "参数校验失败"),
    RESOURCE_NOT_FOUND(10002, "资源不存在"),
    /** 业务规则冲突，如「库存不足」「数量超限」 */
    BUSINESS_CONFLICT(10003, "业务规则冲突"),
    DUPLICATE_KEY(10004, "唯一键冲突"),

    // ---------- 认证与权限 ----------
    UNAUTHORIZED(20001, "未登录或登录已过期"),
    FORBIDDEN(20002, "无权限执行此操作"),
    /** 数据范围越权：能看到但无权访问该条数据 */
    DATA_SCOPE_DENIED(20003, "超出可访问的数据范围"),
    /** 无权访问该业务系统 */
    SYSTEM_NO_ACCESS(20004, "无该系统的访问权限"),

    // ---------- 主数据 ----------
    MASTER_DATA_NOT_FOUND(30001, "主数据不存在"),
    /** 关键：只有 PUBLISHED 状态的主数据才能被业务系统消费 */
    MASTER_DATA_NOT_PUBLISHED(30002, "该主数据尚未发布，不能引用"),
    MASTER_DATA_INVALID_STATE(30003, "当前状态不允许此操作"),
    MASTER_DATA_ALREADY_EXISTS(30004, "主数据已存在"),
    /** 一客多码检测命中 */
    MASTER_DATA_DUPLICATE_SUSPECT(30005, "检测到疑似重复的主数据"),

    // ---------- 业务单据 ----------
    ORDER_STATE_INVALID(40001, "单据状态不允许此操作"),
    INVENTORY_INSUFFICIENT(40002, "库存不足"),
    QUANTITY_EXCEEDED(40003, "数量超出允许范围"),

    // ---------- 审批流程 ----------
    WORKFLOW_NOT_FOUND(50001, "审批流程不存在"),
    WORKFLOW_TASK_NOT_FOUND(50002, "审批任务不存在或已处理"),
    WORKFLOW_NOT_YOUR_TASK(50003, "该待办不属于当前用户"),
    WORKFLOW_ALREADY_FINISHED(50004, "流程已结束"),
    /** 驳回必须填写原因 */
    WORKFLOW_OPINION_REQUIRED(50005, "驳回必须填写审批意见"),

    // ---------- 系统 ----------
    INTERNAL_ERROR(90001, "系统内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
