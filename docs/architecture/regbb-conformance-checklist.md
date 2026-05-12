# GovStack Registration BB — Conformance Checklist

A concrete mapping of our deliverables to the GovStack Registration Building Block specification, with status, evidence, and gaps. Intended for review by the policy team and external GovStack reviewers.

| | |
|---|---|
| Status | Draft |
| Last updated | 2026-04-27 |
| Reference spec | https://govstack.gitbook.io/specification/building-blocks/registration |
| Companion documents | `architecture-overview.md`, `subsidy-application-scope.md`, `identity-resolver-design.md`, `application-engine-design.md` |
| Conformance scope | Horizon 1 (subsidy application end-to-end, internal) |

---

## How to read this document

For each RegBB requirement, four pieces of information:

* **Spec section** — the RegBB section number/title we map to (best-effort against the current GitBook spec)
* **Requirement** — what the spec asks for
* **Status** — `✅` (delivered or planned in H1), `🟡` (partial in H1, completed in later horizon), `❌` (out of scope), `N/A` (not applicable to our domain)
* **Evidence / gap** — the file, plugin, or runbook entry that demonstrates conformance, or what's missing

A requirement marked `🟡` is considered conformant only after the named horizon completes.

---

## 1. Architectural principles

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Architecture / "Configuration over code" | Adding a new service must not require a code change | ✅ | Programme designer (`spProgramMain`) + form-quality master data + resolver master data; demonstrated in 12-step demo (subsidy-application-scope §7) |
| Architecture / "API-first" | Every domain service exposes a stable REST contract | 🟡 H2 | Internal endpoints exist (Identity Resolver §4.3); full REST surface deferred to Horizon 2 |
| Architecture / "Composability" | Each service can be replaced by an external BB | 🟡 H3 | Identity Resolver designed for swap-in (`identity-resolver-design.md` Appendix A); Notification and Payment connectors deferred to Horizon 3 |
| Architecture / "Audit by default" | Every state change recorded | ✅ | `audit_log` table; `joget-status-framework` writes one row per transition; H1 extension adds data-change events |
| Architecture / "Privacy by design" | Minimum data captured, accessed only by authorised parties | 🟡 | Identity Resolver respects `isActive` gating + Joget session auth; field-level redaction deferred to Horizon 2 |

## 2. Service Catalog

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Service Catalog | Stores the set of services a citizen may register for | ✅ | Programme designer (`spProgramMain`) — each programme is a registrable service |
| Service Catalog | Each service has a machine-readable spec (eligibility, benefits, documents) | ✅ | Programme child tables: eligibility criteria, benefit items, document requirements (Block 3.1) |
| Service Catalog | Services have lifecycle (draft / active / closed) | ✅ | `programme.status`; gated by `qa_gate` on `farmers_subsidy` service |
| Service Catalog | Services discoverable by API | 🟡 H2 | Internal datalist works; `/api/services` REST surface deferred |
| Service Catalog | Schema versioning (programme spec changes over time) | 🟡 H2 | Joget app version covers structural changes; row-level versioning deferred |

## 3. Identity Verification

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Identity Verification | Lookup by foundational identifier | ✅ | `IdentityResolverElement` + `ResolverService` (`identity-resolver-design.md`) |
| Identity Verification | Field-level data minimisation (return only what's mapped) | ✅ | `app_resolver_field_map` enumerates exactly which fields are exposed |
| Identity Verification | "Once-only" — citizen never re-types known data | ✅ | Resolver pre-fills target form on input |
| Identity Verification | Configurable per consumer | ✅ | `app_resolver_config` defines named resolvers (`farmerByNid`, `parcelByCode`, …) |
| Identity Verification | Delegate to external Identity BB when available | 🟡 H3 | Migration path documented in `identity-resolver-design.md` Appendix A |
| Identity Verification | Authentication (citizen identity proof) | ❌ Out of scope | We trust the entered NID at H1; full identity proofing requires external Identity BB |
| Identity Verification | Audit every resolve | ✅ | `audit_log` event on each `ResolverService.resolve()` call |

## 4. Application Submission

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Application Submission | Forms generated from service spec (schema-driven) | ✅ | `ApplicationSeedingTool` seeds child grids from programme spec (`application-engine-design.md` §5.2) |
| Application Submission | Real-time validation feedback to applicant | ✅ | `QualityBannerElement` + `farmer_application` rule library |
| Application Submission | Save-as-draft and resume | ✅ | Joget native + `DRAFT` lifecycle state |
| Application Submission | Submit triggers downstream workflow | ✅ | `DRAFT → SUBMITTED` transition routes to case worker via XPDL workflow (Block 4) |
| Application Submission | Submission immutable after the act | ✅ | Snapshot tool (`SnapshotTransitionTool`) freezes resolved values; status gate prevents farmer-side edits |
| Application Submission | API-addressable submission | 🟡 H2 | Internal Joget UI works; REST `/api/applications` deferred |
| Application Submission | Multi-channel intake (web, mobile, kiosk) | 🟡 H2 | Web works; other channels need REST surface |

## 5. Document Submission

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Document Submission | Document requirements declared per service | ✅ | New `sp_doc_requirement_row` programme child (Block 3.1) |
| Document Submission | Citizen-facing upload UI matched to requirements | ✅ | `sp_application_doc_v2` FormGrid on `spApplication` Tab 4 |
| Document Submission | File format and size validation | ✅ | Per-row `accepted_formats` + `max_size_kb`, enforced at upload |
| Document Submission | Verification status tracked | ✅ | `status` column: `MISSING / UPLOADED / VERIFIED / REJECTED` |
| Document Submission | Mandatory docs gate submission | ✅ | Rule `application.required_docs_uploaded` (`application-engine-design.md` §5.5) |
| Document Submission | Document storage compliant with retention policy | 🟡 | Joget's default file storage; retention policy review deferred |
| Document Submission | Verifier identity captured | ✅ | `verifier_note` + audit row (createdBy on `sp_application_doc_v2` update) |

## 6. Eligibility Evaluation

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Eligibility Evaluation | Pluggable rule library per service | ✅ | `qa_service` + `qa_rule` (form-quality runtime) |
| Eligibility Evaluation | Rules editable by operators (no code) | ✅ | Rules are admin-edited rows in `qa_rule` |
| Eligibility Evaluation | Rules can read application form data | 🟡 → ✅ | Block 1.1 extends form-quality engine to support this |
| Eligibility Evaluation | Rules can read related registry data | ✅ | Today: rule SQL can JOIN to any `app_fd_*` table |
| Eligibility Evaluation | Severity levels (block vs warn) | ✅ | `qa_rule.severity = ERROR / WARNING` |
| Eligibility Evaluation | Decisions auditable back to rule outcomes | ✅ | `qa_issue` rows persist per evaluation; linked to record id |
| Eligibility Evaluation | Re-evaluation on data change | ✅ | Post-processor fires on every save |

## 7. Decision Management

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Decision Management | Approve / reject / request-info workflow | ✅ | XPDL process `farmer_application_decision` (Block 4.1) |
| Decision Management | Reason captured with every decision | ✅ | `decision_reason` column + audit row (`application-engine-design.md` §4.4) |
| Decision Management | Decision routed to authorised role | ✅ | XPDL participant assignment; case-worker datalist |
| Decision Management | Grant issuance on approval | ✅ | Decision tool inserts `imEntitlement` row (Block 4.3) |
| Decision Management | Notify applicant on decision | 🟡 H2 | Email/SMS deferred to `notification-dispatcher-runtime` (Horizon 2) |
| Decision Management | Appeal process | 🟡 | `spAppeal` form exists as stub; full flow deferred |
| Decision Management | Decision API | 🟡 H2 | Internal Joget UI works; `/api/decisions` deferred |

## 8. Audit & Observability

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Audit & Observability | Append-only audit log of state changes | ✅ | `audit_log` table; `joget-status-framework` writes on every transition |
| Audit & Observability | Audit log captures actor, timestamp, reason | ✅ | `triggered_by`, `timestamp`, `reason` columns |
| Audit & Observability | Audit log captures data-change events | 🟡 → ✅ | Block 1.2 extends with `event_type=DATA_CHANGE` rows |
| Audit & Observability | Tamper-evidence (e.g. write-once, hash chain) | 🟡 | Joget table is append-only by convention; cryptographic chain deferred to Horizon 2 |
| Audit & Observability | Query API | 🟡 H2 | `/api/audit?entityId=...` deferred |
| Audit & Observability | Retention policy enforced | 🟡 | Default Joget retention; policy review needed for production |

## 9. Workflow

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Workflow | Engine for service-specific processes | ✅ | Joget XPDL 1.0 |
| Workflow | Process-as-config (not code) | ✅ | XPDL definitions are JSON/XML, not Java |
| Workflow | Multi-actor routing | ✅ | XPDL participants |
| Workflow | SLA / deadline support | ✅ | XPDL deadlines + escalation activities |
| Workflow | Process versioning | ✅ | Joget app versioning |

## 10. Notifications

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Notifications | Templated messages per event | ❌ H2 | `spNotifTemplate` form exists as stub |
| Notifications | Multi-channel (email / SMS / Push) | ❌ H2 | Deferred to `notification-dispatcher-runtime` |
| Notifications | Delegation to external Notification BB | ❌ H3 | Designed-for in architecture; not yet implemented |

## 11. Payments

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Payments | Disbursement on approved applications | ❌ H3 | Deferred — gated on Decision Management being real first |
| Payments | Delegation to external Payment BB | ❌ H3 | Designed-for in architecture; not yet implemented |
| Payments | Reconciliation | ❌ H3 | Deferred |

## 12. API surface

| Spec section | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| API / Catalog | `GET /services` | 🟡 H2 | Internal datalist works; REST endpoint deferred |
| API / Identity | `POST /identity/resolve` | ✅ Internal | Implemented (Identity Resolver Block 2.4); session-authenticated only at H1 |
| API / Applications | `POST /applications` | 🟡 H2 | Internal Joget UI; REST deferred |
| API / Applications | `GET /applications/{id}` | 🟡 H2 | Internal datalist; REST deferred |
| API / Eligibility | `POST /eligibility/evaluate` | 🟡 H2 | Engine works internally; REST wrapper deferred |
| API / Decisions | `POST /decisions/{id}` | 🟡 H2 | Internal tools work; REST wrapper deferred |
| API / Audit | `GET /audit?entity={id}` | 🟡 H2 | Internal datalist; REST wrapper deferred |
| API / OpenAPI | Published OpenAPI 3.x specification | 🟡 H2 | Annotations planned in plugin code; full publication deferred |

## 13. Cross-cutting non-functional

| Concern | Requirement | H1 status | Evidence / gap |
|---|---|---|---|
| Authentication | Citizen authenticated before submitting | 🟡 | Joget login at H1; full eIDAS-style proofing in Horizon 3 with Identity BB |
| Authorisation | Role-based access to operator functions | ✅ | Joget roles + HTMLPermission gates on operator-only tabs |
| Internationalisation | Multi-language UI | ❌ | English-only at H1; deferred |
| Accessibility | WCAG 2.1 AA conformance | 🟡 | Joget themes are partially compliant; explicit audit deferred |
| Performance | <2s response on resolve, <5s on form load | 🟡 | Acceptable at dev scale; load testing deferred |
| Disaster recovery | Backup/restore procedures | 🟡 | Postgres backup is operational; runbook deferred |

---

## 14. Conformance summary

* **Fully conformant in H1 (✅):** Configuration-over-code principle, audit by default, Service Catalog (internal), Identity Verification (internal), Application Submission (schema-driven), Document Submission, Eligibility Evaluation (rule library), Decision Management (workflow + grant), Workflow engine
* **Partially conformant in H1, completed in H2 (🟡 H2):** API-first surface, multi-channel intake, Notifications, REST API for every service, OpenAPI publication
* **Deferred to H3 (🟡 H3):** External BB connectors (Identity, Notification, Payment), full identity proofing, cross-registry data exchange (DRC / x-Road)
* **Out of scope, with rationale (❌):** Multi-language UI (low priority for pilot), JSON-Schema dynamic form renderer (architecturally chose seeded-children pattern instead — see `architecture-overview.md` §4)

After the H1 scope ships, an external RegBB reviewer would find this implementation **conformant on the seven core RegBB services for an internal Joget consumer**, with an explicit, dated migration plan to API-conformant and BB-composable implementations.

---

## 15. Review questions for the policy team

1. **Is "internal Joget consumer" sufficient for the pilot launch**, or must we have the H2 REST API surface before going live?
2. **Which external BBs are realistic for Lesotho in 12 months** — is there a national Identity BB, Notification BB, Payment BB candidate to integrate with?
3. **Does the snapshot-at-submission semantic match the policy requirement?** (5-year audit faithfulness vs live-query of current data)
4. **Is the fact that programme edits do not retroactively re-seed existing applications acceptable**, or must the system propagate programme changes to in-flight applications?
5. **What is the appeal process** for rejected applications — is `spAppeal` correct, or should it be a fully separate flow?
6. **What's the file retention policy** for uploaded documents?
7. **Multi-language is deferred** — when does Sesotho UI become a hard requirement?

Answers to these shape Horizon 2 priorities.

---

## 16. References

* GovStack Registration BB specification — https://govstack.gitbook.io/specification/building-blocks/registration
* GovStack Identity BB specification — https://govstack.gitbook.io/specification/building-blocks/identity
* GovStack Notification BB specification — https://govstack.gitbook.io/specification/building-blocks/messaging
* GovStack Payment BB specification — https://govstack.gitbook.io/specification/building-blocks/payments
* `architecture-overview.md` — system layered architecture
* `subsidy-application-scope.md` — H1 scope definition
* `identity-resolver-design.md` — detailed design for Block 2
* `application-engine-design.md` — detailed design for Block 3
