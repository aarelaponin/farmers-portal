# 05 — application_decision_pending

**Recipient:** district supervisor (operator-side, NOT applicant)
**Trigger:** daily scheduled task — find applications in `pending_decision` status >24 hours, group by district, send one digest per supervisor
**Fires once per:** day, per district supervisor with a non-empty queue
**Priority:** routine (not transactional — daily digest)

---

## Subject

```
#form.spApplication.pending_count_district# applications waiting for your review — #form.md03District.district_name#
```

## Body — plaintext

```
Hello #user.firstName#,

You have #form.spApplication.pending_count_district# applications in
#form.md03District.district_name# district that are waiting for a
supervisor decision. Some have been waiting more than 24 hours.

Oldest waiting:    #form.spApplication.oldest_waiting_days# days
Total in queue:    #form.spApplication.pending_count_district#
Average wait time: #form.spApplication.avg_wait_hours_district# hours

Open the operator inbox to review:
#form.md03District.portal_url#/operator-review

Reviewing applications promptly helps citizens plan their season. Each
application that waits more than 5 working days triggers an automatic
escalation notice to the regional manager.

— Farmers Portal automation

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
    <h2 style="margin:0;">Applications awaiting your review</h2>
  </div>
  <div class="content">
    <p>Hello <strong>#user.firstName#</strong>,</p>

    <p>You have
       <strong>#form.spApplication.pending_count_district# applications</strong>
       in <strong>#form.md03District.district_name# district</strong> that
       are waiting for a supervisor decision. Some have been waiting more
       than 24 hours.</p>

    <div class="ref-box">
      <dl style="margin:0;">
        <dt>Oldest waiting</dt>
        <dd>#form.spApplication.oldest_waiting_days# days</dd>
        <dt>Total in queue</dt>
        <dd>#form.spApplication.pending_count_district#</dd>
        <dt>Average wait time</dt>
        <dd>#form.spApplication.avg_wait_hours_district# hours</dd>
      </dl>
    </div>

    <p><a href="#form.md03District.portal_url#/operator-review"
          style="background:#1e6091;color:white;padding:10px 20px;text-decoration:none;border-radius:4px;display:inline-block;">
      Open operator inbox
    </a></p>

    <p style="font-size:0.9em;color:#6c757d;">
      Reviewing applications promptly helps citizens plan their season.
      Each application that waits more than 5 working days triggers an
      automatic escalation notice to the regional manager.
    </p>

    <p>— Farmers Portal automation</p>

    <div class="footer">Do not reply to this message.</div>
  </div>
</body>
</html>
```

## Variables used

| Variable | Source | Notes |
|---|---|---|
| `#user.firstName#` | logged-in user (the supervisor) | Joget hash variable |
| `#form.md03District.district_name#` | MD.03 lookup (per supervisor) | |
| `#form.spApplication.pending_count_district#` | derived | Count of apps in pending_decision for this district. Computed by the scheduled-task BeanShell at send time. |
| `#form.spApplication.oldest_waiting_days#` | derived | `now - oldest decided_at` in days |
| `#form.spApplication.avg_wait_hours_district#` | derived | Average `now - submitted` across the district queue |
| `#form.md03District.portal_url#` | env config | Base URL of the portal — placeholder until production cutover |

## Recipient resolution

- **Production:** the supervisor's `dir_user.email` (Joget user-directory email field).
- **Dev override:** literal `aarelaponin@gmail.com`. Subject line includes the district so we can verify the digest is being computed correctly per district.

## Implementation note

The aggregate counts are NOT simple form-row reads — they need a SQL
aggregation over `app_fd_subsidy_app_2025` filtered by district + status +
age. Two options for W2.4:

1. **BeanShell in a scheduled-task plugin** that runs once per day, queries
   the aggregations per district, populates a small per-supervisor
   `app_fd_pending_digest` row, then fires EmailTool against that row.
2. **Custom Joget tool plugin** with a single `execute()` that does the SQL
   + EmailTool call for each district inline.

Option 2 is cleaner. Defer the choice to W2.4 wiring; this template is
self-describing for either.

## Acceptance test

1. Seed 5 fake `pending_decision` applications for one district, all >24h old.
2. Manually trigger the scheduled task.
3. One email arrives at `aarelaponin@gmail.com` per supervisor of that district.
4. Subject reflects the count + district.
5. Body counts match seeded data; oldest-waiting-days reasonable.
