#!/usr/bin/env python3
"""
Build docs/developer/api_reference.md by scanning every
gs-plugins source file for @Operation / @Param / @Response annotations
and rendering one section per provider.

Catches every REST endpoint authored as a Joget API Builder
@Operation, plus the hand-curated authentication + URL patterns at the
top. Re-run after adding or changing endpoints.

Output structure:
  - Authentication conventions (api_id, api_key, where to find them)
  - One section per provider, with table of endpoints
  - Per-endpoint details: URL, method, summary, params, response codes

Usage:
    python3 tooling/build_api_reference.py
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "developer" / "api_reference.md"

# Providers to extract from. The key is the section title shown in the
# rendered doc; the values are the source file and the URL prefix.
PROVIDERS = [
    ("form-creator-api",
     "plugins/form-creator-api/src/main/java/global/govstack/formcreator/lib/FormCreatorServiceProvider.java",
     "/jw/api/formcreator", "API-e7878006-c15a-425e-9c36-bebc7c4d085c"),
    ("reg-bb-engine — RegBb evaluation",
     "plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/api/RegBbEvalApi.java",
     "/jw/api/regbb", "API-168e3678-1f9a-46fc-8c19-d0d9a917eb73"),
    ("reg-bb-engine — Budget engine",
     "plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/api/BudgetApi.java",
     "/jw/api/budget", "API-BUDGET"),
    ("joget-gis-server — GIS calculations",
     "plugins/joget-gis-server/src/main/java/global/govstack/gisserver/lib/GisApiProvider.java",
     "/jw/api/gis", "(look up in app_builder where name='gis')"),
    ("joget-rules-api — Rule evaluation",
     "plugins/joget-rules-api/src/main/java/global/govstack/rulesapi/lib/RulesApiProvider.java",
     "/jw/api/rules", "(look up in app_builder where name='rules')"),
    ("joget-smart-search — Smart search",
     "plugins/joget-smart-search/src/main/java/global/govstack/smartsearch/api/SmartSearchApiPlugin.java",
     "/jw/api/smartsearch", "(look up in app_builder where name='smartsearch')"),
    ("app-def-provider — App definition export",
     "plugins/app-def-provider/src/main/java/com/fiscaladmin/gam/appdefinitionprovider/lib/AppDefinitionProvider.java",
     "/jw/api/appdef", "(look up in app_builder where name='appdef')"),
]

# Regex patterns
RE_OPERATION = re.compile(
    r'@Operation\s*\(\s*'
    r'(?P<body>.*?)'
    r'\)\s*'
    r'(?:@Responses\s*\(\s*(?P<responses>.*?)\s*\)\s*)?'
    r'public\s+\S+\s+(?P<method>\w+)\s*\(',
    re.DOTALL
)
RE_FIELD = re.compile(r'(\w+)\s*=\s*("(?:[^"\\]|\\.)*"|[\w.]+)')
RE_PARAM_BLOCK = re.compile(
    r'@Param\s*\(\s*(?P<body>[^)]*)\)\s*\S+\s+(?P<varname>\w+)',
    re.DOTALL
)


def _unquote(s):
    if s and s.startswith('"') and s.endswith('"'):
        return s[1:-1].replace('\\"', '"').replace("\\n", "\n")
    return s


def _fields(body):
    """Parse `key = "value"` pairs out of an annotation body, joining
    string concatenations like `"a " + "b"`."""
    # Collapse `\n          "more" +` continuations
    body = re.sub(r'"\s*\+\s*"', '', body)
    out = {}
    for k, v in RE_FIELD.findall(body):
        out[k] = _unquote(v)
    return out


def parse_method_block(text, op_match):
    """For a matched @Operation, walk the immediately-following method
    signature line to extract @Param entries."""
    # The @Operation regex consumes up to and including the opening `(`
    # of the method signature, so op_match.end() is already INSIDE the
    # parameter list. Scan forward with paren_depth=1 until we close it.
    sig_start = op_match.end()
    paren_depth = 1
    j = sig_start
    while j < len(text) and paren_depth > 0:
        if text[j] == '(':   paren_depth += 1
        elif text[j] == ')': paren_depth -= 1
        j += 1
    signature = text[sig_start:j - 1]
    params = []
    for pm in RE_PARAM_BLOCK.finditer(signature):
        f = _fields(pm.group("body"))
        params.append({
            "name":     f.get("value", ""),
            "required": f.get("required", "false") == "true",
            "varname":  pm.group("varname"),
        })
    return op_match.group("method"), params


def parse_responses(s):
    if not s: return []
    out = []
    for m in re.finditer(r'@Response\s*\(\s*([^)]*)\s*\)', s):
        f = _fields(m.group(1))
        out.append({
            "code":  f.get("responseCode", "?"),
            "desc":  f.get("description", ""),
        })
    return out


def extract_provider(java_file):
    """Returns a list of endpoint dicts."""
    src = (ROOT / java_file).read_text()
    out = []
    for m in RE_OPERATION.finditer(src):
        body = m.group("body")
        f = _fields(body)
        method, params = parse_method_block(src, m)
        responses = parse_responses(m.group("responses") or "")
        out.append({
            "method":   method,
            "path":     f.get("path", ""),
            "type":     f.get("type", "").split(".")[-1] or "GET",
            "summary":  f.get("summary", ""),
            "description": f.get("description", ""),
            "params":   params,
            "responses": responses,
        })
    return out


# ─────────────────────────────────────────────────────────────────────────
# Render
# ─────────────────────────────────────────────────────────────────────────
PREAMBLE = """# Farmers Portal — API reference

**Last regenerated**: auto-built by `tooling/build_api_reference.py` from the @Operation annotations across `plugins/`. Re-run that script after adding or changing endpoints.

## Audience

This document is the integration-handover reference for any team building against the Farmers Portal back-end — typically the MAFSN systems team, an external partner consuming the API, or anyone scripting against the platform for testing or migration.

## Authentication

Every endpoint listed below is gated by Joget's **API Builder**: two header values, `api_id` (the per-API UUID) and `api_key` (shared across the app's APIs in this instance).

The credentials in this dev instance:

| Header | Value |
|---|---|
| `api_key` | `<JOGET_API_KEY>` (set via env var; see `~/IdeaProjects/rsr/secrets/lst-credentials.txt` for dev) |

The `api_id` is per-API — see the `Auth` column of each provider section below. In production this changes per environment; pull from the Joget `app_builder` table:

```sql
SELECT id AS api_id, name FROM app_builder WHERE type='api' AND name='<api-name>';
```

### Calling conventions

```bash
curl -X POST -H "Content-Type: application/json" \\
  -H "api_id: API-XXX" -H "api_key: <key>" \\
  -d '{...request body...}' \\
  'http://<joget-host>/jw/api/<path>'
```

Alternatively, pass credentials as query parameters: `?api_id=API-XXX&api_key=<key>`. The headers form is preferred.

### Response envelope

API Builder wraps every response in:

```json
{
  "date":    "Mon May 11 21:01:21 UTC 2026",
  "code":    "200",
  "message": "<original response body, JSON-encoded as a string>"
}
```

JavaScript clients must unwrap the envelope before parsing the message:

```js
const env = await response.json();
const payload = typeof env.message === 'string'
  ? JSON.parse(env.message)
  : env;
```

This is the pattern every dashboard HtmlPage in this app uses.

### Bad auth

If you get `HTTP 400 Bad Request` with a generic `code: "400"` envelope, the most likely cause is an unknown or mistyped `api_id`. The API Builder rejects before the handler runs, so it looks like a body validation problem when it's really an auth/route problem.

---

"""


def render_provider(title, path, prefix, api_id, endpoints):
    lines = []
    lines.append(f"## {title}\n")
    lines.append(f"**URL prefix**: `{prefix}` &nbsp;·&nbsp; "
                 f"**`api_id`**: `{api_id}`\n")
    lines.append(f"**Source**: [`{path}`](../{path})\n")
    lines.append(f"**Endpoints**: {len(endpoints)}\n")

    if endpoints:
        lines.append("\n| Method | Path | Summary |\n|---|---|---|")
        for e in endpoints:
            sm = (e['summary'] or '').replace('|', '\\|')[:80]
            lines.append(f"| `{e['type']}` | `{e['path']}` | {sm} |")
        lines.append("")

    for e in endpoints:
        lines.append(f"\n### `{e['type']} {e['path']}`\n")
        if e["summary"]:
            lines.append(f"**{e['summary']}**\n")
        if e["description"]:
            lines.append(f"{e['description']}\n")
        if e["params"]:
            lines.append("\n**Parameters:**\n")
            lines.append("| Name | Required | Source variable |\n|---|---|---|")
            for p in e["params"]:
                req = "yes" if p["required"] else "no"
                lines.append(f"| `{p['name']}` | {req} | `{p['varname']}` |")
        if e["responses"]:
            lines.append("\n**Responses:**\n")
            for r in e["responses"]:
                lines.append(f"- `{r['code']}` — {r['desc']}")
        lines.append("\n---")
    return "\n".join(lines)


def main():
    sections = []
    total_endpoints = 0
    for title, path, prefix, api_id in PROVIDERS:
        try:
            endpoints = extract_provider(path)
        except FileNotFoundError:
            print(f"WARN: {path} not found, skipping", file=sys.stderr)
            continue
        sections.append(render_provider(title, path, prefix, api_id, endpoints))
        total_endpoints += len(endpoints)
        print(f"  {title}: {len(endpoints)} endpoints")

    body = PREAMBLE + "\n\n".join(sections) + "\n"
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(body)
    print(f"\nWrote {OUT.relative_to(ROOT)} — {total_endpoints} endpoints "
          f"across {len(sections)} providers, {len(body)} bytes.")


if __name__ == "__main__":
    main()
