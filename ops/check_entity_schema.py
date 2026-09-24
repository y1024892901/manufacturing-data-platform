#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""实体-表一致性核对（计划 00 · F4-01）。

比对 DDL 里的建表列集合与 JPA 实体的 @Column 映射，输出「表有列、实体无字段」的差异清单。
纯静态分析：DDL 从 SQL 迁移与初始化脚本解析，实体从 Java 源码解析。

已知缺口见 docs/plans/00-foundation.md 的 F4-02…F4-11 各列；本脚本修完后应报零。

用法：
    python ops/check_entity_schema.py             # 打印报告
    python ops/check_entity_schema.py --strict    # 存在缺口时以非零退出
    python ops/check_entity_schema.py --all       # 连同无实体映射的表一起列出
"""

import argparse
import json
from pathlib import Path
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APPS = os.path.join(ROOT, "source-apps")
SQL_DIRS = [
    os.path.join(ROOT, "infra", "db-init"),
    os.path.join(APPS, "bootstrap", "src", "main", "resources", "db", "migration"),
]

# 建表时无需实体映射的列（审计列由框架或直接 SQL 维护，逐表判断不现实）
IGNORED_COLUMNS = set()

TABLE_RE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`?(\w+)`?\.)?`?(\w+)`?\s*\((.*?)\)\s*(?:ENGINE|COMMENT|;|$)",
    re.S | re.I,
)
ENTITY_TABLE_RE = re.compile(
    r"@Table\s*\(([^)]*)\)", re.S
)
COLUMN_RE = re.compile(r"@Column\s*\(([^)]*)\)", re.S)
NAME_ATTR_RE = re.compile(r"name\s*=\s*\"([^\"]*)\"")
CATALOG_ATTR_RE = re.compile(r"catalog\s*=\s*\"([^\"]*)\"")


def read(p):
    return io.open(p, encoding="utf-8", errors="replace").read()


def strip_comments(src):
    """去掉注释——两类注释都会造成漏报：

    · Java 的 `/* */` 与 `//`：javadoc 里常出现 @Table/@Column 用法示例，
      不剥离会把接口文档当成真实映射（MasterDataEntity 就是这种）。
    · SQL 的 `--`：建表体内以 `-- 分组注释` 起头的列会被整列丢弃，
      且前面带 `--` 的整条 `ALTER TABLE` 会被跳过（V36 就是这种形态，
      它给 qms_inspection 加的三列此前从未被本脚本登记）。
    """
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    src = re.sub(r"--[^\n]*", " ", src)
    return src


def parse_tables():
    """DDL → {(库, 表): {列}}

    建表语句多为非限定名，库名靠前置的 `USE db;` 指定，故按语句顺序追踪当前库。
    """
    tables = {}
    for d in SQL_DIRS:
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            if not fn.endswith(".sql"):
                continue
            src = strip_comments(read(os.path.join(d, fn)))
            current_db = "mfg_auth"
            for stmt in re.split(r";", src):
                um = re.search(r"\bUSE\s+`?(\w+)`?", stmt, re.I)
                if um:
                    current_db = um.group(1)
                for db, tbl, body in TABLE_RE.findall(stmt + ";"):
                    cols = set()
                    for line in body.split(","):
                        line = line.strip()
                        m = re.match(r"`?(\w+)`?\s+[A-Za-z]", line)
                        if m and m.group(1).upper() not in (
                            "PRIMARY", "UNIQUE", "KEY", "INDEX", "CONSTRAINT", "FOREIGN", "CHECK"
                        ):
                            cols.add(m.group(1))
                    if cols:
                        tables.setdefault((db or current_db, tbl), set()).update(cols)
                # ALTER TABLE ... ADD COLUMN：后续迁移新增的列走这条通道，
                # 只解析 CREATE TABLE 会漏掉 F4 任务里点名的那批列。
                am = re.match(
                    r"\s*ALTER\s+TABLE\s+(?:`?(\w+)`?\.)?`?(\w+)`?\s+(.*)$",
                    stmt, re.S | re.I,
                )
                if am:
                    adb, atbl, body = am.group(1), am.group(2), am.group(3)
                    added = re.findall(r"ADD\s+COLUMN\s+`?(\w+)`?", body, re.I)
                    if added:
                        tables.setdefault((adb or current_db, atbl), set()).update(added)
    return tables


def java_entities():
    for base, _dirs, files in os.walk(APPS):
        if os.sep + "target" + os.sep in base + os.sep:
            continue
        for name in files:
            if name.endswith(".java"):
                yield os.path.join(base, name)


def parse_entities():
    """JPA 实体 → {(库, 表): {列}}"""
    entities = {}
    for path in java_entities():
        src = strip_comments(read(path))
        if re.search(r"\binterface\s+\w+|\babstract\s+class\b", src) and not re.search(
            r"@Entity\b", src
        ):
            continue
        tm = ENTITY_TABLE_RE.search(src)
        if not tm:
            continue
        attrs = tm.group(1)
        name_m, cat_m = NAME_ATTR_RE.search(attrs), CATALOG_ATTR_RE.search(attrs)
        if not name_m:
            continue
        tbl = name_m.group(1)
        db = cat_m.group(1) if cat_m else ""
        cols = set()
        for cm in COLUMN_RE.finditer(src):
            nm = NAME_ATTR_RE.search(cm.group(1))
            if nm:
                cols.add(nm.group(1))
        # 未显式写 @Column 的字段按驼峰转下划线推断（JPA 默认命名策略）
        for fm in re.finditer(r"private\s+[\w<>\[\], .]+\s+(\w+)\s*;", src):
            field = fm.group(1)
            snake = re.sub(r"(?<!^)(?=[A-Z])", "_", field).lower()
            cols.add(snake)
            cols.add(field)
        entities[(db, tbl)] = cols
    return entities


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true")
    ap.add_argument("--all", action="store_true", help="同时列出无实体映射的表")
    ap.add_argument("--live", action="store_true", help="只读查询 information_schema，校验实际数据库")
    args = ap.parse_args()

    if args.live:
        from database_metadata import columns
        tables = columns()
    else:
        tables = parse_tables()
    registry_path = Path(ROOT) / "ops/entity_schema_registry.json"
    registry = json.loads(registry_path.read_text(encoding="utf-8")) if registry_path.exists() else {}
    entities = parse_entities()

    # 实体可能省略 catalog（如 @Table(name="x") 靠 use 语句定位），做双向宽松匹配
    def entity_for(db, tbl):
        """优先精确匹配 (库, 表)；退化为按表名匹配时取列最多的那个。

        同一表名可能被多个实体声明（如副本表与源表同名），取列最多的才代表真实映射。
        """
        if (db, tbl) in entities:
            return entities[(db, tbl)]
        cands = [cols for (edb, etbl), cols in entities.items() if etbl == tbl]
        return max(cands, key=len) if cands else None

    mapped, unmapped, diffs = 0, [], []
    for (db, tbl), cols in sorted(tables.items()):
        ec = entity_for(db, tbl)
        if ec is None:
            unmapped.append((db, tbl, len(cols)))
            continue
        mapped += 1
        missing = sorted(c for c in cols if c not in ec and c not in IGNORED_COLUMNS)
        if missing:
            diffs.append((db, tbl, missing))

    print("=" * 78)
    print("实体-表一致性核对（F4-01）")
    print("=" * 78)
    print(f"DDL 表数：{len(tables)}    有实体映射：{mapped}    无实体映射：{len(unmapped)}")
    print()
    if diffs:
        print(f"表有列、实体无字段（{len(diffs)} 张表）：")
        for db, tbl, cols in diffs:
            print(f"  {db}.{tbl}")
            print(f"      缺：{'、'.join(cols)}")
    else:
        print("表有列、实体无字段：无")

    if args.all and unmapped:
        print()
        print(f"无实体映射的表（{len(unmapped)} 张，按 F4-03 口径取舍：补实体或登记为裸 SQL 表）：")
        for db, tbl, n in unmapped:
            print(f"  {db}.{tbl}  ({n} 列)")

    print()
    total = sum(len(c) for _d, _t, c in diffs)
    print(f"合计：{len(diffs)} 张表 / {total} 个未映射列，另有 {len(unmapped)} 张表无实体"
          + ("（用 --all 展开）" if not args.all else ""))
    registry_errors = []
    pending = []
    for db, table, _ in unmapped:
        key = db + "." + table
        entry = registry.get(key)
        if not entry or not entry.get("reason") or not entry.get("plan"):
            registry_errors.append(key + ": 未逐表登记")
            continue
        mode = entry.get("mode")
        if mode == "planned":
            pending.append(key + " (计划 " + entry["plan"] + ")")
        elif mode not in {"sql", "association", "out_of_scope"}:
            registry_errors.append(key + ": 非法管理方式")
        if not entry.get("evidence") or any(not (Path(ROOT) / path).is_file() for path in entry["evidence"]):
            registry_errors.append(key + ": 登记依据文件缺失")
        if args.live and set(entry.get("columns", [])) != tables[(db, table)]:
            registry_errors.append(key + ": 列集合与登记不一致，需同步实体或 SQL 管理登记")
    print("已逐表登记：", len(unmapped) - len([x for x in registry_errors if "未逐表登记" in x]))
    for message in registry_errors:
        print("登记错误：", message)
    print("尚未实现业务访问的表：", len(pending))
    for message in pending:
        print("  待实施：", message)
    if args.strict and (diffs or registry_errors or pending):
        sys.exit(1)


if __name__ == "__main__":
    main()
