#!/usr/bin/env python3
"""
W2.8 — per-application notification timeline.

Creates list_notif_for_application — a JdbcDataListBinder datalist that
returns every row in notification_queue where correlationId matches the
given application id (derived as 'AP-' + first 8 chars uppercase, the
same convention EligibilityProcessingWorker uses when building the
notification vars map).

Designed to be embedded into the spApplicationDecision form via an
EmbeddedDatalist element with:
   filterParams: [{ paramName: "app_id", fieldId: "parent_id" }]

The SQL uses #requestParam.app_id# — the JdbcDataListBinder substitution
token that EmbeddedDatalist populates from filterParams.

HARD RULE compliant: pushes via form-creator-api.
"""
import argparse, json, sys, urllib.error, urllib.request

JOGET = "http://20.87.213.78:8080/jw"
API_ID  = "API-e7878006-c15a-425e-9c36-bebc7c4d085c"
API_KEY = os.environ.get("JOGET_API_KEY", "")
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  API_ID,
    "api_key": API_KEY,
}

SQL = """
SELECT
    id                        AS row_id,
    datecreated               AS when_at,
    c_eventCode               AS event_code,
    c_channel                 AS channel,
    c_status                  AS status,
    c_backend                 AS backend,
    c_actualRecipient         AS sent_to,
    c_intendedRecipient       AS intended,
    c_intendedRecipientStatus AS intended_status,
    c_testMode                AS test_mode,
    c_subject                 AS subject,
    c_lastError               AS last_error
FROM app_fd_notification_queue
WHERE '#requestParam.app_id#' <> ''
  AND c_correlationId = CONCAT('AP-', UPPER(SUBSTRING('#requestParam.app_id#', 1, 8)))
ORDER BY datecreated DESC
""".strip()

DATALIST = {
    "id":   "list_notif_for_application",
    "name": "List: Notifications for Application",
    "useSession":           "false",
    "showPageSizeSelector": "false",
    "pageSize":             50,
    "orderBy":              "when_at",
    "order":                "DESC",
    "binder": {
        "className": "org.joget.plugin.enterprise.JdbcDataListBinder",
        "properties": {
            "jdbcDatasource": "default",
            "primaryKey":     "row_id",
            "sql":            SQL,
        }
    },
    "columns": [
        {"id":"col_when",   "name":"when_at",        "label":"When",       "sortable":"true",
         "format": {
             "className": "org.joget.plugin.enterprise.DateFormatter",
             "properties": {
                 "format":   "yyyy-MM-dd HH:mm",
                 "rawFormat": "yyyy-MM-dd HH:mm:ss"
             }
         }},
        {"id":"col_event",  "name":"event_code",     "label":"Event",       "sortable":"true"},
        {"id":"col_ch",     "name":"channel",        "label":"Ch",          "sortable":"true"},
        {"id":"col_st",     "name":"status",         "label":"Status",      "sortable":"true"},
        {"id":"col_be",     "name":"backend",        "label":"Backend",     "sortable":"true"},
        {"id":"col_to",     "name":"sent_to",        "label":"Sent To",     "sortable":"true"},
        {"id":"col_int",    "name":"intended",       "label":"Intended",    "sortable":"true"},
        {"id":"col_intst",  "name":"intended_status","label":"Reason",      "sortable":"true"},
        {"id":"col_tm",     "name":"test_mode",      "label":"Test?",       "sortable":"true"},
        {"id":"col_subj",   "name":"subject",        "label":"Subject"},
        {"id":"col_err",    "name":"last_error",     "label":"Last Error"},
    ],
    "filters":    [],
    "rowActions": [],
    "actions":    []
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
    print("\nSQL preview (first 200 chars):\n  ", SQL[:200], "...")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    s, b = push()
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
