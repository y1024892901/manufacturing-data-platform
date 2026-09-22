# docs/ — 文档中心

> 截至 2026-09-22 · 本页是文档索引，列出**实际存在**的文档

## 现有文档

| 文件 | 内容 | 状态 |
|---|---|---|
| [current-status.md](current-status.md) | **当前进展总览** —— 十系统各自完成度与差距 | 维护中 |
| [implementation-roadmap.md](implementation-roadmap.md) | 分阶段实施路线（P0–P7）与验收门槛 | 维护中 |
| [system-functional-catalog.md](system-functional-catalog.md) | 十系统功能目录与代码实施边界；**最低验收五项**的定义处 | 稳定 |
| [current-technology-baseline.md](current-technology-baseline.md) | 当前技术选型与运行边界；**优先于**任何早期技术描述 | 稳定 |

## 文档分层约定

本项目文档分三层，冲突时以层级高的为准：

1. **技术基线**（`current-technology-baseline.md`）—— 选型与运行边界的唯一口径；
2. **范围与验收**（`system-functional-catalog.md`、`implementation-roadmap.md`）—— 做什么、做到什么程度算完成；
3. **模块文档**（各目录下的 `PLAN.md` / `STATUS.md`）—— 单个模块的具体计划与现状。

## 各模块文档

有实现代码的目录各含两份：

- `PLAN.md` —— 整体计划：该模块要做什么，依据功能目录；
- `STATUS.md` —— 目前进展：已实现的类与接口，并对照最低验收逐项列出差距。

尚未实施的骨架目录，其 README 顶部标有 `状态：未实现`；已废弃的历史方案标有 `状态：已废弃`。
