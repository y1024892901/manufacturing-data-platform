# -*- coding: utf-8 -*-
"""主数据全生命周期演示：草稿 → 审批 → 发布 → 分发到 9 个业务系统。

演示叙事：
  ① 工艺工程师建物料 M-2099      → 状态 DRAFT
  ② 查「业务系统可选物料」        → 看不到 M-2099   ← 关键对比
  ③ 提交审批                      → 工艺主管待办 +1
  ④ 工艺主管同意 → 生产计划员同意  → 流程通过
  ⑤ 查「业务系统可选物料」        → M-2099 出现了    ← 前后对比
  ⑥ 查分发日志                    → 已推送到 7 个系统
  ⑦ 直连数据库验证副本表           → ERP/MES/WMS 里确实有这条数据

用法:  python ops/demo_master_data_lifecycle.py
"""
import json
import urllib.error
import urllib.request
from datetime import datetime

BASE = "http://localhost:8080"
PASSWORD = "Test@123456"

# 每次运行用唯一物料编码，保证脚本可重复执行
# —— 若用固定编码，上次中断留下的「审批中」记录会导致再次提交被拒
MATERIAL_CODE = "M-" + datetime.now().strftime("%m%d%H%M%S")


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())


def login(u):
    return call("POST", "/api/auth/login", {"username": u, "password": PASSWORD})["data"]


def hr(t):
    print("\n" + "=" * 70)
    print("  " + t)
    print("=" * 70)


def main():
    hr("主数据全生命周期：草稿 → 审批 → 发布 → 分发")

    zhaoliu = login("zhaoliu")       # 工艺工程师：建物料、提交审批
    zhoutao = login("zhoutao")       # 工艺主管：第一级审批
    sunqi = login("sunqi")           # 生产计划员：第二级审批

    # ---------- ① 创建物料 ----------
    print(f"\n[1] {zhaoliu['realName']}（{zhaoliu['positionName']}）建物料 {MATERIAL_CODE}")

    # 先清掉可能存在的旧数据（重复运行友好）
    exist = call("GET", f"/api/mdm/materials?keyword={MATERIAL_CODE}", None, zhaoliu["token"])
    for m in (exist.get("data") or {}).get("content", []):
        if m["materialCode"] == MATERIAL_CODE:
            print(f"    （已存在 id={m['id']}，跳过创建）")
            material_id = m["id"]
            break
    else:
        r = call("POST", "/api/mdm/materials", {
            "materialCode": MATERIAL_CODE,
            "materialName": "高速轴承",
            "materialSpec": "SKF-6205-2RS",
            "materialType": "RAW",
            "baseUnitCode": "PCS",
            "purchaseUnitCode": "BOX",
            "conversionRate": 100,
            "safetyStock": 500,
            "standardPrice": 45.50,
            "inspectionRequired": True,
            "inspectionStandard": "GB/T 276-2013",
            "changeReason": "新品导入，需建立正式物料编码"
        }, zhaoliu["token"])
        if r.get("code") != 0:
            print("    [错误]", r.get("message"))
            return
        material_id = r["data"]["id"]
        print(f"    物料编码: {r['data']['materialCode']}")
        print(f"    物料名称: {r['data']['materialName']}  {r['data']['materialSpec']}")
        print(f"    状态    : {r['data']['status']}  ← 草稿，业务系统不可见")

    # ---------- ② 关键对比：草稿状态查不到 ----------
    print(f"\n[2] ★ 查「业务系统可选物料」—— 看 M-2099 是否出现")
    consumable = call("GET", "/api/mdm/materials/consumable", None, zhaoliu["token"])
    codes = [m["materialCode"] for m in (consumable.get("data") or [])]
    if MATERIAL_CODE in codes:
        print(f"    ✗ 异常：草稿状态的物料出现在可选列表中！")
    else:
        print(f"    可选物料共 {len(codes)} 个：{codes[:6]}{'...' if len(codes) > 6 else ''}")
        print(f"    ✓ {MATERIAL_CODE} 不在列表中 —— 草稿状态业务系统选不到")
    print(f"    → 这正是「只有已发布的主数据才能被消费」规则的体现")

    # ---------- ③ 提交审批 ----------
    print(f"\n[3] 提交审批")
    r = call("POST", f"/api/mdm/materials/{material_id}/submit", None, zhaoliu["token"])
    if r.get("code") != 0:
        print("    [错误]", r.get("message"))
        return
    print(f"    实例号  : {r['data']['instanceNo']}")
    print(f"    状态    : {r['data']['status']}  ← 审批中，不可编辑")
    print(f"    {r['data']['message']}")

    # ---------- ④ 两级审批 ----------
    for idx, (user, name) in enumerate([(zhoutao, "工艺主管"), (sunqi, "生产计划员")], start=4):
        print(f"\n[{idx}] {user['realName']}（{user['positionName']}）审批")

        pend = call("GET", "/api/workflow/tasks/pending", None, user["token"])
        tasks = [t for t in (pend.get("data") or []) if t["bizId"] == material_id]
        if not tasks:
            print(f"    [跳过] 无该物料的待办")
            continue

        task = tasks[0]
        print(f"    待办节点: 【{task['nodeName']}】")
        print(f"    业务标题: {task['bizTitle']}")

        opinion = {
            "工艺主管": "物料规格与检验标准已确认，同意建立料号",
            "生产计划员": "已确认库存策略与采购可行性，同意发布",
        }[name]

        r = call("POST", f"/api/workflow/tasks/{task['taskId']}/approve",
                 {"opinion": opinion}, user["token"])
        if r.get("code") != 0:
            print("    [错误]", r.get("message"))
            return
        print(f'    意见: "{opinion}"')
        print(f"    ✓ {r['data']['message']}  状态={r['data']['status']}")

    # ---------- ⑤ 再次对比：现在能查到了 ----------
    print(f"\n[5] ★ 再查「业务系统可选物料」—— 前后对比")
    consumable2 = call("GET", "/api/mdm/materials/consumable", None, zhaoliu["token"])
    codes2 = [m["materialCode"] for m in (consumable2.get("data") or [])]
    if MATERIAL_CODE in codes2:
        hit = [m for m in consumable2["data"] if m["materialCode"] == MATERIAL_CODE][0]
        print(f"    ✓ {MATERIAL_CODE} 现在出现了！")
        print(f"      名称: {hit['materialName']}  规格: {hit['materialSpec']}")
        print(f"      单位: {hit['unitLabel']}  标准价: {hit['standardPrice']}")
        print(f"      状态: {hit['statusLabel']}  版本: v{hit['versionNo']}")
    else:
        print(f"    ✗ 异常：审批通过后仍查不到")
    print(f"    可选物料总数: {len(codes)} → {len(codes2)}")

    # ---------- ⑥ 分发结果 ----------
    print(f"\n[6] 查询物料详情（含分发状态）")
    detail = call("GET", f"/api/mdm/materials/{material_id}", None, zhaoliu["token"])
    d = detail["data"]
    print(f"    物料: {d['materialCode']} {d['materialName']}")
    print(f"    状态: {d['status']}  版本: v{d['versionNo']}")
    print(f"    更新人: {d['updatedBy']}")

    # ---------- ⑦ 数据库直连验证 ----------
    hr("数据库直连验证：主数据是否真的分发到了各系统副本表")
    verify_by_sql(MATERIAL_CODE)

    hr("完成")
    print(f"  {MATERIAL_CODE} 已走完「创建 → 审批 → 发布 → 分发」全链路")
    print(f"  业务系统现在可以正常引用该物料")


def verify_by_sql(code):
    """直连数据库验证只读副本表"""
    import subprocess
    targets = [
        ("src_erp",  "erp_md_material",  "ERP"),
        ("src_mes",  "mes_md_material",  "MES"),
        ("src_wms",  "wms_md_material",  "WMS"),
        ("src_qms",  "qms_md_material",  "QMS"),
        ("src_srm",  "srm_md_material",  "SRM"),
        ("src_plm",  "plm_md_material",  "PLM"),
        ("src_eam",  "eam_md_material",  "EAM"),
    ]
    print()
    for db, table, label in targets:
        sql = (f"SELECT COUNT(*) FROM {db}.{table} WHERE material_code='{code}';")
        try:
            out = subprocess.run(
                ["D:/mysql8/bin/mysql.exe", "-uroot", "-P3306", "-h127.0.0.1",
                 "-N", "-e", sql],
                capture_output=True, text=True, timeout=15,
                env={**__import__("os").environ, "MYSQL_PWD": "Yuan20010222"})
            cnt = out.stdout.strip()
            mark = "✓" if cnt == "1" else "✗"
            print(f"    {mark} {label:4} ({db}.{table}) : {'已同步' if cnt == '1' else '未找到'}")
        except Exception as e:
            print(f"    ? {label:4} : 查询失败 {e}")

    # 分发日志
    sql = ("SELECT target_system, status, row_count FROM src_mdm.md_distribution_log "
           f"WHERE entity_code='{code}' ORDER BY target_system;")
    try:
        out = subprocess.run(
            ["D:/mysql8/bin/mysql.exe", "-uroot", "-P3306", "-h127.0.0.1",
             "-e", sql],
            capture_output=True, text=True, timeout=15,
            env={**__import__("os").environ, "MYSQL_PWD": "Yuan20010222"})
        print("\n    分发日志（src_mdm.md_distribution_log）:")
        for line in out.stdout.strip().split("\n")[1:]:
            print("      " + line)
    except Exception as e:
        print(f"    分发日志查询失败: {e}")


if __name__ == "__main__":
    main()
