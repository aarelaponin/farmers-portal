#!/usr/bin/env python3
"""
Create the notification_queue form + companion datalist.

The queue is written to by EmailDispatcher when a template's sendImmediately
is set to SCHEDULED (with delayMinutes), and read by the NotificationQueueWorker
daemon every 60 s. Pending rows whose scheduledFor <= now are dispatched in
priority/scheduledFor order.

Columns:
  eventCode      — trigger event (matches spNotifTemplate.triggerEvent)
  toAddress      — resolved recipient (email or phone)
  toName         — display name
  channel        — EMAIL or SMS
  locale         — EN or ST
  varsJson       — JSON-encoded substitution map (saved at enqueue time)
  scheduledFor   — when this should fire
  priority       — NORMAL or HIGH
  status         — PENDING, SENT, FAILED, DEAD
  attempts       — retry counter
  lastError      — most recent error message (truncated)
  sentAt         — wall-clock time when dispatch succeeded

HARD RULE compliant: pushed via form-creator-api.
"""
import argparse, json, sys, urllib.error, urllib.request

JOGET   = "http://20.87.213.78:8080/jw"
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = "a5af1181f77b4a62b481725b6410e965"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  API_ID,
    "api_key": API_KEY,
}


def text(fid, label, **kw):
    p = {"id": fid, "label": label, "value": "", **kw}
    return {"className": "org.joget.apps.form.lib.TextField", "properties": p}

def textarea(fid, label, **kw):
    p = {"id": fid, "label": label, "value": "", "rows": "4", **kw}
    return {"className": "org.joget.apps.form.lib.TextArea", "properties": p}

def selectbox(fid, label, options, default="", **kw):
    return {
        "className": "org.joget.apps.form.lib.SelectBox",
        "properties": {
            "id": fid, "label": label,
            "options": [{"value": v, "label": l} for v, l in options],
            "value": default, "addEmptyOption": "false", **kw
        }
    }

def hidden(fid, value=""):
    return {"className": "org.joget.apps.form.lib.HiddenField",
            "properties": {"id": fid, "value": value}}

FORM = {
    "className": "org.joget.apps.form.model.Form",
    "properties": {
        "id":        "notification_queue",
        "name":      "Notification Queue",
        "tableName": "notification_queue",
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
        "properties": {"id": "section1", "label": "Queued Notification"},
        "elements": [{
            "className": "org.joget.apps.form.model.Column",
            "properties": {"width": "100%"},
            "elements": [
                text("eventCode",   "Event Code"),
                text("toAddress",   "Recipient Address"),
                text("toName",      "Recipient Display Name"),
                selectbox("channel",  "Channel",
                    [("EMAIL","EMAIL"),("SMS","SMS")], default="EMAIL"),
                selectbox("locale",   "Locale",
                    [("EN","EN"),("ST","ST")], default="EN"),
                textarea("varsJson",  "Variables (JSON)",
                    placeholder='{"first_name":"Lerato","application_code":"AP-ABCD1234"}'),
                text("scheduledFor","Scheduled For (ISO-8601 UTC)"),
                selectbox("priority", "Priority",
                    [("NORMAL","NORMAL"),("HIGH","HIGH")], default="NORMAL"),
                selectbox("status",   "Status",
                    [("PENDING","PENDING"),("SENT","SENT"),
                     ("FAILED","FAILED"),("DEAD","DEAD (max retries)")],
                    default="PENDING"),
                text("attempts",    "Attempts", value="0"),
                textarea("lastError","Last Error Message", rows="3"),
                text("sentAt",      "Sent At (ISO-8601 UTC)"),
            ]
        }]
    }]
}

DATALIST = {
    "id":   "list_notification_queue",
    "name": "List: Notification Queue",
    "properties": {
        "name": "List: Notification Queue",
        "id":   "list_notification_queue",
        "useSession":           "false",
        "showPageSizeSelector": "true",
        "pageSize":             0,
        "pageSizeSelectorOptions": "10,20,50,100",
        "orderBy":              "scheduledFor",
        "order":                "DESC",
        "buttonPosition":       "bothLeft",
        "checkboxPosition":     "left",
        "rowActions":           [],
        "actions":              [],
        "binder": {
            "className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
            "properties": {"formDefId": "notification_queue"}
        },
        "columns": [
            {"id":"column_0","name":"eventCode",     "label":"Event",        "sortable":"true","width":"180"},
            {"id":"column_1","name":"channel",       "label":"Channel",      "sortable":"true","width":"80"},
            {"id":"column_2","name":"toAddress",     "label":"To",           "sortable":"true","width":"220"},
            {"id":"column_3","name":"scheduledFor",  "label":"Scheduled For","sortable":"true","width":"180"},
            {"id":"column_4","name":"priority",      "label":"Pri",          "sortable":"true","width":"60"},
            {"id":"column_5","name":"status",        "label":"Status",       "sortable":"true","width":"100"},
            {"id":"column_6","name":"attempts",      "label":"Tries",        "sortable":"true","width":"60"},
            {"id":"column_7","name":"sentAt",        "label":"Sent At",      "sortable":"true","width":"180"},
            {"id":"column_8","name":"lastError",     "label":"Last Error",                       "width":"260"},
        ],
        "filters": [
            {"name":"eventCode","label":"Event",
             "type":{"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
            {"name":"status","label":"Status",
             "type":{"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
            {"name":"channel","label":"Channel",
             "type":{"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
        ]
    }
}


def push_form():
    payload = {
        "targetAppId":    "farmersPortal",
        "formId":         FORM["properties"]["id"],
        "formName":       FORM["properties"]["name"],
        "tableName":      FORM["properties"]["tableName"],
        "formDefinition": json.dumps(FORM),
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


def push_datalist():
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

    print("Form notification_queue with fields:")
    for col in FORM["elements"][0]["elements"][0]["elements"]:
        print(f"   {col['properties']['id']:<14} {col['className'].rsplit('.',1)[-1]:<12} {col['properties'].get('label','')}")
    print()
    print("Datalist list_notification_queue with columns:",
          ", ".join(c["name"] for c in DATALIST["properties"]["columns"]))

    if args.dry_run:
        return 0

    print("\nApplying form...")
    s, b = push_form();   print(f"  HTTP {s}: {b[:200]}")
    if s != 200: return 1
    print("\nApplying datalist...")
    s, b = push_datalist(); print(f"  HTTP {s}: {b[:200]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
