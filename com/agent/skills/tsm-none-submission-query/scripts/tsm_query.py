#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TSM 未提报工时查询工具

用法:
  python tsm_query.py org-tree [--keyword <关键词>]
  python tsm_query.py query --dept-ids <id1,id2,...> [--begin-date <YYYY-MM-DD>] [--end-date <YYYY-MM-DD>]
"""

import sys
import io
import json
import argparse
import os
from datetime import date, timedelta
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError
import ssl

# Fix Windows stdout encoding for Chinese characters
if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
if sys.stderr.encoding != "utf-8":
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")

BASE_URL = "https://pfgatewayuat.transsion.com:9199/service-ipm-tsm"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(SCRIPT_DIR, "tsm_config.json")

ssl_ctx = ssl.create_default_context()
ssl_ctx.check_hostname = False
ssl_ctx.verify_mode = ssl.CERT_NONE

_headers_cache = None


def load_config():
    """从 tsm_config.json 加载认证 token"""
    if not os.path.exists(CONFIG_FILE):
        return error_exit(f"config file not found: {CONFIG_FILE}")
    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    if "p-auth" not in cfg or "p-rtoken" not in cfg:
        return error_exit("config must contain 'p-auth' and 'p-rtoken'")
    return cfg


def get_headers():
    global _headers_cache
    if _headers_cache is None:
        cfg = load_config()
        _headers_cache = {
            "Content-Type": "application/json",
            "p-auth": cfg["p-auth"],
            "p-rtoken": cfg["p-rtoken"],
        }
    return _headers_cache


def error_exit(msg):
    print(json.dumps({"error": msg}, ensure_ascii=False))
    sys.exit(1)


def http_get(path):
    url = BASE_URL + path
    req = Request(url, headers=get_headers())
    try:
        with urlopen(req, context=ssl_ctx) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (URLError, HTTPError) as e:
        return error_exit(f"request failed: {e}")


def http_post(path, body):
    url = BASE_URL + path
    data = json.dumps(body).encode("utf-8")
    req = Request(url, data=data, headers=get_headers(), method="POST")
    try:
        with urlopen(req, context=ssl_ctx) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (URLError, HTTPError) as e:
        return error_exit(f"request failed: {e}")


# ---------- org-tree ----------

def flatten_tree(node):
    """递归展平组织架构树"""
    result = []
    if node.get("id") and node.get("name"):
        result.append({
            "id": node["id"],
            "name": node["name"],
            "parentId": node.get("parentId"),
        })
    for child in (node.get("childList") or []):
        result.extend(flatten_tree(child))
    return result


def cmd_org_tree(args):
    resp = http_get("/tsm/workHourSubmitSet/getOrgTree")
    if resp.get("code") != "200":
        return error_exit(resp.get("message", "unknown error"))

    all_depts = flatten_tree(resp["data"])

    if args.keyword:
        kw = args.keyword.lower()
        matched = [d for d in all_depts if kw in d["name"].lower()]
        print(json.dumps({
            "total": len(all_depts),
            "matched": len(matched),
            "departments": matched,
        }, ensure_ascii=False, indent=2))
    else:
        print(json.dumps({
            "total": len(all_depts),
            "departments": all_depts,
        }, ensure_ascii=False, indent=2))


# ---------- query ----------

def get_last_month_range():
    today = date.today()
    first_of_this_month = today.replace(day=1)
    last_of_prev = first_of_this_month - timedelta(days=1)
    first_of_prev = last_of_prev.replace(day=1)
    return first_of_prev.isoformat(), last_of_prev.isoformat()


def cmd_query(args):
    dept_ids = [d.strip() for d in args.dept_ids.split(",") if d.strip()]
    if not dept_ids:
        return error_exit("dept-ids is required")

    # begin_date 和 end_date 必须同时提供或同时省略
    if bool(args.begin_date) != bool(args.end_date):
        return error_exit("begin-date and end-date must be provided together")

    if args.begin_date and args.end_date:
        begin_date = args.begin_date
        end_date = args.end_date
    else:
        begin_date, end_date = get_last_month_range()

    all_records = []
    current = 1
    size = 9999

    while True:
        body = {
            "count": True,
            "param": {
                "beginDate": begin_date,
                "endDate": end_date,
                "deptIdList": dept_ids,
            },
            "current": current,
            "size": size,
        }
        resp = http_post("/tsm/noneSubmission/queryNoneSubmissionInfo", body)

        if resp.get("code") != "200":
            return error_exit(resp.get("message", "unknown error"))

        page_data = resp.get("data", {})
        records = page_data.get("data", [])
        all_records.extend(records)

        total = page_data.get("total", 0)
        if len(all_records) >= total or not records:
            break
        current += 1

    total_days = sum(r.get("noneSubmissionDays", 0) for r in all_records)
    print(json.dumps({
        "beginDate": begin_date,
        "endDate": end_date,
        "totalPeople": len(all_records),
        "totalDays": total_days,
        "records": all_records,
    }, ensure_ascii=False, indent=2))


# ---------- main ----------

def main():
    parser = argparse.ArgumentParser(description="TSM 未提报工时查询工具")
    sub = parser.add_subparsers(dest="command")

    p_tree = sub.add_parser("org-tree", help="查询组织架构树")
    p_tree.add_argument("--keyword", "-k", help="模糊匹配部门名称关键词")

    p_query = sub.add_parser("query", help="查询未提报工时")
    p_query.add_argument("--dept-ids", "-d", required=True, help="部门ID列表，逗号分隔")
    p_query.add_argument("--begin-date", "-b", help="开始日期 YYYY-MM-DD")
    p_query.add_argument("--end-date", "-e", help="结束日期 YYYY-MM-DD")

    args = parser.parse_args()

    if args.command == "org-tree":
        cmd_org_tree(args)
    elif args.command == "query":
        cmd_query(args)
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
