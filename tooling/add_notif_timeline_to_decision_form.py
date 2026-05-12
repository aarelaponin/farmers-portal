#!/usr/bin/env python3
"""
W2.8 — wire EmbeddedDatalist `list_notif_for_application` into the
spApplicationDecision form as a new Section "Notifications sent for
this application", below the existing operator-decision section.

The EmbeddedDatalist passes the parent form's `parent_id` field
(which holds the application's UUID) as request param `app_id`.
The datalist's JdbcDataListBinder derives the application_code
("AP-" + first 8 chars uppercase) and filters notification_queue
on c_correlationId.

Idempotent: skips if section already present.
HARD RULE compliant: pushes via form-creator-api.
"""
import argparse, copy, json, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": os.environ.get("JOGET_API_KEY", ""),
}

NOTIF_SECTION = {
    "className": "org.joget.apps.form.model.Section",
    "properties": {
        "id":    "section_notifications",
        "label": "Notifications sent for this application",
        "description": "Every email/SMS dispatched for this application. Filter by status / channel on the main Notification Queue if you need cross-application views."
    },
    "elements": [
        {
            "className": "org.joget.apps.form.model.Column",
            "properties": {"width": "100"},
            "elements": [
                {
                    "className": "org.joget.marketplace.EmbeddedDatalist",
                    "properties": {
                        "id":             "notif_timeline",
                        "label":          "",
                        "datalistId":     "list_notif_for_application",
                        "pageSize":       "20",
                        "showPagination": "true",
                        "showFilter":     "",
                        "showExport":     "",
                        "emptyMessage":   "No notifications dispatched for this application yet.",
                        "height":         "320px",
                        "filterParams": [
                            {
                                "paramName":    "app_id",
                                "fieldId":      "parent_id",
                                "defaultValue": ""
                            }
                        ]
                    }
                }
            ]
        }
    ]
}


def find_sections(form_json):
    return form_json.get("elements", [])


def already_present(form_json):
    for s in find_sections(form_json):
        if s.get("properties", {}).get("id") == "section_notifications":
            return True
    return False


def push_form(form_json):
    payload = {
        "targetAppId":    "farmersPortal",
        "formId":         form_json["properties"]["id"],
        "formName":       form_json["properties"].get("name",
                            form_json["properties"]["id"]),
        "tableName":      form_json["properties"].get("tableName",
                            form_json["properties"]["id"]),
        "formDefinition": json.dumps(form_json),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/forms",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_form WHERE formid='spApplicationDecision' AND appid='farmersPortal'")
    form = json.loads(cur.fetchone()[0])

    if already_present(form):
        print("section_notifications already present — nothing to do.")
        return 0

    out = copy.deepcopy(form)
    out.setdefault("elements", []).append(NOTIF_SECTION)

    print("Sections after change:")
    for s in out["elements"]:
        p = s.get("properties", {})
        print(f"  - id={p.get('id'):30s} label={p.get('label','')[:60]}")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    s, b = push_form(out)
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
