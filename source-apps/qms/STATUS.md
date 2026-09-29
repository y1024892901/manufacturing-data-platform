# qms/ — 目前进展

## 2026-09-23 执行更新

新增 integration/ReceiptInspectionConsumer，按 WMS 收货事件生成 IQC，替代到货动作跨库直接建检验单。真实数据库烟测验证 CONSUMED 与两条同业务事件仅一张检验单，全部烟测数据回滚。QMS 判定后的 WMS 库存联动仍为现有同步实现。

以下为 2026-09-22 基线详情，涉及上述内容时以本节为准。

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

QMS 已跑通 P3 目标里最硬的一条链——「ASN 到货生成待检 → IQC 判定 → 库存状态同步转换（可用/隔离/退供/报废）→ 自动派生 NCR → 处置 → 返工闭环 → 回写 SRM 供应商质量」；5 类核心单据（检验单、NCR、返工单、CAPA、8D）均能从创建走到终态，验收项 2 已达标。但**至今未接入审批引擎**（无 `workflow` 包、无 `ApprovalCallback`、无 `workflow.start()`），过程质量与成品质量两个模块整体未实现，统计报表接口为零。

## 已实现

### 分层文件统计

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | 0 | 未建立（结论白名单以 `Set.of(...)` 内联，状态机在 shared-common 的 `P3CrudService.rules()`） |
| `entity/` | 3 | `Inspection`、`DefectRecord`、`ReworkOrder` |
| `repo/` | 3 | `InspectionRepository`、`DefectRecordRepository`、`ReworkOrderRepository` |
| `service/` | 1 | `QmsLifecycleService`（JdbcTemplate + `@Transactional`，承载判定/处置/验证/关闭） |
| `controller/` | 4 | `InspectionController`、`DefectController`、`ReworkController`、`QmsP3Controller` |
| `workflow/` | 0 | **未建立**（无审批接入） |
| `integration/` | 0 | 未建立（跨系统联动写在 service 内：直调 WMS `InventoryTransactionService`、直写 `srm_supplier_quality`） |
| `query/` | 0 | 未建立（列表走 `P3CrudService.page()` 或 `repo.findAll(PageRequest)`） |
| `dto/` | 0 | 未建立（请求体直接用实体或裸 `Map<String,Object>`） |
| **合计** | **12** | 含 `package-info.java` |

## 接口清单

全部 19 个端点，逐个取自 4 个 `@RestController` 的注解。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/qms/inspections` | 检验单分页（JPA） |
| GET | `/api/qms/inspections/{id}` | 检验单详情 |
| POST | `/api/qms/inspections` | 建检验单，置 `PENDING`、`inspect_date=今天`、`inspector_code=当前用户` |
| POST | `/api/qms/inspections/{id}/judge` | ★ 判定：校验「合格数 + 不合格数 = 送检数」，按结论驱动 WMS 库存并派生 NCR |
| GET | `/api/qms/defects` | 不合格品记录分页 |
| POST | `/api/qms/defects` | 建不合格品记录：校验单号唯一、来源检验单存在、数量不超过检验不良数，物料编码取自检验单 |
| POST | `/api/qms/defects/{id}/disposition` | ★ 处置：`REWORK`/`SCRAP`/`CONCESSION`/`RETURN` 四选一，数量不得超不合格数，记录处置人与时间 |
| GET | `/api/qms/reworks` | 返工单分页 |
| POST | `/api/qms/reworks` | ★ 建返工单：**仅当来源不合格品处置为 `REWORK` 时允许**，数量须大于 0 且不超过已处置量 |
| POST | `/api/qms/reworks/{id}/start` | 返工开工：`PENDING`→`DOING`，记录 `start_time` |
| POST | `/api/qms/reworks/{id}/complete` | 返工完工：`DOING`→`DONE` 或 `SCRAPPED`，记录 `end_time` |
| GET | `/api/qms/{kind}` | 通用分页，`kind` ∈ `standards`、`sampling-plans`、`ncrs`、`capas`、`8d` |
| GET | `/api/qms/{kind}/{id}` | 通用详情 |
| POST | `/api/qms/{kind}` | 通用创建：查 `information_schema` 过滤出真实列后插入 |
| POST | `/api/qms/ncrs/{id}/dispose` | ★ NCR 处置：`REWORK`/`SCRAPPED`/`CONCESSION`/`RETURNED`，发布 `QMS.NCR.DISPOSED`。**`@PreAuthorize("hasAuthority('QMS:NCR:DISPOSE') or hasRole('ADMIN')")`** |
| POST | `/api/qms/capas/{id}/verify` | CAPA 验证：`IMPLEMENTING`→`VERIFYING`（无权限注解） |
| POST | `/api/qms/capas/{id}/close` | CAPA 关闭：`VERIFYING`→`CLOSED`。**`@PreAuthorize("hasAuthority('QMS:CAPA:CLOSE') or hasRole('ADMIN')")`** |
| POST | `/api/qms/8d/{id}/verify` | 8D 验证：`SUBMITTED`→`VERIFYING`（无权限注解） |
| POST | `/api/qms/8d/{id}/close` | 8D 关闭：`VERIFYING`→`CLOSED`。**`@PreAuthorize("hasAuthority('QMS:8D:CLOSE') or hasRole('ADMIN')")`** |

`QmsP3Controller` 的白名单 `K = Set.of("standards","sampling-plans","ncrs","capas","8d")` 不含 `inspections`、`reworks`，因此 `GET /api/qms/inspections`、`GET /api/qms/reworks` 会由 Spring MVC 的字面量路径优先规则落到达 `InspectionController` / `ReworkController`，与 `/{kind}` 通配端点并存而不冲突。

## 数据表

### JPA 实体映射（`@Table` 注解）

| 库.表 | 实体类 |
|---|---|
| `src_qms.qms_inspection` | `Inspection` |
| `src_qms.qms_defect` | `DefectRecord` |
| `src_qms.qms_rework` | `ReworkOrder` |

### 走 JdbcTemplate / 通用 CRUD 的表（无实体类）

| 库.表 | 用途 | 建表位置 |
|---|---|---|
| `src_qms.qms_standard` | 检验标准（`standards`） | `V35__p3_qms_standard_sampling.sql` |
| `src_qms.qms_standard_item` | 检验项目（**已建表，无任何接口**） | `V35` |
| `src_qms.qms_sampling_plan` | 抽样方案（`sampling-plans`） | `V35` |
| `src_qms.qms_ncr` | 不合格品评审单（`ncrs`） | `V36__p3_qms_ncr_capa.sql` |
| `src_qms.qms_capa` | 纠正预防措施（`capas`） | `V36` |
| `src_qms.qms_eight_d` | 8D 报告（`8d`） | `V37__p3_qms_8d_permissions.sql` |

### 跨库读写的表（本模块直接操作其他系统的库）

| 库.表 | 方式 | 说明 |
|---|---|---|
| `src_wms.wms_receipt` | `SELECT ... FOR UPDATE` + `UPDATE status` | 判定后回写收货单状态为 `AVAILABLE`/`FAILED`/`REWORK`/`RETURN`/`SCRAPPED` |
| `src_wms.wms_inventory` / `src_wms.wms_inventory_action` | 经 `InventoryTransactionService.action()` | 以 `QMS:<检验单号>:<动作>` 为幂等键做库存转换 |
| `src_srm.srm_supplier_quality` | `INSERT` | 检验不合格时写入 `defect_qty` 与 PPM，`problem_desc` 带 NCR 号 |

**注意**：`infra/db-init/05_business_systems_2.sql` 建立的只读副本 `qms_md_material`，在本模块 Java 代码中**从未被读取**；QMS 也不调用 `MasterDataService.assertConsumable()` 校验主数据是否已发布——建检验单时物料编码不经任何主数据校验。

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| 1. 至少 5 个本职模块具有实体、服务、接口和角色权限 | **部分实现** | 8 类业务对象均有接口；检验单、不合格品、返工单有实体及专用控制器，其余五类通过共享 `P3CrudService`。QMS 的关键写入/闭环端点使用 `@PreAuthorize`；`V59_2` 为 ADMIN、质量工程师/主管及质检员配置相应权限。仍缺少至少 5 个由 QMS 自身实体/服务实现的模块。 |
| 2. 至少 3 类核心单据可以从创建流转到关闭或作废 | **已实现** | 5 类达标，且都有状态机校验：① 检验单 `PENDING`→`judge()`→`PASSED`/`FAILED`/`CONCESSION`/`REWORK`/`RETURN`/`SCRAPPED` 终态；② NCR `OPEN`→`REVIEWING`→`REWORK`/`SCRAPPED`/`CONCESSION`/`RETURNED`→`CLOSED`（规则在 `P3CrudService.rules()`）；③ 返工单 `PENDING`→`DOING`→`DONE`/`SCRAPPED`（`ReworkController`）；④ CAPA `DRAFT`→`IMPLEMENTING`→`VERIFYING`→`CLOSED`（可退回 `IMPLEMENTING`）；⑤ 8D `OPEN`→`SUBMITTED`→`VERIFYING`→`CLOSED`。唯一例外：`DefectRecord` 无状态字段，`disposition` 是覆盖写而非流转 |
| 3. 至少 1 个审批流程和 1 个异常处理闭环 | **未实现** | **审批流程：0** —— 本模块无 `workflow/` 包、无 `ApprovalCallback` 实现、全代码无 `workflow.start()` 调用；CAPA/8D 的关闭改用 `@PreAuthorize` 做门禁，不产生审批实例与审批时间轴。对照 `erp/callback/ErpApprovalCallback.java`，QMS 缺同等的审批接入。**异常处理闭环：已实现** —— 检验不合格 → `createNcr()` 自动派生 NCR + 写 SRM 供应商质量（含 PPM）→ `dispose()` 处置 → 处置为 `REWORK` 才可建返工单（`ReworkController.create()` 强校验）→ 返工完工 → NCR 关闭；同时 `judge()` 同步把库存转 `QUARANTINE`/`SUPPLIER_RETURN`/`SCRAP` |
| 4. 至少 2 个向其他系统发送或消费的幂等业务事件 | **部分实现** | 发送侧达标：2 处 `BusinessEventService.publish()` —— `QMS.INSPECTION.JUDGED`→WMS（带 `inventoryStatus`）、`QMS.NCR.DISPOSED`→按处置结论定向 SRM 或 WMS；幂等由 outbox 的 UUID `event_id` + `INSERT IGNORE INTO mfg_ops.biz_inbox` 保证。另有**比事件更强的同步幂等通路**：`InventoryTransactionService.action()` 以 `QMS:<检验单号>:<动作>` 为 `idempotencyKey`，重复判定不会重复扣账。**缺口：消费侧为 0** —— `biz_inbox` 无任何订阅者；WMS 到货生成待检（`P3FlowService.arriveAsn()` 直写 `qms_inspection` 并发布 `WMS.RECEIPT.PENDING_INSPECTION`→QMS）是**直接写库**而非事件消费。按「发送或消费」字面判定为达标，但双向事件集成的意图未达成（与 ERP 模块同一问题） |
| 5. 至少 1 页本系统运营查询或统计报表 | **部分实现** | 8 类单据页已由 `apps/web-portal/src/shared/components/P3BusinessView.vue` 接入，支持列表、详情、新增、修改、受状态约束的删除和业务流转；统计报表接口仍缺——不良率与 PPM 已记录，但尚无不良率趋势、一次合格率、缺陷 Pareto 或供应商质量绩效聚合查询。 |

## 未实现 / 缺口

按影响面排序：

1. **未接入审批引擎**（验收项 3 不达标的唯一原因）。无 `workflow/` 包、无 `ApprovalCallback`、无 `workflow.start()`。计划中 P3 要求的「让步接收审批」「CAPA 关闭审批」两项都未落地——让步接收目前是 `dispose()` 里 `CONCESSION` 一个字符串，CAPA 关闭只是 `@PreAuthorize` 门禁。
2. **过程质量、成品质量两个模块整体未实现**。`Inspection.inspectionType` 字段可填任意类型，但无首检/巡检/末检触发规则、无工序质量点绑定、无 SPC 数据表与控制图、无质量预警、无完工放行、无留样、无质量证明文件。
3. **无统计报表接口**。不良率与 PPM 已算已存，但取不出来。
4. **权限配置需随部署迁移更新**。当前 QMS 专用权限通过 `V37`、`V38`、`V40_1`、`V59_2` 注册并授予 ADMIN、QUALITY_ENGINEER、QUALITY_SUPERVISOR、QC_INSPECTOR、WORKSHOP_CHIEF 等角色；全新或存量数据库需确认相关 Flyway 迁移均已执行。检验判定方法本身有 `QMS:INSPECTION:JUDGE` 的 `@PreAuthorize`。
5. **通用 CRUD 字段校验仍可加强**。QMS 创建入口已校验核心必填字段、抽样方案基本范围以及 NCR/CAPA/8D 的引用存在性；数据库唯一键负责单号重复校验。仍需完善枚举值与跨字段范围校验，并将请求里未识别的字段从静默忽略改为明确报错。`inspections`/`reworks` 保持在字面量路由控制器中，不能加入泛型路由白名单。
6. **通用质量记录仍无独立实体类**。`Inspection`、`DefectRecord`、`ReworkOrder` 已映射其当前页面字段；NCR、CAPA、8D、检验标准、抽样方案仍由 `P3CrudService` 的表映射与列白名单维护，表结构变更需同步更新该白名单和前端字段配置。
7. **主数据校验缺失**。不调用 `MasterDataService.assertConsumable()`；只读副本 `qms_md_material` 建了但从未被读取。
8. **`DefectController` 与 `QmsLifecycleService` 存在双轨处置**。前者走 `DefectRecord.disposition`（`REWORK`/`SCRAP`/`CONCESSION`/`RETURN`），后者走 `qms_ncr.disposition`（`REWORK`/`SCRAPPED`/`CONCESSION`/`RETURNED`），两套枚举拼写不同（`SCRAP` vs `SCRAPPED`、`RETURN` vs `RETURNED`），且返工单只认前者。同名概念两套取值，是清晰度上的负债。
9. **关键字查询的索引利用有限**。列表已经分页并在数据库中检索；`CONCAT_WS` 加前后通配符模糊搜索不利于普通索引，数据增长后应结合具体检索量补全文索引或可索引过滤字段。
10. **测试覆盖有限**。QMS 目前有 `ReceiptInspectionConsumerTest`（3 项测试），验证收货事件幂等及来源一致性；单据 CRUD、检验判定的库存联动和 CAPA/8D 状态流转尚无专属测试。
