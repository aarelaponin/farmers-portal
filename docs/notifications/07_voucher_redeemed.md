# 07 — voucher_redeemed

**Recipient:** applicant
**Trigger:** `VoucherRedemptionTool` post-redemption hook
**Fires once per:** redemption (multi-call vouchers fire this template once per partial redemption — see Slice 11 partial-redemption logic)
**Priority:** transactional

---

## Subject

```
Voucher redemption confirmed — #form.imVoucherRedemption.redemption_code#
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

Your voucher has been redeemed at #form.md37CollectionPoint.point_name#
on #form.imVoucherRedemption.redemption_date#.

Redemption reference: #form.imVoucherRedemption.redemption_code#
Voucher code:         #form.imVoucher.voucher_code#
Distribution point:   #form.md37CollectionPoint.point_name#
                      (#form.md37CollectionPoint.address#)
Date redeemed:        #form.imVoucherRedemption.redemption_date#
Benefit dispensed:    #form.imVoucherRedemption.quantity_dispensed# of #form.imVoucher.benefit_description#

Remaining on this voucher:

#form.imVoucher.remaining_quantity# of #form.imVoucher.benefit_description#

If your voucher still has a remaining quantity, you may visit a distribution
point again before the expiry date (#form.imVoucher.expiry_date#) to claim
the remainder. If your voucher is now fully redeemed, no further action is
needed.

Keep this email as proof of redemption.

If anything looks wrong with this redemption — e.g. the quantity dispensed
does not match what you received — contact your district office immediately:

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
  <div class="header success">
    <h2 style="margin:0;">Redemption confirmed</h2>
  </div>
  <div class="content">
    <p>Hello <strong>#form.farmerBasicInfo.first_name#</strong>,</p>

    <p>Your voucher has been redeemed at
       <strong>#form.md37CollectionPoint.point_name#</strong>
       on <strong>#form.imVoucherRedemption.redemption_date#</strong>.</p>

    <div class="ref-box">
      <dl style="margin:0;">
        <dt>Redemption reference</dt>
        <dd><strong>#form.imVoucherRedemption.redemption_code#</strong></dd>
        <dt>Voucher code</dt>
        <dd>#form.imVoucher.voucher_code#</dd>
        <dt>Distribution point</dt>
        <dd>#form.md37CollectionPoint.point_name#<br>
            <span style="font-size:0.9em;color:#6c757d;">#form.md37CollectionPoint.address#</span></dd>
        <dt>Date redeemed</dt>
        <dd>#form.imVoucherRedemption.redemption_date#</dd>
        <dt>Benefit dispensed</dt>
        <dd><strong>#form.imVoucherRedemption.quantity_dispensed#</strong>
            of #form.imVoucher.benefit_description#</dd>
      </dl>
    </div>

    <p><strong>Remaining on this voucher:</strong>
       #form.imVoucher.remaining_quantity# of #form.imVoucher.benefit_description#</p>

    <p>If your voucher still has a remaining quantity, you may visit a
       distribution point again before the expiry date
       (<strong>#form.imVoucher.expiry_date#</strong>) to claim the
       remainder. If your voucher is now fully redeemed, no further action
       is needed.</p>

    <p style="background:#fff3e0;border-left:4px solid #ef6c00;padding:12px 16px;">
      Keep this email as proof of redemption. If anything looks wrong with
      this redemption — for example, the quantity dispensed does not match
      what you received — contact your district office immediately.
    </p>

    <p><strong>#form.md03District.district_name# District Office</strong><br>
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
| `#form.imVoucherRedemption.redemption_code#` | redemption row | RDM-YYYYMMDD-NNNN |
| `#form.imVoucherRedemption.redemption_date#` | redemption row | dateCreated |
| `#form.imVoucherRedemption.quantity_dispensed#` | redemption row | Number — unit derived from voucher.benefit_description |
| `#form.imVoucher.remaining_quantity#` | derived | voucher.qty_total - sum(redemptions for this voucher) |
| `#form.md37CollectionPoint.point_name#` | MD.37 lookup | Joined via redemption.collection_point_code |
| `#form.md37CollectionPoint.address#` | MD.37 lookup | |

## Acceptance test

1. Issue voucher (template 06 fires).
2. Redeem at a distribution point through the operator UI.
3. This template (07) fires.
4. If partial redemption, "Remaining on this voucher" reflects the correct delta. Redeem again — template 07 fires a second time with reduced remaining_quantity.
