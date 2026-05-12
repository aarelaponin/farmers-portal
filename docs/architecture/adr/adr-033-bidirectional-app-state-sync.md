# ADR-033: Bidirectional app-state sync — push via form-creator-api, pull via Postgres

**Status:** Accepted
**Date:** 2026-05-12
**Supersedes:** the implicit "configuration-as-code via push only" pattern that produced the 135-form / 162-datalist source-of-truth gap discovered on 2026-05-12.

## Context

The Farmers Portal repository's claim to hold the configuration-as-code source-of-truth has been false in practice. A late-May-2026 audit found:

- 218 forms deployed in Joget, 83 in the repo → 135 missing
- 226 datalists deployed, 64 in the repo → 162 missing
- 1 userview deployed, 2 in the repo (one extra preview snapshot)
- Master-data rows added via the operator UI never made it back to `app/seeds/lesotho-mm-fixture.yaml`

Root cause: the repo workflow is **push-only**. Forms / datalists / userviews are edited locally, pushed to Joget via `form-creator-api` (the correct two-cache-evicting REST endpoint), but **edits made via App Composer's UI don't flow back to the repo**. Over months of operator-driven authoring, App Composer became the de-facto source of truth for ~60% of the artefacts; the repo lagged silently.

This blocks two project commitments:

1. **Fresh-install capability from the repo** (the README claim). A new server cloned from the repo currently gets only ~40% of the deployed configuration — broken userview menus, broken FK references, non-functional citizen flows.
2. **GovStack publication credibility.** A public RegBB reference implementation that's two-thirds incomplete is worse than no reference implementation.

Joget DX 8.x has a built-in Git integration (`Settings → Git Configuration`) that would solve this automatically by auto-committing every save to a per-app filesystem repo at `wflow/app_src/<App ID>/<App ID>_<version>/`. We don't have VM filesystem access (per Khotso/Altron correspondence Jan-Apr 2026 — Altron retains VM admin, no Aare account provisioned), so that path is gated on an infrastructure request and isn't available in this session.

## Decision

Adopt a **bidirectional sync** with asymmetric mechanisms per asset class:

### Asset-class-by-asset-class

| Asset | Authored where | Sync direction | Mechanism |
|---|---|---|---|
| **Plugin source code (Java/OSGi)** | Locally in IDE only | **Push-only** | Build via Maven / `repack.sh` → upload JAR via Joget's plugin manager. No pull. |
| **Forms (JSON definitions)** | Mostly local, sometimes App Composer | **Push + Pull** | Push: `tooling/push_form.py` → `form-creator-api`. Pull: `tooling/sync_pull.py` → Postgres `app_form`. |
| **Datalists (JSON definitions)** | Mostly local, sometimes App Composer | **Push + Pull** | Same pattern. |
| **Userviews (navigation JSON)** | Mostly local, sometimes App Composer | **Push + Pull** | Same pattern. |
| **Master-data row data** (`app_fd_md*`, `app_fd_mm_*`) | Mixed: seed YAML, App Composer "Manage Data", operator userviews | **Push + Pull** | Push: `tooling/seed.py` (existing). Pull: `tooling/sync_pull.py` → per-form YAML under `app/seeds/master-data/`. |
| **XPDL workflow processes** | Authored once via App Composer; rarely changed | **Manual on-demand only** | JWA export → unpack the `package.xpdl` file. Not part of the routine sync. |
| **App-level properties / env vars** | One-time config | **Manual on-demand only** | Same as XPDL — captured into ad-hoc notes when actually changed. |
| **Operational row data** (`app_fd_subsidy_app_2025`, `app_fd_im_voucher`, audit logs, etc.) | Runtime, by users | **Never synced to repo** | This is production data, not source. Belongs in backup runbook scope, not version control. |

### The pull mechanism

`tooling/sync_pull.py` connects to Postgres and dumps four streams:

1. `SELECT id, json FROM app_form WHERE appid='farmersPortal'` → `app/forms/<id>.json` (one file per form)
2. `SELECT id, json FROM app_datalist WHERE appid='farmersPortal'` → `app/datalists/<id>.json`
3. `SELECT id, json FROM app_userview WHERE appid='farmersPortal'` → `app/userviews/<id>.json`
4. For each table matching `app_fd_md*` or `app_fd_mm_*`: `SELECT * FROM <table>` → `app/seeds/master-data/<form_id>.yaml`

Each output passes through the same credential-placeholder substitution we applied in the pre-publication scrub (`a5af1181...` → `<JOGET_API_KEY>`, `Joget@DB#2026!` → `<PGPASSWORD>`).

### The sync ritual

After any App Composer session, or on a weekly cadence:

```bash
python3 tooling/sync_pull.py
git diff app/                  # eyeball what changed
git add app/
git commit -m "sync: pull App Composer edits from Joget <date>"
git push origin main
```

That's the entire ritual. No JWA export needed for the routine case.

## Consequences

### Positive

- **Repo finally becomes honest source-of-truth.** A fresh server cloning the repo and running the push scripts gets the complete current configuration.
- **GovStack publication credibility restored.** The public repo at `github.com/aarelaponin/farmers-portal` represents what's actually deployed, not a 1/3-complete snapshot.
- **Sync ritual is one command.** Low cognitive overhead; encourages frequent syncs rather than letting drift accumulate.
- **No new infrastructure required.** Uses the Postgres access we already have. No VM filesystem access, no Joget config changes, no streaming-ZIP parsing.
- **Per-asset-class clarity.** Plugin source ≠ form JSON ≠ master-data rows ≠ operational data. Each gets the sync discipline it needs.

### Negative

- **Sync is operator-disciplined, not automatic.** Joget's built-in git would auto-commit on every save; we rely on the operator running `sync_pull.py` periodically. Drift can re-accumulate if the ritual is skipped.
- **XPDL + app-level properties remain manual on-demand.** Routine sync doesn't capture them; if they change without a manual export, that change isn't in the repo.
- **`form-creator-api` and `sync_pull.py` are two halves of the same architectural surface.** Whoever inherits the codebase needs to understand both. Documented in CLAUDE.md and CONTRIBUTING.md.
- **The eventual right answer is still Joget's built-in git.** ADR-033 is the right design for now (no VM access), but if Aare or MAFSN IT later gets filesystem access to the VM, switching to Joget's native git becomes the canonical path. ADR-033 should be revisited at that point.

### Trade-offs explicitly accepted

- **Routine sync is forms + datalists + userviews + master-data only.** XPDL processes and app-level env vars are out of routine scope. If those change frequently, this ADR needs revision.
- **Master-data table discovery is by SQL pattern (`app_fd_md*`, `app_fd_mm_*`).** A new master-data form with a non-matching prefix would be missed. Convention enforcement is via the `md_*` / `mm_*` naming discipline already in CLAUDE.md.
- **No conflict detection.** If a form is edited in both App Composer AND the local JSON between syncs, the next pull silently overwrites the local edit with the deployed version. Manual coordination required.

## Alternatives considered

**Option A — JWA export + scripted unpack.** Comprehensive (captures everything in two files: `appDefinition.xml` + `package.xpdl`) but requires a streaming-ZIP parser (Joget's JWA has no proper End-of-Central-Directory record). Heavier implementation. Rejected because:
- Per-asset-class discipline is more honest about what's actually sync-able routinely
- XPDL + app-level properties aren't worth automating since they change rarely
- The Postgres approach is simpler and faster to maintain

**Option B — Joget's built-in git integration.** Canonical Joget pattern; would solve everything automatically. Rejected for now because we don't have VM filesystem access to configure it. **Revisit when VM access is sorted** — at that point, ADR-033 can be superseded by ADR-XXX adopting Joget's native git.

**Option C — Stay push-only (do nothing).** Rejected because of the 135-form gap and the publication credibility problem.

## Principles invoked

- **Honesty over aspiration.** The README's "configuration-as-code" claim was aspirational. ADR-033 makes it true.
- **Convention over Invention** (constrained by what's available). Joget's built-in git is the convention; we'd adopt it if we could. Until then, the closest cousin is "pull from the same data source App Composer reads from" — i.e. Postgres.
- **YAGNI** against XPDL + app-level properties in the routine sync. They change rarely; manual capture suffices.

## Migration path

1. Author `tooling/sync_pull.py` (~100 lines including the master-data YAML dump).
2. Run it once — produces the 135 missing forms + 162 missing datalists + master-data YAML files.
3. `git diff` to inspect; commit the entire delta as `sync: initial pull — close source-of-truth gap (ADR-033)`.
4. Push.
5. Update CLAUDE.md with the sync ritual and the directional discipline.
6. Update CONTRIBUTING.md so the team handover knows to run `sync_pull.py` after App Composer sessions.
7. Update README to mention the bidirectional sync in the project layout description.

After this, the source-of-truth claim is real. Going forward, every push-from-App-Composer change closes its loop via the periodic ritual.

## Future revisit triggers

Re-open this ADR when ANY of:

- VM filesystem access is granted (then switch to Joget's built-in git per Option B above)
- XPDL processes or app-level env vars start changing more than monthly (then expand the routine sync to include them)
- Multi-instance deployment (citizen portal + back-office) — then the sync ritual needs to handle multiple instances cleanly
- A second Joget app under the same project arrives (currently sync_pull.py hard-codes `farmersPortal`)

## Related

- D55 (lazy polyrepo extraction) — same architectural posture: pragmatic and triggered by demand rather than speculative.
- CLAUDE.md HARD RULE on raw SQL — the form-creator-api push path enforces this; the pull path uses `SELECT` only, which the HARD RULE explicitly permits.
- `docs/architecture/govstack-alignment-may2026.md` §1 — the source-of-truth claim was implicit there. After ADR-033's implementation, it becomes honest.
