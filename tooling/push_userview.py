#!/usr/bin/env python3
"""
Push (upsert) userview definitions from `app/userviews/*.json` into the dev
Joget instance via form-creator-api's `POST /formcreator/userviews` endpoint.

Mirrors the contract of `push_datalist.py`. See that module for the design
notes; this one differs only in directory + endpoint + payload field names.
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

ENDPOINT = JOGET_BASE_URL + "/api/formcreator/formcreator/userviews"

_HERE = os.path.dirname(os.path.abspath(__file__))
USERVIEW_DIR = os.path.normpath(os.path.join(_HERE, os.pardir, "app", "userviews"))


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

    # Userview JSON carries id + name under setting.properties (not at top level
    # like datalist JSON does). Be tolerant of both shapes.
    setting_props = (d.get("setting") or {}).get("properties") or {}
    userview_id   = d.get("id") or setting_props.get("userviewId")
    userview_name = d.get("name") or setting_props.get("userviewName") or userview_id
    if not userview_id:
        print(f"  {os.path.basename(path):40s}: missing userview id "
              f"(checked top-level 'id' and setting.properties.userviewId); skipping")
        return False

    payload = {
        "appId":        APP_ID,
        "userviewId":   userview_id,
        "userviewName": userview_name,
        "json":         json.dumps(d, separators=(",", ":")),
    }
    status, body = post_json(ENDPOINT, payload)
    if status != 200:
        print(f"  {userview_id:30s}: HTTP {status} — {body[:200]}")
        return False
    parsed = json.loads(body)
    inner = parsed.get("message")
    if isinstance(inner, str):
        try:
            inner = json.loads(inner)
        except json.JSONDecodeError:
            pass
    op = (inner or {}).get("operation", "?")
    print(f"  {userview_id:30s}: {op}")
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("targets", nargs="*",
                        help="Specific userview files (with or without .json). Empty = all.")
    args = parser.parse_args()

    if args.targets:
        paths = []
        for t in args.targets:
            if os.path.isabs(t) or os.path.exists(t):
                paths.append(t)
            else:
                stem = t if t.endswith(".json") else t + ".json"
                paths.append(os.path.join(USERVIEW_DIR, stem))
    else:
        if not os.path.isdir(USERVIEW_DIR):
            print(f"No app/userviews/ directory found at {USERVIEW_DIR}")
            return 1
        paths = sorted(
            os.path.join(USERVIEW_DIR, f)
            for f in os.listdir(USERVIEW_DIR)
            if f.endswith(".json")
        )

    if not paths:
        print("No userview files to push.")
        return 1

    print(f"=== Pushing {len(paths)} userview file(s) → {ENDPOINT} ===")
    ok = 0
    for p in paths:
        if not os.path.isfile(p):
            print(f"  {os.path.basename(p):30s}: file not found")
            continue
        if push_one(p):
            ok += 1
    print(f"=== {ok}/{len(paths)} succeeded ===")
    return 0 if ok == len(paths) else 1


if __name__ == "__main__":
    sys.exit(main())
