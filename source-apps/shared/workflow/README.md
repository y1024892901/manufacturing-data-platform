# shared/workflow/ — 自研审批引擎

## 技术栈
Java 17 · Spring Boot 3.2 · Spring Data JPA · Spring AOP（方法级鉴权）

## 职责
按流程定义推进审批，**只负责推流程，不认识任何业务概念**。业务后果由各模块实现
`ApprovalCallback` 承接。

## 状态机

```
            提交
   DRAFT ─────────► RUNNING ──(逐节点通过)──► APPROVED
                      │
                      ├──(驳回 BACK)──► REJECTED ──► 业务单据回到草稿
                      ├──(驳回 PREV)──► 退回上一节点，流程继续
                      └──(撤回)────────► CANCELED
```

## 核心设计

### 1. 任务按「角色」创建，不按「人」

节点定义的是"工艺主管审核"，而不是"周涛审核"。谁登录进来领取，就归属谁。

**收益**：人事变动（周涛调岗）时无需改流程配置，新人接岗即可审批。

### 2. 提交时固化数据快照

`wf_instance.biz_snapshot` 保存提交那一刻的业务数据（JSON）。
审批人看到的是**提交时的值**，而非当前值——否则提交后再改数据，
审批意见就失去意义了。

### 3. 提交幂等保护

同一 `bizType + bizId` 已有 RUNNING 实例时拒绝重复提交，
避免审批人看到两条一模一样的待办。

### 4. 驳回必须填理由

`reject_action=BACK` 时退回提交人；否则提交人不知道要改什么。
引擎层强制校验，不依赖前端。

### 5. 流程终点的独立动作

流程走完时记录 `FINISH` 而非 `APPROVE`。
若用 APPROVE，时间轴会出现两条连续的同意，看起来像同一个人批了两次。

## 七个可扩展点：ApprovalCallback

业务模块实现此接口接入引擎，引擎零改动：

```java
@Component
public class BomApprovalCallback implements ApprovalCallback {

    public boolean supports(String bizType) { return "BOM".equals(bizType); }

    /** 三级审批全通过 → 发布 BOM + 分发到 ERP/MES/PLM */
    public void onApproved(WfInstance inst) {
        bomService.publish(inst.getBizId());
        distributeService.distribute(bom);
    }

    /** 被驳回 → 单据回到草稿，记录驳回原因 */
    public void onRejected(WfInstance inst, String reason) {
        bomService.markRejected(inst.getBizId(), reason);
    }
}
```

回调执行失败**不影响审批主流程**（审批结果已落库，业务问题单独排查）。

## 已配置的 7 条审批链

| 流程编码 | 审批链 | 级别 |
|---|---|---|
| `MDM_CUSTOMER_NEW` | 销售主管 → 应收会计（信用核查） | 两级 |
| `MDM_SUPPLIER_NEW` | 供应商质量工程师 → 采购主管 | 两级 |
| `MDM_MATERIAL_NEW` | 工艺主管 → 生产计划员 | 两级 |
| **`MDM_BOM_CHANGE`** | **工艺主管 → 生产主管 → 成本会计** | **三级** |
| `MDM_ROUTING_CHANGE` | 生产主管 | 单级 |
| `MDM_CREDIT_CHANGE` | 财务主管 → 财务总监 | 两级 |
| `ERP_PROD_ORDER_RELEASE` | 生产主管 | 单级 |

**新增审批流只需插两条数据**（`wf_definition` + `wf_node`），不改代码。

## 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/workflow/tasks/pending` | 我的待办（按角色匹配） |
| GET | `/api/workflow/tasks/pending/count` | 待办数量（角标） |
| POST | `/api/workflow/tasks/{id}/approve` | 同意 |
| POST | `/api/workflow/tasks/{id}/reject` | 驳回（意见必填） |
| POST | `/api/workflow/instances/{id}/cancel` | 撤回（仅提交人） |
| GET | `/api/workflow/instances/{id}/timeline` | **审批时间轴** |
| GET | `/api/workflow/instances/history` | 单据的审批历史 |
| GET | `/api/workflow/definitions` | 全部流程定义 |

## 权限边界

审批接口标注 `@PreAuthorize("hasAuthority('WF:TASK:APPROVE')")`，
且引擎内部还会二次校验「当前用户是否持有该任务所需的角色」。

**双重校验的理由**：有 `WF:TASK:APPROVE` 权限（是某类主管）不代表
能审任意节点——生产主管不能审工艺主管的待办。

## 验证脚本

| 脚本 | 覆盖 |
|---|---|
| `ops/demo_bom_approval.py` | BOM 三级审批四账号接力完整走通 |
| `ops/demo_approval_boundary.py` | 11 项边界测试（权限、驳回、幂等、撤回、时间轴） |

```bash
python ops/demo_bom_approval.py       # 演示主线
python ops/demo_approval_boundary.py  # 边界回归
```
