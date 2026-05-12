# 04 — application_rejected

**Recipient:** applicant
**Trigger:** Operator-decision binder → "rejected" (operator chose Reject in the operator-review screen) OR RoutingEvaluator → "rejected" auto-fail
**Fires once per:** application
**Priority:** transactional — but writing tone matters more here than for any other template

---

## Editorial note

This is the only template a citizen receives that delivers bad news. It must:
- Lead with the decision clearly (no burying it).
- Give a plain-language reason — never just an internal eligibility-rule code.
- Tell the applicant what alternatives exist (re-apply later? appeal? other programmes?).
- Leave them feeling respected, not dismissed.

The tone is "regretful and explanatory" — not bureaucratic, not falsely warm.

---

## Subject

```
Decision on your subsidy application — #form.spApplication.application_code#
```

## Body — plaintext

```
Hello #form.farmerBasicInfo.first_name#,

We have completed the review of your application for
#form.spProgram.programme_name#.

Application reference: #form.spApplication.application_code#
Decision: Not approved

Reason:
#form.spApplication.decision_reason#

What this means:

This programme does not match your circumstances at this time. This is not
a reflection on you — programmes are designed for specific groups (for
example, smallholder farmers in particular districts, or farmers with land
above a certain size). If your circumstances change, or if a future
programme matches your situation better, you can apply again.

What you can do next:

1. Other agricultural programmes may be available. Visit your district
   office to discuss what you might qualify for.
2. If you believe this decision is incorrect — for example, the data on
   file about your land area or household is wrong — visit your district
   office with supporting documents. We can correct the record and you
   may re-apply.

Contact your district office:
#form.md03District.district_name# District Office
Phone: #form.md03District.phone#

We thank you for your interest in this programme.

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
    <h2 style="margin:0;">Decision on your application</h2>
  </div>
  <div class="content">
    <p>Hello <strong>#form.farmerBasicInfo.first_name#</strong>,</p>

    <p>We have completed the review of your application for
       <strong>#form.spProgram.programme_name#</strong>.</p>

    <div class="ref-box">
      <dl style="margin:0;">
        <dt>Application reference</dt>
        <dd><strong>#form.spApplication.application_code#</strong></dd>
        <dt>Decision</dt>
        <dd><strong>Not approved</strong></dd>
      </dl>
    </div>

    <p><strong>Reason:</strong></p>
    <blockquote style="border-left:4px solid #6c757d;padding-left:16px;color:#495057;margin:8px 0;">
      #form.spApplication.decision_reason#
    </blockquote>

    <p><strong>What this means:</strong></p>
    <p>This programme does not match your circumstances at this time. This
       is not a reflection on you — programmes are designed for specific
       groups (for example, smallholder farmers in particular districts, or
       farmers with land above a certain size). If your circumstances
       change, or if a future programme matches your situation better, you
       can apply again.</p>

    <p><strong>What you can do next:</strong></p>
    <ol>
      <li>Other agricultural programmes may be available. Visit your district office to discuss what you might qualify for.</li>
      <li>If you believe this decision is incorrect — for example, the data on file about your land area or household is wrong — visit your district office with supporting documents. We can correct the record and you may re-apply.</li>
    </ol>

    <p>Contact your district office:<br>
       <strong>#form.md03District.district_name# District Office</strong><br>
       Phone: #form.md03District.phone#</p>

    <p>We thank you for your interest in this programme.</p>

    <p>— Ministry of Agriculture, Food Security and Nutrition</p>

    <div class="footer">Do not reply to this message.</div>
  </div>
</body>
</html>
```

## Variables used

| Variable | Source | Notes |
|---|---|---|
| `#form.spApplication.decision_reason#` | application | Plain-language reason captured by operator OR derived from auto-rejection rule. Critical that this field never holds a raw rule-code; populator must convert (deferred to wiring step W2.4). |
| (others same as templates 01-03) | | |

## Recipient resolution

Same as previous templates — DEV-override to `aarelaponin@gmail.com`.

## Acceptance test

1. Submit application that auto-fails (fixture profile C — applicant in wrong district for PRG_2025_001).
2. Email arrives.
3. Subject is calmly worded ("Decision on..." not "REJECTED" or "DENIED").
4. Body leads with the decision, gives a plain-language reason, suggests next steps.
5. No raw rule-codes (`DET_DISTRICT_LIST` etc.) appear in the body — only human-readable text.
