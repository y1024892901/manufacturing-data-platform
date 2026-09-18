#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""采集入口。

用法：
    python run.py --mode full                      # 全量初始化
    python run.py --mode incremental               # 增量同步（全部表）
    python run.py --mode incremental --system erp  # 指定系统
    python run.py --mode rerun --table erp_prod_order --from 2026-08-01 --to 2026-08-31
    python run.py --mode shadow --table new_table  # 影子同步，只比对不落库
"""
# TODO:
#   1. 从 platform_meta 读取元数据（系统、表、字段映射、同步策略）
#   2. 通过 connectors/registry 选择连接器
#   3. 按 watermark 抽取增量数据
#   4. 补齐四个审计列，经 loaders/ 幂等写入 ODS
#   5. 更新 watermark，写 ingest_run_log

if __name__ == "__main__":
    raise SystemExit("尚未实现：请按 TODO 完成采集入口")
