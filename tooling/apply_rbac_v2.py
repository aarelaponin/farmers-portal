#!/usr/bin/env python3
"""
Apply role-based access control to the farmersPortal userview — v2.

Why v2 (this version): the original apply_rbac.py had two bugs found by
source-code study of jw-community/wflow-core/src/main/java/org/joget/apps/userview/:

  1) Wrong JSON property name + separator. Joget's GroupPermission plugin
     reads `properties.permission.properties.allowedGroupIds`, semicolon-
     separated (jw-community/.../userview/lib/GroupPermission.java line 39
     and the plugin's properties JSON). The original script wrote `groupId`
     comma-separated. Plugin tokenises empty string -> 0 groups -> isAuthorize
     returns false. Silently never authorizes anyone.
  2) Wrong scope. UserviewService.createUserview() (lines 305-318 evaluates
     CATEGORY-level permission via UserviewUtil.getPermisionResult(); lines
     349-411 only check `permissionDeny`/`permissionHidden` flags on menus —
     the menu's own `permission` block is never run at nav-build time. Stock
     Joget DX 8.1: category-level GroupPermission hides categories; menu-
     level only blocks page-access on click. So menu-level permissions could
     never hide menus from the nav.

Fix:
  - Set GroupPermission on every CATEGORY (primary visibility control).
  - ALSO fix per-menu permissions to use the right key + separator (so click-
    time page-access enforcement works for users who navigate by URL).
  - Drop the dead `_data_access` category that earlier failed attempts left
    behind — does nothing useful and clutters the JSON.

HARD RULE compliant: never raw SQL on app_userview. Read via SELECT, mutate
in memory, push via form-creator-api.

Usage:
    tooling/apply_rbac_v2.py --dry-run     # show changes, push nothing
    tooling/apply_rbac_v2.py --apply       # actually push the patched userview
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

# ---- Connection / endpoint config ---------------------------------------
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

# ---- Role taxonomy -------------------------------------------------------
R_FIELD     = "role_field_officer"
R_SUP       = "role_district_supervisor"
R_FIN       = "role_finance_officer"
R_ANALYST   = "role_analyst"
R_ADMIN     = "role_sysadmin"

# ---- Category-label -> roles mapping ------------------------------------
# Match against the visible label text (after icon HTML stripped, case-
# insensitive). Source: docs/architecture/rbac_taxonomy.md.
CATEGORY_ROLES = {
    "dashboard":          [R_ADMIN, R_SUP, R_FIN],
    "registration forms": [R_ADMIN, R_SUP, R_FIELD],
    "moa office":         [R_ADMIN, R_SUP],
    "budget":             [R_ADMIN, R_FIN],
    "inputs management":  [R_ADMIN, R_SUP, R_FIELD],
    "reports":            [R_ADMIN, R_SUP, R_FIN],
    "mm - configuration": [R_ADMIN, R_ANALYST],
    "admin":              [R_ADMIN],
    "master data":        [R_ADMIN, R_ANALYST],
}

# ---- Per-menu role mapping (for click-time enforcement) -----------------
# Same as v1 but with corrected JSON shape downstream.
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
DEFAULT_ROLES = [R_ADMIN]


def roles_for_menu_label(label):
    if label in LABEL_TO_ROLES:
        return LABEL_TO_ROLES[label]
    if re.match(r'^MD\.\d', label):
        return [R_ADMIN, R_ANALYST]
    return DEFAULT_ROLES


# ---- Permission block builder -------------------------------------------
def perm_block(roles):
    """Build a GroupPermission JSON shape that the plugin actually reads.

    Per GroupPermission.isAuthorize() (line 39 of source) the property name
    is `allowedGroupIds` and the separator is `;`. Earlier scripts used
    `groupId` and `,` — the plugin reads empty string and rejects everyone.
    """
    return {
        "className": PERM_CLASS,
        "properties": {
            "allowedGroupIds": ";".join(roles),
        },
    }


# ---- Label normalisation -------------------------------------------------
TAG_RE = re.compile(r'<[^>]+>')


def normalize_label(raw):
    """Strip icon HTML tags and surrounding whitespace; lowercase."""
    if raw is None:
        return ""
    return TAG_RE.sub("", raw).strip().lower()


# ---- HTTP helper --------------------------------------------------------
def push_userview(uv):
    payload = {
        "appId":          "farmersPortal",
        "userviewId":     uv["properties"]["id"],
        "userviewName":   uv["properties"].get("name", uv["properties"]["id"]),
        "json":           json.dumps(uv),
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


# ---- Main ---------------------------------------------------------------
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

    # Backup local copy before mutating
    if args.apply:
        ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
        backup = f"_backups/v.preRBACv2.{ts}.pretty.json"
        os.makedirs(os.path.dirname(backup), exist_ok=True)
        with open(backup, "w") as f:
            json.dump(uv, f, indent=2)
        print(f"[backup] {backup}")

    # ---- Fix 1: Drop the dead _data_access category ---------------------
    pre_count = len(uv["categories"])
    uv["categories"] = [
        c for c in uv["categories"]
        if c["properties"].get("id") != "_data_access"
    ]
    dropped = pre_count - len(uv["categories"])
    print(f"\nDropped {dropped} dead category (_data_access).")

    # ---- Fix 2: Set CATEGORY-level GroupPermission ----------------------
    print("\nCategory permissions:")
    cat_unmapped = []
    for cat in uv["categories"]:
        props = cat["properties"]
        label_norm = normalize_label(props.get("label", ""))
        # Try id-based match first for the simple cases
        roles = None
        cat_id = props.get("id", "")
        if cat_id == "dashboard":
            roles = CATEGORY_ROLES["dashboard"]
        elif cat_id == "category-budget":
            roles = CATEGORY_ROLES["budget"]
        elif cat_id == "category-inputs-management":
            roles = CATEGORY_ROLES["inputs management"]
        else:
            roles = CATEGORY_ROLES.get(label_norm)

        if roles is None:
            cat_unmapped.append((cat_id, label_norm))
            continue
        props["permission"] = perm_block(roles)
        print(f"  [{cat_id:55s}] {label_norm!r:25s} -> {','.join(roles)}")

    if cat_unmapped:
        print("\n[!!] Unmapped categories — please update CATEGORY_ROLES:")
        for cid, lbl in cat_unmapped:
            print(f"      id={cid!r}  label={lbl!r}")
        sys.exit(2)

    # ---- Fix 3: Re-fix per-menu permissions with right key + separator -
    print("\nMenu permissions (click-time enforcement):")
    menu_count = 0
    for cat in uv["categories"]:
        for menu in cat.get("menus", []):
            label = menu["properties"].get("label", "")
            roles = roles_for_menu_label(label)
            menu["properties"]["permission"] = perm_block(roles)
            menu_count += 1
    print(f"  Patched {menu_count} menus with allowedGroupIds + ; separator.")

    # ---- Push -----------------------------------------------------------
    if args.dry_run:
        print("\nDry-run — no changes pushed.")
        return 0

    print("\nApplying via form-creator-api...")
    status, body = push_userview(uv)
    print(f"HTTP {status}: {body[:300]}")
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
