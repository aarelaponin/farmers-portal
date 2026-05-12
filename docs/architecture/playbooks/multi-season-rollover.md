# Multi-Season Rollover Playbook

## What this document is

The Lesotho Farmers Portal is built around **agricultural seasons / fiscal years**.
A typical cadence:

* **Season opens** — programme(s) configured, budget envelope allocated, allocation
  plan authored, supplier procurement triggered, applications opened.
* **Season runs** — applications submitted, evaluated, approved; vouchers issued;
  inputs distributed at Resource Centres.
* **Season closes** — application window shuts, outstanding vouchers expire or are
  cancelled, residual budget is released, performance is reported, the cycle is
  archived.
* **Next season opens** — new programmes (or new versions of existing programmes),
  new envelopes, new plan, new applications.

This playbook is the operations runbook MAFSN uses each year to perform the
**closing → opening** transition cleanly. It assumes the FY2526 cycle is in
progress and FY2627 is being prepared.

## Audience

* MAFSN systems administrator (executes most steps)
* MAFSN finance lead (signs off the financial release step)
* MAFSN programme manager (signs off the programme cloning step)

This is not a citizen-facing or operator-facing document. It's for the small
handful of people who run the system at season boundary.

## Timing

The full rollover takes roughly two weeks of calendar time, not because any
single step is large, but because a few steps require human sign-off and a
quiet window in the data.

| Phase | When | Effort |
|---|---|---|
| Pre-rollover audit | 4 weeks before close | 0.5 day |
| Application window closure | 2 weeks before close | 0.25 day |
| Voucher cleanup (expiry sweep + cancellations) | 1 week before close | 0.5 day |
| Envelope reconciliation + release | Close day | 0.5 day |
| Reporting + sign-off | Close day + 3 | 1 day |
| FY2627 programme + plan authoring | Pre-open | 1 day |
| FY2627 envelope provisioning | Pre-open | 0.5 day |
| Open the new season | Day 0 | 0.25 day |

## Principles applied

A few decisions worth naming up front because they shape the steps below:

* **No data deletion across seasons.** FY2526 applications, vouchers, redemptions,
  receipts, and stock transactions stay in the database forever. The audit value
  of cross-season comparison ("are participation rates rising? are supplier lead
  times improving?") depends on that history being intact.
* **Programmes are versioned, not edited.** A FY2627 maize-seed programme is a
  *new programme record* (`PRG_2026_001`), not an edit of `PRG_2025_001`. This
  preserves the determinant rules and the budget envelope shape exactly as they
  were when FY2526 applicants were evaluated.
* **Envelopes are versioned with their programme.** `ENV_PRG_2025_001_FY2526`
  and `ENV_PRG_2026_001_FY2627` are independent ledgers.
* **Resource Centres, suppliers, inputs, districts persist.** These are
  master-data entities, not season entities. Adding a new centre or retiring
  an old one is a separate operation that can happen any time, but the centres
  themselves don't have a "season" attribute.
* **In-flight vouchers from FY2526 are settled before FY2627 opens.** No voucher
  should straddle seasons — either it's redeemed, expired, or cancelled before
  the new season's allocation plan goes live.

The reasoning: dual-season vouchers create accounting ambiguity ("is this
EXPENSE against FY2526's envelope or FY2627's?"). A clean closing means the
audit trail per season is unambiguous.

## Phase 1 — Pre-rollover audit (4 weeks before close)

The objective is to know how much work the rollover will take. Run the IM
reports and budget reports and get a snapshot of the closing season's state.

1. Open **Reports → IM — Dashboard KPIs** and capture the current numbers:
   issued vouchers, redeemed vouchers, redemption %, distinct farmers served,
   committed budget, expensed budget.
2. Open **Reports → IM — End-of-campaign reconciliation** and screenshot the
   per-programme view. This is the "where are we vs the original plan" baseline.
3. Open **Budget → Pending pipeline** to count any applications still in
   pending / pending_review / pending_data_clarification status. If the count
   is non-zero, decide who works through them and by when. Goal: zero pending
   apps by application-window-closure day.
4. Run a **count of unredeemed issued vouchers** (system administrator):

   ```sql
   -- Read-only; allowed under HARD RULE
   SELECT v.c_status, count(*) FROM app_fd_im_voucher v
    WHERE v.c_status IN ('issued', 'partially_redeemed')
    GROUP BY v.c_status;
   ```

   Knowing this number lets MAFSN decide between proactive expiration (auto)
   and proactive cancellation (operator-driven, with reason codes that feed
   into M&E later).

## Phase 2 — Close the application window (2 weeks before close)

The new application URL stops accepting submissions. Existing applications in
`pending_review` continue to be processed by operators.

Two ways to do this — pick one based on operational preference:

* **Soft close** — leave the form live, but set its `c_status` filter so any
  new submissions land at `pending_review` only. Operators continue to triage
  the queue, but no new evaluations begin. The citizen-facing label changes
  to "applications now under review — no new submissions accepted".
* **Hard close** — remove the citizen wizard menu from the userview. Operators
  still see the operator inbox. Citizens get a 404 if they bookmark the URL.
  Cleaner but requires a userview re-push.

For Lesotho, the hard close has been the recommended path — no ambiguity about
whether late submissions are accepted. The userview menu can be restored at
season open.

## Phase 3 — Voucher cleanup (1 week before close)

By close day, every issued / partially_redeemed voucher should be either
redeemed, expired, or cancelled. This is the largest operational task.

### 3a — Run the expiry sweeper

If the daily scheduled job is wired, this happens automatically. If not, the
admin runs it manually:

```bash
curl -X POST -H "api_id: API-BUDGET" -H "api_key: <KEY>" \
  http://<host>/jw/api/budget/expire-vouchers
```

Result is reported in the response:

```json
{ "status":"ok",
  "scanned": 47,
  "flipped": 47,
  "released": 45,
  "releaseSkipped": 2,
  "voucherCodes": [...] }
```

`releaseSkipped` covers the corner cases (no original COMMITMENT event,
voucher fully consumed by partial redemptions, dispatch errored). If it's
non-zero, the admin should investigate each.

### 3b — Cancel any vouchers the farmer has explicitly withdrawn

These are usually flagged in MAFSN's helpdesk system. For each, the operator
calls cancel-voucher with the helpdesk ticket number as `reason`:

```bash
curl -X POST -H "api_id: API-BUDGET" -H "api_key: <KEY>" \
  "http://<host>/jw/api/budget/cancel-voucher?\
voucherCode=VCH-...&reason=helpdesk-1234-applicant-withdrew&actor=admin-name"
```

### 3c — Verify zero unredeemed vouchers

```sql
SELECT count(*) FROM app_fd_im_voucher
 WHERE c_status IN ('issued', 'partially_redeemed');
```

Should return zero. If non-zero, those vouchers either still have stock to
collect (rare, post-sweep) or are stuck for some other reason. Investigate
each before proceeding.

## Phase 4 — Envelope reconciliation and release (close day)

By now every voucher is in a terminal state and the budget journal reflects
the final position. Two sub-steps.

### 4a — Snapshot the per-envelope final position

For each FY2526 envelope, capture the closing balance:

```sql
SELECT bp.envelope_code, bp.allocated, bp.available, bp.committed,
       bp.expensed,
       bp.allocated - bp.expensed AS unspent
  FROM budget_projection bp
 WHERE bp.envelope_code LIKE '%FY2526';
```

The `expensed` column is the actual money that flowed to farmers. The
`unspent` column is the residual (allocated minus expensed) — this is what
gets released back to the donor / treasury, if the funding terms require it.

### 4b — Release residual (if applicable)

If the funding agreement requires unspent budget to be returned at season
close, dispatch a `MANUAL_CLAWBACK` event per envelope for the unspent amount:

```bash
curl -X POST -H "api_id: API-BUDGET" -H "api_key: <KEY>" \
  -d '{"envelopeCode":"ENV_PRG_2025_001_FY2526","eventType":"MANUAL_CLAWBACK",
       "amount":"<unspent>","actor":"finance-lead","correlationId":"close-FY2526",
       "idempotencyKey":"close_FY2526:PRG_2025_001:clawback"}' \
  http://<host>/jw/api/budget/dispatch
```

This is finance-lead's call. The journal records the clawback against
`AVAILABLE` and `ALLOCATED` (per BudgetEngine.composeEnvelopeJournal — the
only event type that touches ALLOCATED apart from the original ALLOCATION).

### 4c — Mark envelopes as `closed`

Per the budget engine, a `closed` envelope rejects every further event.
Setting status to `closed` is the operational signal that nothing more
should land here.

This requires a Joget-API write to `app_fd_budget_envelope`:

```bash
# Pseudocode — script via formcreator /seed UPSERT
# (an Envelope CRUD API is available; check app/seeds/.api_endpoints.json
#  for the apiId/formId pair)
```

After this point, the envelope is immutable. Reports continue to show
historical data; any attempted dispatch produces `envelope_closed:CODE`.

## Phase 5 — Reporting and sign-off (close day + 3)

The finance lead and programme manager sign off on the closing reports
before the new season opens. This step is process, not technical.

Reports to print:

* **IM — Dashboard KPIs** (final state)
* **IM — Voucher utilisation by programme** (issued / redeemed / expired /
  cancelled breakdown — answers "how well did each programme work?")
* **IM — End-of-campaign reconciliation** (committed / expensed / released
  per programme — financial close)
* **IM — Supplier performance** (input to next year's procurement decisions)
* **IM — Consumption by centre (last 30 days)** (a "what happened in the
  closing weeks" view)
* **Budget → GL Export** (the Q4 ledger snapshot for accounting hand-off)

These get exported as PDFs, signed by the relevant lead, and filed under
`_07_Training/_seasonal_archives/FY2526/`.

Optional but recommended: a written narrative of "what worked / what didn't",
2-3 pages, that feeds into FY2627's design conversations.

## Phase 6 — FY2627 programme + plan authoring (pre-open)

The new season's programmes are authored. Two patterns:

### Pattern A — Carbon copy (most programmes)

If a programme is unchanged year over year (e.g. the maize-seed programme has
the same eligibility rules and benefit), the new programme is a clone of the
old one with three updates:

1. Programme `code` changes: `PRG_2025_001` → `PRG_2026_001`
2. Determinants are re-pointed (the `mm_determinant.programme_code` foreign
   key gets updated to the new programme code)
3. The fiscal-year suffix in capability lookups flips: `FY2526` → `FY2627`

The form-creator-api `/seed` endpoint with the existing fixture YAML structure
is the path of least resistance — copy `app/seeds/lesotho-mm-fixture.yaml`'s
programme + determinant blocks, change codes, run.

### Pattern B — Versioned change (programmes with revised rules)

If a programme's eligibility or benefit changes (e.g. the smallholder
threshold rises from 5ha to 7ha), the new programme is **not** a clone — it's
a fresh authoring exercise. The ADR for the policy change should be recorded
in `docs/architecture/decision-log.md` (with the rationale — "donor request",
"operational data showed X", etc.) before the programme JSON is authored.

### Allocation plan

A new `im_allocation_plan` row is authored with:

* `code` reflecting the new cycle (e.g. `AP_PRG_2026_001_2026-27`)
* `programme_code` pointing at the new FY2627 programme
* `cycle_label` reflecting the new season
* `effective_from` = season start, `effective_to` = season end
* `lines` re-authored — typically a copy of the prior plan with quantities
  adjusted based on what the closing reports showed (high-utilisation
  centres get more; low-utilisation centres might be re-evaluated for
  closure)

## Phase 7 — FY2627 envelope provisioning (pre-open)

For each new programme, an envelope is created and an `ALLOCATION` event is
dispatched against it for the full FY2627 budget:

```bash
curl -X POST -H "api_id: API-BUDGET" -H "api_key: <KEY>" \
  -d '{"envelopeCode":"ENV_PRG_2026_001_FY2627","eventType":"ALLOCATION",
       "amount":"<budget-LSL>","actor":"finance-lead","correlationId":"open-FY2627",
       "idempotencyKey":"open_FY2627:PRG_2026_001:allocation"}' \
  http://<host>/jw/api/budget/dispatch
```

After dispatch, `budget_projection.allocated` and `.available` should both
equal the dispatched amount. The new envelope is ready to receive
RESERVATION / PRE_COMMITMENT / COMMITMENT events as the new season's
applications come in.

## Phase 8 — Open the new season (day 0)

Three steps, in order:

1. **Re-publish the citizen wizard menu** (if hard-closed in Phase 2). Push
   the updated userview that includes the FY2627 application entry point.
2. **Verify the catalogue** — the catalogue page should now show the FY2627
   programmes, with applicability evaluating against the new determinants.
3. **Smoke test** — submit one test application via Profile A (or a
   designated test NID) for one of the new programmes; verify it
   auto-approves and a voucher gets issued. This catches any wiring issue
   before real citizens hit the system.

## Things that are NOT done at rollover

* **Master data is not touched.** Resource Centres, suppliers, inputs,
  districts, villages, farmer-category catalogue — all stays as is.
* **Inventory is not zeroed.** Stock that's physically at a centre at season
  close stays in `app_fd_im_inventory.c_quantity_on_hand`. The new season
  inherits whatever was on the shelf. (If a major stock writedown is needed,
  it's recorded as an `ADJUSTMENT` stock-transaction event, not a season
  rollover artefact.)
* **The farmer registry is not touched.** Farmer records persist across
  seasons by design — that's the whole point of having a registry.
* **Old programmes are not deleted.** They stay in the system as historical
  records. The FY2627 catalogue surfaces only the active programmes (filter
  on the programme's status / effective dates), but the data isn't removed.

## Forensic / recovery notes

If something goes wrong mid-rollover, the recovery posture is:

* **Phase 4 clawback dispatched against wrong envelope** — the engine rejects
  duplicate idempotency keys, so re-dispatching with the correct envelope is
  safe. The wrongly-dispatched event remains in the journal as a permanent
  record of the mistake; a `MANUAL_TOP_UP` to the source envelope can be
  used to reverse it (with a clear correlation_id linking to the original
  mistake).
* **Phase 6 programme cloned with wrong rules** — the new programme has its
  own code; just re-author the determinants and re-push. Don't attempt to
  edit a determinant after applications have been submitted against it.
* **Phase 7 envelope ALLOCATION dispatched twice** — same idempotency
  protection. Re-dispatching is a no-op.
* **Phase 8 catch-up: a citizen application submitted before the smoke test
  goes wrong** — pull the application id, walk through the same steps the
  smoke test would have done, identify the failure mode, fix the underlying
  issue, then either let the eligibility worker re-process the application
  or DELETE it through the citizen API and ask the citizen to resubmit (the
  latter only if the data wasn't otherwise valid).

## Lessons feed back into design

After each rollover, the operations team should record (in a brief
post-mortem):

* Steps that took longer than expected (so the next playbook can budget
  realistic time)
* Steps that needed manual intervention (so they can be considered for
  automation in the next dev cycle)
* Reports that were missing during the close (so they can be added)
* Any data-shape decisions that bit during the close (so they feed into
  the decision log)

The first rollover (FY2526 → FY2627) is the formative one. By FY2627 →
FY2728, this playbook should have a third revision with most of the rough
edges smoothed.

## Annex — the multi-cycle data model in one paragraph

A single farmer (registry row, persistent) submits one application per
cycle per programme (`subsidy_app_2025` table — note the year suffix,
which ages with the cycle). Each application that auto-approves produces
one voucher (`im_voucher`, `c_programme_code` carries the cycle's
programme). Each voucher's COMMITMENT lands on the programme's
fiscal-year envelope (`ENV_<PROGRAMME>_FY<YEAR>`). Redemptions, expiries,
cancellations all post events against that envelope. At season close, the
envelope's residual is released and the envelope is marked `closed`. The
next season opens new programme codes, new envelope codes, new
application table (`subsidy_app_2026`), and the cycle repeats — same
machinery, same audit trail, no shared state across years except the
master-data registries that genuinely span seasons.

---

*Authored: 2026-05-06 (closing FY2526). Revision 1 — based on the design
of the system as built, not yet on operational experience.*
