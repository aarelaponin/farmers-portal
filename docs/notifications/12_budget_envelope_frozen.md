# 12 — budget_envelope_frozen

**Recipient:** finance officer + district supervisors of districts where this programme is active
**Trigger:** envelope-freeze event (already exists in the threshold-automation flow — fires when utilisation crosses 90 %, or on manual freeze)
**Fires once per:** freeze event
**Priority:** transactional (decision-making — operators must know that new approvals are blocked)

---

## Subject

```
Budget envelope FROZEN — no new approvals for #form.spProgram.programme_name#
```

## Body — plaintext

```
Hello #user.firstName#,

The budget envelope for #form.spProgram.programme_name# has been frozen.
No new applications can be approved for this programme until the
envelope is unfrozen or topped up.

Programme:              #form.spProgram.programme_name#
Envelope:               #form.budgetEnvelope.envelope_code#
Frozen at:              #date.now#
Reason:                 #form.budgetEnvelope.freeze_reason#
Allocated total:        LSL #form.budgetEnvelope.amount_allocated#
Committed to vouchers:  LSL #form.budgetEnvelope.amount_committed#
Expensed:               LSL #form.budgetEnvelope.amount_expensed#
Utilisation:            #form.budgetEnvelope.utilisation_pct# %

What this means for citizens:

- Existing vouchers continue to work normally; redemptions still process.
- New applications submitted against this programme will be auto-rejected
  with a "programme budget exhausted" reason until the freeze is lifted.

What this means for operators:

- Field officers should be informed not to start new applications under
  this programme.
- Pending applications already in the operator inbox can still be approved
  IF the available balance covers their committed amount; otherwise the
  approval will fail with a budget-exceeded error.

What to do next:

If the freeze is expected (programme is fully subscribed and you intended
to close it), no further action — the freeze is the correct outcome.

If you want to keep the programme open, you have three options:

1. Top up the envelope via Budget → Manual adjustments (maker-checker).
2. Re-allocate from another programme's available balance.
3. Roll forward unused balance from a prior cycle.

Open the budget dashboard:
#form.md03District.portal_url#/budget-envelope-state

— Farmers Portal automation

Do not reply to this message.
```

## Body — HTML

`header danger` (red — this is an action-required alert). Same KPI box as
template 11. Adds a prominent "Programme is now CLOSED to new approvals"
banner at the top.

## Variables used

| Variable | Source | Notes |
|---|---|---|
| `#form.budgetEnvelope.freeze_reason#` | envelope row | Auto-set by threshold automation = "Auto-frozen at 90% utilisation"; or operator-provided text on manual freeze |
| (others same as template 11) | | |

## Implementation note

The freeze event itself already exists. What's NEW is:

1. After the freeze is committed, fetch the recipient list:
   - All users with `role_finance_officer`.
   - All users with `role_district_supervisor` who have at least one application currently in the operator inbox for this programme. (For dev: skip the filter; CC every supervisor.)
2. Fire EmailTool with this template.
3. Mark the envelope's `frozen_alert_sent = true` to prevent re-fires.

## Acceptance test

1. Push an envelope's utilisation to 91 % (manual seed of a large committed amount).
2. Run the threshold-detection task.
3. Envelope flips to `status = frozen`.
4. Email arrives at `aarelaponin@gmail.com` (representing both the finance recipient and the supervisor recipient — DEV-override sends them all here).
5. Subject contains "FROZEN" and the programme name.
6. Body explains the operator-side and citizen-side implications.
7. Submit a new test application against the same programme; verify auto-rejection occurs (separate test from email content).
