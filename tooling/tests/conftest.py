"""
Shared pytest fixtures for the regression test suite.

Per `docs/architecture/test_strategy.md`: this is the foundation Layer-1+2 tests reuse.
Connection details mirror `tooling/test_budget_suite.py` and
`tooling/test_im_e2e.py` — same env var precedence, same defaults.
"""
import json
import os
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional

import psycopg2
import pytest

# ---------------------------------------------------------------------------
# Configuration — mirror the rest of _tooling
# ---------------------------------------------------------------------------

PG_HOST     = os.environ.get("PGHOST",     "joget-pgsql-sa.postgres.database.azure.com")
PG_DATABASE = os.environ.get("PGDATABASE", "jogetdb")
PG_USER     = os.environ.get("PGUSER",     "jogetadmin")
PG_PASSWORD = os.environ.get("PGPASSWORD", "Joget@DB#2026!")

JOGET_BASE_URL    = os.environ.get("JOGET_BASE_URL", "http://20.87.213.78:8080/jw")
JOGET_API_KEY     = os.environ.get("JOGET_API_KEY",  "a5af1181f77b4a62b481725b6410e965")
FORMCREATOR_API_ID = os.environ.get("FORMCREATOR_API_ID", "API-e7878006-c15a-425e-9c36-bebc7c4d085c")
REGBB_API_ID      = os.environ.get("REGBB_API_ID",        "API-168e3678-1f9a-46fc-8c19-d0d9a917eb73")
BUDGET_API_ID     = os.environ.get("BUDGET_API_ID",       "API-BUDGET")
APP_ID            = os.environ.get("JOGET_APP_ID", "farmersPortal")


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def pg_conn():
    """One DB connection for the whole test session. autocommit on so we
    don't need to manage transactions in read-only tests."""
    conn = psycopg2.connect(host=PG_HOST, dbname=PG_DATABASE,
                            user=PG_USER, password=PG_PASSWORD,
                            connect_timeout=10)
    conn.autocommit = True
    yield conn
    conn.close()


@pytest.fixture
def cur(pg_conn):
    """Fresh cursor per test."""
    cur = pg_conn.cursor()
    yield cur
    cur.close()


# ---------------------------------------------------------------------------
# Helpers — used by tests; not fixtures, just imports
# ---------------------------------------------------------------------------

def http_get(path: str, api_id: str, params: Optional[Dict[str, str]] = None,
             timeout: int = 15) -> tuple[int, str]:
    """GET against any /jw/api/* endpoint. Returns (status, body)."""
    qs = ""
    if params:
        from urllib.parse import urlencode
        qs = "?" + urlencode(params)
    url = JOGET_BASE_URL + path + qs
    req = urllib.request.Request(url, method="GET", headers={
        "api_id": api_id, "api_key": JOGET_API_KEY,
    })
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return 0, str(e)


def http_post(path: str, api_id: str, body: Optional[Dict[str, Any]] = None,
              timeout: int = 15) -> tuple[int, str]:
    """POST against any /jw/api/* endpoint. Returns (status, body)."""
    url = JOGET_BASE_URL + path
    data = json.dumps(body or {}).encode("utf-8")
    req = urllib.request.Request(url, method="POST", data=data, headers={
        "Content-Type": "application/json",
        "api_id": api_id, "api_key": JOGET_API_KEY,
    })
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return 0, str(e)


def fetch_userview_json(cur) -> dict:
    """Read the live userview JSON from app_userview."""
    cur.execute("SELECT json FROM app_userview WHERE appid=%s AND id='v'", (APP_ID,))
    row = cur.fetchone()
    assert row, f"userview 'v' not found for appId={APP_ID}"
    return json.loads(row[0])


def fetch_form_ids(cur) -> set[str]:
    """All form IDs in the app."""
    cur.execute("SELECT formid FROM app_form WHERE appid=%s", (APP_ID,))
    return {r[0] for r in cur.fetchall()}


def fetch_datalist_ids(cur) -> set[str]:
    """All datalist IDs in the app."""
    cur.execute("SELECT id FROM app_datalist WHERE appid=%s", (APP_ID,))
    return {r[0] for r in cur.fetchall()}
