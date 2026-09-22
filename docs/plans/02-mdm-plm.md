# 02. MDM + PLM 收口

> **依赖**：[00 横向地基](00-foundation.md)（鉴权 / 事件消费 / 终态动作 / 实体同步四套模板） · **预估**：1 轮 · **对应验收项**：MDM ①–⑤ 全部、PLM ①–⑤ 全部——目标是这两个系统**率先五项全达标**
>
> 现状依据：[mdm/STATUS.md](../../source-apps/mdm/STATUS.md)、[plm/STATUS.md](../../source-apps/plm/STATUS.md)；验收定义见 [system-functional-catalog.md](../system-functional-catalog.md)；计划分工见 [README.md](README.md)。
> 下表「涉及文件 / 位置」以 `source-apps/<系统>/src/main/java/com/mfg/<系统>/` 为前缀，用 `…` 省略该前缀；SQL 一律指 `source-apps/bootstrap/src/main/resources/db/migration/`。

## 目标

MDM（★★★★☆）与 PLM（★★★☆☆）是十系统中完成度最高的两个，本计划让它们**率先把「最低验收」五项全部走通**，并把「可写接口怎么鉴权、单据怎么走到终态、事件怎么发、报表层建在哪」沉淀成其余八个系统可直接照抄的样板。做完后：MDM 的可写接口全部具备方法级鉴权、六类主数据都能走到终态 `DISABLED`、对账能真正比出内容差异、并首次出现本系统的报表层；PLM 补上 `domain/` 与 `integration/` 两个空层、ECR/ECO/ECN 都能取消、实施失败可重试、并发布 ≥2 条幂等业务事件。

## 现状

**MDM —— 分发主线已闭环，但「治理」与「鉴权」是软件工程上的短板。** 创建 → 审批 → 发布 → Outbox 分发 → 重试/对账已可直接演示，`domain/` 层（`MasterDataStatus` 7 态）是十系统里唯一的领域层。差距集中在四处：① 权限面破碎——15 个已种子化的 `MDM:*` 权限点中，4 个只在 `@PreAuthorize` 里出现而**任何 SQL 中都不存在**（`MDM:BOM:PUBLISH`、`MDM:MATERIAL:PUBLISH`、`MDM:PRODUCT:CREATE`、`MDM:PRODUCT:UPDATE`）——另有一个 `MDM:BOM:UPDATE` 原本也属此类，但工作区已有**未提交**改动把它换成已定义已授予的 `MDM:BOM:CHANGE`（缺陷 #1，见 [01-bugfix.md](01-bugfix.md)），是否提交由该计划决定，且 `MdmGovernanceController`（含改写状态的 `POST /api/mdm/merge`）与 `PartnerMasterController` 是**整类零方法级鉴权**；② 物料、产品、BOM、工艺路线**没有停用接口**，走不到状态机的终态 `DISABLED`（只有客户/供应商与字典类可停用）；③ 治理工作台的数据所有者/数据质量未实现、重复检测「有仓储方法无调用点」、`md_change_history` 无任何 INSERT；④ 对账是弱实现（`source_hash`/`target_hash` 已建列但从不写入，只能得到有/无行数），且 `dispatch()` 只捞 `PENDING`/`RETRYING`，卡在 `PROCESSING` 的事件无回收。另有一个口径问题：`current-status.md` 的验收矩阵把 MDM 第 ⑤ 项记为 ✗，而 `mdm/STATUS.md` 记为 ✅，本计划一并消除。

**PLM —— 变更链是唯一闭环主线，四层（domain/workflow/integration/query）为空。** ECR → 影响分析 → ECO（三级审批）→ ECN → 实施建 MDM 新版本已跑通，ECO/ECN 两条审批流程与回调齐备，BOM 两版本差异比较可用。差距：① **集成事件为零**（全模块无一处 `BusinessEventService`，`integration/` 包不存在），ECN 实施后 ERP/MES 收不到任何通知；② **无取消/作废动作**——`plm_ecn.ecn_status` 的注释里写了 `CANCELED`，但没有任何代码路径可达；③ **无异常处理闭环**（实施失败无记录无重试）；④ `domain/` 为空，状态以字符串字面量散在两个 service 里；⑤ `query/` 为空，无任何聚合统计；⑥ 工艺规划零接口（`plm_md_routing*` 只是 MDM 分发的只读副本）；⑦ 实体层只有 `EngineeringChange` 一个，且未映射 `V17` 已加的 `ecr_id`/`eco_id`/`target_type`/`target_version`；⑧ `EngineeringChangeService.implement()` 只翻状态、Controller 无映射，是死代码。

两个系统的逐项证据见各自 STATUS.md 的「对照最低验收」与「未实现 / 缺口」两节，此处不重复。

## 任务拆解

> 表内顺序即优先级；每行可独立完成、独立验证。MDM 的 1–14 与 PLM 的 15–26 可并行。
> **与 00 的分工**：凡 00 已承接的动作（权限码与授权、MDM 无鉴权端点、MDM 四类停用样例、PLM 实体映射、事件消费框架），对应行已标注「以 00 为准」，本计划不重复改代码，只在 00 完成后做验收回归；其余各行是本计划自有工作量。

| # | 系统 | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|---|
| 1 | MDM | 补齐 4 个缺失权限码并**授予角色**（`MDM:BOM:PUBLISH`、`MDM:MATERIAL:PUBLISH`、`MDM:PRODUCT:CREATE`、`MDM:PRODUCT:UPDATE`）；`MDM:BOM:UPDATE` 不在此列——它已被工作区未提交改动换成 `MDM:BOM:CHANGE`，以 [01-bugfix.md](01-bugfix.md) 的结论为准。按 `V37`/`V38` 的 `INSERT IGNORE INTO mfg_auth.sys_permission(perm_code,perm_name,system_code,perm_type)` 写法补字典，**并按 `infra/db-init/09_role_permission.sql` 的 `JOIN` 写法补 `sys_role_permission` 授权行**（该表是 `role_id` + `permission_id`，不是权限码） | 新增 `V39__…` 迁移；消费方见 `…/controller/BomController.java:22,25,26`、`ProductController.java:22-24`、`DistributionController.java:28` | 用非 `ADMIN` 角色调 `POST /api/mdm/products`（`MDM:PRODUCT:CREATE`）、`PUT /api/mdm/products/{id}`（`MDM:PRODUCT:UPDATE`）返回 200（当前必 403）；SQL 复查 4 码在 `sys_permission` 与 `sys_role_permission` 均有行。具体授予哪些角色**待确认**（建议与同前缀权限的既有角色集合一致，`MDM_ADMIN` 必授） 　**与 00 的 F1-02 为同一件事**（00 用一个迁移补齐未定义权限码并同批授予），以 00 为准，本行只做验收回归。 |
| 2 | MDM | `MdmGovernanceController` 全类补方法级 `@PreAuthorize`，重点覆盖 **`POST /api/mdm/merge`**（改写状态并写码映射）、`PUT/DELETE /api/mdm/{type}/{id}`、联系人增删改；权限码复用已种子化集合，缺哪个补哪个 | `…/controller/MdmGovernanceController.java`（当前整类零注解，`@RequestMapping("/api/mdm")`） | 无权限账号调 `POST /api/mdm/merge` 返回 403；`MdmGovernanceService.merge()` 未被调用；有权限账号同样调用仍成功 　**与 00 的 F1-06 为同一件事**，以 00 为准。 |
| 3 | MDM | `PartnerMasterController` 补方法级 `@PreAuthorize`，激活已种子化却无人使用的 `MDM:CUSTOMER:*` / `MDM:SUPPLIER:*` | `…/controller/PartnerMasterController.java`（当前整类零注解） | 客户/供应商的创建、修改、停用、提审在无权限账号下 403 　**与 00 的 F1-06 为同一件事**，以 00 为准。 |
| 4 | MDM | `DistributionController` 四个无注解端点补鉴权：`GET /distributions/events`、`/{eventId}`、`POST …/retry`、`POST …/reconcile` | `…/controller/DistributionController.java` | 逐端点 403 验证；有 `MDM:*:VIEW` + `ROLE_ADMIN` 兜底者仍可调 　**与 00 的 F1-06 为同一件事**，以 00 为准。 |
| 5 | MDM | 四类主数据补停用接口：`DELETE /api/mdm/materials/{id}`、`/products/{id}`、`/boms/{id}`、`/routings/{id}`；`MasterDataEntity` 新增默认方法 `disable()`（当前只有 `markPending`/`publish`/`markRejected`），`MasterDataService` 加统一 `disable()` 入口做状态校验与版本处理；写法照抄 `PartnerMasterController` 的「置 `DISABLED`」 | `…/controller/MaterialController.java`、`ProductController.java`、`BomController.java`、`RoutingController.java`、`…/service/MasterDataService.java`、`…/domain/MasterDataEntity.java` | 四类各走一遍 `DRAFT → PENDING → PUBLISHED → DISABLED`；停用后 `GET /api/mdm/materials/consumable` 等 `findConsumable()` 端点不再返回该记录；重复停用返回业务错误码而非 500 　**与 00 的 F3-06 为同一件事**（00 原文「以本模板为准执行」），本行只做验收回归。 |
| 6 | MDM | 重复检测接线：把 `CustomerRepository.findByUnifiedSocialCodeAndIdNot()`、`findByCustomerName()`（已存在但零调用）接入客户提交/审批环节，并在治理端点返回「疑似重复」清单 | `…/repo/CustomerRepository.java`、`…/service/MasterDataService.java`、`…/controller/MdmGovernanceController.java` | 造两条同统一社会信用代码（或同名）客户，第二条提交时返回疑似重复提示；治理端点能列出该疑似对。命中后是「阻断提交」还是「标记待查」**待确认** |
| 7 | MDM | `md_change_history` 落库：提审、发布、驳回、停用、合并五类流转各写一条历史（当前该表只有查询、无任何 INSERT） | `…/service/MasterDataService.java`、`…/service/MdmGovernanceService.java`；表见 `V16` | `GET /api/mdm/governance/history` 由恒空变为可查到本计划产生的全部流转；含操作人、前后状态、时间 |
| 8 | MDM | `merge()` 业务级幂等：被合并方已处于 `DISABLED` 时返回可读业务错误，而不是命中表上 `UNIQUE KEY uk_merge_source` 抛数据库异常 | `…/service/MdmGovernanceService.java` | 对同一对记录连续合并两次，第二次返回 4xx 业务错误码，服务日志无唯一键冲突异常 |
| 9 | MDM | 对账实化：`reconcile()` 计算并写入 `source_hash` / `target_hash`（源记录快照与目标副本行的内容哈希），判据从「有行数」升级为「哈希相等 → 一致 / 不等 → 内容不一致 / 副本缺行 → 待查」 | `…/service/MdmOutboxService.java` 的 private `reconcile()`；列见 `V16` | 手工改一行下游副本后调 `POST /api/mdm/distributions/events/{eventId}/reconcile` 得到「不一致」结论，改回后得到「一致」。`md_reconciliation.result` 是否可新增取值（如 `MISMATCH`）**待确认**，不可则复用现有两值 + `message` 说明 |
| 10 | MDM | `PROCESSING` 超时回收：`dispatch()` 目前只捞 `status IN ('PENDING','RETRYING')`，需新增回收逻辑（把超过阈值的 `PROCESSING` 置 `RETRYING` 或 `FAILED` 并记原因） | `…/service/MdmOutboxService.java`（`dispatch()` 的 WHERE 条件） | 手工把一条 `md_outbox` 置 `PROCESSING` 并改早其时间戳，一个调度周期后状态被回收；阈值取多少**待确认**（建议与 3 次重试语义对齐） |
| 11 | MDM | 建 `query/` 层与运营报表：新增主数据状态分布、版本变更频次、分发成功率与时效、对账结果汇总等聚合端点 | 新建 `…/query/` 包 + 报表 controller（命名待确认）；现有统计散在 `MaterialController.stats()`、`MdmGovernanceService.page()`、`DistributionMonitorService.latest()`、`MdmOutboxService.page()` | 至少 1 个聚合端点返回非空数据；同步把 `mdm/STATUS.md` 验收项 ⑤ 与 [current-status.md](../current-status.md) 矩阵中 MDM 的 ⑤ 口径统一（现为 ✗/✅ 不一致） |
| 12 | MDM | 事件消费侧样板：订阅 PLM 在本计划第 21 行新发的 `PLM.ECN.IMPLEMENTED`，做「变更实施到达确认」，按 00 的幂等消费模板写 `mfg_ops.biz_inbox` | 新建 `…/integration/` 包（当前不存在，职责物理上落在 `service/MasterDataDistributor` 与 `MdmOutboxService`） | 同一条事件重复投递只产生一条消费效果（`biz_inbox(event_id,target_system)` 去重）；[current-status.md](../current-status.md) 横向共性问题 2「无任何模块订阅消费」出现首个反例 　**建立在 00 的 F2 消费框架之上**（F2-02~F2-05）；00 的 F2-07 试点的是 WMS/QMS 侧事件，本行是 MDM 侧消费方。 |
| 13 | MDM | 数据所有者/维护人：加 `owner_code` / `steward_code` 列，并在详情与治理台账中展示（当前实体只有 `created_by`/`updated_by`） | `V39__…` 迁移 + `…/service/MdmGovernanceService.java` | 主数据详情返回两字段；未填时的默认与必填策略**待确认** |
| 14 | MDM | 导出接口：按 [system-functional-catalog.md](../system-functional-catalog.md) 「核心单据至少具备分页查询、详情查看和导出接口」的要求，为物料、BOM、客户等核心对象补导出端点（CSV 或 Excel，格式待确认） | 各 controller 或统一导出端点（命名待确认） | 至少 3 类对象可导出且内容与分页查询一致；`mdm/STATUS.md` 缺口「无导出接口」消除 |
| 15 | PLM | 建 `domain/` 层：新增 ECR/ECO/ECN 状态枚举与状态机（含 `CANCELED`），替换 `EngineeringChangeService` 与 `EngineeringChangeChainService` 里散落的字符串字面量 | 新建 `…/domain/` 包（当前为空）；`plm_ecn.ecn_status` 注释已定义 `CANCELED`（见 `infra/db-init/04_business_systems.sql`） | 非法状态迁移被枚举/状态机拒绝并抛业务错误码；两个 service 内状态字面量 grep 归零 |
| 16 | PLM | 终态动作：ECR / ECO / ECN 各补取消接口（建议 `POST /api/plm/ecrs/{id}/cancel`、`/ecos/{id}/cancel`、`/ecns/{id}/cancel`），置 `CANCELED` 且不可再提审/实施 | `…/controller/EngineeringChangeChainController.java`（`@RequestMapping("/api/plm")`） | 三类单据各走一遍「创建 → 取消」；取消后再次提交返回业务错误码。`plm_ecr` / `plm_eco` 表是否有 `CANCELED` 取值**待确认**（无则同批迁移加注释或状态值） 　端点命名与终态取值须与 00 的 F3-01/F3-02 约定一致（`/void` 还是 `/cancel`、未生效单据用 `CANCELED`）——**待确认**。 |
| 17 | PLM | 实体同步：`EngineeringChange` 补映射 `ecr_id` / `eco_id` / `target_type` / `target_version` 四列（`V17` 已建列，实体未映射，导致 JPA 版 ECN 详情看不到变更链写入的变更目标） | `…/entity/EngineeringChange.java` | `GET /api/plm/ecns/{id}` 能看到变更目标（BOM/工艺路线 + 目标版本） 　**与 00 的 F4-10 为同一件事**，以 00 为准。 |
| 18 | PLM | ECN 查询入口收口：`GET /api/plm/ecns`（JPA `findAll`）与链服务的 `@GetMapping("/{type:ecrs\|ecos}")` 分页分支二者只保留其一（后者正则不含 `ecns`，且字面量路径优先，加进正则也不生效）；保留哪个**待确认**，需先确认前端 `/plm/ecns`（`ChangeChainView.vue`）依赖的返回结构 | `…/controller/EngineeringChangeController.java`、`…/controller/EngineeringChangeChainController.java`；前端见 `apps/web-portal/src/router/index.ts` | 全模块只有一个 ECN 分页入口；`GET /api/plm/ecns` 的返回结构与前端现有页面一致 |
| 19 | PLM | 死代码收口：`EngineeringChangeService.implement()` 只把状态翻成 `IMPLEMENTED`、不写 MDM，且 Controller 无 `/implement` 映射（真正实施在 `EngineeringChangeChainService.implement()`）——保留一条路径，删另一条 | `…/service/EngineeringChangeService.java`、`…/controller/EngineeringChangeController.java` | 全模块只有一个 `/implement` 端点；`plm/STATUS.md` 缺口「两套 ECN 实现互相看不见」消除 |
| 20 | PLM | 补 ECR / ECO 的 JPA 实体（`plm_ecr`、`plm_eco`），把 `Map<String,Object>` 直读改为实体映射，使核心单据三类都有实体 | 新建 `…/entity/`（当前仅 `EngineeringChange`）、`…/repo/`（当前仅 `EngineeringChangeRepository`） | 三类单据的详情接口都走实体；`plm/STATUS.md` 验收项 ① 的实体缺口消除 |
| 21 | PLM | 建 `integration/` 层并发 ≥2 条幂等业务事件：实施成功后发 `PLM.ECN.IMPLEMENTED`（目标 `mdm`/`erp`/`mes`，按 `publish()` 的单目标签名逐条发），ECO 批准后发 `PLM.ECO.APPROVED` | 新建 `…/integration/` 包；调用 `com.mfg.common.integration.BusinessEventService.publish(eventType, sourceSystem, targetSystem, aggregateType, aggregateId, payload)`（签名见 `source-apps/shared/common/…/integration/BusinessEventService.java`），写法照抄 `CrmP2Service` 的调用行 | 同一 ECN 重复实施不产生重复事件（`biz_outbox` 以 UUID `event_id` 为主键、`biz_inbox` 按 `(event_id,target_system)` 去重）；`mfg_ops.biz_outbox` 出现两条 `PLM.*` 事件；`plm/STATUS.md` 验收项 ④ 由「未实现」转达成 |
| 22 | PLM | 异常处理闭环：新增实施记录表（建议 `plm_implementation_log`，记目标类型、新旧版本、结果、错误信息、操作人、时间）并补 `POST /api/plm/ecns/{id}/retry-implement` | `V39__…` 迁移 + `…/service/EngineeringChangeChainService.java` | 人为制造 MDM 侧写入失败 → 记录 FAILED → 重试成功转 SUCCESS；`plm/STATUS.md` 验收项 ③ 的异常闭环由空白转达成 |
| 23 | PLM | 建 `query/` 层与统计报表：变更数量、变更周期（ECR→ECN 时长）、实施率、按产品/类型/状态分布、ECO/ECN 审批时长 | 新建 `…/query/` 包 + 报表 controller（命名待确认）；现状是查询 SQL 内联在 controller 与 service | 至少 1 个聚合端点返回非空数据；`plm/STATUS.md` 验收项 ⑤ 由「部分」转达成 |
| 24 | PLM | 工艺规划最小切片：只读呈现 MDM 分发的 `plm_md_routing` / `plm_md_routing_operation`（列已含 `setup_time_min`、`run_time_min`、`work_center`、`is_key_operation`、`is_inspection_op`，建表见 `V13`），提供产品 → 工艺路线 → 工序/工时的查询；**不写副本表** | 新建 `…/service/` + `…/controller/`（命名待确认）；边界见 [implementation-roadmap.md](../implementation-roadmap.md) 禁止事项 4（业务系统不得自行维护 MDM 已发布的工艺主数据） | 可按产品编码查到工艺路线与工序工时；`plm/STATUS.md` 缺口「工艺规划零接口」消除。PLM 是否另建工程侧工艺规划表**待确认**（与 00 的迁移约定、MDM 主数据边界一并定） |
| 25 | PLM | 失效控制与发布记录：用第 22 行的实施记录表承载「新旧版本映射 + 生效/失效计划」的查询；自动失效旧版本仍不做（刻意取舍，见 `plm/STATUS.md` 缺口 7） | 复用 `plm_implementation_log` | `GET /api/plm/ecns/{id}` 可见实施记录（目标、新版本、时间、操作人）；回退（撤销实施）是否要做**待确认** |
| 26 | PLM | 权限点补齐：`PLM:PRODUCT:UPDATE`、`PLM:DOCUMENT:UPDATE` 种子化并授权（当前任何 SQL 中都不存在，`PlmCatalogController` 的写接口只能靠 `PLM:ECN:CREATE` 或 `ADMIN` 兜底）；`PLM:ECN:APPROVE` 已定义但无引用，决定接入审批动作还是废弃 | `V39__…` 迁移 + `…/controller/PlmCatalogController.java`（POST/PUT/release 三处注解表达式相同） | 用 `PRODUCT_ENGINEER` 调 `POST /api/plm/catalog/documents` 成功且不依赖 `ADMIN`；`PLM:ECN:APPROVE` 的去留**待确认** 　**与 00 的 F1-02 同批迁移**，以 00 为准。 |

## 完成标准

**MDM 达到五项（逐条判定依据）：**

1. **① ≥5 个本职模块具有实体、服务、接口和角色权限** —— 6 个模块已落地（治理工作台/业务伙伴/产品与物料/工程主数据/组织与财务/分发中心）；本计划的判定增量是**权限面闭合**：`MdmGovernanceController`、`PartnerMasterController`、`DistributionController` 的事件类端点全部具备方法级鉴权（403 可复现），5 个缺失权限码在 `sys_permission` 与 `sys_role_permission` 均可查到。
2. **② ≥3 类核心单据可创建 → 关闭/作废** —— 客户、供应商（已具备）之外，物料、产品、BOM、工艺路线四类经本计划新增的 `DELETE` 各验证一条 `DRAFT → PENDING → PUBLISHED → DISABLED`，且停用后不再被 `consumable` 端点返回。
3. **③ ≥1 个审批流程 + 1 个异常处理闭环** —— 保持现有 5 条审批流程；异常闭环以「分发失败 → `md_inbox` FAILED / retry_count+1 → 超 3 次 DEAD → 重试端点 → 对账按 `source_hash`/`target_hash` 比出内容差异」全链验证，并额外验证 `PROCESSING` 卡死可被回收。
4. **④ ≥2 个幂等业务事件** —— 发送侧 6 类 `*.PUBLISHED` 事件已满足；本计划新增消费侧后，`biz_inbox` 中能看到由 MDM 幂等消费的记录（重复投递不产生第二条效果）。
5. **⑤ ≥1 页运营查询/统计报表** —— `query/` 层落地，至少 1 个聚合端点返回非空数据；`mdm/STATUS.md` 与 [current-status.md](../current-status.md) 矩阵中该项口径一致为达成。

**PLM 达到五项（逐条判定依据）：**

1. **① ≥5 个本职模块具有实体、服务、接口和角色权限** —— 产品结构、工程变更、文档管理、配置管理、工艺（BOM 差异比较 + 本计划第 24 行的工艺规划最小切片）均有接口与服务；实体覆盖 ECR/ECO/ECN 三类核心单据；`PLM:PRODUCT:UPDATE`、`PLM:DOCUMENT:UPDATE` 种子化并有授权行。**判定口径待与 00 统一**：① 的「具有实体」是按「每模块至少一个实体」还是「全模块有实体层」判定——本计划按「核心单据均有实体」执行。
2. **② ≥3 类核心单据可创建 → 关闭/作废** —— ECR、ECO、ECN 三类均能走 `… → CANCELED`（第 16 行），取消后不可再提审/实施。
3. **③ ≥1 个审批流程 + 1 个异常处理闭环** —— 已有 `PLM_ECO_APPROVAL`（三级）与 `PLM_ECN_CHANGE`（两级）；异常闭环由实施记录表 + `retry-implement` 补齐（第 22 行）。
4. **④ ≥2 个幂等业务事件** —— `PLM.ECN.IMPLEMENTED` 与 `PLM.ECO.APPROVED` 落 `mfg_ops.biz_outbox`，重复触发不产生重复事件。
5. **⑤ ≥1 页运营查询/统计报表** —— `query/` 层落地，至少 1 个聚合端点返回非空数据（变更数量/周期/实施率）。

**两系统共同：**

- 迁移全部走 `V39+`，**不改动 `infra/db-init/`**（该通道截至 V11 已 baseline；注意 `plm_md_bom*` 属 db-init 建立的表，加列同样要走新脚本）；
- 按 [README.md](README.md) 的交付约定，同步更新 `source-apps/mdm/STATUS.md`、`source-apps/plm/STATUS.md` 与 [current-status.md](../current-status.md) 的总览表、验收矩阵；
- 两个系统在后端侧达到五项后，对应前端页面按需挂接（`EcnView.vue` 等死文件的处置归 08 计划）。

## 风险与前置

- **前置：00 与分工边界。** 00 已承接：权限码补齐与授权（F1-02）、MDM 无鉴权端点（F1-06）、MDM 四类主数据停用样例（F3-06）、PLM 实体映射（F4-10）、事件消费框架与试点（F2-02~F2-07）——这些行在下表中已标注「以 00 为准」，本计划只做验收回归。本计划真正新增的是：PLM 的 `domain/` 与 `integration/` 两层、异常闭环、报表层、工艺规划切片、ECN 查询入口收口，以及 MDM 的治理闭环、对账实化、`PROCESSING` 回收与报表层。若 00 未完成，第 16 行（PLM 取消）与第 21 行（事件发布）需按 00 的约定自定写法。
- **与 01-bugfix 的边界。** [current-status.md](../current-status.md) 的 23 条已确认缺陷中，与本计划相关的 #1（`BomController` 守卫一个不存在的权限码）、#5（`PLM:PRODUCT:UPDATE`/`PLM:DOCUMENT:UPDATE` 不存在）、#6（`implement()` 死代码）、#7（正则不含 `ecns`）由 01 承接。本计划不重复修复，但**验收项 ① 依赖这些码真正可用**——若 01 未执行，第 1、26 行需覆盖同一目标，避免两处改法不一致。#9（审批回调放在非标准的 `callback/` 包）决定包结构口径：PLM 现有的 `callback/` 与 MDM 的 `callback/` 都与标准分层不符，若 01 统一迁到 `workflow/`，本计划新增的 `domain/`、`integration/` 包应与之一致。
- **「分发副本写了但下游没读」不在本计划范围。** [current-status.md](../current-status.md) 把「20 张副本表里只有少数被下游读取」列为 MDM 的最大缺口，但读取端在 MES/WMS/QMS/EAM/SRM 各自模块里——按 [README.md](README.md) 的分工由 04–07 计划承接；本计划只保证分发侧与对账可信（第 9 行）。
- **待确认项（不确认即无法定稿的地方）**：`md_reconciliation.result` 是否允许新增取值；`PROCESSING` 回收阈值；重复检测命中后「阻断」还是「标记」；数据所有者字段的必填策略；`plm_ecr`/`plm_eco` 是否有 `CANCELED` 取值；ECN 分页入口保留哪一个；PLM 是否另建工程侧工艺规划表；回退（撤销实施）是否在范围内；5 个 MDM 权限码各授予哪些角色。
- **未定义权限码的数量已复核（与 00 一致，但受 01 影响）。** 全仓检索 `@PreAuthorize` 引用的权限码与其在 SQL 中的定义，当前**未定义 6 个**：MDM 4 个（`MDM:BOM:PUBLISH`、`MDM:MATERIAL:PUBLISH`、`MDM:PRODUCT:CREATE`、`MDM:PRODUCT:UPDATE`）与 PLM 2 个（`PLM:PRODUCT:UPDATE`、`PLM:DOCUMENT:UPDATE`）——与 00 的 F1-01 记的 6 行一致。差异点在 `MDM:BOM:UPDATE`：`current-status.md` 缺陷 #1 与 [01-bugfix.md](01-bugfix.md) 都把它记为未定义（当时属实），但工作区未提交改动已把它换成 `MDM:BOM:CHANGE`，故当前引用计数里不再出现。**若该改动被回退，未定义码会回到 7 个**，F1-02 的迁移需相应多补一码。
- **PLM 工艺规划的边界风险。** [implementation-roadmap.md](../implementation-roadmap.md) 禁止事项 4 要求业务系统不得自行维护已由 MDM 发布的工艺主数据。本计划只做「只读呈现 + 工程侧台账」，一旦要建 PLM 自有的 MBOM/工艺表，必须先明确它与 `md_routing` 的归属关系，否则会与 MDM 的唯一真实来源定位冲突。
- **表内顺序即优先级。** 若 1 轮内无法完成，建议按每系统表内顺序截断：MDM 保留 1–13（14 可延后），PLM 保留 15–23（24–26 视情况延后）——截断后两系统仍满足五项，只是样板质量与工艺规划覆盖度下降。
- **明确不做（五项不要求，留待后续计划）**：MDM 的序列号策略、数据质量规则引擎与评分、银行/资质台账、组织树（公司/工厂/车间独立实体）、`md_employee` 与 `md_change_request`（有 DDL 无代码）、按单据选择分发目标、`APPROVED` 中间态（`MasterDataStatus` 有定义但代码中无任何赋值点，需在「启用」与「删除」之间二选一）；PLM 的 SPC 类统计与签入签出、文件本体存储。
