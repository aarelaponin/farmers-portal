# 06 — voucher_issued

**Recipient:** applicant
**Trigger:** `VoucherIssuanceTool` post-issuance hook (immediately after a voucher row is written to `app_fd_im_voucher`)
**Fires once per:** voucher
**Priority:** transactional (must arrive within 1 min)

---

## Subject

```
Your voucher is ready — #form.imVoucher.voucher_code#
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

Your subsidy voucher has been issued. You can use this voucher at any
approved distribution point to claim your benefit.

Voucher code:        #form.imVoucher.voucher_code#
Programme:           #form.spProgram.programme_name#
Issued on:           #form.imVoucher.issued_date#
Valid until:         #form.imVoucher.expiry_date#
Benefit:             #form.imVoucher.benefit_description#

How to redeem:

1. Visit any approved distribution point in your district. A list is
   available at your district office or on the citizen portal.
2. Bring your National ID and the voucher code above.
3. The distribution-point operator will verify your identity, confirm
   the voucher is still valid, and dispense your benefit.
4. You will receive a separate confirmation email after redemption.

Important — please redeem before the expiry date:

If you do not redeem by #form.imVoucher.expiry_date#, this voucher will
expire and the benefit returns to the programme budget for redistribution.
A reminder will be sent 7 days before expiry.

If you have questions, contact your district office:
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
    <h2 style="margin:0;">Voucher issued</h2>
  </div>
  <div class="content">
    <p>Hello <strong>#form.farmerBasicInfo.first_name#</strong>,</p>

    <p>Your subsidy voucher has been issued. You can use this voucher at any
       approved distribution point to claim your benefit.</p>

    <div class="ref-box">
      <dl style="margin:0;">
        <dt>Voucher code</dt>
        <dd style="font-size:1.4em;font-weight:bold;font-family:monospace;color:#1e6091;">
          #form.imVoucher.voucher_code#
        </dd>
        <dt>Programme</dt>
        <dd>#form.spProgram.programme_name#</dd>
        <dt>Issued on</dt>
        <dd>#form.imVoucher.issued_date#</dd>
        <dt>Valid until</dt>
        <dd><strong>#form.imVoucher.expiry_date#</strong></dd>
        <dt>Benefit</dt>
        <dd>#form.imVoucher.benefit_description#</dd>
      </dl>
    </div>

    <p><strong>How to redeem:</strong></p>
    <ol>
      <li>Visit any approved distribution point in your district. A list is available at your district office or on the citizen portal.</li>
      <li>Bring your National ID and the voucher code above.</li>
      <li>The distribution-point operator will verify your identity, confirm the voucher is still valid, and dispense your benefit.</li>
      <li>You will receive a separate confirmation email after redemption.</li>
    </ol>

    <div class="alert">
      <strong>Important — please redeem before the expiry date:</strong><br>
      If you do not redeem by <strong>#form.imVoucher.expiry_date#</strong>,
      this voucher will expire and the benefit returns to the programme
      budget for redistribution. A reminder will be sent 7 days before expiry.
    </div>

    <p>If you have questions, contact your district office:<br>
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
| `#form.imVoucher.voucher_code#` | voucher | VCH-YYYYMMDD-NNNN reference |
| `#form.imVoucher.issued_date#` | voucher | dateCreated, formatted yyyy-MM-dd |
| `#form.imVoucher.expiry_date#` | voucher | derived = issued + programme.voucher_validity_days |
| `#form.imVoucher.benefit_description#` | voucher | human-readable benefit (e.g. "50 kg maize seed") |

## Acceptance test

1. Approve an application that triggers auto-voucher-issuance (existing flow from IM Slice 6b).
2. Email arrives at `aarelaponin@gmail.com` within 60 sec of the voucher write.
3. Voucher code displayed prominently in monospace.
4. Expiry date matches the programme's voucher_validity_days.
5. Followed approximately `voucher_validity_days - 7` days later by template 08 (`voucher_expiring_7d`) — though this is verified separately.
