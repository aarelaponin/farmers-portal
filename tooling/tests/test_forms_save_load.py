"""
Layer 1 — Form save/load round-trip.

For a sample of representative forms, asserts:
- Seeding a row via formcreator/seed succeeds.
- The seeded row exists in the underlying table.
- The system metadata (datecreated, datemodified, createdby, modifiedby)
  is populated — this is the regression test for task #235 (timestamp
  fix shipped in build-112 / build-22).
- Cleanup: the test row is deleted at end of test.

The forms exercised are master-data + analyst-authored forms only —
NEVER the registry tables (farmerbasicinfo, parcelregistration, etc.)
per CLAUDE.md HARD RULE on customer-owned registry data.
"""
import json
import time
import urllib.request
import urllib.error
import uuid

import pytest

from conftest import (
    JOGET_BASE_URL, JOGET_API_KEY, FORMCREATOR_API_ID, http_post,
)


# Forms safe for save/load round-trip — all are analyst-authored config
# or master-data. Each entry: (formId, tableName, sample row payload).
SAFE_FORMS = [
    (
        "md_donor", "md_donor",
        {"code": "ROUNDTRIP_DONOR", "name": "round-trip test donor",
         "country": "test", "focal_point": "test", "contact_email": ""}
    ),
    (
        "md_input_unit", "md_input_unit",
        {"code": "ROUNDTRIP_UNIT", "name": "round-trip test unit"}
    ),
    (
        "md_voucher_status", "md_voucher_status",
        {"code": "ROUNDTRIP_STATUS", "name": "round-trip test status"}
    ),
]


def _seed(form_id, table_name, row):
    body = {"fixtures": [{
        "formId": form_id, "tableName": table_name, "rows": [row]
    }]}
    return http_post("/api/formcreator/formcreator/seed",
                     FORMCREATOR_API_ID, body=body)


def _delete(form_id, code, cur):
    """Delete via the form-creator clear endpoint when it exists; else
    fall back to a single-row DELETE bracketed by `c_code = 'X'` since
    these are safe (analyst-authored, not registry) per HARD RULE."""
    cur.execute(
        f"DELETE FROM app_fd_{form_id} WHERE c_code = %s", (code,)
    )
    cur.connection.commit()


@pytest.fixture(autouse=True)
def cleanup_test_rows(pg_conn):
    """Always run — pre-clean any leftover test rows from a prior run,
    then run the test, then post-clean."""
    cur = pg_conn.cursor()
    for form_id, table_name, row in SAFE_FORMS:
        try:
            cur.execute(
                f"DELETE FROM app_fd_{table_name} WHERE c_code = %s",
                (row["code"],)
            )
        except Exception:
            pass
    pg_conn.commit()
    cur.close()
    yield
    cur = pg_conn.cursor()
    for form_id, table_name, row in SAFE_FORMS:
        try:
            cur.execute(
                f"DELETE FROM app_fd_{table_name} WHERE c_code = %s",
                (row["code"],)
            )
        except Exception:
            pass
    pg_conn.commit()
    cur.close()


@pytest.mark.parametrize("form_id,table_name,row", SAFE_FORMS,
                         ids=[f[0] for f in SAFE_FORMS])
def test_seed_persists_row(pg_conn, form_id, table_name, row):
    """Seed → row exists in DB."""
    status, body = _seed(form_id, table_name, row)
    assert status == 200, f"seed failed: status={status}, body={body[:200]}"

    cur = pg_conn.cursor()
    cur.execute(f"SELECT c_code, c_name FROM app_fd_{table_name} "
                f"WHERE c_code = %s", (row["code"],))
    found = cur.fetchone()
    cur.close()
    assert found is not None, f"seeded row not found in app_fd_{table_name}"
    assert found[0] == row["code"]
    assert found[1] == row.get("name")


@pytest.mark.parametrize("form_id,table_name,row", SAFE_FORMS,
                         ids=[f[0] for f in SAFE_FORMS])
def test_seed_populates_system_metadata(pg_conn, form_id, table_name, row):
    """Regression for task #235: datecreated, datemodified, createdby,
    modifiedby must be populated on the seeded row. Was systemic NULL
    before build-22 of form-creator-api."""
    status, body = _seed(form_id, table_name, row)
    assert status == 200

    cur = pg_conn.cursor()
    cur.execute(
        f"""SELECT datecreated, datemodified, createdby, modifiedby
            FROM app_fd_{table_name} WHERE c_code = %s""",
        (row["code"],)
    )
    found = cur.fetchone()
    cur.close()
    assert found is not None
    dc, dm, cb, mb = found
    assert dc is not None, f"datecreated NULL on seeded {form_id} row"
    assert dm is not None, f"datemodified NULL on seeded {form_id} row"
    assert cb, f"createdby empty on seeded {form_id} row"
    assert mb, f"modifiedby empty on seeded {form_id} row"


def test_seed_idempotent_on_business_key(pg_conn):
    """Seeding the same row twice should not produce a duplicate — the
    seeder upserts on the business key (c_code)."""
    form_id, table_name, row = SAFE_FORMS[0]
    s1, _ = _seed(form_id, table_name, row)
    assert s1 == 200
    # Second seed — same code, different name.
    row2 = dict(row); row2["name"] = "round-trip test donor (v2)"
    s2, _ = _seed(form_id, table_name, row2)
    assert s2 == 200

    cur = pg_conn.cursor()
    cur.execute(
        f"SELECT count(*), max(c_name) FROM app_fd_{table_name} "
        f"WHERE c_code = %s", (row["code"],)
    )
    n, name_now = cur.fetchone()
    cur.close()
    assert n == 1, f"seed not idempotent: {n} rows for code {row['code']}"
    assert name_now == "round-trip test donor (v2)", (
        f"second seed should have updated the name; got {name_now!r}"
    )
