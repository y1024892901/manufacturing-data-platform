package com.mfg.common.exception;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>把各类异常统一转换为 {@link ApiResponse}，保证：
 * <ul>
 *   <li>前端只需处理一种响应结构</li>
 *   <li>错误信息是"人话"，可直接展示给业务用户</li>
 *   <li>校验失败时能指出具体是哪个字段出错</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，返回 200 + 业务错误码，不打 error 日志 */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("业务异常 [{}] {} -> {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return ApiResponse.fail(e.getErrorCode(), e.getMessage());
    }

    /** 方法级鉴权异常不能落入通用 500 兜底。 */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(org.springframework.security.access.AccessDeniedException e) {
        return ApiResponse.fail(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage());
    }

    /** 请求体字段校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, detail);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBind(BindException e) {
        String detail = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, detail);
    }

    /**
     * 唯一键冲突。
     *
     * <p>主数据编码唯一性冲突会走到这里（如重复的客户编码），
     * 属于业务问题而非系统故障，因此单独处理。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<Void> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("唯一键或完整性约束冲突: {}", e.getMostSpecificCause().getMessage());
        return ApiResponse.fail(ErrorCode.DUPLICATE_KEY,
                "数据唯一性冲突，请检查编码是否已存在");
    }

    /** 兜底：未预期的异常，打印完整堆栈便于排障 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleOther(Exception e, HttpServletRequest req) {
        log.error("系统异常 [{}] {}", req.getMethod(), req.getRequestURI(), e);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR,
                "系统内部错误，请联系管理员（已记录日志）");
    }
}
