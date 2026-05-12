#!/usr/bin/env python3
"""
Two follow-up fixes after build-135:

1) list_subsidyApplicationOperator2025: filter out DRAFT and WITHDRAWN
   applications. Operators only see SUBMITTED / UNDER_REVIEW / APPROVED /
   REJECTED / PENDING_REVIEW. Empty c_lifecyclestate also passes (legacy
   pre-W3.1 rows). Patches the JdbcDataListBinder SQL.

2) list_subsidyApplication2025 (citizen-facing): add a 'State' column
   that surfaces c_lifecyclestate so the citizen sees DRAFT vs SUBMITTED
   etc. on their applications list.

HARD RULE compliant — pushes via form-creator-api.
"""
import argparse, copy, json, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}


def push(datalist):
    payload = {
        "appId":        "farmersPortal",
        "datalistId":   datalist["id"],
        "datalistName": datalist.get("name", datalist["id"]),
        "json":         json.dumps(datalist),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/datalists",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def fix_operator_inbox(conn):
    cur = conn.cursor()
    cur.execute("SELECT json FROM app_datalist WHERE appid='farmersPortal' AND id='list_subsidyApplicationOperator2025'")
    j = json.loads(cur.fetchone()[0])
    sql = j["binder"]["properties"]["sql"]

    # Insert the lifecycle filter clause before ORDER BY.
    NEW_WHERE = ("WHERE (a.c_lifecyclestate IS NULL "
                 "       OR a.c_lifecyclestate = '' "
                 "       OR a.c_lifecyclestate NOT IN ('draft','withdrawn'))")

    if "c_lifecyclestate" in sql.lower():
        print("operator inbox SQL already references c_lifecyclestate — skipping")
        return False

    # SQL has no WHERE clause currently — insert NEW_WHERE before ORDER BY.
    idx = sql.upper().rfind(" ORDER BY ")
    if idx < 0:
        print("ERROR: could not find ORDER BY in operator SQL", file=sys.stderr)
        return False
    new_sql = sql[:idx].rstrip() + " " + NEW_WHERE + " " + sql[idx:]
    out = copy.deepcopy(j)
    out["binder"]["properties"]["sql"] = new_sql
    print("Operator inbox SQL — patched to add lifecycle filter.")
    print(f"  Old SQL ended: ...{sql[max(0,idx-60):idx+50]}...")
    print(f"  New SQL ended: ...{new_sql[max(0,idx-60):idx+200]}...")

    return out


def fix_citizen_list(conn):
    cur = conn.cursor()
    cur.execute("SELECT json FROM app_datalist WHERE appid='farmersPortal' AND id='list_subsidyApplication2025'")
    j = json.loads(cur.fetchone()[0])
    cols = j.get("columns", [])
    if any(c.get("name") == "lifecycleState" for c in cols):
        print("citizen list already has lifecycleState column — skipping")
        return False

    new_col = {
        "id": "col_state",
        "name": "lifecycleState",
        "label": "State",
        "sortable": "true"
    }
    out = copy.deepcopy(j)
    out["columns"] = cols + [new_col]
    print("Citizen list — added 'State' (lifecycleState) column at end.")
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG)

    op   = fix_operator_inbox(conn)
    cit  = fix_citizen_list(conn)

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    if op:
        s, b = push(op)
        print(f"\noperator list push: HTTP {s}: {b[:200]}")
    if cit:
        s, b = push(cit)
        print(f"\ncitizen list push:  HTTP {s}: {b[:200]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
