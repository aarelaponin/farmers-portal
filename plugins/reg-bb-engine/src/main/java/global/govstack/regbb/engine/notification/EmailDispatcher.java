package global.govstack.regbb.engine.notification;

import org.apache.commons.mail.HtmlEmail;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Sends transactional email driven entirely by operator-editable rows in
 * {@code app_fd_spNotifTemplate}. After W2.5 (May 2026) this is the single
 * entry point for every notification — there is no Java-template fallback;
 * if the operator has disabled the template or removed the row, the send is
 * skipped and a warning is logged.
 *
 * <p><b>Operator-editable knobs per template row:</b>
 * <ul>
 *   <li>{@code emailSubjectEn} / {@code emailSubjectSt} — subject per locale
 *   <li>{@code emailBodyEn} / {@code emailBodySt} — HTML body per locale
 *   <li>{@code emailBodyPlaintext} / {@code emailBodyPlaintextSt} — optional
 *       distinct plaintext alt (auto-derived from HTML by tag-stripping when blank)
 *   <li>{@code recipientResolver} — APPLICANT / OPERATOR_LIST / FINANCE_OFFICERS / etc.
 *   <li>{@code operatorRecipients} — semicolon-separated free-text list for OPERATOR_LIST
 *   <li>{@code emailEnabled} — Y/N — true off (no fallback)
 *   <li>{@code isActive} — Y/N — same effect
 *   <li>{@code sendImmediately} — IMMEDIATE or SCHEDULED
 *   <li>{@code delayMinutes} — when SCHEDULED, delay in minutes
 *   <li>{@code priority} — NORMAL/HIGH — drives queue ordering on SCHEDULED sends
 * </ul>
 *
 * <p><b>Locale routing:</b> when {@code recipientResolver=APPLICANT}, the
 * registry lookup also returns the farmer's {@code preferred_language}, which
 * overrides the caller-supplied locale. For other resolvers, the caller-supplied
 * locale (defaulting to "EN") is used.
 *
 * <p><b>Scheduling:</b> if {@code sendImmediately=SCHEDULED}, the dispatcher
 * writes a row to {@code notification_queue} with the resolved variables and
 * the scheduled time, then returns. The {@code NotificationQueueWorker}
 * daemon picks it up at its polling tick.
 */
public final class EmailDispatcher {

    private static final String CLASS_NAME = EmailDispatcher.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static { ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC")); }

    /**
     * Minimum gap between consecutive sends (legacy throttle for Mailtrap
     * burst rejection; tunable via {@code -Dregbb.email.gap.ms=N}).
     */
    private static final long MIN_SEND_GAP_MS =
        Long.getLong("regbb.email.gap.ms", 10_000L);
    private static volatile long lastSentAt = 0L;

    private EmailDispatcher() {}

    private static synchronized void rateLimit() {
        long now = System.currentTimeMillis();
        long gap = now - lastSentAt;
        if (gap < MIN_SEND_GAP_MS) {
            long waitMs = MIN_SEND_GAP_MS - gap;
            LogUtil.info(CLASS_NAME, "throttle: sleeping " + waitMs + "ms");
            try { Thread.sleep(waitMs); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        lastSentAt = System.currentTimeMillis();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Send an email driven by a {@code spNotifTemplate} row. Recipient,
     * locale, scheduling, and content all come from the DB row.
     *
     * <p>Caller responsibility: populate {@code vars} with the substitution
     * tokens the operator-authored template uses ({@code {first_name}},
     * {@code {voucher_code}}, etc.) AND with {@code national_id} when the
     * template's resolver is APPLICANT (the dispatcher reads the farmer's
     * email + preferred_language from the registry by national_id).
     *
     * @param eventCode template key (matches {@code triggerEvent})
     * @param fallbackLocale caller's preference ("EN" or "ST") — overridden
     *                       by farmer's {@code preferred_language} when resolver=APPLICANT
     * @param vars      substitution map
     * @return true if at least one recipient was dispatched OR the send was
     *         queued; false if disabled, skipped, or all dispatches failed
     */
    public static boolean sendByEvent(String eventCode, String fallbackLocale, Map<String, String> vars) {
        if (eventCode == null || eventCode.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "sendByEvent: empty eventCode — skipping");
            return false;
        }
        if (vars == null) vars = new HashMap<>();

        TemplateRow tpl = loadTemplate(eventCode);
        if (tpl == null) {
            // Template missing entirely — record a SKIPPED row so operator sees it.
            recordTemplateSkip(eventCode, vars, "no DB template or isActive=N");
            LogUtil.warn(CLASS_NAME, "[" + eventCode + "] no DB template (or isActive=N) — skipping send");
            return false;
        }
        if (!tpl.emailEnabled) {
            recordTemplateSkip(eventCode, vars, "emailEnabled=N (operator disabled the template)");
            LogUtil.info(CLASS_NAME, "[" + eventCode + "] emailEnabled=N — skipping send");
            return false;
        }

        // Scheduling path — defer to NotificationQueueWorker.
        if ("SCHEDULED".equalsIgnoreCase(tpl.sendImmediately) && tpl.delayMinutes > 0) {
            return queueScheduled(eventCode, fallbackLocale, vars, tpl);
        }

        // Immediate dispatch.
        return dispatchImmediate(eventCode, fallbackLocale, vars, tpl);
    }

    /** Write a SKIPPED audit row when the template itself is missing/disabled. */
    private static void recordTemplateSkip(String eventCode, Map<String, String> vars, String reason) {
        String corr = vars.getOrDefault("application_code",
                       vars.getOrDefault("voucher_code",
                        vars.getOrDefault("envelope_code", "")));
        String notifId = NotifAudit.create(eventCode, "EMAIL", "n/a",
                "", "", corr, "EN", "(template " + reason + ")",
                "{}", NotificationConfig.get().testModeActive);
        if (notifId != null) NotifAudit.markSkipped(notifId, reason);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Immediate dispatch — resolver → render → SMTP
    // ──────────────────────────────────────────────────────────────────────

    private static boolean dispatchImmediate(
            String eventCode, String fallbackLocale, Map<String, String> vars, TemplateRow tpl) {

        // Resolve intended recipients first — for logging / subject-line provenance,
        // even when we override them in test mode.
        List<RecipientResolver.Resolved> recipients =
                RecipientResolver.resolve(tpl.recipientResolver, tpl.operatorRecipients,
                                          RecipientResolver.Channel.EMAIL, vars);

        NotificationConfig.Snapshot cfg = NotificationConfig.get();

        // Test-mode override takes precedence: we ALWAYS dispatch to the test
        // inbox when testModeActive=Y, even if the resolver returned zero
        // intended recipients (e.g. the citizen has no email in the registry).
        // The whole point of test mode is to exercise the full lifecycle
        // without delivering to real citizens; refusing to send when the
        // would-be citizen lacks contact details would defeat that purpose.
        java.util.List<RecipientResolver.Resolved> effectiveRecipients;
        String testHeader = "";
        if (cfg.testModeActive) {
            String originals;
            if (recipients.isEmpty()) {
                originals = "<no intended recipient — applicant has no email>";
                LogUtil.info(CLASS_NAME, "[" + eventCode + "] TEST MODE active — "
                        + "resolver " + tpl.recipientResolver + " returned 0 recipients; "
                        + "redirecting to " + cfg.testRecipientEmail + " anyway");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < recipients.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(recipients.get(i).address);
                }
                originals = sb.toString();
                LogUtil.info(CLASS_NAME, "[" + eventCode + "] TEST MODE active — re-routing "
                        + recipients.size() + " recipient(s) to " + cfg.testRecipientEmail
                        + " (originals: " + originals + ")");
            }
            testHeader = "[TEST → " + originals + "] ";

            // One delivery per distinct locale (so EN+ST renderings both get
            // tested when a multi-recipient resolver returns mixed languages).
            // When recipients is empty, fall back to the caller's locale arg.
            java.util.Map<String, RecipientResolver.Resolved> byLocale = new java.util.LinkedHashMap<>();
            if (recipients.isEmpty()) {
                String loc = (fallbackLocale == null || fallbackLocale.isEmpty())
                              ? "EN" : fallbackLocale.toUpperCase();
                byLocale.put(loc, new RecipientResolver.Resolved(
                        cfg.testRecipientEmail, "Test Inbox", loc));
            } else {
                for (RecipientResolver.Resolved r : recipients) {
                    String loc = r.locale.isEmpty() ? "EN" : r.locale;
                    byLocale.putIfAbsent(loc, new RecipientResolver.Resolved(
                            cfg.testRecipientEmail, "Test Inbox", loc));
                }
            }
            effectiveRecipients = new java.util.ArrayList<>(byLocale.values());
        } else {
            // Live mode: no recipients means no send.
            if (recipients.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "[" + eventCode + "] resolver " + tpl.recipientResolver
                        + " returned no recipients — skipping email");
                return false;
            }
            effectiveRecipients = recipients;
        }

        String effFallback = (fallbackLocale == null || fallbackLocale.isEmpty()) ? "EN" : fallbackLocale.toUpperCase();
        String correlationId = vars.getOrDefault("application_code",
                                vars.getOrDefault("voucher_code",
                                 vars.getOrDefault("envelope_code", "")));
        String varsJson = mapToJson(vars);
        // Recipient-status flag for the audit row. Recorded as a separate
        // column (intendedRecipientStatus) so operators searching "why did
        // this go to the test inbox" get an explicit answer instead of
        // inferring it from an empty string.
        String recipientStatus = recipients.isEmpty()
                ? "no_contact_on_registry"
                : "resolved";
        int sent = 0, failed = 0;
        for (RecipientResolver.Resolved r : effectiveRecipients) {
            String loc = !r.locale.isEmpty() ? r.locale : effFallback;
            String[] rendered = render(tpl, loc, vars);
            if (rendered == null) {
                // Missing body for this locale — record SKIPPED for visibility.
                String nid = NotifAudit.create(eventCode, "EMAIL", "n/a",
                        intendedFor(recipients), recipientStatus,
                        r.address, correlationId, loc,
                        "(template body missing for locale " + loc + ")", varsJson,
                        cfg.testModeActive);
                if (nid != null) NotifAudit.markSkipped(nid, "template body missing for locale " + loc);
                LogUtil.warn(CLASS_NAME, "[" + eventCode + "] template missing body for locale " + loc);
                failed++;
                continue;
            }
            String subject = testHeader + rendered[0];
            String backend = resolveBackend();

            // Insert PENDING row before the SMTP call so even a hard SMTP
            // crash leaves an audit trail.
            String notifId = NotifAudit.create(eventCode, "EMAIL", backend,
                    intendedFor(recipients), recipientStatus,
                    r.address, correlationId, loc,
                    subject, varsJson, cfg.testModeActive);

            boolean ok;
            String err = null;
            try {
                ok = sendOne(eventCode, r.address, r.displayName,
                             subject, rendered[1], rendered[2]);
                if (!ok) err = "SMTP returned false (configuration or transient error)";
            } catch (Throwable t) {
                ok = false;
                err = t.getClass().getSimpleName() + ":" + (t.getMessage() == null ? "" : t.getMessage());
            }
            if (ok) {
                NotifAudit.markSent(notifId, "smtp accepted via " + backend);
                sent++;
            } else {
                NotifAudit.markFailed(notifId, err == null ? "unknown" : err);
                failed++;
            }
        }
        LogUtil.info(CLASS_NAME, "[" + eventCode + "] resolver=" + tpl.recipientResolver
                + " testMode=" + cfg.testModeActive + " sent=" + sent + " failed=" + failed);
        return sent > 0;
    }

    /** Comma-joined intended recipients for the audit row (truncated to keep DB column happy). */
    private static String intendedFor(List<RecipientResolver.Resolved> recipients) {
        if (recipients == null || recipients.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recipients.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(recipients.get(i).address);
        }
        String s = sb.toString();
        return s.length() <= 250 ? s : s.substring(0, 247) + "...";
    }

    /**
     * Detect which SMTP backend is configured. Reads Joget's stock SMTP host
     * setting; classifies common providers by host suffix. For unfamiliar
     * hosts returns the host string itself so the audit log retains the info.
     */
    private static String resolveBackend() {
        try {
            org.joget.commons.util.SetupManager sm = (org.joget.commons.util.SetupManager)
                    org.joget.apps.app.service.AppUtil.getApplicationContext().getBean("setupManager");
            String host = sm.getSettingValue("smtpHost");
            if (host == null || host.isEmpty()) return "unconfigured";
            String h = host.toLowerCase();
            if (h.contains("gmail"))     return "gmail";
            if (h.contains("amazonaws")) return "ses";
            if (h.contains("sendgrid"))  return "sendgrid";
            if (h.contains("mailgun"))   return "mailgun";
            if (h.contains("postmark"))  return "postmark";
            if (h.contains("mailtrap"))  return "mailtrap";
            if (h.contains("office365") || h.contains("outlook")) return "office365";
            return host;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** Render {subject, html, plaintext} for the given locale. Returns null if body blank. */
    private static String[] render(TemplateRow tpl, String locale, Map<String, String> vars) {
        boolean useSt = "ST".equalsIgnoreCase(locale)
                     && !isBlank(tpl.subjectSt) && !isBlank(tpl.bodySt);
        String subject = useSt ? tpl.subjectSt : tpl.subjectEn;
        String html    = useSt ? tpl.bodySt    : tpl.bodyEn;
        String plain   = useSt ? tpl.bodyPlaintextSt : tpl.bodyPlaintext;
        if (isBlank(subject) || isBlank(html)) return null;
        String subjectS = substitute(subject, vars);
        String htmlS    = substitute(html,    vars);
        String plainS   = isBlank(plain) ? htmlToPlain(htmlS) : substitute(plain, vars);
        return new String[] { subjectS, htmlS, plainS };
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scheduling path — write to notification_queue
    // ──────────────────────────────────────────────────────────────────────

    private static boolean queueScheduled(
            String eventCode, String fallbackLocale, Map<String, String> vars, TemplateRow tpl) {

        long whenMillis = System.currentTimeMillis() + (long) tpl.delayMinutes * 60_000L;
        String scheduledFor = ISO_UTC.format(new Date(whenMillis));

        // Resolve recipients now (so the queue row carries an explicit address),
        // but the worker will re-resolve at dispatch time so changes to the
        // registry land. This row.address is informational only.
        List<RecipientResolver.Resolved> recipients =
                RecipientResolver.resolve(tpl.recipientResolver, tpl.operatorRecipients,
                                          RecipientResolver.Channel.EMAIL, vars);
        if (recipients.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "[" + eventCode + "] scheduled send: no recipients — not queueing");
            return false;
        }

        String varsJson = mapToJson(vars);
        int queued = 0;
        for (RecipientResolver.Resolved r : recipients) {
            FormRow row = new FormRow();
            row.setProperty("eventCode",    eventCode);
            row.setProperty("toAddress",    r.address);
            row.setProperty("toName",       r.displayName);
            row.setProperty("channel",      "EMAIL");
            row.setProperty("locale",       r.locale.isEmpty() ? (fallbackLocale == null ? "EN" : fallbackLocale.toUpperCase()) : r.locale);
            row.setProperty("varsJson",     varsJson);
            row.setProperty("scheduledFor", scheduledFor);
            row.setProperty("priority",     tpl.priority == null ? "NORMAL" : tpl.priority);
            row.setProperty("status",       "PENDING");
            row.setProperty("attempts",     "0");
            try {
                FormRowSet rs = new FormRowSet();
                rs.add(row);
                global.govstack.regbb.engine.support.RowWriter.save(
                        "notification_queue", "notification_queue", rs);
                queued++;
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "[" + eventCode + "] failed to enqueue for " + r.address);
            }
        }
        LogUtil.info(CLASS_NAME, "[" + eventCode + "] scheduled " + queued + " row(s) for " + scheduledFor);
        return queued > 0;
    }

    // ──────────────────────────────────────────────────────────────────────
    // SMTP send (low-level)
    // ──────────────────────────────────────────────────────────────────────

    private static boolean sendOne(
            String templateId, String toAddress, String toDisplayName,
            String subject, String htmlBody, String plaintextBody) {
        if (toAddress == null || toAddress.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "[" + templateId + "] no recipient — skipping");
            return false;
        }
        rateLimit();
        try {
            HtmlEmail email = AppUtil.createEmail(null, null, null, null, null, null);
            if (email == null) {
                LogUtil.warn(CLASS_NAME, "[" + templateId + "] SMTP not configured");
                return false;
            }
            email.setCharset("UTF-8");
            if (toDisplayName != null && !toDisplayName.isEmpty()) email.addTo(toAddress, toDisplayName);
            else email.addTo(toAddress);
            email.setSubject(subject == null ? "" : subject);
            email.setHtmlMsg(htmlBody == null ? "" : htmlBody);
            email.setTextMsg(plaintextBody == null ? "" : plaintextBody);
            String mid = email.send();
            LogUtil.info(CLASS_NAME, "[" + templateId + "] sent to " + toAddress + " (smtp-id=" + mid + ")");
            return true;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "[" + templateId + "] send failed to " + toAddress);
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Template loader
    // ──────────────────────────────────────────────────────────────────────

    private static TemplateRow loadTemplate(String eventCode) {
        String sql = "SELECT c_templatecode, c_emailsubjecten, c_emailbodyen, "
                   + "       c_emailsubjectst, c_emailbodyst, "
                   + "       c_emailbodyplaintext, c_emailbodyplaintextst, "
                   + "       c_emailenabled, c_isactive, "
                   + "       c_recipientresolver, c_operatorrecipients, "
                   + "       c_sendimmediately, c_delayminutes, c_priority "
                   + "  FROM app_fd_spNotifTemplate "
                   + " WHERE UPPER(c_triggerevent) = UPPER(?) "
                   + "   AND COALESCE(c_isactive, 'Y') = 'Y' "
                   + " LIMIT 1";
        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            try (Connection c = ds.getConnection();
                 PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, eventCode);
                try (ResultSet rs = p.executeQuery()) {
                    if (!rs.next()) return null;
                    TemplateRow t = new TemplateRow();
                    t.templateCode        = rs.getString(1);
                    t.subjectEn           = rs.getString(2);
                    t.bodyEn              = rs.getString(3);
                    t.subjectSt           = rs.getString(4);
                    t.bodySt              = rs.getString(5);
                    t.bodyPlaintext       = rs.getString(6);
                    t.bodyPlaintextSt     = rs.getString(7);
                    t.emailEnabled        = "Y".equalsIgnoreCase(rs.getString(8));
                    t.isActive            = !"N".equalsIgnoreCase(rs.getString(9));
                    t.recipientResolver   = rs.getString(10);
                    t.operatorRecipients  = rs.getString(11);
                    t.sendImmediately     = rs.getString(12);
                    String dm             = rs.getString(13);
                    t.delayMinutes        = (dm == null || dm.isEmpty()) ? 0 : Integer.parseInt(dm);
                    t.priority            = rs.getString(14);
                    return t;
                }
            }
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "loadTemplate(" + eventCode + ") failed: "
                    + e.getClass().getSimpleName() + ":" + e.getMessage());
            return null;
        }
    }

    private static class TemplateRow {
        String templateCode;
        String subjectEn, bodyEn, subjectSt, bodySt;
        String bodyPlaintext, bodyPlaintextSt;
        boolean emailEnabled, isActive;
        String recipientResolver, operatorRecipients;
        String sendImmediately;
        int delayMinutes;
        String priority;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private static String substitute(String text, Map<String, String> vars) {
        if (text == null) return "";
        String out = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private static String htmlToPlain(String html) {
        if (html == null) return "";
        return html
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p>", "\n\n")
            .replaceAll("(?i)</li>", "\n")
            .replaceAll("<[^>]+>", "")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("[ \\t]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /** Minimal JSON serializer — for queue vars only (small flat string→string map). */
    private static String mapToJson(Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(jsonEsc(e.getKey())).append("\":");
            sb.append("\"").append(jsonEsc(e.getValue() == null ? "" : e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
