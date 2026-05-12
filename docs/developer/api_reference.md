# Farmers Portal — API reference

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
curl -X POST -H "Content-Type: application/json" \
  -H "api_id: API-XXX" -H "api_key: <key>" \
  -d '{...request body...}' \
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

## form-creator-api

**URL prefix**: `/jw/api/formcreator` &nbsp;·&nbsp; **`api_id`**: `API-e7878006-c15a-425e-9c36-bebc7c4d085c`

**Source**: [`plugins/form-creator-api/src/main/java/global/govstack/formcreator/lib/FormCreatorServiceProvider.java`](../plugins/form-creator-api/src/main/java/global/govstack/formcreator/lib/FormCreatorServiceProvider.java)

**Endpoints**: 8


| Method | Path | Summary |
|---|---|---|
| `POST` | `/formcreator/forms` | Create a new form from JSON definition or file upload |
| `POST` | `/formcreator/seed` | Bulk-upsert test fixture rows by business key |
| `POST` | `/formcreator/clear` | Bulk-delete all rows from the listed forms |
| `POST` | `/formcreator/datalists` | Upsert a datalist definition by id |
| `POST` | `/formcreator/userviews` | Upsert a userview definition by id |
| `POST` | `/formcreator/apis` | Upsert an API Builder API definition for an existing form |
| `POST` | `/formcreator/apis/delete` | Bulk-delete API Builder API definitions by id |
| `GET` | `/formcreator/data/list` | Fetch a Joget datalist's rows as JSON (ROLE_USER-accessible) |


### `POST /formcreator/forms`

**Create a new form from JSON definition or file upload**

Creates a Joget form based on provided JSON definition and metadata. Supports both JSON (application/json) and file upload (multipart/form-data). Optionally creates API endpoint and CRUD interface. Requires formId, formName, tableName, and formDefinition (or formDefinitionFile).


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `appId` | no | `appId` |
| `appVersion` | no | `appVersion` |
| `request` | no | `request` |
| `body` | no | `requestBody` |

**Responses:**

- `200` — Form created successfully
- `400` — Invalid request - validation failed
- `500` — Server error during form creation

---

### `POST /formcreator/seed`

**Bulk-upsert test fixture rows by business key**

Upserts each row keyed by its business code; preserves Joget UUID id on update.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Seed results returned
- `400` — Invalid request
- `500` — Server error during seeding

---

### `POST /formcreator/clear`

**Bulk-delete all rows from the listed forms**

Drops every row in each formId given, in caller-provided order.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Clear results returned
- `400` — Invalid request
- `500` — Server error during clear

---

### `POST /formcreator/datalists`

**Upsert a datalist definition by id**

Inserts or updates a datalist's JSON via DatalistDefinitionDao. Idempotent.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Upsert result returned
- `400` — Invalid request
- `500` — Server error during upsert

---

### `POST /formcreator/userviews`

**Upsert a userview definition by id**

Inserts or updates a userview's JSON via UserviewDefinitionDao. Idempotent.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Upsert result returned
- `400` — Invalid request
- `500` — Server error during upsert

---

### `POST /formcreator/apis`

**Upsert an API Builder API definition for an existing form**

Inserts or updates an app_builder API row via BuilderDefinitionDao. Idempotent: re-running with the same code updates the existing row rather than creating a duplicate. The form must already exist.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Upsert result returned
- `400` — Invalid request
- `500` — Server error during upsert

---

### `POST /formcreator/apis/delete`

**Bulk-delete API Builder API definitions by id**

Calls BuilderDefinitionDao.delete() per id. Idempotent: ids that do not exist or have already been deleted are reported in 'skipped'.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Deletion result
- `400` — Invalid request
- `500` — Server error during delete

---

### `GET /formcreator/data/list`

**Fetch a Joget datalist's rows as JSON (ROLE_USER-accessible)**

Companion to /jw/web/json/data/list but gated by API-Builder credentials, so userview HtmlPages can call it from non-admin user sessions. Returns the same {total, data[]} shape.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `appId` | yes | `appId` |
| `listId` | yes | `listId` |
| `start` | no | `startStr` |
| `rows` | no | `rowsStr` |

**Responses:**

- `200` — Datalist rows
- `400` — Missing appId or listId
- `404` — App or datalist not found
- `500` — Server error during list load

---

## reg-bb-engine — RegBb evaluation

**URL prefix**: `/jw/api/regbb` &nbsp;·&nbsp; **`api_id`**: `API-168e3678-1f9a-46fc-8c19-d0d9a917eb73`

**Source**: [`plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/api/RegBbEvalApi.java`](../plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/api/RegBbEvalApi.java)

**Endpoints**: 5


| Method | Path | Summary |
|---|---|---|
| `POST` | `/eval` | Evaluate one determinant; return outcome (TRUE/FALSE/NULL/ERROR) |
| `POST` | `/submit` | Submit an application; persist; evaluate; persist outcome |
| `POST` | `/catalogue/eval` | Live in-flight evaluation of every programme in a service |
| `POST` | `/bot_pull/eval` | Resolve a bot_pull mm_action against a trigger value |
| `GET` | `/mdm/list` | List all rows of a master-data form |


### `POST /eval`

**Evaluate one determinant; return outcome (TRUE/FALSE/NULL/ERROR)**

Per spec §8. No persistence — pure evaluation. Audited + cached.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Evaluation completed
- `400` — Invalid request
- `500` — Server error during evaluation

---

### `POST /submit`

**Submit an application; persist; evaluate; persist outcome**

Per spec §6.5. Returns the application id + outcome JSON.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Submission persisted; outcome computed
- `400` — Invalid request
- `500` — Server error during submission

---

### `POST /catalogue/eval`

**Live in-flight evaluation of every programme in a service**

L2-2-bis. JS in the catalogue tab posts the wizard's currently-typed form payload; kernel returns per-programme outcomes for client-side card refresh.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Outcomes returned
- `400` — Invalid request
- `500` — Server error

---

### `POST /bot_pull/eval`

**Resolve a bot_pull mm_action against a trigger value**

L2-3 / D11. Walks the action's fieldMappings via the L2-1 capability registry; returns target→value JSON for client-side form auto-fill.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Resolved values returned
- `400` — Invalid request
- `500` — Server error

---

### `GET /mdm/list`

**List all rows of a master-data form**

Returns all rows of a master-data form (formId must start with 'md'). For external integrators (e.g. pilot-data migration teams) who need to validate their data against the portal's MDM. Read-only; whitelisted to master-data forms only. Strips the 'c_' column prefix in output for cleanliness. Returns JSON: {"formId":"...","rowCount":N,"rows":[{"id":"...","code":"...","name":"..."}, ...]}.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `formId` | yes | `formId` |
| `limit` | no | `limitStr` |

**Responses:**

- `200` — Rows returned
- `400` — 
- `404` — Master-data table does not exist
- `500` — Server error

---

## reg-bb-engine — Budget engine

**URL prefix**: `/jw/api/budget` &nbsp;·&nbsp; **`api_id`**: `API-BUDGET`

**Source**: [`plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/api/BudgetApi.java`](../plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/api/BudgetApi.java)

**Endpoints**: 13


| Method | Path | Summary |
|---|---|---|
| `POST` | `/dispatch` | Dispatch a budget event (L3-1 1B-i) |
| `POST` | `/ces/estimate` | Cost Estimation Service — programme cost projection + launch gate (L3-1 1C) |
| `GET` | `/timeseries` | Per-day event counts + commitment sums for the dashboard sparkline |
| `POST` | `/run-projection-refresh` | Run BudgetProjectionRefreshJob on demand (ADR-030 Step 5) |
| `POST` | `/run-eligibility-worker` | Run EligibilityProcessingWorker on demand (ADR-030) |
| `POST` | `/run-threshold-monitor` | Run BudgetThresholdMonitor on demand (test + ops) |
| `POST` | `/issue-vouchers` | Issue IM vouchers for an approved subsidy application (Slice 4 — Phase E) |
| `POST` | `/redeem-voucher` | Redeem an issued voucher at a Resource Centre (Slice 5 — Phase G) |
| `POST` | `/cancel-voucher` | Cancel an issued voucher and release its budget COMMITMENT (Slice 10 — operator  |
| `POST` | `/expire-vouchers` | Sweep expired vouchers and release their budget COMMITMENT (Slice 9 — production |
| `POST` | `/send-pending-digest` | Fire the supervisor pending-decisions digest email (template 05) |
| `POST` | `/send-expiring-reminders` | Fire 7-day voucher expiry reminders (template 08) |
| `POST` | `/send-budget-alerts` | Fire budget-envelope 75% threshold alerts (template 11) |


### `POST /dispatch`

**Dispatch a budget event (L3-1 1B-i)**

Resolves an mm_action.kind=budget_event row, evaluates amount, posts balanced journal entries, refreshes projection. Idempotent on (actionCode + applicationId + eventType).


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — 
- `400` — Invalid request
- `500` — Server error during dispatch

---

### `POST /ces/estimate`

**Cost Estimation Service — programme cost projection + launch gate (L3-1 1C)**

Computes estimated_cost = expectedApplicantCount × per-applicant amount. Returns coverage_ratio_pct, source_breakdown (proration), and the outcome of every programme_launch_gate rule. Read-only; safe to call repeatedly.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `requestBody` |

**Responses:**

- `200` — Estimate returned
- `400` — Invalid request
- `500` — Server error

---

### `GET /timeseries`

**Per-day event counts + commitment sums for the dashboard sparkline**

Returns a time-series of budget activity for the last N days. If envelopeCode is supplied, scoped to that envelope; otherwise system-wide. Output: { days:[YYYY-MM-DD], events:[int], committed:[number] } — chart it as bars+line.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `envelopeCode` | no | `envelopeCode` |
| `days` | no | `daysStr` |

**Responses:**

- `200` — Series returned
- `500` — Server error

---

### `POST /run-projection-refresh`

**Run BudgetProjectionRefreshJob on demand (ADR-030 Step 5)**

Refreshes budget_projection + budget_projection_by_source materialised views CONCURRENTLY. Same logic as the scheduled job. Useful when operator needs an up-to-date dashboard without waiting for the next 30-second tick. Idempotent.


**Responses:**

- `200` — Refresh completed
- `500` — Server error

---

### `POST /run-eligibility-worker`

**Run EligibilityProcessingWorker on demand (ADR-030)**

Drains app_fd_processing_queue, runs the eligibility chain (eligibility evaluation + budget dispatch) for each pending row. Up to 50 rows per invocation; failures retry with exponential backoff; 5 attempts → dead-letter. Same logic as the scheduled worker. Returns a summary string. Idempotent — safe to call repeatedly.


**Responses:**

- `200` — Worker ran
- `500` — Server error

---

### `POST /run-threshold-monitor`

**Run BudgetThresholdMonitor on demand (test + ops)**

Scans every envelope's utilisation, posts WATCH/OVER/AUTO_FREEZE alerts, auto-freezes envelopes at 110%. Same logic as the scheduled job. Returns a summary string. Idempotent — safe to call repeatedly.


**Responses:**

- `200` — Monitor ran
- `500` — Server error

---

### `POST /issue-vouchers`

**Issue IM vouchers for an approved subsidy application (Slice 4 — Phase E)**

Reads the application by id, looks up the active im_allocation_plan for its applied programme, and writes one im_voucher row per matching allocation line. Idempotent — re-running for the same applicationId skips lines that already have a voucher. Pass force=true to bypass the approved/auto_approved status check (admin override; useful for testing).


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `applicationId` | yes | `applicationId` |
| `force` | no | `forceStr` |
| `actor` | no | `actorStr` |

**Responses:**

- `200` — Issuance result returned
- `400` — Invalid request
- `500` — Server error during issuance

---

### `POST /redeem-voucher`

**Redeem an issued voucher at a Resource Centre (Slice 5 — Phase G)**

Validates the voucher (status=issued, not expired, allocated point matches the redemptionPoint), then atomically writes a im_voucher_redemption row, flips voucher.status issued→redeemed, and decrements the matching im_inventory row's quantity_on_hand by the redeemed quantity. Returns JSON with status (ok / voucher_not_found / already_redeemed / wrong_status / expired / wrong_point / error) and the redemption code.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `voucherCode` | yes | `voucherCode` |
| `redemptionPoint` | yes | `redemptionPoint` |
| `redeemedBy` | yes | `redeemedBy` |
| `quantity` | no | `quantity` |

**Responses:**

- `200` — Redemption result returned
- `400` — Invalid request
- `500` — Server error during redemption

---

### `POST /cancel-voucher`

**Cancel an issued voucher and release its budget COMMITMENT (Slice 10 — operator action)**

Operator-triggered void of a voucher in 'issued' state. Reads the original COMMITMENT amount from app_fd_budget_event by idempotency_key='voucher_issued:CODE', dispatches RELEASE_COMMITMENT with idempotency_key='voucher_cancelled:CODE', then flips voucher status to 'cancelled' with the operator's reason annotated in notes. Returns JSON with status (ok / not_found / already_redeemed / already_expired / already_cancelled / wrong_status / error) and the released amount. Idempotent — calling twice returns 'already_cancelled' on the second call.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `voucherCode` | yes | `voucherCode` |
| `reason` | yes | `reason` |
| `actor` | no | `actor` |

**Responses:**

- `200` — Cancellation result returned
- `400` — 
- `500` — Server error during cancellation

---

### `POST /expire-vouchers`

**Sweep expired vouchers and release their budget COMMITMENT (Slice 9 — production hardening)**

Walks app_fd_im_voucher for rows where status='issued' and expiry_date is in the past. For each, looks up the original COMMITMENT amount from app_fd_budget_event by idempotency_key='voucher_issued:CODE', then dispatches RELEASE_COMMITMENT (envelope .AVAILABLE += amount; .COMMITTED -= amount) with idempotency_key='voucher_expired:CODE'. Voucher status flips to 'expired'. Idempotent — running the sweep repeatedly produces no duplicate budget events. Designed to run on a daily schedule via a Joget workflow tool step; this endpoint is the ad-hoc operator trigger. Returns JSON with scanned/flipped/released/releaseSkipped counts.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `actor` | no | `actor` |

**Responses:**

- `200` — Sweep result returned
- `500` — Server error during sweep

---

### `POST /send-pending-digest`

**Fire the supervisor pending-decisions digest email (template 05)**

Counts applications stuck in pending_review/pending_data_clarification for >24h, fires one digest email to the dev recipient (or in production, to all district supervisors). Idempotent — safe to run repeatedly; the email reflects current state.


**Responses:**

- `200` — Digest result

---

### `POST /send-expiring-reminders`

**Fire 7-day voucher expiry reminders (template 08)**

Finds vouchers where expiry_date is exactly 7 days away and status is issued/partially_redeemed, fires one reminder email per voucher. Schedule daily; idempotent within the day.


**Responses:**

- `200` — Reminder sweep result

---

### `POST /send-budget-alerts`

**Fire budget-envelope 75% threshold alerts (template 11)**

Computes (committed+expensed)/allocated per envelope; for any envelope in the 75-90% band (and not yet alerted), fires one alert email to the finance officer. Schedule hourly during active subsidy cycles.


**Responses:**

- `200` — Alert sweep result

---

## joget-gis-server — GIS calculations

**URL prefix**: `/jw/api/gis` &nbsp;·&nbsp; **`api_id`**: `(look up in app_builder where name='gis')`

**Source**: [`plugins/joget-gis-server/src/main/java/global/govstack/gisserver/lib/GisApiProvider.java`](../plugins/joget-gis-server/src/main/java/global/govstack/gisserver/lib/GisApiProvider.java)

**Endpoints**: 9


| Method | Path | Summary |
|---|---|---|
| `POST` | `/gis/calculate` | Calculate geometry metrics |
| `POST` | `/gis/validate` | Validate geometry |
| `POST` | `/gis/simplify` | Simplify geometry |
| `POST` | `/gis/checkOverlap` | Check for overlapping geometries |
| `GET` | `/gis/geocode` | Geocode location |
| `GET` | `/gis/reverseGeocode` | Reverse geocode |
| `GET` | `/gis/nearbyParcels` | Get nearby parcels for display |
| `GET` | `/gis/health` | Health check |
| `POST` | `/gis/batchCalculate` | Batch calculate geometry metrics |


### `POST /gis/calculate`

**Calculate geometry metrics**

Calculates area (hectares), perimeter (meters), centroid, and bounding box for a given GeoJSON polygon.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Calculation successful
- `400` — Invalid GeoJSON
- `422` — Invalid geometry type
- `500` — Server error

---

### `POST /gis/validate`

**Validate geometry**

Validates a GeoJSON polygon against configurable rules (min/max area, min/max vertices, self-intersection, holes, spikes).


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Validation completed
- `400` — Invalid GeoJSON
- `500` — Server error

---

### `POST /gis/simplify`

**Simplify geometry**

Reduces the number of vertices using Douglas-Peucker algorithm while preserving the overall shape.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Simplification successful
- `400` — Invalid GeoJSON
- `422` — Area change exceeds maximum
- `500` — Server error

---

### `POST /gis/checkOverlap`

**Check for overlapping geometries**

Checks if the given polygon overlaps with existing geometries in any Joget form.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Overlap check completed
- `400` — Invalid request
- `500` — Server error

---

### `GET /gis/geocode`

**Geocode location**

Search for a location by name and return coordinates (uses Nominatim/OpenStreetMap).


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `query` | no | `query` |
| `limit` | no | `limit` |
| `countryCode` | no | `countryCode` |
| `boundingBox` | no | `boundingBox` |

**Responses:**

- `200` — Geocoding successful
- `400` — Invalid query
- `503` — Geocoding service unavailable

---

### `GET /gis/reverseGeocode`

**Reverse geocode**

Get place name from coordinates (uses Nominatim/OpenStreetMap).


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `lon` | no | `lonParam` |
| `lat` | no | `latParam` |
| `zoom` | no | `zoom` |

**Responses:**

- `200` — Reverse geocoding successful
- `400` — Invalid coordinates
- `503` — Geocoding service unavailable

---

### `GET /gis/nearbyParcels`

**Get nearby parcels for display**

Retrieves parcels within a bounding box for read-only display context.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `formId` | yes | `formId` |
| `geometryFieldId` | yes | `geometryFieldId` |
| `bounds` | yes | `bounds` |
| `excludeRecordId` | no | `excludeRecordId` |
| `filterCondition` | no | `filterCondition` |
| `returnFields` | no | `returnFields` |
| `maxResults` | no | `maxResults` |

**Responses:**

- `200` — Parcels retrieved successfully
- `400` — Invalid parameters
- `429` — Rate limit exceeded
- `500` — Server error

---

### `GET /gis/health`

**Health check**

Returns service health status and version information.


**Responses:**

- `200` — Service is healthy

---

### `POST /gis/batchCalculate`

**Batch calculate geometry metrics**

Calculates metrics for multiple geometries in a single request.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Batch calculation completed
- `400` — Invalid request
- `500` — Server error

---

## joget-rules-api — Rule evaluation

**URL prefix**: `/jw/api/rules` &nbsp;·&nbsp; **`api_id`**: `(look up in app_builder where name='rules')`

**Source**: [`plugins/joget-rules-api/src/main/java/global/govstack/rulesapi/lib/RulesApiProvider.java`](../plugins/joget-rules-api/src/main/java/global/govstack/rulesapi/lib/RulesApiProvider.java)

**Endpoints**: 8


| Method | Path | Summary |
|---|---|---|
| `POST` | `/jre/validate` | Validate a Rules Script |
| `POST` | `/jre/compile` | Compile Rules Script to SQL |
| `GET` | `/jre/fields` | Get available field definitions |
| `GET` | `/jre/categories` | Get available field categories |
| `POST` | `/jre/fields/refresh` | Refresh field cache |
| `POST` | `/jre/saveRuleset` | Save a ruleset |
| `GET` | `/jre/loadRuleset` | Load a ruleset |
| `POST` | `/jre/publishRuleset` | Publish a ruleset |


### `POST /jre/validate`

**Validate a Rules Script**

Parses and validates a Rules Script, returning any errors or warnings. Does not save the rules to the database.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Validation completed
- `400` — Invalid request format
- `500` — Server error

---

### `POST /jre/compile`

**Compile Rules Script to SQL**

Parses and compiles a Rules Script to SQL queries for eligibility checking and scoring.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Compilation successful
- `400` — Parse/validation error
- `404` — Ruleset not found
- `500` — Server error

---

### `GET /jre/fields`

**Get available field definitions**

Returns fields that can be used in rule conditions, grouped by category. Supports filtering by categories, fieldTypes, isGrid, and lookupFormId.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `scopeCode` | no | `scopeCode` |
| `categories` | no | `categories` |
| `fieldTypes` | no | `fieldTypes` |
| `isGrid` | no | `isGrid` |
| `lookupFormId` | no | `lookupFormId` |

**Responses:**

- `200` — Field definitions returned
- `500` — Server error

---

### `GET /jre/categories`

**Get available field categories**

Returns all active field categories from the md51FieldCategory MDM.


**Responses:**

- `200` — Categories returned
- `500` — Server error

---

### `POST /jre/fields/refresh`

**Refresh field cache**

Clears the field definition cache.


**Responses:**

- `200` — Cache refreshed

---

### `POST /jre/saveRuleset`

**Save a ruleset**

Saves or updates a ruleset. Validates the script before saving.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Ruleset saved
- `400` — Validation failed
- `500` — Server error

---

### `GET /jre/loadRuleset`

**Load a ruleset**

Loads a ruleset by code. Context-based lookup (contextType + contextCode) is deprecated.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `rulesetCode` | no | `rulesetCode` |
| `contextType` | no | `contextType` |
| `contextCode` | no | `contextCode` |

**Responses:**

- `200` — Ruleset loaded
- `404` — Ruleset not found
- `500` — Server error

---

### `POST /jre/publishRuleset`

**Publish a ruleset**

Changes ruleset status to PUBLISHED.


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | yes | `requestBody` |

**Responses:**

- `200` — Ruleset published
- `404` — Ruleset not found
- `500` — Server error

---

## joget-smart-search — Smart search

**URL prefix**: `/jw/api/smartsearch` &nbsp;·&nbsp; **`api_id`**: `(look up in app_builder where name='smartsearch')`

**Source**: [`plugins/joget-smart-search/src/main/java/global/govstack/smartsearch/api/SmartSearchApiPlugin.java`](../plugins/joget-smart-search/src/main/java/global/govstack/smartsearch/api/SmartSearchApiPlugin.java)

**Endpoints**: 8


| Method | Path | Summary |
|---|---|---|
| `POST` | `/search` | Search for farmers |
| `GET` | `/lookup/{id}` | Get farmer by ID |
| `GET` | `/villages` | Get villages list |
| `GET` | `/community-councils` | Get community councils list |
| `GET` | `/cooperatives` | Get cooperatives list |
| `GET` | `/search/byNationalId/{nationalId}` | Search by National ID |
| `GET` | `/search/byPhone/{phone}` | Search by Phone |
| `GET` | `/statistics` | Get search statistics |


### `POST /search`

**Search for farmers**

Search for farmers using various criteria including name, ID, phone, district, and village


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `body` | no | `body` |

**Responses:**

- `200` — Search results returned successfully
- `400` — Invalid search criteria
- `500` — Internal server error

---

### `GET /lookup/{id}`

**Get farmer by ID**

Retrieve a single farmer record by their index ID


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `id` | no | `id` |

**Responses:**

- `200` — Farmer found
- `404` — Farmer not found
- `500` — Internal server error

---

### `GET /villages`

**Get villages list**

Get list of villages for autocomplete, optionally filtered by district


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `district` | no | `district` |
| `q` | no | `query` |

**Responses:**

- `200` — Villages list returned
- `500` — Internal server error

---

### `GET /community-councils`

**Get community councils list**

Get list of community councils for autocomplete, optionally filtered by district


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `district` | no | `district` |

**Responses:**

- `200` — Community councils list returned
- `500` — Internal server error

---

### `GET /cooperatives`

**Get cooperatives list**

Get list of cooperatives for autocomplete, optionally filtered by district and search query


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `district` | no | `district` |
| `q` | no | `query` |

**Responses:**

- `200` — Cooperatives list returned
- `500` — Internal server error

---

### `GET /search/byNationalId/{nationalId}`

**Search by National ID**

Find farmer by exact national ID match


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `nationalId` | no | `nationalId` |

**Responses:**

- `200` — Search results returned
- `404` — Farmer not found
- `500` — Internal server error

---

### `GET /search/byPhone/{phone}`

**Search by Phone**

Find farmer by phone number


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `phone` | no | `phone` |

**Responses:**

- `200` — Search results returned
- `404` — Farmer not found
- `500` — Internal server error

---

### `GET /statistics`

**Get search statistics**

Returns statistics for client-side confidence calculation including name frequencies and effectiveness factors


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `refresh` | no | `String` |

**Responses:**

- `200` — Statistics returned successfully
- `500` — Internal server error

---

## app-def-provider — App definition export

**URL prefix**: `/jw/api/appdef` &nbsp;·&nbsp; **`api_id`**: `(look up in app_builder where name='appdef')`

**Source**: [`plugins/app-def-provider/src/main/java/com/fiscaladmin/gam/appdefinitionprovider/lib/AppDefinitionProvider.java`](../plugins/app-def-provider/src/main/java/com/fiscaladmin/gam/appdefinitionprovider/lib/AppDefinitionProvider.java)

**Endpoints**: 6


| Method | Path | Summary |
|---|---|---|
| `GET` | `/catalog` | List Available Applications |
| `GET` | `/apps/{appId}/form` | Export Form Definition |
| `GET` | `/apps/{appId}/forms` | Export Application Forms |
| `GET` | `/apps/{appId}/crud` | Export CRUD Package |
| `GET` | `/apps/{appId}/cruds` | Export All CRUD Packages |
| `GET` | `/apps/{appId}/userview` | Export Userview |


### `GET /catalog`

**List Available Applications**

Returns a catalog of all available applications that can be exported


**Responses:**

- `200` — Success
- `500` — Server error

---

### `GET /apps/{appId}/form`

**Export Form Definition**

Exports a single form definition by app ID (path) and formId (query parameter). Metadata forms are excluded


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `appId` | no | `appId` |
| `formId` | yes | `formId` |

**Responses:**

- `200` — Form exported successfully
- `400` — Invalid form ID or metadata form
- `404` — Form not found
- `500` — Server error

---

### `GET /apps/{appId}/forms`

**Export Application Forms**

Exports all non-metadata forms from the specified application


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `appId` | no | `appId` |

**Responses:**

- `200` — Application forms exported successfully
- `404` — Application not found
- `500` — Server error

---

### `GET /apps/{appId}/crud`

**Export CRUD Package**

Exports a complete CRUD package (Form + Datalist + Userview menu) by app ID and CRUD customId


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `appId` | no | `appId` |
| `crudId` | yes | `crudId` |

**Responses:**

- `200` — CRUD package exported successfully
- `400` — Invalid CRUD ID or metadata CRUD
- `404` — CRUD package not found
- `500` — Server error

---

### `GET /apps/{appId}/cruds`

**Export All CRUD Packages**

Exports all non-metadata CRUD packages from the specified application


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `appId` | no | `appId` |

**Responses:**

- `200` — CRUD packages exported successfully
- `404` — Application not found
- `500` — Server error

---

### `GET /apps/{appId}/userview`

**Export Userview**

Exports the complete userview definition for the specified application


**Parameters:**

| Name | Required | Source variable |
|---|---|---|
| `appId` | no | `appId` |

**Responses:**

- `200` — Userview exported successfully
- `404` — Application or userview not found
- `500` — Server error

---
