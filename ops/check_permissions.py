#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""权限码三方对照与鉴权基线校验（计划 00 · F1-01 / F1-05）。

做两件事：

  F1-01  列出所有 @PreAuthorize 引用的权限码，逐码标注三方状态——
         字典是否有（sys_permission）/ 是否授予角色（sys_role_permission）/ 被哪些端点引用。

  F1-05  断言每个端点要么有方法级 @PreAuthorize，要么在显式白名单中。

纯静态分析，不连数据库：权限字典与授权行从 SQL 迁移与初始化脚本里解析。

用法：
    python ops/check_permissions.py             # 打印报告
    python ops/check_permissions.py --strict    # 存在缺口时以非零退出（供 CI 使用）
"""

import argparse
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APPS_DIR = os.path.join(ROOT, "source-apps")
SQL_DIRS = [
    os.path.join(ROOT, "infra", "db-init"),
    os.path.join(APPS_DIR, "bootstrap", "src", "main", "resources", "db", "migration"),
]

# 无需方法级鉴权的端点（登录、健康检查、文档等），格式 (HTTP 方法, 路径片段) 或 (None, 片段) 表示不限方法
WHITELIST = [
    (None, "/api/auth/login"),
    (None, "/api/auth/demo-accounts"),
    (None, "/api/health"),
    (None, "/v3/api-docs"),
    (None, "/swagger-ui"),
    (None, "/actuator"),
]

PERM_CODE_RE = re.compile(r"[A-Z][A-Z0-9_]*(?::[A-Z0-9_]+)+")
MAPPING_RE = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping\s*(\([^)]*\))?")
PATH_RE = re.compile(r"\"([^\"]*)\"")
CLASS_MAPPING_RE = re.compile(r"@RequestMapping\s*\(\s*\"([^\"]*)\"")
QUOTED_RE = re.compile(r"'([^']*)'|\"([^\"]*)\"")


def read(path):
    return io.open(path, encoding="utf-8", errors="replace").read()


def java_files():
    for base, _dirs, files in os.walk(APPS_DIR):
        if os.sep + "target" + os.sep in base + os.sep:
            continue
        for name in files:
            if name.endswith(".java") and name.endswith("Controller.java"):
                yield os.path.join(base, name)


def sql_files():
    for d in SQL_DIRS:
        if not os.path.isdir(d):
            continue
        for name in sorted(os.listdir(d)):
            if name.endswith(".sql"):
                yield os.path.join(d, name)


def parse_endpoints():
    """提取每个端点：(HTTP 方法, 完整路径, 是否带 @PreAuthorize, 注解里引用的权限码集合)

    注解归属用「就近原则」：每个 @PreAuthorize 算给字符距离最近的那个映射注解。
    不能按固定窗口前后看——同一文件里两种写法都存在：
        @PreAuthorize(...)  @PostMapping("/x")     ← 注解在前
        @PostMapping("/y")  @PreAuthorize(...)     ← 注解在后
    固定窗口会把后一种写法串到下一个方法上。
    """
    endpoints = []
    for path in java_files():
        src = read(path)
        cls = CLASS_MAPPING_RE.search(src)
        base = cls.group(1) if cls else ""
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        spans = [(m.start(), m.end(), m) for m in MAPPING_RE.finditer(src)]
        auths = []
        for m in re.finditer(r"@PreAuthorize\s*\(", src):
            # 括号配对扫描：注解内容里还有 hasAuthority('...') 的内层括号，
            # 用 [^)]* 会在内层右括号处提前截断，进而把「注解写在映射之前」
            # 的端点误判为无注解。
            depth, i = 0, m.end() - 1
            while i < len(src):
                if src[i] == "(":
                    depth += 1
                elif src[i] == ")":
                    depth -= 1
                    if depth == 0:
                        break
                i += 1
            auths.append((m.start(), i + 1, src[m.end():i]))
        for start, _end, m in spans:
            verb = m.group(1).upper()
            _path = PATH_RE.search(m.group(2) or "")
            sub = _path.group(1) if _path else ""
            best = ""
            for a_start, a_end, blob in auths:
                # 相邻判定：注解与映射之间只隔空白（或另一个注解）才算属于该映射。
                # 同文件两种写法都存在——注解在映射前、或跟在映射后。
                before_ok = src[a_end:start].strip() == "" if a_end <= start else False
                after_ok = src[_end:a_start].strip() == "" if a_start >= _end else False
                if before_ok or after_ok:
                    best = blob
                    break
            codes = set(PERM_CODE_RE.findall(best))
            # @PreAuthorize("isAuthenticated()") 不含权限码，但已提供方法级鉴权，
            # 语义是「仅需登录」——单列一类，不计入「无注解」缺口。
            login_only = bool(best) and not codes and "isAuthenticated" in best
            full = (base.rstrip("/") + "/" + sub.lstrip("/")).replace("//", "/")
            endpoints.append((verb, full, bool(best), codes, rel, login_only))
    return endpoints


def parse_dictionary():
    """从 SQL 解析：字典中已定义的权限码、已授予角色的权限码。

    授权有两种写法，都要认：
      · 逐码白名单 —— `p.perm_code IN ('MDM:PRODUCT:VIEW', ...)`
      · 前缀通配   —— `p.perm_code LIKE 'MDM:%'`
    只认前者会把通配覆盖到的码误报成「未授予」。
    """
    defined, granted, wildcards = set(), set(), set()
    for path in sql_files():
        src = read(path)
        for stmt in re.findall(r"INSERT[^;]*;", src, re.S | re.I):
            # 表名可能是限定名（mfg_auth.sys_permission），故用 [\w.]* 而非 \w*
            target_perm = re.search(r"INTO\s+[\w.]*sys_permission\b", stmt, re.I)
            target_grant = re.search(r"INTO\s+[\w.]*sys_role_permission\b", stmt, re.I)
            if target_grant:
                for w in re.findall(r"LIKE\s*'([A-Z][A-Z0-9_]*):%'", stmt):
                    wildcards.add(w + ":")
            for q1, q2 in QUOTED_RE.findall(stmt):
                for token in (q1, q2):
                    if target_perm and PERM_CODE_RE.fullmatch(token):
                        defined.add(token)
                    if target_grant and PERM_CODE_RE.fullmatch(token):
                        granted.add(token)
    # 前缀通配展开：字典里任何匹配前缀的码都视为已授予
    for code in defined:
        if any(code.startswith(prefix) for prefix in wildcards):
            granted.add(code)
    return defined, granted


def whitelisted(verb, full):
    for _v, frag in WHITELIST:
        if frag in full:
            return True
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="存在缺口时以非零退出")
    args = ap.parse_args()

    endpoints = parse_endpoints()
    defined, granted = parse_dictionary()

    referenced = {}
    for verb, full, has_auth, codes, rel, _login in endpoints:
        for code in codes:
            referenced.setdefault(code, []).append(f"{verb} {full}")

    print("=" * 78)
    print("权限码三方对照（F1-01）")
    print("=" * 78)
    print(f"{'权限码':<34}{'字典':<8}{'已授予':<8}引用端点数")
    print("-" * 78)

    undefined, ungranted = [], []
    for code in sorted(referenced):
        has_def = code in defined
        has_grant = code in granted
        if not has_def:
            undefined.append(code)
        elif not has_grant:
            ungranted.append(code)
        print(f"{code:<34}{'有' if has_def else '缺':<8}"
              f"{'有' if has_grant else '缺':<8}{len(referenced[code])}")

    print()
    print(f"被引用但字典中不存在：{len(undefined)} 个")
    for c in undefined:
        print(f"    {c}   ← {', '.join(sorted(set(referenced[c])))}")
    print(f"已定义但未授予任何角色：{len(ungranted)} 个")
    for c in ungranted:
        print(f"    {c}")

    print()
    print("=" * 78)
    print("鉴权基线校验（F1-05）")
    print("=" * 78)
    uncovered = [(v, p, r) for v, p, has, _c, r, _lo in endpoints
                 if not has and not whitelisted(v, p)]
    login_only = [e for e in endpoints if e[5]]
    print(f"端点总数：{len(endpoints)}")
    print(f"带 @PreAuthorize：{sum(1 for e in endpoints if e[2])}")
    print(f"  其中仅需登录（isAuthenticated）：{len(login_only)}")
    print(f"无注解且不在白名单：{len(uncovered)}")
    if uncovered:
        print()
        by_sys = {}
        for _v, p, r in uncovered:
            sysname = r.split("/")[1] if "/" in r else "?"
            by_sys.setdefault(sysname, 0)
            by_sys[sysname] += 1
        for k in sorted(by_sys, key=lambda x: -by_sys[x]):
            print(f"    {k:<12}{by_sys[k]:>4} 个端点")

    gaps = len(undefined) + len(ungranted) + len(uncovered)
    print()
    print(f"合计缺口：{gaps}")
    if args.strict and gaps:
        sys.exit(1)


if __name__ == "__main__":
    main()
