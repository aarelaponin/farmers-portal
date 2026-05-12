# Subsidy Application — Scope of Work

**Goal:** stand up an end-to-end subsidy-application capability that conforms to the relevant parts of the GovStack Registration Building Block specification, without requiring Java changes for any new programme an operator designs.

| | |
|---|---|
| Status | Draft for review |
| Last updated | 2026-04-27 |
| Companion documents | `docs/architecture/architecture-overview.md`, `docs/architecture/regbb_architecture_diagram.svg` |
| Horizon | H1 (4–8 weeks) per the architecture overview's roadmap |

---

## 1. What "subsidy application up and running" means

A single sentence definition of done for the whole scope:

> A registered farmer opens the citizen portal, enters their National ID, sees their data pre-filled from the farmer registry, picks an open subsidy programme from a list, completes a wizard form whose questions are *generated from that programme's spec* (not authored separately), uploads the documents the *programme requires*, sees real-time quality feedback, submits, and — after a case worker decision — receives an entitlement record. Adding a new programme requires zero Java changes.

In RegBB vocabulary this exercises six of the seven services (everything except Notifications, which is deferred to Horizon 2). Section 3 enumerates them with explicit deliverables.

## 2. Boundaries

### 2.1 In scope

* Dynamic application form generated from the programme spec
* Identity resolution (NID → farmer registry → field auto-fill)
* Eligibility evaluation against the programme's rule library
* Document collection seeded from the programme's required-document list
* Application lifecycle (DRAFT → SUBMITTED → UNDER_REVIEW → DECIDED → GRANTED/REJECTED) with full audit trail
* Snapshot at submission for audit defensibility
* Operator UX (case worker review, approve/reject with reasons)
* End-to-end demo using `prog001` and an existing farmer record

### 2.2 Out of scope (deferred to later horizons)

* REST API surface (Horizon 2)
* External BB connectors — Identity BB, Notification BB, Payment BB (Horizon 3)
* Notifications dispatch (Horizon 2)
* Mobile / kiosk / USSD adapters (consume the H2 API surface)
* Multi-language UX
* Payment disbursement on approved applications (Horizon 3)
* Workflow inbox UX customisation beyond Joget defaults
* Migration of historical/manually-captured applications

### 2.3 Explicit non-goals

We are *not* trying to build a generic JSON-Schema form renderer. We are using the **seeded-children pattern** described in the architecture overview: the dynamic part of the application is real Joget form rows in well-typed child tables, populated from the programme spec by a server-side seeding tool. This keeps reporting, datalists, and form-quality rules working with native Joget primitives instead of a parallel JSON form engine.

---

## 3. Deliverables

Six work blocks. Each is independently shippable with acceptance criteria.

### Block 1 — Foundation extensions (existing plugins)

**1.1 Eligibility-evaluation mode in `form-quality-runtime`**

Today the rule engine evaluates SQL rules against persisted records. For application-time eligibility we need rules that can also read *live* application inputs (not yet committed) and the *chosen programme's* spec. Add a context object that carries `applicationId`, `programmeId`, and the un-saved form data; expose it to rules through new placeholders (`#programmeId#`, `#applicantNationalId#`, etc.).

*Acceptance:* a rule like "applicant's parcel district is in `programme.geographyCoverage`" passes/fails correctly when invoked from the application form, before Save commits anything.

**1.2 Audit spine: data-change events**

Today `audit_log` captures status transitions. Extend it to also capture data-change events (which field changed, from what, to what, by whom). New event-type column distinguishes `STATUS_TRANSITION` from `DATA_CHANGE`. Wire a generic data-change capture hook into the form-quality post-processor.

*Acceptance:* every save on a governed form writes one or more `audit_log` rows; querying by `entity_id` returns a complete history of both statuses and data changes.

### Block 2 — Identity Resolver plugin (new bundle)

`plugins/identity-resolver-runtime/`

**2.1 Configuration master data**

Two admin forms + their datalists:

* `app_resolver_config` — named resolvers (`id`, `sourceFormId`, `sourceLookupField`, `notFoundMessage`, `notFoundActionUrl`)
* `app_resolver_field_map` — source→target mappings (`resolverConfigId`, `sourceFieldId`, `targetFieldId`, `readonlyAfterResolve`, `resolveStrategy`, `chainedSourceFormId`)

*Acceptance:* an operator can author a new resolver in App Composer in under 5 minutes, with no developer involvement.

**2.2 `ResolverService` and supporting classes**

Pure-Java service layer. Reads the two MD tables, queries the source form via `FormDataDao`, applies field mappings (including chained joins for fields that live on a related subform), returns a flat `Map<targetFieldId, value>`.

*Acceptance:* unit-tested against a mocked `FormDataDao`; integration-tested against the live `farmerBasicInfo` + `farmerResidency` tables.

**2.3 `IdentityResolverElement` (Joget form element)**

Drop-and-configure form element. Renders a labelled text input with a small "Resolve" indicator. On blur or button click, fires the lookup and populates the form's other fields via the same subform-prefix-aware DOM selector pattern we hardened in `QualityBannerElement` build-006.

*Acceptance:* drop on `spApplication` Tab 1, configure with `resolverConfigId=farmerByNid`; entering a known NID populates `applicant_first_name`, `applicant_last_name`, `applicant_district`, etc.; entering an unknown NID shows the "register first" link.

**2.4 Internal REST endpoint `/api/identity/resolve`**

Thin REST wrapper around `ResolverService`. Returns JSON. Authenticated via Joget session — no public exposure yet (that's Horizon 2).

*Acceptance:* `curl` from the same Tomcat against the endpoint returns valid mapped JSON.

**2.5 Seed `farmerByNid` configuration**

Production data: one row in `app_resolver_config` and ~6 rows in `app_resolver_field_map` for the most useful pre-fill fields.

*Acceptance:* runtime test on prog001's farmer succeeds; banner element + resolver coexist on `spApplication` without conflict.

### Block 3 — Application Engine plugin (new bundle)

`plugins/application-engine-runtime/`

**3.1 Three application child schemas**

* `sp_application_eligibility_check` — one row per eligibility criterion, columns: `parent_id`, `programme_criterion_id`, `criterion_label`, `applicant_response`, `evaluator_note`
* `sp_application_benefit_request` — one row per benefit item, columns: `parent_id`, `programme_benefit_id`, `item_label`, `unit`, `requested_qty`, `approved_qty`, `cap`, `status`
* `sp_application_doc_v2` — supersedes static `spApplicationDoc`. Columns: `parent_id`, `programme_doc_requirement_id`, `doc_label`, `doc_mandatory`, `uploaded_file`, `status`

Forms created via Joget UI / form-gen skill, tables auto-generated on form save.

*Acceptance:* the three tables exist, can be edited via standard Joget grids, and have datalists for inspection.

**3.2 `ApplicationSeedingTool` post-processor**

Java post-processor wired to `spApplication` create event. Reads the chosen programme's eligibility criteria, benefit items, and document requirements; INSERTs corresponding seed rows into the three child tables. Idempotent: if seed rows already exist for an application, leaves them alone.

*Acceptance:* creating a new application linked to `prog001` automatically populates the three child grids with the programme's items; opening the application shows them ready for the farmer to fill.

**3.3 Snapshot tool for DRAFT → SUBMITTED transition**

A workflow tool plugin that, on the DRAFT → SUBMITTED transition, copies the resolver-populated values (applicant name, district, parcel hectares, etc.) from their live source into immutable application columns. Locks the record from further field edits via a status gate.

*Acceptance:* once an application is SUBMITTED, changing the underlying farmer's name does NOT change the application's recorded applicant name; queries 5 years later return the data the decision was made on.

**3.4 `spApplication` wizard form**

The actual form farmers fill in. Wrapper + 6 tab subforms:

1. Applicant & Programme (Identity Resolver here)
2. Eligibility self-attestation (FormGrid over `sp_application_eligibility_check`)
3. Benefits requested (FormGrid over `sp_application_benefit_request`)
4. Documents (FormGrid over `sp_application_doc_v2`)
5. Declaration & Signature (existing pattern)
6. Approval — visible to operators only (HTMLPermission gate)

`QualityBannerElement` on the wrapper, `serviceId=farmer_application`. `postProcessor` wired to the same engine.

*Acceptance:* opening `spApplication_crud?_mode=add` shows a usable wizard; the QUALITY banner appears at the top; the seeding tool runs on first save and populates the dynamic grids.

**3.5 Form-quality service `farmer_application`**

Register a new service in `qa_service` with primary form `spApplication`. Seed 6–8 starter rules:

* `application.programme_must_be_open` (ERROR — programme.status = ACTIVE and now() between programme.open_date and programme.close_date)
* `application.parcel_in_covered_district` (ERROR)
* `application.eligibility_all_answered` (ERROR — all rows in eligibility_check have a response)
* `application.eligibility_all_passed` (ERROR — all responses are "yes" / programme criteria met)
* `application.at_least_one_benefit_requested` (ERROR — at least one benefit row has qty > 0)
* `application.benefit_within_cap` (ERROR — every requested_qty ≤ cap)
* `application.required_docs_uploaded` (ERROR — every mandatory doc has uploaded_file ≠ null)
* `application.declaration_signed` (WARNING)

*Acceptance:* on save with deliberate gaps, banner shows the right counts; status flips to ISSUES_DETECTED; gates prevent SUBMITTED.

**3.6 End-to-end test with prog001**

Author a complete application against `prog001`, check every gate, fix issues, submit, see the snapshot freeze.

*Acceptance:* full happy path takes < 10 minutes, no Java change required.

### Block 4 — Decision Engine plugin (new bundle)

`plugins/decision-engine-runtime/`

**4.1 Workflow process definition**

XPDL package: `farmer_application_decision`. States: `submitted → under_review → (approved | rejected | needs_info) → granted | closed`. Transitions write to `audit_log` automatically via the status framework.

**4.2 Approve / Reject / Request-info decision tools**

Three `DefaultApplicationPlugin` tools, each parameterised by the application id and reason. Approve creates an entitlement row; Reject closes with reason; Request-info routes back to the farmer with a comment.

**4.3 Grant issuance logic**

On Approve: insert a row into `imEntitlement` with the applicant, programme, the approved benefit items (with their `approved_qty`), and a reference back to the application. This connects RegBB Decision Management to the existing entitlement model.

**4.4 Reason capture**

A small mandatory text field on every reject / request-info decision. Persisted on the audit row.

**4.5 End-to-end test**

Submit application, case worker approves, entitlement appears, audit shows full trail.

### Block 5 — Operator workspace

Pure Joget configuration, no plugin code.

* Datalist: "My assigned applications" filtered by `assigned_to = #currentUser#`
* Datalist: "Applications by status" with date-range filter
* Application review form view (operator-only tab + decision buttons)
* Audit-trail viewer for an individual application

*Acceptance:* a case worker logs in, sees their queue, opens an application, reviews, decides — without ever leaving Joget.

### Block 6 — Documentation

* `docs/architecture/identity-resolver-design.md` — detailed spec for Block 2
* `docs/architecture/application-engine-design.md` — detailed spec for Block 3
* `docs/architecture/decision-engine-design.md` — detailed spec for Block 4
* `docs/architecture/operator-runbook-add-programme.md` — step-by-step "how to add a new subsidy programme" for non-developers
* `docs/architecture/regbb-conformance-checklist.md` — explicit mapping of our deliverables to RegBB spec sections, for external review

---

## 4. Sequencing and dependencies

```
Block 1 (Foundation extensions) ─┐
                                  ├──► Block 3 (Application Engine) ──► Block 4 (Decision Engine) ──► Block 5 (Operator workspace)
Block 2 (Identity Resolver) ─────┘
Block 6 (Documentation) — runs parallel to all coding blocks
```

* Blocks 1 and 2 are independent and can run in parallel
* Block 3 depends on both 1 and 2 being functional (or stubbed)
* Block 4 depends on Block 3
* Block 5 depends on Block 4
* Block 6 documents each block as it lands

If we're constrained to one engineer, the linear path is **1 → 2 → 3 → 4 → 5**, with documentation written immediately after each block while the work is fresh.

If we have two engineers, one takes Block 1+3+4, the other takes Block 2 then helps with Block 5.

---

## 5. Effort estimates (T-shirt)

| Block | Size | Calendar (1 engineer) | Notes |
|---|---|---|---|
| 1 — Foundation extensions | M | 1–2 weeks | Eligibility mode is the bigger half |
| 2 — Identity Resolver | L | 2 weeks | New plugin, small surface, uses patterns we know |
| 3 — Application Engine | XL | 3 weeks | Largest deliverable: 3 child schemas + seeding logic + wizard form + service rules + snapshot tool |
| 4 — Decision Engine | L | 2 weeks | Workflow + tools — Joget primitives we have, just unused |
| 5 — Operator workspace | M | 1 week | Joget configuration heavy, low code |
| 6 — Documentation | M | 1 week, parallel | Roughly half a week per design doc + runbook |

**Total elapsed: ~9 weeks for one engineer; ~6 weeks with two.** All inside the H1 (4–8 weeks) horizon stated in the architecture overview if we're staffed for two.

---

## 6. Risks and mitigations

**R1 — Joget MultiPagedForm tab rendering edge cases.** Already hit during banner work; mitigated by the subform-prefix-aware DOM selector in `QualityBannerElement` build-006. The Identity Resolver inherits the same pattern.

**R2 — Form-quality engine evaluating un-saved data.** Currently rules read committed rows. The eligibility-evaluation mode (deliverable 1.1) introduces in-memory context. Risk: rule authors writing rules that mix committed and uncommitted data unsafely. *Mitigation:* clear documentation in the runbook, plus a rule-author warning in the qa_rule form for context-using rules.

**R3 — Snapshot timing on DRAFT → SUBMITTED.** The snapshot tool must run *exactly once* per application, atomically with the transition. *Mitigation:* idempotent design (snapshot keyed by application id; second invocation is a no-op), tested as part of Block 3.6.

**R4 — Backward compatibility with static `spApplication`.** Replacing the existing form may break references in datalists, userviews, workflow processes. *Mitigation:* keep `spApplication` form id stable; only change its internal structure. Audit references before/after with the same script we used for the broken-refs audit.

**R5 — Sample programme data quality.** `prog001` (PRG-2025-001) was built before the dynamic-application scope; its eligibility / benefit / document rows may not be sufficiently populated to drive a credible demo. *Mitigation:* Block 3.6 includes a "fill out prog001 to demo-ready quality" step before the end-to-end test.

**R6 — Operator confusion between "programme" and "application".** Same fields appear in both contexts. *Mitigation:* operator runbook (deliverable 6.4) explicitly contrasts the two and walks through one full programme lifecycle.

---

## 7. Acceptance criteria — end-to-end demo

The whole scope is "done" when this 12-step demo runs cleanly without any Java change between step 1 and step 12:

1. Operator logs into App Composer, opens the programme designer
2. Operator creates programme `PRG-2026-001` with: 3 eligibility criteria, 2 benefit items (with caps), 2 required documents, district coverage of `Maseru`, open dates straddling today
3. Operator marks programme `ACTIVE`; form-quality engine reports clean
4. Farmer (NID `9765433`, district Maseru) opens citizen portal, picks "Apply for subsidy"
5. Farmer enters NID; banner pops up; resolver fills `applicant_first_name=Itumeleng`, `applicant_last_name=Josias`, `applicant_district=Maseru`
6. Farmer picks `PRG-2026-001` from the dropdown
7. On Save, seeding tool populates the eligibility / benefits / documents grids with the programme's items
8. Farmer answers eligibility (3 yes/no), requests quantities for 2 benefit items, uploads 2 documents
9. Form-quality banner: `✓ Clean`. Farmer clicks Submit; status → SUBMITTED; snapshot tool freezes resolved values
10. Case worker logs in, sees the application in their queue, opens it, reviews, clicks Approve with note "All documents in order"
11. Workflow grants the application; entitlement row appears in `imEntitlement` with the approved quantities; audit_log shows the full trail (programme creation, application creation, every save with data-change events, status transitions, decision)
12. **At no point did anyone change a Java file.**

When this demo runs cleanly, the scope is delivered.

---

## 8. RegBB conformance — what this scope does and doesn't address

| RegBB service | Addressed by this scope | Evidence |
|---|---|---|
| Service Catalog | ✅ Programme designer (existing) used as the catalog | Operator step in demo |
| Identity Verification | ✅ Identity Resolver (Block 2) | NID lookup in demo step 5 |
| Application Submission | ✅ Application Engine (Block 3) | Dynamic seeding in demo step 7 |
| Document Submission | ✅ As part of Block 3 | Doc seeding + upload in step 8 |
| Eligibility Evaluation | ✅ Form-quality eligibility mode (Block 1.1) + service `farmer_application` (Block 3.5) | Quality banner in step 9 |
| Decision Management | ✅ Decision Engine (Block 4) | Approve flow in step 10 |
| Audit & Observability | ✅ Audit spine extension (Block 1.2) | Full trail visible in step 11 |
| Workflow | ✅ Joget XPDL (Block 4.1) | Routing in steps 9–10 |
| Notifications | ❌ Out of scope (Horizon 2) | — |
| Payments | ❌ Out of scope (Horizon 3) | — |
| **REST API surface** | 🟡 Internal endpoint only (Block 2.4) | Full surface in Horizon 2 |

After this scope ships, we are conformant to RegBB on the seven core services for an internal Joget consumer. We are not yet conformant from a Building Block composition standpoint — that requires the API surface, which is the Horizon-2 priority.

---

## 9. What we need from you to start

1. **Sign-off on this scope** — anything to add, remove, or sequence differently?
2. **Confirmation of the prog001 data state** — is `prog001` the right pilot programme, or should we use a different one (or build a fresh one to demo against)?
3. **Decide on engineer allocation** — one or two, affecting calendar.
4. **Pick the starting block** — defaults to Block 2 (Identity Resolver) because it's small, independently useful, and unblocks Block 3.

Once those four answers are settled I'll cut a `docs/architecture/identity-resolver-design.md` (the deeper spec for Block 2), and we can begin the build.

---

*End of document.*
