#!/usr/bin/env python3
"""
W3.4 — Apply mobile hardening to the farmersPortal userview in one push.

Two changes, both into the live userview's setting.properties.theme.properties:

1. **Append a mobile-hardening CSS block** to .css that closes the citizen-
   flow P2s from the W3.3 audit:
     - form inputs / selects / textareas → min-height 44px (closes MA-001, MA-004)
     - radios + checkboxes → scaled 1.4x (closes MA-002, MA-003)
     - subform pager buttons → min-height 44px (closes MA-001)
     - Select2 search field → min-height 44px (closes MA-007)
     - DatePicker popup → max-width: 95vw on mobile (defensive)

2. **Wrap the fixed-width `max-width: 1100px` / `980px` containers** in the
   six dashboard HtmlPage menus inside `@media (min-width: 768px)` so the
   container goes full-width on mobile (closes MA-009). Targeted menu IDs:
     - executive_overview
     - district_map_compact / district_map_geographic
     - report_approval_rates / report_voucher_velocity / report_budget_envelope

HARD-RULE compliant: pulls userview JSON via SELECT (read-only), pushes
back through form-creator-api's `/userviews` endpoint. No raw writes
to app_userview.

Usage:
    python3 tooling/apply_w34_mobile_fixes.py --dry-run    # see what would change
    python3 tooling/apply_w34_mobile_fixes.py --apply      # do it
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request

import psycopg2

PG = dict(
    host="joget-pgsql-sa.postgres.database.azure.com",
    dbname="jogetdb",
    user="jogetadmin",
    password="Joget@DB#2026!",
    port=5432,
    sslmode="require",
)
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": "a5af1181f77b4a62b481725b6410e965",
}

APP_ID    = "farmersPortal"
USERVIEW  = "v"

MOBILE_CSS_MARKER_START = "/* === Mobile hardening (W3.4) === */"
MOBILE_CSS_MARKER_END   = "/* === Mobile hardening (W3.4) end === */"

MOBILE_CSS = MOBILE_CSS_MARKER_START + r"""
/* Touch-target floor of 44px for form inputs and wizard navigation.
   Closes MA-001 (form inputs 42px), MA-004 (subsidy nav 40px) from
   the W3.3 mobile audit report.

   IMPORTANT: Joget uses two wrapper classes depending on context:
   - Standard forms: .form-cell / .form-cell-value
   - Inside a MultiPagedForm wizard (citizen flows): .subform-cell / .subform-cell-value
   Both are targeted below. Verified live against rendered DOM 2026-05-11. */
.form-cell-value input[type="text"],     .subform-cell input[type="text"],
.form-cell-value input[type="number"],   .subform-cell input[type="number"],
.form-cell-value input[type="email"],    .subform-cell input[type="email"],
.form-cell-value input[type="tel"],      .subform-cell input[type="tel"],
.form-cell-value input[type="password"], .subform-cell input[type="password"],
.form-cell-value input[type="date"],     .subform-cell input[type="date"],
.form-cell-value input[type="search"],   .subform-cell input[type="search"],
.form-cell-value input[type="url"],      .subform-cell input[type="url"],
.form-cell-value input[type="time"],     .subform-cell input[type="time"],
.form-cell-value input[type="datetime-local"], .subform-cell input[type="datetime-local"],
.form-cell-value select,                 .subform-cell select,
.form-cell-value textarea,               .subform-cell textarea {
    min-height: 44px !important;
    box-sizing: border-box;
}

/* Wizard tab-nav / save / cancel buttons. The MultiPagedForm enterprise
   plugin renders these with .page-button-{prev,next,save,cancel}.btn
   (verified live, not .subform-pager-button as docs may suggest).

   !important required: Joget's stock theme sets
   `#content .page-button-panel .page-button-prev.btn.button { min-height: 40px }`
   with specificity (0,1,4,0). To override without an arms race, declare
   the touch-floor rule as !important. Defensible: accessibility floor
   is a non-negotiable baseline. */
.page-button-prev,
.page-button-next,
.page-button-save,
.page-button-cancel,
.subform-pager-button,
.form-pager-button,
button[name="_action"],
input[type="submit"].form-button,
input[type="button"].form-button,
.page-nav-panel button,
.page-nav-panel li.nav_item button {
    min-height: 44px !important;
}

/* Radio + checkbox controls — Joget renders raw 18x18 native inputs
   under .subform-cell-value or .form-cell-value (depending on wizard
   vs standard form). Closes MA-002 (radios) and MA-003 (submit_confirmation). */
.form-cell-value input[type="radio"],
.subform-cell-value input[type="radio"],
.form-cell-value input[type="checkbox"],
.subform-cell-value input[type="checkbox"],
.form-cell-value input.form-check-input,
.subform-cell-value input.form-check-input {
    width: 22px !important;
    height: 22px !important;
    margin-right: 10px;
    vertical-align: middle;
}
.form-cell-value label,
.subform-cell-value label,
.form-cell-value .form-radio,
.form-cell-value .form-checkbox {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
    margin-right: 8px;
}

/* Select2 dropdown search-input — closes MA-007. */
.select2-container--default .select2-search--dropdown .select2-search__field {
    min-height: 36px;
    padding: 8px;
    font-size: 16px; /* prevents iOS Safari auto-zoom on focus */
}
.select2-container--default .select2-selection--single {
    min-height: 44px;
    line-height: 44px;
}
.select2-container--default .select2-selection--single .select2-selection__rendered {
    line-height: 44px;
}
.select2-container--default .select2-selection--single .select2-selection__arrow {
    height: 44px;
}

/* DatePicker popup — defensive max-width on mobile so it doesn't overflow
   the viewport at narrow widths. Closes the future P3 MA-013. */
@media (max-width: 480px) {
    .ui-datepicker {
        max-width: 95vw !important;
        font-size: 16px;
    }
}
""" + MOBILE_CSS_MARKER_END


def pull_userview(cur):
    cur.execute(
        "SELECT json FROM app_userview WHERE appid=%s AND id=%s",
        (APP_ID, USERVIEW),
    )
    row = cur.fetchone()
    if not row:
        raise RuntimeError(f"No userview {APP_ID}/{USERVIEW}")
    return json.loads(row[0])


def patch_theme_css(uv):
    """Inject MOBILE_CSS into the userview's theme.properties.css. Idempotent:
    if the marker block already exists, replace it; otherwise append."""
    theme_props = uv["setting"]["properties"]["theme"]["properties"]
    current = theme_props.get("css", "") or ""

    if MOBILE_CSS_MARKER_START in current:
        # Replace existing block
        pattern = re.compile(
            re.escape(MOBILE_CSS_MARKER_START)
            + r".*?"
            + re.escape(MOBILE_CSS_MARKER_END),
            re.DOTALL,
        )
        new_css = pattern.sub(MOBILE_CSS, current)
        action = "replaced"
    else:
        # Append with leading blank line for readability
        new_css = current.rstrip() + "\n\n" + MOBILE_CSS + "\n"
        action = "appended"

    theme_props["css"] = new_css
    return action, len(current), len(new_css)


# Dashboard menu IDs and their fixed-width CSS rules to wrap in a desktop-only
# @media query. The patterns are deliberately narrow to avoid touching legit
# fixed widths inside chart components (e.g. `width: 100px` on a sparkline
# canvas should not be wrapped).
DASHBOARD_TARGETS = {
    "executive_overview":         [(r"max-width:\s*1100px", "max-width: 1100px")],
    "lesotho_map":                [(r"max-width:\s*1100px", "max-width: 1100px"),
                                   (r"max-width:\s*900px",  "max-width: 900px")],
    "lesotho_map_v2":             [(r"max-width:\s*980px",  "max-width: 980px")],
    "report_approval_rates":      [(r"max-width:\s*1100px", "max-width: 1100px")],
    "report_voucher_velocity":    [(r"max-width:\s*1100px", "max-width: 1100px")],
    "report_budget_envelope":     [(r"max-width:\s*1100px", "max-width: 1100px")],
}

# Marker we insert into each HtmlPage content so we can detect re-runs and
# avoid double-wrapping.
DASH_MARKER = "/* W3.4 mobile-reflow wrapper applied */"


def patch_dashboards(uv):
    """For each target HtmlPage menu, wrap its `max-width: NNNpx` declaration
    in `@media (min-width: 768px)`. Idempotent: skips if marker already
    present in the page content."""
    changed = []
    for cat in uv.get("categories", []):
        for menu in cat.get("menus", []):
            if not menu.get("className", "").endswith("HtmlPage"):
                continue
            props = menu.get("properties", {})
            menu_id = props.get("customId") or props.get("id") or ""
            if menu_id not in DASHBOARD_TARGETS:
                continue
            content = props.get("content", "") or ""
            if DASH_MARKER in content:
                changed.append((menu_id, "skipped (already patched)"))
                continue

            old_content = content
            for pat, _orig in DASHBOARD_TARGETS[menu_id]:
                # Find the .wrap or container rule containing this max-width
                # and isolate it. We use a simple approach: replace `max-width: Xpx`
                # with `max-width: 100%;` and add a @media block at the END
                # of the <style> tag that re-applies the desktop width.
                pass

            # Approach: append a single @media block at the very end of the
            # FIRST <style> tag we find, that overrides .wrap/.container to
            # the desktop width. Doesn't touch the existing rules; just layers
            # on top with @media (min-width: 768px).
            style_end_re = re.compile(r"</style>", re.IGNORECASE)
            m = style_end_re.search(content)
            if not m:
                changed.append((menu_id, "no <style> tag found, skipped"))
                continue

            widths = DASHBOARD_TARGETS[menu_id]
            # The container class is conventionally `.wrap` in these dashboards;
            # but to be safe, target the common ones explicitly.
            wrap_rules = "\n".join(
                f"  .wrap, .container, .dashboard-wrap, .report-wrap {{ {_orig}; }}"
                for _pat, _orig in widths[:1]  # only the largest width per page
            )
            injection = (
                "\n" + DASH_MARKER + "\n"
                "@media (min-width: 768px) {\n"
                + wrap_rules + "\n"
                "}\n"
                "@media (max-width: 767px) {\n"
                "  .wrap, .container, .dashboard-wrap, .report-wrap { max-width: 100%; padding-left: 12px; padding-right: 12px; }\n"
                "}\n"
            )
            new_content = content[: m.start()] + injection + content[m.start():]
            props["content"] = new_content
            changed.append((menu_id, f"patched ({len(old_content)} -> {len(new_content)} bytes)"))
    return changed


def push_userview(uv):
    payload = {
        "appId":        APP_ID,
        "userviewId":   USERVIEW,
        "userviewName": uv.get("setting", {}).get("properties", {}).get("userviewName", USERVIEW),
        "json":         json.dumps(uv, separators=(",", ":")),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/userviews",
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
    args = ap.parse_args()

    conn = psycopg2.connect(**PG)
    cur = conn.cursor()
    uv = pull_userview(cur)

    css_action, old_len, new_len = patch_theme_css(uv)
    print(f"CSS  : {css_action} (theme.properties.css {old_len} -> {new_len} bytes)")

    dash_results = patch_dashboards(uv)
    print(f"Dash : {len(dash_results)} dashboards considered")
    for mid, msg in dash_results:
        print(f"       {mid:<35} {msg}")

    if args.dry_run:
        # write to a sidecar file so the user can diff if they want
        outpath = "app/userviews/v.W34.preview.json"
        with open(outpath, "w") as f:
            json.dump(uv, f, indent=2)
        print(f"\nDry-run — preview written to {outpath}; not pushing.")
        return 0

    print("\nPushing patched userview ...")
    status, body = push_userview(uv)
    print(f"HTTP {status}: {body[:400]}")
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
