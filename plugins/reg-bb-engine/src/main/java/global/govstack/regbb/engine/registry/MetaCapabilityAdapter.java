package global.govstack.regbb.engine.registry;

import global.govstack.regbb.engine.api.CapabilityAdapter;
import global.govstack.regbb.engine.api.EvalContext;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadata-driven capability adapter — replaces the three hard-coded
 * Java adapters (FarmerByNidAdapter / ParcelsSummaryAdapter /
 * SubsidyApplicationCountAdapter) with a single generic class that reads
 * its definitions from {@code mm_capability_field} rows.
 *
 * <p>One instance is registered per {@code mm_capability.code}. At
 * {@link #resolve} time the adapter looks up the field row, builds a
 * SELECT from
 * <pre>
 *   SELECT &lt;expression&gt; FROM &lt;source_table&gt;
 *       [ WHERE &lt;where_clause&gt; ]
 *       [ LIMIT 1 ]   -- when result_type = first_row
 * </pre>
 * The {@code expression} and {@code source_table} are spliced verbatim
 * (analyst-controlled, not citizen-input — see security note below). The
 * {@code where_clause} contains named placeholders {@code :name} which
 * are converted to JDBC {@code ?} positional binds at runtime, with
 * values pulled from:
 * <ul>
 *   <li>{@code :nid} → the {@code nationalId} arg</li>
 *   <li>{@code :applicationId} → {@code ctx.applicationId}</li>
 *   <li>{@code :anyOtherKey} → {@code ctx.data.get("anyOtherKey")}</li>
 * </ul>
 *
 * <p><b>Security caveat.</b> {@code source_table}, {@code expression}, and
 * {@code where_clause} are spliced into the SQL string verbatim — they're
 * trusted because mm_capability_field is authored by an MAFSN
 * administrator via App Composer, not by citizens. The
 * {@code DuplicateValueValidator} on the form prevents accidental row
 * duplication; whitelisting at parse time is a future hardening (open as
 * an ADR if exposing this form to non-trusted authors). Bound parameters
 * (the {@code :placeholder} substitution) ARE safe via PreparedStatement.
 *
 * <p><b>Performance.</b> This first cut runs one SELECT per resolve()
 * call, opening a new Connection per call (matching the legacy adapters).
 * Slice A (next task) adds: shared Connection per evaluation, per-eval
 * field cache, batch field resolution per capability. The metadata read
 * itself is also un-cached today; Slice A will add a 30-second TTL cache
 * on the (capability, field) → MetadataRow lookup.
 *
 * <p><b>Defaults / null contract.</b> Mirrors the legacy adapters:
 * {@code default_when_no_anchor} returned when {@code nationalId} is empty;
 * {@code default_when_no_row} returned when the SELECT returns no rows or
 * a NULL value. Both default to literal null when blank, which propagates
 * through the rule grammar to {@code disposition=indeterminate} per
 * RegBB §4.3.0.1.
 */
public class MetaCapabilityAdapter implements CapabilityAdapter {

    private static final String CLASS_NAME = MetaCapabilityAdapter.class.getName();

    /** Allowed-character pattern for named placeholders. Letters (both
     *  cases), digits, underscores. Same shape as a SQL identifier —
     *  keeps the regex simple. CamelCase is supported because some
     *  context keys arrive that way (e.g. {@code :applicationId} maps
     *  to {@link EvalContext#applicationId}). */
    private static final Pattern PLACEHOLDER = Pattern.compile(":([a-zA-Z][a-zA-Z0-9_]*)");

    private final String capabilityCode;

    public MetaCapabilityAdapter(String capabilityCode) {
        this.capabilityCode = capabilityCode == null ? "" : capabilityCode.toLowerCase();
    }

    @Override
    public String getCapabilityName() {
        return capabilityCode;
    }

    @Override
    public String resolve(String field, String nationalId, EvalContext ctx) throws SQLException {
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("missing_field");
        }
        // Per-EvalContext value cache — same (capability, field, nid) hits
        // many rules in one chain; resolve once.
        String cacheKey = capabilityCode + "." + field + ":" + (nationalId == null ? "" : nationalId)
                + ":" + (ctx != null && ctx.applicationId != null ? ctx.applicationId : "")
                + ":" + (ctx != null && ctx.data != null && ctx.data.get("applied_programme") != null
                        ? ctx.data.get("applied_programme").toString() : "");
        java.util.concurrent.ConcurrentHashMap<String, String> cache =
                (ctx == null) ? null : ctx.capabilityCache();
        if (cache != null) {
            String hit = cache.get(cacheKey);
            if (hit != null) {
                return EvalContext.CACHE_NULL.equals(hit) ? null : hit;
            }
        }

        FieldDef def = readFieldDef(capabilityCode, field);   // metadata-cached
        if (def == null) {
            throw new IllegalArgumentException("unknown_field:" + capabilityCode + "." + field);
        }

        String result;
        if (nationalId == null || nationalId.isEmpty()) {
            result = def.defaultWhenNoAnchor; // may be null → indeterminate
        } else {
            result = executeFieldQuery(def, nationalId, ctx);
        }
        if (cache != null) {
            cache.put(cacheKey, result == null ? EvalContext.CACHE_NULL : result);
        }
        return result;
    }

    /** Run the SELECT for one field, return scalar value or
     *  {@code def.defaultWhenNoRow} (which may be null). */
    private static String executeFieldQuery(FieldDef def, String nationalId, EvalContext ctx)
            throws SQLException {
        String sql = buildSql(def);
        List<String> placeholderOrder = collectPlaceholders(def.whereClause);
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String name : placeholderOrder) {
                stmt.setString(idx++, resolvePlaceholder(name, nationalId, ctx));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    if (rs.wasNull()) return def.defaultWhenNoRow;
                    return v == null ? def.defaultWhenNoRow : v;
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "resolve(" + def.expression + ") "
                    + "failed: " + e.getSQLState() + ":" + e.getMessage());
            throw e;
        }
        return def.defaultWhenNoRow;
    }

    // -----------------------------------------------------------------
    //  Metadata read
    // -----------------------------------------------------------------

    /**
     * Process-wide metadata cache: {@code (capability, field) → FieldDef}.
     * mm_capability_field rows are analyst-authored and rarely change; a
     * 30-second TTL means at most one DB read per (cap,field) pair per
     * 30s window across the JVM. The cost of staleness is small (an
     * analyst saving a new field definition sees their change within 30s).
     *
     * <p>If you NEED instant cache invalidation (e.g. during a seed run),
     * call {@link #invalidateMetaCache()} — _tooling/seed.py could be
     * extended to hit a thin REST endpoint that does this. Today the TTL
     * is enough.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, MetaCacheEntry> META_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long META_TTL_MS = 30_000L;

    private static final class MetaCacheEntry {
        final FieldDef def;       // null if no such row in DB
        final long stampMs;
        MetaCacheEntry(FieldDef def, long stampMs) { this.def = def; this.stampMs = stampMs; }
    }

    /** Invalidate the static metadata cache. Call after seed runs that
     *  change mm_capability_field rows. */
    public static void invalidateMetaCache() { META_CACHE.clear(); }

    /** Read one mm_capability_field row by (capability_code, field_code),
     *  with a 30s TTL cache. Returns null if no such row. */
    private static FieldDef readFieldDef(String capability, String field) throws SQLException {
        String key = capability + "." + field;
        MetaCacheEntry entry = META_CACHE.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && (now - entry.stampMs) < META_TTL_MS) {
            return entry.def;
        }
        String sql = "SELECT c_source_table, c_expression, c_where_clause, "
                   + "       c_result_type, c_default_when_no_anchor, c_default_when_no_row "
                   + "  FROM app_fd_mm_capability_field "
                   + " WHERE c_capability_code = ? AND c_code = ? "
                   + " LIMIT 1";
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        FieldDef d = null;
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, capability);
            stmt.setString(2, field);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    d = new FieldDef();
                    d.sourceTable           = nz(rs.getString(1));
                    d.expression            = nz(rs.getString(2));
                    d.whereClause           = nz(rs.getString(3));
                    d.resultType            = nz(rs.getString(4), "scalar");
                    d.defaultWhenNoAnchor   = trimToNull(rs.getString(5));
                    d.defaultWhenNoRow      = trimToNull(rs.getString(6));
                }
            }
        }
        META_CACHE.put(key, new MetaCacheEntry(d, now));
        return d;
    }

    // -----------------------------------------------------------------
    //  SQL build
    // -----------------------------------------------------------------

    private static String buildSql(FieldDef def) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(def.expression).append(" FROM ").append(def.sourceTable);
        String where = def.whereClause;
        if (where != null && !where.isEmpty()) {
            sql.append(" WHERE ").append(replacePlaceholdersWithQuestionMarks(where));
        }
        if ("first_row".equalsIgnoreCase(def.resultType)) {
            sql.append(" LIMIT 1");
        }
        return sql.toString();
    }

    /** Replace every {@code :name} with {@code ?} in left-to-right order. */
    private static String replacePlaceholdersWithQuestionMarks(String where) {
        StringBuilder out = new StringBuilder(where.length());
        Matcher m = PLACEHOLDER.matcher(where);
        int last = 0;
        while (m.find()) {
            out.append(where, last, m.start()).append('?');
            last = m.end();
        }
        out.append(where, last, where.length());
        return out.toString();
    }

    /** Return placeholder names in left-to-right order (so we can bind
     *  positionally in the same order). Duplicates kept — if a where_clause
     *  references {@code :nid} twice, both binds get the same value. */
    private static List<String> collectPlaceholders(String where) {
        List<String> out = new ArrayList<>();
        if (where == null || where.isEmpty()) return out;
        Matcher m = PLACEHOLDER.matcher(where);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Map a placeholder name to a bind value. {@code :nid} → applicant NID;
     *  {@code :applicationId} → ctx.applicationId; everything else → ctx.data. */
    private static String resolvePlaceholder(String name, String nationalId, EvalContext ctx) {
        if (name == null) return "";
        switch (name) {
            case "nid":           return nationalId == null ? "" : nationalId;
            case "applicationId": return (ctx != null && ctx.applicationId != null) ? ctx.applicationId : "";
            default:
                if (ctx == null || ctx.data == null) return "";
                Object v = ctx.data.get(name);
                if (v == null) v = ctx.data.get(name.toLowerCase());
                return v == null ? "" : v.toString();
        }
    }

    // -----------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------

    private static String nz(String s) { return s == null ? "" : s; }
    private static String nz(String s, String fb) { return (s == null || s.isEmpty()) ? fb : s; }
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final class FieldDef {
        String sourceTable;
        String expression;
        String whereClause;
        String resultType;
        String defaultWhenNoAnchor;
        String defaultWhenNoRow;
    }
}
