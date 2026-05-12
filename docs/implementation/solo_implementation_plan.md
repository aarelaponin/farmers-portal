# Solo implementation plan — pre-UAT hardening

**Audience:** architecture lead (you), with AI assistance.
**Scope:** the 13 items from `production_readiness_roadmap.md` flagged "no blockers" — work that requires no MAFSN procurement, no infrastructure provisioning, no third parties.
**Duration:** 8 weeks of focused work.
**Deliverable:** at week 8, every UAT-entry criterion produceable solo is in place.

This is a tactical plan — concrete enough to execute against and tick off as you go. Not strategic; the strategic frame is in `production_readiness_roadmap.md`.

---

## Sequencing principle

Order driven by three rules:

1. **Unblock UAT first.** RBAC and notifications are entry criteria. Do them early.
2. **Build measurable artefacts before tooling around them.** Performance baseline → tuning → re-baseline → monitoring.
3. **Defer documentation until the underlying work is settled.** L1/L2/L3 runbooks at the end, not the start, because the ground keeps shifting underneath.

---

## Week 1 — RBAC + service accounts

**Goal:** every menu in the userview has a role-based permission. Every batch job runs under a named service account.

### W1.1 — RBAC role taxonomy (1 day)

**Deliverable:** `docs/architecture/rbac_taxonomy.md`.

Define the live role list. Recommended starting taxonomy (matches what's already in `mm_role`):

| Role | Sees |
|---|---|
| `role_field_officer` | Registration Forms (all), MOA Office → Inbox (own district only), Reports → Personal |
| `role_district_supervisor` | All Field-officer + Reports → District + MOA Office → Bulk actions + Audit |
| `role_finance_officer` | Budget (all), Reports → Finance, Audit (read-only on financial events) |
| `role_admin` | Master Data (all), MM-Configuration, Admin |
| `role_sysadmin` | Everything |

Workshop the list in your head OR write it as a strawman to review with MAFSN later. Either way: write it down.

**Acceptance:** the taxonomy file lists every role with a one-paragraph mandate.

### W1.2 — Permission classes audit (0.5 day)

The userview already has `permission` blocks on some menus. Audit which menus have them, which don't, what className they use.

```python
# script: tooling/audit_userview_permissions.py
import psycopg2, json
# pull the userview, walk every menu, tabulate (category, menu, permission.className)
```

**Acceptance:** spreadsheet/markdown table of every menu × current permission state.

### W1.3 — Apply role-based permissions (2-3 days)

For every menu in the userview, set `permission.className = "org.joget.lst.RolePermission"` (or whichever the existing pattern is — check the audit) with `properties.role = "role_xxx"`. Use `tooling/push_userview.py` to deploy.

Test users: create one user per role in Joget's local user manager (we'll re-do this against Keycloak later, but for now test users are fine).

Smoke-test: log in as each test user, verify the menu they see matches the taxonomy.

**Acceptance:** screenshot per role, all stored in `_07_Implementation_Guides/screenshots/rbac/`.

### W1.4 — Service accounts for batch jobs (1 day)

Create named service accounts in Joget:
- `svc_eligibility_worker` — for `EligibilityProcessingWorker` queue drain
- `svc_voucher_sweeper` — for voucher expiry sweeper
- `svc_audit_archive` — for the quarterly archival script
- `svc_mv_refresh` — for materialised view refresh

Update each scheduled-task plugin's "run-as" property (or the equivalent) to use the service account. Verify via the audit log that subsequent batch-job rows show the service account, not "admin".

**Acceptance:** scheduled task runs once with the new identity, audit log shows expected `c_actor` field.

---

## Week 2 — Email templates + SMTP wiring

**Goal:** every state transition in the system can fire an email to the right recipient with a meaningful body.

### W2.1 — SMTP test environment (0.5 day)

Pick one:
- **Mailtrap.io** (free tier, sandbox SMTP, captures emails for inspection).
- Your own Gmail with App Password (real delivery, but you see what citizens would see).

Configure Joget → Settings → Email Configuration with the test SMTP. Send a one-shot test email to verify connectivity.

**Acceptance:** test email arrives in inbox / Mailtrap.

### W2.2 — Email template authoring (3 days)

Write 12 templates. Markdown source files under `docs/notifications/`, then import into Joget as Email Tool configurations.

Templates (subject + body):

1. `application_submitted` → applicant
2. `application_auto_approved` → applicant
3. `application_pending_review` → applicant
4. `application_rejected` → applicant
5. `application_decision_pending` → operator (24h reminder)
6. `voucher_issued` → applicant
7. `voucher_redeemed` → applicant (per redemption)
8. `voucher_expiring_7d` → applicant
9. `voucher_expired` → applicant
10. `voucher_cancelled` → applicant
11. `budget_envelope_75pct` → finance officer
12. `budget_envelope_frozen` → district supervisor + finance officer

Each template:
- Subject line in EN + Sesotho placeholder (TODO marker).
- Body uses Joget's `${variable}` syntax for substitutions.
- Plaintext + HTML versions (HTML for office-recipient roles, plaintext for citizens with weak email).
- Footer with "do not reply, contact your district office" + plain-language phone number.

**Acceptance:** all 12 templates render in Joget's Email Tool config UI; sample send for each works.

### W2.3 — SMTP production-ready config draft (0.5 day)

Document what's needed for the production SMTP:
- Host, port, TLS settings (STARTTLS preferred, port 587).
- Service-account credentials (held in secret manager).
- "From" address, bounce-handling mailbox.
- DKIM / SPF / DMARC records — coordinate with MAFSN ICT.

Save as `docs/operations/smtp_production_config.md`. Will be read by MAFSN ICT when they wire the real SMTP server.

**Acceptance:** doc exists, lists every parameter with rationale.

### W2.4 — Email lifecycle wiring (3 days)

For each of the 12 templates, find the lifecycle hook that should fire it:

| Template | Hook |
|---|---|
| `application_submitted` | parcelLocation + spApplication form post-processor on initial save |
| `application_auto_approved` | RoutingEvaluator → "approved" status transition |
| `application_pending_review` | RoutingEvaluator → "pending_review" status |
| `application_rejected` | Operator-decision binder → "rejected" |
| `voucher_issued` | VoucherIssuanceTool post-issuance hook |
| `voucher_redeemed` | VoucherRedemptionTool post-redemption hook |
| `voucher_expiring_7d` | New scheduled task: daily, find vouchers with expiryDate = today+7 |
| `voucher_expired` | VoucherExpirySweeper post-expiry hook |
| `voucher_cancelled` | Voucher cancellation operator action |
| `budget_envelope_75pct` | New scheduled task: hourly, check envelope utilisation |
| `budget_envelope_frozen` | Threshold automation already exists; add email step |
| `application_decision_pending` | New scheduled task: daily, find apps in `pending_decision` >24h |

For existing hooks: add Email Tool invocation to the post-processor.
For new scheduled tasks: write them as Joget BeanShell or a small Java tool plugin (depends on complexity).

**Acceptance:** each template fires under its trigger condition during a manual test cycle. Document which template went where.

---

## Week 3 — Notifications wiring + mobile audit

### W3.1 — Notifications end-to-end test (1 day)

Run a full citizen lifecycle and confirm every email fires:

1. Register farmer → `application_submitted` email.
2. Submit application → eligibility runs → email per outcome.
3. Operator approves → `voucher_issued` email.
4. Wait 7 days → trigger expiry-warning sweeper → email.
5. Voucher redeemed → email.
6. Voucher cancelled → email.

**Acceptance:** screenshot of every received email pasted into a test report.

### W3.2 — SMS sandbox connector (2 days)

Sign up for Africa's Talking sandbox (free). Build a thin Joget plugin (or Tool) that posts to their `/messaging` endpoint. Reuse the email-template body pattern but truncate to 160 chars.

12 SMS templates mirror the email templates but shorter:

```
voucher_issued (SMS):
  "Voucher VCH-XXXXXX for <input> ready at <centre> until DD-MM-YYYY. -MAFSN"
```

For the demo / pilot, only the citizen-facing templates matter (8 of the 12). Operator-facing notifications go via email.

**Acceptance:** SMS sent from sandbox to a test phone number. Documented for the SMS production-rollout note.

### W3.3 — Mobile-device audit (1.5 days)

Open Chrome DevTools → device emulation. Run through every flow on three viewports:
- iPhone SE (375 × 667) — small screen, common in field
- iPad (1024 × 768) — district supervisor preference
- Pixel 7 (412 × 915) — common Android in Lesotho

For each flow:
- Farmer registration wizard
- Parcel registration with GIS capture
- Subsidy application
- Voucher redemption (operator-facing)
- Operator inbox + decision form
- Budget dashboard

For each screen, audit:
- Touch-target sizes (minimum 44×44 CSS pixels)
- Text legibility (16px+ for body, 14px for hints)
- Form field tab order
- Modal dialogs that overflow the viewport
- GIS map controls reachable on touch

**Deliverable:** `docs/implementation/mobile_audit_report.md` with screenshots + defect list.

**Acceptance:** every flow has been opened on all three viewports; defects categorised P1/P2/P3.

### W3.4 — Mobile-audit fixes (1.5 days)

Fix the P1 defects from W3.3 by adjusting CSS in the relevant form / userview, or by recommending adjustments to the Trimeda theme. P2 / P3 → backlog for post-pilot.

**Acceptance:** P1 list closed; P2 list documented in `docs/architecture/decision-log.md` as "deferred".

---

## Week 4 — Performance tuning + monitoring POC

### W4.1 — Performance re-baseline (1 day)

Run `tooling/load_test.py` against the dev instance with realistic numbers:
- 50 concurrent citizens registering
- 30 concurrent operators reviewing
- 10 concurrent voucher redemptions

Capture: response time p50/p95/p99, error rate, DB connection pool utilisation, JVM heap usage, CPU.

**Deliverable:** `docs/implementation/perf_baseline.md` updated with new numbers.

### W4.2 — JVM + Tomcat tuning (1 day)

Based on W4.1 findings, adjust:
- `-Xmx` / `-Xms` heap (start with 4 GB, adjust based on observation)
- Tomcat connector `maxThreads` (typical: 200)
- GC algorithm (G1GC for typical Joget workloads)
- Tomcat JDBC pool size (typical: 50)

Restart, re-run W4.1, verify improvement.

**Acceptance:** p95 response time improved or the trade-off explicitly documented.

### W4.3 — Postgres tuning (1 day)

Inspect via `pg_stat_*` views:
- Slow queries (any query consistently > 100ms)
- Table sizes vs `shared_buffers`
- Cache hit ratio (target > 95%)
- Autovacuum running on time

Adjust `postgresql.conf` parameters as appropriate. Add indexes where slow queries indicate need (especially on `app_fd_reg_bb_eval_audit.c_application_id` and `app_fd_budget_event.c_idempotency_key`).

**Acceptance:** documented set of changes; cache hit > 95%; no query > 200ms in normal load.

### W4.4 — Prometheus + Grafana POC (2 days)

On the dev VM:
- Install Prometheus + Grafana via Docker.
- Tomcat JMX → Prometheus exporter for JVM metrics.
- Postgres exporter for DB metrics.
- Joget → custom metrics endpoint (write a small servlet exposing key counters: applications submitted today, vouchers redeemed today, budget commitment vs available).

Build one Grafana dashboard with:
- Application submissions by hour (24h window)
- Voucher state distribution (pie)
- Budget envelope utilisation per envelope (bar)
- JVM heap + CPU + Tomcat thread pool over 24h
- Postgres connection pool over 24h

Define alert rules in Prometheus:
- Tomcat down for > 60s
- Heap usage > 85% sustained 5m
- DB connection pool > 90% sustained 2m
- 5xx error rate > 1% over 5m

Alerts route to email (re-using §W2 SMTP) for the dev instance. Production will route to PagerDuty or equivalent.

**Acceptance:** dashboard accessible at `http://<dev>:3000`; one alert manually fired and observed.

---

## Week 5 — UAT environment + fixtures

### W5.1 — UAT instance setup (2 days)

Stand up a second Joget via Docker compose:
- Same plugins as dev (copy from `_deploy/`)
- Separate database (Postgres in another container or schema)
- Separate URL: `farmers-uat.<your-domain>` or just `localhost:8081` for local UAT
- All scheduled tasks ENABLED (so we can test the auto-flows)

Document the run instructions in `_07_Implementation_Guides/uat_environment.md`.

**Acceptance:** UAT instance starts cleanly, login works, plugins load.

### W5.2 — Fixture data design (1 day)

Define the volume target:
- 500 farmers across all 10 districts, weighted by district population
- 100 households with 3-7 members each
- 200 parcels with valid GIS polygons (use random polygons sampled from the customer's shapefile)
- 4 active programmes, 2 closed, 2 future
- 50 in-flight applications across all status values
- 20 issued vouchers (5 redeemed, 5 partial, 5 active, 5 cancelled/expired)
- 4 budget envelopes seeded with realistic LSL amounts

**Anonymisation strategy:** Lesotho NID format `XXXXXXXXXXXXX` (13 digits). Use first 6 digits = synthetic DOB, last 7 random. Names from a synthetic Sesotho name generator (50 first names × 50 last names = 2500 combinations).

**Deliverable:** `app/seeds/uat-fixtures.yaml` extending the existing `lesotho-mm-fixture.yaml`.

### W5.3 — Fixture seeding (2 days)

Write the seeder. Likely an extension of `tooling/seed.py` to handle the larger volumes and randomised polygon assignment.

Run against UAT instance. Verify:
- 500 farmers visible in farmer registry
- 200 parcels visible with polygons rendering
- All 4 programme statuses visible
- All voucher statuses present

**Acceptance:** UAT environment populated; smoke-test of key UAT scenarios from `UAT_Guide.md` passes.

---

## Week 6 — Citizen kiosk + status check

### W6.1 — Status-check kiosk page (3 days)

Joget HtmlPage menu under a new userview category `Citizen Self-Service` (or expose it as a public unauthenticated URL — TBD with MAFSN, default to authenticated kiosk).

UI:
- Single big input: "Enter your National ID"
- On submit, query existing datalists scoped to that NID:
  - Latest farmer record (if any)
  - All applications and their current status
  - All vouchers and their current state
  - Pending actions ("you have 1 voucher waiting at <centre>")
- Print receipt button → reuses existing voucher-print template

Use Joget's `/jw/web/json/data/list/...` endpoint for the queries (same pattern as the executive dashboard). Keep authentication in place for now (anonymous access is a security review item).

**Deliverable:** `farmersPortal/v/_/citizen_kiosk` working in dev + UAT.

**Acceptance:** entering a known NID returns all that citizen's records; entering an unknown NID returns "not found, please register first".

### W6.2 — Kiosk receipt printing (2 days)

When a citizen registers via the operator-mediated flow, the operator should be able to hand them a printed receipt. Reuse the voucher-slip template pattern (existing `print_voucher_slip` HtmlPage).

A new HtmlPage `print_application_receipt` that:
- Takes `applicationId` from the URL
- Renders a self-contained printable HTML page with: applicant name, NID, programme, application date, expected decision date, contact info for queries
- Print stylesheet (A5, no browser chrome)

**Acceptance:** operator can click "Print Receipt" on any application detail page; A5 print preview renders correctly.

---

## Week 7 — Defect tracker + accessibility + Sesotho scaffolding

### W7.1 — Defect tracker setup (1 day)

Pick: GitHub Issues (if your team is already there) or Azure Boards (if MAFSN uses Azure DevOps).

Set up:
- Severity definitions (Critical / High / Medium / Low) with response-time SLAs.
- Label scheme: `severity:*`, `area:registration`, `area:voucher`, `area:budget`, `area:auth`, `area:ui-mobile`, `device:phone`, `device:tablet`, `browser:safari` etc.
- Issue template with: steps to reproduce, expected, actual, screenshots, environment.
- Triage cadence: daily during UAT, weekly otherwise.

**Deliverable:** `_07_Implementation_Guides/defect_management_process.md`.

### W7.2 — Accessibility audit (2 days)

Tools:
- WAVE browser extension (free) — run on every page, capture report.
- Lighthouse → Accessibility tab → run on every userview menu.
- Manual keyboard-only navigation: Tab through every form, ensure focus visible and logical order.
- Manual contrast check on every status badge / pill / button.

**Findings categorisation:**
- **Critical** (blocks UAT): screen reader can't announce, focus traps, contrast < 4.5:1 on body text.
- **High** (UAT but with workaround): focus order non-intuitive, missing aria labels.
- **Medium / Low**: cosmetic, deferred.

Fix Criticals; document Highs.

**Deliverable:** `_07_Implementation_Guides/accessibility_audit.md`.

### W7.3 — Sesotho i18n scaffolding (2 days)

Joget supports localisation via `messages_LANG.properties` files inside the JWA. Steps:

1. Extract every UI string from form definitions, datalist column labels, userview menu labels into a `messages.properties` file (the EN baseline).
2. Create a parallel `messages_st.properties` with TODO markers — `# TODO: translate "Application submitted"`.
3. Identify which strings need translation by audience:
   - Citizen-facing (highest priority): farmer registration, parcel registration, voucher messages.
   - Operator-facing (medium): inbox, decision form, dashboards.
   - Admin-facing (lowest): MM-Configuration, master-data CRUD.
4. Wire Joget's locale switcher: a userview header drop-down that sets the `lang` cookie/parameter.

This is technical scaffolding only — actual translation happens later, by MAFSN comms.

**Deliverable:** `_07_Implementation_Guides/sesotho_localisation_strings.md` listing every string by audience priority.

---

## Week 8 — Hand-off documentation + go/no-go criteria

### W8.1 — L1 helpdesk runbook (2 days)

For the customer-side helpdesk (likely MAFSN ICT), authoring:

- "Common citizen questions" with the response operators should give:
  - "How do I check my application status?" → kiosk URL or call-back script
  - "I lost my voucher" → reissuance procedure
  - "My voucher won't redeem" → 5-step diagnostic
- Login problems: forgot password, locked out, browser issues.
- "Operator escalation" path: when L1 should hand off to L2.

**Deliverable:** `_07_Implementation_Guides/l1_helpdesk_runbook.md`.

### W8.2 — L2 system administrator runbook (2 days)

Extends the existing `backup_restore_runbook.md` with:

- Common error patterns and fixes:
  - Eligibility queue backlog growing — what to check
  - Budget event posting failure — what to do
  - Specific datalist render error — diagnostic steps
  - Plugin upload failure — recovery
- Scheduled-task management (manual run, pause, resume).
- DB connection pool exhaustion recovery.
- Disk-space management (audit log archive trigger).
- DR drill procedure (quarterly).

**Deliverable:** `_07_Implementation_Guides/l2_sysadmin_runbook.md`.

### W8.3 — UAT entry/exit criteria + go/no-go (1 day)

Strawman document for joint MAFSN review:

- UAT entry checklist (everything from this 8-week plan + items requiring MAFSN).
- UAT exit criteria (defect targets, scenario sign-off).
- Pilot go/no-go criteria.
- Sign-off matrix (who signs what).

**Deliverable:** `_07_Implementation_Guides/uat_entry_exit_criteria.md`.

### W8.4 — Wrap-up + customer briefing prep (1 day)

- Update decision log with anything from these 8 weeks.
- Update CLAUDE.md if any new gotchas surfaced.
- Compile a one-page status report: "8 weeks of pre-UAT hardening complete; here's what's now ready and what's still pending from MAFSN."

**Deliverable:** `_07_Implementation_Guides/preuat_hardening_report.md`.

---

## Tracking

Maintain progress in this same document — flip each week's items from `[ ]` to `[x]` as you complete them. Add date stamps. Slip-and-replan: if a week takes 1.5 weeks, push downstream by 0.5 weeks but don't drop scope.

```
Week 1  [ ] W1.1 RBAC taxonomy
        [ ] W1.2 Permission audit
        [ ] W1.3 Apply role permissions
        [ ] W1.4 Service accounts
Week 2  [ ] W2.1 SMTP test env
        [ ] W2.2 Email templates
        [ ] W2.3 SMTP prod-config doc
        [ ] W2.4 Lifecycle wiring
Week 3  [ ] W3.1 Notifications e2e test
        [ ] W3.2 SMS sandbox
        [ ] W3.3 Mobile audit
        [ ] W3.4 Mobile fixes
Week 4  [ ] W4.1 Perf re-baseline
        [ ] W4.2 JVM+Tomcat tuning
        [ ] W4.3 Postgres tuning
        [ ] W4.4 Prometheus + Grafana
Week 5  [ ] W5.1 UAT instance
        [ ] W5.2 Fixture design
        [ ] W5.3 Fixture seeding
Week 6  [ ] W6.1 Status-check kiosk
        [ ] W6.2 Kiosk receipt printing
Week 7  [ ] W7.1 Defect tracker
        [ ] W7.2 Accessibility audit
        [ ] W7.3 Sesotho scaffolding
Week 8  [ ] W8.1 L1 runbook
        [ ] W8.2 L2 runbook
        [ ] W8.3 UAT entry/exit
        [ ] W8.4 Wrap-up + briefing
```

---

## Things this plan deliberately does NOT cover

- Anything blocked on MAFSN procurement (Keycloak prod, SMS contract, pen test, hosting).
- Anything blocked on a customer decision (citizen identity strategy, USSD scope, off-line PWA).
- Items that need physical presence in Lesotho (on-site UAT, training delivery).
- Large multi-week dev (off-line PWA at 6-8 weeks is too big for solo).

These are in `customer_briefing_production_path.md` — the parallel document that asks the customer for what we need from them.

---

*Plan v1, 2026-05-08. Adjust ruthlessly as reality intervenes.*
