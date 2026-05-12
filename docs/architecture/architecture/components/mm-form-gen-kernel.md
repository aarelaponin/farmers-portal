# Component SAD — MM-form-gen kernel

| Field             | Value                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Component name    | MM-form-gen kernel                                                                                                                                                                                                                                                                                                                                                                                                          |
| Document title    | Software Architecture Description (component level)                                                                                                                                                                                                                                                                                                                                                                          |
| Version           | 1.0 — DRAFT                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Date              | 2026-05-02                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Author(s)         | Aare Laponin with engineering team                                                                                                                                                                                                                                                                                                                                                                                           |
| Related           | `docs/architecture/architecture/solution-architecture.md` (the parent SAD); ADRs 002, 018, 019, 020, 024 in `docs/architecture/adr/` (some forthcoming); `docs/architecture/determinant-architecture.md`; CLAUDE.md (operational rules)                                                                                                                                                                                                                  |
| What this is      | The component-level SAD for the metadata-driven form-rendering kernel. The kernel takes `mm_screen` + `mm_field` + `mm_catalog` rows and produces a working Joget form at runtime.                                                                                                                                                                                                                                          |
| What this is not  | A description of the RegBB framework that sits on top (separate component SAD); a description of any specific module's content (subsidy, IM); a how-to for authoring `mm_*` rows (`mm-authoring-guide.md`).                                                                                                                                                                                                                  |

---

## 1. Introduction and goals

### 1.1 Purpose and scope

The MM-form-gen kernel is the lowest-layer building block in the Lesotho Farmers Portal architecture. Its single responsibility is **metadata-driven form rendering**: given a row in `mm_screen` and a set of child rows in `mm_field` (with optional reference to `mm_catalog` for option lists), produce a fully working Joget form at runtime — visible to the citizen or operator, persisting data to the underlying `app_fd_*` table, indistinguishable from a hand-built Joget form.

The kernel is **domain-agnostic**. It has no opinion about *why* a form exists, *who* fills it in, or *what* business rules apply on save. Those concerns belong to the layer above — RegBB framework for citizen services, IM module for inputs management, or any future module that opts in to metadata-driven UI.

In scope:

- Translation of `mm_field` rows into Joget Element instances (`TextField`, `SelectBox`, `Radio`, `CheckBox`, `DatePicker`, `TextArea`, `FileUpload`, `FormGrid`, and pass-through of the installed enterprise widgets — `GisPolygonCaptureElement`, `SmartSearchElement`, `Signature`, etc.).
- Composition of multiple screens into a wizard via `MetaWizardElement`.
- Read of `mm_catalog` for option lists in select / radio / checkbox widgets.
- Conditional UI hooks (`mm_field.visibilityDeterminantId`, `mm_field.requirednessDeterminantId`) — note that the *evaluation* of the determinant is delegated to the layer above; the kernel only consumes the verdict.
- The `mm_screen` / `mm_field` / `mm_catalog` form definitions themselves (delivered as JSON files, deployed via `form-creator-api`).
- The DAO that reads these rows from `app_fd_mm_*` tables.

Out of scope (handled elsewhere):

- Eligibility evaluation, audit, role-scoped review, single-window catalogue, operator decision lifecycle (RegBB framework, separate SAD).
- Authoring UX — the admin CRUDs for `mm_*` rows (Phase 1 ships 13 separate CrudMenus; Phase 2 introduces a Programme Builder composite UI; both consume the kernel but are not part of it).
- Persistence of the form data itself (Joget's standard storeBinder writes `app_fd_*`; the kernel synthesises the elements that participate in that lifecycle but does not re-implement persistence).

### 1.2 Quality goals (component-level)

| Rank | Quality goal                  | Concrete motivation and target                                                                                                                                                                                                                                                                                                                                                                                            |
| ---- | ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | **UX parity with hand-built Joget** | Anything Joget renders natively — cascading dropdowns, repeating-group grids, GIS polygon capture, smart-search typeahead, signature, file upload, inline validation, multi-column layout — the kernel renders identically by *passing through* the underlying Joget Element. Bespoke HTML emission is rejected (D18). Target: zero widget categories where the metadata-driven path is strictly worse than hand-built. |
| 2    | **Domain neutrality**         | The kernel does not import any package outside its own (`engine.element`, `engine.dao`) and Joget core. It does not reference `mm_service`, `mm_registration`, `mm_determinant`, `mm_action`, `app_fd_subsidy_*`, or `app_fd_im_*` directly. A future split into a separate OSGi bundle must be a packaging change, not a code change.                                                                                  |
| 3    | **Save lifecycle correctness**| The kernel synthesises Element instances, but the storeBinder pipeline must persist their values exactly as if the elements had been declared in form-builder JSON. This includes: Hibernate mapping discovers all dynamic columns (`getDynamicFieldNames` override); request params are read into the rowSet (`formatData` contribution); file uploads are moved from temp to permanent storage. Target: zero "value typed but lost" bugs. |
| 4    | **Performance — render path** | A wizard render of 6 screens × ~10 fields each must complete in < 300 ms server time, P95. Per-screen children are cached per FormData lifecycle (WeakHashMap) so Joget's many `findElement` / `buildElementMap` / render-tree-walk calls don't re-query the database.                                                                                                                                                  |
| 5    | **Cross-DBMS portability**    | The kernel runs on PostgreSQL today but must not embed PG-specific SQL. Reads use Joget's `FormDataDao` (HQL-like) rather than raw SQL. Postgres-specific behaviours (lowercase column folding) are absorbed by case-tolerant FormRow readers, not by adapting queries.                                                                                                                                                  |

### 1.3 Stakeholders

| Stakeholder                        | Concerns                                                                                                                                                                  |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **RegBB framework developers**     | Stable contract: extension points (custom widget kinds), call sites (synthesise hooks for visibility/required determinants), dependency direction (framework → kernel only). |
| **Module developers (IM, future)** | Same — they consume the kernel without touching it, and can add new widget types via the (forthcoming) widget registry pattern.                                            |
| **Citizen / operator end-users**   | Fields render correctly, submit correctly, validate correctly. They never know they're talking to a metadata-driven form.                                                  |
| **Joget platform**                 | The kernel does not rely on Joget internals that may change between minor versions; uses only documented `Element`, `FormContainer`, `FormElement`, `FormBuilderPaletteElement` extension points. |

---

## 2. Architecture constraints

Inherits all constraints from the solution-level SAD §2 (Joget DX 8.x, OSGi, PostgreSQL, hard rule on raw writes, JDK 11, form ID length cap, DatePicker dialect, no XPDL generation). Component-specific additions:

| #     | Constraint                                                                                                                                                                                                                                                                                                                                                                                  |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| K-C1  | **No HTML emission for data widgets.** D18 forbids returning bespoke HTML strings for fields that capture data. The kernel must synthesise real Joget Element instances. (HTML for static guide screens — `mm_screen.kind = 'guide'` — is permitted because no data is captured.)                                                                                                          |
| K-C2  | **No new metamodel tables added to the kernel.** The metamodel is closed at `mm_screen`, `mm_field`, `mm_catalog`. New entities (mm_action, mm_determinant, mm_required_doc, mm_benefit, etc.) belong to the framework or module layer, not the kernel.                                                                                                                                     |
| K-C3  | **Element synthesis is per FormData lifecycle.** Children are cached in a `Collections.synchronizedMap(new WeakHashMap<FormData, Collection<Element>>())` so Joget's many same-render-pass calls don't re-query the DB. The cache is per-instance, never static.                                                                                                                            |
| K-C4  | **Hibernate mapping must enumerate all dynamic columns.** `MetaScreenElement.getDynamicFieldNames()` returns every storage key from every `mm_field` for the current screen, plus every `doc_<code>` for documents-kind screens. Without this override, Joget's `FormDataDaoImpl.findAllElementIds` walker silently drops dynamic columns from the table mapping (the "value disappears on save" bug). |

---

## 3. Context and scope

### 3.1 Component context

The kernel sits between the platform (Joget) and the layer above (RegBB framework or any module). The diagram below shows what the kernel imports, what imports it, and what it touches in the database.

```
                         ┌────────────────────────────────┐
                         │  RegBB framework               │
                         │  IM module                     │
                         │  (other modules — future)      │
                         └──────────────┬─────────────────┘
                                        │ uses
                                        │ (extension points only)
                                        ▼
   ┌──────────────────────────────────────────────────────────────┐
   │                MM-form-gen kernel                            │
   │                                                              │
   │  ┌────────────────────┐    ┌────────────────────────────┐    │
   │  │ MetaScreenElement  │    │ MetaWizardElement          │    │
   │  │ (FormContainer)    │◀───│ (compound: contains screens│    │
   │  └────────┬───────────┘    │  via getChildren(formData))│    │
   │           │                └─────────────┬──────────────┘    │
   │           │ reads                        │ resolves screens  │
   │           ▼                              ▼                   │
   │  ┌──────────────────────────────────────────────────────┐    │
   │  │ MetaModelDao                                         │    │
   │  │ findScreenByCode / listFieldsForScreen /             │    │
   │  │ findCatalogByCode / findServiceByCode / ...          │    │
   │  └─────────────────────────┬────────────────────────────┘    │
   └────────────────────────────┼─────────────────────────────────┘
                                │ Joget FormDataDao
                                ▼
   ┌─────────────────────────────────────────────────────────────┐
   │  PostgreSQL  app_fd_mm_screen   app_fd_mm_field             │
   │              app_fd_mm_catalog  app_fd_mm_service           │
   │              app_fd_mm_registration  app_fd_mm_role_screen  │
   │              app_fd_mm_required_doc  app_fd_mm_determinant  │
   └─────────────────────────────────────────────────────────────┘
```

### 3.2 Upstream consumers

| Consumer            | What it uses from the kernel                                                                                                                                                                                                                |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RegBB framework     | `MetaScreenElement` + `MetaWizardElement` to render citizen wizards and operator review surfaces. `MetaModelDao` to read `mm_service` / `mm_registration` (the framework's metamodel rows are read by the framework, not the kernel).         |
| Subsidy module      | Indirectly via the framework: subsidy is a configuration deliverable, not a code module.                                                                                                                                                       |
| IM module (Phase 3) | `MetaScreenElement` for input-catalog / supplier / inventory / allocation forms. May also consume `MetaWizardElement` for multi-step processes (e.g. voucher issuance review). Reuses `mm_screen` + `mm_field` + `mm_catalog` for IM screens; adds nothing to the kernel. |

### 3.3 Downstream dependencies

| Dependency                          | Used for                                                                                                                                       |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Joget `wflow-core`                  | `Element`, `FormContainer`, `FormBuilderPaletteElement`, `FormUtil`, `FormData`, `FormRow`, `FormRowSet`, `Form`, `Element.setProperties(Map)`. |
| Joget `wflow-commons`               | `LogUtil`.                                                                                                                                     |
| Joget enterprise plugins (installed)| `FormGrid` (`org.joget.plugin.enterprise.FormGrid`), `MultirowFormBinder`, `OptionsValueFormatter`, `DateFormatter`, etc. — referenced by class name when synthesising. |
| Joget marketplace / custom plugins  | `GisPolygonCaptureElement`, `SmartSearchElement`, `Signature`, `EmbeddedDatalist`, `CalculationField`, `MultiPagedForm` — pass-through synthesis when the configured widget is one of these. |
| `form-creator-api` (sibling bundle) | Not at runtime; only at deploy time — the API endpoint is how `mm_screen` / `mm_field` / `mm_catalog` form definitions get into Joget.         |

---

## 4. Solution strategy

The kernel's design is shaped by four principles, each backed by a recorded decision:

### 4.1 Synthesise real Joget Elements (D18)

Earlier prototypes emitted bespoke HTML for each `mm_field` row. That approach failed at the second problem encountered: Joget's downstream pipeline — `FormUtil.findElement`, `buildElementMap`, `FormRowDataListBinder.getColumns`, the App Composer column picker, validators, store binders — all expect a tree of `Element` instances. Bespoke HTML is invisible to all of it. Datalists couldn't show columns. Validators didn't fire. Store binders didn't persist values.

The current design (D18) makes `MetaScreenElement` a *transparent* `FormContainer`: at `getChildren(FormData)` time, it instantiates real Joget Elements (`new TextField()`, `new SelectBox()`, etc.), configures them via `setProperties(Map)`, sets their parent to `this`, and returns the collection. After this, every Joget pipeline sees a normal element tree and "just works." Datalist patching, separate column-discovery code paths, validator monkey-patching — all retired.

### 4.2 Composite screen authoring via FormGrid (D19)

`mm_field` is a 1:N child of `mm_screen` via a `FormGrid` in the `mm_screen` admin form. The `FormGrid` carries both a `loadBinder` and a `storeBinder` pointing at `MultirowFormBinder` with `formDefId="mm_field"` and `foreignKey="screenId"`. This makes `mm_screen` authoring composite — the operator edits the screen and adds/edits its fields in one screen — without a custom subform widget.

### 4.3 FK convention split (D20)

Joget-internal FKs (FormGrid row → parent, wizard tab → wizard) store the parent's auto-generated UUID. Cross-entity references (`mm_field.optionsCatalogId` → `mm_catalog`, `mm_screen.serviceId` → `mm_service`) store the target's business `code`. SmartSearch and SelectBox configurations source by `code`, not UUID. This makes the metamodel safe to dump-and-reload across environments — UUIDs differ per environment, codes don't.

### 4.4 Wizard as a first-class element (D24)

`MetaWizardElement` is a sibling to `MetaScreenElement`, not a derivation. It walks an ordered list of `mm_screen` codes (resolved either from `mm_role_screen.sectionsJson` for operator views, or from a comma-separated property for citizen views), creates one `MetaScreenElement` per screen as its child, emits the tab bar + tab panels in HTML, and lets each `MetaScreenElement` render normally inside its panel. The tab navigation is client-side (vanilla JS), not server-side — no per-tab page reload.

---

## 5. Building block view (component internals)

### 5.1 Whitebox — packages within the kernel

```
global.govstack.regbb.engine.element/      ← public synthesis API
   MetaScreenElement.java        (1095 LoC)
   MetaWizardElement.java         (747 LoC)

global.govstack.regbb.engine.dao/          ← read access to mm_* tables
   MetaModelDao.java              (235 LoC)
```

Two packages, three classes. The kernel is intentionally small.

The kernel **shares** the `engine` Java root with the RegBB framework today (single OSGi bundle `reg-bb-engine`). The split is logical, not physical. A future split into a separate bundle (forthcoming ADR) would extract `element/` + `dao/` plus the metamodel form JSON files for `mm_screen` / `mm_field` / `mm_catalog`, leaving the rest of the framework in `reg-bb-engine`.

### 5.2 MetaScreenElement — the synthesis engine

**Type.** `extends Element implements FormBuilderPaletteElement, FormContainer`.

**Public configuration properties** (set via Joget form-builder JSON or programmatically):

| Property         | Type   | Default      | Meaning                                                                                                                                               |
| ---------------- | ------ | ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `screenId`       | String | required     | The `mm_screen.code` to render. Looked up at every `getChildren(formData)` call (cached per FormData).                                                |
| `readonly`       | String | `"false"`    | When `"true"`, propagated to every synthesised child as the readonly flag. Used for the operator's read-only inspection of citizen-submitted tabs.    |
| `roleScreenId`   | String | `""`         | Optional — when set on a `MetaWizardElement`, the wizard reads `mm_role_screen.sectionsJson` instead of the static screen list.                       |

**Key methods (private internals worth knowing):**

| Method                                                            | Purpose                                                                                                                                                                                                       |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `getChildren(FormData)`                                           | Joget-required override. Returns the synthesised children for the current `FormData`, cached in a `WeakHashMap<FormData, Collection<Element>>`.                                                              |
| `synthesiseChildren(FormData)`                                    | The cache miss path. Looks up `mm_screen` by code; dispatches by `mm_screen.kind` (`form`, `guide`, `review`, `documents`); returns the list of synthesised Elements.                                          |
| `synthesiseField(FormRow, MetaModelDao, boolean visible, Boolean requiredOverride)` | Translates one `mm_field` row into a configured Joget Element. Handles widget dispatch via a switch on `mm_field.widget`. Returns `HiddenField` if `visible=false` (preserves the column round-trip).                                |
| `synthesiseDocumentChildren(...)`                                 | For `kind='documents'` screens: walks `mm_required_doc` filtered by `serviceId` AND (programme-specific docs only if applicant's `applied_programme` matches), synthesises one `FileUpload` per doc row.       |
| `getDynamicFieldNames()`                                          | Returns the storage-key set for the current screen so Joget's Hibernate mapping discovery sees every dynamic column. **Without this override every dynamic column is silently dropped at `UPDATE` time.**     |
| `formatData(FormData)`                                            | Contributes values directly to the storeBinder rowSet for synthesised `FileUpload` children — replicates `FileUpload.formatData`'s temp-file lifecycle (`FileManager.getFileByPath` → `file.getName()` for the column + `row.putTempFilePath(fieldId, paths)` for the file move). |

**Widget pass-through table** (`switch (widget)` inside `synthesiseField`):

| `mm_field.widget` value | Synthesised class                                          | Notes                                                                                                                                                              |
| ----------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `text`                  | `org.joget.apps.form.lib.TextField`                        |                                                                                                                                                                    |
| `number`                | `org.joget.apps.form.lib.TextField`                        | + numeric type validator (`double` if `dataType=number`, else `integer`).                                                                                          |
| `date`                  | `org.joget.apps.form.lib.DatePicker`                       | Format set to Joget dialect `yy-mm-dd` (NOT Java `yyyy-MM-dd` — see CLAUDE.md gotcha section).                                                                   |
| `textarea`              | `org.joget.apps.form.lib.TextArea`                         | (NOT `TextAreaField` — invented class; correct name has no `Field` suffix.)                                                                                       |
| `select`                | `org.joget.apps.form.lib.SelectBox`                        | Options sourced from `mm_field.optionsCatalogId` (a `mm_catalog.code`).                                                                                            |
| `radio`                 | `org.joget.apps.form.lib.Radio`                            | Same.                                                                                                                                                              |
| `checkbox`              | `org.joget.apps.form.lib.CheckBox`                         | Same.                                                                                                                                                              |
| *(default — unknown)*   | `org.joget.apps.form.lib.TextField` (read-only placeholder)| Label suffixed `[widget=<value> — pending implementation]`. **Currently the placeholder for `gis_polygon`, `signature`, `smart_search`, `repeating_group`, `cascading_select`, `file_upload`, `qr_scan` — these widgets exist as installed Joget plugins but kernel pass-through hasn't been wired yet. (Phase 1 close-out backlog item #1–#3.)** |

### 5.3 MetaWizardElement — composition into a wizard

**Type.** `extends Element implements FormBuilderPaletteElement, FormContainer`.

**Public configuration properties:**

| Property                    | Type   | Default | Meaning                                                                                                                                                                                |
| --------------------------- | ------ | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `serviceId`                 | String | `""`    | When set, the wizard renders all `mm_screen` rows for this service in `orderIndex` order.                                                                                              |
| `roleScreenId`              | String | `""`    | When set, the wizard reads `mm_role_screen.sectionsJson` for the screen list + per-tab readonly mask (operator mode).                                                                  |
| `screenIds`                 | String | `""`    | Comma-separated explicit list of `mm_screen.code` values. Override path; when set, overrides `serviceId` resolution.                                                                   |
| `readonly`                  | String | `"false"` | When `"true"`, every synthesised `MetaScreenElement` child is rendered with `readonly=Y`.                                                                                              |

**Render output structure:**

```
<!-- regbb-meta-wizard service=... screens=N engine=build-XXX -->
<style>...sticky CSS...</style>
<div class="regbb-wizard">

  <!-- Identity header (build-047+) -->
  <div class="regbb-id-header">
    <span class="regbb-id-name">Tšepiso Khabo</span>
    <span><span class="regbb-id-label">NID</span> <span class="regbb-id-value">1995022700567</span></span>
    <span class="regbb-id-prog"><span class="regbb-id-label">Programme</span> <code>PRG_2025_001</code></span>
  </div>

  <!-- Tab bar -->
  <ol class="regbb-wizard-tabs">
    <li class="regbb-wizard-tab-link active" data-step="0">...</li>
    ...
  </ol>

  <!-- Tab panels — one per screen -->
  <div class="regbb-wizard-panels">
    <div class="regbb-wizard-panel" data-step="0">
      <!-- MetaScreenElement.render output for screen 0 -->
    </div>
    <div class="regbb-wizard-panel hidden" data-step="1">
      <!-- ... -->
    </div>
  </div>

  <!-- Footer nav -->
  <div class="regbb-wizard-nav">
    <button class="regbb-prev" disabled>« Previous</button>
    <span class="regbb-progress">Step 1 of 6</span>
    <button class="regbb-next">Next »</button>
  </div>

</div>

<script>(function(){...client-side tab switcher...})();</script>

<!-- §6.4 client-side conditional UI toggle (build-048+) -->
<script>(function(){
  var bindings = [{"target":"cooperative_name","dep":"block_farming_member","equals":"yes"}, ...];
  function applyAll() { /* hide/show form-cells based on dep value */ }
  root.addEventListener('change', applyAll, true);
  applyAll();
})();</script>
```

The wizard's tab navigation is purely client-side — clicking Next/Previous does not submit to the server. Server submit only happens on **Save**, at which point Joget's standard form lifecycle runs (formatData → storeBinder).

### 5.4 MetaModelDao — read access to mm_* tables

A thin facade over Joget's `FormDataDao`. Methods in the order they appear:

```java
public FormRow         findScreenById(String screenId)
public FormRow         findScreenByCode(String screenCode)
public List<FormRow>   listFieldsForScreen(String screenId)
public FormRow         findCatalogById(String catalogId)
public FormRow         findCatalogByCode(String catalogCode)
public List<FormRow>   listRequiredDocsForService(String serviceCode)   // [framework concern]
public FormRow         findServiceByCode(String serviceCode)            // [framework concern]
public FormRow         findRoleScreenByCode(String roleScreenCode)      // [framework concern]
public FormRow         findRegistrationByCode(String registrationCode)  // [framework concern]
public FormRow         findDeterminantByCode(String determinantCode)    // [framework concern]
public List<FormRow>   listDeterminantsForRegistration(String code, String scope)  // [framework concern]
```

Methods marked `[framework concern]` are reads that the framework needs but live in the same DAO for convenience. A future kernel/framework split would move them to a `RegBbMetaDao` in the framework layer — the kernel itself only needs the first five methods.

The DAO is **stateless and inexpensive to instantiate**: every consumer creates a fresh `new MetaModelDao()` per use. Joget's underlying `FormDataDao` is the bean cached in the application context; the DAO façade just dispatches.

### 5.5 The metamodel forms — `mm_screen`, `mm_field`, `mm_catalog`

The kernel ships three form definitions deployed via `form-creator-api`:

| Form         | Key columns                                                                                                                                                                                                                                                                                                                                                                       | Notes                                                                                                                                                          |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `mm_screen`  | `code` (UPPER_SNAKE_CASE, unique), `title`, `serviceId` (FK → `mm_service.code`), `kind` (`form` / `guide` / `review` / `documents`), `audience` (`citizen` / `operator` / `both`), `orderIndex`. Composite child grid for fields.                                                                                                                                              | `code` is the natural key the kernel looks up on every render. `audience` filter prevents OP_DECISION from leaking into citizen wizards.                       |
| `mm_field`   | `id` (Joget UUID — internal; no global business code per D20 since natural key is composite `(screenId, storageKey)`), `screenId` (FK by UUID, internal — D20), `storageKey` (the runtime field id), `label`, `widget` (text/number/date/select/radio/checkbox/textarea/...), `dataType`, `defaultBehaviorOnError`, `optionsCatalogId` (by `code`), `helpText`, `displayOnList`, `claimType`, `visibilityDeterminantId` (by `code`), `requirednessDeterminantId` (by `code`), `orderIndex`. | The seeder pre-clears `mm_field` before re-seed because there's no global `code` for upsert dedupe. |
| `mm_catalog` | `code` (unique), `name`, `source` (`static` / `registry` / `external`), `itemsJson` (a JSON array of `{value, label}` for `source=static`).                                                                                                                                                                                                                                       | `source=registry` (lookup against IM-connector capabilities) and `source=external` (HTTP) are deferred.                                                       |

The form definitions live as JSON under `app/forms/mm/mm-screen.json`, `app/forms/mm/mm-field.json`, `app/forms/mm/mm-catalog.json` and are deployed via the `form-creator-api` `/formcreator/forms` endpoint. The Joget `app_fd_mm_screen` / `app_fd_mm_field` / `app_fd_mm_catalog` tables are managed entirely by Joget's DDL machinery — never altered with raw SQL.

---

## 6. Runtime view

### 6.1 Render path — citizen wizard at edit-mode load

```
HTTP GET /jw/web/userview/.../subsidyApplication2025_crud?_mode=edit&id=<uuid>
   │
   ▼
Joget userview routing → CrudMenu → loads form definition for subsidyApplication2025
   │
   ▼
Form definition contains <MetaWizardElement serviceId="SUBSIDY_2025">
   │
   ▼
Joget instantiates MetaWizardElement, calls render(formData, false)
   │
   ▼
MetaWizardElement.render():
   1. resolveScreens() → MetaModelDao.findServiceByCode("SUBSIDY_2025")
                       → list mm_screen rows where serviceId=that, audience IN (citizen, both)
                       → sort by orderIndex
   2. for each screen: getChildren(formData) → instantiate MetaScreenElement(screenCode)
   3. for each MetaScreenElement: render(formData, false)
        │
        ▼
        MetaScreenElement.render():
           a. synthesiseChildren(formData):
              - findScreenByCode → screen row
              - if kind=guide → return [] (HTML guide rendered via getCustomHtml)
              - if kind=documents → walk mm_required_doc, synthesise FileUpload per doc
              - if kind=form → walk mm_field, synthesise per widget switch
              - readApplicantContext(formData, fields) — collect current values for §6.4 conditional eval
              - for each field: evaluate visibility/requiredness determinants, synthesise with overrides
           b. cache children in WeakHashMap<FormData, Collection<Element>>
           c. for each child: child.render(formData, readonly) → HTML
           d. wrap in <div class="form-cell">
   4. assemble identity header + tab bar + panels + footer nav + JS toggle
   5. return wizard HTML
   │
   ▼
HTML returned to browser, citizen sees the wizard with tab 1 active.
```

### 6.2 Save path — citizen clicks Save

```
HTTP POST .../subsidyApplication2025_crud (form data)
   │
   ▼
Joget FormService.executeFormStoreBinders(form, formData)
   │
   ▼
   FormUtil.executeElementFormatData(form, formData) — recursive walk
      │
      ▼
      For each Element including all synthesised children:
         element.formatData(formData)
            ├─ TextField/SelectBox/etc.: stock Joget — reads request param, puts on rowSet
            ├─ FileUpload: stock Joget — reads request param, calls FileManager.getFileByPath(),
            │                            sets file.getName() on rowSet, putTempFilePath() metadata
            └─ MetaScreenElement: ALSO contributes (because Joget's recursion to synthesised
                                  children isn't always reliable — defensive override
                                  replicates FileUpload's lifecycle for documents-kind screens)
      │
      ▼
   recursiveExecuteFormStoreBinders(form, form, formData) — invokes the form's storeBinder
      │
      ▼
   For subsidyApplication2025: storeBinder = RegBbApplicationStoreBinder (framework concern)
      │
      ▼ 
   AppService.storeFormData(formDefId, tableName, rowSet, primaryKey)
      │
      ▼
   FormDataDaoImpl.findSessionFactory(formId, tableName, rowSet, actionType=1):
      - builds Hibernate mapping from getFormRowColumnNames(rowSet) — keys present in rowSet
      - ⚠️ at actionType=0 (form save, NOT data save) reconciliation calls
        getFormDefinitionColumnNames which calls every Element.getDynamicFieldNames()
        → includes MetaScreenElement.getDynamicFieldNames() → all dynamic columns mapped
   FormDataDao.saveOrUpdate(formId, tableName, rowSet) — UPDATE
   FileUtil.storeFileFromFormRowSet(...) — moves files from temp to wflow/app_formuploads/<table>/<id>/
   │
   ▼
HTTP 302 to list view with success indicator
```

### 6.3 Conditional UI evaluation — server-side

Done as part of `synthesiseChildren(formData)` in `MetaScreenElement` — see [§6.4 of solution-architecture.md] for the broader contract. Key points for the kernel:

- The kernel does **not** evaluate determinants itself. It calls a `RoutingEvaluator` (in the framework layer) and consumes the `EvalResult.Outcome`.
- For each `mm_field` with `visibilityDeterminantId` or `requirednessDeterminantId`: the determinant code is resolved, the evaluator is invoked, the outcome is converted to a (visible, requiredOverride) pair passed to `synthesiseField`.
- Fail-open default: NULL/ERROR outcomes leave the field visible and use the field's default required behaviour.
- L2 cache (in the framework layer) makes per-field evaluation cheap (3–4 ms after warm-up).

This is the kernel's most consequential **upward dependency** on the framework — without `RoutingEvaluator`, the conditional UI hooks degrade to "always visible / default required." The dependency is by reflection-free direct import within the same OSGi bundle today; if/when the kernel splits out, this becomes a service interface the framework registers and the kernel consumes.

### 6.4 Conditional UI — client-side

After the wizard renders, `MetaWizardElement.emitConditionalUiJs(...)` walks every `mm_field` again, picks up those whose `visibilityDeterminantId` rule is a *simple equality* (`<ident> == '<value>'`), and emits a `<script>` that listens for `change` / `input` events at the wizard root and toggles dependent fields' enclosing `.form-cell` instantly. Complex rules (AND, OR, IN, numeric comparisons) are intentionally not handled client-side — the server-side path covers them on save+reload.

The pattern is described in the solution-level SAD §4.3 and codified in (forthcoming) ADR-014.

---

## 7. Deployment view

The kernel today is **packaged inside** the `reg-bb-engine` OSGi bundle:

```
plugins/reg-bb-engine/
├── src/main/java/global/govstack/regbb/engine/
│   ├── element/         ← THE KERNEL
│   │   ├── MetaScreenElement.java
│   │   └── MetaWizardElement.java
│   ├── dao/             ← THE KERNEL (partial — has framework methods too)
│   │   └── MetaModelDao.java
│   ├── api/             ← framework
│   ├── audit/           ← framework
│   ├── binder/          ← framework
│   ├── cache/           ← framework
│   ├── evaluator/       ← framework
│   ├── workflow/        ← framework
│   ├── Activator.java   ← registers BOTH kernel and framework services
│   └── Build.java
├── deploy/repack.sh
├── pom.xml
└── reg-bb-engine-8.1-SNAPSHOT.jar  ← the built bundle
```

**Activator entries for the kernel** (excerpt):

```java
context.registerService(Element.class.getName(), new MetaScreenElement(), null);
context.registerService(Element.class.getName(), new MetaWizardElement(), null);
```

**Bundle MANIFEST.MF (kernel-relevant):**

```
Bundle-SymbolicName: global.govstack.reg-bb-engine
Bundle-Version: 8.1.0.SNAPSHOT
Export-Package: global.govstack.regbb.engine.api;version="8.1.0",
                global.govstack.regbb.engine.evaluator;version="8.1.0"
Import-Package: org.joget.apps.form.dao,
                org.joget.apps.form.model,
                org.joget.apps.form.service,
                org.joget.apps.form.lib,
                ...
DynamicImport-Package: *
```

Note: the kernel's two packages (`element/`, `dao/`) are **not** in `Export-Package`. They are bundle-private — consumed only by code inside the same bundle. The split into a separate bundle would require lifting `element/` and `dao/` into exported packages and giving the framework an `Import-Package` declaration on them.

**Build / deploy:**

- `bash deploy/repack.sh` — bumps `Build.java` NUMBER + TIMESTAMP, compiles all `.java` with `javac --release 11`, repacks the JAR preserving the OSGi MANIFEST.
- Output: `plugins/reg-bb-engine/reg-bb-engine-8.1-SNAPSHOT.jar`.
- Upload via Joget admin **Settings → Manage Plugins → Upload**. Joget OSGi loads the new bundle, replaces the old one, no Tomcat restart required.

---

## 8. Cross-cutting concepts

### 8.0 Wrapping a stock Joget element — read the source first

A widget pass-through case in `synthesiseField` MUST produce the same configured object Joget would produce when loading the element from JSON. Anything else is a different code path and breaks in non-obvious ways.

Reference shape (canonical Joget pattern from `FormUtil.parseBinderFromJsonObject`):
1. Get the binder via `pluginManager.getPlugin(className)`.
2. Call `binder.setProperties(propsMap)`.
3. Call `binder.setElement(parentElement)`.
4. Call `parentElement.setOptionsBinder(binder)` (or equivalent setter for the binder type).

The crucial distinction the kernel got wrong twice on L1-1 cascading_select before getting it right: **Joget elements pull their dependencies from a mix of fields and properties**. `controlField` is a property (read via `getPropertyString("controlField")`). `optionsBinder` is a field (read via `Element.getOptionsBinder()` from a private member set by `setOptionsBinder()`). Putting a binder Map into properties is silently rejected — the property bag has nothing the element looks for. The binder must go onto the field via the setter.

Before adding any new widget pass-through case (L1-2 smart_search, L1-5 gis_polygon, L1-6 repeating_group, anything future), do these three reads in `jw-community/wflow-core/src/main/java/org/joget/apps/form/`:

- **The element's class** (e.g. `lib/SelectBox.java`, `lib/FormGrid.java`). Find its render method and the methods it calls to fetch its data. Note which configuration is **field**, which is **property**.
- **The element's binder, if any** (e.g. `lib/FormOptionsBinder.java`, `lib/MultirowFormBinder.java`). Find the loader method. Note exactly which properties drive its behaviour and how it processes them — `extraCondition` literally vs. with token substitution; `groupingColumn` for parameterised cascades; etc.
- **`FormUtil.parseBinderFromJsonObject`** (`service/FormUtil.java` ≈ line 349). The canonical instantiation pattern Joget uses. Replicate it.

The `attachCascadingOptionsBinder` method is the kernel's reference implementation — copy its shape when wiring any future binder-backed widget. CLAUDE.md "Wrapping a stock Joget element — read the source FIRST" carries the discipline at project scope.

### 8.1 Case-tolerant FormRow reads

PostgreSQL folds unquoted column identifiers to lowercase. So `mm_field.storageKey` (camelCase form-field id) becomes `c_storagekey` on disk. When Joget loads a row, the resulting `FormRow` may carry the lowercased key. `row.getProperty("storageKey")` returns null silently; `row.getProperty("storagekey")` works.

The kernel uses a `prop(FormRow, key)` helper that tries the canonical key first, then falls back to lowercase:

```java
private static String prop(FormRow row, String key) {
    if (row == null || key == null) return null;
    Object v = row.get(key);
    if (v == null) v = row.get(key.toLowerCase());
    return (v != null) ? v.toString() : null;
}
```

This pattern is mandatory for **every** camelCase property read off a `mm_*` row. Direct `row.getProperty(...)` calls are bugs.

### 8.2 Hibernate mapping bounded by getDynamicFieldNames

Joget's `FormDataDaoImpl.findAllElementIds(Element, Collection)` parses the form definition JSON and collects column ids. For static elements (declared in form-builder JSON), the ids are visible. For elements that synthesise children at runtime, the ids are NOT — unless `Element.getDynamicFieldNames()` is overridden to declare them.

If the override is missing, Joget builds a Hibernate mapping that knows about only the JSON-declared columns. When a save runs, the synthesised children's values may make it into the `FormRowSet`, but Joget's `UPDATE` statement excludes columns the mapping doesn't know about. **The user types into the field, the value is silently dropped, no error.** Hours-of-debugging bug.

`MetaScreenElement.getDynamicFieldNames()` returns the full storage-key set for the current screen, walks `mm_required_doc` for documents-kind screens to add `doc_<code>` ids, and is the documented Joget hook that fixes this.

### 8.3 Schema reconciliation only at form save

`FormDataDaoImpl.findSessionFactory(..., actionType=0)` (form save) reconciles columns from `getFormDefinitionColumnNames` and **ALTERs the table** when new columns appear. `actionType=1` (data save) uses the keys present in the rowSet at save time, *without* schema reconciliation — Hibernate's mapping defines what gets sent in the `UPDATE`.

Operational consequence: after changing what `getDynamicFieldNames` returns (e.g. adding a new `mm_field` row), you must trigger a **form save** before the new column appears in the `app_fd_*` table. App Composer's Save button or a `form-creator-api` POST does this. Recovery procedure for forms that got into a stale-schema state is in CLAUDE.md.

### 8.4 File upload temp-path lifecycle

For documents-kind screens, the kernel synthesises `FileUpload` children. The expected lifecycle on save (Joget's standard contract, replicated when the kernel contributes values directly):

1. Browser uploads a file → Joget stages it under `wflow/app_formuploads/.tmp/<sessionId>/`.
2. On form submit, `FileUpload.formatData(FormData)` reads the request param, calls `FileManager.getFileByPath(value)`, and on temp-file hit emits TWO things on the rowSet:
   - The column value: `result.setProperty(fieldId, file.getName())`
   - The metadata: `result.putTempFilePath(fieldId, paths)` — this is what triggers the file-move step.
3. After the storeBinder UPDATE completes, `FileUtil.storeFileFromFormRowSet(rowSet, tableName, primaryKeyValue)` reads `tempFilePathMap` and **moves** each file from the temp dir to `wflow/app_formuploads/<tableName>/<primaryKey>/<filename>`.

If `MetaScreenElement.formatData` contributes the column value but **forgets to call `putTempFilePath`**, the file stays in temp staging, the column has the bare filename, downloads return 404 because the reconstructed path `wflow/app_formuploads/<table>/<id>/<file>` doesn't exist. This was build-30's lesson.

### 8.5 Joget recursion to synthesised children isn't guaranteed

`FormUtil.executeElementFormatData` walks `Element.getChildren(formData)` recursively. The recursion path can break in subtle ways:
- Parent chain not set on cached children (forgot `child.setParent(this)` in synthesise).
- `continueValidation` returning false somewhere in an ancestor.
- A storeBinder declared on an ancestor short-circuiting the walk.

When the recursion doesn't reach synthesised children, their `formatData` doesn't run, no values land on the rowSet, no UPDATE happens, `datemodified` stays unchanged. **The defensive pattern**: override the container's `formatData` to contribute values directly for the children's storage keys — but you must replicate the children's full lifecycle (file upload temp paths, enum coercion, etc.) precisely. This is what `MetaScreenElement.formatData` does for documents-kind screens.

### 8.6 Y/N vs true/false

Joget's enterprise plugins use `"Y"` / `"N"` widely as boolean values. The framework's `Element.isReadonly` checks `"true".equalsIgnoreCase(prop)`. Mixing the two breaks the framework-level check.

The kernel converts `Y`/`N` → `"true"`/`"false"` at boundary points where it propagates a flag that the framework reads (e.g. setting `readonly` on a synthesised child).

### 8.8 Single-window catalogue (RegBB §6.1.6) — `mm_screen.guideKind=catalogue`

Per L2-2 (May 2026): a `kind=guide` screen with `guideKind=catalogue` renders one card per `mm_registration` row scoped to its `serviceId`, with each programme's `applicabilityDeterminantId` evaluated upfront. Outcomes map to indicators: TRUE → green "Applicable"; FALSE → red "Not applicable" + the rule's `failMessage`; NULL → grey "Need more information"; ERROR → yellow "Could not evaluate" + cause. Click-to-select updates a hidden `applied_programme` field; the wizard's storeBinder persists it to `c_applied_programme` on save.

**Phase 1 known limitation — fresh-application UX.** In `?_mode=add` mode (a wizard the citizen has just started, no row saved yet), MultiPagedForm doesn't surface previous-tab field values to the load binder of an earlier tab. The catalogue's `applicantData` map is therefore empty for the entire wizard until the first save: every applicability rule that depends on captured data evaluates to NULL, every card shows grey "Need more information." After the first save (or whenever the citizen reopens an existing application in `?_mode=edit`), the load binder fills `applicantData` and the catalogue evaluates against real data — rules return their proper TRUE/FALSE outcomes and the cards colour accordingly.

This is a framework constraint, not a kernel gap: MultiPagedForm caches in-flight values internally to its own state, not into the form's load binder, until an explicit save event. The catalogue's render path can't see that internal state from where it sits in the element tree.

**Phase 2 path — L2-2-bis live re-evaluation.** Add an Ajax round-trip: when the citizen activates the catalogue tab (via tab-focus event), JS posts the wizard's current form-element values to a new kernel endpoint (`POST /jw/api/regbb/catalogue/eval`), which builds an `EvalContext` from the posted payload, re-evaluates each programme's applicability, and returns the outcomes; the cards swap their indicator + reason in place. Effort estimate: ~3 hours. Deferred to Phase 2 because the structural catalogue is already RegBB §6.1.6 conformant; live re-evaluation is UX polish, not a spec or framework gap.

### 8.7 Widget configuration overrides — `mm_field.widgetConfig`

Per ADR-029 (L1-5b, May 2026): widget-specific configuration is analyst-authorable via a JSON blob in `mm_field.widgetConfig`. The kernel ships sensible defaults per widget; the analyst overlays selected keys via JSON; the rest stay at default. Goal: avoid hardcoding policy decisions like default zoom, default tile provider, validation thresholds, etc., into kernel source.

**Mechanism.** `MetaScreenElement.synthesiseField` sets per-case defaults into the `props` map, then calls `mergeWidgetConfig(props, f, widget, allowedKeys)` which:

1. Reads `f.widgetConfig` (TextArea on the mm_field row).
2. Parses as JSON. Malformed JSON → log warning + use defaults (fail-open).
3. For each key in the JSON, checks `allowedKeys` (per-widget allowlist). In → `props.put(k, v.toString())`. Out → log warning, drop.
4. The Element instantiates with the merged props.

**Per-widget allowlists** are constants in `MetaScreenElement`: `SIGNATURE_OVERRIDABLE_KEYS`, `FILE_UPLOAD_OVERRIDABLE_KEYS`, `GIS_POLYGON_OVERRIDABLE_KEYS`. Each names exactly the property names the analyst is allowed to override.

**Locked keys (intentionally NOT in any allowlist):**

- `id`, `label`, `validator` — kernel sets these from mm_field columns (storageKey, label, defaultBehaviorOnError). Overriding via widgetConfig would silently divorce the rendered field from its mm_field row.
- For `gis_polygon`: `areaFieldId`, `perimeterFieldId`, `centroidFieldId`, `vertexCountFieldId`, `autoCenterLatFieldId`, `autoCenterLonFieldId`, `autoCenterDistrictFieldId`, `autoCenterVillageFieldId`. The kernel synthesises companion HiddenFields with matching ids via `attachGisCompanionHiddenFields`; letting analysts retarget these would break the wiring contract.

**Per-widget property catalogue (Phase 1):**

`signature` — `width` (px), `height` (px), `encryption` (`"true"`/`"false"`), `readonly` (`"true"`/`"false"`).

`file_upload` — `fileType` (semicolon-separated dot-extensions, e.g. `".pdf;.jpg;.png"`), `maxSize` (KB), `multiple` (`"true"`/`"false"`), `removeFile`, `permissionType`, `attachment`.

`gis_polygon` — extensive: `tileProvider`, `defaultLatitude`, `defaultLongitude`, `defaultZoom`, `mapHeight`, `showSatelliteOption`, `captureMode` (UPPERCASE: `BOTH`/`WALK`/`DRAW`/`VIEW_ONLY`), `defaultMode` (`AUTO`/`WALK`/`DRAW`), `minAreaHectares`, `maxAreaHectares`, `minVertices`, `maxVertices`, `allowSelfIntersection`, `required`, `requiredMessage`, `gpsHighAccuracy`, `gpsMinAccuracy`, `autoCloseDistance`, `fillColor`, `fillOpacity`, `strokeColor`, `strokeWidth`, `enableAutoCenter`, `autoCenterCountrySuffix`, `autoCenterZoom`, `autoCenterRetryOnFieldChange`, `enableOverlapCheck` (+ `overlapFormId`, `overlapGeometryField`, `overlapDisplayFields`, `overlapFilterCondition`), `showNearbyParcels` (+ `nearbyParcels*` family), `enableSimplification` (+ `simplificationTolerance`), `apiEndpoint`, `apiId`, `apiKey`.

**Example.**

```yaml
- storageKey: parcel_geometry
  widget: gis_polygon
  widgetConfig: |
    {
      "defaultZoom": 15,
      "mapHeight": 500,
      "tileProvider": "ESRI_SATELLITE",
      "minVertices": 3,
      "maxVertices": 50
    }
```

**Adding a new widget that needs configurability** — three steps: (1) define a new constant `<WIDGET>_OVERRIDABLE_KEYS` near the others, (2) reference it in the `synthesiseField` switch's pre-`newElement` block, (3) document the property catalogue here. No other code changes needed.

**Trade-off named.** Configuration over Code (analyst-authoring) vs. Strong Typing (one column per property). Configuration over Code wins because: (a) the property surface for any one widget is large (gis_polygon alone has 30+ keys); (b) the property surface is per-widget and would force schema bloat if columned; (c) the typed-form alternative — one Joget form per widget kind — drags in an authoring-UX maintenance burden disproportionate to single-team scale (Convention-over-Invention also pulled this way). The principle that pulled the other way (Strong Typing) loses because the cost of bad JSON is bounded — fail-open + log warning + kernel default applied — and the benefit of authoring without a kernel rebuild is large.

---

## 9. Architecture decisions (component-relevant)

| ADR / D-entry                | Title                                                                       | Status                                                                                                                                                                                                                                            |
| ---------------------------- | --------------------------------------------------------------------------- | -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------  |
| **D18** — 2026-04-29         | `MetaScreenElement` as transparent `FormContainer`                          | Accepted. Replaces an earlier HTML-emission design. The keystone decision for the kernel.                                                                                                                                                          |
| **D19** — 2026-04-29         | Composite screen authoring: `mm_field` is a 1:N child of `mm_screen` via FormGrid | Accepted.                                                                                                                                                                                                                                          |
| **D20** — 2026-04-29         | FK convention split — Joget-internal FKs use UUID; cross-entity references use business `code` | Accepted. Affects `mm_field.optionsCatalogId`, `mm_screen.serviceId`, `mm_field.visibilityDeterminantId`, `mm_field.requirednessDeterminantId`.                                                                                                  |
| **D24** — 2026-04-30         | Wizard / multi-screen sequence as a first-class element (`MetaWizardElement`); Lesotho extension to RegBB | Accepted. Documented as a Lesotho-instance extension to the RegBB metamodel since the spec doesn't formalise multi-screen sequences as a single addressable construct.                                                                              |
| **ADR-012** *(forthcoming)*  | MM-form-gen as domain-agnostic kernel                                       | Pending. Codifies the layering described in solution-level SAD §4.1 — kernel below framework below modules — so the boundary doesn't drift.                                                                                                       |
| **ADR-014** *(forthcoming)*  | Conditional UI — server-side authoritative + client-side simple-equality toggle | Pending. Codifies the dual evaluation path described in §6.3–§6.4.                                                                                                                                                                                  |
| **ADR-015** *(forthcoming)*  | Widget pass-through registry                                                | Pending. Currently the widget switch is a hard-coded switch statement inside `synthesiseField`. A registry pattern (consumers register `(widgetKind → factory)` pairs) would let modules add their own widgets without touching the kernel.       |

---

## 10. Quality requirements

### 10.1 Quality scenarios

| Goal                                | Source                | Stimulus                                              | Artifact                  | Environment   | Response                                                                                       | Measure                                  |
| ----------------------------------- | --------------------- | ----------------------------------------------------- | ------------------------- | ------------- | ---------------------------------------------------------------------------------------------- | ---------------------------------------- |
| **UX parity — repeating group**     | Operator analyst       | Adds an `mm_field` with `widget=repeating_group` referencing a child form id | MetaScreenElement         | Phase 1 close-out | Synthesises a `FormGrid` configured with the child form's columns; user adds/edits/deletes rows inline | Indistinguishable from hand-built FormGrid |
| **Save lifecycle correctness**      | Citizen                | Types into a synthesised TextField, clicks Save        | Form save pipeline         | Production     | Value lands in the `app_fd_*` table column on first save                                        | 100% — no "value disappears" reports     |
| **Render performance**              | Citizen                | Opens a 6-screen wizard with ~10 fields per screen    | MetaWizardElement.render   | Production     | Server returns HTML                                                                             | < 300 ms P95                             |
| **Conditional UI snappiness**       | Citizen                | Clicks a radio that changes a determinant outcome     | Client-side toggle script  | Production     | Dependent field hides/shows in the browser                                                       | < 50 ms (no server round-trip)          |
| **Domain neutrality**               | Module developer       | Adds a new module that needs metadata-driven forms     | Kernel imports             | Compile time  | Kernel compiles with no module-specific imports added                                           | Static check: no `import …im…` in `engine.element/` |

### 10.2 ISO/IEC 25010 mapping

Quality goals 1–5 map to ISO/IEC 25010 characteristics:

| Goal                          | 25010 characteristic                                |
| ----------------------------- | --------------------------------------------------- |
| UX parity                     | Interaction capability — User interface aesthetics  |
| Domain neutrality             | Maintainability — Modularity                        |
| Save lifecycle correctness    | Reliability — Faultlessness                          |
| Render performance            | Performance efficiency — Time behaviour             |
| Cross-DBMS portability        | Compatibility — Interoperability                    |

---

## 11. Risks and technical debt

| #     | Item                                                                                                                                                                                                                                                                                                                                            | Severity | Mitigation                                                                                                                                                                                                       |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| K-R1  | **Kernel and framework share a single OSGi bundle.** The split is logical; the bundle is `reg-bb-engine`. A module that wanted to use only the kernel currently has to import the whole bundle. If/when IM lands and wants only the kernel, the bundle should be split.                                                                              | Medium   | Forthcoming ADR-012 codifies the layering. Physical split deferred to Phase 3 IM start.                                                                                                                          |
| K-R2  | **Several widgets render as read-only placeholders.** `gis_polygon`, `signature`, `smart_search`, `repeating_group`, `cascading_select`, `file_upload` (form-kind), `qr_scan` — the field renders with label `[widget=<value> — pending implementation]` and no actual capture. UX parity is not yet achieved.                                            | High     | Phase 1 close-out backlog items #1–#3. The pass-through pattern is straightforward (one switch case + one `newElement(class)` per widget); the hold-up is the field-id ↔ Joget property mapping per widget.       |
| K-R3  | **Widget dispatch is a switch statement, not a registry.** Adding a new widget kind today requires editing `synthesiseField` in MetaScreenElement.java. A module developer can't add their own widget without modifying the kernel.                                                                                                                | Low      | Forthcoming ADR-015 introduces a registry pattern. Until then, widget additions are kernel-team responsibility.                                                                                                  |
| K-R4  | **`MetaModelDao` mixes kernel and framework concerns.** Framework methods (`findServiceByCode`, `listRequiredDocsForService`, `findRoleScreenByCode`, `findRegistrationByCode`, `findDeterminantByCode`, `listDeterminantsForRegistration`) live in the same DAO as kernel methods.                                                                       | Low      | When the kernel splits out, framework methods move to a `RegBbMetaDao` in the framework. Until then, the mixing is a code-smell, not a defect.                                                                  |
| K-R5  | **Caching key is `FormData` instance.** A `WeakHashMap<FormData, Collection<Element>>` works for Joget's typical render lifecycle. If a future Joget version recycles `FormData` instances across renders, cache keys could collide. Defensive but worth re-validating per Joget upgrade.                                                            | Low      | Joget version-upgrade testing. Smoke test: render the same wizard twice in one session, verify children differ if `mm_*` configuration changed between renders.                                                  |
| K-R6  | **No explicit version on the metamodel.** `mm_screen` and `mm_field` rows are mutable; changing one mid-cycle changes how every rendered form behaves. There's no "version 1 of this screen" concept. Auditing what rendering rules were in effect for a past application is currently impossible.                                                       | Medium   | Out of scope for Phase 1. A future ADR (versioned metamodel) would address; coupled with the question of citizen-facing service version pinning per RegBB §4.4.2.                                                |

---

## 12. Glossary

| Term                          | Definition                                                                                                                                                                                                                                                              |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Kernel**                    | The metadata-driven form-rendering layer described in this document. Domain-agnostic. Exposes `MetaScreenElement`, `MetaWizardElement`, `MetaModelDao` and the three metamodel forms.                                                                                  |
| **Framework**                 | The opinionated layer above the kernel — RegBB. Adds eligibility, audit, role-scoped review, single-window catalogue. Public-services-shaped.                                                                                                                          |
| **Module**                    | A domain-specific deliverable on top of kernel and possibly framework. Subsidy module, IM module.                                                                                                                                                                       |
| **Synthesise**                | The verb for what `MetaScreenElement` does at `getChildren(formData)` time: instantiates Joget Element objects from `mm_field` rows and returns them.                                                                                                                  |
| **Storage key**               | The `mm_field.storageKey` value — the runtime field id that becomes the `c_<storageKey>` column in the underlying `app_fd_*` table.                                                                                                                                    |
| **Widget kind**               | The `mm_field.widget` value — `text`, `select`, `radio`, etc. — that drives the synthesise switch.                                                                                                                                                                       |
| **Catalog**                   | An `mm_catalog` row + its `itemsJson` payload. Used as the option list for select / radio / checkbox widgets.                                                                                                                                                          |
| **Determinant**               | A business rule (boolean predicate) authored as an `mm_determinant` row, evaluated by the framework's `RoutingEvaluator`. The kernel consumes the outcome via `mm_field.visibilityDeterminantId` / `requirednessDeterminantId` for §6.4 conditional UI.                  |
| **Form-cell**                 | The Joget DOM wrapper around each rendered field. The client-side conditional UI toggle hides/shows entire form-cells (not just inputs) so labels and help text move with the field.                                                                                    |
| **Pass-through synthesis**    | Calling `Class.forName(joget-element-class).newInstance()` and configuring it via `setProperties(Map)` rather than re-implementing the widget's render logic.                                                                                                          |
