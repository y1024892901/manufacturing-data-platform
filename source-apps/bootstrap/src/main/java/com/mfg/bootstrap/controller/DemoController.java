package com.mfg.bootstrap.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.workflow.dto.StartApprovalRequest;
import com.mfg.workflow.entity.WfInstance;
import com.mfg.workflow.service.ApprovalEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 演示辅助接口。
 *
 * <p>在 MDM 模块的业务提交接口完成前，用这里模拟「提交 BOM 变更申请」，
 * 以便端到端验证审批引擎。MDM 模块就绪后，真实提交会走
 * {@code POST /api/mdm/bom/{id}/change-request}，本接口保留作为演示兜底。
 */
@Tag(name = "99. 演示辅助", description = "端到端验证审批链路的临时入口")
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final ApprovalEngine approvalEngine;

    /**
     * 模拟业务单据 ID 分配。
     *
     * <p>起点取当前时间戳而非固定值：应用重启后若从固定值重新开始，
     * 会与重启前已存在的实例撞 bizId，被幂等保护误判为"重复提交"。
     * 真实系统中 bizId 由数据库自增，不存在此问题。
     */
    private static final AtomicLong DEMO_BIZ_ID =
            new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    @Operation(summary = "模拟提交 BOM 变更申请",
            description = "发起一条 BOM 三级审批（工艺主管 → 生产主管 → 成本会计），用于验证审批链路")
    @PostMapping("/bom-change")
    public ApiResponse<Map<String, Object>> submitBomChange(
            @RequestParam(defaultValue = "BOM-MOTOR-001") String bomCode,
            @RequestParam(defaultValue = "M-2043") String materialCode,
            @RequestParam(defaultValue = "3") String oldQty,
            @RequestParam(defaultValue = "4") String newQty,
            @RequestParam(defaultValue = "客户反馈装配强度不足，单位用量由 3 调整为 4") String reason) {

        if (!CurrentUser.find().isPresent()) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        long bizId = DEMO_BIZ_ID.incrementAndGet();

        // 提交时的数据快照：审批人看到的是这一刻的内容
        String snapshot = """
                {
                  "bomCode": "%s",
                  "materialCode": "%s",
                  "changeType": "UPDATE",
                  "oldQtyPer": "%s",
                  "newQtyPer": "%s",
                  "changeReason": "%s"
                }
                """.formatted(bomCode, materialCode, oldQty, newQty, reason);

        WfInstance instance = approvalEngine.start(new StartApprovalRequest(
                "BOM",
                bizId,
                bomCode,
                bomCode + " · 用量变更 " + oldQty + " → " + newQty,
                snapshot));

        return ApiResponse.ok(Map.of(
                "instanceId", instance.getId(),
                "instanceNo", instance.getInstanceNo(),
                "bizId", bizId,
                "status", instance.getStatus(),
                "currentNodeSeq", instance.getCurrentNodeSeq() == null ? 0 : instance.getCurrentNodeSeq(),
                "submitter", instance.getSubmitter(),
                "message", "已提交，等待【工艺主管】审批"));
    }
}
