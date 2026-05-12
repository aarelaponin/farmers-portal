# Budget Accounting Methodology — Lesotho Farmers Portal

**Status.** Draft 1, May 2026. Authored before any Budget Engine code (L3-1) is committed, so the implementation has a business-grounded model to conform to. Amendments to the existing Budget Engine SAD and ADRs 022-026 are listed in §11.

**Why this document exists.** The Budget Engine SAD describes a state-machine ledger with five funnel stages (Reservation → Pre-Commitment → Commitment → Expense). That's the *mechanism*. This document defines the *business model*: what an account is, what a transaction is, what debits and credits move where, and what invariants must always hold. Without that, the engine works mechanically but nobody can answer "where did the money go?" in business terms or hand the auditor-general a coherent report. With it, every line of code in the Budget Engine has a one-to-one correspondence with an accounting concept that an MoA finance staffer or an external IPSAS auditor would recognise.

**Scope.** Public-sector fund accounting at the level a country-tier subsidy programme requires. Reasonably IPSAS-compliant — terminology and discipline aligned with IPSAS 24 (Presentation of Budget Information), IPSAS 1 (Presentation of Financial Statements), and IPSAS 23 (Revenue from Non-Exchange Transactions, for donor-funded programmes) — without claiming to produce statutorily-conformant IPSAS financial statements end-to-end. Phase 3+ work may extend toward full conformance if the auditor-general adopts the system as a primary record.

**Out of scope.** General ledger across the whole MAFSN; double-entry against the national consolidated fund; payroll, procurement, or non-subsidy expenditure; tax accounting; multi-currency operations; intercompany eliminations.

---

## 1. Why a methodology, not just a ledger

A naive event ledger ("RESERVATION +M2,000 on envelope X at 14:35") tells you what happened but not what it means. A finance officer reviewing it cannot answer:

- How much do we owe this specific applicant right now?
- Of the M450,000 spent on PRG_2025_002 this year, how much was government money and how much was donor money?
- The supplier sent us an invoice for M75,000; does our system agree we owe that?
- The auditor asks "show me your unspent committed balance per programme as of 31 March." Can we produce it?
- The donor asks "what's the burn rate of our 40% contribution to PRG_002 this quarter?"

These are normal accounting questions in any public-sector setting. Answering them requires:

1. **Accounts** — named buckets where money belongs. Not just events.
2. **Transactions** — every business event posts to two or more accounts in a way that preserves a balance equation.
3. **Sub-ledgers** — detailed accounts for each beneficiary and each vendor, summing to programme-level control accounts.
4. **Invariants** — relationships that must hold at all times (e.g., the sum of beneficiary balances equals the programme's pre-commitment + commitment total).
5. **Authority + audit** — who can post which transactions, recorded permanently.

Once those are pinned down, the Budget Engine code becomes a small thing — a faithful renderer of an accounting model that can be explained on a whiteboard.

---

## 2. Chart of accounts

The chart of accounts is the named structure of buckets where money lives. It has four layers in this system, each with a distinct purpose:

### 2.1 Layer 1 — Envelope control accounts

One per **(programme × fiscal year)**. The envelope IS an account; the funnel stages are its **subaccounts**.

```
ENV_PRG_2025_001_FY2526   "Block Farming — Maize Lowlands 2025/26, FY 25-26"
  ├── ENV_PRG_2025_001_FY2526.ALLOCATED        (control total — set by ALLOCATION events)
  ├── ENV_PRG_2025_001_FY2526.AVAILABLE        (unencumbered; the only account that can decrease via citizen action)
  ├── ENV_PRG_2025_001_FY2526.RESERVED         (citizens have submitted, not yet operator-decided)
  ├── ENV_PRG_2025_001_FY2526.PRE_COMMITTED    (operator approved; voucher not yet issued)
  ├── ENV_PRG_2025_001_FY2526.COMMITTED        (voucher issued; not yet redeemed)
  └── ENV_PRG_2025_001_FY2526.EXPENSED         (voucher redeemed; cash discharged)
```

**Account type.** Each is a *budget account* — a memorandum representation of authority to spend, not a real cash account. Real cash sits in the consolidated treasury account, outside this system's purview. The IPSAS distinction matters: budget accounts answer "is this expense within authority?", cash accounts answer "is the money actually there?".

**Balance direction.** AVAILABLE decreases as money moves through the funnel; RESERVED, PRE_COMMITTED, COMMITTED grow then shrink as money advances; EXPENSED only grows (until the cycle closes). Reversal events undo prior advancement.

### 2.2 Layer 2 — Funding-source contribution accounts

One per **(envelope × funding source)**. For each programme, the envelope's allocated amount is decomposed into source contributions whose proportions are fixed at programme launch.

```
PRG_2025_002 envelope total = M450,000
  ├── SRC_GOL_PRG_2025_002_FY2526       (Government of Lesotho, 60% = M270,000)
  └── SRC_FAO_IFAD_PRG_2025_002_FY2526  (FAO/IFAD donor, 40%       = M180,000)
```

Each source contribution carries the same six subaccounts as its parent envelope (ALLOCATED, AVAILABLE, RESERVED, PRE_COMMITTED, COMMITTED, EXPENSED). When an envelope-level transaction posts an amount, it is **prorated to source contributions** by the source's allocated share.

**Why this layer exists.** Donors and the Treasury both have legitimate reporting needs. The donor needs to see "what % of MY 40% has been spent?". The Treasury needs to see "of the M270k we put in, how much has come back as commitments outside our control?". Without source contribution accounts, the system can only answer envelope-level questions; with them, both reports are derivable in a single SELECT.

**Single-source programmes** (PRG_001, PRG_003, PRG_004 in the current Lesotho fixture) have one source contribution at 100%, which is degenerate but keeps the model uniform across programmes.

**Reallocation between sources.** A separate transaction type. If GOL takes over M50k of FAO's commitment in mid-cycle, that's a `SOURCE_REALLOCATION` posting that moves M50k from the FAO contribution to the GOL contribution at the same funnel stage; the envelope total is unchanged.

### 2.3 Layer 3 — Beneficiary sub-ledger

One account per **(applicant × programme application)**, opened when the operator approves the application (PRE_COMMITMENT) and closed when the application is fully expensed (voucher redeemed) or the commitment is released (cancellation, withdrawal).

```
BNF_<applicationId>   e.g. BNF_29e6581a-092a-…
  ├── status:   open | partially_expensed | closed | released
  ├── balance:  the amount currently obligated to this applicant
  ├── lifecycle dates:  pre_committed_at, committed_at, expensed_at, closed_at
  └── linked envelope + source contribution percentages at the time of pre-commitment
```

**Why this layer exists.** A finance officer needs to answer "what does the system say we owe this applicant right now?" — for dispute resolution, for verification of a benefit claim, for handling mid-cycle changes (programme cancellation, applicant withdrawal). Without the sub-ledger, the only way to answer is to scan the event log and reconstruct, which is too slow for an operator-on-a-phone-call use case and too error-prone for audit.

**Source contribution proration on the sub-ledger.** When BNF_X is opened with M2,000 against PRG_2025_002 (60% GOL / 40% FAO), the sub-ledger record stores both the M2,000 total AND the breakdown M1,200 / M800. This metadata is needed for source-level reporting at the beneficiary granularity (e.g., "show all donor-funded approvals this month").

**Lifecycle and closure.** A sub-ledger account closes when its balance reaches zero (fully expensed) or when an explicit RELEASE event zeroes it out (cancellation). Closed accounts are retained indefinitely for forensic purposes; queries for "currently owed" filter on `status='open'`.

### 2.4 Layer 4 — Vendor sub-ledger

One account per **vendor / supplier** that fulfills vouchers under any IM-managed programme. Opened at COMMITMENT (voucher issued, vendor identified), closed when fully paid (Phase 3+ — when accounts payable is integrated).

```
VND_<vendorCode>   e.g. VND_LESOTHO_AGRO_LTD
  ├── pending_balance:   sum of vouchers committed to this vendor, not yet redeemed
  ├── accrued_payable:   sum of vouchers redeemed, not yet paid
  └── status:            active | suspended | closed
```

**Why this layer exists.** When a vendor sends an invoice or a redemption file, the finance officer needs to confirm "yes, our system shows we committed M75,000 to this vendor across these voucher numbers; we agree we owe M75,000." Without a vendor sub-ledger, that reconciliation requires a join through every voucher.

**Phase 1 status.** Vendor accounts are NOT activated in Phase 1 (subsidy approval ends at PRE_COMMITMENT; no vouchers issued, no vendors paid). The sub-ledger structure is defined here so the Phase 3 IM rollout has a target shape to land into.

### 2.5 Account naming conventions

| Layer | Code prefix | Example |
|---|---|---|
| Envelope | `ENV_` | `ENV_PRG_2025_001_FY2526` |
| Source contribution | `SRC_` | `SRC_GOL_PRG_2025_002_FY2526` |
| Beneficiary sub-ledger | `BNF_` | `BNF_29e6581a-092a-…` |
| Vendor sub-ledger | `VND_` | `VND_LESOTHO_AGRO_LTD` |

All UPPER_SNAKE_CASE. Subaccounts are referenced with a dot suffix (`ENV_PRG_2025_001_FY2526.RESERVED`) in transactions and reports.

---

## 3. Transaction catalogue

A **transaction** is a discrete business event that posts one or more **journal entries**. Each journal entry has a *debit account*, a *credit account*, an *amount*, and metadata (actor, source document, rule version, narration). Every transaction is a balanced posting: total debits equals total credits.

For the budget engine, "debit" and "credit" follow the public-sector commitment-funnel convention: a debit increases an account that holds an obligation, a credit decreases it (and vice-versa for accounts that hold availability). Within an envelope's six subaccounts, money flows by debit-the-target / credit-the-source.

The catalogue below is exhaustive for Phase 1 + Phase 3 IM. Each row shows the trigger, the journal entries (envelope-level), the proration rule for source contributions, and the sub-ledger effect.

### 3.1 ALLOCATION

**Trigger.** Budget admin initialises an envelope at programme launch, OR a top-up event during the cycle adds funding.

**Authority.** `budget_authorisation` scope; default = `MOA_BUDGET_ADMIN` + `MOA_FINANCE_DIRECTOR` (four-eyes).

**Envelope journal:**

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `ENV_X.ALLOCATED` | (external — Treasury authority) | full amount |
| 2 | `ENV_X.AVAILABLE` | (external — Treasury authority) | full amount |

The pair of debits represents "this envelope is now authorised to spend M1,200,000 AND has M1,200,000 of unencumbered budget capacity to do so." The credit side is conceptual (the Treasury's grant of authority); the system records it as a dummy `EXTERNAL_TREASURY_AUTHORITY` account that exists only as a balancing counterparty.

**Source contribution journal.** For each (envelope × source) configured at launch, post the source's share to its own contribution account in proportion. Sum of source `ALLOCATED` and sum of source `AVAILABLE` must equal envelope `ALLOCATED` and `AVAILABLE` respectively.

**Sub-ledger.** None.

### 3.2 RESERVATION

**Trigger.** Citizen submits an application that passes minimum field validation. Auto-fired by `BUDGET_RESERVE_ON_SUBMIT` mm_action.

**Authority.** Automatic — the system fires this on submission. Audit attribution is the citizen's user-id (or `system` if anonymous submission via API).

**Envelope journal:**

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `ENV_X.RESERVED` | `ENV_X.AVAILABLE` | per `BENEFIT_AMOUNT_*` rule |

A soft hold. AVAILABLE drops, RESERVED rises by the same amount.

**Source contribution journal.** Pro-rate the amount to each source by its allocated share at the envelope.

**Sub-ledger.** None — beneficiary sub-ledger is not yet opened (we don't know if this applicant will be approved).

**Failure modes.**

- **Insufficient AVAILABLE.** The reservation fails — the engine writes a `RESERVATION_REJECTED` audit event but no journal entries are posted. The citizen's application is still saved (they shouldn't lose data) but flagged as `pending_budget_capacity`. The `budget_overrun_policy` rule decides whether to permit over-reservation in some special cases.
- **Amount formula returns zero or null.** Treated as evaluator error; no posting; application is flagged `pending_eligibility_clarification`.

### 3.3 RELEASE_RESERVATION

**Trigger.** Application is auto-rejected (eligibility failed mandatory) OR citizen withdraws OR application sits in a clarification state past its SLA and times out. Fired by mm_actions like `BUDGET_RELEASE_ON_REJECT`.

**Envelope journal:** reverse of RESERVATION.

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `ENV_X.AVAILABLE` | `ENV_X.RESERVED` | the original amount |

**Sub-ledger.** None.

### 3.4 PRE_COMMITMENT

**Trigger.** Operator approves an application (decision = `approve`, status transitions to `auto_approved` or `approved`). Fired by `BUDGET_PRE_COMMIT_ON_APPROVE`.

**Authority.** Operator's decision authority is gated by ADR-028's `decision_to_status` rules. The budget posting itself is automatic on the operator decision.

**Envelope journal:**

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `ENV_X.PRE_COMMITTED` | `ENV_X.RESERVED` | the original reserved amount |

**Sub-ledger:** **Open beneficiary account.**

```
OPEN BNF_<applicationId>:
  envelope:               ENV_X
  applicant_national_id:  …
  applicant_name:         …
  amount_total:           M2,000
  amount_by_source:       { SRC_GOL: M1,200, SRC_FAO_IFAD: M800 }   (from envelope's source split)
  status:                 open
  pre_committed_at:       2026-05-03T22:14:00Z
  rule_version:           BENEFIT_AMOUNT_PRG_2025_002 v1
  source_application_id:  29e6581a-…
```

**Invariant.** After this transaction: `sum of open BNF balances for envelope X = ENV_X.PRE_COMMITTED + ENV_X.COMMITTED` (the beneficiary control-total identity).

### 3.5 RELEASE_PRE_COMMITMENT

**Trigger.** Supervisor override of an operator's approval, OR cycle cancellation, OR data-integrity failure discovered post-approval.

**Authority.** Stricter than the original approval — `budget_authorisation` scope rule, default = supervisor role with reason note required.

**Envelope journal:** reverse of PRE_COMMITMENT.

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `ENV_X.AVAILABLE` | `ENV_X.PRE_COMMITTED` | beneficiary's balance |

**Sub-ledger:** **Close beneficiary account.** Status → `released`. Closed_at = now. Reason note required.

### 3.6 COMMITMENT *(Phase 3, IM module)*

**Trigger.** IM voucher issued for an approved application. Fired by `BUDGET_COMMIT_ON_VOUCHER_ISSUE`.

**Envelope journal:**

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `ENV_X.COMMITTED` | `ENV_X.PRE_COMMITTED` | voucher face value |

**Beneficiary sub-ledger:** transition `BNF_X` from `pre_committed` to `committed` state; no balance change. Record voucher number and vendor.

**Vendor sub-ledger:** **Open / increment** `VND_<vendor>` pending balance by voucher amount.

**Edge case — partial commitment.** If a beneficiary's pre-committed M2,000 results in two M1,000 vouchers issued separately, each voucher is a separate COMMITMENT transaction. The beneficiary's sub-ledger tracks both; closes only when both are fully expensed.

### 3.7 EXPENSE *(Phase 3, IM module)*

**Trigger.** Vendor redeems a voucher; redemption file processed.

**Envelope journal:**

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `ENV_X.EXPENSED` | `ENV_X.COMMITTED` | redeemed amount |

**Beneficiary sub-ledger:** decrement `BNF_X` balance by the expensed amount. If balance reaches zero, status → `closed`.

**Vendor sub-ledger:** decrement `VND_<vendor>` pending balance, increment `VND_<vendor>` accrued payable by the redeemed amount.

**Edge case — realised differs from committed.** If the voucher was committed at M1,000 but the actual redemption was M950 (price negotiated lower), post the EXPENSE at M950 AND auto-fire an `EXPENSE_ADJUSTMENT` releasing the M50 difference. Documented in §3.10.

### 3.8 RELEASE_COMMITMENT *(Phase 3, IM)*

**Trigger.** Voucher cancelled or expired before redemption.

**Envelope journal:** reverse of COMMITMENT.

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `ENV_X.AVAILABLE` | `ENV_X.COMMITTED` | voucher face value |

(Note: skips PRE_COMMITTED — released directly to AVAILABLE because the sub-ledgers are closed at release.)

**Sub-ledger:** close `BNF_X` (status → `released`); decrement `VND_<vendor>` pending balance.

### 3.9 RECONCILIATION_ADJUSTMENT

**Trigger.** Manual correction posted by budget admin after discovering a discrepancy (e.g., a missed event during a system outage; a duplicate posting that needs reversal).

**Authority.** `budget_authorisation`, strictly four-eyes, mandatory notes field, supervisor sign-off recorded.

**Envelope journal.** Whatever debit/credit pair restores the correct balance — the admin specifies both sides explicitly. Constraint: the posting must balance (debits = credits) and must reference the offending prior transaction by ID (`c_correction_of`).

**Sub-ledger.** May reopen a closed beneficiary or vendor account if the adjustment retroactively affects them; recorded explicitly.

**Audit.** Retained forever; reconciliation reports flag any envelope with non-zero `RECONCILIATION_ADJUSTMENT` totals so external auditors can review the rationale.

### 3.10 EXPENSE_ADJUSTMENT *(Phase 3, IM)*

**Trigger.** Voucher redeemed for an amount different from the committed amount (price negotiation, partial redemption, fraud claim).

**Envelope journal.** Either a top-up (committed M1,000, expensed M1,100 — post extra M100 from AVAILABLE) or a release (committed M1,000, expensed M950 — release M50 to AVAILABLE).

| Direction | Debit | Credit | Amount |
|---|---|---|---|
| Over-redemption | `ENV_X.EXPENSED` | `ENV_X.AVAILABLE` | overage (with `budget_overrun_policy` gate) |
| Under-redemption | `ENV_X.AVAILABLE` | `ENV_X.COMMITTED` | underage |

**Sub-ledger.** Beneficiary balance closed at the expensed amount; vendor accrued payable matches actual.

### 3.11 SOURCE_REALLOCATION

**Trigger.** Multi-source envelope's source split changes mid-cycle (donor changes its share, government takes over a defaulted commitment).

**Authority.** `budget_authorisation` four-eyes, mandatory donor concurrence note.

**Envelope journal.** Zero net at envelope level.

**Source contribution journal:**

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | `SRC_TARGET.<stage>` | `SRC_ORIGINAL.<stage>` | reallocated portion |

The stage matters — reallocation happens within the same funnel stage (you can reallocate a pre-committed amount; you can't "demote" a committed amount back to pre-committed via reallocation, that requires a RELEASE+ALLOCATE pair).

**Beneficiary sub-ledger.** Source split metadata on existing BNF accounts updated to reflect the new proration going forward; historical entries kept for audit.

### 3.12 PROGRAMME_CLOSURE

**Trigger.** Programme cycle ends (acceptance window closed, all commitments resolved).

**Authority.** Budget admin + finance director, end-of-cycle reconciliation report attached.

**Envelope journal.** Zero out remaining AVAILABLE (lapse to next cycle) and freeze the envelope.

| | Debit | Credit | Amount |
|---|---|---|---|
| 1 | (Treasury — lapsed appropriations) | `ENV_X.AVAILABLE` | remaining unspent |
| 2 | (status change) `ENV_X.status` = closed | | |

**Sub-ledger.** All BNF + VND accounts must already be closed; PROGRAMME_CLOSURE is rejected if any are open. The four-eyes review is the forcing function — it can't be done without first reconciling.

**Reporting.** PROGRAMME_CLOSURE event triggers final reports: realised vs original budget per IPSAS 24, source-level utilisation, beneficiary count + amount, voucher redemption rate, lapsed amount.

---

## 4. Invariants — what must always hold

Invariants are constraints the engine MUST enforce and that periodic reconciliation jobs MUST verify. A violated invariant is a class of bug that requires investigation before further postings.

### 4.1 Envelope balance identity

For every envelope at every moment in time:

```
ALLOCATED = AVAILABLE + RESERVED + PRE_COMMITTED + COMMITTED + EXPENSED
```

Implementation note: this is the materialised view's reason for existing. The view derives all six subaccounts from the event ledger; the identity is verifiable by computation, not by trust.

### 4.2 Source contribution closure

For every envelope:

```
ENV_X.<stage> = sum over sources of SRC_<source>_X.<stage>     for each stage in {ALLOCATED, AVAILABLE, RESERVED, PRE_COMMITTED, COMMITTED, EXPENSED}
```

Source contributions sum to the envelope at every stage. Violation indicates a proration bug or a missed source-side journal entry.

### 4.3 Beneficiary sub-ledger closure

For every envelope:

```
ENV_X.PRE_COMMITTED + ENV_X.COMMITTED = sum of open BNF_*.balance where envelope = X
```

The control-total identity. A finance officer querying "what does the system say we owe across all approved applicants for PRG_001?" gets a number that equals the programme's pre-committed + committed total.

### 4.4 Vendor sub-ledger closure *(Phase 3+)*

For every envelope:

```
ENV_X.COMMITTED = sum of open VND_*.pending_balance attributable to envelope X
```

Same control-total discipline at the vendor side. Required for vendor-invoice-vs-system-record reconciliation.

### 4.5 Double-entry invariant

For every envelope-and-source combination, cumulative debits across the lifetime of the envelope equals cumulative credits. (The exception is the conceptual `EXTERNAL_TREASURY_AUTHORITY` account, which exists only to balance ALLOCATION and PROGRAMME_CLOSURE.)

### 4.6 No-negative-balance

The following accounts must never go negative:

- `AVAILABLE` (would mean over-spending the envelope without the operator-overrun rule firing)
- `RESERVED`, `PRE_COMMITTED`, `COMMITTED` at the envelope and source level
- Beneficiary `balance`
- Vendor `pending_balance`

Negative `EXPENSED` or `ALLOCATED` is impossible by construction (the events only add, except via explicit reversal events that themselves balance).

`EXPENSE_ADJUSTMENT` can exceed `COMMITTED` but only if a `budget_overrun_policy` rule has authorised it — otherwise the engine rejects the posting.

### 4.7 Sub-ledger lifecycle ordering

For every BNF account: events on it must occur in stage order (PRE_COMMITTED → COMMITTED → EXPENSED → CLOSED, or PRE_COMMITTED → RELEASED). No skipping, no reverse.

### 4.8 Proration consistency

When an envelope-level transaction prorates to source contributions, the source amounts must sum exactly to the envelope amount (modulo rounding tolerance — see §6.3).

### 4.9 Historical immutability

No event row may be UPDATEd or DELETEd. Errors are corrected only by posting a new event of type `RECONCILIATION_ADJUSTMENT` or `EXPENSE_ADJUSTMENT` that references the offending event's ID.

---

## 5. Worked example — Mants'ali through the full lifecycle

Trace one applicant through every transaction. Programme: PRG_2025_002 Mountain Pulses (60% GOL / 40% FAO/IFAD), envelope M450,000.

### 5.1 t=0 — Envelope launch

Budget admin posts ALLOCATION M450,000 with source split { GOL: 60%, FAO/IFAD: 40% }.

**Postings:**

```
Tx#001  ALLOCATION  M450,000
  ENV_PRG_2025_002_FY2526.ALLOCATED        +450,000  Dr
  ENV_PRG_2025_002_FY2526.AVAILABLE        +450,000  Dr
  EXTERNAL_TREASURY_AUTHORITY              -900,000  Cr
  
  Source proration:
  SRC_GOL_PRG_2025_002.ALLOCATED            +270,000  Dr
  SRC_GOL_PRG_2025_002.AVAILABLE            +270,000  Dr
  SRC_FAO_PRG_2025_002.ALLOCATED            +180,000  Dr
  SRC_FAO_PRG_2025_002.AVAILABLE            +180,000  Dr
  EXTERNAL_TREASURY_AUTHORITY               -900,000  Cr (already balanced above; second entry zeros the source set)
```

Envelope balances after Tx#001:

```
ALLOCATED   =  450,000
AVAILABLE   =  450,000
RESERVED    =        0
PRE_COMMIT  =        0
COMMITTED   =        0
EXPENSED    =        0
                        Identity: 450,000 = 450,000 + 0 + 0 + 0 + 0  ✓
```

### 5.2 t=1 — Mants'ali submits PRG_2025_002

(In the real Mants'ali test scenario she submitted to PRG_001/Block Lowlands; for didactic clarity we use PRG_2025_002 here so the multi-source proration is visible.)

`BUDGET_RESERVE_ON_SUBMIT` fires; `BENEFIT_AMOUNT_PRG_2025_002` resolves to M2,000 (mountain pulse subsidy per beneficiary).

**Postings:**

```
Tx#002  RESERVATION  M2,000   correlation_id=app_<uuid>  source_module=subsidy  actor=mantsali_user
  ENV_PRG_2025_002_FY2526.RESERVED         +2,000  Dr
  ENV_PRG_2025_002_FY2526.AVAILABLE        -2,000  Cr
  
  Source proration (60/40):
  SRC_GOL_PRG_2025_002.RESERVED            +1,200  Dr
  SRC_GOL_PRG_2025_002.AVAILABLE           -1,200  Cr
  SRC_FAO_PRG_2025_002.RESERVED              +800  Dr
  SRC_FAO_PRG_2025_002.AVAILABLE             -800  Cr
```

Envelope balances:

```
ALLOCATED   =  450,000
AVAILABLE   =  448,000
RESERVED    =    2,000
PRE_COMMIT  =        0
COMMITTED   =        0
EXPENSED    =        0     ✓ Identity holds
```

Beneficiary sub-ledger: not yet opened.

### 5.3 t=2 — Operator approves Mants'ali

`BUDGET_PRE_COMMIT_ON_APPROVE` fires.

**Postings:**

```
Tx#003  PRE_COMMITMENT  M2,000  correlation_id=app_<uuid>  actor=operator_thabo
  ENV_PRG_2025_002_FY2526.PRE_COMMITTED    +2,000  Dr
  ENV_PRG_2025_002_FY2526.RESERVED         -2,000  Cr
  
  Source proration (60/40):
  SRC_GOL_PRG_2025_002.PRE_COMMITTED       +1,200  Dr
  SRC_GOL_PRG_2025_002.RESERVED            -1,200  Cr
  SRC_FAO_PRG_2025_002.PRE_COMMITTED         +800  Dr
  SRC_FAO_PRG_2025_002.RESERVED              -800  Cr

OPEN BNF_<applicationId>:
  envelope:               ENV_PRG_2025_002_FY2526
  amount_total:           2,000
  amount_by_source:       { SRC_GOL: 1,200, SRC_FAO_IFAD: 800 }
  status:                 open
  pre_committed_at:       t=2
  rule_version:           BENEFIT_AMOUNT_PRG_2025_002 v1
```

Envelope balances:

```
ALLOCATED   =  450,000
AVAILABLE   =  448,000
RESERVED    =        0
PRE_COMMIT  =    2,000
COMMITTED   =        0
EXPENSED    =        0     ✓ Identity holds

Beneficiary sub-ledger closure:
  ENV.PRE_COMMITTED + ENV.COMMITTED = 2,000
  sum of open BNF balances           = 2,000     ✓
```

### 5.4 t=3 — IM voucher issued *(Phase 3 — illustrative)*

`BUDGET_COMMIT_ON_VOUCHER_ISSUE` fires; voucher V-12345 issued to Mants'ali, redeemable at vendor LESOTHO_AGRO_LTD.

**Postings:**

```
Tx#004  COMMITMENT  M2,000  correlation_id=voucher_V-12345
  ENV_PRG_2025_002_FY2526.COMMITTED        +2,000  Dr
  ENV_PRG_2025_002_FY2526.PRE_COMMITTED    -2,000  Cr
  (source proration mirrors)

BNF_<applicationId>: status → committed; voucher_id added.
OPEN VND_LESOTHO_AGRO_LTD:
  pending_balance += 2,000
  status:           active
```

### 5.5 t=4 — Voucher redeemed

Vendor processes Mants'ali's voucher for M1,950 (M50 less than face — small price adjustment).

**Postings:**

```
Tx#005  EXPENSE  M1,950
  ENV_PRG_2025_002_FY2526.EXPENSED         +1,950  Dr
  ENV_PRG_2025_002_FY2526.COMMITTED        -1,950  Cr
  (source proration mirrors at 60/40 of 1,950)

Tx#006  EXPENSE_ADJUSTMENT  M50  (auto-fired)
  ENV_PRG_2025_002_FY2526.AVAILABLE          +50  Dr
  ENV_PRG_2025_002_FY2526.COMMITTED          -50  Cr
  (the M50 unspent returns to AVAILABLE)

BNF_<applicationId>: balance = 0 → status = closed; closed_at = t=4.
VND_LESOTHO_AGRO_LTD: pending_balance -= 2,000, accrued_payable += 1,950.
```

### 5.6 Final envelope state

```
ALLOCATED   =  450,000
AVAILABLE   =  448,050   (M1,950 spent, M50 returned)
RESERVED    =        0
PRE_COMMIT  =        0
COMMITTED   =        0
EXPENSED    =    1,950
                        Identity: 450,000 = 448,050 + 0 + 0 + 0 + 1,950  ✓

Source breakdown (donor wants this):
  GOL spent:            1,170 (60% of 1,950)
  FAO/IFAD spent:         780 (40% of 1,950)
  Donor utilisation:    0.43% of M180,000   (ready for the donor report)
```

Mants'ali's trace from submission to expensed payment is six journal entries, each balanced, each prorated to sources, each linked to the source-of-truth event in the application or voucher. Any one of them can be quoted to her, to the operator, to the donor, or to the auditor with full attribution.

---

## 6. Adjustment mechanics

### 6.1 Why adjustments break the simple model — and how we restore invariants

The clean model assumes every transaction is correct first time. Reality intervenes:

- A network outage caused a redemption to be missed; we discover three weeks later that vendor V-12345 was redeemed but no expense was posted.
- An operator double-approved (two PRE_COMMITMENTs for the same application).
- A donor concluded their share is now 50/50 not 60/40 mid-cycle.

Each is handled by an explicit adjustment transaction (§3.9, §3.10, §3.11) that posts BALANCED entries to restore invariants.

### 6.2 The "no UPDATE, no DELETE" rule

Every event row is permanent. Adjustments add new events that reference the corrected event by ID. This preserves the audit trail — the auditor can always reconstruct what the system "thought" at any prior moment.

### 6.3 Rounding

All amounts are stored as `NUMERIC(15,2)` (LSL has 2 decimals, Lesotho lisente). Proration to source contributions uses banker's rounding to 2 decimals; the LARGEST source rounds last to absorb the residual. This guarantees source amounts sum exactly to envelope amount.

### 6.4 Reconciliation jobs

A scheduled job runs nightly:

1. For each envelope, verify §4.1 (balance identity).
2. For each envelope-source pair, verify §4.2 (source closure).
3. For each envelope, verify §4.3 (beneficiary closure).
4. For each envelope, verify §4.4 (vendor closure, when Phase 3 active).
5. For each envelope, verify §4.5 (double-entry).
6. For each envelope, verify §4.6 (no-negative-balance).

Any violation triggers an alert AND freezes the affected envelope (`status='frozen'`) until a budget admin reviews. New transactions on a frozen envelope are rejected. This is the IPSAS-style internal control mechanism.

---

## 7. Authority and segregation of duties

The principle: no single user can both authorise and execute a financial action.

| Action | Who can authorise | Who can execute | Notes |
|---|---|---|---|
| Open envelope (ALLOCATION) | MOA_FINANCE_DIRECTOR | MOA_BUDGET_ADMIN | Four-eyes; approval recorded |
| Reserve, Pre-commit (automatic) | n/a | system | Operator's decision_to_status authorises the upstream action; budget posting is automatic |
| Release pre-commitment | MOA_SUPERVISOR | MOA_OPERATOR | Reason note required |
| Issue voucher (COMMITMENT) | MOA_OPERATOR | IM module | Phase 3 |
| Process redemption (EXPENSE) | n/a | IM module | Automatic on voucher redemption file processing |
| Reconciliation adjustment | MOA_FINANCE_DIRECTOR + MOA_BUDGET_ADMIN | MOA_BUDGET_ADMIN | Strict four-eyes; supervisor sign-off on the note |
| Source reallocation | MOA_BUDGET_ADMIN + funder representative | MOA_BUDGET_ADMIN | Donor concurrence note |
| Programme closure | MOA_FINANCE_DIRECTOR | MOA_BUDGET_ADMIN | All sub-ledgers must be closed first |

**How this is implemented.** The `budget_authorisation` mm_determinant scope (per ADR-025) carries one rule per action type. The rule evaluates against `(currentUsername, applicationId, envelope_status, action_type)` and returns TRUE if the actor is permitted. The Budget Engine refuses to post any transaction whose `budget_authorisation` rule evaluates FALSE; refusal is itself an audit row.

Roles are mapped to Joget user groups. Future rule edits add new authority levels without code changes.

---

## 8. Mapping to IPSAS terminology

We adopt the terminology and the discipline; we do not produce statutorily-conformant financial statements.

| Our term | IPSAS term | Reference |
|---|---|---|
| Envelope ALLOCATED | Original (and Final) Budget | IPSAS 24 §14, §15 |
| AVAILABLE | Unencumbered appropriation | Public-sector practice |
| RESERVED | Encumbrance — soft hold | Practice; not formal IPSAS |
| PRE_COMMITTED | Encumbrance — obligation | IPSAS 24 §31 (commitments) |
| COMMITTED | Encumbrance — voucher / earmark | IPSAS 24 §31 |
| EXPENSED | Actual expenditure on a comparable basis | IPSAS 24 §14, §16 |
| Funding source contribution | Revenue (non-exchange transaction) for donor portions | IPSAS 23 §44 |
| Beneficiary sub-ledger | Subsidiary ledger / detail account | General accounting practice |
| Vendor sub-ledger | Accounts payable subsidiary | General accounting practice |
| Reconciliation adjustment | Reclassification or correction | IPSAS 1 §53 (when retrospective) |

**What we don't do (yet).** Generate Statement of Comparison of Budget and Actual Amounts (SCBAA, IPSAS 24 §14). Produce accruals beyond the commitment funnel. Calculate fiduciary ratios. Integrate with national consolidated fund accounting. These are Phase 3+ if and when MoA-Finance asks for the system to feed their statutory reports.

**What we DO support.** Every IPSAS 24 disclosure dimension at the programme level — original budget, final budget, actual on a comparable basis, variance, with explanations from `RECONCILIATION_ADJUSTMENT.notes`. Sufficient for donor reporting and internal audit; falls short of formal IPSAS reporting only on the consolidation side.

---

## 9. Audit trail requirements

Every journal entry must record:

| Field | Purpose | Source |
|---|---|---|
| `transaction_id` | Identifier for cross-referencing | UUID at posting time |
| `transaction_type` | One of the §3 catalogue values | enforced enum |
| `envelope_code` | Which envelope was posted | from action context |
| `account_path` | E.g., `ENV_X.RESERVED`, `SRC_GOL_X.AVAILABLE`, `BNF_<id>` | computed |
| `amount` | The amount of this entry (positive number) | from rule or input |
| `direction` | Debit or Credit | from posting logic |
| `correlation_id` | The application/voucher/redemption it relates to | from upstream module |
| `correlation_type` | Type of correlation source | enforced enum |
| `actor` | User-id who triggered | from session |
| `actor_role` | Effective role at posting time | from auth |
| `authority_basis` | Which `budget_authorisation` rule passed | rule code + version |
| `rule_version` | Version of any amount/proration/auth rule used | rule version table |
| `source_module` | subsidy, im, manual, migration | from action context |
| `posted_at` | Server timestamp | now() |
| `notes` | Free text; required for manual adjustments | from input |
| `correction_of` | If this is an adjustment, the corrected event's ID | nullable |
| `idempotency_key` | Prevents double-post on retry | composed from upstream |

This is what the `app_fd_budget_event` table stores. Reports build on these rows; the materialised view aggregates them; reconciliation jobs verify against them.

---

## 10. Edge cases and standing decisions

### 10.1 Over-reservation

Default: reservation is rejected if it would drive `AVAILABLE` negative. The application is still saved (citizen doesn't lose data) but flagged. A `budget_overrun_policy` rule can override this — typical use case: emergency drought relief programme allows overrun by up to 5%, with director sign-off.

### 10.2 Mid-cycle re-allocation between programmes

Out of scope for Phase 1. Each envelope is independent. A future ENVELOPE_REALLOCATION transaction type can be added if MoA needs cross-programme moves; methodologically it's a paired CLOSURE and ALLOCATION at smaller amounts.

### 10.3 Programme cancellation

A `PROGRAMME_CANCELLATION` transaction releases all open RESERVED + PRE_COMMITTED + COMMITTED to AVAILABLE, then PROGRAMME_CLOSURE finalises. All affected beneficiary and vendor sub-ledgers close with reason `programme_cancelled`. Citizens receive notification through the standard `mm_action.kind=workflow_dispatch` mechanism (notifications are out of Budget Engine scope but triggered by the cancellation event).

### 10.4 Voucher expiry

A voucher that's not redeemed within its window auto-fires RELEASE_COMMITMENT. Beneficiary sub-ledger closes with reason `voucher_expired`. The applicant may re-apply in the next cycle (subject to D-003 duplicate-check, naturally allowed across cycles since the programme code differs — see ADR D-003 / D32).

### 10.5 Applicant withdrawal

Pre-pre-commitment: simple RELEASE_RESERVATION. Post-pre-commitment: RELEASE_PRE_COMMITMENT (supervisor authorisation). Post-commitment: RELEASE_COMMITMENT (additionally, the issued voucher must be cancelled — a separate IM-module transaction).

### 10.6 Duplicate detection (D-003 cross-reference)

The applicability rule `DET_NO_DUPLICATE_PRG_xxx` prevents same-programme duplicate applications, blocking RESERVATION at the eligibility evaluation step. The Budget Engine never sees a duplicate reservation; this is upstream-correct.

### 10.7 Multi-programme applicant

An applicant can apply to multiple programmes. Each is a separate beneficiary sub-ledger account (different `BNF_<applicationId>`); no aggregation across programmes. The system has no "total amount owed to applicant X across all programmes" concept — that's a reporting query, not an account.

### 10.8 Currency and rounding

All amounts in LSL with 2-decimal precision (lisente). Source proration uses banker's rounding per §6.3. No FX, no multi-currency.

### 10.9 Fiscal-year boundary

Envelopes are scoped to one fiscal year. An open programme that crosses a fiscal-year boundary requires a separate envelope per year (`ENV_PRG_2025_001_FY2526` and `ENV_PRG_2025_001_FY2627`); transactions in each fiscal year post to the year-appropriate envelope. This matches IPSAS 24's annual reporting frame.

---

## 11. Amendments to existing ADRs and SAD

The existing Budget Engine design (ADR-022 through ADR-026 plus `docs/architecture/architecture/components/budget-engine.md`) is largely consistent with this methodology, but needs the following amendments:

### 11.1 budget-engine.md SAD

- **§5.2 Storage schema.** Add `budget_envelope_source` table (one row per envelope-source contribution): `c_envelope_code`, `c_source_code`, `c_source_name`, `c_share_percent`, `c_source_donor_class` (government | bilateral_donor | multilateral_donor | private), `c_status`. Update `budget_event` to add `c_account_path` (full account dotted path), `c_direction` (debit | credit), `c_authority_basis`, `c_idempotency_key`, `c_correlation_subtype`.
- **§5.2 Sub-ledger storage.** Add `beneficiary_subledger` (one row per BNF_X) and `vendor_subledger` (one row per VND_X) tables. Define their schemas per §2.3 and §2.4 of this document.
- **§5.2 budget_projection.** Update materialised view to add per-source aggregation columns and beneficiary control-total verification.
- **§5.3 BudgetEventListener.** Add proration logic (split envelope amount across source contributions) and sub-ledger maintenance (open BNF on PRE_COMMITMENT, close on EXPENSE/RELEASE).
- **§5.4 Operator UX.** Add "Donor Report" surface (filter by funding source); add "Beneficiary Account Lookup" admin tool ("show me the system's record for this applicant"); add "Vendor Reconciliation" surface (Phase 3+).

### 11.2 ADR-022 (Budget Engine as separate module)

Add to *Decision*: the engine implements the methodology in `budget-accounting-methodology.md` rather than ad-hoc state-machine logic. The methodology is the contract; the engine is its faithful renderer.

### 11.3 ADR-023 (Commitment funnel state model)

Reframe from "states of an application's budget impact" to "subaccounts within a programme envelope". The mathematical content is the same; the framing is now accounting-grounded. Add §2.2 (source contributions) and §2.3 (sub-ledgers) as cross-cutting concerns that apply at every stage.

### 11.4 ADR-024 (mm_action.kind=budget_event integration)

Extend the trigger-evaluation logic (§3 of the ADR): after computing the envelope-level amount via `BENEFIT_AMOUNT_*` rule, the listener now ALSO derives the per-source proration from the envelope's `budget_envelope_source` rows AND opens/closes the beneficiary sub-ledger as appropriate. The original ADR doesn't mention sub-ledgers; amendment makes them part of the listener's contract.

### 11.5 ADR-025 (rule-based budget governance)

No changes. The six new scopes (`budget_amount`, `budget_tolerance`, `budget_overrun_policy`, `budget_authorisation`, `programme_launch_gate`, `sla_decision`) are exactly what this methodology needs. Add to ADR-025 §3 a subscope listing for `budget_authorisation` covering each transaction type from §3.1-§3.12 of this document.

### 11.6 ADR-026 (Rule-to-SQL compiler)

No changes. The compiler's scope is unchanged: it accelerates batch eligibility counts for the Cost Estimation Service, not transaction posting.

### 11.7 Decision log

Add D34 — "L3-1 Budget Engine adopts proper public-sector fund accounting (multi-fund, beneficiary + vendor sub-ledgers, IPSAS-aligned terminology); methodology document is the contract for the implementation."

---

## 12. Open questions for review

Before implementation begins, confirm these with the project owner:

1. **Funding-source granularity.** §2.2 assumes one row per (envelope × source). Is "source" granular enough at the level of "Government of Lesotho", "FAO", "World Bank — SADP-II"? Or do we need finer structure (specific grant agreement, specific donor budget line)? Default assumption: program-level source naming is sufficient for Phase 1.

2. **Beneficiary identity.** §2.3 keys the BNF account on `applicationId` (UUID). Should we also key on `national_id` for the cross-programme aggregation case, even though we don't aggregate across programmes (§10.7)? Default: `applicationId` is sufficient; cross-programme is a reporting query.

3. **Vendor identity.** §2.4 assumes vendors have stable codes (e.g., `LESOTHO_AGRO_LTD`). Where do these codes come from — IM-module's vendor master? National supplier registry? Phase 3+ decision; placeholder for now.

4. **Authority levels.** §7 lists role names (`MOA_BUDGET_ADMIN`, `MOA_FINANCE_DIRECTOR`, etc.). Are these the actual MoA org roles or placeholders? Probably need a project sign-off before encoding into `budget_authorisation` rules.

5. **Reconciliation cadence.** §6.4 specifies nightly. Confirm that's consistent with operations expectations; for high-volume periods (drought emergency window) we may want hourly or per-event verification.

6. **IPSAS reporting endpoint.** §8 says we don't generate statutory IPSAS statements. If the auditor-general DOES want them, the engine has the data — but a reporting layer needs adding. Phase 3+ if signalled.

7. **EXTERNAL_TREASURY_AUTHORITY account.** §3.1 introduces a conceptual external counterparty for ALLOCATION balancing. Is this acceptable, or should the system model a real Treasury Authority account (with its own movements tracked)? Default: conceptual is fine; modelling the Treasury is outside this system's scope (§Out of scope).

---

*Document end. Next step: review by Aare; once accepted, becomes the contract for L3-1 1A onwards. Implementation diverging from this document requires methodology amendment first (D34-style decision-log entry), not a silent code-side workaround.*
