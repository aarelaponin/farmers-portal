# UAT preparation — pause-and-resume plan

**Author / audience:** architecture lead (Aare).
**Status:** Pause point set 2026-05-12, after W4 (perf re-baseline + Postgres index tuning) completed.
**Trigger to resume:** Khotso team (MAFSN IT) feedback on email + SMS gateway, plus any related infra-side decisions.
**Purpose:** Two jobs. (a) Snapshot exactly where the project stands so picking up later — possibly weeks from now, from a fresh chat session — is fast and accurate. (b) Sequence the remaining UAT-prep work so the next pull-request-sized chunks are obvious before either Khotso responds or you decide to push forward without them.

---

## Section 1 — Snapshot at pause (2026-05-12)

### 1.1 Where the solo plan stands

| Week | Theme | Status at pause |
|---|---|---|
| W1 | RBAC + service accounts | **done** (cat verified seeing 5 categories; dashboards on `/jw/api/`). Service accounts for batch jobs still on `admin` — minor gap, not UAT-blocking. |
| W2 | SMTP + 12 email templates + lifecycle wiring | **done** (Gmail dev SMTP; reg-bb-engine build-126; notification_queue + state machine; test-mode override via JVM property). |
| W3 | SMS sandbox + notifications e2e + mobile audit + mobile fixes | **done except SMS** (user explicitly deferred SMS sandbox). Mobile P1 list closed; one P2 follow-up open as task #335. W3 lifecycle e2e is 12/12 green on the AppAudit fix. |
| W4 | Perf re-baseline + Postgres index tuning (this session) | **done** — see `perf_baseline.md`. One missing index added (`idx_reg_bb_eval_audit_app_id`). The other CLAUDE.md-recommended one (`idx_budget_event_idempotency`) was already in place. JVM/Tomcat tuning and Prometheus POC explicitly out of scope for this pass. |
| W5 | UAT instance + fixture data (500 farmers, 200 parcels, 50 apps, 20 vouchers, 4 envelopes) | **not started — top priority on resume**. |
| W6 | Citizen status-check kiosk + receipt printing | **not started — partially Khotso-dependent**. Depends on the §1.2 citizen-identity decision (Option A/B/C in `production_readiness_roadmap.md`). |
| W7 | Defect tracker + accessibility audit + Sesotho scaffolding | **not started — partially Khotso-dependent**. Defect tracker choice (Jira / Azure Boards / GitHub Issues) needs joint decision. |
| W8 | L1/L2 runbooks + UAT entry/exit criteria | **not started — ~80% solo-doable**. |

Bonus work that landed during W1-W3 but isn't in the plan: operator case-log v1 + kernel `note_thread` widget; 3 new e2e test scripts (notification, eligibility, budget engine); auto-generated `api_reference.md` (57 endpoints, 7 providers); `_07_Implementation_Guides` folder reorganisation into audience subfolders.

### 1.2 Dev environment fingerprint

| Item | Value |
|---|---|
| Joget URL | `http://20.87.213.78:8080/jw` |
| Joget app id | `farmersPortal` |
| Joget API key (formcreator) | `a5af1181f77b4a62b481725b6410e965` |
| formcreator api_id | `API-e7878006-c15a-425e-9c36-bebc7c4d085c` |
| regbb api_id | `API-168e3678-1f9a-46fc-8c19-d0d9a917eb73` |
| reg-bb-engine bundle current build | **build-139** (W3 lifecycle fix; AppAudit no longer seeds from c_status) |
| form-creator-api bundle | build-024+ (FormListDataJsonController via `/jw/api/...`) |
| Postgres host | `joget-pgsql-sa.postgres.database.azure.com` |
| Postgres db | `jogetdb` |
| New index added in W4 | `idx_reg_bb_eval_audit_app_id` on `(c_application_id, datecreated DESC)` |
| Audit table row count | 6,272 (2026-05-12) |
| Budget event row count | 370 (2026-05-12) |

### 1.3 Source-of-truth files for the next session to re-read

These five files are the project memory. Anyone (you in two weeks, a colleague in a year, a fresh Claude session) should re-read these before touching anything:

1. `CLAUDE.md` — codebase-wide rules and hard-won gotchas. Most important file.
2. `docs/architecture/decision-log.md` — every architectural decision with reasoning (up to D49).
3. `docs/implementation/production_readiness_roadmap.md` — the full path to production.
4. `docs/implementation/solo_implementation_plan.md` — the 8-week solo plan; tracking which weeks are done.
5. **This file** — the pause-and-resume plan.

### 1.4 Test harness — what's green right now

| Script | Status at pause | Notes |
|---|---|---|
| `tooling/test_w3_lifecycle_e2e.py` | 12/12 green (build-139) | Application lifecycle state machine. |
| `tooling/test_eligibility_e2e.py` | authored, 12 scenarios | A/B/C × 4 programmes. Profile A / PRG_2025_001 pinned to `pending_review`. |
| `tooling/test_notification_e2e.py` | authored | Submits, polls, asserts notification_queue terminal states. |
| `tooling/test_budget_engine_e2e.py` | authored | RESERVATION → COMMITMENT → EXPENSE chain assertions. |
| `tooling/test_budget_suite.py` | green | Budget reports + controls. |
| `tooling/test_im_e2e.py` | green | IM full lifecycle. |
| `tooling/test_im_stacking.py` | green | Multi-programme stacking stress test. |

**Re-running all seven on the first day back is the right smoke test.** Total wall-clock against sandbox→Azure is probably 15-20 minutes but they don't conflict; can run sequentially.

### 1.5 What's open in the task list

Only one pre-existing pending item at pause:

- **#335 — W3.4 follow-up: per-dashboard inner reflow (6 HtmlPages).** Not UAT-blocking if UAT is desktop-only.

Everything else through #356 is `completed`. Clean slate to resume from.

---

## Section 2 — Before-you-walk-away checklist

Items to do TODAY before stopping. Most are about not losing state.

### 2.1 Git hygiene

- [ ] Commit the W4 index DDL effect indirectly (the index itself lives in Postgres; commit the `perf_baseline.md` update which documents it). Suggested message: `docs(perf): re-baseline 2026-05-12, add idx_reg_bb_eval_audit_app_id`.
- [ ] Commit the `_07_Implementation_Guides/` folder reorganisation (the moves + README rewrite + cross-link rewrites).
- [ ] Commit the new e2e test scripts (`test_notification_e2e.py`, `test_eligibility_e2e.py`, `test_budget_engine_e2e.py`) and the API-reference generator (`build_api_reference.py` + `docs/developer/api_reference.md`).
- [ ] Commit the case-log v1 work (form `case_note.json`, `md_case_note_kind.json`, datalist `list_case_notes_for_app.json`, `CaseNoteThreadRenderer.java`, MetaScreenElement note_thread case, mm_field row for the operator review form).
- [ ] Commit this file.

If anything is in flux locally and you're not sure it should ship, **`git stash` with a descriptive name** rather than committing — the stash is your "uncertain shelf". Avoid leaving uncommitted files on the working tree, since two weeks later you won't remember what they were.

### 2.2 Dev-environment snapshot

- [ ] **Backup the dev Postgres.** Follow `docs/operations/backup_restore_runbook.md` — the documented procedure that's already been drilled. Tag the dump `pre-uat-prep-pause-20260512`. Stored in the same Azure storage account the runbook references.
- [ ] **Download the active JWA bundle** to local, named with date: `_backups/APP_farmersPortal-1-20260512.jwa`. The bundle is the deployable; if dev is wiped, this is the rebuild.
- [ ] **Download the current `reg-bb-engine-...-build-139.jar`** and the GIS UI plugin JAR to `_jars/` or wherever the local convention says. Document the build numbers in this file's §1.2 above (already done).

### 2.3 Documentation closeout

- [ ] Update `CLAUDE.md` with any session-discovered gotchas. From W4: the sandbox→Azure RTT dominates wall-clock load-test numbers; `EXPLAIN ANALYZE` is the right server-side measurement.
- [ ] Update `docs/architecture/decision-log.md` with any decisions taken in W4. Today: implicit D50 — "the two CLAUDE.md-recommended indexes on hot tables are in place; further index work deferred until row counts grow past defined thresholds." Worth recording even if low-stakes; the next session will thank you.

### 2.4 Khotso follow-up

- [ ] Confirm the Khotso request email (`_07_Implementation_Guides/x_archive/sms_gateway_request_to_khotso.md`) was actually sent. If still a draft, send it before stopping — the project sits idle until they respond, so the clock starts now.
- [ ] If sent, log the send date in this file (suggested: add to §5.1 below).

---

## Section 3 — When you come back: first 30 minutes

Sanity-check sequence. Do these before any new work.

### 3.1 Read the four-file memory pack (10 min)

In this order: this file → `CLAUDE.md` → `docs/architecture/decision-log.md` (skim D40-D49 for recency) → `solo_implementation_plan.md` for the W5-W8 detail.

### 3.2 Dev environment health (10 min)

```bash
# 1. Joget reachable?
curl -s -o /dev/null -w "%{http_code}\n" http://20.87.213.78:8080/jw/web/login
#    Expect: 200

# 2. Postgres reachable + index still present?
psql "postgresql://jogetadmin:Joget%40DB%232026%21@joget-pgsql-sa.postgres.database.azure.com/jogetdb?sslmode=require" \
  -c "SELECT indexname FROM pg_indexes WHERE tablename='app_fd_reg_bb_eval_audit' ORDER BY 1;"
#    Expect: app_fd_reg_bb_eval_audit_pkey, idx_reg_bb_eval_audit_app_id

# 3. Smoke: hit the auto-generated api_reference endpoint count
python3 tooling/build_api_reference.py | tail -3
#    Expect: "Wrote ... 57 endpoints across 7 providers" (or higher if endpoints added since)

# 4. One e2e test as a tripwire
python3 tooling/test_w3_lifecycle_e2e.py
#    Expect: 12/12 green
```

If any of those fail, **stop and diagnose before resuming work**. The failure mode is usually one of: Joget restarted and lost ehcache state (just refresh), reg-bb-engine bundle got rolled back (re-upload build-139), or Postgres index dropped (re-create per `perf_baseline.md` §5 step 3).

### 3.3 Read the Khotso reply, if any (10 min)

If Khotso has answered, route their feedback into §5 below — promote each answered item from "blocked" to "decided", and pick the resulting first action.

If they haven't, no problem — §4.1 (Week 5 UAT instance + fixtures) is the right first thing regardless of what they say.

---

## Section 4 — UAT-prep work, prioritised for resume

Five weeks of solo plan remain (W4 done in this session; W5-W8 ahead, plus a small W3 follow-up). Prioritised by leverage-and-unblock — not by week number.

### 4.1 **First priority — W5: UAT instance + fixture data (1 week of work)**

This is the single most important week left in the solo plan. Without a UAT instance, there is no UAT, regardless of how ready the application is.

**4.1.1 UAT Joget instance (2 days).** Stand up a second Joget via Docker compose on the same Azure VM (or a sibling — preferable for capacity-test isolation). Same plugins as dev (copy from current build set). Separate Postgres schema or sibling DB. Scheduled tasks enabled.

- Acceptance: UAT URL accessible; login works; all custom plugins loaded; W3 lifecycle e2e green when pointed at the UAT base URL.

**4.1.2 Fixture data design (1 day).** Volume target per `solo_implementation_plan.md` §W5.2:

- 500 farmers across 10 districts, weighted by district population
- 100 households (3-7 members each)
- 200 parcels with valid GIS polygons (sample from shapefile)
- 4 active programmes, 2 closed, 2 future
- 50 in-flight applications across all statuses
- 20 vouchers (5 redeemed, 5 partial, 5 active, 5 cancelled/expired)
- 4 budget envelopes with realistic LSL amounts

Anonymisation: Lesotho NID format `XXXXXXXXXXXXX` (13 digits), synthetic first-6-digits-as-DOB + 7 random; names from a synthetic Sesotho name table (50 × 50 = 2,500 combinations).

**4.1.3 Fixture seeder (2 days).** Extension of `tooling/seed.py`. Use the customer-data HARD RULE: registry forms (`farmerbasicinfo`, `parcelregistration`, etc.) get seeded ONCE via formcreator/seed against the **UAT instance**, not against dev. Transactional tables (applications, vouchers) can be re-seeded freely.

- Acceptance: 500 farmers visible; 200 parcels with rendered polygons; every voucher status present; every UAT_Guide.md scenario's "Pre-conditions" line satisfied.

### 4.2 **Second priority — W8.3: UAT entry/exit criteria doc (1 day, solo)**

The smallest deliverable with the biggest political weight. This is the single document MAFSN signs to declare UAT "go". Authoring is solo-doable; the sign-off list at the bottom is what needs MAFSN names.

Strawman content per `solo_implementation_plan.md` §W8.3. Save as `docs/operations/uat_entry_exit_criteria.md` (it's MAFSN-facing). Once drafted, share with Khotso and the MAFSN sponsor; their input on the exit criteria is what will eventually let UAT finish.

### 4.3 **Third priority — W7.1: defect tracker choice + setup (1 day, joint)**

Pick: GitHub Issues (if your team's there) or Azure Boards (if MAFSN already runs Azure DevOps — most likely). Ask Khotso first, default to Azure Boards if no answer in 1 week. Setup is ~half a day once chosen: severity scheme, label scheme, issue template, triage cadence.

Save process doc as `docs/operations/defect_management_process.md`.

### 4.4 **Fourth priority — W8.1 + W8.2: L1 + L2 runbooks (2-3 days, solo)**

L2 extends the existing `backup_restore_runbook.md` with the operational-error patterns we've seen during dev. L1 covers the citizen-question scripts. Both live in `docs/operations/`.

The biggest L2 entries to capture from session memory (before they fade):
- Form rendering an empty list when a form is brand-new — the form-creator-api table-before-mapping race (CLAUDE.md "Brand-new forms hit a table-before-mapping race"). Recovery: JVM restart.
- Eligibility queue backlog growing — ADR-030 worker drain path; how to manually run.
- Budget event posting failure — `c_idempotency_key` vs `c_correlation_id` lookup paths.
- Datalist render error after a JSON push — App Composer cache-1/cache-2 invalidation procedure.

### 4.5 **Fifth priority — W3.4 follow-up: 6-HtmlPage inner reflow (task #335) (1 day, solo)**

Open per-dashboard CSS work. Six HtmlPages need their inner Bootstrap grid wrapped in a `@media (max-width:768px)` reflow. Only meaningful if UAT will include mobile; if UAT is desktop-only, defer.

### 4.6 Lower priority / context-dependent

- **W4.2 — JVM/Tomcat tuning.** Not blocking UAT entry; the heap and thread pool defaults are healthy enough at this scale. Revisit if `perf_baseline.md` Section 1 numbers regress.
- **W4.4 — Prometheus + Grafana POC.** Valuable but big; better as a post-UAT-entry deliverable since UAT will consume bandwidth.
- **W6 — Citizen kiosk + receipt printing.** Khotso-decision-dependent (§5.2 below). If their feedback chooses Option C for citizen identity, this becomes a 1-week build; if it chooses Option A/B, scope changes materially.
- **W7.2 — Accessibility audit + W7.3 Sesotho scaffolding.** Valuable; the Sesotho work is technical-scaffolding-only without MAFSN comms doing the actual translation. Schedule once UAT instance + fixtures are landed and the dev cadence is calmer.

---

## Section 5 — What we're waiting on Khotso for

The Khotso draft request covers two big channels (email + SMS) and implies adjacent infra decisions. Below: the specific items, what's blocked on each, what we'd do once unblocked.

### 5.1 Khotso request status

- **Request drafted:** `_07_Implementation_Guides/x_archive/sms_gateway_request_to_khotso.md`, dated 2026-05-11.
- **Sent on:** **(fill in once sent)**.
- **Response received on:** **(fill in when reply arrives)**.

### 5.2 Decisions on the request and what they unblock

| Decision area | What we're asking | What unblocks |
|---|---|---|
| **Email — SMTP relay choice** | Office 365 / AWS SES / SendGrid / MAFSN Postfix | Production-grade email cutover. Current Gmail dev SMTP is for dev only; UAT can run on it but production cannot. |
| **Email — sender mailbox** | `noreply@mafsn.gov.ls` or similar; SMTP creds | Same as above. |
| **SMS — provider choice** | Africa's Talking / Vodacom direct / Econet direct / MAFSN gateway | The 8 citizen-facing SMS templates currently fire to a log-only backend. With creds, real SMS for UAT. |
| **SMS — sender ID** | Registered short code or alphanumeric | Same. |
| **Test credentials timeline** | Test access first, then production | Lets us validate runtime without burning production quota. |

### 5.3 Adjacent decisions worth raising while we have Khotso's attention

If the channel is open, surface these too — same set of stakeholders, same review meeting:

- **Citizen identity strategy** (production_readiness_roadmap.md §1.2). Default recommendation: Option C (operator-mediated) for pilot. If Khotso prefers a different path, scope changes materially for W6.
- **Keycloak realm availability** (§1.1). If MAFSN already runs a Keycloak, the OIDC connector is days of work; if not, 4-6 weeks slip risk.
- **UAT VM provisioning** (§5.1). One additional Azure VM for the UAT instance, sized similarly to dev.
- **Pilot district choice** (§9.1). Mohale's Hoek recommended; needs confirmation.
- **Defect tracker** (§5.4). Azure Boards if MAFSN runs Azure DevOps.
- **Pen test vendor** (§3.5). Pure procurement; not on critical path until UAT exit.

---

## Section 6 — Decision tree for the next session

Three possible states when you come back. Each maps to a clear "first thing to do".

### Branch A — Khotso replied, email + SMS unblocked

1. Update §5.1 with response date.
2. **Day 1 back:** spend the morning wiring the production SMTP into `EmailDispatcher` and the SMS provider into the SMS plugin (the code path exists; only credentials and provider-specific URL/auth change). Half a day.
3. **Day 1 afternoon:** smoke-test the production paths with the test creds Khotso provides. Run `test_notification_e2e.py` end-to-end.
4. **Day 2 onward:** §4.1 (W5 UAT instance + fixtures) — the big rock.

### Branch B — Khotso replied with partial info (e.g. email decided, SMS still pending)

1. Wire whichever channel is unblocked (likely email — easier procurement).
2. Default SMS to "deferred for pilot" per the existing user decision; document explicitly in `decision-log.md`.
3. Proceed to §4.1 (W5 UAT instance + fixtures).

### Branch C — Khotso has not replied yet

1. Send a polite nudge if more than 2 weeks have passed since the original request.
2. **Don't wait** for them to start §4.1. The UAT instance and fixture data are the highest-value-per-day work left and don't depend on the gateway decisions.
3. Proceed to §4.1 (W5 UAT instance + fixtures) regardless.

In all three branches, after §4.1 lands, the next priority is §4.2 (UAT entry/exit criteria doc) — this is what MAFSN needs to sign for UAT to actually start.

---

## Section 7 — Parking lot (ideas that came up but weren't done)

Capture-only. Revisit at the start of the resume sprint or when an obvious slot opens up.

- **Plugin consolidation pass on reg-bb-engine.** The bundle has grown to ~30 classes across notification, lifecycle, capability, processing, and tooling sub-packages. A consolidation review (which classes still earn their keep, what can be deleted) is overdue. Low priority; not UAT-blocking.
- **Audit retention archival cron.** CLAUDE.md says "tens of thousands per active subsidy cycle, no automatic TTL". One full UAT cycle will push `reg_bb_eval_audit` past 10k rows; archival cron is the right time to write it.
- **Mobile app feasibility check.** The user has said "no PWA for pilot". After UAT, revisit whether the deferred §4.2 PWA scope should reopen for the stage-2 expansion.
- **CI/CD for plugin builds.** Hand-built JARs are working but brittle for a multi-developer team. Production roadmap mentions this; deferred to post-UAT.
- **Per-dashboard inner reflow** (task #335). Already in the backlog; mentioned again here as the easiest "warm-up day" task if you want a low-stakes re-entry exercise after the pause.
- **Cross-tenant readiness review.** Not in scope for MAFSN-only deployment. If MAFSN ever wants to white-label, the metamodel architecture supports it but RBAC scoping needs a full design pass.

---

## Section 8 — One-paragraph status for a glance

The Lesotho Farmers Portal is functionally complete for the subsidy + IM lifecycle and three weeks into an eight-week solo UAT-prep plan, with W4 (perf re-baseline + Postgres indexes) just completed in this session. The application itself is UAT-grade; what stands between the project and UAT entry is one MAFSN-owned email/SMS gateway decision (Khotso request out, awaiting response), one solo week of work to stand up a UAT instance with seeded fixtures (W5), and three smaller solo deliverables (UAT entry/exit criteria, defect tracker, L1/L2 runbooks). Resuming this project starts with reading this file plus CLAUDE.md, smoke-testing the dev environment per §3.2, then either wiring Khotso's gateway creds (Branch A/B) or jumping straight into W5 (Branch C). Total remaining solo effort to reach UAT entry: ~3 weeks of focused work, comfortably independent of how Khotso replies.

---

*Plan v1, 2026-05-12. Update §5.1 send/response dates when applicable; flip status items in §4 as they complete; flip parking-lot items in §7 as they materialise.*
