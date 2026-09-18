#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""造数引擎入口。

用法：
    python generate.py --profile demo --seed 20260917
    python generate.py --profile small --inject none
    python generate.py --profile demo --inject P03,P04,E01 --inject-ratio 0.05

执行顺序（强依赖，不可打乱）：
    mdm -> crm/plm -> srm -> erp -> mes -> wms/qms/eam/energy -> erp(财务)
"""
# TODO:
#   1. 读取 seeds/profile_*.yaml 与 business_params.yaml
#   2. schema/*.sql 幂等建表
#   3. 按上面顺序调用 generators/ 各模块
#   4. 调用 injectors/ 注入坏数据（记录注入清单）
#   5. 输出造数报告：各表行数 + 注入清单

if __name__ == "__main__":
    raise SystemExit("尚未实现：请按 TODO 完成造数引擎")
