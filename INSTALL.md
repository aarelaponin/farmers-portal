# Installing the Farmers Portal from this repo

You're a new developer with no prior context. This playbook stands up a working `farmersPortal` on a fresh Joget DX 8.1.x server using only what's in this repository — no contact with the original team, no access to the live Lesotho instance.

Allow ~60 minutes the first time. After the install, `make test` should pass green.

## What you'll end up with

A locally-running Joget DX instance with the Farmers Portal app deployed: 218 forms, 226 datalists, 1 userview with role-aware navigation, 117 master-data tables seeded with the canonical Lesotho lookup data (districts, eco-zones, crops, livestock, master metadata for the RegBB engine). All 7 e2e test scripts in `tooling/` pass against it.

## What you need before you start

* **Java 11** (OpenJDK is fine), **Maven 3.6+**, **Python 3.9+** on your PATH.
* **PostgreSQL 13+** running locally (or accessible). Create an empty database for Joget — `jogetdb` is the canonical name; the install plays well with anything else if you set `PGDATABASE` accordingly.
* **A Joget DX 8.1.x installation.** Use Joget's official installer per the knowledge base — pick the variant that matches your OS:
  * Native install: https://dev.joget.org/community/display/DX8/Joget+DX+8+Installation
  * The Joget docs also cover Docker if you want it; this playbook does not assume Docker.
  * Make sure Joget is configured to use Postgres, not the embedded H2. Set `wflow/app_datasource-default.properties` to point at the same Postgres you created above.
* About 4 GB free disk for Joget + Postgres + the built plugin JARs.

This playbook does not cover production hardening (TLS, reverse proxy, OAuth/Keycloak, SMTP gateway, SMS provider). Those live in `docs/operations/`. The install here gets you a working dev instance.

## Step-by-step

### 1. Clone the repo and create your environment file

```bash
git clone https://github.com/aarelaponin/farmers-portal.git lst-frm-prj
cd lst-frm-prj
cp .env.example .env
$EDITOR .env                     # fill in PG* values; leave JOGET_API_* placeholders for now
```

`.env` is gitignored — your local credentials never leave your machine.

### 2. Start Joget and log in as admin

Start Joget per the official knowledge-base instructions. Browse to `http://localhost:8080/jw/admin` and log in (default `admin` / `admin`; change the password immediately afterwards in the user profile).

### 3. Create the `farmersPortal` app shell

In App Composer: **Design Apps → New App**. Set:

* App ID: **`farmersPortal`** (must match exactly — many references hard-code this id)
* App Name: **Farmers Portal**
* Version: **1**

Save. You'll now see an empty `farmersPortal` app in the Design Apps list. This is the container we'll fill in the remaining steps.

### 4. Fetch the vendored Joget source

The plugins build against Joget Community Edition source and API Builder source, neither of which is committed to this repo (they are large external trees, gitignored as vendored read-references). Clone both at the repo root, **on the branches pinned below** — these match the Joget DX 8.1.1 binary you installed in step 2:

```bash
git clone --branch 8.1-RELEASE https://github.com/jogetworkflow/jw-community.git
git clone --branch 7.0-SNAPSHOT https://github.com/jogetworkflow/api-builder.git
```

Verify the layout is right:

```bash
ls jw-community/wflow-core/pom.xml api-builder/apibuilder_api/pom.xml
```

Both files should exist. If `jw-community/` or `api-builder/` are missing, `plugins/build-all.sh` will fail fast with a clear message rather than spend 30 seconds in Maven before erroring out cryptically.

Skipping this step is the most common install failure — plugin builds will fail with `package org.joget... does not exist` if `jw-community/` is absent.

### 5. Build the plugin JARs

The repo carries 29 plugin source trees under `plugins/`; only 12 are mandatory. See `plugins/BUILD.md` for the list and the rationale. Build them all in one go:

```bash
make build-plugins
```

Under the hood: `plugins/build-all.sh` picks `mvn package` by default (sandbox: pass `--repack` to use the direct-javac path instead). On a clean machine the first run takes 5–10 minutes because Maven downloads its dependencies. Built JARs land in `dist/plugins/`.

### 6. Download API Builder from Joget Marketplace

API Builder is **not** bundled with standard Joget DX 8.1.1 — it ships as a separate Marketplace plugin. Every form-creator-api endpoint depends on it (form-creator-api extends API Builder's `ApiPluginAbstract` SPI), so this JAR must be uploaded **before** any of our own plugin JARs.

Browse to https://marketplace.joget.org, search for **"API Builder"**, and download version **7.0.11** (or any 7.0.x that matches the `7.0-SNAPSHOT` branch you cloned in step 4). Save the JAR — typically named `apibuilder_plugins-7.0.11.jar` — somewhere convenient. You'll upload it in the next step alongside our own JARs.

If Marketplace requires registration, register a free Joget Community account. The download is free for community use.

### 7. Upload all plugin JARs to Joget

In App Composer: **Admin Bar → Manage Plugins → Upload**. Upload one JAR at a time, in this order:

1. **`apibuilder_plugins-7.0.11.jar`** (from step 6 — must be first, everything else depends on its SPI)
2. **`form-creator-api`** (from `dist/plugins/` — must be second, the other 11 plugins are pushed via its endpoints)
3. The remaining 11 mandatory plugins from `dist/plugins/`, in any order

After each upload, the bundle should appear in "Active" state. If not, click into it and Start. Common reason for a bundle to not auto-activate: it depends on something not yet uploaded — re-upload after the dependency lands.

This step is intentionally manual: plugin uploads bypass the API-credential check, so there's no scripted path that doesn't also bypass it. Thirteen clicks total.

### 8. Create the form-creator-api credential

App Composer → Open `farmersPortal` → **API Builder**. You should now see a `formcreator` API in the list (it was registered when you uploaded the form-creator-api JAR in step 7).

Open it, then create an **API Credential**:

* API id (auto-assigned UUID, looks like `API-e7878006-...`) — copy this
* Set an api_key (any random string you choose — keep it secret) — copy this

Open `.env` and fill in both values:

```bash
JOGET_API_ID=API-<the-uuid-from-step-8>
JOGET_API_KEY=<the-secret-you-set>
```

### 9. Source `.env` and bootstrap the Python venv

```bash
set -a; source .env; set +a       # export everything in .env
bash tooling/bootstrap.sh         # creates tooling/.venv with PyYAML + psycopg2
source tooling/.venv/bin/activate
```

### 10. Install the app — push forms, datalists, userview, then seed master-data

```bash
make fresh-install
```

That's the rest of the install in one command. Under the hood:

* `tooling/install_app.py` pushes every JSON under `app/forms/`, `app/datalists/`, `app/userviews/` via form-creator-api;
* it then seeds every YAML under `app/seeds/master-data/` (117 tables, ~1500 rows) via the same API's `/seed` endpoint.

Run time: about 3–4 minutes against a local Joget. Re-running is safe and idempotent — every artefact upserts by its business key.

If any push errors, the script reports per-artefact HTTP status and continues. Common failure: a form references a custom plugin element class that didn't make it into the JAR uploads — go back to step 7 and check that the relevant mandatory plugin is in "Active" state.

### 11. Verify the install

```bash
make test                         # foundational regression (layers 1+2, ~1 min)
make test-l4                      # eligibility regression
make test-im                      # IM end-to-end
```

All three should pass green. If they don't, the failure tells you which subsystem is mis-installed — start with the failing test's first assertion.

Then browse to `http://localhost:8080/jw/web/userview/farmersPortal/v/_/home` — you should see the role-aware Farmers Portal home with the eight userview categories described in CLAUDE.md.

## Where to go next

* **Operating it for UAT / production**: `docs/operations/` — backup runbook, SMTP config, MAFSN IT TO-DO.
* **Understanding the architecture**: `docs/architecture/architecture-overview.md` → `docs/architecture/convergence-framework.md`.
* **The bidirectional sync ritual** (so any edits you make in App Composer flow back to the repo): `docs/architecture/adr/adr-033-bidirectional-app-state-sync.md`, then `make sync` after every App Composer session.
* **Working on the code as a contributor**: `CONTRIBUTING.md` and `CLAUDE.md` — the latter holds every hard-won Joget gotcha, read it before any plugin work.

## Troubleshooting

**`install_app.py` returns HTTP 400 "Bad Request" on every form.** Almost always wrong `JOGET_API_ID`. Double-check it's the per-API UUID (starts with `API-`), not the api_key. The two are not interchangeable.

**`install_app.py` returns HTTP 401 on every form.** Wrong `JOGET_API_KEY`, or the credential isn't enabled in App Builder. Open the credential in App Composer and ensure "Active" is checked.

**A specific form fails with "plugin not installed: org.joget...".** A mandatory JAR is missing or its bundle is Inactive. Open Admin Bar → Manage Plugins, search for the named class, and check the bundle's state. CLAUDE.md's "Custom plugins installed in THIS Joget instance" section maps element class names to JARs.

**`form-creator-api` bundle fails to activate with `NoClassDefFoundError: org/joget/api/...`.** You uploaded form-creator-api before API Builder. The class hierarchy in form-creator-api extends API Builder's SPI; without API Builder present, the bundle can't link. Go back to step 6 — download API Builder from Joget Marketplace and upload it first; then re-upload form-creator-api (no need to remove it first, the upload upserts).

**`make install-app` returns "API Builder API not found".** API Builder is installed but the `formcreator` API definition didn't register. This happens occasionally on first install — restart the Joget Tomcat once; the API Builder runtime registers all installed API plugins on JVM start.

**Seed phase fails with "form X does not exist".** A master-data YAML references a form whose JSON wasn't pushed in Phase 1 — likely because the JSON file is malformed. Search the Phase 1 log for that form id; fix the JSON; re-run `make fresh-install`.

**App Composer's API Builder is empty after uploading form-creator-api.** Restart the Joget Tomcat once. Some Joget DX 8.1 versions don't refresh the API registry on bundle install — only on JVM start.

**Plugin build fails with "package org.joget... does not exist".** You skipped step 4 (fetch vendored Joget source). Clone `jw-community` and `api-builder` per that step; re-run `make build-plugins`.

For anything not covered here, open an issue at https://github.com/aarelaponin/farmers-portal/issues.
