package com.mfg.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 统一 API 响应包装。
 *
 * <p>全项目所有接口统一返回此结构，前端与 AI 工具调用都按同一约定解析，
 * 避免"有的接口返回数组、有的返回对象"这类不一致问题。
 *
 * @param <T> 业务数据类型
 */
@Schema(description = "统一响应结构")
public record ApiResponse<T>(
        @Schema(description = "业务状态码，0 表示成功", example = "0")
        int code,

        @Schema(description = "提示信息", example = "success")
        String message,

        @Schema(description = "业务数据")
        T data,

        @Schema(description = "服务端时间")
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, LocalDateTime.now());
    }
}
