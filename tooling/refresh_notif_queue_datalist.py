#!/usr/bin/env python3
"""
Refresh list_notification_queue datalist to surface the new audit columns
(backend, intendedRecipient, actualRecipient, testMode, correlationId,
subject, status) and wire the Retry / Mark Dead-Letter row actions.

W2.6: the datalist becomes the operator's primary notification-transparency
surface. Every dispatch attempt — immediate or scheduled, EMAIL or SMS — is
visible here with its full state-machine state plus a backend column that
distinguishes real sends from LOG_ONLY simulated ones.

HARD RULE compliant: pushes via form-creator-api.
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

# Joget datalist JSON expects a FLAT structure — columns/filters/binder/actions
# at the top level, NOT nested inside a "properties" object. The previous
# version of this script wrapped everything in properties{}, which is why the
# List Builder canvas rendered empty (App Composer couldn't find the columns).
# Diagnostic compare against list_subsidyApplicationOperator2025 confirmed
# the flat shape is correct.
DATALIST = {
    "id":   "list_notification_queue",
    "name": "List: Notification Queue",
    "useSession":             "false",
    "showPageSizeSelector":   "true",
    "pageSize":               0,
    "pageSizeSelectorOptions": "20,50,100,200",
    "orderBy":                "dateCreated",
    "order":                  "DESC",
    "buttonPosition":         "bothLeft",
    "checkboxPosition":       "left",
    "binder": {
        "className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
        "properties": {"formDefId": "notification_queue"}
    },
    # Columns. DateFormatter applies to the system datecreated column.
    "columns": [
        # Joget exposes the system datecreated column under the
        # camelCase key `dateCreated` (see FormUtil.PROPERTY_DATE_CREATED
        # at wflow-core/.../FormUtil.java line 118). The lowercase
        # postgres column name 'datecreated' is NOT how the binder
        # surfaces it. Joget already pre-formats it as
        # 'yyyy-MM-dd HH:mm:ss' before handing it to the DateFormatter,
        # so rawFormat matches that exact pattern.
        {"id":"col_when",     "name":"dateCreated",       "label":"When",      "sortable":"true",
         "format": {
             "className": "org.joget.plugin.enterprise.DateFormatter",
             "properties": {
                 "format":   "yyyy-MM-dd HH:mm",
                 "rawFormat": "yyyy-MM-dd HH:mm:ss"
             }
         }},
        {"id":"col_event",    "name":"eventCode",         "label":"Event",     "sortable":"true"},
        {"id":"col_channel",  "name":"channel",           "label":"Channel",   "sortable":"true"},
        {"id":"col_status",   "name":"status",            "label":"Status",    "sortable":"true"},
        {"id":"col_actual",   "name":"actualRecipient",   "label":"Sent To",   "sortable":"true"},
        {"id":"col_intended", "name":"intendedRecipient", "label":"Intended",  "sortable":"true"},
        {"id":"col_intst",    "name":"intendedRecipientStatus", "label":"Reason", "sortable":"true"},
        {"id":"col_testmode", "name":"testMode",          "label":"Test?",     "sortable":"true"},
        {"id":"col_corrid",   "name":"correlationId",     "label":"Correlation","sortable":"true"},
        {"id":"col_backend",  "name":"backend",           "label":"Backend",   "sortable":"true"},
        {"id":"col_subject",  "name":"subject",           "label":"Subject"},
    ],
    # Filters — stock TextFieldDataListFilterType (the only filter installed
    # in this Joget). Operator types a partial value, the binder does a LIKE
    # match. CLAUDE.md gotcha: SelectBoxDataListFilterType is NOT installed.
    "filters": [
        {"name":"status",           "label":"Status",
         "type": {"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
        {"name":"channel",          "label":"Channel",
         "type": {"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
        {"name":"eventCode",        "label":"Event",
         "type": {"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
        {"name":"backend",          "label":"Backend",
         "type": {"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
        {"name":"testMode",         "label":"Test? (Y/N)",
         "type": {"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
        {"name":"correlationId",    "label":"Correlation ID",
         "type": {"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
        {"name":"actualRecipient",  "label":"Sent To contains",
         "type": {"className":"org.joget.apps.datalist.lib.TextFieldDataListFilterType","properties":{}}},
    ],
    "rowActions": [],
    # Bulk actions — registered in reg-bb-engine Activator (see
    # plugins/reg-bb-engine/.../Activator.java lines 234, 238).
    # Retry: FAILED → PENDING; NotificationQueueWorker picks it up in ≤60s.
    # MarkDeadLetter: FAILED → DEAD_LETTER (terminal, no further retries).
    # Joget's JsonUtil.parseActionsFromJsonObject reads {id, name, label,
    # className, properties} at the TOP level of each action — `id` must
    # NOT be nested inside properties (verified against list_app_resolver_config).
    # Putting id only inside properties produces "JSONObject[id] not found"
    # when the CrudMenu renders the list.
    "actions": [
        {"id":"action_retry", "name":"action_retry", "label":"Retry Selected",
         "className":"global.govstack.regbb.engine.notification.RetryNotificationAction",
         "properties": {}},
        {"id":"action_dead",  "name":"action_dead",  "label":"Mark Dead-Letter",
         "className":"global.govstack.regbb.engine.notification.MarkDeadLetterAction",
         "properties": {}},
    ]
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

    print("Datalist:", DATALIST["id"])
    print("Columns: ", ", ".join(c["name"] for c in DATALIST["columns"]))
    print("Filters: ", ", ".join(f.get("name", f.get("id","?")) for f in DATALIST["filters"]))
    print("Actions: ", ", ".join(a.get("name", a.get("id","?")) for a in DATALIST["actions"]))

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    s, b = push()
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
