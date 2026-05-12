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
├── plugins/             Lesotho-specific OSGi plugin sources
│                        (Tier 1/2 generic plugins are extracted to separate
│                        repos in Pass B — see docs/implementation/)
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

## Prerequisites

- **Java 11** (OpenJDK)
- **Maven 3.6+**
- **Joget DX 8.1.x** — running locally (Docker or native install) or on a remote dev instance
- **PostgreSQL 13+** — Joget's backing DB
- **Python 3.9+** — for the tooling scripts

## Setting up locally

### 1. Fetch vendored external source

The `jw-community/` and `api-builder/` directories are gitignored. Joget Community Edition and API Builder source are needed for plugin builds against their SPI:

```bash
# (script to be authored — placeholder for now)
tooling/fetch-vendored.sh
```

### 2. Build the plugins

```bash
cd plugins/<plugin-name>
./deploy/repack.sh        # sandbox-friendly build (uses javac + local m2)
# OR
mvn clean package         # standard Maven build
```

Built JARs land in each plugin's `target/`. Upload to Joget via App Composer or the form-creator-api endpoint (see CLAUDE.md).

### 3. Configure environment

The dev environment expects these environment variables (defaults are dev-only):

```bash
export JOGET_BASE_URL="http://20.87.213.78:8080/jw"     # dev URL
export JOGET_API_KEY="<see dev credentials file>"
export PGHOST="joget-pgsql-sa.postgres.database.azure.com"
export PGDATABASE="jogetdb"
export PGUSER="jogetadmin"
export PGPASSWORD="<see dev credentials file>"
```

Dev credentials are stored OUTSIDE this repo at `~/IdeaProjects/rsr/secrets/lst-credentials.txt` (or wherever your local setup keeps them). **Never commit credentials.**

### 4. Deploy forms / datalists / userviews

```bash
cd tooling/
python3 seed.py                              # full fixture seed
python3 push_userview.py                     # push the navigation
python3 build_api_reference.py               # regenerate docs/developer/api_reference.md
```

### 5. Run the e2e test harness

```bash
cd tooling/
python3 test_w3_lifecycle_e2e.py             # application lifecycle, 12 assertions
python3 test_eligibility_e2e.py              # 12 scenarios
python3 test_notification_e2e.py             # email queue lifecycle
python3 test_budget_engine_e2e.py            # budget event chain
python3 test_im_e2e.py                       # full IM lifecycle
python3 test_im_stacking.py                  # multi-programme stress
python3 test_budget_suite.py                 # budget reports + controls
```

All seven should pass green against a healthy dev instance.

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
- Tier 1 + Tier 2 polyrepo split deferred to Pass B (post-UAT).

## Credentials and the "DEV-only" hygiene rule

Several files in this repo contain dev-only credentials (Joget admin password, Postgres dev password, dev API key). These are illustrative dev values, not production credentials. **Before any public release** — including the planned GovStack publication of the RegBB plugins — all such values must be replaced with environment-variable placeholders. The full scrub is queued as part of the Pass B polyrepo split.

In the meantime: **do not make this repo public**. Treat it as a private-team repository.

## License

Apache License 2.0. See `LICENSE`.

## Acknowledgements

Built for the Ministry of Agriculture, Food Security and Nutrition of the Kingdom of Lesotho. Aligned with the GovStack Building Block initiative (https://specs.govstack.global).
