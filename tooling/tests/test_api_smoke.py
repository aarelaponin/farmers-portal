"""
Layer 2 — API endpoint smoke.

For every published REST endpoint we own, asserts the endpoint returns
the documented status code (200 for valid input; documented 4xx for
bad input). Catches: api_id misconfiguration, missing route, plugin not
deployed, broken handler.

The endpoint inventory below is what the project maintains. When a new
endpoint ships, add it here.
"""
import json

import pytest

from conftest import (
    http_get, http_post,
    REGBB_API_ID, BUDGET_API_ID, FORMCREATOR_API_ID,
)


# ---------------------------------------------------------------------------
# Endpoint inventory — verified May 2026
# ---------------------------------------------------------------------------

def test_regbb_eval_with_invalid_payload_returns_documented_error():
    """POST /regbb/eval with a missing applicationId should return a
    structured error envelope, not a 5xx."""
    status, body = http_post("/api/regbb/eval", REGBB_API_ID, body={})
    # Documented behaviour: API Builder envelope wraps the handler's response.
    # Even on error, status is 200 with errors map per the migration guide.
    assert status in (200, 400, 422), (
        f"Unexpected status {status}; body[:200]={body[:200]!r}"
    )


def test_regbb_mdm_list_returns_donors():
    """GET /regbb/mdm/list?formId=md_donor should return the seeded donors."""
    status, body = http_get("/api/regbb/mdm/list", REGBB_API_ID,
                            params={"formId": "md_donor"})
    assert status == 200, f"status={status}, body[:200]={body[:200]!r}"
    # Response is an API-Builder envelope: {"date","code","message":"..."}.
    # The message is itself a JSON-encoded string.
    payload = json.loads(body)
    inner_raw = payload.get("message", body)
    inner = json.loads(inner_raw) if isinstance(inner_raw, str) else inner_raw
    rows = inner.get("rows", [])
    assert len(rows) >= 4, (
        f"expected ≥4 donor rows, got {len(rows)}; first={rows[:1]}"
    )
    codes = {r.get("code") for r in rows}
    assert "WFP" in codes, f"WFP missing from donor list: {sorted(codes)}"


def test_regbb_mdm_list_rejects_non_md_form():
    """GET /regbb/mdm/list?formId=im_voucher should be rejected — the
    endpoint whitelist only allows md_* forms."""
    status, body = http_get("/api/regbb/mdm/list", REGBB_API_ID,
                            params={"formId": "im_voucher"})
    # Should not return data; either 4xx or 200 with empty result.
    if status == 200:
        payload = json.loads(body)
        inner_raw = payload.get("message", body)
        inner = json.loads(inner_raw) if isinstance(inner_raw, str) else inner_raw
        rows = inner.get("rows", [])
        assert len(rows) == 0, (
            f"non-MD form should return 0 rows; got {len(rows)}"
        )


def test_budget_timeseries_returns_data():
    """GET /budget/timeseries returns event-time-series data for a known envelope."""
    status, body = http_get(
        "/api/budget/timeseries", BUDGET_API_ID,
        params={"envelopeCode": "ENV_PRG_2025_001_FY2526", "days": "30"}
    )
    assert status == 200, f"status={status}, body[:200]={body[:200]!r}"
    # Body is an API-Builder envelope; payload is JSON inside `message`.
    payload = json.loads(body)
    assert "message" in payload or "data" in payload, (
        f"unexpected response shape: {body[:200]!r}"
    )


def test_formcreator_seed_rejects_invalid_payload():
    """POST /formcreator/seed without 'fixtures' should return a
    structured 400 (validation error), not a 500."""
    status, body = http_post(
        "/api/formcreator/formcreator/seed", FORMCREATOR_API_ID,
        body={"formId": "md_donor", "rows": []}  # wrong shape on purpose
    )
    assert status == 400, f"expected 400 on bad payload, got {status}"
    assert "fixtures" in body.lower() or "validation" in body.lower(), (
        f"error message should mention the missing field; got {body[:200]!r}"
    )


def test_formcreator_seed_accepts_valid_payload():
    """POST /formcreator/seed with a valid envelope should accept."""
    status, body = http_post(
        "/api/formcreator/formcreator/seed", FORMCREATOR_API_ID,
        body={
            "fixtures": [{
                "formId": "md_donor", "tableName": "md_donor",
                "rows": [{"code": "TEST_SMOKE_DROP", "name": "smoke-test, safe to delete"}]
            }]
        }
    )
    assert status == 200, f"status={status}, body[:300]={body[:300]!r}"


# ---------------------------------------------------------------------------
# Auth-rejection tests
# ---------------------------------------------------------------------------

def test_regbb_endpoint_rejects_bad_api_id():
    """GET /regbb/mdm/list with the wrong api_id should be rejected."""
    status, body = http_get("/api/regbb/mdm/list", "API-NOT-A-REAL-ID",
                            params={"formId": "md_donor"})
    assert status in (400, 401, 403), (
        f"expected 4xx with bad api_id; got {status}, body={body[:200]!r}"
    )


def test_unknown_endpoint_returns_404():
    """An entirely fictitious endpoint should 404."""
    status, body = http_get("/api/regbb/this-endpoint-does-not-exist", REGBB_API_ID)
    assert status in (400, 404), (
        f"expected 4xx for unknown endpoint; got {status}, body={body[:200]!r}"
    )
