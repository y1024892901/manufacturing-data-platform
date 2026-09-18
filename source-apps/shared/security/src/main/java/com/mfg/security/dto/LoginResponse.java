package com.mfg.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 登录响应。
 *
 * <p>返回的信息足以让前端渲染出「这个人是谁、属于哪个部门、能进哪些系统、能做什么」，
 * 无需二次请求。演示时切换账号后的差异一目了然。
 */
@Schema(description = "登录响应")
public record LoginResponse(

        @Schema(description = "JWT 令牌")
        String token,

        @Schema(description = "令牌有效期（分钟）")
        long expireMinutes,

        @Schema(description = "用户 ID")
        Long userId,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "姓名")
        String realName,

        @Schema(description = "部门编码")
        String deptCode,

        @Schema(description = "部门名称")
        String deptName,

        @Schema(description = "岗位")
        String positionName,

        @Schema(description = "角色编码列表")
        List<String> roles,

        @Schema(description = "角色中文名列表（前端展示）")
        List<String> roleNames,

        @Schema(description = "可访问系统列表")
        List<String> systems,

        @Schema(description = "权限码列表")
        List<String> permissions
) {
}
