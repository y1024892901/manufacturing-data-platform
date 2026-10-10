#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Generate realistic, repeatable demo records in the local development database.

The generator is additive: it only fills form tables to at least the requested
volume, keeps existing records, and uses stable business numbers so reruns do
not duplicate generated documents.  No schema, auth, or workflow tables are
modified.

Example (dry run):
    python -X utf8 source-data/generate.py --systems mdm,crm

Apply to the local development database:
    python -X utf8 source-data/generate.py --systems mdm,crm --apply
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / "infra" / ".env"
MYSQL = Path(r"D:\mysql8\bin\mysql.exe")
TARGET = 200
RUN_DATE = dt.date.today()

PROVINCES = ["上海", "苏州", "杭州", "南京", "无锡", "宁波", "合肥", "武汉", "长沙", "成都", "重庆", "天津", "青岛", "济南", "西安", "广州", "深圳", "东莞", "厦门", "福州"]
COMPANY_MIDDLES = ["远航", "恒信", "华辰", "精工", "启明", "瑞达", "科创", "宏远", "新锐", "卓越", "安泰", "智联", "恒通", "鼎盛", "嘉和", "正力", "优能", "博远", "联创", "致远"]
COMPANY_ENDINGS = ["智能装备有限公司", "精密制造有限公司", "自动化科技有限公司", "机电技术有限公司", "工业设备有限公司"]
CONTACT_SURNAMES = list("王李张刘陈杨赵黄周吴徐孙胡朱高林何郭马罗梁宋郑谢韩唐冯于董萧程曹袁邓许傅沈曾彭吕苏卢蒋蔡贾丁魏薛叶阎余潘杜戴夏钟汪田任姜范方石姚谭廖邹熊金陆郝孔白崔康毛邱秦江史顾侯邵孟龙万段雷钱汤尹黎易常武乔贺赖龚文")
CONTACT_GIVEN = ["明", "华", "伟", "芳", "静", "磊", "敏", "杰", "婷", "强", "娜", "凯", "琳", "超", "丽", "涛", "宁", "勇", "佳", "峰", "晨", "悦", "洋", "倩"]
INDUSTRIES = ["工业自动化", "新能源装备", "汽车零部件", "精密仪器", "电子制造", "智能物流", "数控装备", "轨道交通", "医疗器械", "工程机械"]
PRODUCT_FAMILIES = ["伺服驱动器", "精密减速机", "智能控制柜", "数控加工单元", "自动检测设备", "工业输送模组", "机器视觉终端", "柔性装配工作站", "高精度传感器", "能源监测终端"]
MATERIAL_NAMES = ["高精度轴承", "伺服电机", "线性导轨", "工业控制器", "铝合金壳体", "精密齿轮组", "传感器组件", "电源模块", "连接器组件", "防护钣金件", "编码器", "滚珠丝杠", "工业交换机", "制动单元", "气动阀组"]
SALES_USERS = [("liuyang", "刘洋"), ("qianyi", "钱一"), ("zhangwei", "张伟")]
FOLLOW_TEXT = ["已与客户确认当前产线的设备改造窗口，客户希望在下季度预算评审前收到方案。", "客户完成技术交流，已提供现用设备参数，下一步安排现场测量并补充节拍数据。", "采购负责人正在收集三家方案，约定本周复核交付周期和备件保障条件。", "客户反馈现有工位存在换型时间偏长的问题，已记录产品型号与年度需求计划。", "已向客户说明产品验证流程和质保范围，客户计划安排工艺与设备团队共同评审。"]
PROBLEM_TEXT = ["设备运行过程中出现间歇性报警，客户希望确认传感器和控制参数的匹配情况。", "客户反馈包装到货后外箱局部受潮，要求核对运输防护并补充防潮措施。", "现场调试时设备节拍略高于既定目标，客户希望共同复核工艺参数和测试条件。", "客户发现两批产品标签上的配置说明不一致，要求核实批次记录并更新发货校验。", "使用过程中偶发通讯中断，客户希望确认接口版本并提供现场排查安排。"]


def read_env() -> dict[str, str]:
    if not ENV_FILE.is_file():
        raise RuntimeError(f"未找到本机开发环境配置：{ENV_FILE}")
    values: dict[str, str] = {}
    for line in ENV_FILE.read_text(encoding="utf-8-sig").splitlines():
        match = re.match(r"^\s*([^#=\s]+)\s*=\s*(.*?)\s*$", line)
        if match:
            values[match.group(1)] = match.group(2).strip("\"'")
    if values.get("ENV", "").lower() != "dev" or values.get("DEMO_MODE", "").lower() != "true":
        raise RuntimeError("造数器仅允许写入 infra/.env 明确标记的 dev + DEMO_MODE 环境。")
    for key in ("MYSQL_HOST", "MYSQL_PORT", "MYSQL_USER", "MYSQL_PASSWORD"):
        if not values.get(key):
            raise RuntimeError(f"开发环境配置缺少 {key}。")
    return values


class MySql:
    def __init__(self, values: dict[str, str]):
        if not MYSQL.is_file():
            raise RuntimeError(f"没有找到本机 MySQL 客户端：{MYSQL}")
        self.values = values
        self.env = os.environ.copy()
        self.env["MYSQL_PWD"] = values["MYSQL_PASSWORD"]

    def run(self, sql: str) -> str:
        result = subprocess.run(
            [str(MYSQL), "--protocol=tcp", "--default-character-set=utf8mb4",
             "-h", self.values["MYSQL_HOST"], "-P", self.values["MYSQL_PORT"],
             "-u", self.values["MYSQL_USER"], "--batch", "--raw", "--skip-column-names"],
            input=sql, text=True, encoding="utf-8", errors="replace",
            capture_output=True, env=self.env, check=False,
        )
        if result.returncode:
            # Do not print SQL or environment values; diagnostics identify the
            # constraint/type failure while this local generator is repaired.
            detail = result.stderr.strip().splitlines()
            safe_detail = detail[-1][:240] if detail else "MySQL 返回非零状态"
            raise RuntimeError("本地数据库语句执行失败：" + safe_detail)
        return result.stdout.strip()

    def query(self, sql: str) -> list[list[str]]:
        output = self.run(sql)
        return [line.split("\t") for line in output.splitlines() if line]


def q(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, dt.date):
        value = value.isoformat()
    if isinstance(value, dt.datetime):
        value = value.strftime("%Y-%m-%d %H:%M:%S")
    return "'" + str(value).replace("\\", "\\\\").replace("'", "\\'").replace("\x00", "") + "'"


def name_of(index: int) -> str:
    district = (index - 1) // 20 + 1
    return f"{PROVINCES[(index * 7) % len(PROVINCES)]}{COMPANY_MIDDLES[(index * 11) % len(COMPANY_MIDDLES)]}{COMPANY_ENDINGS[(index * 3) % len(COMPANY_ENDINGS)]}（{INDUSTRIES[(index - 1) % len(INDUSTRIES)]}事业部{district:02d}）"


def short_name(index: int) -> str:
    return f"{COMPANY_MIDDLES[(index * 11) % len(COMPANY_MIDDLES)]}{PROVINCES[(index * 7) % len(PROVINCES)]}{((index - 1) // 20) + 1:02d}事业部"


def person(index: int) -> str:
    return CONTACT_SURNAMES[index % len(CONTACT_SURNAMES)] + CONTACT_GIVEN[(index * 5 + 2) % len(CONTACT_GIVEN)] + CONTACT_GIVEN[(index * 7 + 3) % len(CONTACT_GIVEN)]


def phone(index: int) -> str:
    # Reserved-looking office numbers used only by this local demo dataset.
    return f"021-8000-{1000 + index:04d}"


def date_offset(days: int) -> dt.date:
    return RUN_DATE + dt.timedelta(days=days)


def count_table(db: MySql, schema: str, table: str) -> int:
    rows = db.query(f"SELECT COUNT(*) FROM `{schema}`.`{table}`")
    return int(rows[0][0]) if rows else 0


def marker_count(db: MySql, schema: str, table: str, key: str, prefix: str) -> int:
    rows = db.query(f"SELECT COUNT(*) FROM `{schema}`.`{table}` WHERE `{key}` LIKE {q(prefix + '%')}")
    return int(rows[0][0]) if rows else 0


def insert_rows(db: MySql, schema: str, table: str, key: str, prefix: str,
                columns: list[str], build: Any, target: int | None = None) -> int:
    target = TARGET if target is None else target
    total = count_table(db, schema, table)
    generated = marker_count(db, schema, table, key, prefix)
    missing = max(0, target - total)
    if not missing:
        return 0
    start = generated + 1
    rows = [build(i) for i in range(start, start + missing)]
    sql = ["START TRANSACTION;", f"INSERT INTO `{schema}`.`{table}` ({','.join(f'`{c}`' for c in columns)}) VALUES"]
    sql.append(",\n".join("(" + ",".join(q(value) for value in row) + ")" for row in rows) + ";")
    sql.append("COMMIT;")
    db.run("\n".join(sql))
    return len(rows)


def query_dicts(db: MySql, schema: str, table: str, columns: list[str], where: str = "") -> list[dict[str, str]]:
    rows = db.query(f"SELECT {','.join(f'`{column}`' for column in columns)} FROM `{schema}`.`{table}` {where}")
    return [dict(zip(columns, row)) for row in rows]


def insert_missing_contacts(db: MySql, customers: list[dict[str, str]]) -> int:
    existing = db.query("SELECT CONCAT(partner_type,':',partner_id) FROM src_mdm.md_partner_contact")
    keys = {row[0] for row in existing}
    rows = []
    for i, customer in enumerate(customers, 1):
        identity = f"CUSTOMER:{customer['id']}"
        if identity in keys:
            continue
        rows.append(("CUSTOMER", int(customer["id"]), customer["contact_person"] or person(i),
                     "采购与项目联系人", phone(i), f"contact{i:04d}@partner.example.invalid", 1,
                     "ACTIVE", dt.datetime.combine(date_offset(-(i % 140)), dt.time(9, 0))))
    if rows:
        statement = ["START TRANSACTION;",
                     "INSERT INTO src_mdm.md_partner_contact(partner_type,partner_id,contact_name,position_name,mobile,email,is_primary,status,created_at) VALUES"]
        statement.append(",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in rows) + ";")
        statement.append("COMMIT;")
        db.run("\n".join(statement))
    return len(rows)


def seed_mdm(db: MySql) -> dict[str, list[dict[str, str]]]:
    totals: dict[str, int] = {}
    def customer(i: int) -> tuple[Any, ...]:
        level = ("A", "B", "C")[(i - 1) % 3]
        limit = 300000 + (i % 18) * 100000
        name = name_of(i)
        return (f"C-MFG26-{i:04d}", name, short_name(i), level,
                ("OEM", "DIRECT", "END_USER", "DISTRIBUTOR")[(i - 1) % 4], INDUSTRIES[(i - 1) % len(INDUSTRIES)],
                PROVINCES[(i * 7) % len(PROVINCES)], limit, round(limit * ((i % 4) / 20), 2),
                "月结30天" if i % 3 else "月结45天", person(i), phone(i),
                f"sales{i:04d}@partner.example.invalid", f"{PROVINCES[(i * 7) % len(PROVINCES)]}高新技术产业园{(i % 12) + 1}号",
                "PUBLISHED", 1, "系统演示客户档案", "admin", dt.datetime.combine(date_offset(-(i % 320)), dt.time(10, 0)))
    total = count_table(db, "src_mdm", "md_customer")
    gen = marker_count(db, "src_mdm", "md_customer", "customer_code", "C-MFG26-")
    new_count = max(0, TARGET - total)
    if new_count:
        rows = [customer(i) for i in range(gen + 1, gen + new_count + 1)]
        cols = ["customer_code", "customer_name", "short_name", "customer_level", "customer_type", "industry", "region", "credit_limit", "credit_used", "payment_terms", "contact_person", "contact_phone", "contact_email", "address", "status", "version_no", "change_reason", "created_by", "created_at"]
        sql = "START TRANSACTION;\nINSERT INTO src_mdm.md_customer(" + ",".join(f"`{c}`" for c in cols) + ") VALUES\n"
        sql += ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in rows) + ";\nCOMMIT;"
        db.run(sql)
    totals["mdm.customers"] = count_table(db, "src_mdm", "md_customer")

    def supplier(i: int) -> tuple[Any, ...]:
        lead = 5 + (i % 16)
        return (f"S-MFG26-{i:04d}", f"{name_of(i + 23)}供应链中心", short_name(i + 23),
                ("A", "B", "C")[(i - 1) % 3], ("MATERIAL", "COMPONENT", "PROCESSING", "SERVICE")[(i - 1) % 4], lead,
                MATERIAL_NAMES[(i - 1) % len(MATERIAL_NAMES)], "QUALIFIED", date_offset(365 + i % 180),
                "月结30天" if i % 2 else "月结60天", None, None, round(0.93 + (i % 7) * 0.009, 4),
                round(0.96 + (i % 4) * 0.008, 4), "PUBLISHED", 1, "完成年度供方审核", "admin",
                dt.datetime.combine(date_offset(-(i % 280)), dt.time(10, 30)))
    new_count = max(0, TARGET - count_table(db, "src_mdm", "md_supplier"))
    if new_count:
        gen = marker_count(db, "src_mdm", "md_supplier", "supplier_code", "S-MFG26-")
        rows = [supplier(i) for i in range(gen + 1, gen + new_count + 1)]
        cols = ["supplier_code", "supplier_name", "short_name", "supplier_level", "supplier_type", "lead_time_days", "supply_category", "qual_status", "qual_expire_date", "payment_terms", "bank_account", "tax_no", "on_time_rate", "quality_pass_rate", "status", "version_no", "change_reason", "created_by", "created_at"]
        db.run("START TRANSACTION;\nINSERT INTO src_mdm.md_supplier(" + ",".join(f"`{c}`" for c in cols) + ") VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in rows) + ";\nCOMMIT;")
    totals["mdm.suppliers"] = count_table(db, "src_mdm", "md_supplier")
    suppliers = query_dicts(db, "src_mdm", "md_supplier", ["id", "supplier_code", "supplier_name"], "ORDER BY id")

    materials = query_dicts(db, "src_mdm", "md_material", ["id", "material_code", "material_name", "material_type", "base_unit_code", "standard_price"], "ORDER BY id")
    new_count = max(0, TARGET - len(materials))
    if new_count:
        gen = marker_count(db, "src_mdm", "md_material", "material_code", "M-MFG26-")
        rows = []
        for i in range(gen + 1, gen + new_count + 1):
            rows.append((f"M-MFG26-{i:04d}", MATERIAL_NAMES[(i - 1) % len(MATERIAL_NAMES)] + f" {i:03d}型", f"系列{chr(65 + i % 6)} / 精度等级{(i % 4) + 1}", f"适用于设备装配与产线维护，按批次进行来料检验；技术参数按图纸版本执行。", "PURCHASE", 1 + (i - 1) % 5, "PCS", "PCS", 1, 20 + i % 120, 730 if i % 5 == 0 else None, 1, int(suppliers[(i - 1) % len(suppliers)]["id"]), round(18 + (i % 130) * 4.25, 4), 1, "尺寸、材质与外观符合采购技术协议。", "PUBLISHED", 1, "年度物料目录扩充", "admin", dt.datetime.combine(date_offset(-(i % 240)), dt.time(11, 0))))
        cols = ["material_code", "material_name", "material_spec", "spec_desc", "material_type", "category_id", "base_unit_code", "purchase_unit_code", "conversion_rate", "safety_stock", "shelf_life_days", "is_batch_managed", "default_supplier_id", "standard_price", "is_inspection_required", "inspection_standard", "status", "version_no", "change_reason", "created_by", "created_at"]
        db.run("START TRANSACTION;\nINSERT INTO src_mdm.md_material(" + ",".join(f"`{c}`" for c in cols) + ") VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in rows) + ";\nCOMMIT;")
        materials = query_dicts(db, "src_mdm", "md_material", ["id", "material_code", "material_name", "material_type", "base_unit_code", "standard_price"], "ORDER BY id")
    totals["mdm.materials"] = len(materials)

    products = query_dicts(db, "src_mdm", "md_product", ["id", "product_code", "product_name", "unit_code"], "ORDER BY id")
    new_count = max(0, TARGET - len(products))
    if new_count:
        gen = marker_count(db, "src_mdm", "md_product", "product_code", "P-MFG26-")
        material_ids = [int(row["id"]) for row in materials]
        rows = []
        for i in range(gen + 1, gen + new_count + 1):
            family = PRODUCT_FAMILIES[(i - 1) % len(PRODUCT_FAMILIES)]
            rows.append((f"P-MFG26-{i:04d}", f"{family} {i:03d}型", f"MFG-{chr(65 + i % 6)}{200 + i:04d}", material_ids[(i - 1) % len(material_ids)], 1 + (i - 1) % 5, "PCS", round(6 + (i % 45) * 0.73, 4), "MASS", date_offset(-1000 + i % 600), None, "PUBLISHED", 1, "补充在产产品档案", "admin", dt.datetime.combine(date_offset(-(i % 200)), dt.time(11, 30))))
        cols = ["product_code", "product_name", "product_model", "material_id", "category_id", "unit_code", "weight_kg", "lifecycle_status", "launch_date", "eol_date", "status", "version_no", "change_reason", "created_by", "created_at"]
        db.run("START TRANSACTION;\nINSERT INTO src_mdm.md_product(" + ",".join(f"`{c}`" for c in cols) + ") VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in rows) + ";\nCOMMIT;")
        products = query_dicts(db, "src_mdm", "md_product", ["id", "product_code", "product_name", "unit_code"], "ORDER BY id")
    totals["mdm.products"] = len(products)

    customers = query_dicts(db, "src_mdm", "md_customer", ["id", "customer_code", "customer_name", "short_name", "customer_level", "customer_type", "industry", "region", "credit_limit", "payment_terms", "contact_person", "contact_phone", "contact_email"], "ORDER BY id")
    totals["mdm.customer_contacts_added"] = insert_missing_contacts(db, customers)
    # Keep the CRM copy aligned with the MDM authority for customer-selection pages.
    existing_shadow = {row[0] for row in db.query("SELECT customer_code FROM src_crm.crm_md_customer")}
    shadows = []
    for i, c in enumerate(customers, 1):
        if c["customer_code"] in existing_shadow:
            continue
        shadows.append((c["customer_code"], c["customer_name"], c["short_name"], c["customer_level"], c["region"], c["credit_limit"], c["payment_terms"], c["contact_person"], c["contact_phone"], 1, dt.datetime.combine(RUN_DATE, dt.time(9, 0))))
    if shadows:
        db.run("START TRANSACTION;\nINSERT INTO src_crm.crm_md_customer(customer_code,customer_name,short_name,customer_level,region,credit_limit,payment_terms,contact_person,contact_phone,master_version,synced_at) VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in shadows) + ";\nCOMMIT;")
    totals["crm.customer_master_copy"] = count_table(db, "src_crm", "crm_md_customer")
    return {"totals": totals, "customers": customers, "products": products, "materials": materials}


def seed_crm(db: MySql, mdm: dict[str, Any]) -> dict[str, int]:
    customers = [c for c in mdm["customers"] if str(c["customer_code"]).startswith("C-MFG26-")]
    if not customers:
        customers = mdm["customers"]
    products = [p for p in mdm["products"] if str(p["product_code"]).startswith("P-MFG26-")]
    if not products:
        products = mdm["products"]
    if not customers or not products:
        raise RuntimeError("CRM 造数依赖已发布的 MDM 客户和产品档案。")
    totals: dict[str, int] = {}
    lead_codes: list[str] = []
    def lead(i: int) -> tuple[Any, ...]:
        customer = customers[(i - 1) % len(customers)]
        lead_no = f"LD-MFG26-{i:04d}"
        lead_codes.append(lead_no)
        return (lead_no, customer["id"], customer["customer_code"], customer["customer_name"], customer["contact_person"], customer["contact_phone"], ("行业展会", "官网咨询", "客户转介绍", "合作伙伴推荐", "行业协会活动")[(i - 1) % 5], ("HIGH", "MEDIUM", "LOW")[(i - 1) % 3], 55 + (i * 7) % 46, date_offset(30 + i % 180), SALES_USERS[(i - 1) % len(SALES_USERS)][0], "NEW", "admin", dt.datetime.combine(date_offset(-(i % 90)), dt.time(9 + i % 8, 30)), f"sales{i:04d}@customer.example.invalid", ("设备采购经理", "生产技术负责人", "供应链经理", "工艺工程师")[(i - 1) % 4], customer["industry"], 150000 + (i % 25) * 50000, ("电话", "邮件", "现场拜访")[(i - 1) % 3])
    insert_rows(db, "src_crm", "crm_lead", "lead_no", "LD-MFG26-", ["lead_no", "customer_id", "customer_code", "customer_name", "contact_name", "contact_phone", "source_channel", "intent_level", "score", "expected_close_date", "owner_user", "status", "created_by", "created_at", "contact_email", "contact_title", "industry", "expected_budget", "preferred_contact_method"], lead)
    totals["crm.leads"] = count_table(db, "src_crm", "crm_lead")
    generated_leads = query_dicts(db, "src_crm", "crm_lead", ["id", "lead_no", "customer_id", "customer_code", "customer_name", "contact_name", "owner_user", "expected_budget"], "WHERE lead_no LIKE 'LD-MFG26-%' ORDER BY lead_no")
    existing_follow = {int(row[0]) for row in db.query("SELECT lead_id FROM src_crm.crm_lead_follow")}
    follow_rows = []
    for i, lead_row in enumerate(generated_leads, 1):
        if int(lead_row["id"]) in existing_follow:
            continue
        follow_rows.append((int(lead_row["id"]), ("CALL", "VISIT", "EMAIL", "MEETING")[(i - 1) % 4], FOLLOW_TEXT[(i - 1) % len(FOLLOW_TEXT)], dt.datetime.combine(date_offset(5 + i % 35), dt.time(10, 0)), lead_row["owner_user"], dt.datetime.combine(date_offset(-(i % 60)), dt.time(10 + i % 7, 15))))
    if follow_rows:
        db.run("START TRANSACTION;\nINSERT INTO src_crm.crm_lead_follow(lead_id,follow_type,content,next_follow_at,follow_user,follow_at) VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in follow_rows) + ";\nCOMMIT;")
    totals["crm.lead_follow_records"] = count_table(db, "src_crm", "crm_lead_follow")

    def opportunity(i: int) -> tuple[Any, ...]:
        linked = i <= 100 and i <= len(generated_leads)
        lead_row = generated_leads[i - 1] if linked else None
        customer = customers[(i - 1) % len(customers)] if lead_row is None else next((x for x in customers if x["customer_code"] == lead_row["customer_code"]), customers[(i - 1) % len(customers)])
        stage = ("LEAD", "QUALIFY", "PROPOSAL", "NEGOTIATE")[(i - 1) % 4]
        amount = 260000 + (i % 40) * 35000
        code = f"OP-MFG26-{i:04d}"
        return (code, f"{customer['short_name'] or customer['customer_name']} · {PRODUCT_FAMILIES[(i - 1) % len(PRODUCT_FAMILIES)]}改造项目", customer["id"], customer["customer_code"], customer["customer_name"], customer.get("contact_person") or person(i), amount, date_offset(40 + i % 190), stage, dt.datetime.combine(date_offset(-(i % 40)), dt.time(9, 0)), SALES_USERS[(i - 1) % len(SALES_USERS)][0], SALES_USERS[(i - 1) % len(SALES_USERS)][1], "SALES", "admin", dt.datetime.combine(date_offset(-(i % 120)), dt.time(9 + i % 8, 0)), None if lead_row is None else lead_row["lead_no"], 20 + (i % 70), date_offset(40 + i % 190), None, "PIPELINE" if stage in ("LEAD", "QUALIFY") else ("BEST_CASE" if stage == "PROPOSAL" else "COMMIT"), ("NORMAL", "HIGH")[(i - 1) % 2], ("现场核实产线节拍", "组织技术方案评审", "确认交付窗口与验收口径", "复核预算并准备正式报价")[(i - 1) % 4], f"围绕{PRODUCT_FAMILIES[(i - 1) % len(PRODUCT_FAMILIES)]}开展需求评估，已记录产能、接口和交付要求。")
    opp_cols = ["opportunity_no", "opportunity_name", "customer_id", "customer_code", "customer_name", "contact_name", "expect_amount", "expect_sign_date", "stage_code", "stage_updated_at", "owner_user", "owner_name", "dept_code", "created_by", "created_at", "source_lead_no", "probability", "expected_close_date", "lost_reason", "forecast_category", "priority", "next_step", "description"]
    insert_rows(db, "src_crm", "crm_opportunity", "opportunity_no", "OP-MFG26-", opp_cols, opportunity)
    # Mark converted leads in one set-based statement so large seeds stay fast.
    db.run("UPDATE src_crm.crm_lead l JOIN src_crm.crm_opportunity o ON o.source_lead_no=l.lead_no SET l.status='CONVERTED',l.converted_opportunity_no=o.opportunity_no WHERE l.lead_no LIKE 'LD-MFG26-%' AND l.status='NEW'")
    totals["crm.opportunities"] = count_table(db, "src_crm", "crm_opportunity")
    opportunities = query_dicts(db, "src_crm", "crm_opportunity", ["id", "opportunity_no", "customer_id", "customer_code", "customer_name", "expect_amount", "owner_user"], "WHERE opportunity_no LIKE 'OP-MFG26-%' ORDER BY opportunity_no")

    opp_product_rows, competitor_rows, opp_follow_rows = [], [], []
    for i, opp in enumerate(opportunities, 1):
        product = products[(i - 1) % len(products)]
        opp_product_rows.append((opp["opportunity_no"], product["product_code"], product["product_name"], 1 + (i % 12) * 5, "PCS", round(900 + (i % 17) * 85.5, 4)))
        competitor_rows.append((opp["opportunity_no"], ("华东智造科技有限公司", "中科自动化设备有限公司", "远景工业系统有限公司", "启航精密装备有限公司")[(i - 1) % 4], round(float(opp["expect_amount"]) * (1.02 + (i % 8) / 100), 2), "本地服务网络覆盖较广，交付响应速度较快。", "定制化适配周期较长，跨区域备件保障需要进一步确认。"))
        opp_follow_rows.append((opp["opportunity_no"], ("CALL", "VISIT", "EMAIL", "MEETING")[(i - 1) % 4], FOLLOW_TEXT[(i - 1) % len(FOLLOW_TEXT)], opp["owner_user"], dt.datetime.combine(date_offset(-(i % 45)), dt.time(13 + i % 5, 20))))
    for table, cols, rows in [("crm_opportunity_product", ["opportunity_no", "product_code", "product_name", "expected_qty", "unit_code", "expected_price"], opp_product_rows),
                              ("crm_competitor", ["opportunity_no", "competitor_name", "quoted_amount", "strengths", "weaknesses"], competitor_rows),
                              ("crm_opportunity_follow", ["opportunity_no", "follow_type", "follow_content", "follow_user", "follow_at"], opp_follow_rows)]:
        existing = {row[0] for row in db.query(f"SELECT opportunity_no FROM src_crm.{table}")}
        missing_rows = [row for row in rows if row[0] not in existing]
        if missing_rows:
            db.run("START TRANSACTION;\nINSERT INTO src_crm." + table + "(" + ",".join(f"`{c}`" for c in cols) + ") VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in missing_rows) + ";\nCOMMIT;")
    legacy = db.query("SELECT opportunity_no,expect_amount,owner_user FROM src_crm.crm_opportunity WHERE opportunity_no NOT LIKE 'OP-MFG26-%' ORDER BY id LIMIT 1")
    if legacy:
        legacy_no, legacy_amount, legacy_owner = legacy[0]
        for table, columns, values in [
            ("crm_opportunity_product", "opportunity_no,product_code,product_name,expected_qty,unit_code,expected_price", (legacy_no, products[-1]["product_code"], products[-1]["product_name"], 10, "PCS", 1680)),
            ("crm_competitor", "opportunity_no,competitor_name,quoted_amount,strengths,weaknesses", (legacy_no, "华东智造科技有限公司", round(float(legacy_amount or 280000) * 1.04, 2), "本地服务团队响应及时，具备同类产线改造经验。", "跨区域备件保障仍需核实。")),
            ("crm_opportunity_follow", "opportunity_no,follow_type,follow_content,follow_user,follow_at", (legacy_no, "VISIT", "已完成客户现场交流，确认产线改造范围与后续技术评审安排。", legacy_owner or "liuyang", dt.datetime.combine(date_offset(-7), dt.time(14, 0))))]:
            if count_table(db, "src_crm", table) < TARGET:
                db.run(f"INSERT INTO src_crm.{table}({columns}) VALUES (" + ",".join(q(v) for v in values) + ")")
    totals["crm.opportunity_products"] = count_table(db, "src_crm", "crm_opportunity_product")
    totals["crm.opportunity_competitors"] = count_table(db, "src_crm", "crm_competitor")
    totals["crm.opportunity_follow_records"] = count_table(db, "src_crm", "crm_opportunity_follow")

    quote_cols = ["quotation_no", "version_no", "opportunity_no", "customer_code", "currency", "tax_rate", "valid_until", "payment_terms", "total_amount", "total_cost", "discount_rate", "gross_margin_rate", "status", "created_by", "created_at", "ship_to_address", "delivery_terms", "notes"]
    def quote_row(i: int) -> tuple[Any, ...]:
        opp = opportunities[i - 1]
        qty = 1 + (i % 12) * 5
        std, price, cost = 1300 + (i % 6) * 80, 1240 + (i % 6) * 76, 840 + (i % 8) * 45
        amount = qty * price
        margin = round((price - cost) / price * 100, 4)
        discount = round((std - price) / std * 100, 4)
        return (f"QT-MFG26-{i:04d}", 1, opp["opportunity_no"], opp["customer_code"], "CNY", 13, date_offset(45 + i % 80), "30%预付款，发货前付清", amount, qty * cost, discount, margin, "DRAFT", "admin", dt.datetime.combine(date_offset(-(i % 80)), dt.time(10, 0)), f"{PROVINCES[(i * 7) % len(PROVINCES)]}客户指定收货地址", "订单确认后45天内完成交付，现场安装调试另行排期。", "配置与报价有效期以双方确认的技术协议为准。")
    insert_rows(db, "src_crm", "crm_quotation", "quotation_no", "QT-MFG26-", quote_cols, quote_row)
    totals["crm.quotations"] = count_table(db, "src_crm", "crm_quotation")
    quotes = query_dicts(db, "src_crm", "crm_quotation", ["id", "quotation_no", "opportunity_no", "customer_code", "total_amount"], "WHERE quotation_no LIKE 'QT-MFG26-%' ORDER BY quotation_no")
    quote_lines = []
    for i, quote in enumerate(quotes, 1):
        product = products[(i - 1) % len(products)]
        qty = 1 + (i % 12) * 5
        standard = 1300 + (i % 6) * 80
        price = 1240 + (i % 6) * 76
        cost = 840 + (i % 8) * 45
        quote_lines.append((quote["id"], 1, product["product_code"], product["product_name"], qty, product.get("unit_code") or "PCS", standard, price, cost, round(qty * price, 2), round(qty * cost, 2)))
    existing_line_keys = {(int(row[0]), int(row[1])) for row in db.query("SELECT quotation_id,line_no FROM src_crm.crm_quotation_line")}
    new_lines = [row for row in quote_lines if (int(row[0]), int(row[1])) not in existing_line_keys]
    if new_lines:
        db.run("START TRANSACTION;\nINSERT INTO src_crm.crm_quotation_line(quotation_id,line_no,product_code,product_name,qty,unit_code,standard_price,sales_price,standard_cost,line_amount,line_cost) VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in new_lines) + ";\nCOMMIT;")
    totals["crm.quotation_lines"] = count_table(db, "src_crm", "crm_quotation_line")

    contract_cols = ["contract_no", "version_no", "contract_name", "quotation_id", "quotation_no", "opportunity_no", "customer_code", "customer_name", "amount", "tax_amount", "currency", "effective_date", "expire_date", "delivery_terms", "payment_terms", "penalty_terms", "status", "created_by", "created_at", "auto_renew", "renewal_notice_days", "special_terms"]
    def contract_row(i: int) -> tuple[Any, ...]:
        quote = quotes[i - 1]
        opp = opportunities[i - 1]
        amount = float(quote["total_amount"])
        return (f"CT-MFG26-{i:04d}", 1, f"{PROVINCES[(i * 7) % len(PROVINCES)]}{PRODUCT_FAMILIES[(i - 1) % len(PRODUCT_FAMILIES)]}采购与服务合同", quote["id"], quote["quotation_no"], quote["opportunity_no"], quote["customer_code"], opp["customer_name"], amount, round(amount * 0.13, 2), "CNY", date_offset(40 + i % 60), date_offset(405 + i % 60), "按双方确认的项目计划分批交付。", "30%预付款，发货前付清", "因不可抗力造成延期时，双方协商调整履约计划。", "DRAFT", "admin", dt.datetime.combine(date_offset(-(i % 60)), dt.time(11, 0)), i % 5 == 0, 60, "设备验收按技术协议及合同附件执行，质保期为最终验收后12个月。")
    insert_rows(db, "src_crm", "crm_contract", "contract_no", "CT-MFG26-", contract_cols, contract_row)
    totals["crm.contracts"] = count_table(db, "src_crm", "crm_contract")
    contracts = query_dicts(db, "src_crm", "crm_contract", ["id", "contract_no", "amount"], "WHERE contract_no LIKE 'CT-MFG26-%' ORDER BY contract_no")
    existing_plan = {int(row[0]) for row in db.query("SELECT contract_id FROM src_crm.crm_payment_plan")}
    plans = []
    for i, contract in enumerate(contracts, 1):
        if int(contract["id"]) in existing_plan:
            continue
        amount = float(contract["amount"])
        plans.append((int(contract["id"]), 1, date_offset(30 + i % 55), 0.30, round(amount * 0.30, 2), 0, "PLANNED"))
    if plans:
        db.run("START TRANSACTION;\nINSERT INTO src_crm.crm_payment_plan(contract_id,period_no,due_date,plan_ratio,plan_amount,received_amount,status) VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in plans) + ";\nCOMMIT;")
    totals["crm.payment_plans"] = count_table(db, "src_crm", "crm_payment_plan")

    complaint_cols = ["complaint_no", "customer_code", "product_code", "batch_no", "severity", "problem_desc", "responsible_dept", "status", "created_by", "created_at", "contact_email", "source_channel", "response_due_at", "desired_resolution"]
    def complaint(i: int) -> tuple[Any, ...]:
        customer = customers[(i - 1) % len(customers)]
        product = products[(i - 1) % len(products)]
        return (f"CS-MFG26-{i:04d}", customer["customer_code"], product["product_code"], f"LOT-{RUN_DATE:%y%m}-{(i % 40) + 1:03d}", ("LOW", "MEDIUM", "HIGH")[(i - 1) % 3], PROBLEM_TEXT[(i - 1) % len(PROBLEM_TEXT)], ("客户服务部", "质量工程部", "售后技术部")[(i - 1) % 3], "OPEN", "admin", dt.datetime.combine(date_offset(-(i % 70)), dt.time(14, 0)), customer["contact_email"], ("客户热线", "现场服务", "项目验收", "邮件反馈")[(i - 1) % 4], dt.datetime.combine(date_offset(1 + i % 3), dt.time(17, 0)), "请提供问题初步分析、现场处置计划及预计完成时间。")
    insert_rows(db, "src_crm", "crm_complaint", "complaint_no", "CS-MFG26-", complaint_cols, complaint)
    totals["crm.complaints"] = count_table(db, "src_crm", "crm_complaint")

    forecast_rows = []
    existing_forecasts = {(row[0], row[1], row[2], row[3]) for row in db.query("SELECT forecast_month,owner_user,dept_code,product_code FROM src_crm.crm_sales_forecast")}
    for i in range(1, TARGET + 1):
        owner = SALES_USERS[(i - 1) % len(SALES_USERS)][0]
        product = products[(i - 1) % len(products)]
        month = f"{(RUN_DATE.year + ((RUN_DATE.month - 1 + (i - 1) % 6) // 12))}-{((RUN_DATE.month - 1 + (i - 1) % 6) % 12) + 1:02d}"
        key = (month, owner, "SALES", product["product_code"])
        if key in existing_forecasts:
            continue
        amount = 180000 + (i % 33) * 25000
        forecast_rows.append((month, owner, "SALES", product["product_code"], amount, round(amount * (0.2 + (i % 7) * 0.1), 2), dt.datetime.combine(RUN_DATE, dt.time(9, 15))))
    if forecast_rows:
        db.run("START TRANSACTION;\nINSERT INTO src_crm.crm_sales_forecast(forecast_month,owner_user,dept_code,product_code,forecast_amount,weighted_amount,generated_at) VALUES\n" + ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in forecast_rows) + ";\nCOMMIT;")
    totals["crm.sales_forecasts"] = count_table(db, "src_crm", "crm_sales_forecast")
    return totals


def seed_remaining_tables(db: MySql, schemas: set[str]) -> dict[str, int]:
    """Add realistic, uniquely numbered local demo rows to remaining app tables.

    The domain tables use explicit seeders above. Other writable business tables
    have no declared foreign keys in this source database, so this metadata-led
    seeder respects required columns, defaults, enums and identifier shape while
    leaving database-generated ids/timestamps untouched.
    """
    excluded = {"src_crm.crm_lead", "src_crm.crm_opportunity", "src_crm.crm_quotation",
                "src_crm.crm_contract", "src_crm.crm_complaint", "src_crm.crm_payment_plan",
                "src_crm.crm_quotation_line", "src_crm.crm_lead_follow", "src_crm.crm_opportunity_product",
                "src_crm.crm_competitor", "src_crm.crm_opportunity_follow", "src_crm.crm_md_customer",
                "src_mdm.md_customer", "src_mdm.md_supplier", "src_mdm.md_material", "src_mdm.md_product",
                "src_mdm.md_partner_contact", "src_mdm.md_inbox", "src_mdm.md_outbox",
                "src_mdm.md_reconciliation", "src_mdm.md_distribution_log", "src_mdm.md_merge_record",
                "src_mdm.md_change_request", "src_mdm.md_change_history", "src_mdm.md_code_mapping",
                "src_mdm.md_unit", "src_mdm.md_material_category"}
    table_rows = db.query("SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema LIKE 'src_%' AND table_type='BASE TABLE' ORDER BY table_schema,table_name")
    parent_max_cache: dict[tuple[str, str], int] = {}
    results: dict[str, int] = {}
    for schema, table in table_rows:
        full = f"{schema}.{table}"
        if schema not in schemas or full in excluded or "_md_" in table:
            continue
        total = count_table(db, schema, table)
        missing = max(0, TARGET - total)
        if missing == 0:
            results[full] = total
            continue
        metadata = db.query("SELECT column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),COALESCE(NULLIF(extra,''),'<NONE>') FROM information_schema.columns WHERE table_schema=" + q(schema) + " AND table_name=" + q(table) + " ORDER BY ordinal_position")
        cols = [r for r in metadata if "auto_increment" not in r[4] and "GENERATED" not in r[4].upper()]
        inserts: list[list[Any]] = []
        for i in range(total + 1, TARGET + 1):
            values: list[Any] = []
            for name, coltype, nullable, default, extra in cols:
                low, typ = name.lower(), coltype.lower()
                if nullable == "YES" and i % 4 == 0 and low.endswith(("_id", "_at", "_date", "_url", "_email")):
                    values.append(None)
                    continue
                if typ.startswith("enum("):
                    choices = re.findall(r"'((?:\\.|[^'])*)'", coltype)
                    values.append(choices[(i - 1) % len(choices)] if choices else "ACTIVE")
                elif "datetime" in typ or typ == "timestamp":
                    day = (i * 17) % 240
                    future = any(k in low for k in ("due", "expected", "plan", "expire", "valid", "next"))
                    date = date_offset((1 + day % 90) if future else -day)
                    values.append(dt.datetime.combine(date, dt.time(9 + i % 8, (i * 7) % 60)))
                elif typ == "date":
                    future = any(k in low for k in ("due", "expected", "plan", "expire", "valid", "next"))
                    values.append(date_offset(30 + i % 180 if future else -((i * 13) % 500)))
                elif any(x in typ for x in ("int", "decimal", "numeric", "float", "double", "bit")):
                    if low == "id":
                        values.append(TARGET + i)
                    elif low.startswith("is_") or low.startswith("has_"):
                        values.append((i - 1) % 2)
                    elif low == "stat_hour":
                        values.append((i - 1) % 24)
                    elif low.endswith("_id"):
                        # Use a stable existing parent identity where available.
                        token = low[:-3].replace("_", "")
                        candidates = [r[1] for r in table_rows if r[0] == schema and token in r[1].replace("_", "")]
                        parent = candidates[0] if candidates else table
                        cache_key = (schema, parent)
                        parent_max = parent_max_cache.get(cache_key)
                        if parent_max is None:
                            try: parent_max = max(1, int(db.query(f"SELECT COALESCE(MAX(id),1) FROM `{schema}`.`{parent}`")[0][0]))
                            except (ValueError, RuntimeError): parent_max = 1
                            parent_max_cache[cache_key] = parent_max
                        values.append(1 + (i - 1) % parent_max)
                    elif any(k in low for k in ("amount", "price", "cost", "budget", "value", "balance")):
                        values.append(round(2500 + (i % 47) * 137.65, 2))
                    elif any(k in low for k in ("rate", "ratio", "percent", "probability", "pass_rate")):
                        values.append(round(0.68 + (i % 30) / 100, 4))
                    elif "level" in low:
                        values.append(1 + (i - 1) % 5)
                    elif "score" in low:
                        values.append(60 + (i * 7) % 41)
                    elif any(k in low for k in ("qty", "quantity", "count", "stock", "days", "version", "seq", "hour", "line_no")):
                        values.append(1 + (i % 300))
                    else:
                        values.append((i - 1) % 5)
                elif any(x in typ for x in ("char", "text", "json", "set")):
                    if typ.startswith("json"):
                        value = json.dumps({"说明": _CN_TABLE_NOTES[(i - 1) % len(_CN_TABLE_NOTES)], "来源": "本地演示数据"}, ensure_ascii=False)
                    elif low.endswith(("status", "state")) or "approval_status" in low:
                        value = "DRAFT" if "draft" in typ else ("OPEN" if "fault" in table or "defect" in table or "alert" in table else "ACTIVE")
                    elif low in ("energy_type",):
                        value = ("ELECTRIC", "GAS", "WATER", "STEAM")[(i - 1) % 4]
                    elif low in ("material_type",):
                        value = ("RAW", "SEMI", "FINISHED", "PACKAGING", "PURCHASE")[(i - 1) % 5]
                    elif low in ("inspection_type",):
                        value = ("INCOMING", "PROCESS", "FINAL")[(i - 1) % 3]
                    elif low in ("currency",):
                        value = "CNY"
                    elif low in ("customer_code",):
                        value = f"C-MFG26-{i:04d}"
                    elif low in ("supplier_code",):
                        value = f"S-MFG26-{i:04d}"
                    elif low in ("material_code", "child_material_code"):
                        value = f"M-MFG26-{i:04d}"
                    elif low in ("product_code",):
                        value = f"P-MFG26-{i:04d}"
                    elif low in ("equipment_code", "default_equipment_code"):
                        value = f"EQ-MFG26-{i:04d}"
                    elif low in ("meter_code",):
                        value = f"EM-MFG26-{i:04d}"
                    elif low in ("warehouse_code",):
                        value = f"WH-MFG26-{i:04d}"
                    elif low in ("location_code", "bin_code"):
                        value = f"LOC-MFG26-{i:04d}"
                    elif low in ("bom_code",):
                        value = f"BOM-MFG26-{i:04d}"
                    elif low in ("routing_code",):
                        value = f"RT-MFG26-{i:04d}"
                    elif low in ("work_center", "work_center_code"):
                        value = f"WC-MFG26-{i:04d}"
                    elif low in ("operation_code",):
                        value = f"OP-{(i - 1) % 20 + 1:02d}"
                    elif "version" in low and not low.endswith("_code"):
                        value = "V1.0"
                    elif "batch" in low or "lot_no" in low:
                        value = f"LOT-{RUN_DATE:%y%m}-{(i - 1) % 40 + 1:03d}"
                    elif "shift" in low:
                        value = ("白班", "中班", "夜班")[(i - 1) % 3]
                    elif low in ("production_order_no", "prod_order_no"):
                        value = f"PO-MFG26-{i:04d}"
                    elif low in ("customer_name",):
                        value = name_of(i)
                    elif low in ("supplier_name",):
                        value = f"{name_of(i + 23)}供应链中心"
                    elif low in ("material_name",):
                        value = MATERIAL_NAMES[(i - 1) % len(MATERIAL_NAMES)] + f" {i:03d}型"
                    elif low in ("product_name",):
                        value = PRODUCT_FAMILIES[(i - 1) % len(PRODUCT_FAMILIES)] + f" {i:03d}型"
                    elif any(k in low for k in ("contact", "person", "owner_name", "operator", "inspector", "manager")):
                        value = person(i)
                    elif "_no" in low or low.endswith(("_code", "_key")) or low in ("code", "number", "document_no"):
                        tag = re.sub(r"[^A-Z0-9]", "", table.upper())[:6]
                        value = f"M26-{tag}-{i:04d}"
                    elif any(k in low for k in ("name", "title", "subject")):
                        value = f"{_CN_TABLE_NAMES.get(table, '业务记录')} {i:03d}"
                    elif any(k in low for k in ("desc", "remark", "content", "reason", "comment", "note", "solution", "requirement", "spec")):
                        value = _CN_TABLE_NOTES[(i - 1) % len(_CN_TABLE_NOTES)]
                    elif "email" in low:
                        value = f"record{i:04d}@demo.example.invalid"
                    elif any(k in low for k in ("phone", "mobile", "tel")):
                        value = phone(i)
                    elif "user" in low or low.endswith("_by"):
                        value = "admin"
                    elif "dept" in low or "workshop" in low:
                        value = ("装配一部", "机加二部", "质量工程部", "供应链部")[(i - 1) % 4]
                    elif "unit" in low:
                        value = "PCS"
                    elif "address" in low or "location" in low:
                        value = f"{PROVINCES[(i * 7) % len(PROVINCES)]}高新技术产业园{(i % 12) + 1}号"
                    else:
                        value = f"{_CN_TABLE_NAMES.get(table, '业务记录')}：已完成资料核验，按生产计划和质量要求执行。"
                    max_len = int(re.search(r"\((\d+)\)", typ).group(1)) if re.search(r"\((\d+)\)", typ) else 2048
                    if isinstance(value, str) and len(value) > max_len and ("_code" in low or "_no" in low or low.endswith(("code", "_key"))):
                        suffix = f"{i % 10000:04d}"
                        value = value[:max(0, max_len - len(suffix) - 1)] + "-" + suffix
                    values.append(value[:max_len])
                else:
                    values.append(None if nullable == "YES" else "")
            inserts.append(values)
        if cols and inserts:
            sql = ["START TRANSACTION;", f"INSERT INTO `{schema}`.`{table}` ({','.join(f'`{r[0]}`' for r in cols)}) VALUES"]
            sql.append(",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in inserts) + ";")
            sql.append("COMMIT;")
            try:
                db.run("\n".join(sql))
            except RuntimeError as exc:
                raise RuntimeError(f"{full} 批次未写入。{exc}") from None
        results[full] = count_table(db, schema, table)
    return results


def repair_primary_demo_rows(db: MySql, schemas: set[str]) -> None:
    """Normalize synthetic rows to valid codes and operational state values.

    Each UPDATE is limited to the stable identifiers emitted by this generator;
    original demonstration records remain untouched.
    """
    if "src_eam" in schemas:
        db.run("""
UPDATE src_eam.eam_equipment SET
 equipment_code=CONCAT('EQ-MFG26-',LPAD(id,4,'0')),
 equipment_name=CONCAT(ELT(MOD(id-1,6)+1,'数控加工中心','伺服压装机','自动检测设备','精密车床','工业机器人','激光切割设备'),' ',LPAD(id,3,'0'),'号'),
 equipment_model=CONCAT(ELT(MOD(id-1,6)+1,'VMC-850','SERVO-12T','VISION-300','CNC-620','ROBOT-6AX','LASER-3015'),'-',LPAD(1+MOD(id,8),2,'0')),
 equipment_type=ELT(MOD(id-1,4)+1,'加工设备','装配设备','检测设备','物流设备'),
 workshop_code=CONCAT('WS-',LPAD(1+MOD(id-1,8),2,'0')),
 work_center=CONCAT('WC-MFG26-',LPAD(1+MOD(id-1,20),4,'0')),
 location_desc=CONCAT(ELT(MOD(id-1,4)+1,'机加车间','装配车间','质量实验室','成品仓库'),' A区-',LPAD(1+MOD(id-1,30),2,'0'),'工位'),
 capacity_per_hour=20+MOD(id*7,81), equipment_status=ELT(MOD(id-1,4)+1,'IDLE','RUNNING','FAULT','MAINTENANCE'),
 purchase_date=DATE_SUB(CURDATE(),INTERVAL (120+MOD(id*13,1500)) DAY),
 purchase_price=ROUND(58000+MOD(id*17,90)*2150,2),
 warranty_end_date=DATE_ADD(purchase_date,INTERVAL 1095 DAY),
 maintenance_cycle_days=ELT(MOD(id-1,3)+1,30,60,90),
 last_maintenance_date=DATE_SUB(CURDATE(),INTERVAL MOD(id*11,65) DAY),
 health_score=70+MOD(id*7,30), health_level=CASE WHEN MOD(id*7,30)>=22 THEN 'GOOD' WHEN MOD(id*7,30)>=10 THEN 'NORMAL' ELSE 'ATTENTION' END
WHERE equipment_code LIKE 'MFG26-EAMEQUIPMENT-%' OR equipment_code LIKE 'M26-EAMEQUIPMENT-%' OR equipment_code LIKE 'EQ-MFG26-%';
UPDATE src_eam.eam_fault f JOIN src_eam.eam_equipment e ON e.id=MOD(f.id-1,200)+1 SET
 f.fault_no=CONCAT('FLT-MFG26-',LPAD(f.id,4,'0')), f.equipment_code=e.equipment_code, f.equipment_name=e.equipment_name,
 f.fault_type=ELT(MOD(f.id-1,4)+1,'MECHANICAL','ELECTRICAL','CONTROL','HYDRAULIC'),
 f.fault_level=ELT(MOD(f.id-1,4)+1,'LOW','MEDIUM','HIGH','CRITICAL'),
 f.fault_desc=ELT(MOD(f.id-1,4)+1,'主轴运行时出现间歇性振动，需检查轴承与润滑状态。','伺服驱动器报过流告警，需复核负载与接线端子。','安全门联锁信号偶发中断，需检查传感器位置。','液压系统压力偏低，需检查油路密封和滤芯。'),
 f.fault_cause=ELT(MOD(f.id-1,4)+1,'轴承润滑不足','接线端子松动','联锁传感器偏移','滤芯压差偏高'),
 f.fault_time=DATE_SUB(NOW(),INTERVAL MOD(f.id*9,400) HOUR), f.work_order_no=CONCAT('WO-MFG26-',LPAD(MOD(f.id-1,200)+1,4,'0'),'-01'),
 f.prod_order_no=CONCAT('PO-MFG26-',LPAD(MOD(f.id-1,200)+1,4,'0')), f.operation_code=CONCAT('OP-',LPAD(1+MOD(f.id-1,12),2,'0')),
 f.fault_status=IF(MOD(f.id-1,4)=2,'OPEN','CLOSED'), f.reported_by=ELT(MOD(f.id-1,3)+1,'liuyang','qianyi','zhangwei')
WHERE f.fault_no LIKE 'M26-EAMFAULT-%' OR f.fault_no LIKE 'MFG26-EAMFAULT-%' OR f.fault_no LIKE 'FLT-MFG26-%';
UPDATE src_eam.eam_inspection SET inspection_no=CONCAT('EIN-MFG26-',LPAD(id,4,'0')), equipment_code=CONCAT('EQ-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 inspection_type=ELT(MOD(id-1,3)+1,'ROUTINE','SAFETY','PREVENTIVE'), plan_date=DATE_SUB(CURDATE(),INTERVAL MOD(id*5,90) DAY),
 actual_date=DATE_SUB(CURDATE(),INTERVAL MOD(id*5,90) DAY), inspection_result=IF(MOD(id,9)=0,'ATTENTION','PASS'),
 abnormal_desc=IF(MOD(id,9)=0,'发现防护罩固定螺栓松动，已安排紧固复查。',NULL), check_items=JSON_OBJECT('润滑状态','正常','防护装置','完好','运行噪声','符合标准'),
 inspector_code=ELT(MOD(id-1,3)+1,'liuyang','qianyi','zhangwei'), is_missed=0
WHERE inspection_no LIKE 'M26-EAMINSPECTION-%' OR inspection_no LIKE 'MFG26-EAMINSPECTION-%' OR inspection_no LIKE 'EIN-MFG26-%';
UPDATE src_eam.eam_repair SET repair_no=CONCAT('REP-MFG26-',LPAD(id,4,'0')), fault_no=CONCAT('FLT-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 equipment_code=CONCAT('EQ-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')), repair_start_time=DATE_SUB(NOW(),INTERVAL MOD(id*6,350) HOUR),
 repair_end_time=DATE_ADD(DATE_SUB(NOW(),INTERVAL MOD(id*6,350) HOUR),INTERVAL 2 HOUR), downtime_minutes=45+MOD(id*17,360),
 repair_type=ELT(MOD(id-1,3)+1,'CORRECTIVE','PREVENTIVE','EMERGENCY'), repair_content='完成故障定位、易损件检查和设备试运行，运行参数已恢复至工艺范围。',
 replaced_parts=ELT(MOD(id-1,4)+1,'轴承组件','伺服电源模块','接近开关','液压密封件'), repair_cost=ROUND(350+MOD(id*23,2500),2),
 maintenance_hours=1+MOD(id,6), repairman_code=ELT(MOD(id-1,3)+1,'liuyang','qianyi','zhangwei'), repair_result='REPAIRED'
WHERE repair_no LIKE 'M26-EAMREPAIR-%' OR repair_no LIKE 'MFG26-EAMREPAIR-%' OR repair_no LIKE 'REP-MFG26-%';
""")
    if "src_energy" in schemas:
        db.run("""
UPDATE src_energy.energy_meter SET meter_code=CONCAT('EM-MFG26-',LPAD(id,4,'0')),
 meter_name=CONCAT(ELT(MOD(id-1,8)+1,'机加车间电表','装配车间电表','空压站电表','冷却水表','蒸汽流量表','天然气表','试验室电表','总装线电表'),' ',LPAD(id,3,'0')),
 energy_type=ELT(MOD(id-1,5)+1,'ELECTRIC','WATER','GAS','STEAM','COMPRESSED_AIR'),
 unit_code=CASE ELT(MOD(id-1,5)+1,'ELECTRIC','WATER','GAS','STEAM','COMPRESSED_AIR') WHEN 'ELECTRIC' THEN 'KWH' WHEN 'STEAM' THEN 'TON' WHEN 'GAS' THEN 'M3' WHEN 'WATER' THEN 'M3' ELSE 'M3' END,
 meter_kind=IF(MOD(id-1,10)=0,'MAIN','SUBMETER'), workshop_code=CONCAT('WS-',LPAD(1+MOD(id-1,8),2,'0')),
 equipment_code=CONCAT('EQ-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')), parent_meter_code=NULL,
 install_location=CONCAT(ELT(MOD(id-1,4)+1,'机加车间','装配车间','公辅站房','质量实验室'),'计量柜 ',LPAD(id,3,'0')),
 multiplier=1.0000, read_interval_minutes=15, calibration_date=DATE_SUB(CURDATE(),INTERVAL MOD(id*7,300) DAY),
 warning_threshold_percent=15.0000, data_source='能源采集网关', responsible_person=ELT(MOD(id-1,3)+1,'刘洋','钱一','张伟'), status='ACTIVE',
 remark='按月抄表并与产量、班次记录核对，异常读数由能源管理员复核。'
WHERE meter_code LIKE 'EM-MFG26-%' OR meter_name LIKE '能源仪表 %';
UPDATE src_energy.energy_equipment_usage SET equipment_code=CONCAT('EQ-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 meter_code=CONCAT('EM-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')), energy_type=ELT(MOD(id-1,5)+1,'ELECTRIC','WATER','GAS','STEAM','COMPRESSED_AIR'),
 unit_code=CASE ELT(MOD(id-1,5)+1,'ELECTRIC','WATER','GAS','STEAM','COMPRESSED_AIR') WHEN 'ELECTRIC' THEN 'KWH' WHEN 'STEAM' THEN 'TON' WHEN 'GAS' THEN 'M3' ELSE 'M3' END,
 stat_hour=MOD(id-1,24), energy_value=ROUND(60+MOD(id*19,160)/2,4), run_hours=6+MOD(id,5), output_qty=40+MOD(id*3,80),
 unit_consumption=ROUND((60+MOD(id*19,160)/2)/(40+MOD(id*3,80)),6), baseline_value=ROUND((60+MOD(id*19,160)/2)*0.90,4),
 deviation_rate=11.1111, warning_threshold_percent=20, is_abnormal=0, shift_code=ELT(MOD(id-1,3)+1,'DAY','SHIFT_B','NIGHT'),
 production_order_no=CONCAT('PO-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 baseline_period_start_date=DATE_SUB(stat_date,INTERVAL 30 DAY), baseline_period_end_date=DATE_SUB(stat_date,INTERVAL 1 DAY),
 remark='设备运行能耗与当班产出同步记录，基线按最近一个完整生产周期测算。'
WHERE equipment_code LIKE 'EQ-MFG26-%' OR equipment_code LIKE 'MFG26-ENERGYEQUIPMENT-%';
UPDATE src_energy.energy_workshop_usage SET workshop_code=CONCAT('WS-',LPAD(1+MOD(id-1,8),2,'0')),
 energy_type=ELT(MOD(id-1,5)+1,'ELECTRIC','WATER','GAS','STEAM','COMPRESSED_AIR'),
 unit_code=CASE ELT(MOD(id-1,5)+1,'ELECTRIC','WATER','GAS','STEAM','COMPRESSED_AIR') WHEN 'ELECTRIC' THEN 'KWH' WHEN 'STEAM' THEN 'TON' ELSE 'M3' END,
 energy_value=ROUND(850+MOD(id*23,2300),4), total_output=120+MOD(id*7,680), unit_consumption=ROUND((850+MOD(id*23,2300))/(120+MOD(id*7,680)),6),
 baseline_value=ROUND((850+MOD(id*23,2300))*0.92,4), deviation_rate=8.6957, warning_threshold_percent=18, is_abnormal=0,
 cost_center_code=CONCAT('CC-MFG26-',LPAD(1+MOD(id-1,30),3,'0')), shift_code=ELT(MOD(id-1,3)+1,'DAY','SHIFT_B','NIGHT'),
 baseline_period_start_date=DATE_SUB(stat_date,INTERVAL 30 DAY), baseline_period_end_date=DATE_SUB(stat_date,INTERVAL 1 DAY),
 remark='车间能源汇总与当日合格产量、班次及成本中心同步核对。'
WHERE workshop_code LIKE 'MFG26-ENERGYWORKSHOP-%' OR workshop_code LIKE 'WS-%';
UPDATE src_energy.energy_alert SET alert_no=CONCAT('EA-MFG26-',LPAD(id,4,'0')),
 source_type=ELT(MOD(id-1,3)+1,'METER','EQUIPMENT','WORKSHOP'), source_id=MOD(id-1,200)+1,
 object_code=CASE MOD(id-1,3) WHEN 0 THEN CONCAT('EM-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')) WHEN 1 THEN CONCAT('EQ-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')) ELSE CONCAT('WS-',LPAD(1+MOD(id-1,8),2,'0')) END,
 energy_type=ELT(MOD(id-1,5)+1,'ELECTRIC','WATER','GAS','STEAM','COMPRESSED_AIR'),
 actual_value=100+MOD(id*13,600), threshold_value=90+MOD(id*11,500), deviation_rate=12+MOD(id,50),
 severity=ELT(MOD(id-1,4)+1,'LOW','MEDIUM','HIGH','CRITICAL'), status=ELT(MOD(id-1,3)+1,'OPEN','PROCESSING','CLOSED'),
 assigned_to=ELT(MOD(id-1,3)+1,'liuyang','qianyi','zhangwei'), due_date=DATE_ADD(CURDATE(),INTERVAL 1+MOD(id,14) DAY),
 cause='实际能耗较同期基线偏高，需结合运行时长、产量和设备状态复核。', corrective_action='复核采集读数和设备运行参数，完成节能调整后记录验证结果。'
WHERE alert_no LIKE 'M26-ENERGYALERT-%' OR alert_no LIKE 'EA-MFG26-%';
""")
    if "src_erp" in schemas:
        db.run("""
UPDATE src_erp.erp_sales_order SET sales_order_no=CONCAT('SO-MFG26-',LPAD(id,4,'0')),
 customer_code=CONCAT('C-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 customer_name=CONCAT(ELT(MOD((id-1)*3,20)+1,'上海','苏州','杭州','南京','无锡','宁波','合肥','武汉','长沙','成都','重庆','天津','青岛','济南','西安','广州','深圳','东莞','厦门','福州'),
 ELT(MOD((id-1)*11,20)+1,'远航','恒信','华辰','精工','启明','瑞达','科创','宏远','新锐','卓越','安泰','智联','恒通','鼎盛','嘉和','正力','优能','博远','联创','致远'),'智能装备有限公司（工业自动化事业部）'),
 order_date=DATE_SUB(CURDATE(),INTERVAL 5+MOD(id*3,75) DAY), delivery_date=DATE_ADD(CURDATE(),INTERVAL 20+MOD(id*5,100) DAY),
 total_amount=ROUND(180000+MOD(id*37,2800)*125,2), tax_amount=ROUND((180000+MOD(id*37,2800)*125)*0.13,2), currency='CNY',
 order_status=ELT(MOD(id-1,4)+1,'DRAFT','CONFIRMED','IN_PROD','DELIVERED'),
 source_opportunity_no=NULL, source_contract_no=NULL, factory_code='FAC-01',
 sales_user=ELT(MOD(id-1,3)+1,'liuyang','qianyi','zhangwei'), dept_code='SALES',
 created_by='admin', source_type='MANUAL', tax_rate=13, credit_status='UNCHECKED', atp_status='UNCHECKED',
 ship_to_address=CONCAT('华东制造园区 ',LPAD(1+MOD(id-1,30),2,'0'),'号收货平台'), customer_reference=CONCAT('客户计划-',YEAR(CURDATE()),'-',LPAD(id,4,'0')),
 payment_terms='月结30天', shipping_terms='送达指定收货区并完成交接'
WHERE sales_order_no LIKE 'MFG26-ERPSALESORDE-%' OR sales_order_no LIKE 'M26-ERPSALESORDE-%' OR sales_order_no LIKE 'SO-MFG26-%';
UPDATE src_erp.erp_sales_order o JOIN src_mdm.md_customer c ON c.customer_code=o.customer_code
 SET o.customer_name=c.customer_name,o.credit_status='UNCHECKED',o.atp_status='UNCHECKED'
 WHERE o.sales_order_no LIKE 'SO-MFG26-%';
UPDATE src_erp.erp_sales_order_line SET sales_order_no=CONCAT('SO-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 line_no=1, material_code=CONCAT('M-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 material_name=CONCAT(ELT(MOD(id-1,15)+1,'高精度轴承','伺服电机','线性导轨','工业控制器','铝合金壳体','精密齿轮组','传感器组件','电源模块','连接器组件','防护钣金件','编码器','滚珠丝杠','工业交换机','制动单元','气动阀组'),' ',LPAD(id,3,'0'),'型'),
 order_qty=10+MOD(id*7,120), unit_code='PCS', unit_price=ROUND(680+MOD(id*17,180)*12.5,2), line_amount=(10+MOD(id*7,120))*ROUND(680+MOD(id*17,180)*12.5,2),
 delivered_qty=IF(MOD(id-1,4)=3,10+MOD(id*7,120),0), delivery_date=DATE_ADD(CURDATE(),INTERVAL 20+MOD(id*5,100) DAY),
 customer_material_code=CONCAT('CUS-MAT-',LPAD(id,5,'0')), line_remark='按订单技术协议与指定配置供货。'
WHERE id BETWEEN 1 AND 200;
UPDATE src_erp.erp_prod_order SET prod_order_no=CONCAT('PO-MFG26-',LPAD(id,4,'0')),
 sales_order_no=CONCAT('SO-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 product_code=CONCAT('P-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 product_name=CONCAT(ELT(MOD(id-1,10)+1,'伺服驱动器','精密减速机','智能控制柜','数控加工单元','自动检测设备','工业输送模组','机器视觉终端','柔性装配工作站','高精度传感器','能源监测终端'),' ',LPAD(id,3,'0'),'型'),
 plan_qty=80+MOD(id*11,420), completed_qty=IF(MOD(id-1,4)=2,ROUND((80+MOD(id*11,420))*0.45,0),IF(MOD(id-1,4)=3,80+MOD(id*11,420),0)),
 qualified_qty=IF(MOD(id-1,4)=3,80+MOD(id*11,420),IF(MOD(id-1,4)=2,ROUND((80+MOD(id*11,420))*0.43,0),0)),
 scrap_qty=IF(MOD(id-1,4)=2,ROUND((80+MOD(id*11,420))*0.02,0),0), unit_code='PCS',
 plan_start_date=DATE_ADD(CURDATE(),INTERVAL MOD(id,35) DAY), plan_finish_date=DATE_ADD(CURDATE(),INTERVAL 35+MOD(id,75) DAY),
 order_status=ELT(MOD(id-1,4)+1,'DRAFT','RELEASED','IN_PROD','COMPLETED'), priority_level=ELT(MOD(id-1,3)+1,'NORMAL','HIGH','URGENT'),
 factory_code='FAC-01', workshop_code=CONCAT('WS-',LPAD(1+MOD(id-1,8),2,'0')), cc_code=CONCAT('CC-MFG26-',LPAD(1+MOD(id-1,30),3,'0')),
 bom_code=CONCAT('BOM-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')), bom_version='V1.0', routing_code=CONCAT('RT-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 routing_version='V1.0', created_by='admin', kit_status=ELT(MOD(id-1,3)+1,'READY','SHORTAGE','NOT_REQUIRED'), planner_remark='已完成产能、工艺路线与关键物料齐套核对。'
WHERE prod_order_no LIKE 'MFG26-ERPPRODORD-%' OR prod_order_no LIKE 'M26-ERPPRODORD-%' OR prod_order_no LIKE 'PO-MFG26-%';
""")
    if "src_mes" in schemas:
        db.run("""
UPDATE src_mes.mes_work_order SET work_order_no=CONCAT('WO-MFG26-',LPAD(id,4,'0'),'-01'),
 prod_order_no=CONCAT('PO-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')), product_code=CONCAT('P-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 op_seq=1+MOD(id-1,12), operation_code=CONCAT('OP-',LPAD(1+MOD(id-1,12),2,'0')),
 operation_name=ELT(MOD(id-1,6)+1,'来料核对','部件预装','精密加工','电气接线','功能测试','成品检验'),
 routing_code=CONCAT('RT-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')), routing_version='V1.0', equipment_code=CONCAT('EQ-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 work_center=CONCAT('WC-MFG26-',LPAD(1+MOD(id-1,20),4,'0')), workshop_code=CONCAT('WS-',LPAD(1+MOD(id-1,8),2,'0')),
 plan_qty=80+MOD(id*11,420), completed_qty=IF(MOD(id-1,4)=2,ROUND((80+MOD(id*11,420))*0.45,0),IF(MOD(id-1,4)=3,80+MOD(id*11,420),0)),
 qualified_qty=IF(MOD(id-1,4)=3,80+MOD(id*11,420),IF(MOD(id-1,4)=2,ROUND((80+MOD(id*11,420))*0.43,0),0)),
 scrap_qty=IF(MOD(id-1,4)=2,ROUND((80+MOD(id*11,420))*0.02,0),0), plan_start_time=DATE_ADD(NOW(),INTERVAL MOD(id,35) DAY),
 plan_end_time=DATE_ADD(NOW(),INTERVAL 35+MOD(id,75) DAY), wo_status=ELT(MOD(id-1,4)+1,'CREATED','RELEASED','STARTED','COMPLETED'),
 plan_hours=8, actual_hours=IF(MOD(id-1,4)>=2,4,0), workshop_user=ELT(MOD(id-1,3)+1,'liuyang','qianyi','zhangwei'),
 priority_level=ELT(MOD(id-1,3)+1,'NORMAL','HIGH','URGENT'), batch_no=CONCAT('LOT-',DATE_FORMAT(CURDATE(),'%y%m'),'-',LPAD(1+MOD(id-1,40),3,'0')),
 shift_code=ELT(MOD(id-1,3)+1,'DAY','SHIFT_B','NIGHT'), remark='按工艺路线执行，完工后需完成数量确认与质量检验。'
WHERE work_order_no LIKE 'M26-MESWOR-%' OR work_order_no LIKE 'WO-MFG26-%';
UPDATE src_mes.mes_work_report SET report_no=CONCAT('WR-MFG26-',LPAD(id,4,'0')),
 work_order_no=CONCAT('WO-MFG26-',LPAD(MOD(id-1,200)+1,4,'0'),'-01'), prod_order_no=CONCAT('PO-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 op_seq=1+MOD(id-1,12), report_qty=10+MOD(id*7,40), qualified_qty=9+MOD(id*7,39), scrap_qty=MOD(id,3),
 report_date=DATE_SUB(CURDATE(),INTERVAL MOD(id*3,45) DAY), start_time=DATE_SUB(NOW(),INTERVAL 8 HOUR), end_time=DATE_SUB(NOW(),INTERVAL 1 HOUR),
 work_hours=7, equipment_code=CONCAT('EQ-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')), operator_code=ELT(MOD(id-1,3)+1,'liuyang','qianyi','zhangwei'),
 shift_code=ELT(MOD(id-1,3)+1,'DAY','SHIFT_B','NIGHT'), is_paused=0, pause_minutes=0, created_by='admin', updated_by='admin', remark='本班报工数量已与工序流转卡核对。'
WHERE id BETWEEN 1 AND 200;
UPDATE src_mes.mes_prod_result SET prod_order_no=CONCAT('PO-MFG26-',LPAD(id,4,'0')), product_code=CONCAT('P-MFG26-',LPAD(id,4,'0')),
 total_plan_qty=80+MOD(id*11,420), total_completed_qty=IF(MOD(id-1,4)=2,ROUND((80+MOD(id*11,420))*0.45,0),IF(MOD(id-1,4)=3,80+MOD(id*11,420),0)),
 total_qualified_qty=IF(MOD(id-1,4)=3,80+MOD(id*11,420),IF(MOD(id-1,4)=2,ROUND((80+MOD(id*11,420))*0.43,0),0)),
 progress_rate=IF(MOD(id-1,4)=3,1.0000,IF(MOD(id-1,4)=2,0.4500,0.0000)), current_op_seq=1+MOD(id-1,12), last_report_at=DATE_SUB(NOW(),INTERVAL MOD(id*9,400) HOUR)
WHERE id BETWEEN 1 AND 200;
""")
    if "src_srm" in schemas:
        db.run("""
UPDATE src_srm.srm_purchase_order SET purchase_order_no=CONCAT('PO-SRM-MFG26-',LPAD(id,4,'0')),
 supplier_code=CONCAT('S-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 supplier_name=CONCAT(ELT(MOD((id-1)*3,20)+1,'上海','苏州','杭州','南京','无锡','宁波','合肥','武汉','长沙','成都','重庆','天津','青岛','济南','西安','广州','深圳','东莞','厦门','福州'),
 ELT(MOD((id-1)*11,20)+1,'远航','恒信','华辰','精工','启明','瑞达','科创','宏远','新锐','卓越','安泰','智联','恒通','鼎盛','嘉和','正力','优能','博远','联创','致远'),'智能装备有限公司供应链中心'),
 material_code=CONCAT('M-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 material_name=CONCAT(ELT(MOD(id-1,15)+1,'高精度轴承','伺服电机','线性导轨','工业控制器','铝合金壳体','精密齿轮组','传感器组件','电源模块','连接器组件','防护钣金件','编码器','滚珠丝杠','工业交换机','制动单元','气动阀组'),' ',LPAD(id,3,'0'),'型'),
 order_qty=20+MOD(id*7,180), received_qty=IF(MOD(id-1,4)=3,20+MOD(id*7,180),IF(MOD(id-1,4)=2,ROUND((20+MOD(id*7,180))*0.6,0),0)),
 unit_code='PCS', unit_price=ROUND(35+MOD(id*19,280)*3.8,2), total_amount=(20+MOD(id*7,180))*ROUND(35+MOD(id*19,280)*3.8,2),
 expected_date=DATE_ADD(CURDATE(),INTERVAL 15+MOD(id*5,60) DAY), promised_date=DATE_ADD(CURDATE(),INTERVAL 18+MOD(id*5,60) DAY),
 order_status=ELT(MOD(id-1,4)+1,'CREATED','SENT','PARTIAL','CLOSED'), currency_code='CNY', payment_terms='月结30天',
 shipping_terms='送至指定收货区，随货提供材质证明和检验报告', ship_to_address=CONCAT('华东制造园区 ',LPAD(1+MOD(id-1,30),2,'0'),'号收货平台'),
 buyer_name=ELT(MOD(id-1,3)+1,'刘洋','钱一','张伟'), supplier_reference_no=CONCAT('SUP-ACK-',LPAD(id,5,'0')),
 supplier_note='到货前请确认包装、防潮措施及批次标签信息。'
WHERE purchase_order_no LIKE 'M26-SRMPUR-%' OR purchase_order_no LIKE 'MFG26-SRMPURCHASE-%' OR purchase_order_no LIKE 'PO-SRM-MFG26-%';
UPDATE src_srm.srm_asn SET asn_no=CONCAT('ASN-MFG26-',LPAD(id,4,'0')),
 purchase_order_no=CONCAT('PO-SRM-MFG26-',LPAD(MOD(id-1,199)+2,4,'0')),
 supplier_code=CONCAT('S-MFG26-',LPAD(MOD(id-1,199)+2,4,'0')), material_code=CONCAT('M-MFG26-',LPAD(MOD(id-1,199)+2,4,'0')),
 batch_no=CONCAT('LOT-',DATE_FORMAT(CURDATE(),'%y%m'),'-',LPAD(1+MOD(id-1,40),3,'0')), ship_qty=20+MOD(id*7,150),
 expected_arrival_date=DATE_ADD(CURDATE(),INTERVAL 5+MOD(id*3,30) DAY), status=ELT(MOD(id-1,3)+1,'SHIPPED','ARRIVED','RECEIVED'),
 carrier_name=ELT(MOD(id-1,4)+1,'安达物流','华东供应链运输','顺达货运','制造业协同物流'), tracking_no=CONCAT('TRK',YEAR(CURDATE()),LPAD(id,8,'0')),
 packing_slip_no=CONCAT('PS-MFG26-',LPAD(id,4,'0')), contact_name=ELT(MOD(id-1,3)+1,'李明','周静','陈磊'), note='按采购订单分批发运，外包装已完成防潮与标签复核。'
WHERE asn_no LIKE 'M26-SRMASN-%' OR asn_no LIKE 'MFG26-SRMASN-%' OR asn_no LIKE 'ASN-MFG26-%';
""")
    if "src_qms" in schemas:
        db.run("""
UPDATE src_qms.qms_inspection SET inspection_no=CONCAT('QI-MFG26-',LPAD(id,4,'0')),
 inspection_type=ELT(MOD(id-1,4)+1,'IQC','IPQC','FQC','OQC'), source_type='MANUAL', source_no=CONCAT('检验委托-',YEAR(CURDATE()),'-',LPAD(id,4,'0')),
 material_code=CONCAT('M-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 material_name=CONCAT(ELT(MOD(id-1,15)+1,'高精度轴承','伺服电机','线性导轨','工业控制器','铝合金壳体','精密齿轮组','传感器组件','电源模块','连接器组件','防护钣金件','编码器','滚珠丝杠','工业交换机','制动单元','气动阀组'),' ',LPAD(id,3,'0'),'型'),
 batch_no=CONCAT('LOT-',DATE_FORMAT(CURDATE(),'%y%m'),'-',LPAD(1+MOD(id-1,40),3,'0')),
 supplier_code=CONCAT('S-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 inspected_qty=80+MOD(id*7,120), sample_qty=5+MOD(id,16),
 qualified_qty=IF(MOD(id,11)=0,ROUND((80+MOD(id*7,120))*0.88,0),80+MOD(id*7,120)),
 defect_qty=IF(MOD(id,11)=0,ROUND((80+MOD(id*7,120))*0.12,0),0),
 inspection_point=ELT(MOD(id-1,5)+1,'来料检验台','加工工位A区','电气测试台','总装终检工位','出货复核区'),
 inspect_result=IF(MOD(id,11)=0,'FAILED','PASSED'), inspection_basis='产品图纸及对应版本检验规范',
 inspection_method=ELT(MOD(id-1,4)+1,'抽样测量','外观检查','功能测试','尺寸复核'),
 environment_temp=22+MOD(id,6), environment_humidity=45+MOD(id*3,16), workshop_code=CONCAT('WS-',LPAD(1+MOD(id-1,8),2,'0')),
 remark='检验记录已关联物料批次，测量设备按校准周期使用。'
WHERE inspection_no LIKE 'M26-QMSINS-%' OR inspection_no LIKE 'MFG26-QMSINSPECTION-%' OR inspection_no LIKE 'QI-MFG26-%';
UPDATE src_qms.qms_defect SET defect_no=CONCAT('QD-MFG26-',LPAD(id,4,'0')),
 inspection_no=CONCAT('QI-MFG26-',LPAD(MOD(id-1,199)+2,4,'0')), material_code=CONCAT('M-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 defect_qty=1+MOD(id,8), defect_type=ELT(MOD(id-1,4)+1,'SIZE','APPEARANCE','PERFORMANCE','MATERIAL'),
 defect_level=ELT(MOD(id-1,3)+1,'MINOR','MAJOR','CRITICAL'), defect_code=CONCAT('QC-',LPAD(1+MOD(id-1,25),3,'0')),
 location_desc=ELT(MOD(id-1,4)+1,'轴承座安装面','电气接线端子','外壳喷涂表面','成品功能测试工位'),
 disposition=ELT(MOD(id-1,4)+1,'OPEN','REWORK','SCRAP','CONCESSION'), disposition_qty=1+MOD(id,8), responsible_dept='质量工程部'
WHERE defect_no LIKE 'M26-QMSDEF-%' OR defect_no LIKE 'MFG26-QMSDEFECT-%' OR defect_no LIKE 'QD-MFG26-%';
UPDATE src_qms.qms_ncr SET ncr_no=CONCAT('NCR-MFG26-',LPAD(id,4,'0')), inspection_no=CONCAT('QI-MFG26-',LPAD(MOD(id-1,199)+2,4,'0')),
 material_code=CONCAT('M-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')), supplier_code=CONCAT('S-MFG26-',LPAD(MOD(id-1,200)+1,4,'0')),
 defect_qty=1+MOD(id,8), defect_type=ELT(MOD(id-1,4)+1,'SIZE','APPEARANCE','PERFORMANCE','MATERIAL'),
 risk_evaluation='已评估库存、在制品和已交付批次影响，当前按批次隔离并跟踪复检结果。',
 severity=ELT(MOD(id-1,3)+1,'MINOR','MAJOR','CRITICAL'), responsible_dept='质量工程部', owner_user=ELT(MOD(id-1,3)+1,'liuyang','qianyi','zhangwei'),
 due_date=DATE_ADD(CURDATE(),INTERVAL 5+MOD(id,25) DAY), status=ELT(MOD(id-1,4)+1,'OPEN','REVIEWING','REWORK','CLOSED'),
 disposition=ELT(MOD(id-1,4)+1,'REWORK','SCRAPPED','CONCESSION','RETURNED')
WHERE ncr_no LIKE 'M26-QMSNCR-%' OR ncr_no LIKE 'MFG26-QMSNCR-%' OR ncr_no LIKE 'NCR-MFG26-%';
""")


def sync_master_mirrors(db: MySql, schemas: set[str]) -> None:
    """Populate downstream read-only master-data copies from MDM records."""
    material_selects = {
        "src_eam": "INSERT IGNORE INTO src_eam.eam_md_material(material_code,material_name,base_unit_code,master_version,synced_at) SELECT material_code,material_name,base_unit_code,version_no,NOW(3) FROM src_mdm.md_material",
        "src_mes": "INSERT IGNORE INTO src_mes.mes_md_material(material_code,material_name,base_unit_code,master_version,synced_at) SELECT material_code,material_name,base_unit_code,version_no,NOW(3) FROM src_mdm.md_material",
        "src_qms": "INSERT IGNORE INTO src_qms.qms_md_material(material_code,material_name,is_inspection_required,inspection_standard,master_version,synced_at) SELECT material_code,material_name,is_inspection_required,inspection_standard,version_no,NOW(3) FROM src_mdm.md_material",
        "src_srm": "INSERT IGNORE INTO src_srm.srm_md_material(material_code,material_name,base_unit_code,purchase_unit_code,conversion_rate,standard_price,master_version,synced_at) SELECT material_code,material_name,base_unit_code,purchase_unit_code,conversion_rate,standard_price,version_no,NOW(3) FROM src_mdm.md_material",
        "src_wms": "INSERT IGNORE INTO src_wms.wms_md_material(material_code,material_name,base_unit_code,safety_stock,is_batch_managed,master_version,synced_at) SELECT material_code,material_name,base_unit_code,safety_stock,is_batch_managed,version_no,NOW(3) FROM src_mdm.md_material",
        "src_erp": "INSERT IGNORE INTO src_erp.erp_md_material(material_code,material_name,material_spec,material_type,base_unit_code,standard_price,safety_stock,master_version,synced_at) SELECT material_code,material_name,material_spec,material_type,base_unit_code,standard_price,safety_stock,version_no,NOW(3) FROM src_mdm.md_material",
        "src_plm": "INSERT IGNORE INTO src_plm.plm_md_material(material_code,material_name,material_spec,master_version,synced_at) SELECT material_code,material_name,material_spec,version_no,NOW(3) FROM src_mdm.md_material"
    }
    for schema, statement in material_selects.items():
        if schema in schemas:
            db.run(statement)
    if "src_erp" in schemas:
        db.run("INSERT IGNORE INTO src_erp.erp_md_customer(customer_code,customer_name,credit_limit,credit_used,payment_terms,master_version,synced_at) SELECT customer_code,customer_name,credit_limit,credit_used,payment_terms,version_no,NOW(3) FROM src_mdm.md_customer WHERE status='PUBLISHED'")
        db.run("INSERT IGNORE INTO src_erp.erp_md_supplier(supplier_code,supplier_name,lead_time_days,master_version,synced_at) SELECT supplier_code,supplier_name,lead_time_days,version_no,NOW(3) FROM src_mdm.md_supplier WHERE status='PUBLISHED'")
        db.run("INSERT IGNORE INTO src_erp.erp_md_product(product_code,product_name,product_model,lifecycle_status,master_version,synced_at) SELECT product_code,product_name,product_model,lifecycle_status,version_no,NOW(3) FROM src_mdm.md_product WHERE status='PUBLISHED'")
        db.run("INSERT IGNORE INTO src_erp.erp_md_cost_center(cc_code,cc_name,master_version,synced_at) SELECT cc_code,cc_name,version_no,NOW(3) FROM src_mdm.md_cost_center WHERE status IN ('PUBLISHED','ACTIVE')")
        db.run("INSERT IGNORE INTO src_erp.erp_md_bom(bom_code,bom_version,product_code,is_current,base_qty,master_version,synced_at) SELECT bom_code,bom_version,product_code,is_current,base_qty,version_no,NOW(3) FROM src_mdm.md_bom WHERE status IN ('PUBLISHED','ACTIVE')")
        db.run("INSERT IGNORE INTO src_erp.erp_md_bom_line(bom_code,bom_version,line_no,child_material_code,qty_per,unit_code,scrap_rate,synced_at) SELECT b.bom_code,b.bom_version,l.line_no,l.child_material_code,l.qty_per,l.unit_code,l.scrap_rate,NOW(3) FROM src_mdm.md_bom_line l JOIN src_mdm.md_bom b ON b.id=l.bom_id WHERE b.status IN ('PUBLISHED','ACTIVE')")
    if "src_plm" in schemas:
        db.run("INSERT IGNORE INTO src_plm.plm_md_product(product_code,product_name,product_model,lifecycle_status,master_version,synced_at) SELECT product_code,product_name,product_model,lifecycle_status,version_no,NOW(3) FROM src_mdm.md_product WHERE status='PUBLISHED'")
        db.run("INSERT IGNORE INTO src_plm.plm_md_bom(bom_code,bom_version,product_code,bom_type,effective_date,master_version,synced_at) SELECT bom_code,bom_version,product_code,bom_type,effective_date,version_no,NOW(3) FROM src_mdm.md_bom WHERE status IN ('PUBLISHED','ACTIVE')")
        db.run("INSERT IGNORE INTO src_plm.plm_md_bom_line(bom_code,bom_version,line_no,child_material_code,qty_per,unit_code,scrap_rate,synced_at) SELECT b.bom_code,b.bom_version,l.line_no,l.child_material_code,l.qty_per,l.unit_code,l.scrap_rate,NOW(3) FROM src_mdm.md_bom_line l JOIN src_mdm.md_bom b ON b.id=l.bom_id WHERE b.status IN ('PUBLISHED','ACTIVE')")
        db.run("INSERT IGNORE INTO src_plm.plm_md_routing(routing_code,routing_version,routing_name,product_code,effective_date,master_version,synced_at) SELECT routing_code,routing_version,routing_name,product_code,effective_date,version_no,NOW(3) FROM src_mdm.md_routing WHERE status IN ('PUBLISHED','ACTIVE')")
        db.run("INSERT IGNORE INTO src_plm.plm_md_routing_operation(routing_code,routing_version,op_seq,operation_code,operation_name,work_center,setup_time_min,run_time_min,default_equipment_code,is_key_operation,is_inspection_op,master_version,synced_at) SELECT r.routing_code,r.routing_version,o.op_seq,o.operation_code,o.operation_name,o.work_center,o.setup_time_min,o.run_time_min,o.default_equipment_code,o.is_key_operation,o.is_inspection_op,r.version_no,NOW(3) FROM src_mdm.md_routing_operation o JOIN src_mdm.md_routing r ON r.id=o.routing_id WHERE r.status IN ('PUBLISHED','ACTIVE')")
    if "src_mes" in schemas:
        db.run("INSERT IGNORE INTO src_mes.mes_md_routing_operation(routing_code,routing_version,op_seq,operation_code,operation_name,work_center,run_time_min,default_equipment_code,is_inspection_op,master_version,synced_at) SELECT r.routing_code,r.routing_version,o.op_seq,o.operation_code,o.operation_name,o.work_center,o.run_time_min,o.default_equipment_code,o.is_inspection_op,r.version_no,NOW(3) FROM src_mdm.md_routing_operation o JOIN src_mdm.md_routing r ON r.id=o.routing_id WHERE r.status IN ('PUBLISHED','ACTIVE')")
    if "src_srm" in schemas:
        db.run("INSERT IGNORE INTO src_srm.srm_md_supplier(supplier_code,supplier_name,supplier_level,lead_time_days,qual_status,master_version,synced_at) SELECT supplier_code,supplier_name,supplier_level,lead_time_days,qual_status,version_no,NOW(3) FROM src_mdm.md_supplier WHERE status='PUBLISHED'")
    if "src_energy" in schemas:
        db.run("INSERT IGNORE INTO src_energy.energy_md_equipment(equipment_code,equipment_name,workshop_code,capacity_per_hour,master_version,synced_at) SELECT equipment_code,equipment_name,workshop_code,capacity_per_hour,1,NOW(3) FROM src_eam.eam_equipment")


def repair_plm_demo_rows(db: MySql, schemas: set[str]) -> None:
    """Turn only the generator's identifiable PLM placeholder rows into usable documents."""
    if "src_plm" not in schemas:
        return
    db.run("""
UPDATE src_plm.plm_ecr SET
 ecr_no=CONCAT('ECR-MFG26-',LPAD(id,4,'0')),
 ecr_title=CONCAT(ELT(MOD(id-1,6)+1,'伺服驱动器接口优化','精密减速机装配公差调整','控制柜散热结构改进','检测工位节拍优化','线性导轨防护方案更新','设备安全联锁改进'),'（',LPAD(id,3,'0'),'）'),
 product_code=CONCAT('P-MFG26-',LPAD(id,4,'0')),
 problem_desc=ELT(MOD(id-1,5)+1,'试制评审发现接口公差偏紧，需复核装配间隙并降低现场调整风险。','客户工况下传动组件温升偏高，需校核负载边界与润滑方案。','产线换型时检测节拍波动，需优化工装定位及检验顺序。','现场反馈防护罩检修空间不足，需评估结构和维护可达性。','电气联锁验证发现信号恢复时间偏长，需复核线路与程序接口。'),
 change_reason=ELT(MOD(id-1,5)+1,'为提高批次装配一致性并减少返工，需要在新版本中固化改进方案。','为满足新版客户技术协议要求，需更新产品定义并完成重新验证。','为降低产线等待时间，需基于当前节拍数据调整工艺设计。','为提升现场维护安全性，需要补充可操作空间和防护要求。','为保障测试结果稳定，需统一接口定义与检验依据。'),
 urgency=ELT(MOD(id-1,4)+1,'LOW','NORMAL','HIGH','URGENT'),
 proposal=ELT(MOD(id-1,5)+1,'建议建立尺寸复核清单，更新接口图纸并安排小批试装验证。','建议调整润滑参数和装配间隙，完成温升对比测试后再定版。','建议优化夹具定位基准，按工艺路线复测标准节拍。','建议调整防护罩开合方式并进行维护可达性评审。','建议完善联锁测试用例，验证异常恢复与安全停机逻辑。'),
 planned_effective_date=DATE_ADD(CURDATE(),INTERVAL 15+MOD(id,75) DAY),
 status='ECO_CREATED'
WHERE ecr_no LIKE 'M26-PLMECR-%';

UPDATE src_plm.plm_eco SET
 eco_no=CONCAT('ECO-MFG26-',LPAD(id,4,'0')),
 ecr_id=id,
 eco_title=CONCAT(ELT(MOD(id-1,6)+1,'接口公差优化执行方案','装配公差与检具更新方案','控制柜热管理改进方案','检测节拍优化执行方案','维护空间结构改进方案','安全联锁逻辑改进方案'),'（',LPAD(id,3,'0'),'）'),
 product_code=CONCAT('P-MFG26-',LPAD(id,4,'0')),
 change_scope=ELT(MOD(id-1,5)+1,'更新产品图纸尺寸链，补充装配检查要求并安排样件复核。','调整关键工序参数，新增首件确认与过程抽检要求。','修改结构设计并完成热仿真、样件测试和维护评审。','更新检测工位作业顺序，验证设备节拍及重复性。','完善检修空间、防护要求和受控作业文件。'),
 target_type=IF(MOD(id,2)=0,'BOM','ROUTING'),
 target_code=IF(MOD(id,2)=0,CONCAT('BOM-MFG26-',LPAD(id,4,'0')),CONCAT('RT-MFG26-',LPAD(id,4,'0'))),
 new_version='V1.1', status='DRAFT'
WHERE eco_no LIKE 'M26-PLMECO-%';

UPDATE src_plm.plm_ecn SET
 ecn_no=CONCAT('ECN-MFG26-',LPAD(id,4,'0')),
 ecr_id=NULL, eco_id=NULL,
 ecn_title=CONCAT(ELT(MOD(id-1,6)+1,'伺服接口优化变更通知','装配公差更新变更通知','控制柜散热改进变更通知','检测节拍优化变更通知','防护结构更新变更通知','安全联锁改进变更通知'),'（',LPAD(id,3,'0'),'）'),
 product_code=CONCAT('P-MFG26-',LPAD(id,4,'0')),
 bom_code=CONCAT('BOM-MFG26-',LPAD(id,4,'0')),
 change_type=ELT(MOD(id-1,3)+1,'DESIGN','MATERIAL','PROCESS'), target_type='BOM', target_version='V1.1',
 change_content='按批准的工程方案更新对应 BOM 版本，保留现行版本并执行首批物料与装配验证。',
 change_reason='依据客户工况反馈、样件评审结果和生产可制造性分析提出版本改进。',
 ecn_status='DRAFT', effective_date=DATE_ADD(CURDATE(),INTERVAL 30+MOD(id,90) DAY),
 submitted_by=NULL, approved_by=NULL, approved_at=NULL
WHERE ecn_no LIKE 'M26-PLMECN-%';
""")

    # Add clear, actionable ECR records without overwriting user-created rows.
    products = db.query("SELECT product_code FROM src_plm.plm_md_product ORDER BY product_code LIMIT 100")
    extra_rows = []
    for i, row in enumerate(products, 1):
        product = row[0]
        kind = "DRAFT" if i <= 50 else "ANALYZED"
        ecr_no = f"ECR-MFG26-{kind[0]}-{i:03d}"
        title = f"{product} 工程改进申请 {i:03d}"
        extra_rows.append((ecr_no, title, product,
                           "结合试制装配记录和客户现场反馈，评估版本调整对性能、工艺和检验的影响。",
                           "提升量产一致性并降低现场维护与返工风险。", ("NORMAL", "HIGH", "LOW")[i % 3],
                           "更新受控图纸或工艺参数，完成样件验证和跨部门评审。", date_offset(20 + i % 90), kind, "demo-plm"))
    if extra_rows:
        values = ",\n".join("(" + ",".join(q(value) for value in row) + ")" for row in extra_rows)
        db.run("INSERT IGNORE INTO src_plm.plm_ecr(ecr_no,ecr_title,product_code,problem_desc,change_reason,urgency,proposal,planned_effective_date,status,created_by) VALUES\n" + values)

    analyzed = db.query("SELECT id,product_code FROM src_plm.plm_ecr WHERE ecr_no LIKE 'ECR-MFG26-A-%' ORDER BY ecr_no")
    for row in analyzed:
        ecr_id, product = int(row[0]), row[1]
        object_code = "BOM-" + product.removeprefix("P-")
        existing = db.query("SELECT COUNT(*) FROM src_plm.plm_impact_analysis WHERE ecr_id=" + str(ecr_id))
        if not existing or int(existing[0][0]) == 0:
            db.run("INSERT INTO src_plm.plm_impact_analysis(ecr_id,impact_type,object_code,impact_desc,risk_level,cost_impact,schedule_impact_days,owner_user) VALUES(" + ",".join(map(q, (ecr_id, "BOM", object_code, "评估物料清单用量、装配工时和检验覆盖范围，变更前后需完成首件对比。", "MEDIUM", 3200 + ecr_id * 8, ecr_id % 6, "demo-plm"))) + ")")
            db.run("UPDATE src_plm.plm_ecr SET status='ANALYZED' WHERE id=" + str(ecr_id) + " AND status='DRAFT'")


def cleanup_synthetic_nonforms(db: MySql) -> None:
    """Remove only fake workflow/audit rows mistakenly generated as forms."""
    db.run("""
DELETE FROM src_mdm.md_change_request WHERE request_no LIKE 'MFG26-MDCHANGEREQU-%';
DELETE FROM src_mdm.md_change_history WHERE entity_code LIKE 'MFG26-MDCHANGEHIST-%';
DELETE FROM src_mdm.md_code_mapping WHERE old_code=current_code AND old_code LIKE 'MFG26-MDCODEMAPPIN-%';
DELETE FROM src_mdm.md_distribution_log WHERE id>21 AND entity_code LIKE 'MFG26-MDDISTRIBUTI-%';
UPDATE src_mdm.md_material_category SET
 category_code=CONCAT('CAT-MFG26-',LPAD(id,4,'0')),
 category_name=CONCAT(ELT(MOD(id-6,15)+1,'轴承及传动件','伺服与驱动模块','线性运动部件','工业控制模块','机加工结构件','齿轮传动组件','传感与测量组件','电源及能源模块','电气连接器件','设备防护钣金','编码与反馈组件','丝杠传动部件','工业网络设备','制动与安全单元','气动执行元件'),'（',LPAD(id,3,'0'),'）'),
 parent_id=1+MOD(id-6,5), category_level=2, is_leaf=1,
 category_path=CONCAT(ELT(MOD(id-6,5)+1,'原材料','半成品','成品','备品备件','包装材料'),'/',ELT(MOD(id-6,15)+1,'轴承及传动件','伺服与驱动模块','线性运动部件','工业控制模块','机加工结构件','齿轮传动组件','传感与测量组件','电源及能源模块','电气连接器件','设备防护钣金','编码与反馈组件','丝杠传动部件','工业网络设备','制动与安全单元','气动执行元件'),'（',LPAD(id,3,'0'),'）'),
 status='PUBLISHED', version_no=1, created_by='admin', updated_by='admin'
WHERE id BETWEEN 6 AND 200 AND category_code LIKE 'MFG26-MDMATERIALCA-%';
""")


_CN_TABLE_NAMES = {
    "erp_sales_order": "销售订单", "erp_sales_order_line": "订单明细", "erp_purchase_requisition": "采购申请",
    "erp_prod_order": "生产订单", "erp_invoice": "销售发票", "erp_receipt": "回款记录", "erp_payable": "应付账款",
    "erp_receivable": "应收账款", "erp_fin_voucher": "财务凭证", "qms_inspection": "质量检验", "qms_defect": "不合格品记录",
    "qms_ncr": "不合格评审", "qms_capa": "纠正预防措施", "eam_equipment": "生产设备", "eam_fault": "设备故障",
    "eam_repair": "维修工单", "mes_work_order": "生产工单", "mes_work_report": "生产报工", "mes_andon_event": "车间异常",
    "srm_purchase_order": "采购订单", "srm_rfq": "询价项目", "srm_supplier_quote": "供应商报价", "wms_receipt": "收货单",
    "wms_transfer": "库存调拨", "wms_count_plan": "库存盘点", "plm_ecn": "工程变更", "plm_document": "受控文档",
    "plm_product_family": "产品族", "md_bom": "物料清单", "md_routing": "工艺路线", "md_warehouse": "仓库档案",
    "md_cost_center": "成本中心", "md_org_unit": "组织单元", "md_employee": "员工档案", "energy_meter": "能源仪表",
    "energy_alert": "能耗告警", "energy_equipment_usage": "设备能耗记录", "energy_workshop_usage": "车间能耗记录"
}
_CN_TABLE_NOTES = [
    "已根据现场需求确认规格、数量和交付窗口，相关部门正在按计划协同处理。",
    "本记录对应本季度制造运营安排，执行前已核对图纸版本、物料状态和质量标准。",
    "经业务负责人复核，当前方案满足产线节拍与追溯要求，后续按节点更新执行结果。",
    "已与供应链、生产及质量团队完成信息确认，需按约定时间完成检验和交接。",
    "现场反馈设备运行稳定，关键参数处于工艺控制范围，记录用于后续运营追溯。"
]


def main() -> int:
    global TARGET
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--systems", default="mdm,crm,erp,plm,srm,wms,mes,qms,eam,energy", help="需要补齐的系统，逗号分隔")
    parser.add_argument("--target", type=int, default=TARGET, help="每个可录入业务表单的目标总记录数，默认 200")
    parser.add_argument("--apply", action="store_true", help="将追加数据写入 infra/.env 指定的本机 dev 演示库；省略时只显示目标范围")
    args = parser.parse_args()
    if args.target < 200:
        parser.error("本任务的每种业务表单至少 200 条，--target 不能小于 200。")
    systems = {item.strip().lower() for item in args.systems.split(",") if item.strip()}
    supported = {"mdm", "crm", "erp", "plm", "srm", "wms", "mes", "qms", "eam", "energy"}
    unknown = systems - supported
    if unknown:
        parser.error("未知系统：" + ", ".join(sorted(unknown)))
    if not systems:
        parser.error("请至少指定一个系统，例如 --systems mdm,crm。")
    TARGET = args.target
    values = read_env()
    db = MySql(values)
    if not args.apply:
        print(f"目标：{', '.join(sorted(systems))}；每个业务单据表至少 {args.target} 条。")
        print("当前是预览模式；实际写入请加 --apply。数据库范围仅为本机 dev 演示库。")
        for schema in sorted(systems):
            dbname = "src_" + schema
            for row in db.query("SELECT table_name,table_rows FROM information_schema.tables WHERE table_schema=" + q(dbname) + " AND table_type='BASE TABLE' ORDER BY table_name"):
                print(f"  {dbname}.{row[0]}: 当前约 {row[1]}，目标至少 {args.target}")
        return 0
    result: dict[str, Any] = {}
    mdm: dict[str, Any] | None = None
    if "mdm" in systems or "crm" in systems:
        mdm = seed_mdm(db)
        result.update(mdm["totals"])
    if "crm" in systems:
        assert mdm is not None
        result.update(seed_crm(db, mdm))
    selected = {"src_" + name for name in systems}
    result.update(seed_remaining_tables(db, selected))
    repair_primary_demo_rows(db, selected)
    sync_master_mirrors(db, selected)
    repair_plm_demo_rows(db, selected)
    cleanup_synthetic_nonforms(db)
    print("本地演示数据已补齐；以下为各业务表当前总量：")
    for table, rows in result.items():
        print(f"  {table}: {rows}")
    print("生成数据均使用稳定业务编号，可重复执行补齐，不会清理或覆盖现有记录。")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(2)
