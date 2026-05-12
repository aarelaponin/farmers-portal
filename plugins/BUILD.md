# Building the Farmers Portal plugins

This directory holds **29 plugin sources**. Not all of them are needed to run the Farmers Portal app — most are legacy experiments retained for forensic context. This file documents which JARs the app actually depends on, the order to build them in, and how each one builds.

## Prerequisite: vendored Joget source

Plugins build against the Joget Community Edition source tree and the API Builder source tree. Neither is committed to this repo (they're gitignored as vendored read-references — large external projects with their own release cadence). Before running `plugins/build-all.sh` for the first time, clone both at the repo root, **on the branches pinned below**:

```bash
git clone --branch 8.1-RELEASE https://github.com/jogetworkflow/jw-community.git
git clone --branch 7.0-SNAPSHOT https://github.com/jogetworkflow/api-builder.git
```

The branches above match Joget DX 8.1.1 — the binary release this app is built against. If you target a different Joget version, you will need to pick matching branches and may hit API drift; this app is not tested against other Joget versions.

`plugins/build-all.sh` runs a preflight check and fails fast with a clear message if either tree is missing — you don't need to wait for Maven to time out.

## Mandatory plugins (12)

These are the JARs you must upload into Joget for the `farmersPortal` app to function. Every form, datalist, and userview in `app/` references at least one element class from this set.

| # | Plugin | What it provides | Build |
|---|---|---|---|
| 1 | `form-creator-api` | The push API used by `tooling/install_app.py`, `push_form.py`, `push_datalist.py`, `push_userview.py`, `seed.py`. **Build this first** — nothing else can be installed without it. | `mvn package` or `./deploy/repack.sh` |
| 2 | `reg-bb-engine` | Runtime workhorse: `MetaScreenElement`, `RoutingEvaluator`, status framework, notifier, Budget engine, IM tools, post-processors. Embeds `joget-status-framework` via `Embed-Dependency`. | `./deploy/repack.sh` (preferred — see "JAR repack note" below) |
| 3 | `reg-bb-publisher` | Userview publishing helpers + `PublishUserviewMenu`. | `./deploy/repack.sh` or `mvn package` |
| 4 | `joget-gis-ui` | `GisPolygonCaptureElement` — the parcel-geometry capture widget on `parcelGeometry`. | `mvn package` |
| 5 | `joget-gis-server` | `GisApiProvider` — overlap-check API the parcel-geometry widget calls back into. | `mvn package` |
| 6 | `joget-smart-search` | `SmartSearchElement` — used by `md44Input.applicableCrops` and several MM forms. | `mvn package` |
| 7 | `joget-concat-field` | `ConcatFieldElement` — derived-field widget. | `mvn package` |
| 8 | `joget-advanced-filters` | `CascadingMdmSelectFilterType`, `DateRangeFilterType` — datalist filter types used across operator surfaces. | `mvn package` |
| 9 | `embedded-datalist` | `EmbeddedDatalist` — embeds a child datalist inside a form (operator review uses this). | `mvn package` |
| 10 | `parcel-zone-centring` | `AutoCenterBootstrapElement` — emits client-side JS on `parcelGeometry` to pre-centre the map from MD.95 centroids. | `./deploy/repack.sh` |
| 11 | `form-quality-runtime` | The four runtime classes that materialise `qa_record_status`, `qa_issue`, and the `audit_log`. Reads quality rules from `mm_determinant` (post ADR-031). | `./deploy/repack.sh` |
| 12 | `farmer-derived-plugin` | Derived-field calculators on farmer profile (age, household size, etc.). | `mvn package` |

`joget-status-framework` is not in this list because it is a plain JAR (no `Bundle-SymbolicName`), not an OSGi bundle. It is bundled **inside** `reg-bb-engine` via `Embed-Dependency` in that plugin's `pom.xml`. Don't try to upload `joget-status-framework` separately — Joget's plugin manager will reject it.

## Optional plugins (17)

These exist in the tree for historical reasons. Skipping them produces a fully functional Farmers Portal install.

`application-engine-runtime`, `decision-engine-runtime`, `identity-resolver-runtime`, `subsidy-eligibility-runtime` — pre-reg-bb-engine runtime designs, kept for design-history reference.

`joget-rule-editor`, `joget-rules-api`, `rules-grammar` — early rule-engine experiments superseded by `reg-bb-engine`'s `RoutingEvaluator`.

`registry-overview-plugin`, `app-def-provider`, `diagnostic-tool`, `doc-submitter`, `processing-server`, `wf-activator` — experiments and migration tools from earlier phases. None of them are referenced by any form / datalist / userview under `app/`.

`joget-lookup-field` — NOT installed in this Joget anyway (per CLAUDE.md); do not use.

Some optional plugins may not even compile against current Joget DX 8.1 source — they have not been maintained.

## Build order

Within the mandatory list, the order is:

1. **Build `form-creator-api` first** and upload it. Every other JAR's install step depends on form-creator-api being live (because `tooling/install_app.py` uses it to push forms).
2. The remaining 11 can be built in any order; the build-all script alphabetises them. Upload them in any order before running `tooling/install_app.py`.

There are **no inter-plugin compile dependencies** within the mandatory set. Each plugin builds standalone against `jw-community/` and (where present) `api-builder/`.

## Per-plugin build commands

Two styles coexist:

**Maven (`mvn package`)** — the canonical path. Reads `pom.xml`, produces a JAR under `target/`. Use if you have Maven 3.6+ on PATH and a writable `~/.m2`. Build all mandatory plugins:

```bash
plugins/build-all.sh           # iterates the mandatory list, runs each plugin's build
```

**Sandbox repack (`./deploy/repack.sh`)** — direct-javac build, no Maven required. Useful in restricted environments. Reads classpath JARs from `~/.m2` if present, falls back to `target/jw-community/` directly. Plugins that ship a `repack.sh` are noted in the table above; the others use `mvn package`.

The `repack.sh` path is preferred for `reg-bb-engine` specifically because that plugin's pom uses `<Embed-Dependency>` to inline `joget-status-framework`, and a hand-rolled repack reproduces this step correctly even when Maven isn't available. See `reg-bb-engine/deploy/repack.sh` for the canonical pattern.

## JAR repack note (reg-bb-engine)

`reg-bb-engine`'s repack script inlines the `joget-status-framework` JAR's `.class` files into the bundle JAR root. If you build `reg-bb-engine` via `mvn package` instead, the `maven-bundle-plugin` does the same step automatically via `<Embed-Dependency>` in `pom.xml`. Either path produces a JAR that Joget can install.

Diagnostic: after a build, run `unzip -l reg-bb-engine/target/reg-bb-engine-*.jar | grep statusframework`. You should see five `.class` files under `global/govstack/statusframework/{api,core}/`. If empty, the bundle is broken and Joget will fail to start it with `NoClassDefFoundError: global/govstack/statusframework/api/EntityType`.

## After building

Upload every mandatory JAR via Joget App Composer → "Manage Plugins" (admin-only) → Upload, one JAR at a time. After each upload, Joget will show the bundle in "Active" state; if it's not active, click the bundle and start it. Refresh App Composer; the plugin's elements / formatters / binders should appear in the various dropdowns.

Then run `tooling/install_app.py` — see `INSTALL.md` at the repo root for the full playbook.
