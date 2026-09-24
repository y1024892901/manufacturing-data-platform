# ops/ — 目前进展

## 2026-09-23 执行更新

审批边界脚本新增 main 守卫，可安全导入，失败继续返回非零。新增 run_foundation_smoke.py 与 tests/BusinessEventDatabaseSmoke.java；数据库烟测使用事务回滚，现已通过。静态权限缺口为 0，已有实体列缺口为 0；123 张无实体表仍需逐表登记，严格实体检查尚未通过。

以下为 2026-09-22 基线详情，涉及上述内容时以本节为准。

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**3 个演示脚本已全部实现（共 508 行，纯 Python 标准库、零依赖、可直接运行）**，覆盖主数据全生命周期、BOM 三级审批、审批引擎边界三条叙事线；`README.md` 已改写同步（技术栈、三个脚本的用途与运行方式、四个未实现子目录均已如实说明）；但四个规划子目录（`scripts/` `sql/` `monitoring/` `backup/`）**全部是空骨架，无一份实现代码**。

## 文件清单

| 文件 | 行数 | 说明 |
|---|---|---|
| `README.md` | 90 | ✅ 已同步 —— 技术栈一栏为 Python 3 标准库（零第三方依赖）；正文以 3 个**已实现**的演示脚本为主（逐个说明用途与运行方式、前置条件、退出码行为），四个未实现子目录明确标注「仅 README」；格式对齐 `infra/README.md` |
| `demo_master_data_lifecycle.py` | 214 | ✅ **已实现**。主数据全生命周期：草稿 → 审批 → 发布 → 分发到 9 个业务系统 |
| `demo_bom_approval.py` | 140 | ✅ **已实现**。BOM 三级审批端到端，四账号接力 |
| `demo_approval_boundary.py` | 154 | ✅ **已实现**。审批引擎边界测试，6 组共 11 项断言 |
| `scripts/README.md` | 7 | ❌ 未实现 —— 骨架目录，无实现代码 |
| `sql/README.md` | 7 | ❌ 未实现 —— 骨架目录，无实现代码 |
| `monitoring/README.md` | 7 | ❌ 未实现 —— 骨架目录，无实现代码 |
| `backup/README.md` | 7 | ❌ 未实现 —— 骨架目录，无实现代码 |

> 判定依据：三个脚本均有完整的 `main()` 与端到端调用链，**不是骨架**；四个子目录下除 README 外无任何文件。

## 关键机制 / 使用方式

### 共同点：这三个脚本怎么跑

```bash
python ops/demo_master_data_lifecycle.py
python ops/demo_bom_approval.py
python ops/demo_approval_boundary.py
```

| 项 | 值 |
|---|---|
| 目标服务 | `http://localhost:8080`（`source-apps/` 的 Spring Boot，对应 `.env` 的 `SOURCE_API_PORT`） |
| 依赖库 | **无** —— 只用 `json` / `sys` / `urllib.request` / `subprocess` / `datetime` / `os`，无需 `pip install` |
| 登录口令 | 演示账号统一 `Test@123456`，脚本内以常量 `PASSWORD` 写出（演示账号，可接受） |
| 认证方式 | `POST /api/auth/login` 取 `token`，后续请求带 `Authorization: Bearer <token>` |

**前置条件（三者相同）**：MySQL 8.0.43 已启动 → `infra/db-init/` 的 11 个脚本已执行 → 10 个 Java 源系统已在 8080 端口运行。任一没起来脚本都会以「登录失败」告终。

### 1. `demo_master_data_lifecycle.py` —— 主数据全生命周期（★ 最有演示价值）

演示叙事（脚本内分 7 段，段落号在注释里）：

| 段 | 动作 | 看点 |
|---|---|---|
| ① | 工艺工程师 `zhaoliu` 建物料 `M-<月日时分秒>` | 状态为 `DRAFT` |
| ② | 查「业务系统可选物料」（`GET /api/mdm/materials/consumable`） | **看不到该物料 ← 关键对比** |
| ③ | 提交审批（`POST /api/mdm/materials/{id}/submit`） | 工艺主管待办 +1 |
| ④ | 工艺主管 `zhoutao` 同意 → 生产计划员 `sunqi` 同意 | 流程通过 |
| ⑤ | 再查「可选物料」 | **出现了 ← 前后对比** |
| ⑥ | 查分发结果 | 已推送到 7 个业务系统 |
| ⑦ | **直连数据库**校验副本表 | 在 `src_erp`/`src_mes`/`src_wms`/`src_qms`/`src_srm`/`src_plm`/`src_eam` 的 `*_md_material` 表里逐张确认该物料存在，并打印 `src_mdm.md_distribution_log` 的分发日志 |

**可重复运行**：物料编码取当前时间戳（`M-` + `%m%d%H%M%S`），重复执行不会撞「审批中」记录；若编码已存在则跳过创建、复用旧 id。

**第 ⑦ 段是唯一的例外** —— 它通过 `subprocess` 调 `D:/mysql8/bin/mysql.exe` 直连数据库查询，因此需要 MySQL 在 3306 上运行。

### 2. `demo_bom_approval.py` —— BOM 三级审批端到端

四个账号接力走完整条链，每级都打印审批意见与流转结果：

```
zhaoliu(赵六/工艺工程师) 提交
   → zhoutao(周涛/工艺主管)  同意
   → yangfan(杨帆/生产主管)  同意
   → zhengshuang(郑爽/成本会计) 同意
```

末尾打印 **审批时间轴**（演示核心视觉）—— 逐条列出「时间 / 操作人 / 动作 / 节点 / 耗时 / 意见」，最后输出实例号、业务标题、最终状态、提交人、总耗时，并断言最终状态为 `APPROVED`。

用固定编码 `BOM-MOTOR-001` / 物料 `M-2043`；任一步返回非 0 立即 `sys.exit(1)`。

### 3. `demo_approval_boundary.py` —— 审批引擎边界测试（6 组 / 11 项断言）

每一项以 `[PASS]` / `[FAIL]` 打印，结尾汇总 `结果: N/11 通过`。脚本内 6 个分组及各自断言：

| 组 | 断言 |
|---|---|
| 【1】权限边界（第 68 行） | ① 销售代表看不到自己不相关角色的待办 ② 无 `WF:TASK:APPROVE` 权限时返回 `20002` 无权限 |
| 【2】驳回必须填写理由（85） | 空意见驳回被拒 |
| 【3】驳回退回提交人（90） | ① 驳回成功且状态=`REJECTED` ② 驳回后下游节点不再有待办 |
| 【4】同一单据重复提交保护（幂等）（99） | 同一业务单据最多一个进行中的实例 |
| 【5】撤回（122） | ① 提交人可撤回（状态→`CANCELED`）② 非提交人不能撤回 |
| 【6】审批时间轴完整性（134） | ① 时间轴含提交 + 三次同意 ② 含全部审批意见 ③ 流程最终状态 = `APPROVED` |

为互不干扰，每一组用独立的业务编码（`BOM-T5-001`、`BOM-T6-001` 等）新建流程实例。

### 用到的接口

三个脚本共用的后端端点（均在 8080 的 Java 服务上）：

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/auth/login` | 登录取 token |
| POST | `/api/demo/bom-change` | **演示专用**：发起一张 BOM 变更单（`demo_bom_approval` / `demo_approval_boundary` 依赖） |
| GET | `/api/workflow/tasks/pending` | 当前用户待办 |
| POST | `/api/workflow/tasks/{taskId}/approve` | 审批（同意/驳回 + 意见） |
| GET | `/api/workflow/instances/{id}` | 实例详情（状态、耗时） |
| GET | `/api/workflow/instances/{id}/timeline` | 审批时间轴 |
| GET | `/api/workflow/instances/history` | 实例历史 |
| POST | `/api/workflow/instances/{id}/cancel` | 撤回 |
| GET | `/api/mdm/materials` | 物料分页查询 |
| GET | `/api/mdm/materials/consumable` | **仅已发布**物料（前后对比的关键） |
| GET | `/api/mdm/materials/{id}` | 物料详情 |
| POST | `/api/mdm/materials` | 新建物料（草稿） |
| POST | `/api/mdm/materials/{id}/submit` | 提交审批 |

## 未实现 / 缺口

| # | 缺口 | 影响 | 说明 |
|---|---|---|---|
| 1 | **明文数据库口令硬编码在脚本里** | **高（安全问题）** | `demo_master_data_lifecycle.py` 第 190、205 行两处把 MySQL `root` 口令以字面量写死（形如 `"MYSQL_PWD": "<口令明文>"`，此处不复述具体值），直接把口令提交进了仓库 —— 与本项目「口令只存在于 `.env`、密钥绝不进代码库」的原则直接冲突（`.gitignore` 排除了 `.env`，却被这个脚本绕过了）。应改为从 `infra/.env` 读取 `MYSQL_PASSWORD`（`infra/mysql8/*.bat` 已有现成的解析写法可参照）。**处置建议**：修复前该口令应视为已泄露，演示环境重建时更换 |
| 2 | `demo_approval_boundary.py` 无失败退出码 | 中 | 结尾只打印 `结果: N/12 通过`，**不 `sys.exit(非0)`**，因此无法被 CI 或批处理据此判定失败（`demo_bom_approval.py` 反而有 `sys.exit(1)`） |
| 3 | 四个子目录全空 | 中 | `scripts/`（含承诺的 `start.sh` / `reset.sh` / `demo_prep.sh`）、`sql/`、`monitoring/`、`backup/` 均只有 README。**「一键重置、演示可反复重来」这条核心承诺目前没有实现** |
| 4 | 无备份/快照 | 中 | 演示前无法快速回滚；改坏数据只能重跑 `db-init/` 的 SQL |
| 5 | 脚本硬编码服务地址与端口 | 低 | `BASE = "http://localhost:8080"` 写死，未读 `.env` 的 `SOURCE_API_PORT` |
| 6 | 脚本依赖演示专用接口 | 低 | `/api/demo/bom-change` 是演示脚手架端点，若日后清理演示端点，两个脚本会一起失效 |
| 7 | 无统一入口 | 低 | 三个脚本各自独立运行，没有 `demo_prep` 那样「一条命令跑全套并打印就绪清单」的编排 |
| 8 | 无巡检能力 | 低 | 服务状态、数据新鲜度、质量通过率、磁盘占用均无检查脚本（`ops/monitoring/` 未实现） |
