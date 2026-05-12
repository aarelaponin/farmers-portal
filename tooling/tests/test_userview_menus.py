"""
Layer 1 — userview menu hygiene.

For every CrudMenu / DataListMenu in the live userview, assert that the
form / datalist it references actually exists in the app's metadata.

Catches the dead-menu class of bugs documented in CLAUDE.md
("Userview menu hygiene — every CrudMenu must have its form AND datalist
alive"). Verified May 2026 — that audit found 1 dead menu (MD.38 - Input
Category pointing at a never-authored datalist). This test would have
caught it on the first push.
"""
import pytest

from conftest import fetch_userview_json, fetch_form_ids, fetch_datalist_ids


def collect_menu_refs(uv: dict) -> list[dict]:
    """Walk the userview's category tree, returning every menu with its
    referenced form / datalist IDs flattened for inspection."""
    out = []
    for cat in uv.get("categories", []):
        cat_label = cat.get("properties", {}).get("label", "?")
        for menu in cat.get("menus", []):
            p = menu.get("properties", {})
            out.append({
                "category": cat_label,
                "label": p.get("label", "?"),
                "className": menu.get("className", "?"),
                "editFormId": p.get("editFormId"),
                "addFormId": p.get("addFormId"),
                "datalistId": p.get("datalistId"),
                "formDefId": p.get("formDefId"),
            })
    return out


def test_userview_loads(cur):
    """Smoke — the userview JSON parses and has categories."""
    uv = fetch_userview_json(cur)
    assert uv.get("categories"), "userview has no categories"
    assert len(uv["categories"]) >= 5, f"only {len(uv['categories'])} categories — too few"


def test_every_crud_menu_has_live_form_and_datalist(cur):
    """Every CrudMenu must reference forms + datalists that exist."""
    uv = fetch_userview_json(cur)
    forms = fetch_form_ids(cur)
    datalists = fetch_datalist_ids(cur)
    menus = collect_menu_refs(uv)

    dead = []
    for m in menus:
        if "CrudMenu" not in m["className"]:
            continue
        # editFormId should resolve to a live form
        if m["editFormId"] and m["editFormId"] not in forms:
            dead.append(("editFormId", m["editFormId"], m["category"], m["label"]))
        # addFormId should resolve (often same as editFormId)
        if m["addFormId"] and m["addFormId"] not in forms:
            dead.append(("addFormId", m["addFormId"], m["category"], m["label"]))
        # datalistId should resolve
        if m["datalistId"] and m["datalistId"] not in datalists:
            dead.append(("datalistId", m["datalistId"], m["category"], m["label"]))

    if dead:
        msg = "\n".join(f"  {kind}={ref!r} on menu {label!r} in category {cat!r}"
                        for kind, ref, cat, label in dead)
        pytest.fail(f"{len(dead)} dead reference(s) in CrudMenu menus:\n{msg}")


def test_every_datalist_menu_has_live_datalist(cur):
    """Every DataListMenu must reference a datalist that exists."""
    uv = fetch_userview_json(cur)
    datalists = fetch_datalist_ids(cur)
    menus = collect_menu_refs(uv)

    dead = []
    for m in menus:
        if "DataListMenu" not in m["className"]:
            continue
        if m["datalistId"] and m["datalistId"] not in datalists:
            dead.append((m["datalistId"], m["category"], m["label"]))

    if dead:
        msg = "\n".join(f"  datalistId={ref!r} on menu {label!r} in category {cat!r}"
                        for ref, cat, label in dead)
        pytest.fail(f"{len(dead)} DataListMenu(s) point at missing datalist(s):\n{msg}")


def test_no_orphan_html_pages(cur):
    """HtmlPage menus don't reference forms / datalists, but they should
    have content. Quick sanity that no HtmlPage is empty."""
    uv = fetch_userview_json(cur)
    empty = []
    for cat in uv.get("categories", []):
        for menu in cat.get("menus", []):
            if "HtmlPage" in menu.get("className", ""):
                content = menu.get("properties", {}).get("content", "")
                if not content or len(content) < 20:
                    empty.append(menu.get("properties", {}).get("label", "?"))
    if empty:
        pytest.fail(f"{len(empty)} HtmlPage(s) with empty / trivial content: {empty}")
