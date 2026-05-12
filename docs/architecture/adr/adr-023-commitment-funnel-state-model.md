# ADR-023 — Commitment funnel state model

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | ADR-022 (budget engine boundary), ADR-024 (event integration), component SAD `_design/architecture/components/budget-engine.md` §4.1–§4.2; **methodology** `_design/architecture/components/budget-accounting-methodology.md` §2 + §3 (D34, May 2026). |

## D34 reframing (May 2026)

Originally framed as "states of an application's budget impact" — i.e. an application enters RESERVATION, advances to PRE_COMMITMENT, etc. Per the budget-accounting methodology this is reframed as **"subaccounts within a programme envelope"**: the envelope IS an account, the funnel stages are its subaccounts, and individual applications drive transactions that move money between those subaccounts. Mathematically identical; framing is now accounting-grounded. Two cross-cutting layers added at every stage:

- **Source contributions** — when an envelope has multiple funding sources (e.g., 60% government / 40% donor), every funnel-stage transaction is mirrored prorationally into source-contribution accounts. Donor reports + treasury reports both fall out of the same ledger.
- **Sub-ledgers** — at PRE_COMMITMENT, a beneficiary sub-ledger account opens and tracks what's owed to that specific applicant; at COMMITMENT (Phase 3+), a vendor sub-ledger account opens for the supplier the voucher targets. Sub-ledgers close when balances reach zero or on RELEASE. Control-total invariants tie them back to envelope subaccounts.

See `budget-accounting-methodology.md` §2 (chart of accounts), §3 (transaction catalogue), §4 (invariants) for the full specification.

## Context

A budget envelope passes through several states between "money allocated" and "money spent". Naming and ordering these states matters because they're visible in every report, every audit row, every operator UI. An earlier draft used "pre-pre-commitment" for the soft-hold stage at application receipt — clear evidence the term was wrong (the awkward double prefix).

Standard public-sector accounting (IPSAS-aligned) has stable terms for parts of this funnel:

- *Encumbrance / obligation* — when a legal commitment is created (we owe somebody this).
- *Expenditure* — when actual money goes out.

But the Lesotho subsidy system has TWO commitment levels: programme-level (operator approves the application) and asset-level (IM voucher issued). Standard IPSAS doesn't distinguish; the system needs to.

## Decision

The funnel has four forward stages and matching reverse events:

| Stage | Trigger | Effect on envelope |
|---|---|---|
| **Reservation** | Citizen submits the *finalised* application (status transitions to a non-draft value) | Soft hold |
| **Pre-commitment** | Operator approves | Hard obligation; programme-level commitment |
| **Commitment** | IM voucher issued | Asset-level earmark; specific physical claim with specific input × price |
| **Expense** | IM voucher redeemed | Actual money out |

Plus inverses: `RELEASE_RESERVATION` (rejection / withdrawal), `RELEASE_PRE_COMMITMENT` (rare; e.g. supervisor override of an approval), `RELEASE_COMMITMENT` (voucher cancelled / expired unredeemed), `EXPENSE_ADJUSTMENT` (realised redemption amount differs from committed amount).

The terminology choices:

- "Reservation" is standard public-finance for soft holds. Pairs with verb "release."
- "Pre-commitment" is kept (it's the user's term and is well-understood as "before the asset-level commitment").
- "Commitment" specifically means the IM-voucher-issuance act. This is more granular than IPSAS but matches the system's actual mechanism.
- "Expense" is the simplest form of "expenditure"; matches operator vocabulary.

The state of an envelope at any moment is the sum of its events. The materialised view `budget_projection` derives `(allocated, reserved, pre_committed, committed, expensed, available)` from the append-only ledger.

## Consequences

**Positive:**

- Standard-aligned terminology (Reservation + Expense from IPSAS; Pre-commitment + Commitment specific to this system's two commitment layers).
- The four-stage funnel is precise enough to support per-stage reporting (utilisation, burn rate, unredeemed-tail).
- Append-only ledger means the funnel is reconstructible at any point in history — pre-commitment as of last quarter is just "events up to that date".
- Inverse events make the model symmetric — rejection releases reservation; expiry releases commitment.

**Negative:**

- Four stages is more than IPSAS's two-stage encumbrance/expenditure; introduces vocabulary that isn't universally familiar.
- The Reservation/Pre-commitment distinction needs operator training: the operator's *view* of "available" budget should subtract reservations, not just pre-commitments. The dashboard must show all four stages plainly.

**Trade-off named:** IPSAS-alignment vs. mechanism-fidelity. We chose mechanism-fidelity (four stages matching the real lifecycle) because abstracting Pre-commitment + Commitment into a single IPSAS-style "obligation" hides the IM voucher transition that's operationally meaningful. The cost is a small vocabulary delta; the benefit is reports that match what's actually happening.

**Documents updated:** Budget Engine SAD §4.1–§4.2, §6, §12 (glossary); subsidy + IM SAD updates name the events as triggers.
