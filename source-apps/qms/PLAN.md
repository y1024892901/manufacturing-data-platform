# qms/ — 质量管理

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 QMS 章节

## 系统定位

QMS 是十系统中质量数据的判定与处置中心：从 WMS 收货确认（`wms_receipt.status = PENDING_INSPECTION`）接入待检批次，检验判定后反过来驱动 WMS 的库存状态转换（可用 / 隔离 / 退供 / 报废），并把不合格数据推给 SRM 形成供应商质量绩效。它同时是延期风险模型的输入提供方——不良率与返工工时/延期天数是「质量 → 工期」传导的显式记录。

按 [docs/implementation-roadmap.md](../../docs/implementation-roadmap.md)，本模块归属 **P3「采购、仓储、质量闭环」**，当前代码即该阶段的落地结果。

## 本职模块基线

下表「模块 / 核心能力」两列抄录自 `system-functional-catalog.md` 的 QMS 章节，第三列为依据本模块实际代码的判定。

| 模块 | 核心能力 | 当前状态 |
|---|---|---|
| 质量基础 | 检验标准、检验项目、AQL、抽样方案、缺陷代码、质量等级 | 部分实现 |
| 来料质量 | 来料检验单、抽样、结果、合格/隔离/退供、供应商质量数据 | 已实现 |
| 过程质量 | 首检、巡检、末检、工序质量点、SPC 数据、质量预警 | 未实现 |
| 成品质量 | 完工检验、放行、留样、出货检验、质量证明文件 | 未实现 |
| 不合格管理 | NCR、不合格评审、处置、返工、报废、让步接收 | 已实现 |
| CAPA/8D | 原因分析、纠正措施、预防措施、验证、关闭、效果评价 | 部分实现 |
| 质量分析 | 不良率、PPM、一次合格率、缺陷 Pareto、供应商质量绩效 | 部分实现 |

逐项落点与缺口：

| 模块 | 已有实现 | 缺口 |
|---|---|---|
| 质量基础 | `V35` 建好 `qms_standard`、`qms_standard_item`、`qms_sampling_plan` 三表；`GET/POST /api/qms/standards`、`/api/qms/sampling-plans` 两个通用 CRUD 入口可读写 `qms_standard`、`qms_sampling_plan` | AQL 计算与抽样量推导；`qms_standard_item` 无任何接口；缺陷代码库、质量等级 |
| 来料质量 | `InspectionController` + `QmsLifecycleService.judge()` 完整闭环：IQC 判定 → 按结论同步驱动 WMS 库存（`RECEIPT` / `QUARANTINE` / `SUPPLIER_RETURN` / `SCRAP`）→ 回写 `wms_receipt.status` → 写 `src_srm.srm_supplier_quality` 并算 PPM | 抽样的自动取样逻辑（送检量由外部写入，不由 QMS 推导） |
| 过程质量 | — | `Inspection.inspectionType` 字段可填任意值，但无首检/巡检/末检的触发规则，无工序质量点绑定，无 SPC 数据表与控制图，无质量预警 |
| 成品质量 | — | `inspectionType` 可填 FQC/OQC，但无完工放行、留样、质量证明文件 |
| 不合格管理 | `QmsLifecycleService.dispose()` 处置 NCR；`P3CrudService.rules()` 的 NCR 状态机 `OPEN`→`REVIEWING`→`REWORK`/`SCRAPPED`/`CONCESSION`/`RETURNED`→`CLOSED`；`ReworkController` 返工闭环 `PENDING`→`DOING`→`DONE`/`SCRAPPED`；`DefectController` 四级处置 `REWORK`/`SCRAP`/`CONCESSION`/`RETURN` | 不合格评审会议与评审记录（现为单点处置）；`DefectRecord` 无状态字段，处置后无终态 |
| CAPA/8D | `capas`、`8d` 两个通用 CRUD + 状态机（CAPA `DRAFT`→`IMPLEMENTING`→`VERIFYING`→`CLOSED`；8D `OPEN`→`SUBMITTED`→`VERIFYING`→`CLOSED`）+ `verify`/`close` 接口 | 原因分析、纠正/预防措施的结构化字段靠通用 `create` 透传，无强制校验；效果评价未实现 |
| 质量分析 | `judge()` 实时计算 `defect_rate` 落库；`createNcr()` 计算 PPM 写入 `srm_supplier_quality` | 一次合格率（FSY）、缺陷 Pareto、供应商质量绩效查询、本系统统计报表 |

## 代码分层标准

本项目 `source-apps` 的统一分层为 `domain/ entity/ repo/ service/ controller/ workflow/ integration/ query/`。qms/ 实际落位：

| 标准分层 | 本模块实际 | 说明 |
|---|---|---|
| `domain/` | **未建立** | 无枚举与状态机类。检验结论白名单以 `Set.of("PASSED","FAILED",...)` 内联在 `QmsLifecycleService.judge()`；NCR/CAPA/8D 的状态转移规则集中在 shared-common 的 `P3CrudService.rules()` 里，不由本模块持有 |
| `entity/` | **已建立**，3 个 | `Inspection`、`DefectRecord`、`ReworkOrder`，全部 `catalog="src_qms"` |
| `repo/` | **已建立**，3 个 | 均为 `JpaRepository`，除 `existsByXxx` 幂等判重外无领域查询方法 |
| `service/` | **已建立**，1 个 | `QmsLifecycleService`（JdbcTemplate + `@Transactional`），承载判定、处置、验证、关闭 |
| `controller/` | **已建立**，4 个 | `InspectionController`、`DefectController`、`ReworkController`、`QmsP3Controller` |
| `workflow/` | **未建立该包** | **无 `ApprovalCallback` 实现、无 `workflow.start()` 调用**——QMS 至今未接入 shared-workflow 审批引擎。CAPA/8D 关闭改用 `@PreAuthorize` 做门禁 |
| `integration/` | **未建立该包** | 跨系统联动有两条真实通路，但都写在 service 里：① 同进程直调 `InventoryTransactionService.action()` 驱动 WMS 库存；② 直写 `src_srm.srm_supplier_quality`。出向事件用 `BusinessEventService.publish()`，2 处调用点 |
| `query/` | **未建立该包** | 列表查询走 `P3CrudService.page()` 或各 Controller 的 `repo.findAll(PageRequest)`；无独立查询层，也无统计报表接口 |
| `dto/` | **未建立** | 请求体直接用实体（`Inspection`/`DefectRecord`/`ReworkOrder`）或裸 `Map<String,Object>` |

### 关键设计取舍：不合格 → 库存状态由 QMS 单向下推

`QmsLifecycleService.judge()` 是本模块最有价值的一段逻辑，它把「质量结论」翻译成「库存状态」，用一个幂等键前缀串联：

```java
String base = "QMS:" + i.get("inspection_no");
inventory.action("RECEIPT",   cmd(base + ":RECEIPT",   total));   // 先入账
if (FAILED || REWORK)        inventory.action("QUARANTINE",      ...);
else if (RETURN)             inventory.action("SUPPLIER_RETURN", ...);
else if (SCRAPPED)           inventory.action("SCRAP",           ...);
```

这个取舍的好处是**判定即生效**、不会出现「已判不合格但库存仍可用」的窗口；代价是 QMS 与 WMS 变成编译期强耦合（`qms/pom.xml` 直接依赖 `app-wms`），且整个判定必须在一个事务里完成——WMS 侧任何一条库存动作失败都会回滚判定本身。`InventoryActionCommand` 的 `idempotencyKey` 保证了同一检验单重复判定不会重复扣账。

### 关键设计取舍：通用 CRUD 换开发速度，牺牲字段校验

`QmsP3Controller` 用 `GET/POST /api/qms/{kind}` 一个端点覆盖 `standards`、`sampling-plans`、`ncrs`、`capas`、`8d` 五类对象，落到 `P3CrudService`。`create()` 的做法是查 `information_schema.COLUMNS` 拿白名单，再把驼峰 key 转下划线后**只保留表里真实存在的列**：

```java
x.forEach((a,b) -> { String c = a.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase();
                     if (allowed.contains(c)) v.put(c,b); });
```

好处是新增一类 P3 对象只需在映射表里加一行，不必写实体、repo、service；代价是**没有任何业务字段校验**（必填、取值范围、关联存在性全部缺位），且请求字段名写错时会被静默丢弃而非报错。路径匹配上还依赖 Spring MVC「字面量优先于 `{kind}` 变量」的规则：`GET /api/qms/inspections` 与 `GET /api/qms/reworks` 同时匹配 `QmsP3Controller` 的 `/{kind}` 和各自控制器的字面量路径，靠该规则才落到达正确控制器——**若将来把 `inspections` 或 `reworks` 加进 `QmsP3Controller` 的 `K` 白名单，会立刻产生路由冲突。**

## 最低验收（五项）

抄录自 `system-functional-catalog.md` 末尾「系统本职完成的最低验收」：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。

以上五项的逐条对照与实际证据，见 [STATUS.md](STATUS.md) 的「对照最低验收」章节。
