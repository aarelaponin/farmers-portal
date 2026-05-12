# 09 — voucher_expired

**Recipient:** applicant
**Trigger:** `VoucherExpirySweeper` post-expiry hook (already exists from IM Slice 9 — fires when sweeper marks voucher as `expired`)
**Fires once per:** voucher
**Priority:** routine notification (not transactional — citizen can't act on it anymore)

---

## Subject

```
Your voucher has expired — #form.imVoucher.voucher_code#
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

This is a notification that your subsidy voucher has expired without
being redeemed (or fully redeemed).

Voucher code:           #form.imVoucher.voucher_code#
Programme:              #form.spProgram.programme_name#
Issued on:              #form.imVoucher.issued_date#
Expired on:             #form.imVoucher.expiry_date#
Unredeemed quantity:    #form.imVoucher.remaining_quantity# of #form.imVoucher.benefit_description#

What this means:

The unredeemed portion of your voucher has been returned to the programme
budget for redistribution. You will not be charged anything; nothing was
taken from you. The system simply withdrew the unused benefit.

Why we send this:

So that if you believed the voucher had been redeemed (for example, you
visited a distribution point and were turned away, or there was a system
error), you can contact your district office promptly.

If you did try to redeem and were unable to, contact your district office
within 14 days. We can investigate and, where appropriate, re-issue:

#form.md03District.district_name# District Office
Phone: #form.md03District.phone#

If you simply did not need the benefit this season, no action is needed.

— Ministry of Agriculture, Food Security and Nutrition

Do not reply to this message.
```

## Body — HTML

Same shape as 08, with `header danger` colour and the alert content updated to "expired" wording.

## Variables used

Same as templates 06-08, all from imVoucher / spProgram / md03District.

## Acceptance test

1. Set a voucher's `expiry_date` to yesterday (manual seed); leave it unredeemed.
2. Run the VoucherExpirySweeper manually.
3. Voucher transitions to `expired` status.
4. Email arrives at `aarelaponin@gmail.com`.
5. Subject is calmly worded ("has expired" — no exclamation, no alarm).
6. Body explains what happened and offers a 14-day window to dispute.
