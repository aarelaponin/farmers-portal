#!/usr/bin/env python3
"""
W3.4 — Strip fixed-pixel column widths from operator/admin datalists.

Closes MA-008 from the W3.3 mobile audit report: 23 datalists have
cumulative fixed-pixel column widths between 800-1570 px, causing
horizontal scroll on a narrow-viewport (mobile or iPad portrait).

Removes the `width: NNNpx` property from each affected column. Joget's
default rendering takes over and sizes columns by content width on
desktop (no regression) while letting the table reflow on mobile.

Datalists whose column widths are NOT fixed-px (`%`-based, or absent)
are left alone. Columns with `alignment` or other non-width properties
are preserved.

HARD-RULE compliant: pulls via SELECT (read-only), pushes back through
form-creator-api's /datalists endpoint. No raw writes to app_datalist.

Usage:
    python3 tooling/strip_datalist_widths.py --dry-run   # see candidates
    python3 tooling/strip_datalist_widths.py --apply     # do it
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.request

import psycopg2

PG = dict(
    host="joget-pgsql-sa.postgres.database.azure.com",
    dbname="jogetdb",
    user="jogetadmin",
    password=os.environ.get("PGPASSWORD", ""),
    port=5432,
    sslmode="require",
)
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": os.environ.get("JOGET_API_KEY", ""),
}
APP_ID = "farmersPortal"


def is_fixed_px(width_value):
    """Return True if the width property is a fixed-pixel value like '120' or '120px'."""
    if not width_value:
        return False
    return bool(re.match(r"^\s*\d+\s*(px)?\s*$", str(width_value)))


def strip_widths(datalist):
    """Mutate the datalist dict in place; return (cols_stripped, total_cols)."""
    cols = datalist.get("columns", [])
    total = len(cols)
    stripped = 0
    for c in cols:
        w = c.get("width", "")
        if is_fixed_px(w):
            del c["width"]
            stripped += 1
    return stripped, total


def push_one(datalist):
    payload = {
        "appId":        APP_ID,
        "datalistId":   datalist["id"],
        "datalistName": datalist.get("name", datalist["id"]),
        "json":         json.dumps(datalist, separators=(",", ":")),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/datalists",
        data=json.dumps(payload).encode("utf-8"),
        headers=HEADERS,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    ap.add_argument("--threshold", type=int, default=800,
                    help="Only touch datalists whose cumulative fixed-px width is >= threshold (default 800).")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG)
    cur = conn.cursor()
    cur.execute("SELECT id, json FROM app_datalist WHERE appid=%s", (APP_ID,))
    rows = cur.fetchall()

    candidates = []
    for dl_id, raw in rows:
        try:
            dl = json.loads(raw)
        except Exception:
            continue
        cols = dl.get("columns", [])
        if not cols:
            continue
        total_px = 0
        n_fixed = 0
        for c in cols:
            w = c.get("width", "")
            if is_fixed_px(w):
                m = re.match(r"\d+", str(w))
                if m:
                    total_px += int(m.group(0))
                    n_fixed += 1
        if total_px >= args.threshold:
            candidates.append((dl_id, dl, total_px, n_fixed, len(cols)))

    candidates.sort(key=lambda x: -x[2])
    print(f"Found {len(candidates)} datalists with cumulative fixed-px width >= {args.threshold}px:")
    for dl_id, _dl, total, n_fixed, n_total in candidates:
        print(f"  {dl_id:<42} {total:>5}px ({n_fixed}/{n_total} columns)")

    if args.dry_run:
        print("\nDry-run — nothing pushed.")
        return 0

    print("\nStripping + pushing ...")
    pushed = 0
    failed = 0
    for dl_id, dl, _t, _nf, _nt in candidates:
        stripped, total = strip_widths(dl)
        if stripped == 0:
            continue
        status, body = push_one(dl)
        if status == 200:
            print(f"  {dl_id:<42} stripped {stripped}/{total} ✓")
            pushed += 1
        else:
            print(f"  {dl_id:<42} HTTP {status}: {body[:160]}")
            failed += 1
    print(f"\nDone — pushed {pushed}, failed {failed}.")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
