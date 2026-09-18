# -*- coding: utf-8 -*-
"""BOM 三级审批端到端演示脚本。

四个账号接力走完：工艺工程师提交 → 工艺主管 → 生产主管 → 成本会计。

用法:  python ops/demo_bom_approval.py
"""
import json
import sys
import urllib.request

BASE = "http://localhost:8080"
PASSWORD = "Test@123456"

# 演示角色与预期
CHAIN = [
    ("zhaoliu",   "赵六",   "工艺工程师", "submit"),
    ("zhoutao",   "周涛",   "工艺主管",   "approve"),
    ("yangfan",   "杨帆",   "生产主管",   "approve"),
    ("zhengshuang", "郑爽", "成本会计",   "approve"),
]


def call(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())


def login(username):
    r = call("POST", "/api/auth/login", {"username": username, "password": PASSWORD})
    if r.get("code") != 0:
        print("  [错误] 登录失败:", r.get("message"))
        sys.exit(1)
    return r["data"]


def hr(title):
    print("\n" + "=" * 66)
    print("  " + title)
    print("=" * 66)


def main():
    hr("BOM 三级审批端到端演示")

    # ---------- 1. 赵六提交 ----------
    zhaoliu = login("zhaoliu")
    print(f"\n[1] {zhaoliu['realName']}（{zhaoliu['positionName']}）提交 BOM 变更申请")
    print(f"    角色: {zhaoliu['roleNames']}   可访问系统: {zhaoliu['systems']}")

    r = call("POST", "/api/demo/bom-change?bomCode=BOM-MOTOR-001&materialCode=M-2043"
                     "&oldQty=3&newQty=4", None, zhaoliu["token"])
    if r.get("code") != 0:
        print("    [错误]", r.get("message"))
        sys.exit(1)

    inst = r["data"]
    instance_id = inst["instanceId"]
    print(f"    实例号  : {inst['instanceNo']}")
    print(f"    业务标题: BOM-MOTOR-001 · 用量变更 3 → 4")
    print(f"    当前节点: {inst['currentNodeSeq']} → 等待【工艺主管】")
    print(f"    ✓ {inst['message']}")

    # ---------- 2~4. 逐个审批 ----------
    for idx, (username, real_name, position, action) in enumerate(CHAIN[1:], start=2):
        user = login(username)
        print(f"\n[{idx}] {user['realName']}（{user['positionName']}）登录")
        print(f"    角色: {user['roleNames']}")

        # 查待办
        pend = call("GET", "/api/workflow/tasks/pending", None, user["token"])
        tasks = pend.get("data") or []
        print(f"    待办数量: {len(tasks)}")

        mine = [t for t in tasks if t["instanceId"] == instance_id]
        if not mine:
            print(f"    [错误] 未找到实例 {instance_id} 的待办")
            print(f"           现有待办: {[(t['taskId'], t['nodeName']) for t in tasks]}")
            sys.exit(1)

        task = mine[0]
        print(f"    待办节点: 【{task['nodeName']}】")
        print(f"    业务标题: {task['bizTitle']}")
        print(f"    提交人  : {task['submitter']}    已等待 {task['waitingHours']} 小时")
        print(f"    数据快照: {task['bizSnapshot'].strip()[:100]}...")

        opinion = {
            "zhoutao": "BOM 结构正确，用量调整有工艺依据，同意",
            "yangfan": "已评估排产影响，现有产能可满足，同意",
            "zhengshuang": "成本影响已核算，单件成本上升可接受，同意",
        }.get(username, "同意")

        r = call("POST", f"/api/workflow/tasks/{task['taskId']}/approve",
                 {"opinion": opinion}, user["token"])
        if r.get("code") != 0:
            print("    [错误]", r.get("message"))
            sys.exit(1)

        res = r["data"]
        print(f'    审批意见: "{opinion}"')
        print(f"    ✓ {res['message']}   流程状态={res['status']}")

    # ---------- 5. 时间轴 ----------
    hr("审批时间轴（演示核心视觉）")
    tl = call("GET", f"/api/workflow/instances/{instance_id}/timeline", None, zhaoliu["token"])
    for item in tl.get("data") or []:
        node = item.get("nodeName") or "发起"
        dur = f"  耗时 {item['durationMin']} 分钟" if item.get("durationMin") else ""
        print(f"  {item['operatedAt'][:19]}  {item['operatorName']:6} "
              f"{item['actionLabel']:6} 【{node}】{dur}")
        if item.get("opinion"):
            print(f"      意见: {item['opinion']}")

    # ---------- 6. 最终状态 ----------
    hr("最终状态")
    final = call("GET", f"/api/workflow/instances/{instance_id}", None, zhaoliu["token"])
    d = final["data"]
    print(f"  实例号  : {d['instanceNo']}")
    print(f"  业务标题: {d['bizTitle']}")
    print(f"  最终状态: {d['status']}")
    print(f"  提交人  : {d['submitter']}")
    print(f"  总耗时  : {d['totalDurationMin']} 分钟")
    print()
    if d["status"] == "APPROVED":
        print("  ✓ BOM 三级审批全链路走通 —— 四个账号接力完成")
    else:
        print(f"  ✗ 流程未通过，状态异常: {d['status']}")


if __name__ == "__main__":
    main()
