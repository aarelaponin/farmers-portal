# 08 — voucher_expiring_7d

**Recipient:** applicant
**Trigger:** new daily scheduled task — find vouchers where `expiry_date = today + 7` AND status IN ('active', 'partially_redeemed')
**Fires once per:** voucher (7 days before expiry)
**Priority:** routine reminder (does not have to arrive instantly, but must arrive same day)

---

## Subject

```
Reminder — your voucher expires in 7 days (#form.imVoucher.voucher_code#)
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

This is a friendly reminder. Your subsidy voucher expires in 7 days.

Voucher code:        #form.imVoucher.voucher_code#
Programme:           #form.spProgram.programme_name#
Expires on:          #form.imVoucher.expiry_date#
Remaining benefit:   #form.imVoucher.remaining_quantity# of #form.imVoucher.benefit_description#

If you do not redeem before the expiry date, this voucher will lapse and
the benefit returns to the programme budget.

To redeem:

1. Visit any approved distribution point in your district. The nearest
   point to your registered village is:
   #form.md37CollectionPoint.nearest_point_name# — #form.md37CollectionPoint.nearest_point_address#
2. Bring your National ID and the voucher code above.
3. The distribution-point operator will dispense your benefit and you
   will receive a confirmation email.

If you have already redeemed this voucher and believe this reminder is
in error, please ignore this message. The system fires reminders 7 days
before expiry regardless of remaining quantity.

If you cannot reach a distribution point before the expiry date, contact
your district office to discuss alternatives:

#form.md03District.district_name# District Office
Phone: #form.md03District.phone#

— Ministry of Agriculture, Food Security and Nutrition

Do not reply to this message.
```

## Body — HTML

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <!-- inline _shared_styles.html here -->
</head>
<body>
  <div class="header warning">
    <h2 style="margin:0;">Voucher expires in 7 days</h2>
  </div>
  <div class="content">
    <p>Hello <strong>#form.farmerBasicInfo.first_name#</strong>,</p>

    <p>This is a friendly reminder. Your subsidy voucher expires in 7 days.</p>

    <div class="ref-box" style="border-left-color:#ef6c00;">
      <dl style="margin:0;">
        <dt>Voucher code</dt>
        <dd><strong style="font-family:monospace;">#form.imVoucher.voucher_code#</strong></dd>
        <dt>Programme</dt>
        <dd>#form.spProgram.programme_name#</dd>
        <dt>Expires on</dt>
        <dd><strong style="color:#c62828;">#form.imVoucher.expiry_date#</strong></dd>
        <dt>Remaining benefit</dt>
        <dd>#form.imVoucher.remaining_quantity# of #form.imVoucher.benefit_description#</dd>
      </dl>
    </div>

    <div class="alert">
      If you do not redeem before the expiry date, this voucher will lapse
      and the benefit returns to the programme budget.
    </div>

    <p><strong>To redeem:</strong></p>
    <ol>
      <li>Visit any approved distribution point in your district. The nearest point to your registered village is:<br>
          <strong>#form.md37CollectionPoint.nearest_point_name#</strong><br>
          <span style="font-size:0.9em;color:#6c757d;">#form.md37CollectionPoint.nearest_point_address#</span></li>
      <li>Bring your National ID and the voucher code above.</li>
      <li>The distribution-point operator will dispense your benefit and you will receive a confirmation email.</li>
    </ol>

    <p style="font-size:0.9em;color:#6c757d;">
      If you have already redeemed this voucher and believe this reminder
      is in error, please ignore this message. The system fires reminders
      7 days before expiry regardless of remaining quantity.
    </p>

    <p>If you cannot reach a distribution point before the expiry date, contact your district office to discuss alternatives:<br>
       <strong>#form.md03District.district_name# District Office</strong><br>
       Phone: #form.md03District.phone#</p>

    <p>— Ministry of Agriculture, Food Security and Nutrition</p>

    <div class="footer">Do not reply to this message.</div>
  </div>
</body>
</html>
```

## Variables used

| Variable | Source | Notes |
|---|---|---|
| `#form.md37CollectionPoint.nearest_point_name#` | derived | Computed by the scheduled task — nearest collection point to farmer's registered village. Geo-distance ranking using the centroid coordinates loaded in IM Slice 2. |
| `#form.md37CollectionPoint.nearest_point_address#` | MD.37 lookup | |
| (others same as templates 06-07) | | |

## Implementation note

The "nearest distribution point" computation is non-trivial:

1. Read farmer's registered village (or parcel centroid as a fallback).
2. Query `app_fd_md37CollectionPoint` for points active in the farmer's district.
3. Rank by Haversine distance.
4. Pick the closest.

For the first cut: skip the geo-distance, just pick the **first active
collection point in the farmer's district**. The "nearest" sales pitch is
deferred to W3 SMS work where it matters more (160-char SMS doesn't have
room for "or visit any other point").

## Acceptance test

1. Set a voucher's `expiry_date` to `today + 7` (manual seed).
2. Run the daily scheduled task manually.
3. Email arrives at `aarelaponin@gmail.com`.
4. Subject says "expires in 7 days" plus voucher code.
5. Body contains the correct expiry date and the suggested distribution point.
