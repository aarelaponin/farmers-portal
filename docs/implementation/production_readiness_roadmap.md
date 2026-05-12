# Production-readiness roadmap

**Audience:** Architecture lead + MAFSN ICT + implementing partners.
**Purpose:** Single source of truth for the full path from "demo-ready" (where we are now, May 2026) to "production-live" (target: Q4 2026 / FY26-27 cycle launch). Covers technical hardening, functional gaps, UAT preparation, UAT execution including on-site/off-line, training, hand-off, and cutover.
**Status:** Draft 1, 2026-05-08.
**Cross-references:** `minagri_it_todo.md`, `backup_restore_runbook.md`, `UAT_Guide.md`, `payment_integration_scoping_note.md`, `vat_tax_decision_note.md`. This document does NOT duplicate those — it sequences them and identifies the gaps they don't cover.

---

## Executive view

```
                                              May 2026                     Q3 2026 (planned)            Q4 2026 (target)
                                                  │                              │                            │
DEMO-READY ────► PRE-UAT HARDENING ────► UAT EXECUTION ────► PRODUCTION CUTOVER ────► STEADY-STATE OPS
   (you are here)        ~6-10 weeks                ~6-12 weeks                  ~2-4 weeks                    ongoing
```

**The honest read.** The application is functionally complete for the subsidy-and-IM lifecycle. What's NOT done is roughly half of what a real production deploy needs:

- **Authentication is local-only.** Every user (operator, citizen, admin) authenticates against Joget's built-in user store. No SSO, no MFA, no integration with the customer's identity infrastructure. **This is the largest single gap.**
- **Notifications are silent.** Vouchers issue, applications get approved, none of this proactively reaches the citizen. SMS / email integration is a known gap (`minagri_it_todo` §1).
- **Network/infrastructure is dev-grade.** Single Tomcat instance, public IP, basic TLS at best, no load balancer, no WAF, no monitoring, no alerting. Adequate for showing screens; not for real operations.
- **No off-line capability.** The portal is a server-rendered web app. Lose connectivity at a rural collection point and the operator is blocked. UAT plans are silent on this.
- **Mobile is "responsive" only.** Forms render on phones but there's no app, no offline cache, no native GPS integration beyond the browser API. The GIS widget works on phone browsers; that's it.
- **No CI/CD.** Every plugin JAR has been hand-built and uploaded. Adequate when there's one developer; brittle for a 5-person team during UAT defect-fixing.

This document scopes everything. **Do not be alarmed by the length** — most items are 1-3 days of focused work. The total is ~10-14 weeks elapsed time, not effort, because items chain (Keycloak before SSO testing, SSO before role-based UAT, etc.).

---

## Section 1 — Identity, authentication, authorization

### 1.1 — Keycloak SSO for MAFSN staff (back office)

**Why.** Operators, district officers, finance staff, sysadmins are MAFSN employees. They already have ICT credentials; making them re-register in Joget is a friction tax and a security regression (parallel password store, no central policy enforcement, no central deactivation when staff leaves).

**Scope.** Joget's `Directory Manager` plugin slot supports OAuth2/OIDC out of the box. We point Joget at a Keycloak realm that the MAFSN ICT team owns. Users authenticate against Keycloak; Joget receives an OIDC ID token; Joget maps Keycloak groups to Joget roles.

**Components.**
1. Keycloak server (existing or new — MAFSN ICT to confirm).
2. Realm: `mafsn` or similar. Client: `farmers-portal-backoffice`.
3. Joget configuration: Settings → Directory Manager → install/configure an OIDC connector. Joget Marketplace ships `OAuth2/OIDC Directory Manager`; needs licence verification.
4. Group mapping: Keycloak groups → Joget roles (`role_field_officer`, `role_district_supervisor`, `role_finance_officer`, `role_admin`).

**Effort.** 2-3 weeks elapsed. ~5 days actual work, but blocked by Keycloak provisioning (MAFSN ICT) and group definition (joint workshop).

**Prerequisites.**
- MAFSN ICT confirms Keycloak instance availability (existing or to be stood up).
- Joint workshop to define the role taxonomy: who logs in, what they can do.
- Directory Manager plugin licence resolved (Joget Enterprise vs community).

**Risks.**
- Keycloak doesn't exist → 4-6 week slip while MAFSN stands one up.
- Existing AD/LDAP — different connector path, easier in some ways (Joget ships LDAP Directory Manager) but loses Keycloak's modern features.
- Group-to-role mapping is wrong → operators can't see their inbox; immediate UAT blocker.

**Owner.** Joint: MAFSN ICT (Keycloak server, group provisioning) + us (Joget configuration + role mapping).

---

### 1.2 — Citizen identity: separate IDP or self-registration?

**The decision pending.** Citizens are not MAFSN staff. They cannot sit in the same Keycloak realm. Three options:

**Option A — Use Lesotho's national digital ID (eID).** If MAFSN can integrate with the national ID provider (typically operated by Ministry of Home Affairs), citizens log in with their existing national credentials. Highest trust, lowest friction. **Likely a multi-month integration involving inter-ministry data-sharing agreement.**

**Option B — Stand up a separate Keycloak realm for citizens.** Realm `citizens`, self-registration enabled, identity proofed via NID + mobile verification. Simpler than Option A but the citizen has to remember another password.

**Option C — Anonymous self-service with later identity proofing.** Citizen submits an application without a portal account; the operator verifies identity at the collection point against the NID printed on the voucher. Lowest tech effort, but loses traceability of submission-time identity.

**Recommendation.** Start with **Option C for FY26-27 pilot** (low risk, fast to deploy), evolve to Option B in year 2, target Option A as a 3-year goal aligned with the national digital ID roadmap.

**Effort (Option C):** 2-3 days — the system already supports operator-submitted applications. Add a kiosk mode or assisted-registration flow.

**Owner.** Architecture decision before UAT. Then us (impl) + MAFSN policy lead.

---

### 1.3 — Multi-factor authentication

**Required for:** sysadmin, finance officer (budget adjustments), district supervisor (final approval rights).
**Optional for:** field officer, citizens.

**Implementation.** Driven by Keycloak — once SSO is in place, MFA is a Keycloak realm policy (TOTP via Google Authenticator, SMS OTP, etc.). No code change in Joget.

**Effort.** 1 week elapsed (policy decision + Keycloak config + user enrolment).
**Owner.** MAFSN ICT (Keycloak policy) + us (Joget policy mirror — session lifetime, IP-based controls).

---

### 1.4 — Role-Based Access Control (RBAC) wired to actual users

**Status.** The role MODEL exists in `mm_role` + `mm_role_screen`. No actual user is assigned a role; the userview has no per-role permissions. Operators currently see everything.

**Gap.** Until we wire roles to users, every UAT participant sees every menu, which makes role-specific scenarios impossible to test.

**Steps.**
1. Define the live role taxonomy (workshop output from §1.1).
2. Per Joget userview menu, add a `permission` block restricting visibility to a specific role.
3. Assign Keycloak groups → Joget roles → users.
4. Smoke-test: log in as each role, verify the menu is what we expect.

**Effort.** 3-5 days (depends on how many roles × menus × discrimination rules).
**Owner.** Us.

---

### 1.5 — Service accounts for batch jobs

**Examples.** Voucher expiry sweeper, eligibility worker, audit archive script, MV refresh. These currently run as no specific user (or hard-coded `admin`).

**Required.** Each batch job runs as a service account with documented permissions. Audit trail captures the actual job, not "admin".

**Effort.** 2 days.
**Owner.** Us (Joget config) + MAFSN ICT (account approval).

---

## Section 2 — Communication infrastructure

### 2.1 — Email server (SMTP) integration

**Use cases.** Operator notifications (decision pending, escalation), application receipts to the citizen, weekly digest to district supervisor, alert emails to the finance officer.

**Implementation.** Joget DX 8 ships with `EmailTool`. Configuration is one-time:

- SMTP host, port, TLS settings.
- Service account on the SMTP server.
- "From" address (e.g. `noreply@mafsn.gov.ls`).
- Bounce-handling mailbox.

**Templates.** Multilingual (English + Sesotho) email templates per trigger event. Today: zero templates exist. We need ~12 templates spanning the application + voucher + budget + audit lifecycles.

**Effort.** 1 week (3 days config + 4 days template authoring + Sesotho translation).
**Owner.** Us (config + EN templates) + MAFSN comms (Sesotho translation).

**Reference.** `minagri_it_todo.md §1` covers most of this.

---

### 2.2 — SMS gateway

**Why critical.** Mobile network coverage (~85% of Lesotho) far exceeds smartphone penetration (~30%). SMS is the realistic citizen channel.

**Decision pending.** Which gateway?

| Option | Pros | Cons |
|---|---|---|
| Direct via mobile operators (Vodacom Lesotho, Econet Lesotho) | Highest deliverability, lowest cost per SMS | Two contracts, manual integration per operator |
| Aggregator (Twilio, Africa's Talking, Smshub) | One API, multi-operator | Higher per-SMS cost; data leaves country (compliance review needed) |
| Government SMS service (if MAFSN has one) | Aligns with government infrastructure | May not exist; deliverability variable |

**Recommended:** start with **Africa's Talking** (Pan-African aggregator with strong Lesotho operator coverage) for pilot; migrate to direct operator agreements at scale.

**Volume estimate.** 4 SMS per applicant lifecycle × 50K applicants/year = 200K SMS/year ≈ M2,000 LSL/month at typical aggregator rates.

**Implementation.** Joget Marketplace has SMS plugins; we'd write a thin connector specific to Africa's Talking.

**Effort.** 2-3 weeks (gateway selection 1w, contract & integration 1w, Sesotho templates + retry logic 1w).
**Owner.** Us (integration) + MAFSN procurement (gateway contract).

---

### 2.3 — USSD channel

**Question.** Do we want citizens with feature phones (no smartphone, no data) to be able to register / check application status via USSD?

**If yes.** Significant additional scope: USSD gateway integration, menu-tree design, session state, operator-shared shortcodes. **2-3 months effort.**

**If no.** Field officers register such citizens at the village level (paper → form). System sees the operator's submission, not the citizen's.

**Decision pending.** This was flagged in the demo Q&A. Default to "no" for FY26-27 pilot, revisit in year 2.

---

### 2.4 — Push notifications / in-app messaging

**Mobile app does not exist.** Skip until/unless a native app is built. The browser-based portal can show an unread count via polling — implementable but not a priority.

---

## Section 3 — Network, infrastructure, deployment

### 3.1 — Production environment topology

**Current.** Single Azure VM (20.87.213.78), Tomcat on port 8080, public IP, no TLS, no firewall, single Postgres on Azure managed service.

**Target.**

```
                ┌──────────────┐
                │ HTTPS (443)  │
                │ Public DNS   │  farmers.mafsn.gov.ls
                └──────┬───────┘
                       │
                ┌──────▼───────┐
                │  WAF / CDN   │  Azure Front Door / Cloudflare
                └──────┬───────┘
                       │
                ┌──────▼───────┐
                │ Load balancer│  Two Tomcat nodes minimum
                └──────┬───────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
   ┌────▼────┐    ┌────▼────┐    ┌────▼────┐
   │ Tomcat 1│    │ Tomcat 2│    │ Worker  │  scheduled tasks node
   │ (Joget) │    │ (Joget) │    │ (Joget) │  (eligibility queue,
   └────┬────┘    └────┬────┘    └────┬────┘   sweeper, MV refresh)
        │              │              │
        └──────────────┼──────────────┘
                       │
              ┌────────▼─────────┐
              │ Postgres primary │  + read replica for reporting
              │  Azure managed   │  + automated backups (PITR)
              └──────────────────┘
```

**Tasks.**

1. **DNS** — register and delegate `farmers.mafsn.gov.ls` (or chosen subdomain).
2. **TLS certs** — Let's Encrypt with auto-renewal, or government CA if MAFSN policy requires.
3. **WAF** — Azure Front Door Standard or Cloudflare. Block obvious attack patterns; rate-limit per IP.
4. **Load balancer** — Azure Load Balancer in front of two Tomcat instances, sticky sessions for Joget's session model (or move Joget sessions to Redis — bigger task).
5. **Tomcat hardening** — security headers (X-Frame-Options, CSP), connection pool tuning, JVM heap right-sized for traffic.
6. **Worker isolation** — scheduled tasks (voucher expiry sweeper, eligibility worker drain, MV refresh, audit archive) on a dedicated Joget node so heavy batch work doesn't compete with citizen requests.
7. **Backup** — already documented in `backup_restore_runbook.md`. Verify in production.
8. **Network segmentation** — Postgres on private subnet, only Tomcat nodes can reach it.

**Effort.** 2-4 weeks. Heavy lift; most of it is MAFSN ICT or partner. We define the topology and review their config; we do not own infrastructure provisioning.

**Owner.** MAFSN ICT lead (provisioning) + us (architecture review, Joget-side config).

---

### 3.2 — High availability / disaster recovery

**Defined targets needed.**
- **RTO** (Recovery Time Objective): how long can the system be down before it's a crisis? Suggest: 4 hours for office hours, 24 hours overnight.
- **RPO** (Recovery Point Objective): how much data loss is acceptable? Suggest: 15 minutes (driven by Postgres PITR).

**HA setup.** Two-node Tomcat behind LB (above) handles single-node failure. Postgres managed service handles DB failure with automatic failover (Azure default).

**DR setup.** Off-region backup of database + JWA + uploaded files, restore procedure tested quarterly.

**Effort.** 2 weeks (definition + implementation + first DR drill).
**Owner.** MAFSN ICT + us.

**Reference.** `backup_restore_runbook.md`.

---

### 3.3 — Performance & scaling

**Current baseline.** `docs/implementation/perf_baseline.md` documents single-VM benchmarks. Adequate for ~50 concurrent operators, ~500 daily citizen submissions.

**Production target.** Inputs needed from MAFSN:
- Peak concurrent operators? (likely <100)
- Peak citizen submissions? (5K/day during application window?)
- Peak voucher redemptions? (10K/day during distribution window?)

**Tuning items.**
- JVM heap (4-8 GB for application nodes; 1-2 GB for worker).
- Tomcat thread pool (200-400 max threads).
- Postgres connection pool size, query plan caching.
- Joget ehcache configuration review (form definitions, datalist results).
- CDN for static assets (Bootstrap, jQuery, etc. ship from Joget; offload to Front Door).

**Effort.** 1-2 weeks.
**Owner.** Us (tuning) + MAFSN ICT (capacity plan sign-off).

---

### 3.4 — Monitoring, logging, observability

**Missing today.** No APM, no centralised logs, no alerting. If the system crashes at 3 AM, no one knows until an operator complains.

**Target stack.**
- **Metrics:** Prometheus + Grafana (or Azure Monitor for the platform).
- **Logs:** Centralised log aggregation (ELK / Loki / Azure Log Analytics).
- **Alerts:** PagerDuty or Opsgenie integrated with the on-call roster.
- **Uptime checks:** Pingdom or equivalent on `https://farmers.mafsn.gov.ls/jw/web/login`.
- **APM:** New Relic Free tier OR Tomcat JMX → Prometheus exporter → Grafana.

**Critical alerts (Day-1).**
- Tomcat down (uptime check fails).
- Postgres connection pool exhausted.
- Disk > 80% on application or DB nodes.
- Eligibility worker queue backlog > 100 items.
- Budget event posting failure (single failure = page someone).
- 5xx error rate > 1% over 5 minutes.

**Audit-log forwarding.** If MAFSN has a SIEM (Splunk, etc.), forward `app_fd_reg_bb_eval_audit` + `app_fd_audit_log` to it for security analysis.

**Effort.** 2-3 weeks.
**Owner.** MAFSN ICT (infra + tooling) + us (alert thresholds + dashboard authoring).

---

### 3.5 — Security: pen test + OWASP review

**Required before go-live.** A government-facing portal handling citizen PII and budget transactions must have an external security audit signed off.

**Scope (typical).**
- OWASP Top 10 coverage: injection, broken auth, XSS, broken access control, security misconfig, vulnerable deps.
- API security review (the api-builder endpoints).
- Authentication / session-management review (post-Keycloak).
- Data-in-transit (TLS, HSTS).
- Data-at-rest (DB encryption, backup encryption).
- File-upload sanitization (parcel photos, signatures, supplier invoices).
- Rate limiting / brute-force defences on login.
- Audit-trail integrity (can a privileged user delete their own audit row?).

**Effort.** 2-4 weeks elapsed (pen test runs ~2 weeks; remediation + retest ~2 weeks).
**Owner.** Third-party auditor (cost: M250-500K LSL typical) + us (remediation).

---

### 3.6 — Secret management

**Current.** API keys, DB credentials, SMTP password, etc. live in plaintext config files. Adequate for dev; unacceptable for production.

**Target.** Azure Key Vault (or HashiCorp Vault if MAFSN policy prefers), Joget config references vault-stored secrets at startup. Rotation policy: 90 days for service-account passwords, 30 days for batch-job tokens.

**Effort.** 1 week.
**Owner.** MAFSN ICT.

---

## Section 4 — Functional gaps

### 4.1 — Notifications wiring (the silent system)

Every state change today is silent. Until SMS / email is wired, the citizen has no idea their application moved. Reference `minagri_it_todo.md §1`.

**Specific triggers needed.**
- Application submitted → SMS receipt + email if address known.
- Application auto-approved → SMS with voucher code.
- Application rejected → SMS with reason.
- Voucher 7 days from expiry → SMS reminder.
- Voucher cancelled → SMS notification.
- Voucher redeemed (each call) → SMS receipt with running balance.
- Budget envelope >75% → email to finance officer.
- Budget envelope frozen → email + SMS to district supervisor.

**Effort.** 1 week (post-SMS-gateway from §2.2).
**Owner.** Us.

---

### 4.2 — Off-line capability for collection points

**The scenario.** Field officer at a rural collection point. Network connectivity is intermittent. A farmer arrives to redeem a voucher. The officer cannot wait 30 minutes for connectivity; the farmer cannot return tomorrow.

**Today's behaviour.** Officer is blocked. Cannot redeem. Cannot record.

**Target behaviour.** A lightweight "field-officer companion" that:
- Caches voucher data for the assigned collection point (encrypted, expires after 24h).
- Allows redemption to be recorded locally with a "pending sync" flag.
- Syncs to the server when connectivity returns; resolves conflicts (server wins, with audit trail).

**Two implementation options.**

**Option A — Progressive Web App (PWA).** Build a thin PWA that installs on the officer's phone, caches data via service worker, syncs via the existing REST endpoints. ~6-8 weeks effort. Cleanest from MAFSN's perspective: zero app-store distribution.

**Option B — Native mobile app (Android first).** ~4-6 months effort, requires app-store presence, signing keys, version management. Too much for FY26-27.

**Recommendation.** Defer for the pilot. Pilot at collection points with reliable connectivity. **This is the single largest deferred item.**

**Effort if taken.** 6-8 weeks (PWA path).
**Owner.** Us, with MAFSN field-ops input.

---

### 4.3 — Mobile-device readiness

**Today.** Forms render in mobile browsers (Joget responsive theme). The GIS widget works on Chrome/Safari mobile. Photo upload uses the browser's camera API.

**Gaps.**
- Touch-target sizes not audited (some buttons may be too small).
- No "save as draft, resume later" UX for long forms on shaky connections.
- Map tile downloads on mobile data are heavy (~1-2 MB per session) — not a blocker but worth a CDN.
- No tested matrix of {iOS, Android} × {Chrome, Safari, Samsung Internet} × {3 screen sizes}.

**Effort.** 1-2 weeks (audit + fixes).
**Owner.** Us.

---

### 4.4 — Multilingual UI (Sesotho)

**Status.** UI is English-only. Joget supports localisation natively (`messages_st.properties` style). Effort to localise:

- Joget core UI strings (theme labels, errors): ~2 days, mostly using existing community translations.
- Form-field labels (custom to our app): ~1 week — every form, every field.
- Datalist column labels, userview menu labels: ~3 days.
- Email/SMS templates: see §2.1, §2.2.
- Operator manual + UAT guide: ~2 weeks (outside the codebase).

**Effort.** 4-5 weeks for full UI Sesotho localisation.
**Owner.** Us (technical) + MAFSN comms (translation).

---

### 4.5 — Citizen-channel polish

If we accept the §1.2 "Option C" recommendation (operator-mediated registration), the citizen-facing surface is mostly the kiosk / district-office assisted flow. The portal already supports this; what's missing is:

- Receipt printing from the operator's screen at registration (we have voucher slip print; extend to application receipt).
- Status-check kiosk (citizen enters NID, sees state of all their applications).
- Multilingual kiosk mode.

**Effort.** 1-2 weeks.
**Owner.** Us.

---

## Section 5 — UAT preparation

### 5.1 — UAT environment

**Need.** A separate Joget instance from dev, with:
- Same plugins as prod.
- Anonymised real-shape data (~500 farmers, ~50 active applications, ~20 vouchers).
- Same Keycloak realm config (or dedicated UAT realm).
- Same network topology (mini version of prod).

**Effort.** 1 week to provision + 1 week to seed.
**Owner.** MAFSN ICT (infra) + us (data seeding from `app/seeds/lesotho-mm-fixture.yaml`).

---

### 5.2 — UAT data fixtures

**What we need.** Real-shape test data covering edge cases:
- 100+ farmers across all 10 districts and all 4 agro-zones.
- Farmers with diverse household compositions, livestock, crops.
- Pre-saved parcels with valid GIS polygons.
- Pre-launched programmes (1 active, 1 closed, 1 future).
- Pre-issued vouchers (issued, redeemed, expired, cancelled — all states).

**Anonymisation.** Real names replaced with synthetic ones; real IDs replaced with synthetic NIDs that match Lesotho's NID format.

**Effort.** 1 week.
**Owner.** Us.

---

### 5.3 — UAT scope and entry/exit criteria

**Reference.** `_07_Training/_uat_guide/UAT_Guide.md` exists with scenarios. **Needs entry/exit criteria added.**

**Entry criteria (suggested).**
- All Section 1-4 production-readiness items at minimum dev-grade complete.
- Keycloak SSO working in UAT environment.
- SMS gateway working in UAT (test number whitelist).
- Email gateway working in UAT (no real recipients yet — internal address only).
- All user roles assignable via Keycloak.
- UAT data seeded.
- Defect-tracking tool configured (Jira / Azure Boards / GitHub Issues).
- UAT participants identified, trained for 1 hour on the system.

**Exit criteria (suggested).**
- Zero `Critical` defects (system unusable for a primary scenario).
- Zero `High` defects in citizen-facing flows; ≤5 in operator flows with workarounds.
- All 30 UAT scenarios from `UAT_Guide.md` passed, signed off by a named operator per role.
- Documented deferred items list with target dates.
- Sign-off from: District Supervisor (operations), Finance Officer (budget), MAFSN ICT (technical), Architecture lead (us).

**Effort.** 3 days to draft + workshop + sign-off.
**Owner.** Joint.

---

### 5.4 — Defect management process

**Required.**
- Tracker: Jira / Azure Boards / GitHub Issues — pick one. Recommend Azure Boards if MAFSN already uses Azure.
- Severity definitions: Critical / High / Medium / Low with response-time SLAs.
- Triage daily during UAT.
- Hotfix path: defect → fix → review → deploy to UAT → retest. Target turnaround: 24h for High, 72h for Medium, weekly for Low.

**Effort.** 2 days setup.
**Owner.** Architecture lead (us).

---

## Section 6 — UAT execution including on-site and off-line

### 6.1 — Test matrix

| Dimension | Coverage |
|---|---|
| **Roles** | Field officer, district supervisor, finance officer, sysadmin, citizen (kiosk-mediated for pilot) |
| **Devices** | Office desktop (primary), iPad (district supervisor), Android phone (field officer), feature phone (out of scope for pilot) |
| **Browsers** | Chrome, Edge, Safari (iOS), Samsung Internet, Firefox |
| **Screen sizes** | 1920×1080 desktop, 1024×768 tablet, 375×667 phone |
| **Network** | Office wired, office Wi-Fi, 4G, 3G, intermittent (deliberately bad) |
| **Locations** | Maseru HQ, district office (1-2), rural collection point (1-2) |

### 6.2 — On-site testing programme

**Pilot district.** Pick one district (e.g. Mohale's Hoek — manageable size, mix of urban/rural).

**Sites.**
- District HQ — office testing, all roles.
- Resource centre — field officer + voucher redemption.
- 2-3 rural collection points — voucher redemption under poor connectivity.
- Citizen-mediated — village-level field officer registers a citizen.

**Schedule.** 2 weeks on-site, 1 visit per site per week.

**Specific scenarios to test on-site.**
1. Field officer registers a new farmer with photo capture.
2. Field officer captures a parcel polygon by walking the boundary with GPS.
3. District supervisor reviews and approves a stack of applications on a tablet.
4. Field officer redeems a voucher under intermittent 3G.
5. Citizen at kiosk views their application status.
6. Connectivity dies mid-form — what happens? Does data survive a browser refresh?

**Effort.** 2 weeks + 1-2 staff per site × 2 weeks = significant logistics.
**Owner.** MAFSN field operations (host) + us (presence + observation).

### 6.3 — Off-line behaviour testing (if §4.2 is in scope)

If the PWA / off-line companion is built (§4.2), explicit test scenarios:
- Officer's phone has voucher data cached. Network down. Redemption recorded locally.
- Network returns. Sync runs. Server-side state updates.
- Officer redeems voucher offline; another officer at a different point also redeems same voucher offline (rare but possible). Sync detects conflict; one redemption rejected with audit row.

If §4.2 is deferred, off-line testing is "what happens when the network breaks mid-operation" — answer: rage, data loss, escalation. Document this as a known limitation for the pilot.

### 6.4 — Performance under load

**Concurrent-user simulation.** `tooling/load_test.py` exists. Run during UAT:
- 100 concurrent citizens submitting applications (peak season simulation).
- 50 concurrent operators reviewing in the inbox.
- 200 voucher redemptions/hour at peak distribution.

Verify:
- Response time p95 < 2 seconds for navigation, < 5 seconds for form save.
- Database connection pool not saturated.
- No memory leak over 4-hour run.

**Effort.** 3 days + remediation if anything slow.

### 6.5 — Accessibility & usability

**Audit dimensions.**
- Visual: contrast ratios, font sizes (operators may be 50+).
- Motor: form-field tab order, keyboard-only navigation.
- Cognitive: form complexity, error messages, recovery from mistakes.
- Literacy: instructions in plain Sesotho, numeric input clarity.

**Effort.** 1 week, ideally with a UX person reviewing.

---

## Section 7 — Training & enablement

### 7.1 — Operator training

**Already exists.** Per-role one-pagers (`_07_Training/`) covering:
- Field officer: register a farmer, capture a parcel, register an application.
- District supervisor: review and approve.
- Finance officer: budget dashboard, manual adjustments.
- Sysadmin: user management, scheduled tasks.

**Needed.**
- 2-day train-the-trainer session: 4-6 master trainers from MAFSN ICT + ops.
- Cascading rollout: master trainers train district supervisors (1 day per district × 10 districts).
- Field-officer training: short (2-hour) sessions at resource centres, hands-on with sandbox device.

**Materials gap.** Existing one-pagers are EN-only; need Sesotho versions for field officers.

**Effort.** 4-6 weeks elapsed (parallel with UAT).
**Owner.** MAFSN training lead + us (curriculum).

### 7.2 — Citizen orientation

For the kiosk-mediated model: a printed leaflet at every district office and resource centre explaining how to register, what NID is needed, expected timelines. Sesotho + English.

**Effort.** 1 week (content + printing).
**Owner.** MAFSN comms.

---

## Section 8 — Hand-off & operations

### 8.1 — Support model

**Define.**
- L1 (helpdesk): MAFSN ICT helpdesk. Resets passwords, walks through "how do I" questions, escalates real defects.
- L2 (system admin): MAFSN ICT system administrator. Investigates errors, runs scheduled tasks manually if needed, restores from backup if needed.
- L3 (vendor / us): code-level fixes, plugin redeploys, schema changes.

**Documentation needed for each level.** L1 and L2 docs largely missing. L1 needs a "common citizen questions" + screen-by-screen reference. L2 needs a "common errors and what to do" runbook (extending `backup_restore_runbook`).

**Effort.** 2 weeks.
**Owner.** Joint.

### 8.2 — SLA

**Define between MAFSN and us.**
- Response time per severity (Critical: 1h, High: 4h, Medium: 1d, Low: 5d).
- Maintenance windows (Sunday 02:00-06:00 typical).
- Patch frequency (monthly minor, quarterly major).
- Vulnerability response (CVE high: 30 days, critical: 7 days).

**Effort.** 1 week negotiation.
**Owner.** Architecture lead + MAFSN procurement.

### 8.3 — Knowledge transfer

**Risk.** This codebase has subtle gotchas (D49 centring saga, the partiallyStore wizard quirk, the metamodel layer). If MAFSN ICT inherits this without sustained handover, a year-2 modification could regress the system badly.

**Plan.**
- 2-week shadowing period: 1 MAFSN engineer pairs with us.
- Code-walkthrough sessions (4 × 2-hour) covering: rule engine, budget engine, metamodel layer, custom plugins.
- Architecture decision log (already exists at `docs/architecture/decision-log.md`) — make sure they have read access and we walk through it together.

**Effort.** 4 weeks (parallel with UAT).
**Owner.** Us (deliver) + MAFSN (assign engineer).

---

## Section 9 — Cutover & go-live

### 9.1 — Cutover plan

**Three-stage rollout.**

**Stage 1 — Pilot (Q4 2026).** One district, one programme, one season. ~2,000 farmers, ~500 applications, ~300 vouchers. Duration: 8-12 weeks. Success criteria: zero data loss, ≤5% application reject rate due to system errors (not eligibility errors).

**Stage 2 — Expansion (Q1-Q2 2027).** Add 3-4 districts, retain single programme. Capacity stress test. Address pilot defects.

**Stage 3 — National rollout (Q3 2027 onwards).** All 10 districts, multiple programmes, full capacity.

### 9.2 — Communications plan

- Citizens: SMS announcement when registration opens; printed leaflets at chiefs' offices.
- Operators: pre-launch all-hands; daily standup during pilot.
- Donors: monthly status report with budget burn-down + voucher KPIs.

### 9.3 — Risk register (top 5)

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| 1 | Keycloak integration delayed → UAT entry blocked | Medium | High | Start §1.1 immediately; have a "local-auth UAT" fallback plan |
| 2 | SMS gateway contract takes longer than expected | High | High | Start procurement in parallel with build; have email-only fallback for pilot |
| 3 | Field officer connectivity worse than expected | Medium | Critical | Pilot in connectivity-friendly district first; defer rural-rural until §4.2 PWA is built |
| 4 | Pen test finds Critical issue close to go-live | Medium | High | Run pen test mid-UAT, not at the end; budget 4 weeks for remediation |
| 5 | MAFSN ICT capacity to absorb knowledge | High | Medium | Start §8.3 early; insist on dedicated MAFSN engineer through pilot |

### 9.4 — Go / no-go criteria

A formal go-no-go meeting at end of UAT, before pilot launch. **Go** requires:
- All UAT exit criteria met (§5.3).
- Pen test remediation complete and signed off.
- Backup/restore verified in UAT.
- DR drill executed successfully.
- Support model live with named L1/L2/L3 contacts.
- Pilot communications plan launched.

---

## Section 10 — Effort summary & timeline

```
                                  Weeks
                          0   2   4   6   8   10  12  14  16  18  20
Section 1 — Identity       ████████████
Section 2 — Comms              ████████
Section 3 — Network            ████████████████
Section 4 — Functional gaps        ████████████
Section 5 — UAT prep                       ████████
Section 6 — UAT execution                          ████████████████
Section 7 — Training                       ████████████████
Section 8 — Hand-off                               ████████████
Section 9 — Cutover                                            ████
                        ──────────────────────────────────────────────
Cumulative effort      Demo───────────UAT-ready───────UAT done─────GO-LIVE
```

**Critical path.** Keycloak (§1.1) → RBAC wiring (§1.4) → UAT prep (§5) → UAT execution (§6) → cutover (§9). Anything in the critical path that slips, slips the whole thing.

**Effort estimate range.**
- **Optimistic (everything in parallel, no blockers):** 10 weeks.
- **Realistic (typical govt project pace, expected blockers):** 14-16 weeks.
- **Pessimistic (Keycloak + SMS gateway both delayed):** 20+ weeks.

**Cost categories not in this document.**
- Pen test: M250-500K LSL.
- SMS volume: ~M2K/month at pilot scale, M20K+/month at national scale.
- Production hosting: Azure costs, ~M15-30K/month for HA setup.
- Vendor support contract: typically 15-20% of build cost annually.

---

## Section 11 — Open decisions list

The following must be decided BEFORE we can commit to a sequence and timeline. Each has a direct downstream effect on Sections 1-9.

1. **Identity strategy for citizens** (§1.2). Default: Option C (operator-mediated) for pilot.
2. **SMS gateway** (§2.2). Default: Africa's Talking.
3. **USSD scope** (§2.3). Default: out of scope for pilot.
4. **Off-line support for collection points** (§4.2). Default: out of scope for pilot, lessons-learned drives PWA build for stage-2 expansion.
5. **Sesotho UI** (§4.4). Default: forms localised pre-pilot; manuals at pilot launch.
6. **Pilot district** (§9.1). Default: Mohale's Hoek.
7. **Pen-test vendor selection.** No default — needs procurement.
8. **Hosting platform.** Azure currently used; confirm vs alternatives (on-prem MAFSN data centre, AWS, etc.).
9. **Vendor support model post-go-live.** Continuing-engagement contract or hand-off-and-walk-away?

---

## Section 12 — What we DON'T need to do (deliberate non-goals for FY26-27)

**Capturing this so we don't accidentally scope-creep into them.**

- Native mobile apps. PWA only if §4.2 is in.
- USSD channel.
- Multi-tenancy (one MAFSN, one app — no white-label).
- Real-time analytics dashboards (existing batch-refreshed datalists are sufficient).
- AI / ML scoring for eligibility (rules engine is the source of truth).
- Integration with national digital ID (year-3 goal).
- Multi-currency / multi-country support.
- Financial transactions (no card processing, no bank integration — vouchers are the value transfer).

---

## Appendix A — Cross-reference to existing documents

| Topic | Existing doc | Status |
|---|---|---|
| MinAgri ICT to-do list | `docs/operations/minagri_it_todo.md` | Useful baseline; this roadmap supersedes the production-prep parts |
| Backup & restore | `docs/operations/backup_restore_runbook.md` | Comprehensive; reference unchanged |
| Performance baseline | `docs/implementation/perf_baseline.md` | Dev-grade numbers; needs prod re-run |
| Payment integration | `_07_Implementation_Guides/for_customer/payment_integration_scoping_note.md` | Scoping done; out of scope for FY26-27 |
| VAT / tax | `_07_Implementation_Guides/for_customer/vat_tax_decision_note.md` | Decision recorded |
| API migration | `docs/migration/api_migration_guide.md` | Customer-facing migration path |
| UAT scenarios | `_07_Training/_uat_guide/UAT_Guide.md` | 30 scenarios; needs entry/exit criteria added |
| User manual | `_07_Training/_user_manual/User_Manual.md` | EN-only; needs Sesotho |
| Architecture decisions | `docs/architecture/decision-log.md` | Up to D49 |

## Appendix B — Glossary

- **MAFSN** — Ministry of Agriculture, Food Security and Nutrition (Lesotho).
- **NID** — National Identity (citizen identifier issued by Ministry of Home Affairs).
- **MD** — Master Data (lookup table).
- **mm_** — Metamodel (analyst-authored configuration tables).
- **RBAC** — Role-Based Access Control.
- **SSO** — Single Sign-On.
- **MFA** — Multi-Factor Authentication.
- **WAF** — Web Application Firewall.
- **LB** — Load Balancer.
- **PWA** — Progressive Web App.
- **APM** — Application Performance Monitoring.
- **SIEM** — Security Information and Event Management.
- **RTO / RPO** — Recovery Time Objective / Recovery Point Objective.
- **SLA** — Service Level Agreement.
- **PII** — Personally Identifiable Information.
- **CDN** — Content Delivery Network.
- **MV** — Materialised View (Postgres).

---

*Document status: Draft 1, 2026-05-08. To be revised after MAFSN review of the open-decisions list (Section 11).*
