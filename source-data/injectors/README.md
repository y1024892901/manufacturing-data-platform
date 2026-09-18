# source-data/injectors/ — 坏数据注入器

## 技术栈
Python 3.11 · SQL DML

## 职责
在造数完成后（或过程中）有选择地向源表注入**受控的、可预期的、可追踪的**数据质量问题。

## 放什么代码

每个注入器实现统一接口：

```python
class Injector:
    rule_id: str          # 对应的治理规则编号，如 "P03"
    target_table: str     # 目标源表
    def inject(self, conn, ratio: float, seed: int) -> InjectionReport: ...
```

| 文件 | 规则 | 注入方式 |
|---|---|---|
| `dup_voucher.py` | F01 | 复制若干凭证行，改主键保留业务键 |
| `unbalanced_entry.py` | F02 | 篡改借方或贷方金额使差额非零 |
| `bad_date_order.py` | P03 | 交换完工日期与开工日期 |
| `invalid_operation.py` | P04 | 将工序编码改为工艺路线中不存在的值 |
| `orphan_equipment.py` | E01 | 引用不存在的设备编码 |
| `overlapping_state.py` | E02 | 延长停机结束时间使与下次运行重叠 |
| `missing_inspection.py` | E04 | 删除部分点检记录 |
| `negative_energy.py` | E05 | 将能耗值置为负或乘以异常倍数 |

## 干什么事情
1. 每次注入记录 `(规则编号, 表名, 主键值, 注入类型)` 到注入清单表
2. 演示时打开治理看板，**逐一对照注入清单与规则命中结果**，证明治理链路真实有效
3. 支持 `--inject none` 生成全干净数据，用于对比"治理前 / 治理后"的效果差异
