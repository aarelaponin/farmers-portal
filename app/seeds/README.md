# Lesotho mm_* test-fixture seeder

One command to (re)create the canonical test fixture for the meta-model layer.
Replaces 20 minutes of manual CRUD clicking with one Python invocation.

This folder holds **fixture data** (YAML); the seeder script lives in
`../tooling/seed.py` so the project's Python venv stays in one place. See
`../tooling/README.md` for first-time setup (`bash tooling/bootstrap.sh`).

## Quick start

```sh
# First-time setup (creates tooling/.venv, installs PyYAML + psycopg2)
bash tooling/bootstrap.sh

# Seed the canonical fixture (idempotent — safe to re-run)
tooling/.venv/bin/python tooling/seed.py

# Wipe all mm_* rows then re-seed (canonical reset)
tooling/.venv/bin/python tooling/seed.py --clear

# Just wipe, don't re-seed
tooling/.venv/bin/python tooling/seed.py --clear --no-seed

# Use a different fixture file
tooling/.venv/bin/python tooling/seed.py --fixture app/seeds/some-other.yaml
```

Or activate the venv first if you prefer:
```sh
source tooling/.venv/bin/activate
python tooling/seed.py --clear
```

## What it seeds today

Defined in `lesotho-mm-fixture.yaml`:

| Entity            | Codes                                                      |
|-------------------|------------------------------------------------------------|
| `mm_institution`  | `MIN_AGRO`                                                 |
| `mm_service`      | `SUBSIDY_2025` (umbrella for all four programmes)          |
| `mm_registration` | `PRG_2025_001` … `PRG_2025_004` (the four programmes)      |
| `mm_screen`       | `FIRST_SUBSIDY_INTRO` (demo application screen)            |
| `mm_field`        | `full_name`, `age`, `dob`, `notes` on the demo screen      |
| `mm_catalog`      | (empty for now — populated when D21 lands in Week 3)       |

Each entity is keyed by its business `code`; re-running the seeder upserts in
place. Joget UUIDs stay stable across re-runs (insert-once, update-thereafter).

## How references are resolved

* **Cross-entity FKs** (e.g. `mm_registration.serviceId = "SUBSIDY_2025"`) — go
  through as plain strings. Joget's FormOptionsBinder for these forms uses
  `idColumn: "code"` per D20, so the FK column literally stores the parent's
  business code. No lookup needed.
* **Joget-internal FKs** (e.g. `mm_field.screenId` storing the parent screen's
  UUID, the FormGrid pattern) — use the placeholder `_ref:<formId>:<code>` in
  YAML. The seeder resolves the placeholder to the parent row's UUID by looking
  it up in postgres after the parent batch is inserted.

## Architecture

```
seed.py  ──HTTP──▶  /jw/api/formcreator/formcreator/seed
                        ↓
                  FixtureSeedService.java
                        ↓
                  FormDataDao.saveOrUpdate (Joget standard)
                        ↓
                  app_fd_<formId> tables
```

`seed.py` also reads postgres directly for **lookup** (resolving
`_ref:` placeholders by code → UUID). Reading is allowed under the
`CLAUDE.md` HARD RULE; only writing is forbidden. All writes go through
`FormDataDao` via the API.

## Adding a new fixture entry

1. Edit `lesotho-mm-fixture.yaml`. Pick any business `code`; the seeder will
   upsert it on next run. Cross-entity FK fields take target codes
   (`serviceId: SUBSIDY_2025`); Joget-internal child references use
   `_ref:<formId>:<code>`.
2. Run `python3 app/seeds/seed.py`. New rows get inserted; existing ones get
   updated in place.

If you change a row's `code`, the seeder treats it as a new row (the old row
isn't auto-deleted; rename = create + leave-old). To rename cleanly: clear,
edit YAML, re-seed.

## Configuration

Environment variables (defaults in parens):
- `JOGET_BASE_URL` (`http://20.87.213.78:8080/jw`)
- `JOGET_API_ID`   (the lst-credentials API id)
- `JOGET_API_KEY`  (the lst-credentials API key)
- `JOGET_APP_ID`   (`farmersPortal`)

## Requirements

- Python 3.7+ with `pyyaml` and `psycopg2-binary` — install via the project
  venv: `bash tooling/bootstrap.sh` (one-time).
- form-creator-api plugin **build-003 or later** deployed (provides the `/seed`
  and `/clear` endpoints).
- Network access to the dev Joget and its postgres.
