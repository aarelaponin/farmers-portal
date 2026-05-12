#!/usr/bin/env python3
"""
Apply role-based access control to every userview menu.

Per `docs/architecture/rbac_taxonomy.md`, every menu gets a `permission` block of class
`org.joget.apps.userview.lib.GroupPermission` with `groupId` = comma-separated
list of role group ids. Joget shows the menu if the user is in ANY of those
groups (disjunctive).

Usage:
    tooling/apply_rbac.py --dry-run     # show what would change, push nothing
    tooling/apply_rbac.py --apply       # actually push the patched userview

Per CLAUDE.md HARD RULE this never touches `app_userview` directly. It pulls
the live userview, mutates in memory, and pushes via form-creator-api's
`POST /formcreator/userviews` endpoint, which goes through the Joget DAO
and refreshes the cache atomically.
"""

import argparse
import json
import os
import re
import sys
import urllib.request
import urllib.error

import psycopg2

# ---- Connection / endpoint config (matches the rest of _tooling) ---------
PG = dict(
    host="joget-pgsql-sa.postgres.database.azure.com",
    dbname="jogetdb", user="jogetadmin",
    password="Joget@DB#2026!", port=5432, sslmode="require",
)
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}

PERM_CLASS = "org.joget.apps.userview.lib.GroupPermission"

# ---- Role taxonomy → menu-label mapping ---------------------------------
# Source of truth: docs/architecture/rbac_taxonomy.md.
# Roles encoded as group-id strings. Joget will accept comma-separated lists.

R_FIELD     = "role_field_officer"
R_SUP       = "role_district_supervisor"
R_FIN       = "role_finance_officer"
R_ANALYST   = "role_analyst"
R_ADMIN     = "role_sysadmin"

# Map by exact menu label (case-sensitive). Unmatched labels fall through
# to DEFAULT_ROLES below — currently sysadmin only, so unknown menus default
# to sysadmin-locked rather than wide-open.
DEFAULT_ROLES = [R_ADMIN]

LABEL_TO_ROLES = {
    # Dashboard
    "Executive Overview":                 [R_ADMIN, R_SUP, R_FIN],
    "District Map (compact)":             [R_ADMIN, R_SUP, R_FIN],
    "District Map (geographic)":          [R_ADMIN, R_SUP, R_FIN],
    # Registration Forms
    "01 - Farmer Registration Form":      [R_ADMIN, R_SUP, R_FIELD],
    "02 - Parcel Registration":           [R_ADMIN, R_SUP, R_FIELD],
    "2025 Subsidy Application":           [R_ADMIN, R_SUP, R_FIELD],
    # MOA Office
    "Manage Support Program":             [R_ADMIN, R_SUP, R_FIN],
    "2025 Subsidy Application — Operator Review": [R_ADMIN, R_SUP],
    "RegBB Evaluation Audit":             [R_ADMIN, R_SUP],
    # Budget
    "Budget — Envelope state":            [R_ADMIN, R_FIN],
    "Budget — Per-source breakdown":      [R_ADMIN, R_FIN],
    "Budget — Recent ledger entries":     [R_ADMIN, R_FIN],
    "Budget — Pending pipeline":          [R_ADMIN, R_FIN],
    "Budget — Variance report":           [R_ADMIN, R_FIN],
    "Budget — Alerts":                    [R_ADMIN, R_FIN],
    "Budget — Roll-forward":              [R_ADMIN, R_FIN],
    "Budget — General Ledger":            [R_ADMIN, R_FIN],
    "Budget — Donor disbursement":        [R_ADMIN, R_FIN],
    "Budget — Manual adjustments":        [R_ADMIN, R_FIN],
    # Inputs Management
    "IM - Supplier":                       [R_ADMIN, R_SUP],
    "IM - Inventory":                      [R_ADMIN, R_SUP],
    "IM - Stock Transactions":             [R_ADMIN, R_SUP],
    "IM - Allocation Plan":                [R_ADMIN, R_SUP, R_FIN],
    "IM - Vouchers":                       [R_ADMIN, R_SUP],
    "IM - Redeem Voucher":                 [R_ADMIN, R_SUP, R_FIELD],
    "IM - Print Voucher Slip":             [R_ADMIN, R_SUP, R_FIELD],
    "IM - Redemption Audit Log (admin)":   [R_ADMIN],
    "IM - Distribution Receipts":          [R_ADMIN, R_SUP, R_FIELD],
    # Reports
    "Overview":                            [R_ADMIN, R_SUP, R_FIN],
    "List of Farmers":                     [R_ADMIN, R_SUP],
    "IM — Dashboard KPIs":                 [R_ADMIN, R_SUP, R_FIN],
    "IM — Voucher utilisation by programme": [R_ADMIN, R_SUP, R_FIN],
    "IM — Consumption by centre (30d)":    [R_ADMIN, R_SUP],
    "IM — Supplier performance":           [R_ADMIN, R_SUP, R_FIN],
    "IM — End-of-campaign reconciliation": [R_ADMIN, R_SUP, R_FIN],
    "IM — Funding by donor":               [R_ADMIN, R_FIN],
    "IM — Programme funding breakdown (per donor)": [R_ADMIN, R_FIN],
    "Report — Approval rates":             [R_ADMIN, R_SUP, R_FIN],
    "Report — Voucher velocity":           [R_ADMIN, R_SUP, R_FIN],
    "Report — Budget envelope":            [R_ADMIN, R_FIN],
    # MM-Config
    "MM - Institution":                    [R_ADMIN, R_ANALYST],
    "MM - Service":                        [R_ADMIN, R_ANALYST],
    "MM - Registration":                   [R_ADMIN, R_ANALYST],
    "MM - Screen":                         [R_ADMIN, R_ANALYST],
    "MM - Catalog":                        [R_ADMIN, R_ANALYST],
    "MM - Rules":                          [R_ADMIN, R_ANALYST],
    "MM - Action":                         [R_ADMIN, R_ANALYST],
    "MM - Required Document":              [R_ADMIN, R_ANALYST],
    "MM - Fee":                            [R_ADMIN, R_ANALYST],
    "MM - Benefit":                        [R_ADMIN, R_ANALYST],
    "MM - Role":                           [R_ADMIN],
    "MM - Role Screen":                    [R_ADMIN],
    "MM - Field":                          [R_ADMIN, R_ANALYST],
    "Programme Funding (donor share)":     [R_ADMIN, R_FIN],
    # Admin
    "Manage API Key":                      [R_ADMIN],
    "Form Creator":                        [R_ADMIN, R_ANALYST],
    "Identity Resolvers":                  [R_ADMIN],
    "Resolver Field Maps":                 [R_ADMIN],
    "Audit Trail":                         [R_ADMIN],
}

# All MD.* labels follow the same pattern → analyst + admin
def roles_for_label(label):
    if label in LABEL_TO_ROLES:
        return LABEL_TO_ROLES[label]
    if re.match(r'^MD\.\d', label):
        return [R_ADMIN, R_ANALYST]
    return DEFAULT_ROLES

# ---- HTTP helper --------------------------------------------------------
def push_userview(uv):
    raw = json.dumps(uv)
    payload = {
        "appId":          "farmersPortal",
        "userviewId":     uv["properties"]["id"],
        "userviewName":   uv["properties"].get("name", uv["properties"]["id"]),
        "json":           raw,
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

# ---- Main --------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true", help="show changes, push nothing")
    g.add_argument("--apply",   action="store_true", help="push the patched userview")
    ap.add_argument("--backup", default="_backups/v.preRBAC.{ts}.pretty.json",
                    help="local backup path; {ts} expands to timestamp")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG)
    cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    # Backup local copy
    if args.apply:
        import datetime, os
        ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
        backup_path = args.backup.replace("{ts}", ts)
        os.makedirs(os.path.dirname(backup_path), exist_ok=True)
        with open(backup_path, "w") as f:
            json.dump(uv, f, indent=2)
        print(f"[backup] {backup_path}")

    # Walk the menus and patch each permission block
    patched = []
    defaulted = []
    for cat in uv.get("categories", []):
        for menu in cat.get("menus", []):
            label = menu["properties"].get("label", "")
            roles = roles_for_label(label)
            menu["properties"]["permission"] = {
                "className": PERM_CLASS,
                "properties": {
                    "groupId": ",".join(roles),
                },
            }
            if label not in LABEL_TO_ROLES and not re.match(r'^MD\.\d', label):
                defaulted.append((label, roles))
            else:
                patched.append((label, roles))

    print(f"\nPatched {len(patched)} menus (matched in taxonomy).")
    print(f"Defaulted {len(defaulted)} menus to {DEFAULT_ROLES} (no taxonomy entry).")

    if defaulted:
        print("\n=== Menus that defaulted (review before --apply) ===")
        for lbl, rs in defaulted:
            print(f"  {lbl!r:55s} → {rs}")

    if args.dry_run:
        print("\nDry-run — no changes pushed.")
        return 0

    print("\nApplying...")
    status, body = push_userview(uv)
    print(f"HTTP {status}: {body[:200]}")
    return 0 if status == 200 else 1

if __name__ == "__main__":
    sys.exit(main())
