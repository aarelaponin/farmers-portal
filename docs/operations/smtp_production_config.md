# SMTP production configuration — what MAFSN ICT must provision

**Status:** specification (not yet provisioned).
**Owner of this document:** architecture team.
**Owner of the production SMTP server:** MAFSN ICT.
**Reading time:** 8 minutes.

---

## Purpose

This document is the contract between the architecture team and MAFSN ICT
for the production email infrastructure. The architecture team is wiring
12 email templates against a Mailtrap.io test SMTP for development. When
the system goes to UAT and then production, the SMTP target needs to be a
real server hosted by MAFSN. This document spells out exactly what we need
from that server, in language that ICT staff who provision SMTP for other
ministries' systems will recognise.

It is **not** an instruction to use any specific vendor. It is a
parameter sheet. Whatever your standard SMTP host is — an on-premise
Postfix relay, Microsoft Exchange Online, AWS SES, SendGrid — needs to
satisfy these requirements, and the configuration values below need to be
provided to the architecture team before UAT.

---

## What we need from MAFSN ICT

### 1. SMTP host endpoint

A single SMTP host accessible from the Joget application server (currently
running at `20.87.213.78:8080`, target Azure VM in production).

| Parameter | Required value | Reason |
|---|---|---|
| Host | FQDN or IP. Whatever your standard relay is. | Joget config field "host" |
| Port | **587** (preferred — STARTTLS) or 465 (TLS-wrapped) | Plaintext port 25 must NOT be used for outbound — Lesotho's data-protection regulations require TLS in transit |
| Security | **STARTTLS** if port 587, **SSL/TLS** if port 465 | Joget config field "security" |
| TLS minimum version | TLS 1.2 or higher | OpenSSL/Joget supports this — no special config |

### 2. Service-account credentials

A dedicated SMTP authentication account, NOT a personal mailbox.

| Parameter | Required value | Reason |
|---|---|---|
| Username | `noreply-mafsn@<your-mail-domain>` (suggestion) | Used in SMTP AUTH; visible only to ICT, never to citizens |
| Password | Strong (24+ char, generated, rotated quarterly) | Stored in Joget Settings — never in code, never in this document |
| Permissions | Send-only. Inbox of this account is unused. | Reduces attack surface: a leaked credential can spam, but cannot read mail |

The architecture team will receive these credentials via a secure channel
(1Password vault, Bitwarden share, password manager — never email or
chat). They land in the Joget admin console at:

```
System Settings → Email Configuration → SMTP
```

Joget stores them encrypted at rest in `app.properties` (the
`SetupManager` reads them on startup). They never appear in form
definitions, plugin code, or our Git repository.

### 3. From address

The "From:" header citizens see when they receive mail.

**Recommended:** `noreply-mafsn@farmersportal.gov.ls`

Rationale: a `gov.ls` second-level domain signals "this is government"
and reduces phishing-look-alike risk. A `noreply-mafsn` mailbox name
makes the sender clearly identifiable to citizens who may receive mail
from many government services.

If `farmersportal.gov.ls` is not yet provisioned, an interim From of
`noreply@<mafsn-domain>.gov.ls` is acceptable — the templates won't need
re-authoring, only the From string in Joget config changes.

### 4. Bounce-handling mailbox

Bounces (delivery failures, mailbox-full, address-doesn't-exist) need
somewhere to go. Two options:

**Option A — Discard bounces.** Set `Return-Path` to the same
`noreply-mafsn@...` and let the server discard incoming. Simple but
loses information; we never learn that an email failed.

**Option B (recommended) — A real bounce mailbox.** Set
`Return-Path: bounces-mafsn@farmersportal.gov.ls`. Bounces accumulate in
a real inbox, monitored by ICT. When a citizen's address bounces 3
times in a row, the system can mark their `farmer.email` field as
invalid and stop sending. This is a feature the architecture team will
build later (post-UAT) if Option B is provisioned.

For UAT: Option A is fine. For full production: please provision Option B.

### 5. SPF, DKIM, DMARC records

Without these, mail from `noreply-mafsn@farmersportal.gov.ls` is highly
likely to land in spam folders or be rejected outright by major mail
providers (Gmail, Outlook 365). All three are DNS records, owned by
whoever controls the `farmersportal.gov.ls` zone.

#### SPF (mandatory)

Tells receiving mail servers "these IPs are allowed to send mail as
@farmersportal.gov.ls":

```
farmersportal.gov.ls. IN TXT "v=spf1 ip4:<smtp-server-ip>/32 -all"
```

The `-all` is strict mode — anyone NOT listed is rejected outright.
Use `~all` (soft-fail) for the first 30 days while we monitor logs,
then switch to `-all` once we're confident the IP list is complete.

#### DKIM (mandatory)

Cryptographically signs each outbound message; the receiver can verify
it really came from us. Joget itself doesn't sign — the SMTP server
does, transparently. Whatever your mail server is (Postfix, Exchange),
turn DKIM signing on for the `farmersportal.gov.ls` domain. Publish
the public key as a TXT record:

```
default._domainkey.farmersportal.gov.ls. IN TXT "v=DKIM1; k=rsa; p=<public-key>"
```

#### DMARC (recommended)

The policy that tells receivers what to do when SPF or DKIM fails:

```
_dmarc.farmersportal.gov.ls. IN TXT "v=DMARC1; p=quarantine; rua=mailto:dmarc-reports@mafsn.gov.ls; sp=reject"
```

`p=quarantine` for the first 60 days (failing mail goes to spam, doesn't
get rejected outright); `p=reject` once we've cleaned up any false
positives.

### 6. Rate limits (capacity sizing)

Worst-case scenario for the Farmers Portal at full national rollout:

| Event | Frequency |
|---|---|
| Application submitted | ~50 citizens/hour during peak (start of agricultural season) |
| Voucher issued | ~50 citizens/hour during peak |
| Daily expiry-reminder digest | ~500 citizens/day |
| Daily operator pending-decision digest | ~10 supervisors/day |

Steady-state peak: **~150 emails/hour, ~600/day.** Burst: **~500/hour**
during programme launch days.

Whatever SMTP host you provision needs to handle these rates without
throttling. AWS SES free tier (1 email/sec) is too slow; SES sandbox
(200/day) is too slow. AWS SES production (14 emails/sec) works.
Microsoft 365 (10,000/day) works. SendGrid free (100/day) does NOT
work; SendGrid Essentials (50,000/month) does.

If MAFSN ICT plans to use a managed sender, please confirm the chosen
plan covers 600/day with a 500/hour burst. If using on-premise Postfix,
no rate-limit concern.

### 6a. Dev/UAT SMTP — lessons learned May 2026

The architecture team's first dev SMTP was **Mailtrap free-tier sandbox**.
That choice surfaced an unexpected mismatch with the system's send pattern.
The subsidy lifecycle fires three emails per approval in a tight cluster:
one decision email, then two voucher emails ~3-4 seconds later via the
voucher-issuance hook. Mailtrap free-tier returned `550 5.7.0 Too many
emails per second` on the second and third sends, regardless of any
client-side throttle (verified up to 10-second client-side gap).

Two corrective actions taken:

1. **Switched dev SMTP to Gmail with App Password.** A 16-character app-
   specific password generated in Google Account → Security → App
   passwords lets Joget authenticate as a regular Gmail SMTP user without
   exposing the account's main password. Gmail accepts bursts of 10+
   emails per second from a single client — well within our peak. Real
   delivery to a real inbox is also a better simulation of production
   behaviour than a sandbox capture.

2. **EmailDispatcher now ships a configurable inter-send throttle**
   (`-Dregbb.email.gap.ms=N`, default 10000 ms). For Gmail, AWS SES, or
   any production-grade SMTP the throttle should drop to 100 ms or be
   disabled outright (set `N=0`). For Mailtrap, no client-side throttle
   is sufficient — switch providers.

For production cutover, MAFSN-hosted SMTP (Postfix or O365) is expected
to have no per-second cap; the throttle should be set to 100 ms as a
polite default.

### 7. IP / network allowlisting

Joget's outbound SMTP needs to reach the SMTP host on port 587 (or 465).
If the production SMTP is on the public internet (cloud-managed sender):

- Joget VM's outbound port 587/465 must be open in the Azure NSG.
- No special inbound rules — Joget initiates the connection.

If the production SMTP is on-premise within MAFSN's network:

- Joget VM needs site-to-site VPN OR private peering to reach it.
- The Azure VM's outbound traffic to MAFSN's network must be authorised
  via firewall rule on the MAFSN side.
- Latency matters less than reliability — single packet drops can stall a
  send. A < 50 ms RTT is comfortable; up to 200 ms works.

### 8. Connection pooling and retry behaviour

Joget's `EmailTool` plugin (jw-community
`wflow-core/.../app/lib/EmailTool.java`) opens a fresh SMTP connection
per send by default. For 600/day this is fine; no pooling needed. If
later traffic grows, the architecture team can introduce a small batching
layer.

Retry policy: Joget retries failed sends 3 times with exponential
backoff (5s, 25s, 125s). After 3 failures the email is logged and
dropped. Operators have visibility into this via the audit log; not
the citizen's concern.

---

## Hand-off checklist

When MAFSN ICT is ready to provision the production SMTP, the following
need to be communicated back to the architecture team in writing:

- [ ] SMTP host FQDN or IP
- [ ] SMTP port (587 expected)
- [ ] SMTP security mode (STARTTLS expected)
- [ ] Service-account username
- [ ] Service-account password (via secure channel, NOT in this document)
- [ ] From address (`noreply-mafsn@farmersportal.gov.ls` or alternative)
- [ ] Bounce-handling mailbox (or "discard" decision)
- [ ] DNS confirmation: SPF, DKIM, DMARC records published and propagated
- [ ] Rate limit confirmed (≥600/day, ≥500/hour burst)
- [ ] Network path confirmed (Azure NSG / VPN / firewall as applicable)
- [ ] Test send: from a curl on the Joget VM through the SMTP host succeeds

The architecture team can then update Joget's Email Configuration in 15
minutes, fire one test email through every template (12 templates × 1
test = 12 emails), confirm receipt, and that segment of UAT is open.

---

## Sesotho note

The 12 email templates are currently authored in English only. Sesotho
translation is a separate work-stream owned by MAFSN's communications or
translation function. The architecture team does not author Sesotho
content; we only swap the body strings when MAFSN provides them. Each
template is structured so swap is a one-line config change per template.

This is by design: the architect should not be the language authority for
a citizen-facing system.

---

## What's NOT in this document

- DKIM private-key handling (an MAFSN ICT internal procedure).
- Mail-server brand selection (Postfix vs. Exchange vs. SES vs. SendGrid).
- Citizen email-address collection (a separate workstream — currently the
  farmer registration form does NOT capture an email address; production
  cutover will add a field, with a fallback "no email" path for citizens
  who don't have one).
- SMS gateway selection (Week 3 work; separate document).
