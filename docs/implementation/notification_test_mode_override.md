# Notification Test-Mode Override

**Author:** Aare Laponin
**Date:** 2026-05-11
**Status:** Active — build-127 onwards
**Audience:** MAFSN IT (the team who will operate the production go-live cutover)

---

## What this is

A safety switch that re-routes every outbound notification (email and SMS) to a designated **test inbox / test phone** instead of the citizen the event is about. While the switch is `Y`, no real citizen ever receives a Farmers Portal notification — every dispatch lands in the test inbox with the intended-recipient address visible in the subject line.

This exists because:
- Pre-production runs need exercise of the full notification lifecycle without spamming real Lesotho farmers.
- The 12 lifecycle events (application submitted, approved, voucher issued, etc.) can fire dozens of times per UAT pass; re-routing to a single inbox lets us verify the routing logic without the social and legal risk of accidental real sends.
- The cutover to live ("flip the switch") needs to be a deliberate, one-time, audit-trailed operation. It should not be a Wednesday-afternoon UI click.

## How it's controlled

Three JVM system properties, set in Joget's `setenv.sh` (or your deployment's equivalent CATALINA_OPTS source):

| Property | Default | Meaning |
|---|---|---|
| `regbb.notif.testMode` | `Y` (fail-safe) | `Y` redirects every email + SMS to the test addresses below. `N` sends to the per-template recipient resolver (the live behaviour). |
| `regbb.notif.testEmail` | `aarelaponin@gmail.com` | Test inbox for emails when `testMode=Y`. |
| `regbb.notif.testPhone` | `+26658515039` | Test phone for SMS when `testMode=Y`. |

If a property is unset, the default applies. The dispatcher therefore **fail-safes to test mode** — the system can never start sending to real citizens because someone forgot to set a property. Going live requires an explicit `-Dregbb.notif.testMode=N`.

## To verify current state

In `joget.log`, every dispatch emits a line that confirms the mode it ran under, for example:

```
INFO  global.govstack.regbb.engine.notification.EmailDispatcher -
  [VOUCHER_ISSUED] TEST MODE active — re-routing 1 recipient(s) to
  aarelaponin@gmail.com (originals: Pmantsali@gmail.com)
```

When the switch flips to live, the same line reads:

```
INFO  global.govstack.regbb.engine.notification.EmailDispatcher -
  [VOUCHER_ISSUED] resolver=APPLICANT testMode=false sent=1 failed=0
```

To inspect the active configuration via a heartbeat log line, look for any `EmailDispatcher` send: each emits `testMode=true/false` as part of its summary line. (`NotificationConfig.describe()` is also available if a diagnostic endpoint ever needs to surface the live state.)

## Where operators see notifications

**Admin → Notification Queue** is the operator transparency surface. Every dispatch attempt — immediate or scheduled, email or SMS — appears as a row with full state:

| Column | What it tells you |
|---|---|
| When | Timestamp the dispatch was attempted |
| Event | `APP_SUBMITTED`, `VOUCHER_ISSUED`, etc. |
| Ch | EMAIL or SMS |
| Backend | `gmail`, `ses`, `mailtrap`, `LOG_ONLY`, etc. — **`LOG_ONLY` means simulated, not actually transmitted** |
| Status | `pending` → `sent` / `skipped` / `failed` / `dead_letter` |
| Test? | `Y` if the test-mode redirect was active when this fired |
| Intended | Where the resolver said it should go (real applicant address) |
| Actual | Where it was actually delivered (test inbox in test mode) |
| Subject | Rendered subject line for emails |
| Correlation | application_id / voucher_code / envelope_code for cross-reference |
| Tries | Retry counter |
| Last Error | If failed, the exception or backend rejection reason |

**Filters available** at the top of the list: status, channel, eventCode, backend, testMode, correlationId, actual-recipient-contains.

**Bulk row actions:**
- **Retry Selected** — pick FAILED rows, click Retry. The state machine transitions FAILED → PENDING; the NotificationQueueWorker picks them up on the next 60s poll and re-attempts the dispatch.
- **Mark Dead-Letter** — pick FAILED rows, click Mark Dead-Letter to terminally bury them (e.g., operator has out-of-band-contacted the citizen by phone and the email retry no longer matters). DEAD_LETTER is terminal — no further automatic retries.

**The full forensic trail** lives in `app_fd_audit_log` — every state transition writes one row there via `joget-status-framework`. Operators can browse it through the existing **Admin → Audit Trail** datalist, filter by `entity_type=NOTIFICATION`.

## The yellow banner

When you load any operator page, a yellow strip across the top reads:

> 🚧 **TEST MODE ACTIVE** — every email is redirected to **aarelaponin@gmail.com**, every SMS to **+26658515039**. Real citizens will not receive anything.

This banner is hardcoded in the userview's theme JS/CSS. To remove it as part of going live, run `tooling/inject_test_mode_banner.py --apply --remove` (this is in step 6 of the go-live procedure below).

## To flip live — the go-live procedure

**Pre-conditions:** MAFSN must have explicitly authorised the Farmers Portal to send to real citizens. This is a one-way operation in the project's lifecycle; it should be accompanied by a short note in the project log naming the authoriser, date, and any constraints.

1. SSH into the Joget host (or whichever way your DevOps team reaches the Tomcat instance).
2. Open `${CATALINA_HOME}/bin/setenv.sh` for editing. If the file doesn't exist yet, create it.
3. Append (or change) the three properties:

   ```sh
   CATALINA_OPTS="$CATALINA_OPTS -Dregbb.notif.testMode=N"
   # The two below are still read in test mode; keep them in setenv.sh
   # so that if you ever need to flip back to test mode, the test addresses
   # are remembered.
   CATALINA_OPTS="$CATALINA_OPTS -Dregbb.notif.testEmail=aarelaponin@gmail.com"
   CATALINA_OPTS="$CATALINA_OPTS -Dregbb.notif.testPhone=+26658515039"
   ```

4. Save the file.
5. Restart Tomcat:

   ```sh
   ${CATALINA_HOME}/bin/shutdown.sh
   # Wait for Tomcat to fully stop (check ps aux | grep tomcat or the
   # access log). Then:
   ${CATALINA_HOME}/bin/startup.sh
   ```

6. Watch `joget.log` for `reg-bb-engine registered: ... build-127 @ ...` confirming the plugin came back up.

7. **Remove the test-mode banner** from the userview so operators don't see a stale warning:

   ```sh
   python3 tooling/inject_test_mode_banner.py --apply --remove
   ```

   This strips the managed CSS + JS blocks from the userview's `Dx8TrimedaTheme.css` and `Dx8TrimedaTheme.js` properties, leaving any other customisations intact (the script uses sentinel markers `=== RegBB test-mode banner ===` to scope its edits).

8. Smoke-test by firing one notification (e.g. a citizen submits a test application). The next log line for that event should read `testMode=false sent=1`. The notification should land in the real applicant's inbox, not yours.

That's it — citizens now receive their own notifications.

## To flip BACK to test mode (rollback)

The same procedure with `regbb.notif.testMode=Y`. Restart Tomcat. Within ~30 seconds the next dispatch goes back to the test inbox.

## Why JVM properties, not a database row

We initially built this as a database-backed singleton form (App Composer → Admin → Notification Test-Mode Override) so operators could flip the switch via the UI. That design hit a Joget cache trap — see CLAUDE.md "What goes wrong if you violate the rule" — where the newly-created form's Hibernate ORM mapping was populated before the underlying Postgres table existed, leaving cache 2 stuck. The datalist couldn't render the row even though it was correctly written, and the documented App Composer Save recovery did not clear it reliably.

System properties are the standard place for "kill switch" / "safety mode" flags in production Java applications. They:

- Survive restarts and crashes.
- Are auditable — `setenv.sh` lives in version control or a deployment script, so every change leaves a trail.
- Don't require any Joget caches to be in a consistent state.
- Are explicit — there's no UI button to "accidentally" click. A go-live decision becomes a deliberate edit to a config file by someone with shell access to the Joget host.
- Are the same mechanism the dispatcher already uses for the SMTP throttle (`regbb.email.gap.ms`) and the SMS backend selector (`regbb.sms.backend`).

The trade-off — that the operator can't flip the switch from a web UI — is acceptable because this is a single-shot decision (typically once in the project's lifetime), made by a person who has shell access anyway (to coordinate the SMTP relay swap and the SMS gateway credentials that go live at the same time).

## Dormant orphan forms (cleanup)

The earlier UI-based design left behind:

- The form definition `notif_global_config` in `app_form`.
- The datalist `list_notif_global_config` in `app_datalist`.
- The Postgres table `app_fd_notif_global_config`.

These are inert — no Java code reads from them, no menu surfaces them. They can be removed via App Composer's "Delete Form" UI when convenient. They are not removed via raw SQL because the HARD RULE (CLAUDE.md) prohibits it on Joget-managed tables.

## Related JVM properties

For completeness, the other system properties the notification stack consults at startup:

| Property | Default | Purpose |
|---|---|---|
| `regbb.email.gap.ms` | `10000` | Minimum gap (ms) between consecutive SMTP sends. Drops to 100ms for production SMTP relays that don't throttle. |
| `regbb.sms.backend` | `log` | `log` writes "would have sent" to joget.log; `http` enables the real SMS HTTP backend. |
| `regbb.sms.http.endpoint` | (unset) | When `regbb.sms.backend=http`, the gateway's POST URL. |
| `regbb.sms.http.auth-header` | (unset) | Authorization header value sent to the SMS gateway (`Bearer XYZ` or `Basic <base64>`). |
| `regbb.sms.http.from` | `MAFSN` | Alphanumeric sender ID on outbound SMS. |

Set all of these in the same `setenv.sh` so that one file is the single source of truth for the dispatcher's runtime configuration.
