#!/usr/bin/env python3
"""
Add hidden userview menus that expose dashboard-side datalists for the data
API permission check.

Background. Joget's data-list JSON endpoint
(`/jw/web/json/data/list/{appId}/{datalistId}`) requires the requesting user
to have access to AT LEAST one userview menu that lists the datalist as its
`datalistId`. Several HtmlPage menus (Executive Overview, District Maps, the
chart-wrapped Reports) fetch their data via JavaScript from such datalists,
but none of those datalists are formally exposed by any userview menu in the
farmersPortal userview. Today this works for `admin` because admin is a
super-user; every non-admin user gets HTTP 403.

Fix shape. Append a hidden category "_data_access" containing one minimal
CrudMenu per orphan datalist. The category and its menus are hidden from the
nav (no clutter for the operator) but Joget's permission check still grants
access through them.

Usage:
    tooling/expose_dashboard_datalists.py --dry-run
    tooling/expose_dashboard_datalists.py --apply
"""

import argparse
import datetime
import json
import os
import re
import sys
import urllib.error
import urllib.request

import psycopg2

PG = dict(
    host="joget-pgsql-sa.postgres.database.azure.com",
    dbname="jogetdb", user="jogetadmin",
    password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require",
)
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": os.environ.get("JOGET_API_KEY", ""),
}

PERM_CLASS = "org.joget.apps.userview.lib.GroupPermission"
MENU_CLASS = "org.joget.plugin.enterprise.CrudMenu"
CATEGORY_ID = "_data_access"
CATEGORY_LABEL = "Data access"  # hidden by category-level permission below

# Per-datalist role assignment. Match the parent dashboard's permission.
# Dashboards (Executive Overview, District Maps): sysadmin + district_supervisor + finance_officer
# Reports — Approval rates, Voucher velocity: same three (sysadmin/district_supervisor/finance_officer)
# Reports — Budget envelope: sysadmin + finance_officer
DATALIST_ROLES = {
    "dl_budget_envelopes_view":         ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_budget_events_view":            ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_budget_gl_view":                ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_dashboard_apps_by_district":    ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_dashboard_budget_events":       ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_dashboard_district_metrics":    ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_dashboard_envelopes":           ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_dashboard_kpi":                 ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_dashboard_parcel_growth":       ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_dashboard_recent_activity":     ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_dashboard_voucher_status":      ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_report_approval_rates":         ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
    "dl_report_envelope_detail":        ["role_sysadmin", "role_finance_officer"],
    "dl_report_voucher_velocity":       ["role_sysadmin", "role_district_supervisor", "role_finance_officer"],
}


def make_menu(datalist_id, roles):
    """Minimal CrudMenu that exposes the given datalist for permission gating only."""
    return {
        "className": MENU_CLASS,
        "properties": {
            "id":          "menu-data-access-" + datalist_id,
            "customId":    datalist_id,
            "label":       datalist_id,             # hidden anyway via category permission
            "datalistId":  datalist_id,
            "selectionType": "none",
            "edit-readonly": "true",
            "list-showDeleteButton": "no",
            "iconIncluded": False,
            "permission": {
                "className": PERM_CLASS,
                "properties": {
                    "groupId": ",".join(roles),
                },
            },
        },
    }


def push_userview(uv):
    payload = {
        "appId":        "farmersPortal",
        "userviewId":   uv["properties"]["id"],
        "userviewName": uv["properties"].get("name", uv["properties"]["id"]),
        "json":         json.dumps(uv),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/userviews",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG)
    cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    # Backup
    if args.apply:
        ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
        backup = f"_backups/v.preDashboardExpose.{ts}.pretty.json"
        os.makedirs(os.path.dirname(backup), exist_ok=True)
        with open(backup, "w") as f:
            json.dump(uv, f, indent=2)
        print(f"[backup] {backup}")

    # Drop any pre-existing _data_access category (idempotent)
    uv["categories"] = [c for c in uv["categories"]
                        if c["properties"].get("id") != CATEGORY_ID]

    # Build the hidden category with one menu per datalist
    union_roles = sorted({r for rs in DATALIST_ROLES.values() for r in rs})
    cat = {
        "className": "org.joget.apps.userview.lib.UserviewCategory",
        "properties": {
            "id":     CATEGORY_ID,
            "label":  CATEGORY_LABEL,
            # Category-level permission: union of roles (admin's super-user
            # status passes through; non-admin users in any of these roles
            # get the menus for data API gating).
            "permission": {
                "className": PERM_CLASS,
                "properties": {"groupId": ",".join(union_roles)},
            },
        },
        "menus": [make_menu(d, r) for d, r in sorted(DATALIST_ROLES.items())],
    }
    uv["categories"].append(cat)

    print(f"\nWill add {len(DATALIST_ROLES)} hidden datalist menus under "
          f"category '{CATEGORY_ID}'.")
    for d, r in sorted(DATALIST_ROLES.items()):
        print(f"  {d:35s} → {','.join(r)}")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    print("\nApplying...")
    status, body = push_userview(uv)
    print(f"HTTP {status}: {body[:200]}")
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
