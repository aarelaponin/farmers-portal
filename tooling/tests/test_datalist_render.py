"""
Layer 2 — Datalist render smoke.

For every JdbcDataListBinder datalist: asserts the SQL parses and
executes against the live database. Catches the class of bugs where a
column is renamed in a form but the datalist SQL still references the
old name (silent until an operator opens the screen).

For AdvancedFormRowDataListBinder datalists: asserts the binder's
formDefId resolves to a live form.
"""
import json
import re

import psycopg2
import pytest

from conftest import (
    APP_ID, PG_HOST, PG_DATABASE, PG_USER, PG_PASSWORD,
)


# Tokens like #requestParam.q?sql# need a value to make the SQL valid.
TOKEN_RE = re.compile(r"#[^#\s]+#")


def _fetch_all_datalists():
    """Module-load-time fetch for parametrize. Uses its own short-lived
    connection so the session-scoped pg_conn fixture isn't needed at
    collection time."""
    conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE,
                            user=PG_USER, password=PG_PASSWORD,
                            connect_timeout=10)
    try:
        cur = conn.cursor()
        cur.execute("SELECT id, json FROM app_datalist WHERE appid=%s ORDER BY id",
                    (APP_ID,))
        rows = [(r[0], json.loads(r[1])) for r in cur.fetchall()]
        cur.close()
        return rows
    finally:
        conn.close()


_ALL_DATALISTS = _fetch_all_datalists()
_JDBC_DATALISTS = [
    (dl_id, dl_json) for dl_id, dl_json in _ALL_DATALISTS
    if "JdbcDataListBinder" in dl_json.get("binder", {}).get("className", "")
]
_ADV_DATALISTS = [
    (dl_id, dl_json) for dl_id, dl_json in _ALL_DATALISTS
    if "AdvancedFormRowDataListBinder" in dl_json.get("binder", {}).get("className", "")
]


def test_datalist_count_sane():
    """Sanity check: the app has a meaningful number of datalists."""
    assert len(_ALL_DATALISTS) >= 30, (
        f"only {len(_ALL_DATALISTS)} datalists; project should have ≥30"
    )


@pytest.mark.parametrize("dl_id,dl_json", _JDBC_DATALISTS,
                         ids=[r[0] for r in _JDBC_DATALISTS])
def test_jdbc_datalist_sql_parses(pg_conn, dl_id, dl_json):
    """JdbcDataListBinder SQL must parse + execute against the DB."""
    sql = dl_json["binder"]["properties"].get("sql", "").strip()
    if not sql:
        pytest.fail(f"{dl_id}: JdbcDataListBinder has empty SQL")

    # Substitute Joget runtime tokens with empty strings so SQL parses.
    cleaned = TOKEN_RE.sub("", sql)

    # Add LIMIT 1 if absent — confirm shape, don't pull whole table.
    if " LIMIT " not in cleaned.upper():
        cleaned = cleaned.rstrip("; \n") + " LIMIT 1"

    cur = pg_conn.cursor()
    try:
        cur.execute(cleaned)
        cur.fetchall()
    except Exception as e:
        pytest.fail(
            f"{dl_id}: SQL failed:\n  {type(e).__name__}: {str(e)[:300]}"
        )
    finally:
        cur.close()


def test_advanced_form_binder_target_forms_exist(cur):
    """AdvancedFormRowDataListBinder formDefId must resolve to a live form."""
    cur.execute("SELECT formid FROM app_form WHERE appid=%s", (APP_ID,))
    forms = {r[0] for r in cur.fetchall()}

    broken = []
    for dl_id, dl_json in _ADV_DATALISTS:
        form_def_id = dl_json["binder"]["properties"].get("formDefId")
        if not form_def_id:
            broken.append((dl_id, "missing formDefId"))
        elif form_def_id not in forms:
            broken.append((dl_id, f"references missing form '{form_def_id}'"))

    if broken:
        msg = "\n".join(f"  {dl_id}: {issue}" for dl_id, issue in broken)
        pytest.fail(f"{len(broken)} datalist(s) reference missing forms:\n{msg}")


def test_datalist_count_summary():
    """Print a summary of what was checked. Not really an assertion, just
    visibility into the inventory the test discovered."""
    print(
        f"\n  Total datalists: {len(_ALL_DATALISTS)}\n"
        f"  JdbcDataListBinder (SQL-checked): {len(_JDBC_DATALISTS)}\n"
        f"  AdvancedFormRowDataListBinder (form-checked): {len(_ADV_DATALISTS)}\n"
        f"  Other binders (skipped): "
        f"{len(_ALL_DATALISTS) - len(_JDBC_DATALISTS) - len(_ADV_DATALISTS)}"
    )
