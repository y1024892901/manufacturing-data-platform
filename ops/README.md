# ops/ — 运维与演示

> 截至 2026-09-22 · 整体计划见 [PLAN.md](PLAN.md)，当前状态见 [STATUS.md](STATUS.md)

## 技术栈

**Python 3 标准库**（零第三方依赖）。三个演示脚本只用 `json` / `sys` / `urllib.request` / `subprocess` / `datetime` / `os`，`python xxx.py` 直接运行，无需 `pip install`。

## 职责

让系统**可演示**：用真实 HTTP 调用把关键业务链路跑一遍并打印结果。

它是演示与冒烟验证，**不是单元测试** —— 打的是运行中的真实服务（`http://localhost:8080`），不是 mock；也不造数（建物料/提单据只是叙事的必要步骤，批量造数归造数引擎）。

## 实际文件

```
ops/
├── demo_master_data_lifecycle.py   主数据全生命周期：草稿 → 审批 → 发布 → 分发（214 行）
├── demo_bom_approval.py            BOM 三级审批端到端，四账号接力（140 行）
├── demo_approval_boundary.py       审批引擎边界测试，6 组 / 11 项断言（154 行）
├── scripts/                        启停、重置、演示准备脚本（未实现，仅 README）
├── sql/                            巡检、统计、排障用 SQL（未实现，仅 README）
├── monitoring/                     运行时指标与告警（未实现，仅 README）
└── backup/                         数据库备份与恢复（未实现，仅 README）
```

## 前置条件

三个脚本相同，缺一不可：

1. MySQL 8.0.43 已启动（`infra\mysql8\start-mysql8.bat`）；
2. `infra/db-init/` 的 11 个 SQL 脚本已按序号执行完毕；
3. 10 个 Java 源系统已在 8080 端口运行。

任一没起来，脚本都会以「登录失败」告终。演示账号统一口令 `Test@123456`，脚本内以常量 `PASSWORD` 写出（演示账号，可接受）。

## 三个演示脚本

### 1. `demo_master_data_lifecycle.py` —— 主数据全生命周期（★ 最有演示价值）

```bash
python ops/demo_master_data_lifecycle.py
```

让主数据从草稿一路走到业务系统：建物料 → 提交审批 → 两级审批 → 发布 → 分发。

核心看点是**前后对比**：草稿状态下「业务系统可选物料」接口查不到它，审批通过后同一接口就能查到。末尾直连数据库（`subprocess` 调 `D:/mysql8/bin/mysql.exe`）逐张校验 7 个业务系统的副本表，并打印 `src_mdm.md_distribution_log` 的分发日志。

物料编码取当前时间戳（`M-` + `%m%d%H%M%S`），**可重复运行**；若编码已存在则跳过创建、复用旧 id。

### 2. `demo_bom_approval.py` —— BOM 三级审批端到端

```bash
python ops/demo_bom_approval.py
```

四个账号接力走完整条链，每级都打印审批意见与流转结果：

```
zhaoliu（赵六/工艺工程师）提交
   → zhoutao（周涛/工艺主管）  同意
   → yangfan（杨帆/生产主管）  同意
   → zhengshuang（郑爽/成本会计） 同意
```

末尾打印**审批时间轴**（演示核心视觉）与实例的最终状态、提交人、总耗时。任一步失败立即 `sys.exit(1)`，退出码可供批处理判断。

### 3. `demo_approval_boundary.py` —— 审批引擎边界测试

```bash
python ops/demo_approval_boundary.py
```

6 组共 11 项断言，逐项以 `[PASS]` / `[FAIL]` 打印，结尾汇总 `结果: N/11 通过`：权限边界、驳回必须填写理由、驳回退回提交人、同一单据重复提交保护（幂等）、撤回、审批时间轴完整性。

> ⚠️ 该脚本失败时**不返回非 0 退出码**，无法被 CI 据此判定失败（缺口详见 [STATUS.md](STATUS.md)）。

## 未实现（规划中的四个子目录）

| 子目录 | 规划用途 | 状态 |
|---|---|---|
| `scripts/` | 启停（`start.sh`）、重置（`reset.sh`）、演示准备（`demo_prep.sh`） | ❌ 仅 README |
| `sql/` | 巡检、统计、排障用 SQL | ❌ 仅 README |
| `monitoring/` | 运行时指标与告警规则 | ❌ 仅 README |
| `backup/` | 数据库备份与恢复 | ❌ 仅 README |

「一键重置、演示可反复重来」这条核心承诺目前**没有实现**，重置只能重跑 `infra/db-init/` 的 SQL。

> 逐脚本说明与缺口清单见 [STATUS.md](STATUS.md)。
