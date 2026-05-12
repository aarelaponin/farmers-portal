# Farmers Portal — Email templates (Week 2)

Twelve transactional email templates covering every state transition in the
subsidy lifecycle that a citizen, operator, or finance staff member should
hear about.

## Structure

One markdown file per template. Each file is the canonical source — when we
import a template into Joget's Email Tool config (W2.4), the values come
straight from that file. Subject + body + variable list + trigger + recipient
all in one place.

## Naming

`NN_template_id.md` where `NN` is the order the citizen-facing templates
fire across a typical lifecycle, and `template_id` matches the Joget
`emailTemplateId` we'll use in the EmailTool tool tasks.

## Conventions

- **Language:** English only. Sesotho translation is deferred to MAFSN
  post-UAT (decision recorded in Week 1 close-out note). Customer-visible
  strings remain English placeholders that a translator will swap.
- **Variable syntax:** Joget hash-variable syntax — `#form.formId.fieldId#`
  for form-row values, `#date.now#` for the current date, etc. EmailTool
  resolves these via `AppUtil.processHashVariable()` at send time
  (verified at `wflow-core/src/main/java/org/joget/apps/app/lib/EmailTool.java:78`).
  We do NOT use Velocity / Freemarker / Mustache — Joget hash variables only.
- **HTML vs plaintext:** every template ships both. Citizens with weak email
  clients (low-bandwidth, screen readers, government-issued feature phones)
  get plaintext. HTML is reserved for office recipients (operators, finance,
  district supervisors) who are reading on Outlook / GMail web.
- **Recipient override (DEV ONLY):** every outgoing email in the dev
  environment routes to `aarelaponin@gmail.com` regardless of the citizen's
  real address. The body shows the original-intended-recipient name + their
  district so we can verify routing logic without spamming real people.
  Production cutover removes the override; the EmailTool's `toSpecific` field
  goes back to reading the actual `farmer.email` field.
- **From address:** `noreply-mafsn@dev.farmersportal.gov.ls` (placeholder).
  Production From address is an MAFSN ICT decision documented in
  `smtp_production_config.md`.
- **Footer:** every customer-facing template includes "Do not reply to this
  message; for questions contact your district office at <phone>." The
  district phone number is itself a hash variable (`#form.md03District.phone#`)
  pulled from the MD.03 District lookup so a single MD update changes every
  template's footer.

## Template inventory

| # | Template ID | Recipient | Trigger |
|---|---|---|---|
| 01 | `application_submitted` | applicant | Form post-processor on first save of `spApplication` |
| 02 | `application_auto_approved` | applicant | RoutingEvaluator → "approved" status |
| 03 | `application_pending_review` | applicant | RoutingEvaluator → "pending_review" status |
| 04 | `application_rejected` | applicant | Operator-decision binder → "rejected" |
| 05 | `application_decision_pending` | district supervisor | Daily scheduled task: applications in `pending_decision` >24h |
| 06 | `voucher_issued` | applicant | `VoucherIssuanceTool` post-issuance hook |
| 07 | `voucher_redeemed` | applicant | `VoucherRedemptionTool` post-redemption hook (per redemption) |
| 08 | `voucher_expiring_7d` | applicant | Daily scheduled task: vouchers with expiryDate = today+7 |
| 09 | `voucher_expired` | applicant | `VoucherExpirySweeper` post-expiry hook |
| 10 | `voucher_cancelled` | applicant | Voucher cancellation operator action |
| 11 | `budget_envelope_75pct` | finance officer | Hourly scheduled task: envelope utilisation crosses 75 % |
| 12 | `budget_envelope_frozen` | finance officer + district supervisors | Threshold-automation hook (already exists; add EmailTool step) |

## After authoring

W2.3 produces `smtp_production_config.md` — what MAFSN ICT needs to wire up
the production SMTP server, separate from the templates themselves.

W2.4 imports each template into Joget as an EmailTool tool task or a post-
processor invocation. The mapping happens once per template; the markdown
source is the source of truth thereafter.
