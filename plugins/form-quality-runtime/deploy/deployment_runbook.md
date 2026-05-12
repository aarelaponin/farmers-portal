# form-quality-runtime — Deployment Runbook

## What this is

Two artefacts you deploy through standard Joget mechanisms — no direct SQL on `app_form` or `app_datalist`, no schema hacks. Everything follows your "form-first, code-second" rule: tables are created when Joget imports the app archive.

| Artefact | Purpose | Deployed via |
|---|---|---|
| `APP_formQuality-1-<timestamp>.jwa` | The admin module: 7 forms, 8 datalists, 1 userview | **Joget admin → Apps → Import App** |
| `form-quality-runtime-8.1-SNAPSHOT.jar` | The OSGi plugin: post-processor, status framework, rules engine | **Joget admin → Settings → Manage Plugins → Upload Plugin** |

After both are deployed, you (or any other operator) maintain rules through the **Form Quality Admin** userview at `/jw/web/userview/formQuality/v` — no SQL, no Java, no rebuild.

## Sequence

### Step 1 — Import the formQuality app

[Download the .jwa](computer:///Users/aarelaponin/IdeaProjects/rsr/lst-frm-prj/plugins/form-quality-runtime/deploy/APP_formQuality-1-20260426203914.jwa)

In Joget admin (`http://20.87.213.78:8080/jw/`):

1. Open **Apps**
2. Click **Import App** at the top right
3. Select the `.jwa` above
4. Joget shows what will be imported (1 app, 7 forms, 8 datalists, 1 userview)
5. Click **Import**

Joget creates `app_fd_qa_service`, `app_fd_qa_tab`, `app_fd_qa_rule`, `app_fd_qa_gate`, `app_fd_qa_issue`, `app_fd_qa_record_status`, `app_fd_audit_log` automatically.

Verify by visiting `http://20.87.213.78:8080/jw/web/userview/formQuality/v` — you should see the **Form Quality Admin** userview with three categories (Configuration, Runtime, About).

### Step 2 — Upload the plugin JAR

[Download the JAR](computer:///Users/aarelaponin/IdeaProjects/rsr/lst-frm-prj/plugins/form-quality-runtime/form-quality-runtime-8.1-SNAPSHOT.jar)

In Joget admin:

1. **Settings → Manage Plugins**
2. Click **Upload Plugin**
3. Select the JAR
4. Confirm — `form-quality-runtime` appears in the list with a green "active" badge

### Step 3 — Seed the demo rules for spProgramMain

Open the **Form Quality Admin** userview → **Configuration → Services** → **+ New** and create a service:

- Service ID: `farmers_subsidy`
- Service Name: `Lesotho Farmers Subsidy Programme`
- Primary Form ID: `spProgramMain`
- Active: Yes

Then **Configuration → Tabs** → add 5 tabs (one per wizard tab you want to group rules under):

| Service ID | Tab Code | Tab Label | Tab Form ID | Order |
|---|---|---|---|---|
| farmers_subsidy | identity | Identity | spProgramIdentity | 100 |
| farmers_subsidy | timeline | Timeline & Budget | spProgramTimeline | 200 |
| farmers_subsidy | geography | Geography | spProgramGeography | 300 |
| farmers_subsidy | benefits | Benefits | spProgramBenefits | 500 |
| farmers_subsidy | monitoring | Monitoring | spProgramMonitoring | 800 |

Then **Configuration → Rules** → add 5 rules. The `Rule Script` is plain SQL; it returns ≥1 row when the rule fails. Two placeholders are substituted: `#recordId#` (the wizard's record id) and `#formId#` (always `spProgramMain`).

(If you'd rather not click 5 times, run the [seed_subsidy_quality_rules.py](computer:///Users/aarelaponin/IdeaProjects/rsr/lst-frm-prj/plugins/form-quality-runtime/deploy/seed_subsidy_quality_rules.py) script from your terminal — it inserts identical rows in one shot. Either route is fine because the tables now exist as a result of Step 1's app import.)

### Step 4 — Tell me "ready"

Reply once steps 1–3 are done. I'll push the patched `f03-spProgramMain.json` to dev (it adds `postProcessor.className = global.govstack.formquality.hook.FormQualityPostProcessor` plus the EmbeddedDatalist panel) and evict the cache so it takes effect.

I'm holding this back because we don't want spProgramMain trying to invoke a class that hasn't loaded yet.

### Step 5 — See it in action

After step 4:

1. Open `http://20.87.213.78:8080/jw/web/userview/farmersPortal/v/_/spProgramMain_crud?id=prog001&_mode=edit`
2. Click **Save** anywhere
3. Reload — the "Quality Issues" panel at the top should be **empty** (prog001 has full data; passes everything)
4. Open `prog004` (Vulnerable HH, sparse) and **Save** + reload — panel shows live ERRORs
5. Edit the programme name to blank → save → reload — a new ERROR for `identity.programme_name_required` appears

You can also browse `Form Quality Admin → Runtime → Active Issues` to see the same data filtered system-wide, or `Audit Log` to see every status transition the plugin made.

## What's still pending (Day 4)

- `StatusTransitionValidator` — enforces gates at save time (today the gate row is seeded but not yet consulted; programme can still go to APPROVED while errors exist).
- JRE DSL integration — wraps the SQL path so authors write `programme.programName != ''` instead of raw SQL.
- REST API (`@Operation` endpoints) for on-demand evaluation + issue retrieval.
- Apply the same pattern to `farmerRegistrationForm` and `parcelRegistration`.
