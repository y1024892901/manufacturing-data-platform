package com.mfg.common.exception;

import com.mfg.common.api.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>所有可预期的业务失败都应抛出此异常，由 {@code GlobalExceptionHandler}
 * 统一转换为 {@code ApiResponse}，避免在 Controller 里写满 try-catch。
 *
 * <p>示例：
 * <pre>{@code
 * throw BizException.of(ErrorCode.MASTER_DATA_NOT_PUBLISHED,
 *         "物料 " + code + " 当前状态为 " + status + "，需审批发布后才能引用");
 * }</pre>
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    /** 资源不存在 */
    public static BizException notFound(String what, Object id) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND, what + " 不存在：" + id);
    }

    /** 业务规则冲突 */
    public static BizException conflict(String message) {
        return new BizException(ErrorCode.BUSINESS_CONFLICT, message);
    }

    /** 状态机不允许 */
    public static BizException badState(String message) {
        return new BizException(ErrorCode.MASTER_DATA_INVALID_STATE, message);
    }
}
