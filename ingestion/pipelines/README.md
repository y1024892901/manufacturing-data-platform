# ingestion/pipelines/ — dlt 管道定义

## 技术栈
dlt（Python 数据加载库）· PostgreSQL source / destination

## 放什么代码

| 文件 | 内容 |
|---|---|
| `base.py` | 管道工厂：读取元数据配置，动态生成 source 与 resource |
| `source_system.py` | 通用源系统管道（一个文件覆盖 9 类系统，靠配置驱动） |
| `resources.py` | 表级 resource 定义：主键、水位线列、字段映射、写入模式 |
| `state.py` | dlt state 持久化与位点查看 |

## 配置驱动设计（核心）

不为每个系统写一个管道，而是**读管理后台的元数据动态生成**：

```yaml
# 元数据示例（存于 platform_meta 库）
system: erp
tables:
  - name: erp_prod_order
    primary_key: [prod_order_no]
    watermark: updated_at
    write_disposition: merge
    columns:
      - {src: ORDER_NO,   dst: prod_order_no, type: text,    pk: true}
      - {src: PLAN_QTY,   dst: plan_qty,      type: numeric}
      - {src: STATUS_CD,  dst: status_code,   type: text}
```

**收益**：新增一个系统或一张表 → 后台登记 → 管道自动生效，**零代码改动**。这是"表数量不固定"需求的技术实现。

## 干什么事情
1. 提供 `run(system=None, table=None, mode='incremental')` 统一入口
2. 写 ODS 前自动补齐四个审计列
3. 自动处理源表新增字段（schema 演化），不因加字段而崩
