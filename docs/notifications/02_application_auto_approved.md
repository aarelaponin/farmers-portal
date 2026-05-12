# 02 — application_auto_approved

**Recipient:** applicant
**Trigger:** RoutingEvaluator → "approved" status transition (auto-approval path, no operator review needed)
**Fires once per:** application
**Priority:** transactional

---

## Subject

```
Good news — your subsidy application is approved (#form.spApplication.application_code#)
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

Your application for #form.spProgram.programme_name# has been APPROVED.

Application reference: #form.spApplication.application_code#
Decision date: #date.now#
Programme: #form.spProgram.programme_name#

What happens next:

1. An electronic voucher is being issued to you. You will receive a separate
   email confirming the voucher details, including the voucher code and
   expiry date.
2. To redeem your voucher, visit any approved distribution point in your
   district. Bring your National ID.
3. If you do not redeem within #form.spProgram.voucher_validity_days# days,
   the voucher will expire and the benefit returns to the programme budget.

You do not need to visit any office to claim approval. The voucher will
arrive automatically in a separate message.

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
    <h2 style="margin:0;">Application approved</h2>
  </div>
  <div class="content">
    <p>Hello <strong>#form.farmerBasicInfo.first_name#</strong>,</p>

    <div class="alert success">
      <strong>Your application for #form.spProgram.programme_name# has been APPROVED.</strong>
    </div>

    <div class="ref-box">
      <dl style="margin:0;">
        <dt>Application reference</dt>
        <dd><strong>#form.spApplication.application_code#</strong></dd>
        <dt>Decision date</dt>
        <dd>#date.now#</dd>
        <dt>Programme</dt>
        <dd>#form.spProgram.programme_name#</dd>
      </dl>
    </div>

    <p><strong>What happens next:</strong></p>
    <ol>
      <li>An electronic voucher is being issued to you. You will receive a separate email confirming the voucher details, including the voucher code and expiry date.</li>
      <li>To redeem your voucher, visit any approved distribution point in your district. Bring your National ID.</li>
      <li>If you do not redeem within #form.spProgram.voucher_validity_days# days, the voucher will expire and the benefit returns to the programme budget.</li>
    </ol>

    <p>You do not need to visit any office to claim approval. The voucher will arrive automatically in a separate message.</p>

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
| `#form.farmerBasicInfo.first_name#` | farmer registry | |
| `#form.spApplication.application_code#` | application | AP-XXXXXX reference |
| `#date.now#` | system | Joget hash variable for current date |
| `#form.spProgram.programme_name#` | programme registry | |
| `#form.spProgram.voucher_validity_days#` | programme registry | Sets the redemption window expectation |
| `#form.md03District.district_name#` | MD.03 lookup | |
| `#form.md03District.phone#` | MD.03 lookup | |

## Recipient resolution

Same as `01_application_submitted.md` — DEV-override to `aarelaponin@gmail.com`.

## Acceptance test

1. Submit an application that fully passes RoutingEvaluator (e.g. fixture profile A — Mants'ali Panyane on PRG_2025_004).
2. Email arrives at `aarelaponin@gmail.com` within 60 sec of submission.
3. Subject says "Good news" + application code.
4. Body confirms approved status, lists programme, reference, and voucher-validity expectation.
5. Followed shortly by template 06 (`voucher_issued`) per the auto-issuance hook.
