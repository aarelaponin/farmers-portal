# ADR-014 — Conditional UI: server-side authoritative + client-side simple-equality toggle

| | |
|---|---|
| Status | Proposed (already implemented in build-046 + build-048) |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | Kernel SAD §6.3–§6.4; framework SAD §4 (rule grammar); RegBB spec §6.4. |

## Context

RegBB §6.4 calls for conditional UI: fields that show/hide or become required/optional based on the runtime value of other fields. Examples: "Cooperative Name" only shows when "Member of a Block Farming Cooperative" is "yes"; a parcel field becomes required when the programme requires parcel data.

Two natural implementation paths existed:

- **Pure server-side.** `MetaScreenElement` evaluates the determinant on each render; renders `HiddenField` if FALSE, visible widget if TRUE. Authoritative on save (the server always re-evaluates). But the citizen has to click Next/Previous (or Save) to see the field hide/show — there's no in-page reactivity.
- **Pure client-side.** A piece of JavaScript listens for changes to dependency fields and toggles dependent fields directly. Snappy UX. But the client doesn't have the rule engine; either we ship a JS rule interpreter (expensive) or we restrict to simple cases the JS can handle without a full evaluator. And the client-side toggle is bypass-able (anyone with browser DevTools can flip the field back on); not authoritative.

Either alone is a regression vs. native Joget UX (which has both).

## Decision

Both paths, layered:

- **Server-side path (authoritative).** `MetaScreenElement.synthesiseChildren` evaluates `mm_field.visibilityDeterminantId` and `requirednessDeterminantId` against the current `EvalContext.data` (request params + load-binder data). For visibility=FALSE, synthesises `HiddenField` instead of the visible widget — preserves round-trip of the value. For requiredness=TRUE, sets validator's `mandatory=true`. **This runs on every save and every full re-render. Authoritative.**

- **Client-side path (snappy UX).** `MetaWizardElement.emitConditionalUiJs` walks every `mm_field` with a visibility determinant whose rule is **simple equality** (e.g. `block_farming_member == 'yes'`). For each, emits a small `<script>` that listens for `change` / `input` events at the wizard root and toggles the dependent field's `.form-cell` `display: none` / `''` based on the dependency's current value. Complex rules (`AND`, `OR`, `IN`, numeric comparisons, `$registry.*` references) are intentionally NOT handled client-side — the server-side path covers them on save+reload.

The client-side script's pattern-match for "simple equality" is a regex against the rule source: `^\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*==\s*['"]([^'"]*)['"]\s*$`. Anything that doesn't match is silently skipped on the client; it still works on the server.

## Consequences

**Positive:**

- UX parity with native Joget: simple conditional fields toggle instantly (~ no perceived latency); complex rules degrade gracefully to save+reload (still correct, just less snappy).
- Server-side path is authoritative: even if the client-side script is bypassed or fails, save-time evaluation enforces the rule. Spec §6.4 conformance is real, not just a UX claim.
- The pattern (~25 LoC of client JS) covers the majority of real-world conditional-UI cases. We measured against the four 2025 programmes: 100% of conditional rules are simple equality.

**Negative:**

- Two execution paths means two places to maintain. A future change to the visibility semantic (e.g. "fail-closed by default" instead of "fail-open") needs updates in both paths.
- The pattern-match for "simple equality" is regex-based; a rule like `block_farming_member==' yes '` (with whitespace around the value) won't match cleanly. Mitigation: pattern is forgiving on whitespace; falls back to server-side path when in doubt.
- A "complex" rule (e.g. `district == 'mafeteng' AND block_farming_member == 'yes'`) doesn't get the snappy toggle. Acceptable today; future enhancement could either expand the regex or — more rigorously — port the closed-twenty grammar to JavaScript and run any rule client-side. Deferred.

**Trade-off named:** UX parity won against architectural elegance. The "pure client-side, port the engine to JS" alternative is cleaner architecturally but doubles the maintenance burden (two evaluators to keep in sync) and adds significant new code surface. Layered is messier but covers the cases we have, with a clean fallback.

**Documents updated:** Kernel SAD §6.3 (server path), §6.4 (client path); framework SAD §4 (rule grammar covers both); RegBB conformance checklist confirms §6.4 compliance.
