package com.mfg.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 演示账号信息（登录页的「一键切换账号」面板数据源）。
 *
 * <p>演示价值：把 36 个账号按部门列出来，观众能直观看到
 * 「同一个系统里有这么多不同岗位」，点一下就能切换身份，
 * 比手动输账号密码流畅得多。
 *
 * <p>注意：此接口只返回账号信息，**不返回密码哈希**。
 * 演示密码在登录页以提示形式展示。
 */
@Schema(description = "演示账号")
public record DemoAccount(
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

        @Schema(description = "角色中文名")
        List<String> roleNames,

        @Schema(description = "可访问系统")
        List<String> systems
) {
}
