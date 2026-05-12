# 03 — application_pending_review

**Recipient:** applicant
**Trigger:** RoutingEvaluator → "pending_review" status (mixed-result eligibility, operator decision needed)
**Fires once per:** application
**Priority:** transactional

---

## Subject

```
Your application is under review — #form.spApplication.application_code#
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

Your application for #form.spProgram.programme_name# is being reviewed by
our team.

Application reference: #form.spApplication.application_code#
Status: Under review
Programme: #form.spProgram.programme_name#

Why this review:

Some details on your application need a closer look from our staff before a
decision can be made. This is normal and does not mean anything is wrong.

What happens next:

1. A district supervisor reviews your application. Most reviews complete
   within 3 working days.
2. You will receive a follow-up email when the decision is made — either
   approval or, if your circumstances do not match this programme, an
   explanation.
3. You do not need to do anything right now.

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
  <div class="header info">
    <h2 style="margin:0;">Application under review</h2>
  </div>
  <div class="content">
    <p>Hello <strong>#form.farmerBasicInfo.first_name#</strong>,</p>

    <p>Your application for <strong>#form.spProgram.programme_name#</strong>
       is being reviewed by our team.</p>

    <div class="ref-box">
      <dl style="margin:0;">
        <dt>Application reference</dt>
        <dd><strong>#form.spApplication.application_code#</strong></dd>
        <dt>Status</dt>
        <dd>Under review</dd>
        <dt>Programme</dt>
        <dd>#form.spProgram.programme_name#</dd>
      </dl>
    </div>

    <p><strong>Why this review:</strong> some details on your application
       need a closer look from our staff before a decision can be made.
       This is normal and does not mean anything is wrong.</p>

    <p><strong>What happens next:</strong></p>
    <ol>
      <li>A district supervisor reviews your application. Most reviews complete within 3 working days.</li>
      <li>You will receive a follow-up email when the decision is made — either approval or, if your circumstances do not match this programme, an explanation.</li>
      <li>You do not need to do anything right now.</li>
    </ol>

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

Same set as 02_application_auto_approved minus voucher_validity_days.

## Acceptance test

1. Submit an application that yields a mixed eligibility result (one rule pass, one fail — e.g. fixture profile B with a borderline land-area rule).
2. Email arrives at `aarelaponin@gmail.com` within 60 sec.
3. Subject + body explain the "under review" status calmly.
4. Reassures the applicant they don't need to act.
