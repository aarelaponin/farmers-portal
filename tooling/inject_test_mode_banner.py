#!/usr/bin/env python3
"""
Inject the W2.7 test-mode banner into the userview's Dx8TrimedaTheme
`css` + `js` properties so it renders on every operator-facing page.

The banner is yellow, sticky-top, non-closable. It alerts operators that
all notifications are being redirected to the test inbox/phone and
points at the runbook for going live.

To remove when going live: re-run this script with --apply --remove, or
edit the userview in App Composer and clear the BANNER block from the
theme's CSS and JS properties.

HARD RULE compliant: pushes via form-creator-api userview endpoint.
"""
import argparse, datetime, json, os, re, sys, urllib.error, urllib.request
import psycopg2

PG = dict(host="joget-pgsql-sa.postgres.database.azure.com", dbname="jogetdb",
          user="jogetadmin", password=os.environ.get("PGPASSWORD", ""), port=5432, sslmode="require")
JOGET = "http://20.87.213.78:8080/jw"
HEADERS = {
    "Content-Type": "application/json",
    "api_id":  "API-e7878006-c15a-425e-9c36-bebc7c4d085c",
    "api_key": os.environ.get("JOGET_API_KEY", ""),
}

# Sentinel markers — keep our block isolatable for clean idempotent updates / removal.
BANNER_BEGIN = "/* === RegBB test-mode banner — managed by inject_test_mode_banner.py === */"
BANNER_END   = "/* === RegBB test-mode banner end === */"

BANNER_CSS = BANNER_BEGIN + """
/* Subtle peripheral cue: 4-pixel stripe along the very top of the viewport.
   The visual idiom for "non-production environment" across professional
   tooling. Doesn't grab attention; doesn't shift content. */
.regbb-test-stripe {
    position: fixed; top: 0; left: 0; right: 0;
    height: 4px;
    background: linear-gradient(90deg, #ff9800 0%, #f57c00 50%, #e65100 100%);
    z-index: 99999;
    pointer-events: none;
}
/* Small pill near the user menu. Click to expand into a details card. */
.regbb-test-pill {
    position: fixed;
    top: 18px; right: 220px;
    background: #fff3e0;
    color: #e65100;
    border: 1px solid #fb8c00;
    border-radius: 12px;
    padding: 3px 12px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.06em;
    cursor: pointer;
    z-index: 99999;
    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    user-select: none;
    transition: background 0.15s ease;
}
.regbb-test-pill:hover { background: #ffe0b2; }
.regbb-test-pill::before { content: '\\1F6A7  '; }
/* Details card shown when the pill is clicked. */
.regbb-test-card {
    position: fixed;
    top: 48px; right: 30px;
    background: white;
    border: 1px solid #fb8c00;
    border-top: 4px solid #e65100;
    border-radius: 6px;
    padding: 16px 20px;
    box-shadow: 0 6px 20px rgba(0,0,0,0.18);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    font-size: 12px; line-height: 1.5; color: #333;
    max-width: 380px;
    z-index: 99999;
}
.regbb-test-card h4 { margin: 0 0 10px 0; color: #e65100; font-size: 13px; }
.regbb-test-card p  { margin: 0 0 6px 0; }
.regbb-test-card .footnote { margin-top: 10px; color: #777; font-size: 11px; }
.regbb-test-card .closebtn {
    display: inline-block; margin-top: 10px; color: #888; font-size: 11px;
    cursor: pointer; text-decoration: underline;
}
""" + BANNER_END

BANNER_JS_BEGIN = "/* === RegBB test-mode banner JS — managed by inject_test_mode_banner.py === */"
BANNER_JS_END   = "/* === RegBB test-mode banner JS end === */"

BANNER_JS = BANNER_JS_BEGIN + r"""
(function() {
    if (window.__regbbTestBannerInjected) return;
    window.__regbbTestBannerInjected = true;
    function ensureStripe() {
        if (document.getElementById('regbb-test-stripe')) return;
        var s = document.createElement('div');
        s.id = 'regbb-test-stripe';
        s.className = 'regbb-test-stripe';
        document.body.insertBefore(s, document.body.firstChild);
    }
    function ensurePill() {
        if (document.getElementById('regbb-test-pill')) return;
        var p = document.createElement('div');
        p.id = 'regbb-test-pill';
        p.className = 'regbb-test-pill';
        p.textContent = 'TEST MODE';
        p.title = 'Every email is redirected to aarelaponin@gmail.com, every SMS to +26658515039. Real citizens receive nothing. Click for details.';
        p.addEventListener('click', toggleCard);
        document.body.appendChild(p);
    }
    function toggleCard() {
        var existing = document.getElementById('regbb-test-card');
        if (existing) { existing.remove(); return; }
        var c = document.createElement('div');
        c.id = 'regbb-test-card';
        c.className = 'regbb-test-card';
        c.innerHTML =
            '<h4>&#x1F6A7; TEST MODE ACTIVE</h4>' +
            '<p>Every <strong>email</strong> &rarr; <strong>aarelaponin@gmail.com</strong></p>' +
            '<p>Every <strong>SMS</strong> &rarr; <strong>+26658515039</strong></p>' +
            '<p class="footnote">Real citizens receive nothing. Subject lines show the intended recipient as <code>[TEST &rarr; ...]</code>. ' +
            'See <em>notification_test_mode_override.md</em> for the go-live procedure.</p>' +
            '<a class="closebtn">close</a>';
        c.querySelector('.closebtn').addEventListener('click', function() { c.remove(); });
        document.body.appendChild(c);
        // Click-outside-to-dismiss.
        setTimeout(function() {
            document.addEventListener('click', function dismiss(e) {
                if (!c.contains(e.target) && e.target.id !== 'regbb-test-pill') {
                    c.remove();
                    document.removeEventListener('click', dismiss);
                }
            });
        }, 0);
    }
    function show() {
        ensureStripe();
        ensurePill();
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', show);
    } else {
        show();
    }
})();
""" + BANNER_JS_END


def push_userview(uv):
    payload = {
        "appId": "farmersPortal",
        "userviewId":   uv["properties"]["id"],
        "userviewName": uv["properties"].get("name", uv["properties"]["id"]),
        "json":         json.dumps(uv),
    }
    req = urllib.request.Request(
        JOGET + "/api/formcreator/formcreator/userviews",
        data=json.dumps(payload).encode(),
        headers=HEADERS, method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def find_theme(uv):
    """Return the theme dict so we can mutate its properties."""
    setting = uv.get("setting", {}) or uv.get("properties", {}).get("setting", {})
    theme = setting.get("properties", {}).get("theme")
    if theme is None:
        # Try the nested form (different Joget versions structure this differently)
        theme = uv.get("properties", {}).get("setting", {}).get("properties", {}).get("theme")
    return theme


def strip_managed_block(text, begin, end):
    """Remove our managed block if present, preserving everything else."""
    if not text:
        return ""
    pattern = re.compile(re.escape(begin) + r".*?" + re.escape(end), re.DOTALL)
    return pattern.sub("", text).strip()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply",   action="store_true")
    ap.add_argument("--remove", action="store_true",
                    help="Remove the banner (use when going live).")
    args = ap.parse_args()

    conn = psycopg2.connect(**PG); cur = conn.cursor()
    cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
    uv = json.loads(cur.fetchone()[0])

    theme = find_theme(uv)
    if theme is None:
        print("Could not find theme in userview", file=sys.stderr); return 2

    tp = theme.setdefault("properties", {})
    existing_css = tp.get("css", "") or ""
    existing_js  = tp.get("js", "")  or ""

    # Strip any prior managed block (idempotent re-runs leave one copy only).
    cleaned_css = strip_managed_block(existing_css, BANNER_BEGIN, BANNER_END)
    cleaned_js  = strip_managed_block(existing_js,  BANNER_JS_BEGIN, BANNER_JS_END)

    if args.remove:
        new_css = cleaned_css
        new_js  = cleaned_js
        action_desc = "REMOVING banner (go-live mode)"
    else:
        new_css = (cleaned_css + "\n\n" + BANNER_CSS).strip()
        new_js  = (cleaned_js  + "\n\n" + BANNER_JS).strip()
        action_desc = "INJECTING banner (test-mode visibility)"

    tp["css"] = new_css
    tp["js"]  = new_js

    print(f"\n{action_desc}\n")
    print(f"  CSS length: {len(existing_css)} → {len(new_css)}")
    print(f"  JS  length: {len(existing_js)} → {len(new_js)}")

    if args.dry_run:
        print("\nDry-run — not pushing.")
        return 0

    ts = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    backup = f"_backups/v.prebanner.{ts}.json"
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    with open(backup, "w") as f: json.dump(uv, f, indent=2)
    print(f"[backup] {backup}")

    s, b = push_userview(uv)
    print(f"\nHTTP {s}: {b[:300]}")
    return 0 if s == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
