# Lesotho Farmers Portal

The agricultural subsidy management system for Lesotho's Ministry of Agriculture, Food Security and Nutrition (MAFSN). Implementation of the GovStack Registration Building Block (RegBB) on Joget DX 8.x.

## What this is

A configuration-driven subsidy lifecycle platform: programme design, citizen application, eligibility evaluation, decision, voucher issuance, distribution, redemption, audit. Built on the metadata-engine-grammar triad: 12 `mm_*` metadata entities define the service shape, `reg-bb-engine` interprets the metadata at runtime, and a DSL rule grammar (Determinant) evaluates eligibility. Adding a new subsidy programme requires zero Java changes — just rows in the metadata tables.

Substantially GovStack-conformant for the H1 horizon. See `docs/architecture/govstack-alignment-may2026.md` for the current verdict.

## Repository layout

```
.
├── app/                Joget application artefacts (configuration-as-code)
│   ├── forms/             form JSON definitions
│   ├── datalists/         datalist JSON definitions
│   ├── userviews/         userview (navigation) JSON
│   ├── form-specs/        analyst-authored YAML specs
│   ├── seeds/             fixture data (MM, master data, samples)
│   └── branding/          logos, favicons
│
├── plugins/             OSGi plugin sources — all 29 plugins (Tier 1 generic,
│                        Tier 2 RegBB suite, Tier 3 Lesotho-specific) live in
│                        one tree. Individual plugins are extracted to their
│                        own repos *lazily*, when a concrete second consumer
│                        appears — see docs/architecture/adr/adr-032-lazy-
│                        polyrepo-extraction.md. First lazy extraction:
│                        joget-status-framework, May 2026 (triggered by GAM).
│
├── tooling/             Python scripts — deploy helpers, test harness,
│                        seeders, load-tests, e2e tests
│
├── docs/                Project documentation
│   ├── architecture/      ADRs, convergence framework, GovStack alignment,
│   │                      decision log, component SADs (the timeless reasoning)
│   ├── implementation/    Roadmaps, plans, perf baseline (architect-facing)
│   ├── operations/        MAFSN ICT runbooks — backup/restore, SMTP, TO-DO
│   ├── developer/         Auto-generated API reference
│   ├── migration/         Pilot-data migration guide + sample script
│   └── notifications/     Markdown source-of-truth for the 12 lifecycle emails
│
├── CLAUDE.md            AI-assistant operating notes (hard-won gotchas, HARD
│                        RULE on raw SQL, source-reading discipline)
├── CONTRIBUTING.md      How to work on this repo (for the team handover)
├── LICENSE              Apache 2.0
├── Makefile             Build automation
└── .gitignore
```

## Installing from this repo

The full end-to-end install playbook — fresh Joget, plugin builds, app shell creation, API credentials, configuration push, master-data seed, smoke test — lives in **[INSTALL.md](INSTALL.md)**. Allow about an hour the first time.

Quick sketch of the path, for orientation only:

1. Install Joget DX 8.1.x per [Joget's knowledge base](https://dev.joget.org/community/display/DX8/Joget+DX+8+Installation) and configure it against your Postgres.
2. Create the empty `farmersPortal` app in App Composer.
3. `cp .env.example .env`, fill in PG\* values.
4. Clone the vendored Joget source: `git clone --branch 8.1-RELEASE https://github.com/jogetworkflow/jw-community.git` and `git clone --branch 7.0-SNAPSHOT https://github.com/jogetworkflow/api-builder.git` at the repo root.
5. `make build-plugins` — produces the 12 mandatory JARs under `dist/plugins/`. See `plugins/BUILD.md` for the mandatory vs optional split.
6. Upload each JAR via App Composer → Manage Plugins (form-creator-api first).
7. Create an API Builder credential against the `formcreator` API; fill `JOGET_API_ID` / `JOGET_API_KEY` in `.env`.
8. `make fresh-install` — pushes 218 forms / 226 datalists / 1 userview, seeds 117 master-data tables, runs the smoke test.

After install, regression tests live in:

```bash
make test         # baseline (layers 1+2) — ~1 min
make test-l4      # eligibility regression
make test-im      # IM end-to-end
make test-all     # everything
```

## Where to start reading

| If you are… | Start here |
|---|---|
| **A new developer joining the team** | `CONTRIBUTING.md` → `CLAUDE.md` → `docs/architecture/architecture-overview.md` |
| **An architect reviewing the design** | `docs/architecture/convergence-framework.md` → `docs/architecture/govstack-landscape-review-may2026.md` → `docs/architecture/govstack-alignment-may2026.md` |
| **MAFSN ICT preparing for production** | `docs/operations/minagri_it_todo.md` → `docs/operations/backup_restore_runbook.md` → `docs/operations/smtp_production_config.md` |
| **The migration team loading pilot data** | `docs/migration/api_migration_guide.md` → `docs/migration/sample_migration.py` |
| **An API integrator** | `docs/developer/api_reference.md` (57 endpoints, 7 providers) |
| **Planning UAT** | `docs/implementation/production_readiness_roadmap.md` → `docs/implementation/uat_prep_pause_resume_plan.md` |
| **The Claude AI assistant** | `CLAUDE.md` (every session — the HARD RULE plus accumulated gotchas) |

## Status

As of 2026-05-12 (pre-UAT):

- Substantially GovStack RegBB-conformant at H1. See `docs/architecture/govstack-alignment-may2026.md` for per-section verdicts.
- W1-W4 of the pre-UAT plan complete (RBAC, notifications, lifecycle, perf baseline + indexes).
- W5-W8 ahead (UAT instance + fixtures, kiosk, defect tracker, accessibility, Sesotho i18n scaffolding, runbooks, entry/exit criteria).
- UAT entry blocked on: MAFSN-provisioned UAT environment, Keycloak realm, SMTP gateway choice (Khotso request out 2026-05-11), named UAT participants.
- Polyrepo extraction policy: lazy, per-plugin, triggered by concrete reuse demand. See ADR-032.

## Credentials

This repo does not carry production secrets. Local install reads credentials from a gitignored `.env` (see `.env.example` for the template) and from `~/IdeaProjects/rsr/secrets/lst-credentials.txt` (or your own equivalent path) for the bidirectional sync ritual. Where dev-environment values appear in source code as defaults, they target the Lesotho dev Azure VM with non-sensitive dev data — they are not production credentials. Treat any value committed to this repo as **public knowledge**; never commit anything that needs to stay secret.

## License

Apache License 2.0. See `LICENSE`.

## Acknowledgements

Built for the Ministry of Agriculture, Food Security and Nutrition of the Kingdom of Lesotho. Aligned with the GovStack Building Block initiative (https://specs.govstack.global).
