#!/usr/bin/env python3
"""
Create the singleton notif_global_config form + its datalist + admin menu.

The form holds a SINGLE row (id='singleton') with three knobs the dispatcher
reads on every send:

  testModeActive          Y/N  — when Y, ALL email and SMS dispatches re-route
                                 to the test addresses below, regardless of
                                 the per-template recipientResolver
  testRecipientEmail      string — where all emails go in test mode
  testRecipientPhone      string — where all SMS go in test mode
  notes                   freeform — when the override was last toggled, why

Operator flips testModeActive=N from App Composer when MAFSN authorises
sending to real citizens. No rebuild, no restart.

Idempotent — form + datalist + menu created if missing, existing row left
alone.
"""
import argparse, datetime, json, os, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password="Joget@DB#2026!", port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}


FORM = {
    "className": "org.joget.apps.form.model.Form",
    "properties": {
        "id":        "notif_global_config",
        "name":      "Notification Global Config",
        "tableName": "notif_global_config",
        "loadBinder": {
            "className": "org.joget.apps.form.lib.WorkflowFormBinder",
            "properties": {}
        },
        "storeBinder": {
            "className": "org.joget.apps.form.lib.WorkflowFormBinder",
            "properties": {}
        }
    },
    "elements": [{
        "className": "org.joget.apps.form.model.Section",
        "properties": {"id": "section1", "label": "Test-Mode Override"},
        "elements": [{
            "className": "org.joget.apps.form.model.Column",
            "properties": {"width": "100%"},
            "elements": [
                {
                    "className": "org.joget.apps.form.lib.CustomHTML",
                    "properties": {
                        "id":    "warningSection",
                        "label": "",
                        "value": (
                            "<div style=\"background:#fff3e0;border-left:4px solid #ef6c00;"
                            "padding:14px 18px;margin:12px 0 20px 0;border-radius:4px;\">"
                            "<h4 style=\"margin:0 0 8px 0;color:#ef6c00;\">🚧 Test-Mode Override</h4>"
                            "<p style=\"margin:0;color:#5d4037;\">When <strong>Active = Y</strong>, every email "
                            "and SMS dispatch is re-routed to the test addresses below, regardless of the "
                            "per-template recipient resolver. The intended-recipient address is logged and "
                            "injected into the subject line ("
                            "<code>[TEST → original@email]</code>) so routing can be verified end-to-end "
                            "without delivering to real citizens.</p>"
                            "<p style=\"margin:8px 0 0 0;color:#5d4037;\"><strong>Flip Active = N "
                            "to go live</strong>. No plugin rebuild needed; the dispatcher reads this "
                            "row on every send.</p></div>"
                        )
                    }
                },
                {
                    "className": "org.joget.apps.form.lib.Radio",
                    "properties": {
                        "id":    "testModeActive",
                        "label": "Test Mode Active",
                        "options": [
                            {"value": "Y", "label": "Y — re-route every send to test addresses below"},
                            {"value": "N", "label": "N — go live; use per-template recipientResolver as authored"}
                        ],
                        "value": "Y",
                        "validator": {
                            "className": "org.joget.apps.form.lib.DefaultValidator",
                            "properties": {"type": "", "message": "", "mandatory": "true"}
                        }
                    }
                },
                {
                    "className": "org.joget.apps.form.lib.TextField",
                    "properties": {
                        "id":          "testRecipientEmail",
                        "label":       "Test Recipient Email (used when Test Mode Active = Y)",
                        "value":       "aarelaponin@gmail.com",
                        "placeholder": "where all emails go in test mode"
                    }
                },
                {
                    "className": "org.joget.apps.form.lib.TextField",
                    "properties": {
                        "id":          "testRecipientPhone",
                        "label":       "Test Recipient Phone (used when Test Mode Active = Y)",
                        "value":       "+26658515039",
                        "placeholder": "where all SMS go in test mode"
                    }
                },
                {
                    "className": "org.joget.apps.form.lib.TextArea",
                    "properties": {
                        "id":          "notes",
                        "label":       "Notes (audit trail — who toggled when, and why)",
                        "rows":        "4",
                        "value":       "Initialised on 2026-05-11. Toggle Active=N when MAFSN authorises production sends."
                    }
                }
            ]
        }]
    }]
}

DATALIST = {
    "id":   "list_notif_global_config",
    "name": "List: Notification Global Config",
    "properties": {
        "name": "List: Notification Global Config",
        "id":   "list_notif_global_config",
        "useSession":             "false",
        "showPageSizeSelector":   "true",
        "pageSize":               0,
        "pageSizeSelectorOptions": "10,20,50",
        "orderBy":                "datemodified",
        "order":                  "DESC",
        "buttonPosition":         "bothLeft",
        "checkboxPosition":       "left",
        "rowActions":             [],
        "actions":                [],
        "binder": {
            "className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
            "properties": {"formDefId": "notif_global_config"}
        },
        "columns": [
            {"id":"column_0", "name":"testModeActive",     "label":"Active",     "sortable":"true", "width":"100"},
            {"id":"column_1", "name":"testRecipientEmail", "label":"Test Email", "sortable":"true", "width":"260"},
            {"id":"column_2", "name":"testRecipientPhone", "label":"Test Phone", "sortable":"true", "width":"180"},
            {"id":"column_3", "name":"notes",              "label":"Notes",                          "width":"300"},
        ],
        "filters": []
    }
}

SINGLETON_ROW = {
    # businessKey is testRecipientEmail (any stable non-`id` column works — Joget's
    # seed endpoint chokes on businessKey='id' with UnknownPathException).
    "testModeActive":     "Y",
    "testRecipientEmail": "aarelaponin@gmail.com",
    "testRecipientPhone": "+26658515039",
    "notes":              "Initialised on 2026-05-11. Toggle Active=N when MAFSN authorises production sends to real citizens."
}


def push_form():
    payload = {
        "targetAppId":    "farmersPortal",
        "formId":         FORM["properties"]["id"],
        "formName":       FORM["properties"]["name"],
        "tableName":      FORM["properties"]["tableName"],
        "formDefinition": json.dumps(FORM),
    }
    return _post("/api/formcreator/formcreator/forms", payload)


def push_datalist():
    payload = {
        "appId":        "farmersPortal",
        "datalistId":   DATALIST["id"],
        "datalistName": DATALIST["name"],
        "json":         json.dumps(DATALIST),
    }
    return _post("/api/formcreator/formcreator/datalists", payload)


def seed_singleton():
    payload = {
        "appId": "farmersPortal",
        "fixtures": [
            {
                "formId":      "notif_global_config",
                "businessKey": "testRecipientEmail",   # any stable non-`id` column
                "rows":        [SINGLETON_ROW]
            }
        ]
    }
    return _post("/api/formcreator/formcreator/seed", payload)


def add_menu():
    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])
    admin_cat = None
    for cat in uv["categories"]:
        if "admin" in cat["properties"].get("label", "").lower():
            admin_cat = cat
            break
    if admin_cat is None:
        return ("nocat", "Admin category not found")
    for m in admin_cat.get("menus", []):
        if m["properties"].get("datalistId") == "list_notif_global_config":
            return (200, "menu already present")

    menu = {
        "className": "org.joget.plugin.enterprise.CrudMenu",
        "properties": {
            "id":            "menu-notif-global-config",
            "customId":      "notif_global_config",
            "label":         "Notification Test-Mode Override",
            "addFormId":     "notif_global_config",
            "editFormId":    "notif_global_config",
            "datalistId":    "list_notif_global_config",
            "selectionType": "single",
            "showRowActions": "Yes",
            "icon":           "fa fa-paper-plane",
            "iconIncluded":   True,
            "permission": {
                "className": "org.joget.apps.userview.lib.GroupPermission",
                "properties": {"allowedGroupIds": "role_sysadmin"}
            }
        }
    }
    admin_cat.setdefault("menus", []).append(menu)
    ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    backup = f"_backups/v.preNotifGlobalConfigMenu.{ts}.json"
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    with open(backup, "w") as f: json.dump(uv, f, indent=2)

    payload = {
        "appId": "farmersPortal",
        "userviewId":   uv["properties"]["id"],
        "userviewName": uv["properties"].get("name", uv["properties"]["id"]),
        "json":         json.dumps(uv),
    }
    return _post("/api/formcreator/formcreator/userviews", payload)


def _post(path, payload):
    req = urllib.request.Request(JOGET + path,
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return (resp.status, resp.read().decode())
    except urllib.error.HTTPError as e:
        return (e.code, e.read().decode())


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    print("Form notif_global_config (singleton)")
    print("Datalist list_notif_global_config")
    print("Singleton row:", SINGLETON_ROW)
    print("Admin menu: Notification Test-Mode Override")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    print("\n[1/4] Form...");      s, b = push_form();     print(f"  HTTP {s}: {b[:200]}")
    if s != 200: return 1
    print("\n[2/4] Datalist...");  s, b = push_datalist(); print(f"  HTTP {s}: {b[:200]}")
    if s != 200: return 1
    print("\n[3/4] Seed singleton row..."); s, b = seed_singleton(); print(f"  HTTP {s}: {b[:200]}")
    if s != 200: return 1
    print("\n[4/4] Userview menu...");      s, b = add_menu();      print(f"  HTTP {s}: {b[:200]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
