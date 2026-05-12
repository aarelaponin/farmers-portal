#!/usr/bin/env python3
"""
Refresh list_spNotifTemplate to surface the new operator-authoring columns
(recipientResolver, isActive, priority) and trim the noise columns. Pushes
via form-creator-api /datalists (HARD RULE compliant).
"""
import argparse, json, sys, urllib.error, urllib.request

JOGET   = "http://20.87.213.78:8080/jw"
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = os.environ.get("JOGET_API_KEY", "")
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  API_ID,
    "api_key": API_KEY,
}

DATALIST = {
    "id":   "list_spNotifTemplate",
    "name": "List: Notification Templates",
    "properties": {
        "name": "List: Notification Templates",
        "id":   "list_spNotifTemplate",
        "useSession":           "false",
        "showPageSizeSelector": "true",
        "pageSize":             0,
        "pageSizeSelectorOptions": "10,20,50",
        "orderBy":              "templateCode",
        "order":                "ASC",
        "buttonPosition":       "bothLeft",
        "checkboxPosition":     "left",
        "rowActions":           [],
        "actions":              [],
        "binder": {
            "className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
            "properties": {"formDefId": "spNotifTemplate"}
        },
        "columns": [
            {"id":"column_0", "name":"templateCode",      "label":"Code",
             "sortable":"true",  "width":"160"},
            {"id":"column_1", "name":"templateName",      "label":"Name",
             "sortable":"true",  "width":"240"},
            {"id":"column_2", "name":"triggerEvent",      "label":"Event",
             "sortable":"true",  "width":"180"},
            {"id":"column_3", "name":"recipientResolver", "label":"Recipient Resolver",
             "sortable":"true",  "width":"180"},
            {"id":"column_4", "name":"category",          "label":"Category",
             "sortable":"true",  "width":"100"},
            {"id":"column_5", "name":"priority",          "label":"Priority",
             "sortable":"true",  "width":"80"},
            {"id":"column_6", "name":"emailEnabled",      "label":"Email",
             "sortable":"true",  "width":"60"},
            {"id":"column_7", "name":"smsEnabled",        "label":"SMS",
             "sortable":"true",  "width":"60"},
            {"id":"column_8", "name":"isActive",          "label":"Active",
             "sortable":"true",  "width":"60"},
        ],
        "filters": [
            {"name":"triggerEvent",      "label":"Event",
             "type":{"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType",
                     "properties":{}}},
            {"name":"recipientResolver", "label":"Resolver",
             "type":{"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType",
                     "properties":{}}},
            {"name":"isActive",          "label":"Active",
             "type":{"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType",
                     "properties":{}}},
        ]
    }
}


def push():
    payload = {
        "appId":        "farmersPortal",
        "datalistId":   DATALIST["id"],
        "datalistName": DATALIST["name"],
        "json":         json.dumps(DATALIST),
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


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    print("Datalist:", DATALIST["id"], "→", DATALIST["name"])
    print("Columns:", ", ".join(c["name"] for c in DATALIST["properties"]["columns"]))

    if args.dry_run:
        print("Dry-run — not pushing.")
        return 0

    status, body = push()
    print(f"HTTP {status}")
    print(body[:400])
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
