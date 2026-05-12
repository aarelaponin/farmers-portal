#!/usr/bin/env python3
"""
Push (upsert) one or more datalist definitions from `app/datalists/*.json` into
the dev Joget instance via form-creator-api's `POST /formcreator/datalists`
endpoint. The CI / replay path — replaces App Composer paste.

Usage:
    python3 tooling/push_datalist.py                     # push every app/datalists/*.json
    python3 tooling/push_datalist.py list_mm_determinant # push named files (with or without .json)
    python3 tooling/push_datalist.py *.json              # shell glob is fine

Requires form-creator-api build-009 or later (the version that adds the
/datalists endpoint). For older bundles, falls back to a clean error.

Per CLAUDE.md HARD RULE this never touches `app_datalist` directly — every
write goes through Joget's `DatalistDefinitionDao`. Read-only verification at
the end is permitted.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


JOGET_BASE_URL = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_ID   = os.environ.get("JOGET_API_ID",   "API-e7878006-c15a-425e-9c36-bebc7c4d085c")
JOGET_API_KEY  = os.environ.get("JOGET_API_KEY",  os.environ.get("JOGET_API_KEY", ""))
APP_ID         = os.environ.get("JOGET_APP_ID",   "farmersPortal")

ENDPOINT = JOGET_BASE_URL + "/api/formcreator/formcreator/datalists"

_HERE = os.path.dirname(os.path.abspath(__file__))
DATALIST_DIR = os.path.normpath(os.path.join(_HERE, os.pardir, "app", "datalists"))


def post_json(url, payload, timeout=30):
    req = urllib.request.Request(
        url, method="POST",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "api_id":  JOGET_API_ID,
            "api_key": JOGET_API_KEY,
        })
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def push_one(path):
    with open(path) as f:
        raw = f.read()
    try:
        d = json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"  {os.path.basename(path):40s}: BAD JSON — {e}")
        return False

    datalist_id   = d.get("id")
    datalist_name = d.get("name", datalist_id)
    if not datalist_id:
        print(f"  {os.path.basename(path):40s}: missing 'id' in JSON; skipping")
        return False

    # Re-serialise compactly so the wire payload is deterministic.
    payload = {
        "appId":        APP_ID,
        "datalistId":   datalist_id,
        "datalistName": datalist_name,
        "json":         json.dumps(d, separators=(",", ":")),
    }
    status, body = post_json(ENDPOINT, payload)
    if status != 200:
        print(f"  {datalist_id:40s}: HTTP {status} — {body[:200]}")
        return False
    parsed = json.loads(body)
    inner = parsed.get("message")
    if isinstance(inner, str):
        try:
            inner = json.loads(inner)
        except json.JSONDecodeError:
            pass
    op = (inner or {}).get("operation", "?")
    print(f"  {datalist_id:40s}: {op}")
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("targets", nargs="*",
                        help="Specific datalist files (with or without .json). Empty = all.")
    args = parser.parse_args()

    if args.targets:
        paths = []
        for t in args.targets:
            if os.path.isabs(t) or os.path.exists(t):
                paths.append(t)
            else:
                # Allow `list_mm_determinant` or `list_mm_determinant.json` shorthand.
                stem = t if t.endswith(".json") else t + ".json"
                paths.append(os.path.join(DATALIST_DIR, stem))
    else:
        paths = sorted(
            os.path.join(DATALIST_DIR, f)
            for f in os.listdir(DATALIST_DIR)
            if f.endswith(".json")
        )

    if not paths:
        print("No datalist files to push.")
        return 1

    print(f"=== Pushing {len(paths)} datalist file(s) → {ENDPOINT} ===")
    ok = 0
    for p in paths:
        if not os.path.isfile(p):
            print(f"  {os.path.basename(p):40s}: file not found")
            continue
        if push_one(p):
            ok += 1
    print(f"=== {ok}/{len(paths)} succeeded ===")
    return 0 if ok == len(paths) else 1


if __name__ == "__main__":
    sys.exit(main())
