#!/usr/bin/env python3
"""
Builds APP_formQuality-1-<timestamp>.jwa — a standalone Joget app archive
that ships the form-quality admin module:

  * 7 forms      (qa_service, qa_tab, qa_rule, qa_gate, qa_issue, qa_record_status, audit_log)
  * 8 datalists  (one CRUD list per form, plus list_qa_issues_for_record for the embedded panel)
  * 1 userview   (Form Quality Admin, with Configuration + Runtime categories)

Output:  ./APP_formQuality-1-<YYYYMMDDhhmmss>.jwa  (a zip)

Imported via Joget admin → Settings → Apps → Import App. Joget creates the
app_fd_qa_* tables on first form save.
"""

import datetime
import json
import os
import sys
import xml.sax.saxutils as xs
import zipfile

# ── paths ──────────────────────────────────────────────────────────────────
HERE = os.path.dirname(os.path.abspath(__file__))
PLUGIN_ROOT = os.path.dirname(HERE)
GS_PLUGINS_ROOT = os.path.dirname(PLUGIN_ROOT)
FORM_DIR = os.path.join(PLUGIN_ROOT, "src/main/resources/forms")
AUDIT_LOG_FORM_PATH = os.path.join(
    GS_PLUGINS_ROOT, "joget-status-framework/src/main/resources/forms/audit_log.json")

APP_ID = "formQuality"
APP_VERSION = "1"
APP_NAME = "Form Quality Admin"

# ── form definitions (7) ───────────────────────────────────────────────────
FORMS = [
    ("qa_service",         "qa_service",         "QA Service",
     os.path.join(FORM_DIR, "qa_service.json")),
    ("qa_tab",             "qa_tab",             "QA Tab",
     os.path.join(FORM_DIR, "qa_tab.json")),
    ("qa_rule",            "qa_rule",            "QA Rule",
     os.path.join(FORM_DIR, "qa_rule.json")),
    ("qa_gate",            "qa_gate",            "QA Gate",
     os.path.join(FORM_DIR, "qa_gate.json")),
    ("qa_issue",           "qa_issue",           "QA Issue (runtime)",
     os.path.join(FORM_DIR, "qa_issue.json")),
    ("qa_record_status",   "qa_record_status",   "QA Record Status (runtime)",
     os.path.join(FORM_DIR, "qa_record_status.json")),
    ("audit_log",          "audit_log",          "Status Transition Audit Log",
     AUDIT_LOG_FORM_PATH),
]


# ── datalist factories ─────────────────────────────────────────────────────

def admin_crud_datalist(list_id, name, form_id, columns):
    """One CRUD datalist over a form, used in the admin userview.
    No actions are wired — the surrounding CrudMenu provides + New / Edit /
    Delete buttons natively, so the datalist itself stays minimal.
    """
    return {
        "id": list_id,
        "name": name,
        "description": f"CRUD list for {form_id}",
        "properties": { "key": "id" },
        "binder": {
            "className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
            "properties": {
                "formDefId": form_id,
                "extraCondition": "",
                "orderBy": "dateCreated",
                "order": "DESC"
            }
        },
        "columns": columns,
        "filters": [],
        "actions": [],
        "rowActions": []
    }


def col(field, label, width="*", sortable="true", formatter=None):
    c = {"name": field, "label": label, "sortable": sortable, "width": width}
    if formatter:
        c["format"] = formatter
    return c


# ── userview factory ───────────────────────────────────────────────────────

def crud_menu(menu_id, label, list_id, form_id, custom_id):
    """Enterprise CrudMenu — list + create/edit/view UI in one menu.
    Property shape mirrors farmersPortal's working menus (verified by reading
    that app's userview JSON). custom_id sets the URL slug.
    """
    return {
        "className": "org.joget.plugin.enterprise.CrudMenu",
        "properties": {
            "id": menu_id,
            "iconIncluded": False,
            "label": label,
            "customId": custom_id,
            "datalistId": list_id,
            "addFormId": form_id,
            "editFormId": form_id,
            "keyName": "",
            "selectionType": "multiple",
            "checkboxPosition": "left",
            "buttonPosition": "bothLeft",
            "rowCount": "true",
            "list-showDeleteButton": "yes",
            "list-confirmation": "",
            "list-newButtonLabel": "",
            "list-newLinkTarget": "",
            "list-editLinkLabel": "",
            "list-editLinkTarget": "",
            "list-deleteButtonLabel": "",
            "list-deleteSubformData": "",
            "list-deleteGridData": "",
            "list-deleteFiles": "",
            "list-abortRelatedRunningProcesses": "",
            "list-customHeader": "",
            "list-customFooter": "",
            "list-moreActions": [],
            "add-saveButtonLabel": "",
            "add-cancelButtonLabel": "",
            "add-afterSaved": "list",
            "add-afterSavedRedirectUrl": "",
            "add-afterSavedRedirectParamName": "",
            "add-afterSavedRedirectParamvalue": "",
            "add-messageShowAfterComplete": "",
            "add-customHeader": "",
            "add-customFooter": "",
            "edit-saveButtonLabel": "",
            "edit-backButtonLabel": "",
            "edit-prevButtonLabel": "",
            "edit-nextButtonLabel": "",
            "edit-firstButtonLabel": "",
            "edit-lastButtonLabel": "",
            "edit-readonly": "",
            "edit-readonlyLabel": "",
            "edit-allowRecordTraveling": "",
            "edit-afterSaved": "list",
            "edit-afterSavedRedirectUrl": "",
            "edit-afterSavedRedirectParamName": "",
            "edit-afterSavedRedirectParamvalue": "",
            "edit-messageShowAfterComplete": "",
            "edit-customHeader": "",
            "edit-customFooter": "",
            "edit-moreActions": [],
            "cacheAllLinks": "",
            "cacheListAction": "",
            "userviewCacheDuration": "",
            "userviewCacheScope": "",
            "enableOffline": ""
        }
    }


def datalist_menu(menu_id, label, list_id, custom_id):
    """Read-only datalist menu — used for runtime dashboards (no add/edit)."""
    return {
        "className": "org.joget.apps.userview.lib.DataListMenu",
        "properties": {
            "id": menu_id,
            "iconIncluded": False,
            "label": label,
            "customId": custom_id,
            "datalistId": list_id,
            "keyName": "",
            "selectionType": "multiple",
            "checkboxPosition": "left",
            "buttonPosition": "bothLeft",
            "rowCount": "",
            "customHeader": "",
            "customFooter": "",
            "cacheAllLinks": "",
            "cacheListAction": "",
            "userviewCacheDuration": "",
            "userviewCacheScope": "",
            "enableOffline": ""
        }
    }


def html_menu(menu_id, label, html_content, custom_id):
    """Static HTML menu — used for the About / readme page."""
    return {
        "className": "org.joget.apps.userview.lib.HtmlPage",
        "properties": {
            "id": menu_id,
            "iconIncluded": False,
            "label": label,
            "customId": custom_id,
            "content": html_content,
            "userviewCacheDuration": "",
            "userviewCacheScope": ""
        }
    }


def category(cat_id, label_with_icon, menus):
    return {
        "className": "org.joget.apps.userview.model.UserviewCategory",
        "menus": menus,
        "properties": {
            "id": cat_id,
            "label": label_with_icon,
            "iconIncluded": True,
            "hide": "",
            "comment": "",
            "permission": {"className": "", "properties": {}}
        }
    }


def build_userview():
    return {
        "className": "org.joget.apps.userview.model.Userview",
        # Top-level properties: identity + welcome strings only (no theme here).
        "properties": {
            "id": "v",
            "name": "Form Quality Admin",
            "description": "Authoring + monitoring UI for the form-quality-runtime plugin.",
            "logoutText": "Logout",
            "welcomeMessage": "#date.EEE, d MMM yyyy#",
            "footerMessage": "Powered by Joget"
        },
        "categories": [
            category("category-config", "<i class=\"fa fa-cogs\"></i> Configuration", [
                crud_menu("menu-services", "Services", "list_qa_service",       "qa_service",       "qa_service_crud"),
                crud_menu("menu-tabs",     "Tabs",     "list_qa_tab",           "qa_tab",           "qa_tab_crud"),
                crud_menu("menu-rules",    "Rules",    "list_qa_rule",          "qa_rule",          "qa_rule_crud"),
                crud_menu("menu-gates",    "Gates",    "list_qa_gate",          "qa_gate",          "qa_gate_crud"),
            ]),
            category("category-runtime", "<i class=\"fa fa-heartbeat\"></i> Runtime", [
                datalist_menu("menu-active-issues", "Active Issues", "list_qa_issue",        "qa_issue_list"),
                datalist_menu("menu-record-status", "Record Status", "list_qa_record_status","qa_record_status_list"),
                datalist_menu("menu-audit-log",     "Audit Log",     "list_audit_log",       "audit_log_list"),
            ]),
            category("category-about", "<i class=\"fa fa-info-circle\"></i> About", [
                html_menu("menu-readme", "Read me", README_HTML, "readme"),
            ])
        ],
        # Theme + permission live under setting.properties — Joget's documented contract.
        "setting": {
            "properties": {
                "userviewId": "v",
                "userviewName": "Form Quality Admin",
                "userviewDescription": "",
                "userview_thumbnail": "",
                "userview_category": "",
                "hideThisUserviewInAppCenter": "",
                "tempDisablePermissionChecking": "",
                "userview_thumbnail": "#appResource.qa_logo_120.png#",
                "theme": {
                    "className": "org.joget.apps.userview.lib.Dx8TrimedaTheme",
                    "properties": {
                        # All properties exist (Joget renders them as empty config) — same shape farmersPortal uses.
                        "logo": "#appResource.qa_logo_120.png#",
                        "fav_icon": "#appResource.qa_favicon_32.png#",
                        "homeUrl": "",
                        "css": "", "js": "",
                        "horizontal_menu": "",
                        "compactMode": "",
                        "subheader": "",
                        "subfooter": "",
                        "homeAttractBanner": "",
                        "loginPageTop": "", "loginPageBottom": "",
                        "loginBackground": "",
                        "customLogin": "",
                        "profile": "",
                        "removeAssignmentTitle": "",
                        "shortcutLinkLabel": "",
                        "userImage": "",
                        "userMenu": [],
                        "shortcut": [],
                        "inbox": "",
                        "fontControl": "",
                        "disablePush": "",
                        "disableHelpGuide": "",
                        "disablePwa": "",
                        "mobileViewDisabled": "",
                        "mobileCacheEnabled": "",
                        "enableResponsiveSwitch": "",
                        "urlsToCache": "",
                        "dx8background": "",
                        "dx8backgroundImage": "",
                        "dx8contentbackground": "",
                        "dx8mainBoxBackgroundColor": "",
                        "dx8secondaryBoxBackgroundColor": "",
                        "dx8headerColor": "",
                        "dx8headerFontColor": "",
                        "dx8headingBgColor": "",
                        "dx8headingFontColor": "",
                        "dx8contentFontColor": "",
                        "dx8fontColor": "",
                        "dx8secondaryFontColor": "",
                        "dx8primaryColor": "",
                        "dx8generalAccentColor": "",
                        "dx8linkColor": "",
                        "dx8linkActiveColor": "",
                        "dx8buttonColor": "",
                        "dx8buttonBackground": "",
                        "dx8fieldOutlineColor": "",
                        "dx8footerColor": "",
                        "dx8footerBackground": "",
                        "dx8navBackground": "",
                        "dx8navLinkColor": "",
                        "dx8navLinkBackground": "",
                        "dx8navLinkIcon": "",
                        "dx8navActiveLinkColor": "",
                        "dx8navActiveIconColor": "",
                        "dx8navActiveLinkBackground": "",
                        "dx8navDropdownBackground": "",
                        "dx8dropdownBackgroundColor": "",
                        "dx8navBadge": "",
                        "dx8navBadgeText": ""
                    }
                },
                "permission": {
                    "className": "org.joget.apps.userview.lib.LoggedInUserPermission",
                    "properties": {}
                }
            }
        }
    }


README_HTML = """
<div style="max-width:900px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;line-height:1.5;">
<h2>Form Quality Admin</h2>
<p>Generic, form-id-driven quality validation for any Joget DX 8.x form. Powered by the
<code>form-quality-runtime</code> OSGi plugin and the shared <code>joget-status-framework</code>.</p>

<h3>How it works</h3>
<ol>
  <li>Define a <strong>service</strong> for each form you want governed (Configuration → Services).</li>
  <li>Optionally add <strong>tabs</strong> for UI grouping.</li>
  <li>Add <strong>rules</strong>: each rule's <code>ruleScript</code> is SQL today (returns ≥1 row when the rule fails). Day 4 wraps this behind the JRE DSL.</li>
  <li>Optionally add <strong>gates</strong>: block status transitions when ERRORs remain.</li>
  <li>Wire the <code>FormQualityPostProcessor</code> on your target form's
      <code>postProcessor</code> property, with the matching <code>serviceId</code>.</li>
  <li>On every save, the post-processor runs the rules and updates Active Issues + Record Status.</li>
</ol>

<h3>Tables</h3>
<table style="border-collapse:collapse;">
  <thead><tr><th style="text-align:left;padding:4px 12px 4px 0;">Form</th><th style="text-align:left;padding:4px 12px 4px 0;">Owner</th></tr></thead>
  <tbody>
    <tr><td>qa_service</td><td>You (operator)</td></tr>
    <tr><td>qa_tab</td><td>You</td></tr>
    <tr><td>qa_rule</td><td>You</td></tr>
    <tr><td>qa_gate</td><td>You</td></tr>
    <tr><td>qa_issue</td><td>Plugin (runtime)</td></tr>
    <tr><td>qa_record_status</td><td>Plugin (runtime)</td></tr>
    <tr><td>audit_log</td><td>joget-status-framework (runtime, shared)</td></tr>
  </tbody>
</table>

<p style="margin-top:24px;color:#666;font-size:13px;">
form-quality-runtime 8.1-SNAPSHOT — Form Quality Admin app v1 — generated 2026-04-26.
</p>
</div>
""".strip()


# ── XML serialisation ──────────────────────────────────────────────────────

def xml_escape_json(json_str):
    """Joget escapes JSON inside <json> tags using XML entities."""
    return (json_str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace('"', "&quot;"))


def xml_now():
    return datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3] + " UTC"


def render_form_def(form_id, table_name, name, json_str):
    return f"""      <formDefinition>
         <id>{xs.escape(form_id)}</id>
         <appId>{APP_ID}</appId>
         <appVersion>{APP_VERSION}</appVersion>
         <name>{xs.escape(name)}</name>
         <tableName>{xs.escape(table_name)}</tableName>
         <json>{xml_escape_json(json_str)}</json>
         <dateCreated class="java.sql.Timestamp">{xml_now()}</dateCreated>
         <dateModified class="java.sql.Timestamp">{xml_now()}</dateModified>
      </formDefinition>"""


def render_datalist_def(list_def):
    return f"""      <datalistDefinition>
         <id>{xs.escape(list_def['id'])}</id>
         <appId>{APP_ID}</appId>
         <appVersion>{APP_VERSION}</appVersion>
         <name>{xs.escape(list_def['name'])}</name>
         <description>{xs.escape(list_def.get('description',''))}</description>
         <json>{xml_escape_json(json.dumps(list_def))}</json>
         <dateCreated class="java.sql.Timestamp">{xml_now()}</dateCreated>
         <dateModified class="java.sql.Timestamp">{xml_now()}</dateModified>
      </datalistDefinition>"""


def render_userview_def(uv):
    return f"""      <userviewDefinition>
         <id>v</id>
         <appId>{APP_ID}</appId>
         <appVersion>{APP_VERSION}</appVersion>
         <name>Form Quality Admin</name>
         <description></description>
         <json>{xml_escape_json(json.dumps(uv))}</json>
         <dateCreated class="java.sql.Timestamp">{xml_now()}</dateCreated>
         <dateModified class="java.sql.Timestamp">{xml_now()}</dateModified>
      </userviewDefinition>"""


# ── main ───────────────────────────────────────────────────────────────────

def main():
    # forms
    form_blocks = []
    for form_id, table_name, name, path in FORMS:
        with open(path) as f:
            json_str = f.read()
        form_blocks.append(render_form_def(form_id, table_name, name, json_str))

    # datalists
    datalists = [
        admin_crud_datalist("list_qa_service", "QA Services", "qa_service", [
            col("serviceId", "Service ID", "150"),
            col("serviceName", "Name", "*"),
            col("primaryFormId", "Primary Form", "180"),
            col("isActive", "Active", "60"),
            col("dateModified", "Modified", "180", formatter={"className":"org.joget.plugin.enterprise.DateFormatter","properties":{"format":"yyyy-MM-dd HH:mm"}})
        ]),
        admin_crud_datalist("list_qa_tab", "QA Tabs", "qa_tab", [
            col("serviceId", "Service ID", "150"),
            col("tabCode", "Code", "120"),
            col("tabLabel", "Label", "*"),
            col("tabFormId", "Tab Form", "180"),
            col("tabOrder", "Order", "60")
        ]),
        admin_crud_datalist("list_qa_rule", "QA Rules", "qa_rule", [
            col("serviceId", "Service", "120"),
            col("tabCode", "Tab", "100"),
            col("ruleCode", "Rule Code", "240"),
            col("severity", "Severity", "80"),
            col("isActive", "Active", "60"),
            col("message", "Message", "*")
        ]),
        admin_crud_datalist("list_qa_gate", "QA Gates", "qa_gate", [
            col("serviceId", "Service", "120"),
            col("gateField", "Field", "100"),
            col("gateValues", "Gated Values", "200"),
            col("blockedBySeverity", "Blocked By", "100"),
            col("isActive", "Active", "60")
        ]),
        admin_crud_datalist("list_qa_issue", "Active Issues", "qa_issue", [
            col("severity", "Severity", "80"),
            col("formId", "Form", "120"),
            col("recordId", "Record", "150"),
            col("tabCode", "Tab", "100"),
            col("ruleCode", "Rule", "180"),
            col("message", "Message", "*"),
            col("isActive", "Active", "60"),
            col("dateRaised", "Raised", "180")
        ]),
        admin_crud_datalist("list_qa_record_status", "Record Status", "qa_record_status", [
            col("formId", "Form", "150"),
            col("recordId", "Record", "180"),
            col("status", "Quality Status", "150"),
            col("errorCount", "Errors", "70"),
            col("warningCount", "Warnings", "70"),
            col("lastEvaluated", "Last Evaluated", "180")
        ]),
        admin_crud_datalist("list_audit_log", "Status Audit Log", "audit_log", [
            col("entity_type", "Entity", "120"),
            col("entity_id", "Record", "180"),
            col("from_status", "From", "120"),
            col("to_status", "To", "120"),
            col("triggered_by", "By", "120"),
            col("timestamp", "When", "180"),
            col("reason", "Reason", "*")
        ])
    ]

    # The embedded panel datalist (used INSIDE forms in other apps)
    embedded_panel = {
        "id": "list_qa_issues_for_record",
        "name": "Quality Issues — Embedded Panel",
        "description": "Filter by recordId via embedded-datalist hash variable.",
        "properties": { "key": "id" },
        "binder": {
            "className": "org.joget.plugin.enterprise.JdbcDataListBinder",
            "properties": {
                "datasource": "default",
                "primaryKey": "id",
                "sql": "SELECT id, c_severity AS severity, c_tabcode AS tab, c_rulecode AS rule_code, c_message AS message, c_dateraised AS date_raised FROM app_fd_qa_issue WHERE c_isactive = 'Y' AND ('#requestParam.recordId#' = '' OR c_recordid = '#requestParam.recordId#') ORDER BY CASE c_severity WHEN 'ERROR' THEN 1 WHEN 'WARNING' THEN 2 ELSE 3 END, c_tabcode, c_rulecode",
                "countSql": "SELECT COUNT(*) FROM app_fd_qa_issue WHERE c_isactive = 'Y' AND ('#requestParam.recordId#' = '' OR c_recordid = '#requestParam.recordId#')"
            }
        },
        "columns": [
            col("severity", "Severity", "80"),
            col("tab", "Tab", "120"),
            col("rule_code", "Rule", "200"),
            col("message", "Issue", "*", "false"),
            col("date_raised", "Raised", "180"),
        ],
        "filters": [], "actions": [], "rowActions": []
    }
    datalists.append(embedded_panel)
    datalist_blocks = [render_datalist_def(d) for d in datalists]

    # userview
    uv = build_userview()
    userview_block = render_userview_def(uv)

    # assemble appDefinition.xml
    app_xml = f"""<?xml version="1.0" encoding="UTF-8"?>
<appDefinition>
   <appId>{APP_ID}</appId>
   <id>{APP_ID}</id>
   <version>{APP_VERSION}</version>
   <name>{APP_NAME}</name>
   <dateCreated class="java.sql.Timestamp">{xml_now()}</dateCreated>
   <dateModified class="java.sql.Timestamp">{xml_now()}</dateModified>
   <packageDefinitionList/>
   <formDefinitionList>
{chr(10).join(form_blocks)}
   </formDefinitionList>
   <userviewDefinitionList>
{userview_block}
   </userviewDefinitionList>
   <datalistDefinitionList>
{chr(10).join(datalist_blocks)}
   </datalistDefinitionList>
   <pluginDefaultPropertiesList/>
   <environmentVariableList/>
   <messageList/>
   <createdBy>admin</createdBy>
</appDefinition>
"""

    # write the .jwa zip
    timestamp = datetime.datetime.utcnow().strftime("%Y%m%d%H%M%S")
    out_path = f"/tmp/APP_{APP_ID}-{APP_VERSION}-{timestamp}.jwa"
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("appDefinition.xml", app_xml)
    print(f"  wrote {out_path}  ({os.path.getsize(out_path)} bytes)")

    # copy into deploy/ for user access
    final_path = os.path.join(HERE, f"APP_{APP_ID}-{APP_VERSION}-{timestamp}.jwa")
    with open(out_path, "rb") as src, open(final_path, "wb") as dst:
        dst.write(src.read())
    print(f"  also at  {final_path}")
    return out_path, final_path


if __name__ == "__main__":
    out, final = main()
    print("done.")
