package global.govstack.regbb.engine.notification;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the recipient list for a notification dispatch from the
 * {@code recipientResolver} column on {@code app_fd_spNotifTemplate}.
 *
 * <p>Five resolver types are supported:
 *
 * <ul>
 *   <li>{@code APPLICANT} — look up the citizen the event is about. Reads
 *       {@code farmer.email_address} (or {@code mobile_number} for SMS) and
 *       {@code preferred_language} from {@code app_fd_farmerbasicinfo}
 *       keyed by {@code vars.get("national_id")}.</li>
 *   <li>{@code OPERATOR_LIST} — split the free-text {@code operatorRecipients}
 *       column on {@code ;} or {@code ,}, trim each entry, drop blanks.</li>
 *   <li>{@code FINANCE_OFFICERS} — every active user in {@code role_finance_officer}
 *       (via {@code dir_user_group} join). Skips users without an email
 *       on file.</li>
 *   <li>{@code FIELD_OFFICER_OF_DISTRICT} — every active user in
 *       {@code role_field_officer}. (District-scoped filtering deferred until
 *       MAFSN assigns districts to user accounts; today it sends to all field
 *       officers and logs the lack of district filter.)</li>
 *   <li>{@code SUPERVISOR_OF_DISTRICT} — every active user in
 *       {@code role_district_supervisor}. (Same district-scoped caveat.)</li>
 * </ul>
 *
 * <p>The resolver returns a list of {@link Resolved} entries; each carries
 * the address, the display name, and the recipient's preferred locale (used
 * by {@code EmailDispatcher} to pick the EN or ST template body). For
 * non-applicant resolvers the locale defaults to {@code "EN"}.
 *
 * <p>Empty result is a valid outcome — the caller logs and skips the send.
 * No exceptions thrown; SQL failures are logged and converted to empty
 * list, never propagated.
 */
public final class RecipientResolver {

    private static final String CLASS_NAME = RecipientResolver.class.getName();

    public enum Channel { EMAIL, SMS }

    /** One resolved recipient. */
    public static final class Resolved {
        public final String address;
        public final String displayName;
        public final String locale;
        public Resolved(String address, String displayName, String locale) {
            this.address     = address;
            this.displayName = displayName == null ? "" : displayName;
            this.locale      = (locale == null || locale.isEmpty()) ? "EN" : locale.toUpperCase();
        }
    }

    private RecipientResolver() {}

    /**
     * Resolve recipients for a template dispatch.
     *
     * @param resolverType   value of the {@code recipientResolver} column
     * @param operatorRecips value of the {@code operatorRecipients} column (only consulted for OPERATOR_LIST)
     * @param channel        EMAIL or SMS — controls whether we read email_address or mobile_number
     * @param vars           substitution map; must contain {@code national_id} for APPLICANT
     * @return non-null list (possibly empty)
     */
    public static List<Resolved> resolve(
            String resolverType,
            String operatorRecips,
            Channel channel,
            Map<String, String> vars) {

        if (resolverType == null || resolverType.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "resolve: empty resolverType — returning empty");
            return Collections.emptyList();
        }
        switch (resolverType.toUpperCase()) {
            case "APPLICANT":                return resolveApplicant(channel, vars);
            case "OPERATOR_LIST":            return resolveOperatorList(operatorRecips, channel);
            case "FINANCE_OFFICERS":         return resolveByRole("role_finance_officer", channel);
            case "FIELD_OFFICER_OF_DISTRICT":return resolveByRole("role_field_officer", channel);
            case "SUPERVISOR_OF_DISTRICT":   return resolveByRole("role_district_supervisor", channel);
            default:
                LogUtil.warn(CLASS_NAME, "resolve: unknown resolverType '" + resolverType + "'");
                return Collections.emptyList();
        }
    }

    // ─── APPLICANT ────────────────────────────────────────────────────────

    private static List<Resolved> resolveApplicant(Channel ch, Map<String, String> vars) {
        String nid = vars == null ? null : vars.get("national_id");
        if (nid == null || nid.isEmpty()) {
            LogUtil.warn(CLASS_NAME,
                    "APPLICANT resolver: vars.national_id missing — cannot look up registry");
            return Collections.emptyList();
        }
        String addrCol = (ch == Channel.SMS) ? "c_mobile_number" : "c_email_address";
        String sql = "SELECT " + addrCol + ", c_first_name, c_last_name, c_preferred_language "
                   + "  FROM app_fd_farmerbasicinfo WHERE c_national_id = ? LIMIT 1";
        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            try (Connection c = ds.getConnection();
                 PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, nid);
                try (ResultSet rs = p.executeQuery()) {
                    if (!rs.next()) {
                        LogUtil.warn(CLASS_NAME, "APPLICANT resolver: no registry row for national_id=" + nid);
                        return Collections.emptyList();
                    }
                    String addr  = rs.getString(1);
                    String first = rs.getString(2);
                    String last  = rs.getString(3);
                    String lang  = rs.getString(4);
                    if (addr == null || addr.trim().isEmpty()) {
                        LogUtil.warn(CLASS_NAME, "APPLICANT resolver: registry row for "
                                + nid + " has no " + (ch == Channel.SMS ? "mobile" : "email"));
                        return Collections.emptyList();
                    }
                    String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
                    String localeCode = mapLanguageToLocale(lang);
                    return Collections.singletonList(new Resolved(addr.trim(), name, localeCode));
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "APPLICANT resolver SQL failed for national_id=" + nid);
            return Collections.emptyList();
        }
    }

    /** Map md02language code to {@code EN}/{@code ST}. */
    private static String mapLanguageToLocale(String lang) {
        if (lang == null) return "EN";
        String x = lang.trim().toLowerCase();
        if (x.startsWith("ses") || x.equals("st") || x.contains("sotho")) return "ST";
        return "EN";
    }

    // ─── OPERATOR_LIST ────────────────────────────────────────────────────

    private static List<Resolved> resolveOperatorList(String operatorRecips, Channel ch) {
        if (operatorRecips == null || operatorRecips.trim().isEmpty()) {
            LogUtil.warn(CLASS_NAME, "OPERATOR_LIST resolver: operatorRecipients column is empty");
            return Collections.emptyList();
        }
        // Split on ; , or whitespace, then de-dup preserving insertion order.
        String[] parts = operatorRecips.split("[;,\\s]+");
        Map<String, Resolved> dedup = new LinkedHashMap<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            // For SMS, look up phone format separately; for now operator-list
            // SMS isn't a real production use-case (operator notifications are
            // email-only by convention). Stored as-is.
            dedup.putIfAbsent(trimmed.toLowerCase(),
                    new Resolved(trimmed, "Operator", "EN"));
        }
        return new ArrayList<>(dedup.values());
    }

    // ─── BY-ROLE (FINANCE_OFFICERS, FIELD_OFFICER_*, SUPERVISOR_*) ───────

    private static List<Resolved> resolveByRole(String roleGroupId, Channel ch) {
        if (ch == Channel.SMS) {
            LogUtil.warn(CLASS_NAME, "by-role resolver for SMS not supported (dir_user has no phone column); "
                    + "use OPERATOR_LIST or fall back to email");
            return Collections.emptyList();
        }
        String sql = "SELECT u.username, u.email, u.firstname, u.lastname, u.locale "
                   + "FROM dir_user u JOIN dir_user_group g ON g.userid = u.id "
                   + "WHERE g.groupid = ? AND COALESCE(u.active,'0') = '1' "
                   + "  AND COALESCE(u.email,'') <> ''";
        List<Resolved> out = new ArrayList<>();
        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            try (Connection c = ds.getConnection();
                 PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, roleGroupId);
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        String email = rs.getString("email");
                        String fn    = rs.getString("firstname");
                        String ln    = rs.getString("lastname");
                        String loc   = rs.getString("locale");
                        String name  = ((fn == null ? "" : fn) + " " + (ln == null ? "" : ln)).trim();
                        if (name.isEmpty()) name = rs.getString("username");
                        out.add(new Resolved(email.trim(), name,
                                mapLanguageToLocale(loc)));
                    }
                }
            }
            if (out.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "by-role resolver " + roleGroupId
                        + ": no active users with email — dispatch will be skipped");
            }
            return out;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "by-role resolver SQL failed for group " + roleGroupId);
            return Collections.emptyList();
        }
    }
}
