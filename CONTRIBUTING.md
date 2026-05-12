# Contributing to Lesotho Farmers Portal

This guide is for the team taking over development from the architecture lead. It assumes you've read `README.md` and at least skimmed `CLAUDE.md`. The HARD RULE in `CLAUDE.md` (no raw SQL on Joget metadata or `app_fd_*` form-data tables) is non-negotiable — read it before your first change.

## Branching

- **`main`** is the always-deployable trunk. Every commit on `main` should be releasable.
- **Feature branches** (`feature/<short-description>`) for any non-trivial work. Open a PR back to `main`.
- **Hotfix branches** (`hotfix/<ticket>`) for urgent UAT/production fixes — short-lived, merged via PR with at least one reviewer.

A trunk-based workflow with short-lived feature branches matches the size of this team. GitFlow with long-lived `develop` branches is overkill at our scale.

## Commit messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) shape:

```
<type>(<scope>): <subject>

<body — optional, explains why, not what>

<footer — optional, references issues / ADRs>
```

Types in use: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `build`, `perf`.

Scopes that map to our structure: `app`, `plugins`, `tooling`, `docs`. Plus specific plugin names for plugin-internal changes (`reg-bb-engine`, `application-engine-runtime`, etc.).

Examples:

```
feat(reg-bb-engine): add note_thread widget to MetaScreenElement

Operators needed an append-only notes thread on each application.
The widget reads from app_fd_case_note, joined to md_case_note_kind
for label resolution. New mm_field row added to subsidyApplicationOperator2025.

Refs: docs/architecture/decision-log.md D50
```

```
fix(tooling): correct idempotency-key prefix in test_im_e2e step 8

The redeem-event idempotency key shape changed in Slice 11 (partial
redemption) from voucher_redeemed:{vch} to voucher_redeemed:{vch}:{redem}.
Test was still checking the two-segment shape.
```

```
docs(architecture): add ADR-032 for IM module framing

Records the May 2026 decision that the IM module is a Lesotho
extension to RegBB rather than a candidate GovStack BB.
```

## PR reviews

- Every PR needs at least **one reviewer** before merge.
- Reviewer is responsible for verifying:
  1. The change builds (CI green).
  2. The change is consistent with the architecture (no drift mode 1 or 2 per `docs/architecture/convergence-framework.md` §4).
  3. The HARD RULE on raw SQL is not violated.
  4. Any new architectural decision is captured in `docs/architecture/decision-log.md`.
  5. CLAUDE.md gets updated if a new gotcha is discovered (search for the "Hard-won Joget gotchas" section).
- For changes touching `reg-bb-engine`, `application-engine-runtime`, `decision-engine-runtime`, `form-quality-runtime`: re-run the relevant e2e test in `tooling/test_*_e2e.py` before merge.

## Working on plugins

Each plugin under `plugins/<plugin-name>/` has its own:

- `pom.xml` — Maven build definition (declares OSGi packaging via `maven-bundle-plugin`)
- `src/main/java/...` — Java source
- `src/main/resources/META-INF/MANIFEST.MF` — OSGi manifest (or auto-generated)
- `deploy/repack.sh` — sandbox-friendly build helper that uses `javac` against the local `~/.m2` cache (use this when Maven is unavailable)
- `Activator.java` — OSGi `BundleActivator` that registers services on bundle start
- `Build.java` — generated build stamp, surfaced in the plugin's description in App Composer

### Adding a new plugin SPI class

Every class that extends a Joget plugin SPI (`DefaultApplicationPlugin`, `WorkflowFormBinder`, `Element`, `ApiPluginAbstract`, `FormBinder`, `Validator`) must be registered in the bundle's `Activator.start()`:

```java
context.registerService(YourPluginClass.class.getName(), new YourPluginClass(), null);
```

Without that registration, the JAR uploads cleanly but Joget's PluginManager can't discover the class, and any form referencing it as a className surfaces as "There are plugins not installed" in App Composer. The CLAUDE.md section "Every plugin class needs an Activator registration" has the full detail.

### Building a plugin

```bash
cd plugins/<plugin-name>
# Option A — Maven (preferred for CI)
mvn clean package
# Output: target/<plugin-name>-<version>.jar

# Option B — repack.sh (preferred for sandbox / quick iterate)
./deploy/repack.sh
# Output: target/<plugin-name>-<version>-build-NNN.jar
```

Bump the `Build.java` build counter on every functional change so operators can verify in App Composer which version is deployed.

### Deploying a plugin

Upload the JAR via Joget's "Manage Plugins" UI. There is no zero-downtime hot-swap path in Joget DX 8.1 — uploading a new bundle stops the old one and starts the new one within the same JVM. Schedule deploys during quiet windows.

## Working on form / datalist / userview definitions

**Never edit `app_form` / `app_datalist` / `app_userview` rows with raw SQL.** See CLAUDE.md "HARD RULE" at the top. Use one of:

1. **Joget App Composer UI** — for ad-hoc visual edits. Save through the UI.
2. **`tooling/push_form.py`, `push_datalist.py`, `push_userview.py`** — programmatic deploy through `form-creator-api` REST endpoints (the only safe two-cache-evicting path).
3. **`tooling/seed.py`** — full fixture seed (used for UAT instance setup).

Form / datalist / userview JSONs live in `app/forms/`, `app/datalists/`, `app/userviews/`. Edit the JSON, then push via `tooling/`. The git commit should contain both the JSON change and any related metadata change.

## Adding an ADR

ADRs (Architecture Decision Records) live in `docs/architecture/adr/`. Format: `adr-NNN-<short-slug>.md`. The next available number is one above the highest existing — at time of writing, ADR-032 is the next free.

Template:

```markdown
# ADR-NNN: <Title>

**Status:** Proposed / Accepted / Superseded
**Date:** YYYY-MM-DD
**Context:** What's the situation?
**Decision:** What did we decide?
**Consequences:** What follows?
**Alternatives considered:** What did we reject and why?
**Related:** Other ADRs, decision-log entries.
```

After authoring, add a one-line entry to `docs/architecture/decision-log.md` summarising the ADR and linking to it.

## Running tests

The e2e test harness is in `tooling/`. Seven scripts cover the major lifecycles:

```bash
cd tooling/
python3 test_w3_lifecycle_e2e.py      # Application lifecycle (12 assertions)
python3 test_eligibility_e2e.py       # Eligibility (12 scenarios)
python3 test_notification_e2e.py      # Notification queue
python3 test_budget_engine_e2e.py     # Budget commitment chain
python3 test_im_e2e.py                # IM full lifecycle
python3 test_im_stacking.py           # Multi-programme stress
python3 test_budget_suite.py          # Budget reports + controls
```

All seven should pass green on every PR that touches the engine plugins. CI should run them automatically when configured.

The load-test script (`tooling/load_test.py`) is for performance baseline runs, not for CI.

## Credentials

Production credentials NEVER enter the repo. The dev credentials currently in this codebase (CLAUDE.md, several `tooling/*.py` scripts, several `docs/` files) are illustrative dev values, queued for env-var refactor in Pass B before public publication. **Do not promote this repo to a public visibility setting until that scrub is complete.**

The actual production secrets file (`lst-credentials.txt`) is gitignored at the repo root level. Local-only.

## Asking Claude to help

The repo is set up to be navigable by AI assistants. `CLAUDE.md` is the entry point for Claude — read first, hard-won gotchas, source-reading discipline, the HARD RULE. When in doubt, point Claude at `docs/architecture/architecture-overview.md` for the system shape and `docs/architecture/convergence-framework.md` for the trio framing.

## Questions

If anything in the architecture isn't clear: the lead author (Aare Laponin) is happy to talk through specific sections. Otherwise, the documentation index in `README.md` "Where to start reading" gives the right entry point per role.
