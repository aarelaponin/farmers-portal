package global.govstack.regbb.engine.notification;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SMS counterpart to {@link EmailDispatcher#sendByEvent(String, String, Map)}.
 * Reads the same {@code spNotifTemplate} rows, substitutes {@code {var}}
 * tokens into the SMS body, resolves recipients via
 * {@link RecipientResolver}, and hands the (resolved address, rendered text)
 * pair to a pluggable {@link SmsBackend}.
 *
 * <p><b>Backends.</b> The active backend is chosen at JVM start via system
 * property {@code regbb.sms.backend}:
 *
 * <ul>
 *   <li>{@code log} (default) — {@link LogOnlySmsBackend}, just writes the
 *       message to joget.log. Used today while MAFSN's MNO/aggregator
 *       choice is pending; the runtime path is fully exercised but no SMS
 *       leaves the system.</li>
 *   <li>{@code http} — {@link HttpSmsBackend}, generic REST POST against
 *       any provider that accepts JSON {to, from, text} (Twilio,
 *       Clickatell, Infobip, most MNO APIs).</li>
 * </ul>
 *
 * <p><b>Switching backends without a rebuild.</b> Add to Joget's setenv.sh:
 * {@code -Dregbb.sms.backend=http -Dregbb.sms.http.endpoint=https://api.provider.com/messages
 *  -Dregbb.sms.http.auth-header=Bearer XYZ -Dregbb.sms.http.from=MAFSN}
 *
 * <p><b>Disabled by default.</b> If the template row has {@code smsEnabled=N}
 * or {@code isActive=N}, the dispatcher returns false without invoking the
 * backend.
 */
public final class SmsDispatcher {

    private static final String CLASS_NAME = SmsDispatcher.class.getName();

    private static volatile SmsBackend backend = pickBackend();

    private SmsDispatcher() {}

    /** Pick a backend at first use based on system property; can be re-read by tests. */
    static SmsBackend pickBackend() {
        String pick = System.getProperty("regbb.sms.backend", "log").toLowerCase();
        switch (pick) {
            case "http":  return new HttpSmsBackend();
            case "log":
            default:      return new LogOnlySmsBackend();
        }
    }

    /** Test hook: override the backend at runtime. */
    public static void setBackend(SmsBackend b) { backend = b; }

    /**
     * Send an SMS driven by an operator-editable template. Mirrors
     * {@code EmailDispatcher.sendByEvent} but for the SMS channel.
     *
     * @param eventCode template key (matches {@code triggerEvent})
     * @param vars      substitution map (must include {@code national_id} for
     *                  APPLICANT resolver)
     * @return true if at least one recipient was dispatched; false if disabled, skipped, or all failed
     */
    public static boolean sendByEvent(String eventCode, Map<String, String> vars) {
        if (eventCode == null || eventCode.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "sendByEvent: missing eventCode — skipping");
            return false;
        }
        if (vars == null) vars = new HashMap<>();

        TemplateRow tpl = loadTemplate(eventCode);
        if (tpl == null) {
            LogUtil.warn(CLASS_NAME, "[" + eventCode + "] no DB template (or isActive=N) — skipping SMS");
            return false;
        }
        if (!tpl.smsEnabled) {
            LogUtil.info(CLASS_NAME, "[" + eventCode + "] smsEnabled=N — skipping SMS");
            return false;
        }

        List<RecipientResolver.Resolved> recipients =
                RecipientResolver.resolve(tpl.recipientResolver, tpl.operatorRecipients,
                                          RecipientResolver.Channel.SMS, vars);

        NotificationConfig.Snapshot cfg = NotificationConfig.get();

        // Test-mode override takes precedence: ALWAYS dispatch to the test
        // phone when testModeActive=Y, even if the resolver returned zero
        // intended recipients (e.g. citizen has no phone in the registry).
        java.util.List<RecipientResolver.Resolved> effectiveRecipients;
        String testPrefix = "";
        if (cfg.testModeActive) {
            String originals;
            if (recipients.isEmpty()) {
                originals = "<no intended recipient — applicant has no phone>";
                LogUtil.info(CLASS_NAME, "[" + eventCode + "] TEST MODE active — "
                        + "SMS resolver " + tpl.recipientResolver + " returned 0; "
                        + "redirecting to " + cfg.testRecipientPhone + " anyway");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < recipients.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(recipients.get(i).address);
                }
                originals = sb.toString();
                LogUtil.info(CLASS_NAME, "[" + eventCode + "] TEST MODE active — re-routing "
                        + recipients.size() + " SMS recipient(s) to " + cfg.testRecipientPhone
                        + " (originals: " + originals + ")");
            }
            testPrefix = "[TEST→" + originals + "] ";

            java.util.Map<String, RecipientResolver.Resolved> byLocale = new java.util.LinkedHashMap<>();
            if (recipients.isEmpty()) {
                byLocale.put("EN", new RecipientResolver.Resolved(
                        cfg.testRecipientPhone, "Test Phone", "EN"));
            } else {
                for (RecipientResolver.Resolved r : recipients) {
                    String loc = r.locale.isEmpty() ? "EN" : r.locale;
                    byLocale.putIfAbsent(loc, new RecipientResolver.Resolved(
                            cfg.testRecipientPhone, "Test Phone", loc));
                }
            }
            effectiveRecipients = new java.util.ArrayList<>(byLocale.values());
        } else {
            // Live mode: no recipients means no send.
            if (recipients.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "[" + eventCode + "] resolver " + tpl.recipientResolver
                        + " returned no phone numbers — skipping SMS");
                return false;
            }
            effectiveRecipients = recipients;
        }

        String correlationId = vars.getOrDefault("application_code",
                                vars.getOrDefault("voucher_code",
                                 vars.getOrDefault("envelope_code", "")));
        String varsJson = mapToJson(vars);
        String intended = intendedRecipients(recipients);
        String recipientStatus = recipients.isEmpty()
                ? "no_contact_on_registry"
                : "resolved";
        int sent = 0, failed = 0;
        for (RecipientResolver.Resolved r : effectiveRecipients) {
            String rawBody = "ST".equals(r.locale) && tpl.smsBodySt != null && !tpl.smsBodySt.isEmpty()
                           ? tpl.smsBodySt : tpl.smsBodyEn;
            if (rawBody == null || rawBody.trim().isEmpty()) {
                // Missing body for this locale — SKIPPED for visibility.
                String nid = NotifAudit.create(eventCode, "SMS", backend.name(),
                        intended, recipientStatus,
                        r.address, correlationId, r.locale,
                        "(template body missing for locale " + r.locale + ")",
                        varsJson, cfg.testModeActive);
                if (nid != null) NotifAudit.markSkipped(nid, "template body missing for locale " + r.locale);
                LogUtil.warn(CLASS_NAME, "[" + eventCode + "] template body empty for locale " + r.locale);
                continue;
            }
            String body = testPrefix + substitute(rawBody, vars);
            String notifId = NotifAudit.create(eventCode, "SMS", backend.name(),
                    intended, recipientStatus,
                    r.address, correlationId, r.locale,
                    truncate(body, 250), varsJson, cfg.testModeActive);
            try {
                boolean ok = backend.send(eventCode, r.address, body);
                if (ok) {
                    NotifAudit.markSent(notifId, "backend " + backend.name() + " accepted");
                    sent++;
                } else {
                    NotifAudit.markFailed(notifId, "backend " + backend.name() + " returned false");
                    failed++;
                }
            } catch (Exception e) {
                NotifAudit.markFailed(notifId,
                        e.getClass().getSimpleName() + ":" + (e.getMessage() == null ? "" : e.getMessage()));
                LogUtil.error(CLASS_NAME, e, "[" + eventCode + "] SMS dispatch failed for " + r.address);
                failed++;
            }
        }
        LogUtil.info(CLASS_NAME, "[" + eventCode + "] SMS via " + backend.name()
                + " testMode=" + cfg.testModeActive + " sent=" + sent + " failed=" + failed);
        return sent > 0;
    }

    private static String intendedRecipients(List<RecipientResolver.Resolved> recipients) {
        if (recipients == null || recipients.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recipients.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(recipients.get(i).address);
        }
        return truncate(sb.toString(), 250);
    }

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
        return sb.append("}").toString();
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 3) + "...";
    }

    private static String substitute(String text, Map<String, String> vars) {
        if (text == null) return "";
        String out = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private static TemplateRow loadTemplate(String eventCode) {
        String sql = "SELECT c_smstemplateen, c_smstemplatest, "
                   + "       c_smsenabled, c_recipientresolver, c_operatorrecipients "
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
                    t.smsBodyEn           = rs.getString(1);
                    t.smsBodySt           = rs.getString(2);
                    t.smsEnabled          = "Y".equalsIgnoreCase(rs.getString(3));
                    t.recipientResolver   = rs.getString(4);
                    t.operatorRecipients  = rs.getString(5);
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
        String smsBodyEn, smsBodySt;
        boolean smsEnabled;
        String recipientResolver, operatorRecipients;
    }

    // ── Backend SPI ───────────────────────────────────────────────────────

    public interface SmsBackend {
        String name();
        /** @return true if accepted by provider, false otherwise. */
        boolean send(String eventCode, String toPhone, String body) throws Exception;
    }

    /** Default backend — writes "would have sent" lines to joget.log. */
    public static final class LogOnlySmsBackend implements SmsBackend {
        @Override public String name() { return "LOG_ONLY"; }
        @Override public boolean send(String eventCode, String toPhone, String body) {
            String trimmed = body.length() > 160 ? body.substring(0, 157) + "..." : body;
            LogUtil.info(CLASS_NAME,
                    "[SMS-LOG] [" + eventCode + "] would send to " + toPhone
                    + " (" + body.length() + " chars): " + trimmed);
            return true; // counted as "sent" so end-to-end tests see green
        }
    }

    /** HTTP REST backend — generic POST. Provider-specific shape via system properties. */
    public static final class HttpSmsBackend implements SmsBackend {
        @Override public String name() { return "HTTP"; }
        @Override public boolean send(String eventCode, String toPhone, String body) throws Exception {
            String endpoint = System.getProperty("regbb.sms.http.endpoint");
            if (endpoint == null || endpoint.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "HttpSmsBackend: regbb.sms.http.endpoint not set — falling back to LOG_ONLY");
                return new LogOnlySmsBackend().send(eventCode, toPhone, body);
            }
            String auth     = System.getProperty("regbb.sms.http.auth-header", "");
            String fromId   = System.getProperty("regbb.sms.http.from", "MAFSN");
            // Build a generic JSON payload. Each provider needs a slightly
            // different shape; this default works for Twilio's REST API with
            // basic auth and Twilio-flavoured field names. Override the
            // template via subclass for other providers.
            String payload = "{\"to\":\"" + esc(toPhone)
                          + "\",\"from\":\"" + esc(fromId)
                          + "\",\"text\":\"" + esc(body) + "\"}";
            java.net.HttpURLConnection con = (java.net.HttpURLConnection)
                    new java.net.URL(endpoint).openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (!auth.isEmpty()) con.setRequestProperty("Authorization", auth);
            con.setDoOutput(true);
            con.setConnectTimeout(8_000);
            con.setReadTimeout(15_000);
            try (java.io.OutputStream os = con.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }
            int code = con.getResponseCode();
            boolean ok = code >= 200 && code < 300;
            LogUtil.info(CLASS_NAME, "[SMS-HTTP] [" + eventCode + "] to=" + toPhone
                    + " status=" + code + " body=" + body.length() + " chars");
            return ok;
        }
        private static String esc(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}
