# 10 — voucher_cancelled

**Recipient:** applicant
**Trigger:** Voucher cancellation operator action (the operator-side "Cancel voucher" button — Slice 10 flow)
**Fires once per:** cancellation
**Priority:** transactional (operator-initiated; citizen needs to know quickly)

---

## Subject

```
Your voucher has been cancelled — #form.imVoucher.voucher_code#
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

Your subsidy voucher has been cancelled by Ministry staff.

Voucher code:        #form.imVoucher.voucher_code#
Programme:           #form.spProgram.programme_name#
Cancelled on:        #date.now#
Reason:              #form.imVoucher.cancellation_reason#

What this means:

The voucher is no longer valid. If you visit a distribution point with
this voucher code, the system will refuse the redemption.

Why this happened:

Vouchers are normally cancelled when:
- An applicant has withdrawn from the programme.
- A duplicate voucher was issued in error and only one is needed.
- A data correction is being made (e.g. voucher issued under the wrong
  programme; a replacement will be issued shortly).

#form.imVoucher.cancellation_reason#

If you believe this cancellation is in error, contact your district office
within 7 days:

#form.md03District.district_name# District Office
Phone: #form.md03District.phone#

— Ministry of Agriculture, Food Security and Nutrition

Do not reply to this message.
```

## Body — HTML

Same shape; `header warning`. The reason field is shown in a quote-style
block (same pattern as `04_application_rejected`).

## Variables used

| Variable | Source | Notes |
|---|---|---|
| `#form.imVoucher.cancellation_reason#` | voucher row | Captured by the operator at cancel time. Plain-language; never an internal code. |

## Acceptance test

1. Issue a voucher (template 06 fires).
2. As an operator, cancel it via the operator UI with a plain-language reason ("Replaced by VCH-... — wrong programme assigned in error").
3. Email arrives at `aarelaponin@gmail.com`.
4. Cancellation reason from operator appears verbatim in the body.
