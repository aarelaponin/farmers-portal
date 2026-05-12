# 11 — budget_envelope_75pct

**Recipient:** finance officer (every user with `role_finance_officer`)
**Trigger:** hourly scheduled task — find envelopes whose utilisation crosses 75 % since the last run, and which haven't already triggered a 75 % alert this season
**Fires once per:** envelope crossing 75 %
**Priority:** routine alert (hourly cadence is fine)

---

## Subject

```
Budget alert: #form.spProgram.programme_name# at #form.budgetEnvelope.utilisation_pct#%
```

## Body — plaintext

```
Hello #user.firstName#,

Programme budget envelope crossed 75 % utilisation:

Programme:              #form.spProgram.programme_name#
Envelope:               #form.budgetEnvelope.envelope_code#
Allocated total:        LSL #form.budgetEnvelope.amount_allocated#
Committed to vouchers:  LSL #form.budgetEnvelope.amount_committed#
Expensed (redeemed):    LSL #form.budgetEnvelope.amount_expensed#
Available for new:      LSL #form.budgetEnvelope.amount_available#
Utilisation:            #form.budgetEnvelope.utilisation_pct# %

Recommended actions:

1. Open the budget envelope dashboard for a 60-day projection of run-rate.
2. If projection shows envelope exhausting before #form.spProgram.cycle_end#,
   coordinate with district supervisors to slow new approvals OR initiate
   an envelope top-up via the manual-adjustments workflow.
3. At 90 % utilisation the envelope automatically freezes (no new
   approvals). At that point the budget_envelope_frozen alert fires.

Open the dashboard:
#form.md03District.portal_url#/budget-envelope-state

— Farmers Portal automation

Do not reply to this message.
```

## Body — HTML

`header warning`. KPI box shows the four amounts (allocated, committed,
expensed, available) in a 4-column grid for quick scan. Utilisation
percentage shown big and orange. Action button to dashboard.

## Variables used

| Variable | Source | Notes |
|---|---|---|
| `#form.budgetEnvelope.envelope_code#` | budget envelope row | ENV-XXX-YY |
| `#form.budgetEnvelope.amount_allocated#` | budget envelope row | LSL with thousand separators |
| `#form.budgetEnvelope.amount_committed#` | budget engine derived | sum of voucher COMMITMENT events not yet released |
| `#form.budgetEnvelope.amount_expensed#` | budget engine derived | sum of voucher EXPENSE events |
| `#form.budgetEnvelope.amount_available#` | budget engine derived | allocated − committed − expensed |
| `#form.budgetEnvelope.utilisation_pct#` | budget engine derived | (committed + expensed) / allocated × 100 |
| `#form.spProgram.cycle_end#` | programme registry | YYYY-MM-DD |
| `#user.firstName#` | logged-in user | each finance officer's first name |

## Implementation note

This is the alert layer that already exists from L3-1 Slice "Threshold
automation + envelope freeze" (task #141). That work shipped the
threshold-detection scheduled task and the envelope-freeze logic. What's
NEW for W2.4 is adding the EmailTool step at the right point in the
existing flow:

1. Threshold task detects envelope > 75 % AND `seventy_five_pct_alerted` flag is false.
2. **NEW:** dispatch this email template to all `role_finance_officer` users.
3. Set `seventy_five_pct_alerted = true` on the envelope row to avoid re-firing.

## Acceptance test

1. Manually adjust an envelope's `amount_committed` to push utilisation to 76 %.
2. Run the threshold-detection task.
3. Email arrives at `aarelaponin@gmail.com`.
4. Subject contains the programme name and percentage.
5. KPI numbers are correct.
6. Re-run the task; the email does NOT fire a second time (idempotency).
