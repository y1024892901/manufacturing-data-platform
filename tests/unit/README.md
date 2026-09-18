# tests/unit/ — 单元测试

## 技术栈
pytest · pytest-mock · hypothesis（属性测试，可选）

## 放什么代码

| 文件 | 测什么 | 重点 |
|---|---|---|
| `test_bom_explode.py` | BOM 多层展开 | **递归正确性**，防循环引用死循环 |
| `test_generators.py` | 造数业务约束 | 时序、库存守恒、数量关系 |
| `test_injectors.py` | 坏数据注入器 | 注入的记录确实违反了目标规则 |
| `test_normalize.py` | 编码归一宏 | 9 种编码风格都能正确收敛 |
| `test_interval_overlap.py` | 时间区间重叠检测 | 边界情况：相邻不算重叠 |
| `test_shap_attributor.py` | SHAP 值聚合到业务维度 | 贡献度加总守恒 |
| `test_narrator.py` | 归因文案生成 | **数字与结论严格对应，无编造** |
| `test_sql_guard.py` | SQL 安全检查 | 各种绕过尝试都被拦截 |
| `test_rule_scoring.py` | 规则评分 | 边界值、满分、零分 |

## 重点测试设计

### `test_bom_explode.py` —— 最容易出错的地方

| 用例 | 验证 |
|---|---|
| 单层 BOM | 正确展开 |
| 多层 BOM（3 层） | 递归正确，用量逐层相乘 |
| 同一物料在多条路径出现 | 用量累加而非覆盖 |
| **循环引用** | 检测并报错，不死循环 |
| 空 BOM | 返回空结果不报错 |

### `test_narrator.py` —— 演示安全的关键

```python
def test_no_fabricated_numbers():
    """生成的文案中出现的所有数字，必须来自输入数据"""
    inputs = load_fixture("order_risk_sample.json")
    text = narrator.render(inputs)
    for num in extract_numbers(text):
        assert num in collect_input_numbers(inputs), f"文案出现输入中不存在的数字: {num}"
```

**这个测试直接防止 AI 编造数字导致演示翻车。**

## 干什么事情
1. 单元测试不依赖数据库，运行速度在秒级，适合提交前快速验证
2. BOM 展开与区间重叠是本项目两个算法难点，必须有充分的边界用例
3. 归因文案测试是"演示安全"的一部分，不可省略
