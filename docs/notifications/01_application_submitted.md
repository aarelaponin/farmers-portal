# 01 — application_submitted

**Recipient:** applicant
**Trigger:** post-processor on first save of `spApplication`
**Fires once per:** application
**Priority:** transactional (must arrive within 1 min of trigger)

---

## Subject

```
Your subsidy application has been received — #form.spApplication.application_code#
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

Thank you. Your application for #form.spProgram.programme_name# has been
received by the Ministry of Agriculture, Food Security and Nutrition.

Application reference: #form.spApplication.application_code#
Submitted: #form.spApplication.dateCreated#
Programme: #form.spProgram.programme_name#

What happens next:

1. Our team reviews your application against the eligibility rules for
   this programme.
2. If everything checks out, you will receive a follow-up email within
   3 working days telling you whether you are approved.
3. If approved, an electronic voucher will be issued to your registered
   phone number.

You do not need to do anything right now.

If you have questions, contact your district office:
#form.md03District.district_name# District Office
Phone: #form.md03District.phone#

— Ministry of Agriculture, Food Security and Nutrition

Do not reply to this message — replies are not monitored. Contact your
district office for any questions.
```

## Body — HTML

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <style>
    body { font-family: Arial, Helvetica, sans-serif; color: #2c3e50; max-width: 600px; margin: 0 auto; padding: 20px; }
    .header { background: #1e6091; color: white; padding: 16px; border-radius: 4px 4px 0 0; }
    .content { background: #f8f9fa; padding: 20px; border: 1px solid #dee2e6; }
    .ref-box { background: white; border-left: 4px solid #1e6091; padding: 12px 16px; margin: 16px 0; }
    .ref-box dt { font-weight: bold; color: #6c757d; font-size: 0.85em; }
    .ref-box dd { margin: 0 0 8px 0; }
    .footer { font-size: 0.85em; color: #6c757d; margin-top: 16px; padding-top: 16px; border-top: 1px solid #dee2e6; }
    ol { padding-left: 24px; }
    ol li { margin-bottom: 8px; }
  </style>
</head>
<body>
  <div class="header">
    <h2 style="margin:0;">Application received</h2>
  </div>
  <div class="content">
    <p>Hello <strong>#form.farmerBasicInfo.first_name#</strong>,</p>

    <p>Thank you. Your application for
       <strong>#form.spProgram.programme_name#</strong> has been received by
       the Ministry of Agriculture, Food Security and Nutrition.</p>

    <div class="ref-box">
      <dl style="margin:0;">
        <dt>Application reference</dt>
        <dd><strong>#form.spApplication.application_code#</strong></dd>
        <dt>Submitted</dt>
        <dd>#form.spApplication.dateCreated#</dd>
        <dt>Programme</dt>
        <dd>#form.spProgram.programme_name#</dd>
      </dl>
    </div>

    <p><strong>What happens next:</strong></p>
    <ol>
      <li>Our team reviews your application against the eligibility rules for this programme.</li>
      <li>If everything checks out, you will receive a follow-up email within 3 working days telling you whether you are approved.</li>
      <li>If approved, an electronic voucher will be issued to your registered phone number.</li>
    </ol>

    <p>You do not need to do anything right now.</p>

    <p>If you have questions, contact your district office:<br>
       <strong>#form.md03District.district_name# District Office</strong><br>
       Phone: #form.md03District.phone#</p>

    <p>— Ministry of Agriculture, Food Security and Nutrition</p>

    <div class="footer">
      Do not reply to this message — replies are not monitored. Contact your
      district office for any questions.
    </div>
  </div>
</body>
</html>
```

## Variables used

| Variable | Source | Notes |
|---|---|---|
| `#form.farmerBasicInfo.first_name#` | farmer registry | Greeting personalisation |
| `#form.spApplication.application_code#` | application | The AP-XXXXXX reference |
| `#form.spApplication.dateCreated#` | application | Joget auto-populated timestamp |
| `#form.spProgram.programme_name#` | programme registry | Joined via spApplication.programme_code |
| `#form.md03District.district_name#` | MD.03 lookup | Joined via farmerResidency.district |
| `#form.md03District.phone#` | MD.03 lookup | District-office contact phone |

## Recipient resolution

- **Production:** `#form.farmerBasicInfo.email#` (the email field on farmer registration — currently doesn't exist; deferred to production cutover, see W2 dev-override note in README).
- **Dev override:** literal `aarelaponin@gmail.com`. The body shows the original-intended recipient via the `#form.farmerBasicInfo.first_name#` greeting so routing is verifiable.

## Acceptance test

1. Submit a new test application via the citizen wizard.
2. Within 60 seconds, an email arrives at `aarelaponin@gmail.com`.
3. Subject contains the application code.
4. Greeting uses the farmer's first name.
5. Application code, submission timestamp, and programme name all populated correctly.
6. District phone number resolves (no `#form...#` literal in the body).
