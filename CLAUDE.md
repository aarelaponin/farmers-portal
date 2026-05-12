# Farmers Portal (LST) — Claude Code Instructions

## Project overview
Joget DX 8.x application: agricultural subsidy management system (ASMS) for Lesotho.
App ID: `farmersPortal` | Main JWA: `APP_farmersPortal-*.jwa`

## Sync ritual (ADR-033 — read this before any session that touches `app/`)

The repo holds the canonical source for forms, datalists, userviews, and master-data. **App Composer edits in Joget DO NOT auto-flow back to the repo** — they have to be pulled. If the user mentions "did some App Composer work", "edited a form in the UI", or anything similar, run the sync ritual to capture those edits.

**One command:**

```bash
make sync          # pull + commit (no push)
make sync-push     # pull + commit + push to origin/main
make sync-dry      # pull only, show diff, no commit
```

Or directly: `./tooling/sync.sh [--push|--dry-run|--no-commit]`.

Reads `PGPASSWORD` from environment or from `~/IdeaProjects/rsr/secrets/lst-credentials.txt`. Calls `tooling/sync_pull.py` which dumps forms / datalists / userviews / `app_fd_md*` / `app_fd_mm_*` tables into `app/`. Applies credential placeholder substitution. Idempotent.

See `docs/architecture/adr/adr-033-bidirectional-app-state-sync.md` for the architecture.

## ⛔ HARD RULE — Joget-native API only. Never raw SQL on Joget metadata or form data.

**Never** write to `app_form`, `app_userview`, `app_datalist`, `app_package`, any
`app_fd_*` form-data table, or any other Joget-managed table with raw SQL
(`INSERT`, `UPDATE`, `DELETE`, `ALTER`). This includes psycopg2, `mysql`/`psql`
shells, Joget's "Database Query" UI, and any script that bypasses Joget's own
DAO. **No exceptions.** Bypassing the DAO causes silent data loss (Hibernate
mapping desync — see "What goes wrong" below) and falsifies the audit trail.

`SELECT` for read-only inspection or debugging is fine. Reading is not writing.

### Native paths to use instead

| What you want to do | Use |
|---|---|
| Create or update a form definition | `POST /jw/api/formcreator/formcreator/forms` (the **form-creator-api** plugin under `plugins/form-creator-api/`). Internally calls `FormDefinitionDao.add()` and refreshes both caches. Upsert: it loads the existing form by id, updates if found, creates if not. |
| Create or update a datalist or userview | Same plugin — see `CrudService.java` for the upsert flow it does for the companion datalist + userview. For ad-hoc edits: App Composer UI. |
| Insert / update / delete row data in any `app_fd_*` table | Either the standard CRUD UI in the userview, or the Joget **form-data REST API** exposed by API Builder (`POST /jw/api/{apiId}/data`). For programmatic seeding from Java, use `FormDataDao.saveOrUpdate(formId, tableName, FormRowSet)` — that's how every plugin in `plugins/` does it. |
| Add or remove a column from a form's table | Add the field to the form definition (HiddenField, TextField, etc.) and re-save the form via form-creator-api or App Composer. Joget regenerates the Hibernate mapping and DDLs the column on next save. **Never** `ALTER TABLE` directly. |
| Patch an existing form (add a HiddenField, tweak a property) | Re-POST the full updated JSON to form-creator-api with the same `formId` — it upserts. Or edit in App Composer's Form Builder and Save. |
| Inspect what's in the DB | `SELECT` is fine — psycopg2, `psql`, datalists, App Builder's "Manage Data" view. |

### What goes wrong if you violate the rule

Joget DX 8 keeps form definitions in **two independent JVM caches**, both
backed by ehcache, and `INSERT/UPDATE` on `app_form` evicts neither.

* **Cache 1 — AppDefinition** (form/userview/datalist JSON, plugin configs)
  is keyed by `(appId, appVersion)`. Evicted only when `AppService.saveX()`
  runs. So your raw `UPDATE app_form` is invisible to the rendering layer
  until something else triggers `saveX()`.

* **Cache 2 — per-form Hibernate ORM mapping** (which columns this form
  knows how to INSERT/UPDATE) is keyed by `(appId, formId)` independently
  per form. Only rebuilt when `AppService.saveForm()` runs **on that exact
  form**. A `saveForm()` on any *other* form does NOT rebuild it.

If you `UPDATE app_form SET json = ...` to add a TextField with id `X`,
the form *renders* `X` (cache 1 evicts on the next form save anywhere) but
the storeBinder *silently ignores* `X` (cache 2 is stale: the Hibernate
mapping doesn't know about the new column, so Joget's UPDATE statement
won't include it). The user types into the new field, the value is dropped
on save, no error message. Pre-creating the column in DB does NOT help —
Hibernate's mapping defines what gets sent in the UPDATE, not the column
list of the table.

The form-creator-api plugin avoids both traps: `FormDefinitionDao.add()`
saves through Joget's DAO, which evicts cache 1 and rebuilds cache 2 for
that form atomically. App Composer's Save button does the same. **Use
those, never raw SQL.**

### Forensic recovery if you discover an old SQL patch broke a form

You'll see this as: form renders a field correctly, but `app_fd_<table>.c_<id>`
is never written and the user reports their input "disappearing." Recovery
without a server restart:

1. Open the broken form in App Composer → Form Builder.
2. **Hard-refresh the browser** (Cmd-Shift-R / Ctrl-Shift-R) to ensure App
   Composer is reading the form's current JSON from the DB, not from a
   stale local view.
3. Verify the canvas shows the fields you expected. If it shows the *old*
   layout, App Composer also has a stale cached view — open any other
   form, save it (this evicts cache 1 across the app), hard-refresh, and
   come back.
4. Click Save on the broken form. Joget calls `AppService.saveForm()` →
   `FormDefinitionDao.add()` → both caches refresh atomically. The form
   now persists user input correctly.

This procedure is documented as recovery only. The way to never need it
is to not patch forms with SQL in the first place.

## Joget Form Generation

**Spec input folder:** `./app/form-specs/` (YAML or .spec.md files)
**Form JSON output folder:** `./app/forms/`
**Naming pattern:** `<prefix>-<formId>.json` — e.g. `md03-district.json`, `sp-application.json`

### Module prefixes
- `md` — Master data / lookup forms (MD.xx series)
- `01.xx` — Farmer registration forms
- `02.xx` — Parcel registration forms
- `03.xx` / `sp` — Subsidy program forms
- `im` — Input/campaign management forms
- `jre` — Rule engine forms

### Domain context
- District → Village cascade in stock Joget DX 8: configure the SelectBox with `controlField: "district"` AND its `FormOptionsBinder` with `groupingColumn: "district"` AND `useAjax: "true"`. **Do NOT** use `extraCondition: "district=#district#"` — `extraCondition` is appended verbatim to the WHERE clause with no `#X#` token substitution (verified against `jw-community/wflow-core/.../FormOptionsBinder.java` line 113-116). The cascade mechanism is `groupingColumn` + `controlField` + `useAjax`; at render time `FormUtil.setAjaxOptionsElementProperties` reads the controlField's value and calls `binder.loadAjaxOptions(controlValues)`, which the binder uses to build a parameterised `WHERE e.customProperties.<groupingColumn> in (?)`. Earlier versions of this note documented `extraCondition` with `#X#` tokens — that was wrong; the source-verified pattern is here.
- All farmer forms carry `parent_id` HiddenField for wizard linkage
- GIS forms require companion HiddenFields: `area_hectares`, `perimeter_meters`, `centroid_lat`, `vertex_count`, `auto_center_lat`, `auto_center_lon`
- GIS default coordinates: latitude `-29.6`, longitude `28.2` (Lesotho)
- IdGenerator format: `FR-??????` (farmers), `PC-??????` (parcels), `AP-??????` (applications)
- All MD.xx lookup forms use `idColumn: "code"`, `labelColumn: "name"`. The same convention applies to all `mm_*` meta-model forms per D20 — cross-entity SelectBoxes/SmartSearches always source by the target's business `code`, never by Joget's UUID `id`. (An earlier note said `idColumn: "id"` — that was wrong; the canonical pattern in `md16livestockType` and others uses `code`.)

### Custom plugins installed in THIS Joget instance
Only use these custom element types for Farmers Portal forms — others will cause "plugin not installed" errors:
- ✅ `global.govstack.gisui.element.GisPolygonCaptureElement` — GIS polygon capture
- ✅ `global.govstack.concatfield.element.ConcatFieldElement` — concat/derive field
- ✅ `org.joget.marketplace.EmbeddedDatalist` — embedded datalist
- ✅ `org.joget.plugin.enterprise.FormGrid` — child record grid
- ✅ `org.joget.plugin.enterprise.MultiPagedForm` — wizard
- ✅ `org.joget.plugin.enterprise.CalculationField` — computed numeric
- ✅ `global.govstack.smartsearch.element.SmartSearchElement` — smart search (JAR `joget-smart-search-8.1-SNAPSHOT.jar` is bundled in the JWA; currently used by `md44Input.applicableCrops`)
- ✅ `org.joget.plugin.enterprise.AdvancedFormRowDataListBinder` — datalist binder over a form table with `extraCondition`
- ✅ `org.joget.plugin.enterprise.JdbcDataListBinder` — datalist binder using raw SQL (used by `farmersOverview`, `dl_farmer_listing_advanced`)
- ✅ `org.joget.plugin.enterprise.OptionsValueFormatter` — render FK ID as label in datalist columns
- ✅ `org.joget.plugin.enterprise.DateFormatter` — datalist date column formatter
- ✅ `org.joget.apps.datalist.lib.TextFieldDataListFilterType` — text-input filter for datalist columns (the only stock SelectBox/TextField filter that IS installed). Use it for code/name lookups; users type a partial value and the binder applies a LIKE match.
- ✅ `org.joget.lst.CascadingMdmSelectFilterType` — cascading MDM dropdown filter for datalists
- ✅ `org.joget.lst.DateRangeFilterType` — date-range datalist filter
- ✅ `org.joget.plugin.enterprise.MultirowFormBinder` — child rows load/store binder
- ✅ `org.joget.plugin.enterprise.Signature` — signature capture element
- ✅ `global.govstack.parcelzonecentring.element.AutoCenterBootstrapElement` — invisible element that emits client-side JS to pre-populate GIS auto_center_lat/lon HiddenFields from MD.95 (district × eco-zone) centroids, by reading sibling form-field values from the wizard's DOM. Currently wired on parcelGeometry as the first child of `gisSection`. See decision log D49 for the full architecture rationale.
- ❌ `global.govstack.lookupfield.element.LookupFieldElement` — NOT installed, do NOT use
- ❌ `org.joget.apps.form.lib.FormDataDaoBinder` — does NOT exist, do NOT use
- ❌ `org.joget.apps.userview.lib.CrudMenu` — community-edition class is NOT installed in this Joget. Use `org.joget.plugin.enterprise.CrudMenu` (the enterprise version, which IS installed) for every CRUD menu in the userview. Joget reports the missing community class as "plugin not installed: org.joget.apps.userview.lib.CrudMenu" — misleading because the enterprise CrudMenu IS there, just under a different className.
- ❌ `org.joget.apps.form.lib.TextAreaField` — does NOT exist. The correct class is `org.joget.apps.form.lib.TextArea` (no `Field` suffix). Confusingly, `TextField` IS the right class name — Joget's naming convention is inconsistent.
- ❌ `org.joget.plugin.enterprise.HyperlinkFormatter` — NOT installed (verified May 2026, IM Slice 8 — App Composer raised "plugin not installed" after pushing list_im_distribution.json with this formatter on the signature columns). Don't use it. For "render this column as a clickable link", the workable patterns in this Joget are: (a) bake the `<a href="...">` into a JdbcDataListBinder SQL column with `"renderHtml": "true"` on the column spec, or (b) leave the column unformatted (the bare value renders, and Joget auto-links file-typed columns elsewhere in the UI).
- ❌ `org.joget.apps.datalist.lib.SelectBoxDataListFilterType` — community-edition class is NOT installed (verified May 2026, IM Phase 3 Slice 1 — App Composer raised "plugin not installed" on the form load). For a stock dropdown filter sourced from another form, the only working options in this Joget are: (a) `TextFieldDataListFilterType` (user types code prefix; same UX as every other datalist in the app); or (b) `org.joget.lst.CascadingMdmSelectFilterType` configured with no parent control field — the cascading filter is a superset that handles non-cascading dropdowns too. Don't author SelectBoxDataListFilterType in new datalists; it'll fail "plugin not installed" the moment App Composer or `dl_*.json` is opened.

When you need to display a label from a FK field and LookupFieldElement is not available, use either `SmartSearchElement` (typeahead lookup against an MD form) or a plain readonly TextField populated via workflow. Do NOT use FormDataDaoBinder or any other invented binder className.

### Userview category ordering — business first, config/admin last

The Farmers Portal userview (`app/userviews/v.json`) groups categories by audience. **Business-facing categories on top, configuration / admin / master-data at the bottom**. Within each band, by frequency-of-use. As of May 2026 the canonical order is:

1. **Registration Forms** — citizen + farmer/parcel registration entry points.
2. **MOA Office** — operator inbox, application review, audit list.
3. **Budget** — envelope dashboards, ledger, alerts (operator-finance daily tools).
4. **Inputs Management** — IM module operator surfaces.
5. **Reports** — cross-cutting reporting datalists.
6. **MM - Configuration** — analyst-authoring surface for the metamodel.
7. **Admin** — sysadmin-only utilities.
8. **Master Data** — large lookup-form CRUD; rarely opened, kept last.

When adding a new category to the userview, slot it into the right band. New business-facing modules go between Registration Forms and Reports; new admin / config surfaces go between MM - Configuration and Master Data. Don't insert anything between bands without an explicit reason — operators rely on the visual grouping.

**Within each business category, the rule is `transactional surfaces only`.** Master-data lookups go in the Master Data category, not in the business module's category. Worked example (May 2026, IM Phase 3 Slice 1 second cleanup): the initial Inputs Management category bundled 3 admin lookups (`md_input_unit`, `md_supplier_type`, `md_voucher_status`) alongside one operational entry (`im_supplier`). Wrong shape — admin content in a business band. Fix: move the 3 lookups to Master Data with the next available sequential numbers (MD.91 - Input Unit, MD.92 - Supplier Type, MD.93 - Voucher Status); keep `IM - Supplier` in Inputs Management because supplier registry is operational data (grows over time as procurement onboards real suppliers). The Inputs Management category will fill out properly as Phase 3 ships transactional surfaces (im_inventory, im_voucher, im_distribution).

**Master Data numbering: continue the sequence — and check the actual highest number first.** The legacy MD chain runs all the way to **MD.90** as of May 2026 (not MD.65 — that was a partial-grep miscount in an earlier draft of this guidance). New master-data entries get the next available number after the highest-existing one. Always scan the live `app_userview` JSON before assigning — sub-numbers like MD.16.1, MD.19.1 also exist, and a couple of historical collisions (MD.25, MD.27) carry two entries each. The number is just a stable visual sort key; the form id remains the snake_case business name (e.g. `md_input_unit`, not `md91inputUnit`). Keep the form-id and the menu label decoupled.

To find the next available number programmatically:

```python
import re, json, psycopg2
conn = psycopg2.connect(...)  # standard connect string
cur = conn.cursor()
cur.execute("SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'")
uv = json.loads(cur.fetchone()[0])
nums = []
for cat in uv["categories"]:
    for m in cat.get("menus", []):
        match = re.match(r'^MD\.(\d+)(\.\d+)?\s*-', m["properties"].get("label",""))
        if match: nums.append(int(match.group(1)))
print("Next available:", max(nums) + 1 if nums else 1)
```

Editing the userview lives in the local `app/userviews/v.json` and pushes via `python3 tooling/push_userview.py`. App Composer can also edit it directly; if you do that, **pull the live JSON back into local before re-pushing** (otherwise the next push overwrites your manual changes). Pull is a `SELECT json FROM app_userview WHERE appid='farmersPortal' AND id='v'` — read-only, allowed under HARD RULE.

### Userview menu hygiene — every CrudMenu must have its form AND datalist alive

Every `CrudMenu` in the userview points at `editFormId` (+ optional `addFormId`) AND `datalistId`. Joget renders the menu without checking either — clicking a menu whose form/datalist doesn't exist surfaces an error mid-page (`"Data List X not exist"` or similar). The user lands on a broken page. Three rules:

1. **Before adding a menu**, confirm both the form and the datalist already exist in the JWA. If you authored a new form, also author + push its companion datalist before wiring the menu. The push order is: form → datalist → userview.
2. **When removing a form or datalist**, search the userview for any menu that references it and remove the menu in the same change. Don't leave a menu pointing at something that's gone.
3. **Periodic audit** — run a check that every CrudMenu's `editFormId`, `addFormId`, and `datalistId` resolves to a live row in `app_form` / `app_datalist`. Worked example pattern (read-only, allowed):

   ```python
   # Pseudocode — see the audit run on 2026-05-06 for the full version.
   forms = {r[0] for r in cur.execute("SELECT formid FROM app_form WHERE appid='farmersPortal'")}
   datalists = {r[0] for r in cur.execute("SELECT id FROM app_datalist WHERE appid='farmersPortal'")}
   for cat in uv["categories"]:
     for menu in cat["menus"]:
       p = menu.get("properties", {})
       if p.get("editFormId") and p["editFormId"] not in forms: ...
       if p.get("datalistId") and p["datalistId"] not in datalists: ...
   ```

Verified May 2026 (Slice 1 cleanup): the May 6 audit found 1 dead menu — `MD.38 - Input Category` pointed at `list_md38InputCategory` which had never been authored. Fix: remove the menu (the form `md38InputCategory` exists but is the unused MD.38 chain — `md27inputCategory` is the canonical Input Category form). Going forward, run this audit after any form/datalist removal pass so dead menus don't accumulate.

### Available DataList IDs (use exact IDs — never invent new ones)
Farmer registration: `list_farmerBasicInfo`, `list_farmerResidency`, `list_farmerAgriculture`, `list_farmerHousehold`, `list_farmerCropsLivestock`, `list_farmerIncomePrograms`, `list_farmerDeclaration`, `list_farmerRegistrationForm`, `farmersOverview`, `dl_farmer_listing_advanced`
Parcels: `list_parcelRegistration`, `list_parcelLocation`, `list_parcelGeometry`, `list_parcelClassification`, `list_parcelCrops`, `list_cropDetailForm`, `listFarmersParcels`
Household: `list_householdMemberForm`
Programs: `list_spProgram`, `list_spProgramMain`, `list_spProgramIdentity`, `list_spProgramTimeline`, `list_spProgramGeography`, `list_spProgramEligibility`, `list_spProgramBenefits`, `list_spProgramBeneficiary`, `list_spProgramApproval`, `list_spProgramMonitoring`
Applications: `list_spApplication`, `list_spApplicationDoc`, `list_spFarmerDerived`
Entitlements: `list_imEntitlement`, `list_imEntitlementItem`
Harvest: `list_spCropHarvItem`, `list_spCropHarvestItem`

### FormGrid child form field IDs (for grid column definitions)
When generating a FormGrid pointing to these child forms, use ONLY these exact field IDs as column `value`:

**householdMemberForm** → `memberName`, `sex`, `date_of_birth`, `relationship`, `orphanhoodStatus`, `participatesInAgriculture`, `disability`, `chronicallyIll`
**livestockDetailsForm** → `livestockType`, `numberOfMale`, `numberOfFemale`
**imCampaignInput** → `inputCode`, `unit`, `totalQtyAvailable`, `subsidyRatePct`, `maxPerFarmer`
**imDistribItem** → `inputCode`, `quantity`, `unit`, `unitPrice`, `subsidyAmount`, `farmerPays`
**imEntitlementItem** → `inputCode`, `qtyEntitled`, `unit`, `subsidyAmount`, `farmerContrib`, `qtyRedeemed`, `itemStatus`

### Existing forms reference
See `APP_farmersPortal-*.jwa` for full form inventory (**119 forms** as of `APP_farmersPortal-1-20260425161508.jwa`).
Key forms to reference for patterns:
- `farmerBasicInfo` — multi-section registration with Radio, SelectBox, DatePicker
- `parcelGeometry` — GIS polygon capture with auto-center and overlap check
- `farmerRegistrationForm` — MultiPagedForm wizard (8 pages)
- `spApplication` — large program application form
- `md03district` — canonical simple MD lookup form

### Known issues to fix in the current JWA
Found during 2026-04-25 audit of `APP_farmersPortal-1-20260425161508.jwa`:

**Broken `formDefId` references** (the target form does not exist):
- `imCampaign.targetDistricts` → `md04agroEcologicalZone` — should be `md04agroEcologicalZo` (Joget truncates form IDs to 24 chars to keep table names ≤32)
- `md44Input.applicableCrops` → `md04agroEcologicalZone` — almost certainly a copy-paste error; field is "Applicable Crops" so should target `md19crops` (or `md191cropCategory`)
- `md44Input.categoryCode` → `md44InputSubcat` — no such form; either create the subcategory MD form or change to point at `md38InputCategory`
- `md44Input.manufacturer` → `mdSupplier` — no such form; create a supplier MD form or remove the binder

**Scrap forms (safe to delete except where noted):**
- `x`, `x2` — placeholder test forms, no external refs
- `testApiKey123` — test fixture
- `formCreator` — referenced by the userview menu "Form Creator"; remove the menu first if deleting
- `allFieldTypes` — referenced by the userview menu "Manage 99.00 - All Field Types (Test)"; remove the menu first if deleting

### Naming convention reminder
Joget form IDs are capped at **24 characters** (so the underlying `app_fd_<id>` table stays ≤32 chars). Several MD forms in this app have truncated IDs that don't match their human label (e.g. label "MD.04 - Agro Ecological Zone" → id `md04agroEcologicalZo`). When generating new forms or referencing existing ones, always use the truncated ID exactly as it appears in the JWA — never reconstruct from the label.

### Widget configuration via `mm_field.widgetConfig`

Per ADR-029 (L1-5b, May 2026): widget-specific configuration on `mm_field` rows is analyst-authorable through a JSON blob in the `widgetConfig` column. Kernel ships defaults; analyst overrides via JSON; per-widget allowlists in `MetaScreenElement` decide which keys can be overridden. Locked keys (`id`, `label`, `validator`, plus per-widget field-binding props like `areaFieldId` for gis_polygon) are kernel-managed and silently dropped from override JSON with a log warning. Full per-widget property catalogues live in `docs/architecture/architecture/components/mm-form-gen-kernel.md` §8.7 — read that before authoring widgetConfig overrides.

### FormGrid foreignKey convention inside a MultiPagedForm wizard

**Rule:** when a FormGrid sits inside a wizard tab subform, its child rows store the IMMEDIATE tab subform's record id in the FK column — **not** the wizard parent's id.

Concretely, in spProgramMain (wizard) → spProgramGeography (Tab 3 subform) → districtAllocations (FormGrid):

- `sp_program.id` = wizard record id (e.g. `prog001`)
- `sp_program_geography.id` = the Tab 3 record id (e.g. `prog001-geog`)
- `sp_program_geography.c_parent_id` = `prog001` (links tab → wizard)
- `sp_district_alloc.c_program_id` = **`prog001-geog`** (links grid row → tab, NOT → wizard)

Verified against the working `farmerHousehold → householdMemberForm` pattern: `app_fd_household_members.c_farmer_id = farmer_household.id` (the household tab's id), NOT `farms_registry.id` (the wizard's id).

When seeding wizard-form data, `c_program_id` (or any FormGrid foreignKey column) must be set to the matching tab subform's record id. Do this through `FormDataDao.saveOrUpdate(...)` from a plugin, the form-data REST API, or App Composer — never raw SQL (see the "HARD RULE" section at the top). The wizard's `c_tab_*` columns (e.g. `c_tab_geography`) are the OPPOSITE direction — they hold the tab record's id, populated by `MultiPagedForm` so the wizard can navigate to its tabs.

(The two-cache invalidation trap that used to live here has moved to the
"What goes wrong if you violate the rule" subsection at the top, alongside
the rule itself — read that for the procedural details. Keeping cache
forensics in one place to make it harder to mistake for a development
workflow.)

### FormGrid stores its column definitions under the property name `options`, NOT `columns`

The native parcelGeometry / farmerHousehold forms expose what looks like a "columns" UI in Form Builder, but the underlying JSON property is named `options[]` — each entry is a Map with `value` (field id), `label` (header), `width`, `format`, `formatType`. Naming a widgetConfig key "columns" instead of "options" silently fails: the grid renders the right row count (binder works) but with empty headers and rows showing only row numbers. Always use `"options": [...]` in the widgetConfig JSON for repeating_group widgets.

### FormGrid requires both `loadBinder` AND `storeBinder`

A FormGrid that only has a `storeBinder` will SAVE child rows correctly but won't LOAD existing rows when the parent is opened for edit — the grid renders empty. The canonical working pattern (verified in `farmerHousehold` / `livestockDetailsForm`) carries **both** binders, both pointing at `MultirowFormBinder` with the same `formDefId` and `foreignKey`:

```json
"loadBinder": {
  "className": "org.joget.plugin.enterprise.MultirowFormBinder",
  "properties": { "formDefId": "<child>", "foreignKey": "<fk_column>" }
},
"storeBinder": {
  "className": "org.joget.plugin.enterprise.MultirowFormBinder",
  "properties": { "formDefId": "<child>", "foreignKey": "<fk_column>" }
}
```

If you only ship the storeBinder and the grid renders empty on edit, the loadBinder is the missing piece — symptom looks identical to a data-integrity issue (FK pointing at the wrong parent id), so check the form definition first before chasing the data.

### Joget DatePicker uses its own format dialect, NOT Java SimpleDateFormat

When configuring `org.joget.apps.form.lib.DatePicker.format` (whether in form JSON or programmatically via `setProperty("format", ...)`), use **Joget's own format dialect**:

* `yy` → 4-digit year (Joget translates to Java `yyyy`)
* `mm` → 2-digit month numeric (Joget translates to Java `MM`)
* `MM` → month name, full (Joget translates to Java `MMMMM`)
* `dd` → 2-digit day
* `D` / `DD` → day-of-week short / full

So the ISO `yyyy-MM-dd` Java pattern is written as **`yy-mm-dd`** in Joget's DatePicker format. Passing the Java pattern directly produces the bug `"yyyy-MM-dd"` → `"yyyyyyyy-MMMMM-dd"` (eight-digit year + month name) because Joget's `getJavaDateFormat()` re-translates every `yy` to `yyyy` and every `MM` to `MMMMM`. The placeholder shown in the input box (`YYYYYYYY-MMMMM-DD`) is a quick visual signal of this mistake.

### FK convention split: Joget-internal UUIDs vs cross-entity business codes (D20)

Two-tiered convention applied across this app — see decision-log entry D20 for the architectural reasoning, here is the operational summary:

* **Joget-internal FK** — relationships managed automatically by Joget machinery (FormGrid row → parent, wizard tab subform → wizard, AbstractSubForm → host) — store the parent's auto-generated `id` (UUID). Hidden from authors, never appear on forms. Examples: `mm_field.screenId` (FormGrid-managed), `household_members.farmer_id` (FormGrid-managed inside the wizard's household tab).
* **Cross-entity / user-visible reference** — relationships established explicitly by an analyst picking from a dropdown or SmartSearch (e.g. Parcel → Farmer, mm_field.optionsCatalogId → mm_catalog, MetaScreenElement.screenId widget property → mm_screen) — store the target's **business `code`**. Use `idColumn: "code"`, `labelColumn: "name"` (or `"title"` for `mm_screen`).

Canonical anchor: `farmerBasicInfo.national_id` (DuplicateValueValidator, business key) is referenced as `parcel.farmer_id` storing the national_id, not the UUID.

### Master Data / `mm_*` configuration entities — D20 + D22 + D23

All twelve `mm_*` configuration entities (`mm_institution`, `mm_service`, `mm_registration`, `mm_screen`, `mm_field`, `mm_catalog`, `mm_action`, `mm_required_doc`, `mm_fee`, `mm_benefit`, `mm_determinant`, `mm_role`, `mm_role_screen`) follow the same shape:

* No `id` field on the form — Joget assigns UUID, never visible to authors.
* `code` (TextField, `DuplicateValueValidator` self-referencing — handles mandatory + uniqueness in one) — UPPER_SNAKE_CASE, e.g. `MIN_AGRO`, `INPUT_SUBSIDY_001`, `FIRST_SUBSIDY_INTRO`.
* `name` (TextField, `DefaultValidator` mandatory) — display label, not required to be globally unique.
* No `isActive` — hard-delete only; obsolete codes are replaced by new ones, both rows coexist (D23). Don't add temporal validity columns without a full versioning design.
* `mm_screen` exception: uses `title` as the display name (existing column, kept for spec alignment with §7.2.3).
* `mm_field` exception: no global `code`; the natural key is `(screenId, storageKey)` and Joget can't enforce composite uniqueness. Disciplined storageKey naming within a screen suffices (option (a)).

`mm_catalog` is acknowledged as the Master Data implementation per D22 — RegBB §7 doesn't formalise MD as an entity, the spec mentions catalog only in passing (lines 546, 577, 580, 638). Documented in `convergence-framework.md` §9 as an identified RegBB spec gap, candidate upstream feedback to GovStack.

### Rule engine — `mm_determinant` unified store (ADR-031)

**One table, multiple consumers, discriminated by `scope`.** Per ADR-031 (May 2026), every rule the platform evaluates lives in `mm_determinant` regardless of who runs it. The `scope` column tells each consumer which subset of rows belongs to it:

| `scope` value | Read by | Purpose |
|---|---|---|
| `eligibility` | `RoutingEvaluator` (reg-bb-engine) | Pass/fail/warning rules for citizen applicability |
| `quality` | `RuleRepository` (form-quality-runtime) | Form-quality probes that fire on every save (severity error / warning) |
| `bot_pull` | `BotPullEvaluator` (reg-bb-engine) | Auto-fill rules invoked by `MetaScreenElement` capability adapters |
| `applicability` | `RoutingEvaluator` | Catalogue display ("which programmes can this farmer see") |
| `decision_to_status` | `DecisionMapper` (reg-bb-engine) | Operator-decision → application-status mapping |
| `budget_amount` | `CostEstimationService` | Programme-launch cost estimation |

When authoring a rule, the analyst goes to **MM-Configuration → MM-Rules** in the userview, picks the right `scope` for the consumer they want to drive, and fills in the scope-relevant fields. Filters at the top of the list let an analyst see "just my eligibility rules" or "just my quality rules" — the column shape is unified, the discipline is per-row.

**Pre-ADR-031 shape (now retired).** Quality rules used to live in a separate `qa_rule` form in a separate `formQuality` Joget app. RuleRepository read from `qa_rule`; everything else read from `mm_determinant`. Slices A through E.3 unified the two stores: legacy rules were migrated byte-identically, the runtime was switched to read `mm_determinant` filtered by `scope='quality'`, the old authoring UI was deprecated, and the formQuality app was relocated and ultimately deleted. **Do not author new rules in `qa_rule`.** That table still exists in Postgres as a forensic backup but is no longer read by any code.

**Form-quality runtime infrastructure (still present, under farmersPortal).** Three operational tables survived the formQuality app delete because they hold runtime state:

* `qa_record_status` — current quality status of every saved record (one row per `(formId, recordId)`). Written by `IssueRepository` on every form save.
* `qa_issue` — open quality issues per record. Written when a quality probe fails; cleared when the probe passes again.
* `audit_log` — status-transition history for the `FORM_QUALITY_ISSUE` lifecycle, written by `StatusFramework`.

These are NOT `mm_*` because they're not analyst-authored configuration; they're runtime infrastructure. The form definitions live under `farmersPortal` (relocated from formQuality in Slice E.3) and follow the bounded-context naming convention parallel to `reg_bb_eval_audit` and `processing_queue`. Don't try to author rows in them by hand or move them into the `mm_*` namespace.

### Reading Joget source — `jw-community/` and `api-builder/` are checked in

Full Joget Community Edition source is in this repo at `jw-community/` (the full DX 8.1 community-edition tree, including `wflow-core/`, `wflow-commons/`, `wflow-plugin-base/`, etc.) and the API Builder source is at `api-builder/`. **Read the source first.** Do not decompile JARs as the primary investigation tool — decompiled bytecode is incomplete (lambdas elided, generics erased, comments gone), reading source takes seconds and is unambiguous.

When working on the **form-creator-api** plugin (or any plugin that uses the `@Operation`/`@Param`/`@Response` annotation set, exposes `/jw/api/{apiId}/...` endpoints, or extends `ApiPluginAbstract`), the canonical reference is `api-builder/`:

* `api-builder/apibuilder_api/` — the SPI: `ApiPlugin`, `ApiPluginAbstract`, `ApiDefinition`, `ApiResponse`, plus the `org.joget.api.annotations` package (`@Operation`, `@Param`, `@Response`, `@Responses`, etc.). Read these to know what annotations are available and what their attributes mean.
* `api-builder/apibuilder_plugins/` — concrete plugins that ship with API Builder; useful as worked examples of the same patterns we use.
* `api-builder/apibuilder_sample_plugin/` — minimal end-to-end example of an API plugin (`SampleAPI.java` + `Activator.java` + `SampleObject.java`). Look here when adding a brand-new endpoint to remind yourself how the request/response shape, error handling, and OpenAPI metadata fit together.

### Wrapping a stock Joget element — read the source FIRST

When the kernel's `MetaScreenElement` synthesises a stock Joget element (any switch case in `synthesiseField` — TextField, SelectBox, FormGrid, GisPolygonCaptureElement, SmartSearchElement, Signature, etc.), the wrapper must produce **the same configured object Joget would produce when loading the element from JSON**. Anything else is a different code path that the rest of the platform doesn't know about, and it will break in non-obvious ways.

**The discipline.** Before writing the synthesise case, do these three reads in `jw-community/wflow-core/src/main/java/org/joget/apps/form/`:

1. **The element's own class** (e.g. `lib/SelectBox.java`). Find its render method and the methods it calls to fetch its data (`getOptionMap`, `getControlElement`, etc.). Note which **fields** they read (`controlElement`, `optionsBinder`) versus which **properties** they read (`getProperty("controlField")`). The distinction matters: fields are set via dedicated setters on the Element, properties are entries in the properties Map. Mixing them up is the most common synthesis bug — a Map-shaped binder put into properties is silently rejected by the element which expects a real binder via `setOptionsBinder()`.

2. **The element's binder, if any** (e.g. `lib/FormOptionsBinder.java`). Find the actual loader method (`load`, `loadAjaxOptions`, `loadFormRows`, etc.). Note exactly which configuration properties drive its behaviour and how it processes them — `extraCondition` literally vs. with token substitution; `groupingColumn` for parameterised cascades; etc. Don't trust prose documentation about these — the source is the contract.

3. **`FormUtil.parseBinderFromJsonObject`** (`service/FormUtil.java` ≈ line 349). This is the canonical pattern Joget uses to instantiate binders from JSON. Replicate it in your synthesise: `pluginManager.getPlugin(className)` → `binder.setProperties(props)` → `binder.setElement(parent)` → `parent.setOptionsBinder(binder)`. Don't `new` the binder yourself — the PluginManager path registers it correctly with Joget's lifecycle hooks, gets you a properly-classloader-scoped instance, and is what every JSON-loaded form does.

**Why this discipline matters.** Joget's element classes pull their dependencies from a mix of fields and properties. Documentation (this file, `mm-authoring-guide.md`, even other comments in the codebase) sometimes lags behind or simplifies. The source is the truth. Reading three small files (the element, the binder, `FormUtil.parseBinderFromJsonObject`) takes about ten minutes; getting it wrong takes hours. Always read first.

**Worked example — cascading SelectBox** (L1-1, May 2026): the first two attempts wrote a Map into `optionsBinder` property and used `extraCondition: "district=#field.district#"` based on prose notes. Both wrong. The third attempt — after reading `SelectBox.java` (controlField property + optionsBinder field), `FormOptionsBinder.java` (groupingColumn drives cascade, extraCondition is verbatim), and `FormUtil.parseBinderFromJsonObject` (PluginManager + setProperties + setElement + setOptionsBinder) — worked first time. The `attachCascadingOptionsBinder` method in `MetaScreenElement` is now the reference shape for any future wrapper that needs an options binder.

### Calling form-creator-api endpoints — use API Builder credentials, not basic auth

The form-creator-api endpoints (`POST /jw/api/formcreator/formcreator/forms`, `/seed`, `/clear`, etc.) are exposed via API Builder, which has its own auth: an `api_id` + `api_key` pair registered against the API definition. Do **not** use HTTP Basic auth (`-u admin:admin`) — even when it appears to work, it bypasses the API Builder access-control path and produces inconsistent behaviour against rate-limit and audit-log middleware.

The api_key is shared (one credential per app — `farmerPortal`); the **api_id is per-API** because each API Builder API has its own UUID. Wrong api_id → API Builder rejects with a generic `{"date":"...","code":"400","message":"Bad Request"}` *before* your handler runs, so it looks like a body validation problem when it's really an auth/route problem. The canonical pairs in this instance:

| API Builder API | api_id (UUID, header) | api_key (shared) | Endpoint examples |
| --- | --- | --- | --- |
| `formcreator` (form-creator-api) | `API-e7878006-c15a-425e-9c36-bebc7c4d085c` | `<JOGET_API_KEY>` | `/jw/api/formcreator/formcreator/{forms,seed,clear,datalists,userviews}` |
| `regbb` (RegBbEvalApi) | `API-168e3678-1f9a-46fc-8c19-d0d9a917eb73` | `<JOGET_API_KEY>` | `/jw/api/regbb/{eval,submit}` |
| `gis` (GisApiProvider) | (look up in `app_builder` where `name='gis'`) | `<JOGET_API_KEY>` | `/jw/api/gis/gis/calculate` |
| MD.xx generated APIs (one per master-data form) | per-API in `app_builder` | `<JOGET_API_KEY>` | `/jw/api/<apiPath>/...` |

Source-verified: `api-builder/apibuilder_plugins/.../service/ApiBuilder.java` ≈ line 745 enforces `apiId.startsWith("API-")` against the `api_id` request header; that header value is matched to the API definition's UUID in `app_builder.id`. The api_key matches a row in the `api_credential` table by `apikey` value (which is *not* scoped per-API in this instance — there is one `farmerPortal` credential covering everything).

`tooling/seed.py` hard-codes the formcreator pair (`JOGET_API_ID`, `JOGET_API_KEY`) because that script only ever talks to formcreator. For ad-hoc curl against other APIs (regbb, gis, MD.xx), look up the api_id from the database — either via App Builder UI → "API Builder" → hover the API → URL contains its id, or:

```sql
SELECT id AS api_id, name FROM app_builder WHERE type='api' AND name = 'regbb';
```

Pass them as either query params (`?api_id=...&api_key=...`) or, preferably, as headers:

```sh
# regbb /eval
curl -X POST -H "Content-Type: application/json" \
  -H "api_id: API-168e3678-1f9a-46fc-8c19-d0d9a917eb73" \
  -H "api_key: <JOGET_API_KEY>" \
  -d '{"determinantCode":"...","applicantData":{...}}' \
  'http://20.87.213.78:8080/jw/api/regbb/eval'

# formcreator /forms
curl -X POST -H "Content-Type: application/json" \
  -H "api_id: API-e7878006-c15a-425e-9c36-bebc7c4d085c" \
  -H "api_key: <JOGET_API_KEY>" \
  --data-binary @app/forms/mm/mm-field.json \
  'http://20.87.213.78:8080/jw/api/formcreator/formcreator/forms?createCrud=false'
```

Same convention applies to every `/jw/api/{apiPath}/...` endpoint in this Joget instance.

Concrete entry points worth knowing when debugging the form save/load lifecycle (real paths, all verified):

* `jw-community/wflow-core/src/main/java/org/joget/apps/form/service/FormUtil.java`
   * `executeElementFormatData(Element, FormData)` — the recursive walker that calls each element's `formatData` and merges results into the storeBinder rowSet. **Recursion happens regardless of whether the current element has a binder; only the binder block is conditional.** Read this when wondering "why isn't my synthesised child being persisted".
   * `findStoreBinder(Element)` — walks `element.getParent()` chain until it finds a non-null `getStoreBinder()`. If your dynamic children's parent chain is broken anywhere, formatData skips them silently. Diagnostic: log the parent chain inside your container's `formatData`.
   * `findRootForm(Element)` — same pattern but stops at the first `Form` ancestor.
* `jw-community/wflow-core/src/main/java/org/joget/apps/form/service/FormServiceImpl.java`
   * `executeFormStoreBinders(Form, FormData)` — orchestrator. Calls `executeElementFormatData(form, formData)` first (builds the rowSet), then `recursiveExecuteFormStoreBinders(form, form, formData)` (invokes binders). If the rowSet is empty, no `storeBinder.store()` runs and no UPDATE happens — `datemodified` stays unchanged, which is your first symptom that the rowSet didn't get populated.
   * `recursiveExecuteFormStoreBinders(...)` — gates everything on `!isReadonly && isAuthorize && continueValidation`. If any of those fails on an ancestor, the binder is never invoked.
* `jw-community/wflow-core/src/main/java/org/joget/apps/form/dao/FormDataDaoImpl.java`
   * `findAllElementIds(Element, Collection<String>)` — the schema-discovery walker that parses the form definition JSON and decides which DB columns the form's Hibernate mapping gets. **Calls `element.getDynamicFieldNames()` on every element** — that's the documented Joget hook for elements that synthesise children at runtime. Override it to declare runtime-only column IDs.
   * `findSessionFactory(...)` — at `actionType=0` (form save) reconciles columns from `getFormDefinitionColumnNames`. At `actionType=1` (data save) builds Hibernate mapping from `getFormRowColumnNames(rowSet)` — i.e., from the keys actually present in the rowSet at save time. **Schema reconciliation only ALTERs the table when a new SessionFactory is created (`getSessionFactory(..., needUpdate=true)` invokes `internalUpdateSchema` → Hibernate `SchemaUpdate`).** Forces the reconciliation by saving the form once after changing `getDynamicFieldNames`.
* `jw-community/wflow-core/src/main/java/org/joget/apps/form/lib/FileUpload.java`
   * `formatData(FormData)` — reads request params, calls `FileManager.getFileByPath(value)` per value, on temp-file hit emits `result.setProperty(id, file.getName())` AND `result.putTempFilePath(id, paths)`. The `tempFilePath` metadata is what triggers `FileUtil.storeFileFromFormRowSet` to MOVE files from the temp staging dir to the form's permanent uploads dir. Replicate this contract precisely if you ever contribute file values from a container's `formatData` instead of relying on Joget's recursion.
* `jw-community/wflow-core/src/main/java/org/joget/apps/form/service/FileUtil.java`
   * `storeFileFromFormRowSet(FormRowSet, String tableName, String primaryKeyValue)` — iterates `tempFilePathMap` per row, moves files to `wflow/app_formuploads/<tableName>/<primaryKeyValue>/<filename>`. The DB column stores only the bare filename; the file location is reconstructed from `tableName + primaryKey`.
* `jw-community/wflow-core/src/main/java/org/joget/apps/app/service/AppServiceImpl.java`
   * `storeFormData(formDefId, tableName, FormRowSet, primaryKey)` — the real save path. Calls `formDataDao.updateSchema(formDefId, tableName, rows)` (column reconciliation), then `formDataDao.saveOrUpdate(...)` (UPDATE), then `FileUtil.storeFileFromFormRowSet(...)` (file move). Set a breakpoint here mentally when wondering whether a save actually persisted.

### Audit retention — `reg_bb_eval_audit` grows unbounded by design

The `reg_bb_eval_audit` table accumulates one row per Determinant evaluation
plus one per operator decision plus one per workflow dispatch — comfortably
into the tens of thousands per active subsidy cycle. There is no automatic
TTL: the table is the forensic timeline operators rely on when investigating
"why did this applicant get this disposition?", so silent garbage-collection
would falsify the very evidence the audit list exists to provide.

Operations playbook:

* **Default retention** — keep all rows for the lifetime of the subsidy cycle
  they reference (typically 12-24 months including the appeal window).
* **Quarterly archival** — at the close of each quarter, copy rows older
  than the retention horizon to a cold archive (CSV or a separate
  `reg_bb_eval_audit_archive` table), THEN delete from the live table. The
  archive is what survives long-term; the live table stays small enough for
  the operator UI to render the full list inside ~200 ms.
* **Read-side performance** — the operator audit list orders by
  `dateCreated DESC`. Postgres serves that ordering via the table's primary
  key + a btree on `dateCreated` (auto-created by Joget). At &gt; 100k rows
  consider an explicit `CREATE INDEX idx_reg_bb_eval_audit_app_id ON
  app_fd_reg_bb_eval_audit (c_application_id, datecreated DESC)` so the
  per-applicant filter on the operator review screen stays fast.
* **One-shot prune** (only after archival is verified):

  ```sql
  -- READ first to confirm the row count
  SELECT count(*) FROM app_fd_reg_bb_eval_audit
   WHERE datecreated &lt; now() - interval '90 days';
  -- Then prune. NO HARD-RULE violation: this table is reg-bb-engine-owned
  -- audit data, not Joget metadata; it has no Hibernate mapping outside its
  -- own form. Even so, prefer running this from a maintenance window.
  DELETE FROM app_fd_reg_bb_eval_audit
   WHERE datecreated &lt; now() - interval '90 days';
  ```

A scheduled-task plugin to automate the archival is deferred per Convention
over Invention — operators get more value from a documented quarterly
ritual than from a half-finished cron-style automation. Revisit when the
table crosses 1 M rows.

### Plugin writes to form tables go through `AppService.storeFormData`, not `FormDataDao.saveOrUpdate` directly

Calling `FormDataDao.saveOrUpdate(formId, tableName, rows)` directly from plugin code skips Joget's metadata-population step — the resulting rows have NULL `datecreated`, `datemodified`, `createdby`, `createdbyname`, `modifiedby`, `modifiedbyname`. Verified May 2026 (task #235): 23 of 284 `app_fd_*` tables in this app had 100 % NULL timestamps; every one of them written via direct `FormDataDao.saveOrUpdate`. Native Joget tables (those written through the Save button or the citizen API) had 100 % populated timestamps.

The right path is `AppService.storeFormData(formDefId, tableName, FormRowSet, primaryKeyValue)`. Source-verified at `wflow-core/.../AppServiceImpl.java` ≈ line 2135–2209: `storeFormData` is the wrapper around `FormDataDao.saveOrUpdate` that Joget's own `WorkflowFormBinder.store` chain uses. It generates UUIDs for missing ids, sets `dateModified = now`, looks up the previous row's `dateCreated` and reuses it on updates (or sets `dateCreated = now` for inserts), captures the current user via `WorkflowUserManager`, calls `FormDataDao.updateSchema(...)` to reconcile new columns, then delegates to `FormDataDao.saveOrUpdate(...)`.

**Use the `RowWriter` helper** at `plugins/reg-bb-engine/.../support/RowWriter.java`:

```java
import global.govstack.regbb.engine.support.RowWriter;
...
FormRow row = new FormRow();
row.setId(applicationId);
row.setProperty("status", "approved");
FormRowSet rs = new FormRowSet();
rs.add(row);
RowWriter.save(formDefId, tableName, rs);   // populates timestamps + actor attribution
```

For other bundles that don't import `reg-bb-engine` (e.g. `form-creator-api`), call `AppService.storeFormData(...)` directly — same fix, no helper needed.

**The lower-level `FormDataDao.saveOrUpdate` is still useful** for callers that supply their own timestamps (e.g. a data-import path replaying historical events with their original timestamps). For everything else — operator-triggered actions, scheduled sweepers, post-processors, fixture seeding — go through `AppService.storeFormData` so the row reads back as a normal Joget row everywhere downstream.

**Existing NULL rows are not backfilled.** The HARD RULE forbids raw SQL on `app_fd_*` tables; reconstructing accurate historical timestamps from any other source isn't possible. Reports that need event ordering use business-date columns (`c_issued_date`, `c_redemption_date`, `c_decided_at`, `c_eval_started_at`) as the authoritative source — that's the long-term right pattern anyway, since system timestamps reflect when Joget wrote the row, not when the business event happened.

**Two specific gotchas to know:**

1. The `dao` local variable can stay declared after the swap — Java doesn't fail builds on unused locals. If you remove the variable entirely, also drop the unused `import org.joget.apps.form.dao.FormDataDao` to keep the file clean. The build doesn't fail either way.
2. Helper methods that received `FormDataDao dao` as a parameter and only used it for `saveOrUpdate` no longer use the parameter after the swap. The parameter can stay (unused-parameter warning, harmless) or be removed (touches the call sites). Either is fine; pick whichever produces a smaller diff.

### Every plugin class needs an Activator registration

Adding a new class that extends `DefaultApplicationPlugin`, `WorkflowFormBinder`, `Element`, or any other Joget plugin SPI is only HALF the work — the other half is registering it in the bundle's `Activator.start()` via `context.registerService(ClassName.class.getName(), new ClassName(), null)`. Without that, the class compiles into the JAR, the JAR uploads cleanly, but Joget's PluginManager has no way to discover it, and any reference to its class name (e.g. as a form's storeBinder) surfaces as **"There are plugins not installed"** in App Composer.

The trap: classes that are only invoked through direct `new ClassName()` calls (e.g. tools called from a sibling REST endpoint in the same bundle) work fine without registration — the compiler resolves the class reference, and the OSGi classloader exposes it within the bundle. So you can ship a tool class, see it work via the REST endpoint, and not realise it's invisible to the platform until something tries to look it up by class name through PluginManager.

**Two rules to avoid this:**

1. After adding any class that extends a Joget plugin SPI, add a corresponding `registerService` in `Activator.java`. Bump the build counter. Re-deploy.
2. Before re-deploying, scan: every plugin SPI subclass in `src/main/java/.../` should appear at least once in `Activator.start()`. A grep helps:
   ```
   grep -rh "extends \(DefaultApplicationPlugin\|WorkflowFormBinder\|Element\|ApiPluginAbstract\|FormBinder\|Validator\)" src/main/java | grep -oE "class [A-Z][A-Za-z0-9]+" | sort -u
   grep -oE "registerService\([a-zA-Z0-9.]+\.class\.getName" src/main/java/.../Activator.java | sort -u
   diff the two lists.
   ```

Verified May 2026 (IM Phase 3 Slice 6): authored `StockTransactionStoreBinder`, `VoucherIssuanceTool`, `VoucherRedemptionTool` across Slices 4-6, only the binder was reachable via class-name lookup (form storeBinder), so only the binder triggered the App Composer error. The two tools worked because BudgetApi instantiated them directly. Build-103 added all three to Activator. Going forward, treat the Activator file as part of the change set whenever a new plugin SPI subclass is authored.

### Form post-processor vs. storeBinder — when to reach for which

Joget gives you two extension points around a form save: the **storeBinder** (runs as part of the save tree, per element) and the **form post-processor** (runs once after the entire save tree commits). Pick by where in the lifecycle the side-effect needs to fire:

| Need | Use |
|---|---|
| Replace or augment how the parent's own row gets persisted | parent's `storeBinder` (extend `WorkflowFormBinder`) |
| Validate / transform child rows as they save | child form's `storeBinder` |
| Apply a side-effect that needs **both parent + all FormGrid children** to be on disk first (rollups, ledger postings, fan-out emails over aggregate state) | parent's `postProcessor` (any `DefaultApplicationPlugin`, wired in form JSON's `postProcessor.className` + `postProcessorRunOn`) |

Why the distinction matters. Joget's `executeFormStoreBinders` walks the form tree depth-first. The parent's storeBinder fires **before** any FormGrid's storeBinder, because the FormGrid is a child element and the recursion is post-order. So at parent-storeBinder time the child rows are not yet committed to the DB — `SELECT ... FROM app_fd_<child_table> WHERE c_<fk> = ?` returns empty. Trying to apply rollup logic in the parent's storeBinder produces a silent zero-output bug.

Source-verified `wflow-core/src/main/java/org/joget/apps/form/service/FormUtil.java` ≈ line 2202: `executePostFormSubmissionProccessor(form, formData)` is invoked from `AppServiceImpl.submitForm` ≈ line 1985, *after* `formService.submitForm(...)` returns — i.e. after the whole form tree has been persisted. The post-processor receives a Map with `recordId` (= parent's primary key), `appDef`, `pluginManager`, and `request`, and runs as a regular `ApplicationPlugin.execute(props)`.

**RunOn semantics.** Form JSON property `postProcessorRunOn` accepts `create`, `update`, `both`, `draft`. Source contract: `if (run.equals(status) || ("both".equals(run) && !"draft".equals(status)))`. For append-only audit data (where edits should not retrigger side-effects) use `"create"` plus an idempotency flag column on the parent (e.g. `posted_at`) for belt-and-braces. For data that should re-sync on every save use `"both"`.

Worked example: IM Slice 6d's `StockTransactionPostProcessor` applies inventory deltas after both the parent stock-txn and its line-grid rows commit. Lives at `plugins/reg-bb-engine/src/main/java/global/govstack/regbb/engine/processing/StockTransactionPostProcessor.java`. The earlier Slice 6a's `StockTransactionStoreBinder` (one-row-per-movement shape, inventory-update inline) is preserved as legacy code — D37 explains the migration.

### `c_correlation_id` vs. `c_idempotency_key` on `app_fd_budget_event`

Both columns exist on `app_fd_budget_event` and they are NOT the same thing. Mixing them up yields silent empty-result-set failures during forensics or testing.

| Column | Holds | Example |
|---|---|---|
| `c_correlation_id` | The bare business key the event refers to (no prefix) | `VCH-20260506-0005`, `RDM-20260506-0003`, application UUID |
| `c_idempotency_key` | The dispatch's idempotency key (prefixed) | `voucher_issued:VCH-20260506-0005`, `voucher_redeemed:VCH-20260506-0006:RDM-20260506-0005`, `BUDGET_RESERVE_ON_SUBMIT_PRG_001|<app_id>|RESERVATION` |

The idempotency key is what the BudgetEngine uses to short-circuit duplicate dispatches (re-submitting the same voucher's COMMITMENT just returns the existing event). The correlation_id is what the operator dashboards filter on to show "all events for voucher X". Different audiences, different semantics, both useful, both stored.

**Note on the redemption-key shape (Slice 11, partial redemption).** Before partial redemption shipped, the redeem-event idempotency key was `voucher_redeemed:{voucher_code}` — fine while every voucher had at most one redemption. Slice 11 made multi-call redemptions a first-class flow, which means the same voucher can produce 2+ EXPENSE events. The key was extended to `voucher_redeemed:{voucher_code}:{redemption_code}` so each redemption gets its own idempotency record. Old single-redemption rows (pre-Slice 11) still carry the legacy two-segment shape and live alongside; new redemptions always use the three-segment shape. Tests / forensics that look up an EXPENSE event by idempotency key should either use the three-segment shape (preferred — exact match against the new shape) or fall back to a `LIKE 'voucher_redeemed:{vch}%'` prefix match for tolerance against historical data.

Verified May 2026 (IM e2e test C): the test queried `c_correlation_id = 'voucher_issued:VCH-...'` looking for the COMMITMENT event and got nothing. The right query is `c_idempotency_key = 'voucher_issued:VCH-...'`. Anyone debugging budget events in the future, remember the prefix lives in `c_idempotency_key`.

### NEVER seed customer-owned registry tables

Some tables in this app hold **customer-authored data**: `app_fd_farmerbasicinfo`, `app_fd_farmerresidency`, `app_fd_farmeragriculture`, `app_fd_farmer_household`, `app_fd_farmer_declaration`, `app_fd_parcelregistration`, `app_fd_parcellocation`, `app_fd_parcelgeometry`, `app_fd_parcelclassification`, `app_fd_farms_registry`, `app_fd_household_members`, `app_fd_livestock_details`, plus any future table whose semantic is "this is a citizen / parcel / asset record entered by MOA staff or a farmer themselves". These tables are **read-only from a tooling perspective**. Test scripts, e2e harnesses, fixture seeders — none of them may insert, update, or delete rows in these tables.

**Why.** The customer's farmer registry has real Lesotho farmers in it. Polluting it with `TEST-XXXXXXXX` rows looks harmless to the test harness but corrupts the customer's data of record. Recovery is annoying — you have to find every test row by NID prefix and delete them via the Joget API (HARD-RULE compliant; never raw SQL). Worse, if a test runs against a row that already exists for a real farmer (NID collision), the test could silently mutate that farmer's record.

**The right pattern.** When an e2e test needs an applicant who passes `DET_FARMER_REGISTERED`, it picks a **pre-existing customer NID** with a known registry row (e.g. Profile A's `066257236627` — Mants'ali Panyane) and reads it. The test pre-cleans only the *transactional* tables it owns: `app_fd_subsidy_app_2025`, `app_fd_im_voucher`, `app_fd_im_voucher_redemption`, `app_fd_im_distribution`. These hold what the test produces — application rows, vouchers, redemptions, receipts — and the test is allowed to delete its own outputs through the Joget API. Registry tables are off-limits.

**Operational rule.** If you find yourself writing `formcreator/seed` payload with `formId: "farmerBasicInfo"` (or any of the registry forms above) outside of a one-time customer-data import, stop. The test should be reading existing data, not authoring it. If no suitable existing row exists, the right move is to ask the customer for a designated test NID they've earmarked for fixture use, not to invent one.

This rule was learned the hard way in May 2026: the IM e2e test (Slice C) initially seeded fresh `TEST-{uuid8}` rows into `farmerbasicinfo` to satisfy the eligibility chain. Two such rows landed in the customer's registry before the user caught it. They were deleted via Joget API DELETE through the `01.01 - Farmer Basic Information API` endpoint. The test was rewritten to use `066257236627` and pre-clean only its own application rows. The registry has been clean ever since.

### Cross-bundle reflection: pin the target's classloader explicitly

When code in bundle A calls into bundle B by reflection (`Class.forName(...)` →
instantiate → reflective method invoke), every `Class<?>` lookup must go
through B's classloader, not A's. OSGi gives each bundle its own classloader,
and `Class<?>` identity is per-classloader. Two classloaders can both load
"global.govstack.regbb.engine.api.EvalContext" and produce two distinct
`Class<?>` objects that compare unequal. `getMethod(name, paramTypes...)`
matches parameter types by identity, so passing the wrong-classloader
`Class<?>` makes it raise `NoSuchMethodException` even though the method
exists with the matching textual signature.

Concrete example from the §8 eval endpoint: form-creator-api looks up
`RoutingEvaluator.evaluate(String, EvalContext)`. Wrong way:
`getMethod("evaluate", String.class, Class.forName("...EvalContext"))` —
the `Class.forName` resolves through the caller's classloader, returning
form-creator-api's view of EvalContext. Right way:

```java
Object evaluator = Class.forName("...RoutingEvaluator").getDeclaredConstructor().newInstance();
ClassLoader engineCl = evaluator.getClass().getClassLoader();
Class<?> ctxCls = engineCl.loadClass("...EvalContext");
Method m = evaluator.getClass().getMethod("evaluate", String.class, ctxCls);
```

Pin the engine's classloader the moment you have a handle to one of its
instances; route every subsequent class lookup through it. Same applies
when constructing arguments — the EvalContext you pass to `m.invoke(...)`
must be built from the engine-classloader version of the class, not the
caller's.

Also remember: bundle B must **export** the package in question
(`Export-Package` in the manifest). `DynamicImport-Package: *` in bundle
A is necessary but not sufficient — A can dynamically import only what B
explicitly exports. Bundle-private packages remain unreachable regardless
of dynamic imports.

### Userview RBAC — three Joget specifics that cost a day to discover (May 2026, Week 1)

Three independent footguns in Joget DX 8.1 Community Edition's permission
model. All three were hit while wiring role-based menu visibility for the
Farmers Portal (`role_field_officer`, `role_district_supervisor`,
`role_finance_officer`, `role_analyst`, `role_sysadmin`). Source-verified
against `jw-community/wflow-core/src/main/java/org/joget/apps/userview/`.

* **`/web/json/**` requires ROLE_ADMIN or ROLE_APPADMIN — period.** Joget's
  data API at `/jw/web/json/data/list/{appId}/{listId}` (and every other
  endpoint under `/web/json/**`) is gated by Spring Security's
  `<intercept-url pattern="/web/json/**" access="ROLE_ADMIN, ROLE_APPADMIN" />`
  rule (`wflow-consoleweb/.../applicationContext.xml` line 124). Non-admin
  users get HTTP 403 on every call. Userview HtmlPage menus that fetch
  dashboard data via `fetch('/jw/web/json/data/list/...')` therefore break
  for every operator, even when the per-menu/per-category permissions allow
  them to see the menu. **Fix: route the fetch via `/jw/api/...`.** The
  `/api/**` rule (line 77) allows `ROLE_USER` and `ROLE_ANONYMOUS`, gated
  by API Builder credentials (`api_id` + `api_key` headers or query params)
  instead of session role. The companion endpoint we ship for this is
  `GET /jw/api/formcreator/formcreator/data/list?appId=...&listId=...` (form-
  creator-api build-024+, source-mirrors `FormListDataJsonController`
  verbatim, returns same `{total, data:[]}` shape after envelope unwrap).

* **`GroupPermission` reads `allowedGroupIds`, semicolon-separated.** The
  plugin's `isAuthorize()` (`userview/lib/GroupPermission.java` line 39)
  calls `getPropertyString("allowedGroupIds")` and splits on `;`. The JSON
  property name is `allowedGroupIds` and the separator is `;`. Earlier
  scripts authored `groupId` comma-separated — the plugin tokenises empty
  string, gets zero groups, and `isAuthorize()` returns false. Symptom:
  permission appears configured but silently authorises no-one (or, with
  admin's super-user bypass, only admin works). Always use the right
  property name + separator.

* **Category-level `permission` is enforced at nav-build time; menu-level
  is NOT.** `UserviewService.createUserview()` evaluates the category's
  `permission` block at lines 305-318 via `UserviewUtil.getPermisionResult()`.
  But the menu-loop at lines 349-411 only inspects `permissionDeny` and
  `permissionHidden` flags inside the menu's `permission_rules` map — it
  never runs the menu's own `permission` block. The menu-level GroupPermission
  is enforced at *click time* (page access control via the userview
  controller / theme renderer), not at navigation rendering. **Consequence:
  to hide menus from the left nav, put GroupPermission on the CATEGORY.**
  Per-menu GroupPermission only blocks the page when a user navigates to it
  by direct URL. To get true per-menu nav-hide you'd need the
  `permission_rules` infrastructure — userview-level rules with a
  `permission_key` referenced by each menu's `permission_rules.<key>.permissionHidden`
  block — which is much more invasive and was deferred.

* **Bonus: `ROLE_ADMIN_GROUP` app property escalates to ROLE_ADMIN, not
  just ROLE_APPADMIN.** When App Composer's "Select Groups for Admin" lists
  groups, `EnhancedWorkflowUserManager.getCurrentRoles()` line 134 adds
  `ROLE_ADMIN` (the global admin role) to those users while they navigate
  the app's userview. That bypasses every per-menu and per-category
  GroupPermission check. So using this property as a shortcut for granting
  data-API access defeats the per-menu RBAC. We tried this; it surfaced as
  cat seeing Budget + MM-Config + Admin + Master Data despite the
  permissions being correctly authored. **Don't use ROLE_ADMIN_GROUP for
  fine-grained access; refactor to API Builder routes (gated under
  `/api/**`) instead.**

* **API Builder wraps responses.** `ApiResponse` returns are wrapped by
  the API Builder runtime in `{"date":"...","code":"200","message":"<original
  body as string>"}`. JS callers that expect the bare body (e.g. dashboards
  doing `r.json().then(d => d.total)`) need to unwrap: `r.json().then(env =>
  env && typeof env.message === 'string' ? JSON.parse(env.message) : env)`.

The end-state pattern in this app, after Week 1 RBAC closure:
1. Category-level `GroupPermission` (with `allowedGroupIds` + `;`) on every
   userview category — controls left-nav visibility.
2. Menu-level `GroupPermission` (same shape) on every menu — click-time
   enforcement for direct-URL navigation.
3. `tooling/apply_rbac_v2.py` is the source of truth for both.
4. Dashboards fetch via `/jw/api/formcreator/formcreator/data/list` with
   API Builder credentials, with the envelope-unwrap chain on every call.
5. `tooling/rewrite_dashboard_urls.py` is idempotent and converts any
   legacy `/jw/web/json/data/list/...` URL it finds in HtmlPage bodies.

### Email throttling — Mailtrap free is unusable for our send pattern

The subsidy lifecycle fires three emails per approved application in a
tight cluster (decision + 2 vouchers, total wall-clock ~5 seconds). May
2026 we tried Mailtrap free-tier sandbox as the dev SMTP. Mailtrap free
returns `550 5.7.0 Too many emails per second. Please upgrade your plan`
on the second and third sends of any cluster — regardless of any client-
side throttle (verified up to a 10-second client-side gap; Mailtrap still
rejects). The cap appears to be a burst-detection algorithm, not a simple
N-per-second window. The only email that consistently arrives is the
first one in any short cluster.

Don't use Mailtrap free for dev SMTP if your app fires bursts. Two
working alternatives:

* **Gmail with App Password** — generate at
  `myaccount.google.com/apppasswords` (requires 2-Step Verification on
  the account). Configure Joget SMTP with host `smtp.gmail.com` port 587
  TLS, username = the Gmail address, password = the 16-char app password,
  from = the Gmail address. Gmail accepts 10+ emails/sec from a single
  client. Real delivery to a real inbox simulates production behaviour
  better than any sandbox.
* **AWS SES sandbox** — 14 emails/sec, $0/month, requires AWS account.
  Verified-sender list for sandbox mode means you can only send TO
  verified addresses — fine for dev where the override goes to one
  address.

The `EmailDispatcher` in `plugins/reg-bb-engine` ships a configurable
inter-send throttle: JVM property `-Dregbb.email.gap.ms=N` (default
10000 ms while we were on Mailtrap; drop to 100 ms or 0 once SMTP is
Gmail/SES/MAFSN-prod).

The "Mailtrap is the wrong tool for this app" finding is documented in
`docs/operations/smtp_production_config.md` §6a. Production
SMTP via MAFSN-managed Postfix / O365 is not expected to throttle.

### Hard-won Joget gotchas that don't show up until production

Documenting these so the next debug session doesn't burn the same hours:

* **Postgres unquoted column folding.** Joget creates `c_<fieldId>` columns. On Postgres, unquoted identifiers fold to lowercase, so `mm_required_doc.registrationId` (camelCase form-field id) becomes `c_registrationid` on disk. When Joget loads a row via Hibernate, the resulting `FormRow` keys may end up lowercased — `row.getProperty("registrationId")` returns null, `row.getProperty("registrationid")` works. **Always use a case-tolerant reader for camelCase property reads off `mm_*` rows** (see `MetaScreenElement.prop(row, key)` for the pattern). Symptom: dynamic filters silently exclude rows that should match.
* **Hibernate mapping is bounded by the form definition.** Any keys in the storeBinder rowSet that aren't in the form's Hibernate mapping are silently dropped at the UPDATE statement. Joget builds the mapping from `findAllElementIds(form, ...)` which walks the JSON-derived element tree and calls `getDynamicFieldNames()` per element. **If your element synthesises children dynamically, override `getDynamicFieldNames()` to return all possible field IDs the children could write.** Symptom: rowSet has the right keys at save time, but the column doesn't get written and `datemodified` updates without the value persisting.
* **Schema reconciliation only runs at `actionType=0`.** App Composer Save / form-creator-api re-POST triggers `actionType=0` which compares declared columns to existing schema and ALTERs the table. After changing `getDynamicFieldNames` output, you must trigger a form-save before the new columns appear. Symptom: `getDynamicFieldNames` log entries fire, but `information_schema.columns` doesn't show the new columns.
* **Joget recursion to runtime-synthesised children isn't guaranteed.** `executeElementFormatData` walks `getChildren(formData)` recursively; if it doesn't reach a synthesised FileUpload, the file's `formatData` (with its `putTempFilePath` metadata) never runs. The recursion path can break in subtle ways — parent chain not set on cached children, `continueValidation` returning false somewhere, etc. **If you can't make Joget reach your synthesised children, override the parent container's `formatData` and contribute the values directly** — but you must replicate `FileUpload.formatData`'s temp-file dance (`FileManager.getFileByPath` → `file.getName()` for the column + `row.putTempFilePath(fieldId, paths)` for the file move). Symptom: column gets the temp-path string instead of the bare filename, OR the column is set but downloads 404 because the file is still in the temp staging dir.
* **`isReadonly` and similar guards check `"true"`, not `"Y"`/`"N"`.** Joget's enterprise plugins (and our wizard) use `Y`/`N` widely; the framework's `Element.isReadonly` checks `"true".equalsIgnoreCase(prop)`. If you propagate an enterprise-style `Y`/`N` to a property the framework reads, Joget will treat it as not-set. Convert `Y`/`N` → `"true"`/`"false"` at the boundary if you need framework-level recognition.
* **Joget DatePicker's own dialect is NOT Java SimpleDateFormat.** Already documented above (see DatePicker section) — `yy`→`yyyy`, `mm`→`MM`, `MM`→`MMMMM`. Caused the eight-digit-year bug.
* **GIS Polygon Capture's `captureMode` / `defaultMode` props use UPPERCASE enum values.** `gis-capture.js` (lines 1105-1127) does strict-equality checks: `=== 'BOTH'`, `=== 'DRAW'`, `=== 'WALK'`, `=== 'VIEW_ONLY'` (captureMode) and `=== 'AUTO'`, `=== 'DRAW'`, `=== 'WALK'` (defaultMode). Lowercase values fall through every branch, the drawing UI never attaches, the map looks fine but is read-only. Source-verified May 2026 (L1-5 build-060 → 061). Always pass these values uppercase.
* **`Class.forName` doesn't see across un-exported bundle packages.** Joget plugins live in OSGi bundles; each bundle's manifest `Export-Package` decides which packages other bundles can see directly. `wflow-core` exports basically everything (so `Class.forName("org.joget.apps.form.lib.HiddenField")` works from anywhere), but third-party bundles like `joget-gis-ui` (and most of our `plugins/*`) ship with `<Export-Package></Export-Package>` empty, meaning their classes are bundle-private. From another bundle, `Class.forName("global.govstack.gisui.element.GisPolygonCaptureElement")` throws `ClassNotFoundException` even though the class loads fine inside its own bundle. The cross-bundle-safe path is `PluginManager.getPlugin(className)` — it goes through the OSGi service registry which knows about every plugin every bundle's Activator registered. `MetaScreenElement.newElement` tries PluginManager first, falls back to `Class.forName` for stock wflow-core elements. Symptom: silent missing element on the form (no exception bubbles up because the synthesise loop logs and skips); fix: route the lookup via PluginManager.
* **`CustomHTML` requires a non-null `label` property.** When synthesising a CustomHTML element from Java, you must `props.put(FormUtil.PROPERTY_LABEL, "")` even if there's no visible label — otherwise the freemarker template `customHTML_en.ftl` blows up on `#if element.properties.label == ""` with `InvalidReferenceException`. The whole form render aborts. Symptom: the form was working, you add a CustomHTML for some reason (e.g., L2-3 bot_pull script injection), the wizard tab fails to render and the freemarker stack trace dominates joget.log. Always set `label` to at least an empty string when synthesising CustomHTML.
* **Avoid `pg` (and other `pg_*`-shaped) table aliases on Postgres.** The SQL standard doesn't reserve `pg` as an alias, and a standalone `psql` / psycopg2 connection runs `... FROM app_fd_parcelregistration pr ...` fine — but the same query through Joget's JDBC pool / pgjdbc driver throws an opaque `PSQLException`. Verified May 2026 against build-055/056 of `ParcelsSummaryAdapter`: renaming `pg`/`pr`/`fbi` → `geo`/`reg`/`fbi` flipped the rule from `ERROR` to clean `FALSE` with no other change. Postgres uses the `pg_*` prefix throughout its own system catalog (`pg_class`, `pg_attribute`, etc.); the JDBC parser's behaviour around it is not worth debugging — just use longer, descriptive aliases. Symptom: SQL works in `psql`, fails through Joget with bare `PSQLException`.
* **DSL string literals don't support SQL-style {@code ''} escape sequences.** The fast-path tokeniser in `FastPathEvaluator.tokenise` greedily reads characters until it sees the matching quote char — there is no escape handling. So `'Mants''ali'` (SQL escape for `Mants'ali`) tokenises to two atoms: `'Mants'` and `'ali'`, with the leftover apostrophe leading to `parse_error:unsupported operator: 'ali'`. Verified May 2026 (L4-1 D-002): Mants'ali Panyane's `$registry.farmer.first_name != ""` evaluation broke on substitution. The DSL accepts both `'` and `"` as quote chars, so when emitting a string literal that contains an apostrophe, prefer `"..."` instead of trying to escape with `''`. Fix lives in `SqlPathEvaluator.substituteRefs`. Same trap will apply to any future code that synthesises DSL literals from arbitrary string values — names, village names, anything user-typed. If both quote chars appear in the value, neither emission is parseable; that's an ADR-territory discussion (DSL escape support) rather than a quick patch.
* **`getLoadBinderDataProperty(form, key)` can return arbitrary rows when the load binder has no PK to filter on.** In `?_mode=add`, FormData has no real `primaryKeyValue` (or has a freshly-generated UUID for which no row exists yet), and some load binder configurations return rows from the table anyway — typically the first row by PK or insertion order. Consequence: a fresh wizard render that consults the load binder for catalogue evaluation, default values, etc. silently leaks another applicant's saved data into the new application's evaluation. Symptom in L4-1 D-001 (May 2026): brand-new subsidy application catalogue showed PRG_001 "Applicable" because Lerato's seeded row (`agro_zone='lowlands'`) was being read by `getLoadBinderDataProperty`. Source-verified gating: `formData.getPrimaryKeyValue()` returns null/empty in `?_mode=add` until the first save, non-empty in `?_mode=edit` from the start. Defensive pattern (see `MetaScreenElement.renderCatalogue`): only consult the load binder when `getPrimaryKeyValue()` is non-empty AND there's reason to believe a saved record exists. Otherwise read from request params only. Ignoring this turns add-mode renders into "view first row in DB" surfaces.
* **`querySelector('[name=X]')` returns the FIRST radio in a group, not "the radio whose value is X".** When auto-filling form values from JS (L2-3 bot_pull, conditional UI toggles, any cross-field automation), `document.querySelector('[name="gender"]')` finds only the first `<input type="radio" name="gender">` in DOM order. Setting `.value="female"` on that single node mutates its `value` attribute (changing what *would* be submitted if it were checked) but leaves all radios unchecked — the visible UI doesn't flip. Same trap for checkbox groups. The correct pattern is `querySelectorAll('[name=X]')` → iterate → for each radio/checkbox, `n.checked = (n.value === target)`. Symptom: text and date fields auto-fill correctly, radio/checkbox groups stay empty; no JS error. Verified May 2026 (L2-3 build after FarmerByNidAdapter refactor). The fix lives in `MetaScreenElement.attachBotPullScript`'s `applyValue` helper — copy that pattern for any future client-side auto-fill code.
* **psycopg2: `%` characters in raw SQL with `params=()` are interpreted as placeholders.** When writing a Python test/tooling script that passes empty params, any literal `%` in the SQL (e.g. a `LIKE 'ENV_%.ALLOCATED'` pattern) makes psycopg2 raise `IndexError: tuple index out of range` on `cur.execute(sql, params)` — not a SQL error, a Python-level error from the placeholder substitution machinery. Two fixes: either (a) escape `%` as `%%` in the literal (`LIKE 'ENV\_%%.ALLOCATED'`), or (b) move the pattern into `params` and use `%s` properly (`LIKE %s`, `('ENV\_%.ALLOCATED',)`). Verified May 2026 in `tooling/test_budget_suite.py::test_gl_balances_to_zero` — wasted ~20 minutes diagnosing because the IndexError hides the actual cause. The trap doesn't fire when params are non-empty; it's specifically the empty-params + literal-`%` combination that breaks.
* **Seed-payload key casing must match the form-definition field id, not the lowercase Postgres column name.** Joget's Hibernate mapping is built from the form definition's `id` attribute, not the underlying column. Postgres folds unquoted column names to lowercase, so the form definition for `md45DistribPoint` declares `id="districtCode"` (camelCase) and the column ends up as `c_districtcode` (lowercase). When seeding via `/formcreator/seed`, the row payload MUST use `districtCode` — using `districtcode` to match the column name produces a row with NULL in that field; Hibernate silently drops keys that don't match the mapping. Two patterns appear in this app's legacy MD forms: snake_case (e.g. `md27input` uses `input_category`, `default_unit`, `estimated_cost_per_unit`) and camelCase (e.g. `md45DistribPoint` uses `districtCode`, `pointType`, `isActive`, `gpsLatitude`). The convention is per-form; always inspect the form-definition element ids (e.g. `SELECT json FROM app_form WHERE formid='X'` then grep for the `"id"` keys inside `properties` blocks) before authoring a seed payload — never infer keys from `\d app_fd_<table>` output. Verified May 2026 (IM Phase 3 Slice 1 cleanup): a seed of `md45DistribPoint` with lowercase keys silently landed only `code` + `name`; the re-seed with camelCase keys filled the remaining columns correctly via UPDATE.
* **MultiPagedForm with `partiallyStore=true` does NOT propagate the wizard's primary key to tab subform render lifecycles for new records.** Per-tab Next clicks on a new (mode=add) wizard call `AppService.storeFormData` (not `submitForm`) — the wizard parent record (`parcelRegistration`) isn't created until the citizen clicks the wizard's final Save. Until then, every tab subform renders with `formData.getPrimaryKeyValue()` empty, `AbstractSubForm.populateSubFormWithParentKey` (line 297) early-returns, and `parent_id` stays empty on the saved subform rows. Any server-side cross-subform data-flow approach that depends on `parent_id` (post-processor → sibling write, loadBinder → sibling lookup, custom storeBinder → cascade) will silently no-op on first visit. Symptom: DB writes look correct but the next render can't find them; sibling tab shows default state. Workaround: use a custom Element plugin that emits client-side JavaScript reading sibling field values from the DOM (Joget renders all tabs as hidden divs in one document, so DOM-read is reliable). Pattern documented in `AutoCenterBootstrapElement` (parcel-zone-centring plugin) and decision log D49. Whole arc burned 11 plugin builds across two days before landing on the right answer; if you find yourself fighting this, jump straight to the client-side Element approach.
* **CustomHTML strips `<script>` from form rendering; custom Element plugins do not.** When an inline JS block needs to run on form load, you cannot put it inside a CustomHTML element — Joget's renderer strips `<script>` tags from CustomHTML output (verified empirically with the failed first attempt at parcel-zone-centring). The fix is to build a one-class custom Element plugin that emits its own `<script>` from `renderTemplate()`. Reference shapes: `QualityBannerElement` (`form-quality-runtime`) for a simple single-script element, `GisPolygonCaptureElement` (`joget-gis-ui`) for an element that runs substantial client-side code, `AutoCenterBootstrapElement` (`parcel-zone-centring`) for a synchronous-DOM-read-and-write element with embedded JSON lookup data. Custom Element plugins also get a form-builder palette entry for free (implement `FormBuilderPaletteElement`), so analysts can drag-drop them onto forms.
* **Brand-new forms hit a table-before-mapping race in form-creator-api.** When form-creator-api creates a form via `FormDefinitionDao.add()`, the underlying `app_fd_<formId>` Postgres table doesn't yet exist. The DAO call populates cache 2 (per-form Hibernate ORM mapping) referencing a table Hibernate now thinks has no columns. A `forceTableCreation()` call moments later creates the table, but cache 2 is already locked in the wrong state for the lifetime of the JVM. **Symptom:** datalist binders (AdvancedFormRowDataListBinder) over the new form return zero rows even though the row IS in the table (verifiable via raw `SELECT`). The `joget.log` log line `ERROR ... relation "app_fd_<form>" does not exist` at the moment of form creation is the diagnostic fingerprint. **Documented recovery** (CLAUDE.md HARD-RULE recovery section) — open the form in App Composer → hard-refresh → Save — is meant to re-fire `AppService.saveForm()` and rebuild cache 2 atomically. In practice (W2.5 step 4b, May 2026) this recovery did NOT clear it; the broken mapping persisted across several App Composer saves AND multiple form-creator-api re-pushes. The reliable workaround is a JVM restart (clears all caches). **For NEW configuration-shaped data needs, prefer JVM system properties over a singleton form** — see `docs/implementation/notification_test_mode_override.md` for the pattern (`regbb.notif.testMode`, `regbb.notif.testEmail`, etc.). The form-based approach is only worth it for collections of rows where the operator actively manages multiple entries; for one-shot toggles, use system properties read by a static method (`NotificationConfig.get()` is the reference shape). This avoids the entire cache trap.

When in doubt, grep `jw-community/` for the exact method name and read the actual implementation. It's almost always faster than a debugger session.

### Sandbox-built reg-bb-engine JAR must inline joget-status-framework classes

`joget-status-framework` is a plain (non-OSGi) JAR — its `META-INF/MANIFEST.MF` has no `Bundle-SymbolicName` / `Export-Package`. It cannot be installed in Joget's OSGi container as its own bundle. The Maven build of reg-bb-engine handles this via `<Embed-Dependency>` in `pom.xml` (maven-bundle-plugin extracts the status-framework JAR and writes its classes into the reg-bb-engine bundle JAR's root). The sandbox `repack.sh` build doesn't run Maven, so this step has to be replicated manually: `unzip -o $M2/.../joget-status-framework-8.1-SNAPSHOT.jar -C $WORK/classes/` before the `jar cfm` line. Without it, the bundle compiles fine but the OSGi container fails to start it with `NoClassDefFoundError: global/govstack/statusframework/api/EntityType` — verified May 2026 against build-129. The fix is committed in `plugins/reg-bb-engine/deploy/repack.sh`; if any other gs-plugin starts depending on joget-status-framework, replicate the same inline step in that plugin's repack.sh.

Diagnostic: `unzip -l <bundle.jar> | grep statusframework` — should list 5 `.class` files under `global/govstack/statusframework/{api,core}/`. If empty, the bundle is broken and will not start.

### Datalist `actions[]` entries need `id` at TOP level, not inside properties

Joget's `JsonUtil.parseActionsFromJsonObject` (`wflow-core/.../JsonUtil.java` line 470) reads each action's `id` via `getString("id")` on the bare action object — not on its `properties` map. The canonical shape (verified against `list_app_resolver_config`) is:

```json
{"id": "create", "name": "create", "label": "+ New resolver",
 "className": "org.joget.apps.datalist.lib.HyperlinkDataListAction",
 "properties": {"hyperlink": "?action=create"}}
```

Putting `id` only inside `properties` (`{"properties": {"id": "..."}}`) makes the datalist push succeed (the JSON validator doesn't catch it) but the next time `CrudMenu.getDataList` parses the JSON it throws `JSONException: JSONObject["id"] not found` and the menu page errors out. The data and column rendering still work, but actions disappear and the list errors at load. Verified May 2026 (W2.6 Notification Queue datalist).

Same convention applies to `rowActions[]` and `filters[]` — `id` and `name` at the top level of each entry, behaviour-specific config inside `properties`.

### mm_field options come from `optionsCatalogId` only — `optionsBinderJson` is ignored by MetaScreenElement

`MetaScreenElement.addCatalogOptions` (`plugins/reg-bb-engine/.../element/MetaScreenElement.java` ≈ line 1714) reads the option list from `mm_field.optionsCatalogId` (a business-code reference to an `mm_catalog` row). It does NOT inspect `mm_field.optionsBinderJson` — that column exists in the table but isn't wired into the synthesise path for `select` / `radio` / `checkbox` widgets. Authoring a field with `optionsBinderJson={"className":"...StaticBinder","properties":{"options":[...]}}` produces a Joget element with zero options: the widget renders but has no tickable / selectable items, so it appears blank to the citizen.

For inline options, the canonical path is: author an `mm_catalog` row with the items in its `itemsJson` column, then point `mm_field.optionsCatalogId` at that catalog's business code. Even a single-item catalog (e.g. `SUBMIT_CONFIRMATION` with one option `Y / "I confirm submission..."`) is the right shape.

Verified May 2026 (W3.4 submit_confirmation checkbox — initially invisible because options were in `optionsBinderJson`; fixed by creating `mm_catalog SUBMIT_CONFIRMATION` and setting `optionsCatalogId=SUBMIT_CONFIRMATION` on the field).

### `dateCreated` / `dateModified` are camelCase, not snake_case, in datalist column refs

Joget's system timestamps surface in `FormRowDataListBinder` / `AdvancedFormRowDataListBinder` under camelCase keys `dateCreated` and `dateModified` (`FormUtil.PROPERTY_DATE_CREATED` = `"dateCreated"`, line 118 of `wflow-core/.../FormUtil.java`). The underlying Postgres column folds to lowercase (`datecreated`) but the binder explicitly remaps it. A datalist column with `"name": "datecreated"` renders blank — there's no error, just empty cells — because the binder doesn't put that key in the row map.

Also: Joget pre-formats the value to `yyyy-MM-dd HH:mm:ss` before handing it to a column formatter. So `DateFormatter.rawFormat` must be `yyyy-MM-dd HH:mm:ss` (Java SimpleDateFormat dialect — not Joget's DatePicker dialect, which is different per the separate gotcha). The `orderBy` field on the datalist accepts the same key — use `"orderBy": "dateCreated"` to sort newest-first.

Verified May 2026 (W2.6 Notification Queue datalist — "When" column rendered empty until `name` was flipped from `datecreated` to `dateCreated`).

### Surface SQL errors, don't swallow them

When you wrap a JDBC call in a try/catch, the bare exception class name is almost never enough to diagnose a failure. Postgres' `SQLException.getMessage()` carries the actual problem (`column "..." does not exist`, `relation "..." does not exist`, `permission denied for table ...`); `getSQLState()` carries the standardised five-character code (`42P01` = undefined table, `42703` = undefined column, `42501` = insufficient privilege, etc.). Both are cheap to surface and pay for themselves the first time something breaks in production. The L2-1 build-055 bug took an extra round trip to diagnose because `SqlPathEvaluator` only logged the exception class — once the cause was added to the audit row, the next iteration fixed it in one redeploy. Default policy: any `catch (SQLException e)` that produces a user-visible error message should include `e.getSQLState() + ":" + e.getMessage()` (truncated if needed) in the cause string, not just `e.getClass().getSimpleName()`.

### Architectural decisions name two principles, not one

When an ADR or design decision invokes a principle to justify a choice, it should also name the principle that pulled the other way and explain why the latter lost. A decision citing one principle with no contrary principle named is suspect — it's selective application masquerading as principled reasoning.

Concrete pairs that conflict regularly in this project:

* **SRP vs. YAGNI / KISS** — SRP says split things by responsibility; YAGNI/KISS say combine until complexity is earned. Both are valid. Lesotho-MAFSN scale (one customer, one team, one cadence) generally favours YAGNI; multi-tenant federated scale would flip it. The architect's job is to name which side of the conflict the present scale lands on.
* **DIP vs. Convention over Invention** — DIP says depend on abstractions, often suggesting OSGi service-lookup ceremony. Convention over Invention says don't deviate from established platform patterns (Joget's plain-JAR contract sharing) without scale-proportionate justification.
* **OCP vs. Convention over Invention** — OCP says open for extension; Convention says use the platform's extension mechanism rather than inventing a new one.
* **Spec P5 (loud failure) vs. spec P1 (configuration over code)** — P5 says surface errors loudly; P1 says give operators control. They sometimes conflict (an operator's bad rule produces a loud failure that interrupts citizen-facing UI). The decision names which side wins.

ADR-002 revision 1 (now superseded) was a worked example of single-principle reasoning gone wrong: SRP was cited in isolation, YAGNI/KISS/Convention weren't named, and the decision over-applied SRP at single-team scale. Revision 2 corrected it.

The discipline: when reading or writing an ADR, **find the principle that pulled the other way and name it explicitly**, even if it lost. If you can't find a counter-principle, the decision probably has only one face — which means you've probably missed something.
