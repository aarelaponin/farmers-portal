# START HERE — Returning to the Farmers Portal

You are Aare, returning to this project after a break. You don't remember details. This document gets you oriented, then gets the system running again. Read it linearly the first time; later it's a reference.

Last edited: **2026-06-01**, after a full sync pull from the dev Azure VM. The repo at that moment is the authoritative snapshot of everything that was deployed.

---

## 1. The 60-second orientation

**What this is.** The Farmers Portal (a.k.a. LST, a.k.a. Lesotho Subsidy / ASMS) is an agricultural-subsidy management system for the Ministry of Agriculture, Food Security and Nutrition (MAFSN) of the Kingdom of Lesotho. Built on **Joget DX 8.1.1** as an implementation of GovStack's Registration Building Block (RegBB). One app, app id `farmersPortal`.

**Where it lived.** A dev Azure VM at `http://20.87.213.78:8080/jw` with a Postgres on Azure (`joget-pgsql-sa.postgres.database.azure.com`). HTTP-only because dev. Fictional test data only. The VM became unreachable on 2026-06-01 — that was the trigger for the full snapshot pull captured in this repo.

**Where it lives now.** Public GitHub at `github.com/aarelaponin/farmers-portal`. This is the source of truth for everything reproducible. If the Azure VM never comes back, the repo can rebuild the system end-to-end on any Joget DX 8.1.1 instance.

**Who's on the project.** You (Aare) — solo developer + architect. MAFSN ICT has no support staff yet. Khotso at MAFSN handles operational decisions (SMTP, UAT, infra requests). Altron retains VM admin access on the Azure dev VM (you never had filesystem access). Plans were for MAFSN to bring in implementation team after UAT.

**What state it was in.** Pre-UAT. W1–W4 of the production-readiness roadmap were complete (RBAC, notifications, lifecycle state machine, perf baseline + index tuning). UAT was waiting on MAFSN-provisioned UAT environment, Keycloak realm, SMTP gateway choice, named participants — all blocked on Khotso's responses.

**What you were doing the day you stopped.** Closed the "stranger can install from repo" gap end-to-end: wrote `INSTALL.md`, `tooling/install_app.py`, `plugins/build-all.sh`, the `make sync` automation extension that captures App Builder + operational data, and committed everything to GitHub. The repo is now a complete restore source.

---

## 2. Repository map

Everything you need is in this repo. Where to find what:

```
.
├── START_HERE.md          ← you are here
├── README.md              ← public-facing project summary
├── INSTALL.md             ← end-to-end install playbook for a fresh Joget
├── CONTRIBUTING.md        ← team handover doc
├── CLAUDE.md              ← AI-assistant operating notes; 1000+ lines of hard-won
│                            Joget gotchas, debugging recipes, source-reading
│                            discipline. ALWAYS read first before touching code.
├── .env.example           ← template for local config
├── Makefile               ← the make targets (sync, fresh-install, test*)
├── LICENSE                ← Apache 2.0
│
├── app/                   ← Joget application artefacts (the canonical state)
│   ├── forms/             ← 218 form JSON definitions
│   ├── datalists/         ← 226 datalist JSON definitions
│   ├── userviews/         ← 1 userview JSON (the navigation)
│   ├── api-builder/       ← 108 App Builder API endpoint definitions
│   ├── form-specs/        ← analyst-authored YAML specs (where forms come from)
│   ├── seeds/
│   │   ├── master-data/   ← 117 md_* + mm_* tables, ~1k rows (config catalogs)
│   │   ├── data/          ← 173 operational tables, ~10k rows (subsidy apps,
│   │   │                    vouchers, registry, audit, budget ledger, etc.)
│   │   ├── environment.yaml  ← 2 env-var rows (Id Generator counters)
│   │   ├── lesotho-mm-fixture.yaml ← legacy seed (superseded by master-data/)
│   │   ├── files/         ← uploaded file blobs (sync'd separately if at all)
│   │   ├── migrations/    ← any one-shot data migrations
│   │   └── test-files/    ← test fixtures
│   ├── snapshots/         ← JWA exports (Joget-native app archive)
│   │   └── APP_farmersPortal-1-20260601134754.jwa  ← native restore fallback
│   └── branding/          ← logos, favicons
│
├── plugins/               ← 27 plugin source trees + Marketplace API Builder JAR
│                            (gitignored locally). See plugins/BUILD.md for the
│                            mandatory-12-vs-optional-15 split.
│   ├── BUILD.md           ← which plugins are mandatory, how to build
│   ├── build-all.sh       ← `make build-plugins` calls this
│   ├── form-creator-api/  ← THE push API. Build first; everything else depends.
│   ├── reg-bb-engine/     ← THE runtime: MetaScreenElement, RoutingEvaluator,
│   │                        status framework, notifier, Budget engine, IM tools
│   ├── reg-bb-publisher/  ← userview publishing helpers
│   ├── joget-status-framework/  ← lifecycle state machine (embedded in reg-bb-engine)
│   ├── joget-gis-ui/      ← GIS polygon capture widget (parcelGeometry)
│   ├── joget-gis-server/  ← overlap-check API the polygon widget uses
│   ├── joget-smart-search/  ← typeahead lookup widget
│   ├── joget-concat-field/  ← derived-field widget
│   ├── joget-advanced-filters/  ← datalist filter types
│   ├── embedded-datalist/ ← embed child datalist in form
│   ├── parcel-zone-centring/  ← AutoCenterBootstrapElement
│   ├── form-quality-runtime/  ← quality probes + audit log
│   ├── farmer-derived-plugin/  ← derived fields on farmer profile
│   └── (15 optional / legacy plugins)
│
├── tooling/               ← Python scripts (need venv: `bash tooling/bootstrap.sh`)
│   ├── bootstrap.sh       ← one-time venv setup
│   ├── requirements.txt   ← PyYAML + psycopg2-binary
│   ├── sync.sh            ← `make sync` calls this (pulls + commits)
│   ├── sync_pull.py       ← the SELECT-only Postgres dumper
│   ├── install_app.py     ← inverse: pushes everything in app/ to a Joget
│   ├── seed.py            ← legacy fixture seeder (mostly superseded)
│   ├── push_form.py, push_datalist.py, push_userview.py
│   ├── test_*.py          ← e2e regression suites (7 of them)
│   ├── run_l4_scenarios.py  ← eligibility parity test (20 scenarios)
│   ├── load_test.py       ← read-side perf baseline
│   └── (many one-shot migration / patch scripts — historical)
│
├── docs/                  ← project documentation
│   ├── architecture/      ← ADRs (033 most recent), convergence framework,
│   │                        GovStack alignment, decision log, component SADs
│   │                        — the timeless reasoning
│   ├── implementation/    ← roadmaps, perf baseline, the pause-and-resume plan
│   ├── operations/        ← MAFSN ICT runbooks: backup/restore, SMTP, TO-DO
│   ├── developer/         ← auto-generated API reference (57+ endpoints)
│   ├── migration/         ← pilot-data migration guide + sample script
│   └── notifications/     ← markdown source for the 12 lifecycle emails
│
├── jw-community/          ← gitignored. Joget Community Edition source (DX 8.1
│                            branch). Clone before building plugins. Read-only
│                            reference per CLAUDE.md "Reading Joget source".
├── api-builder/           ← gitignored. API Builder source (7.0-SNAPSHOT branch).
│                            Same role as jw-community/.
├── x_archive/             ← gitignored. Historical scratch / staging.
└── dist/                  ← gitignored. Build output of plugins/build-all.sh.
```

If you don't find what you're looking for, grep CLAUDE.md first — it has the highest density of cross-references to files and design decisions.

---

## 3. State as of 2026-06-01

**Last sync**: today, full pull captured forms / datalists / userviews / master-data / API Builder / operational data / env vars. Commit `59d425e`. Public repo is the byte-faithful snapshot of what was deployed.

**Last code change**: install gap closure — `INSTALL.md` + `install_app.py` + `plugins/BUILD.md` + `build-all.sh` + `.env.example`. Commits `9eaa596`, `901ad63`, `7710c89`, `59d425e`.

**Phase you stopped in**: pre-UAT, post-W4. Specifically:

- W1 (Pass A pre-publication cleanup) — done
- W2 (notifications + SMTP + RBAC) — done; production SMTP wiring waiting on Khotso
- W3 (lifecycle state machine + mobile audit + DRAFT/WITHDRAWN/UNDER_REVIEW + case log) — done; one pending follow-up (#335: per-dashboard inner reflow on 6 HtmlPages)
- W4 (perf baseline + index tuning) — done
- W5 onwards (UAT instance + fixtures, kiosk, defect tracker, accessibility audit, Sesotho i18n scaffolding, entry/exit criteria) — **not started**, blocked on MAFSN provisioning

**Open items**:

1. **#335** — W3.4 follow-up: per-dashboard inner reflow on 6 HtmlPages (mobile UX polish). Low priority.
2. **API Builder pull missing 1 endpoint** — sync reported 109, repo has 108. Likely two endpoints with identical `name` field collided on filename. Forensic only; doesn't affect restore.
3. **Phase 5 vs Phase 6 install ordering** — `install_app.py` runs Phase 5 (API provisioning) before Phase 6 (operational data seeding). If Phase 5 partially fails, Phase 6 will still run. Acceptable on a fresh install but worth knowing.
4. **Live shakedown** — `install_app.py` Phase 4 (master-data) and Phase 6 (operational data) are dry-run verified only. First real `make fresh-install` against a new Joget will surface any edge cases (e.g. a YAML row whose business-key column isn't `code`).

**What was about to happen next** (pre-UAT plan): UAT environment provisioning by MAFSN (Khotso request out 2026-05-11), Keycloak realm setup, SMTP gateway choice (Mailtrap free was unusable due to burst throttling — Gmail App Password or AWS SES recommended in `docs/operations/smtp_production_config.md`), named UAT participants, then W5–W8 of the production-readiness roadmap.

**Pause-and-resume plan**: `docs/implementation/uat_prep_pause_resume_plan.md` has the canonical "where I left off, where to restart" walk-through, written for exactly this scenario.

---

## 4. Three-minute architecture refresher

**The shape.** Joget DX 8.1 plus 12 custom plugins. The Lesotho-specific app is `farmersPortal`. Configuration is data, not code: forms / datalists / userviews are JSON definitions stored in `app_form` / `app_datalist` / `app_userview` Postgres tables, mirrored in `app/forms/` / `app/datalists/` / `app/userviews/` of this repo.

**The key insight** — the **mm_\* metamodel**. Twelve `mm_*` tables (`mm_institution`, `mm_service`, `mm_registration`, `mm_screen`, `mm_field`, `mm_catalog`, `mm_action`, `mm_required_doc`, `mm_fee`, `mm_benefit`, `mm_determinant`, `mm_role`) define the service shape. The `reg-bb-engine` plugin reads these rows at runtime and synthesises Joget forms / datalists / dispatch logic dynamically. **Adding a new subsidy programme requires zero Java changes — just rows in the metamodel.** This is the GovStack RegBB pattern: configuration-driven service composition.

**The rule grammar** — `mm_determinant` rows hold Determinant rules (eligibility / quality / bot_pull / decision-to-status / etc.) in a DSL Aare designed (see ADR-001). One unified table, multiple consumers discriminated by `scope` column. The DSL is partitioned: SQL-path expressions, value comparators, JSON envelopes. Rules are authored via App Composer UI, evaluated by `reg-bb-engine`'s `RoutingEvaluator`, audited to `reg_bb_eval_audit`.

**The Budget Engine** — pre-existing financial controls: `budget_envelope` (allocated funds per programme × source), `budget_event` (the ledger), `beneficiary_subledger` (per-applicant accounting), three-state event chain (RESERVATION → COMMITMENT on issue → EXPENSE on redeem). Operator dashboards show variance, drill-downs, sparklines. All in reg-bb-engine.

**The IM module** — Inputs Management: voucher issuance after approval, redemption at distribution points, partial redemption, expiry sweeper, cancellation. Wires through to the Budget Engine for the financial events.

**The lifecycle state machine** — applications transition DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED|REJECTED|PENDING_DOCS → withdrawn|appeal. Status framework (under `joget-status-framework`, embedded in reg-bb-engine) keeps the canonical state per record. Notifications fire on transitions (12 templates total). State transitions audited to `audit_log`.

**Bidirectional sync (ADR-033)** — the repo holds canonical source for forms/datalists/userviews/master-data, but **App Composer edits in Joget don't auto-flow back to the repo**. `make sync` runs the SELECT-only Postgres dumper and commits any drift. Push direction goes via form-creator-api. The two halves are documented in ADR-033 — read that ADR if you forget how this works.

For the rest of the architecture: `docs/architecture/architecture-overview.md` → `docs/architecture/convergence-framework.md` → `docs/architecture/govstack-alignment-may2026.md`. The full ADR series (001–033) lives in `docs/architecture/adr/`. The decision log at `docs/architecture/decision-log.md` is the running narrative — D1 through D56+ as of 2026-06-01.

---

## 5. The sync ritual (how the repo stays canonical)

The dev VM was the only place where the app actually ran. App Composer edits made via the UI never auto-flowed back to the repo — they had to be pulled. The push half (form-creator-api) and the pull half (`sync_pull.py`) are the two sides of ADR-033.

The pull ritual, after any App Composer session or before any long pause:

```bash
make sync          # pulls + auto-commits, no push
make sync-push     # pulls + commits + pushes to origin/main
make sync-dry      # pulls only, shows diff, no commit
```

What it captures: forms, datalists, userviews, all `md_*`/`mm_*` master-data tables, all `app_builder` API endpoint definitions, all other `app_fd_*` operational data tables, plus 2 env-var rows.

What it does NOT capture: plugin JAR binaries (rebuildable), the API Builder Marketplace JAR (downloadable), vendored Joget source (gitignored), and `api_credential` plaintext (irrecoverable; you generate new credentials on restore anyway).

Per ADR-033: operational data (`app_fd_subsidy_app_2025`, `app_fd_im_voucher`, audit logs, registry) was originally excluded as "production data, not source." On 2026-06-01 you explicitly opted in to pull it too, because this dev environment is fictional data only and the goal was full restoration capability. The pull is now complete.

---

## 6. Restoring the environment

### Door A — On your local Mac (recommended for orientation)

This gets you a working Farmers Portal on your laptop in ~60 minutes. Follow `INSTALL.md` step-by-step:

1. Install Joget DX 8.1.1 per Joget's KB (link in INSTALL.md step 2).
2. Configure Joget against a local Postgres.
3. Create the empty `farmersPortal` app in App Composer.
4. Clone `jw-community @ 8.1-RELEASE` and `api-builder @ 7.0-SNAPSHOT` at the repo root (one-time prerequisite — these are gitignored).
5. `make build-plugins` — produces 12 JARs in `dist/plugins/`.
6. Download API Builder from Joget Marketplace (free, 7.0.x) — **not bundled with stock Joget**.
7. Upload all plugin JARs (API Builder first, then form-creator-api, then the rest).
8. Create API Builder credential, fill `.env`.
9. `bash tooling/bootstrap.sh && source tooling/.venv/bin/activate`.
10. `make fresh-install` — pushes 218 forms / 226 datalists / 1 userview, seeds 117 master-data tables, provisions 108 API endpoints, seeds 173 operational tables.
11. Browse to `http://localhost:8080/jw/web/userview/farmersPortal/v/_/home`.

Two minutes of manual after: re-create the 2 env-vars listed in `app/seeds/environment.yaml` in App Composer (Id Generator counters).

If `make fresh-install` fails on a specific phase, the error tells you which JAR is missing or which form has a broken reference. Most failures are missing plugins; check CLAUDE.md's "Custom plugins installed in THIS Joget instance" section to map class names to plugins.

### Door B — On a new Azure VM (production-style)

Same playbook as Door A but with managed Postgres and a real domain. Configure Joget's `wflow/app_datasource-default.properties` to point at managed Postgres. The `.env` template's `JOGET_BASE_URL` and `PG*` vars are the only things that need cloud-specific values. Everything else is identical.

### Door C — JWA-native restore (fallback)

If the Joget-side install path errors and you need a faster restore for demo purposes, the `app/snapshots/APP_farmersPortal-1-20260601134754.jwa` file is the native Joget app archive. Joget Admin → "Import App" → upload that file. This restores forms / datalists / userviews / packageDefinition / environmentVariableList in one shot — but **does NOT include** App Builder API definitions, master-data rows, or operational data. So Door C gives you the UI shell quickly; you still need to run Phase 4 + 5 + 6 of `install_app.py` to fill it.

Use Door C only as a fallback when `install_app.py` Phase 1–3 (forms / datalists / userviews) is having problems and you want to bypass it. The clean path is Door A or Door B.

### Door D — Hand off to a team

The repo + this START_HERE.md is the entire hand-off package. Give a new developer:

1. The repo URL: `https://github.com/aarelaponin/farmers-portal`.
2. This document as their starting point.
3. A pointer at CLAUDE.md (every session).
4. A pointer at `CONTRIBUTING.md` for team workflow.

They follow `INSTALL.md`. There's nothing else they need from you.

---

## 7. Day-to-day operations

All wrapped in the Makefile:

```bash
make help                # see all targets

# Install / restore
make build-plugins       # builds 12 mandatory plugin JARs
make install-app         # pushes forms/datalists/userviews + seeds data
make fresh-install       # install-app + smoke test

# Regression
make test                # baseline (layers 1+2): userview + MD + datalist
                         #   + API smoke + form save/load (~1 min)
make test-perf           # perf baseline (read-side timing)
make test-l4             # eligibility 20-scenario parity regression
make test-im             # IM end-to-end smoke
make test-all            # everything above

# Sync
make sync                # pull deployed state + commit (no push)
make sync-push           # pull + commit + push to origin/main
make sync-dry            # pull only, show diff, no commit

# Cleanup
make clean               # remove pytest cache
```

Tests assume a reachable Joget. Override connection with env vars (`PGHOST` etc.).

---

## 8. When things go wrong

**Joget won't start.** Check Tomcat logs. Most common: Postgres credentials wrong in `wflow/app_datasource-default.properties`.

**Plugin upload fails / bundle inactive.** Did you upload API Builder first? Did you upload form-creator-api second? Did you check the bundle's state in Admin Bar → Manage Plugins? See INSTALL.md troubleshooting + plugins/BUILD.md.

**`make sync` reports "no changes" but you know there should be drift.** Check `.gitignore` — see commit `59d425e` where a `api-builder/` rule (matching `app/api-builder/`) silently filtered the new pulls. Root-anchored patterns (`/api-builder/`) fix this.

**`make fresh-install` returns HTTP 400 on every form.** Wrong `JOGET_API_ID` in `.env`. Must be the per-API UUID (starts with `API-`), not the api_key.

**`make fresh-install` returns HTTP 401.** Wrong `JOGET_API_KEY`, or the credential isn't enabled in App Builder. Re-check.

**Cascading select doesn't work, or a date picker shows `yyyyyyyy-MMMMM-DD`, or a citizen field "disappears" on save**, or anything weirdly Joget-specific. **Read CLAUDE.md.** The whole file is hard-won gotchas. Grep for the symptom; the fix is documented.

**You need to debug a Joget element / binder / lifecycle hook.** Read the actual Joget source under `jw-community/`. CLAUDE.md's "Reading Joget source — `jw-community/` and `api-builder/` are checked in" section explains the discipline: read the element class + the binder it depends on + `FormUtil.parseBinderFromJsonObject` BEFORE writing wrapper code. This saved many hours during development.

**You can't remember what an ADR decided.** Open `docs/architecture/decision-log.md` — it's the running narrative, one entry per significant decision (D1 through D56+). Each entry cross-references the ADR with full reasoning.

---

## 9. Key documents in priority order

If you only have time to read 3 things:

1. **This file** (`START_HERE.md`) — orientation.
2. **`CLAUDE.md`** — the gotchas. 1000+ lines, but they map directly to "if you do X you'll waste hours; here's why and how to avoid it." Read end-to-end at least once.
3. **`INSTALL.md`** — actually run the install.

If you have an afternoon:

4. **`docs/architecture/architecture-overview.md`** — solution shape.
5. **`docs/architecture/convergence-framework.md`** — the GovStack mapping.
6. **`docs/architecture/decision-log.md`** — every significant decision, dated.
7. **`docs/architecture/adr/adr-001-rule-grammar-canonicity.md`** — the rule DSL.
8. **`docs/architecture/adr/adr-031-unified-rule-engine.md`** — the mm_determinant unification.
9. **`docs/architecture/adr/adr-033-bidirectional-app-state-sync.md`** — the sync ritual.

If you have a week:

10. The component SADs in `docs/architecture/architecture/components/` — mm-form-gen kernel, RegBB framework, Subsidy module, IM module, Reporting engine, Budget Engine, Registry integration. One per major subsystem.
11. **`docs/implementation/production_readiness_roadmap.md`** — the full hardening + UAT roadmap (W1–W8 as planned).
12. **`docs/operations/`** — backup runbook, SMTP config, MAFSN IT TO-DO.

---

## 10. External dependencies

**Joget Community Edition** — DX 8.1.1, the underlying platform. Vendored source at `jw-community/` (clone branch `8.1-RELEASE` from `github.com/jogetworkflow/jw-community`). Read-only reference per CLAUDE.md discipline.

**Joget Marketplace** — API Builder plugin (`apibuilder_plugins-7.0.11.jar`). Free download, requires Joget Community account. Not bundled with stock Joget DX 8.1. INSTALL.md step 6.

**API Builder source** — vendored at `api-builder/` (clone branch `7.0-SNAPSHOT` from `github.com/jogetworkflow/api-builder`). Same role as jw-community/.

**Joget knowledge base** — `https://dev.joget.org/community/display/DX8/` — official install docs, plugin API reference.

**Postgres** — Joget's backing store. On the dev environment: Azure-managed Postgres at `joget-pgsql-sa.postgres.database.azure.com`. Schema is per-app; no app-specific config beyond standard Joget setup.

**SMTP** — production SMTP via MAFSN-managed Postfix / O365 (per `docs/operations/smtp_production_config.md`). Mailtrap free was tried and found unusable (burst throttling). Gmail App Password works for dev; AWS SES sandbox works for testing.

**SMS** — not configured at pause time. Decision deferred to Khotso.

---

## 11. People & access

- **Aare Laponin** — you. Architect + sole developer. Email `aarelaponin@gmail.com`.
- **Khotso** — MAFSN ICT contact for operational decisions, infra, SMTP, UAT.
- **Christopher White / Altron** — VM admin for the Azure dev VM. You don't have filesystem access; Altron's team did the initial Joget install and retains root.
- **Nisha / Joget Inc.** — was provisioned VM access in January 2026 per a Christopher White email thread (forensic context if you need to recall who else has touched the VM).

Credentials live OUTSIDE this repo at `~/IdeaProjects/rsr/secrets/lst-credentials.txt`. The repo never carries production secrets; dev values in code defaults are dev-only and not sensitive.

---

## 12. The 5 things to do if you only have an hour

1. **Read this file end-to-end** (you just did).
2. **Skim CLAUDE.md's table of contents and first 200 lines** — the HARD RULE on raw SQL + the form-generation overview. Set the right mental model.
3. **Open `docs/implementation/uat_prep_pause_resume_plan.md`** — that's the canonical "where I left off" doc, written for this exact scenario.
4. **Run `make sync-dry`** against the dev DB if it's reachable — confirms the sync ritual still works.
5. **Optionally run `make fresh-install` against a local Joget** — that proves you can actually rebuild.

If after that hour you want to keep going: pick up `#335` (the lone pending todo from W3.4) for a small warm-up, then return to `docs/implementation/production_readiness_roadmap.md` for W5+ planning.

---

## 13. What's specifically NOT in this repo (so you know to look elsewhere)

| Thing | Where it actually lives | How to recover |
|---|---|---|
| Plugin JAR binaries | `dist/plugins/` after `make build-plugins` (gitignored) | Rebuild from `plugins/` sources |
| API Builder JAR | Joget Marketplace (free) | Download per INSTALL.md step 6 |
| Vendored Joget source | `jw-community/` + `api-builder/` (gitignored) | Clone per INSTALL.md step 4 |
| `.env` with real values | Your local `.env` (gitignored) | Copy `.env.example` + fill in |
| Production / dev passwords | `~/IdeaProjects/rsr/secrets/lst-credentials.txt` | Where you stash secrets |
| `api_credential` plaintext keys | Joget hashes them; irrecoverable | Generate new ones on restore |
| Citizen file uploads | `wflow/app_formuploads/` on the VM filesystem | Lost if VM is destroyed; out-of-scope for this repo |
| Tomcat logs / runtime metrics | VM-side | Out-of-scope |

For everything else, the repo is the source of truth.

---

## 14. The single most important thing

If you remember one thing: **read CLAUDE.md before any plugin work, every time.** It's the accumulated cost of every Joget gotcha I hit during development. Each entry is a real bug I debugged; the fix is documented so the next person (you) doesn't waste the same hours. The HARD RULE on raw SQL alone has paid for itself dozens of times.

If you remember two things: the second is **the sync ritual is one command**: `make sync`. The repo stays canonical as long as you run it after every App Composer session and before any long pause. ADR-033 explains why this matters.

Welcome back. Everything you need is here.
