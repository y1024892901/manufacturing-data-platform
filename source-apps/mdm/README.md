# mdm/ — 统一主数据管理平台（第 10 个系统）

## 技术栈
Java 17 · Spring Boot 3.2 · Spring Data JPA · 跨库限定名访问（无需多数据源）

## 职责
12 类主数据的**唯一真实来源**：创建 → 审批 → 发布 → 分发到 9 个业务系统。

## 核心业务规则

> **只有 `PUBLISHED` 状态的主数据才能被业务系统消费。**

这是整个模块存在的理由。演示时的对比效果：

| 物料状态 | 在 ERP 建采购订单时 |
|---|---|
| `DRAFT` | **下拉框里找不到** |
| `PENDING` | **下拉框里找不到** |
| `PUBLISHED` | 可以选到 |

## 状态机

```
            提交审批                审批通过              发布
   DRAFT ───────────► PENDING ───────────► (回调) ────────► PUBLISHED
     ▲                    │                                    │
     │                 驳回(退回)                               │ 发起变更
     └────────────────────┘                                    ▼
                                                          CHANGING
                                                     （旧版本仍可用）
   任意状态 ──► DISABLED（停用，不再可被引用）
```

| 状态 | 可编辑 | 业务系统可见 | 说明 |
|---|---|---|---|
| `DRAFT` | ✓ | ✗ | 新建默认状态 |
| `PENDING` | ✗ | ✗ | 审批中，锁定编辑 |
| `PUBLISHED` | ✗ | **✓** | ★ 唯一可被引用的状态 |
| `CHANGING` | ✗ | ✓ | 变更审批中，**旧版本仍可用** |
| `REJECTED` | ✓ | ✗ | 被驳回，可改后重提 |
| `DISABLED` | ✗ | ✗ | 停用，但历史单据仍能查到名称 |

## 12 类主数据

| 类型 | 实体 | 需审批 | 分发目标 |
|---|---|---|---|
| 客户 | `Customer` | ✓ 两级 | CRM、ERP |
| 供应商 | `Supplier` | ✓ 两级 | SRM、ERP |
| 物料 | `Material` | ✓ 两级 | ERP、MES、WMS、QMS、SRM、EAM、PLM |
| BOM | `Bom` + `BomLine` | ✓ **三级** | ERP、MES、PLM |
| 物料分类 | `MaterialCategory` | ✗ | ERP、MES、WMS |
| 产品 / 工艺 / 计量单位 / 组织 / 成本中心 / 科目 / 员工 | 待实现 | 部分 | — |

## 关键设计

### 1. 跨库访问靠限定名，不配多数据源

应用主数据源连的是 `mfg_auth`，但主数据在 `src_mdm`。MySQL 支持跨库限定名：

```java
@Entity
@Table(name = "md_customer", catalog = "src_mdm")   // → src_mdm.md_customer
```

分发时同样用限定名直接写目标库，**无需配置 10 个数据源**：

```java
jdbc.update("INSERT INTO src_erp.erp_md_material (...) VALUES (...)");
```

### 2. 分发只新增/更新，绝不删除

主数据被停用后，下游副本**保留**。因为历史单据还引用着它，
删掉会导致历史单据查不到物料名。停用只是「不再允许新业务引用」。

### 3. 单个系统分发失败不阻断整体

`MasterDataDistributor.distributeTo()` 标 `REQUIRES_NEW`，
ERP 写失败不影响 MES 写入，也不回滚主数据发布本身——
只为该目标系统记一条 `FAILED` 日志，供人工重试。

### 4. 提交时固化数据快照

`MasterDataService.submitForApproval()` 把实体快照为 JSON 存入流程实例。
审批人看到的是**提交那一刻的数据**，而非后续可能被改动的当前值。

### 5. 循环依赖的解法

审批引擎与业务模块天然互相依赖：

```
ApprovalEngine → ApprovalCallback → MasterDataService → ApprovalEngine   ← 循环
```

方案：引擎用 `ObjectProvider<ApprovalCallback>` **延迟获取**回调，
构造期不解析，只在真正触发回调时才向容器索取。

## 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/materials` | 物料分页查询 |
| GET | `/api/mdm/materials/consumable` | **★ 可选物料（仅已发布）** |
| GET | `/api/mdm/materials/{id}` | 物料详情 |
| GET | `/api/mdm/materials/by-code/{code}` | 按编码查（含引用前校验） |
| POST | `/api/mdm/materials` | 新建（草稿） |
| PUT | `/api/mdm/materials/{id}` | 修改（仅草稿可改） |
| POST | `/api/mdm/materials/{id}/submit` | 提交审批 |
| POST | `/api/mdm/materials/{id}/redistribute` | 手动重新分发 |
| GET | `/api/mdm/materials/stats` | 状态统计 |

## 演示脚本

```bash
python ops/demo_master_data_lifecycle.py
```

完整走通：建草稿 → **查不到** → 提交审批 → 两级审批 → 发布 → **查得到** → 验证 7 个系统副本表。

实测输出：

```
[2] ★ 查「可选物料」→ M-0918111542 不在列表中（草稿）
[5] ★ 再查「可选物料」→ 现在出现了！状态: 已发布  版本: v2
[7] 数据库直连验证:
    ✓ ERP  ✓ MES  ✓ WMS  ✓ QMS  ✓ SRM  ✓ PLM  ✓ EAM
```
