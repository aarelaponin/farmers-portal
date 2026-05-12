package global.govstack.regbb.engine.evaluator;

import global.govstack.regbb.engine.api.CapabilityAdapter;
import global.govstack.regbb.engine.api.DeterminantEvaluator;
import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.api.FeeResult;
import global.govstack.regbb.engine.api.RequiredDocResult;
import global.govstack.regbb.engine.api.ScreenEvalResult;
import global.govstack.regbb.engine.dao.MetaModelDao;
import global.govstack.regbb.engine.registry.MetaCapabilityAdapter;
import org.joget.apps.form.model.FormRow;
import org.joget.commons.util.LogUtil;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slice 1B-b SQL-path evaluator. Resolves {@code $registry.<entity>.<field>}
 * references against Joget registry tables via JDBC, substitutes the resolved
 * values into the rule source, then delegates to {@link FastPathEvaluator} to
 * evaluate the now-substituted expression.
 *
 * <p>Per ADR-001 r2 the long-term plan was to bridge to {@code joget-rules-api}'s
 * compiler, but its grammar is its own ANTLR-based DSL distinct from ours;
 * teaching it our determinant DSL is a multi-day integration. This evaluator
 * takes a narrower, focused path: a small regex-pluck-then-substitute that
 * keeps the SQL footprint to single-row lookups by {@code national_id}.
 *
 * <p><b>Supported scopes:</b>
 * <ul>
 *   <li>{@code $registry.farmer.<field>} → reads from {@code app_fd_farmerbasicinfo}</li>
 *   <li>{@code $registry.parcel.<field>} → reads from {@code app_fd_parcelregistration}</li>
 * </ul>
 *
 * <p><b>Behaviour on missing registry row:</b> the SQL returns no row, the
 * value is {@code null}, and the substitution emits the literal token
 * {@code null} into the rule. The fast-path evaluator's null-propagation per
 * spec §4.3.0.1 then turns the comparison into NULL, which the binder maps
 * to {@code disposition=indeterminate}. This is the intended semantics —
 * missing registry data is "we don't know", not "false".
 *
 * <p><b>SQL injection:</b> entity and field identifiers are validated against
 * {@code \w+} before being inlined into the SQL; values use {@link PreparedStatement}.
 * The set of allowed entities is closed (white-list per
 * {@link #entityToTable}); unknown scopes return ERROR.
 */
public class SqlPathEvaluator implements DeterminantEvaluator {

    private static final String CLASS_NAME = SqlPathEvaluator.class.getName();
    private static final String EVAL = "sql";

    /** Matches {@code $registry.<entity>.<field>} where entity and field are
     *  identifier-like (letters, digits, underscores). Case-tolerant so
     *  authoring conventions like {@code $registry.Farmer.firstName} keep
     *  working alongside our snake_case norm. */
    private static final Pattern REGISTRY_REF =
            Pattern.compile("\\$registry\\.([A-Za-z][A-Za-z0-9_]*)\\.([A-Za-z][A-Za-z0-9_]*)");

    private final MetaModelDao dao;
    private final FastPathEvaluator fast;

    /**
     * Capability registry — {@link CapabilityAdapter}s keyed by their
     * declared capability name. Per RegBB spec §3.2 / ADR-020.
     *
     * <p>To add a new capability (e.g. {@code households_vulnerability} per D7,
     * or IM-side capabilities), add a new {@code CapabilityAdapter}
     * implementation under {@code engine.registry/} and one line in
     * {@link #defaultRegistry()}. No other change is required.
     *
     * <p>The registry is per-instance, not per-classloader, so a future
     * IM-module bundle could in principle register additional adapters by
     * holding a reference to the {@code SqlPathEvaluator} — but that's a
     * cross-bundle coupling we don't take today. Adapters live in the
     * engine bundle for Phase 1.
     */
    private final Map<String, CapabilityAdapter> capabilities;

    public SqlPathEvaluator() {
        this.dao = new MetaModelDao();
        this.fast = new FastPathEvaluator(dao);
        this.capabilities = defaultRegistry();
    }

    public SqlPathEvaluator(MetaModelDao dao, FastPathEvaluator fast) {
        this.dao = dao;
        this.fast = fast;
        this.capabilities = defaultRegistry();
    }

    /** L2-3 — exposed accessor for the capability registry so the bot_pull
     *  endpoint (and any future caller that wants direct value resolution
     *  without going through determinant evaluation) can look up an adapter
     *  by name and call its {@code resolve} directly. Lookup is
     *  case-insensitive, mirroring the lookup path used by
     *  {@link #evaluate} on every {@code $registry.<name>.<field>} hit. */
    public CapabilityAdapter getCapability(String name) {
        if (name == null) return null;
        return capabilities.get(name.toLowerCase());
    }

    /** Custom registry override — used by tests and (in future) by modules
     *  registering their own capabilities. */
    public SqlPathEvaluator(MetaModelDao dao, FastPathEvaluator fast,
                            List<CapabilityAdapter> adapters) {
        this.dao = dao;
        this.fast = fast;
        this.capabilities = new LinkedHashMap<>();
        if (adapters != null) {
            for (CapabilityAdapter a : adapters) {
                if (a != null && a.getCapabilityName() != null) {
                    this.capabilities.put(a.getCapabilityName().toLowerCase(), a);
                }
            }
        }
    }

    /** Default registry — metadata-driven since L2-1 follow-up.
     *
     *  <p>Reads {@code app_fd_mm_capability} at construction time and
     *  registers one {@link MetaCapabilityAdapter} per row. The adapter
     *  itself reads {@code app_fd_mm_capability_field} on every resolve()
     *  to find the SQL definition. Adding a new capability is now a
     *  YAML/seed edit + push — no Java change, no plugin rebuild.
     *
     *  <p>The three legacy hard-coded adapters
     *  ({@code FarmerByNidAdapter}, {@code ParcelsSummaryAdapter},
     *  {@code SubsidyApplicationCountAdapter}) remain in the package for
     *  reference but are no longer registered. Their behaviour was
     *  replicated as {@code mm_capability_field} rows in
     *  {@code lesotho-mm-fixture.yaml}.
     *
     *  <p>If {@code app_fd_mm_capability} is empty (e.g. fresh install
     *  before seed) or unreadable, returns an empty registry. Rule
     *  evaluation that hits a $registry reference will surface a clear
     *  {@code unknown_registry_capability} error pointing at the empty
     *  registry — louder than silently passing.
     */
    private static Map<String, CapabilityAdapter> defaultRegistry() {
        Map<String, CapabilityAdapter> r = new LinkedHashMap<>();
        String sql = "SELECT DISTINCT c_code FROM app_fd_mm_capability "
                   + " WHERE c_code IS NOT NULL AND c_code <> ''";
        try {
            javax.sql.DataSource ds = (javax.sql.DataSource) org.joget.apps.app.service.AppUtil
                    .getApplicationContext().getBean("setupDataSource");
            try (java.sql.Connection c = ds.getConnection();
                 java.sql.PreparedStatement p = c.prepareStatement(sql);
                 java.sql.ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString(1);
                    if (code == null || code.isEmpty()) continue;
                    r.put(code.toLowerCase(), new MetaCapabilityAdapter(code.toLowerCase()));
                }
            }
        } catch (Throwable t) {
            org.joget.commons.util.LogUtil.warn(CLASS_NAME,
                "defaultRegistry: failed to read mm_capability rows ("
                + t.getClass().getSimpleName() + ":" + t.getMessage()
                + "); rule evaluation will see an empty registry until seed runs");
        }
        return r;
    }

    @Override
    public EvalResult evaluate(String determinantCode, EvalContext ctx) {
        if (determinantCode == null || determinantCode.isEmpty()) {
            return EvalResult.error("missing_determinant_code", EVAL);
        }
        try {
            FormRow det = dao.findDeterminantByCode(determinantCode);
            if (det == null) return EvalResult.error("determinant_not_found:" + determinantCode, EVAL);

            String source = unwrapSource(prop(det, "ruleJson"));
            if (source == null || source.trim().isEmpty()) {
                return EvalResult.error("rule_source_empty", EVAL);
            }

            // 1. Find every distinct $registry.<entity>.<field> ref.
            Set<String> refs = new LinkedHashSet<>();
            Matcher m = REGISTRY_REF.matcher(source);
            while (m.find()) refs.add(m.group());

            // 2. Resolve each via JDBC. Single-row SELECT joined by national_id.
            String applicantNid = readApplicantNationalId(ctx);
            if (applicantNid == null || applicantNid.isEmpty()) {
                // No anchor — every $registry ref resolves to null. Substituted
                // literal "null" propagates per spec §4.3.0.1.
                LogUtil.warn(CLASS_NAME, "evaluate(" + determinantCode + "): no applicant national_id in EvalContext; registry refs resolve to null");
            }
            Map<String, String> resolved = new HashMap<>();
            for (String ref : refs) {
                Matcher mr = REGISTRY_REF.matcher(ref);
                if (!mr.matches()) continue;  // shouldn't happen; pattern came from this regex
                String capability = mr.group(1).toLowerCase();
                String field      = mr.group(2);
                CapabilityAdapter adapter = capabilities.get(capability);
                if (adapter == null) {
                    return EvalResult.error("unknown_registry_capability:" + capability
                            + " (registered: " + capabilities.keySet() + ")", EVAL);
                }
                try {
                    resolved.put(ref, adapter.resolve(field, applicantNid, ctx));
                } catch (IllegalArgumentException badArg) {
                    return EvalResult.error("invalid_registry_ref:" + badArg.getMessage(), EVAL);
                } catch (SQLException sqle) {
                    LogUtil.error(CLASS_NAME, sqle, "SqlPathEvaluator JDBC failure for " + ref);
                    // Surface the actual Postgres message + SQLState so audit
                    // and operator review have something diagnosable instead of
                    // the bare class name. Trim/clean to keep the audit cell
                    // reasonable in size.
                    String msg = sqle.getMessage();
                    if (msg != null) {
                        msg = msg.replaceAll("\\s+", " ").trim();
                        if (msg.length() > 200) msg = msg.substring(0, 200) + "...";
                    }
                    return EvalResult.error("sql_error:" + sqle.getClass().getSimpleName()
                            + ":" + sqle.getSQLState() + ":" + msg, EVAL);
                } catch (Exception ex) {
                    LogUtil.error(CLASS_NAME, ex, "Capability adapter '" + capability
                            + "' failed for " + ref);
                    return EvalResult.error("adapter_error:" + capability + ":"
                            + ex.getClass().getSimpleName(), EVAL);
                }
            }

            // 3. Substitute resolved values back into the source. Quote strings,
            //    use the literal `null` for missing rows.
            String substituted = substituteRefs(source, resolved);

            // 4. Hand off to FastPathEvaluator for the actual operator evaluation.
            //    Re-tag the result's evaluator field so callers see "sql" — the
            //    audit trail correctly attributes registry-touching evaluations
            //    to this path even though the operator-application step ran
            //    through fast-path code.
            EvalResult fastResult = fast.evaluateExpr(substituted, ctx);
            return new EvalResult(fastResult.outcome,
                    fastResult.numericValue,
                    fastResult.actionTarget,
                    EVAL,
                    fastResult.errorCause);

        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "SqlPathEvaluator.evaluate failed for " + determinantCode);
            return EvalResult.error("internal:" + t.getClass().getSimpleName(), EVAL);
        }
    }

    @Override
    public ScreenEvalResult evaluateScreen(String screenId, EvalContext ctx) {
        return new ScreenEvalResult(Collections.emptyMap(), false, "evaluateScreen_not_implemented_in_sql_path");
    }

    @Override
    public FeeResult computeFees(String serviceId, EvalContext ctx) {
        return new FeeResult(0.0, "LSL", Collections.emptyList());
    }

    @Override
    public List<RequiredDocResult> resolveRequiredDocs(String serviceId, EvalContext ctx) {
        return Collections.emptyList();
    }

    @Override
    public void invalidate(String applicationId) { /* no per-app cache yet */ }

    @Override
    public void invalidateService(String serviceId) { /* no per-service cache yet */ }

    // ------------------------------------------------------------------
    //  JDBC + substitution helpers
    // ------------------------------------------------------------------

    /** Read the applicant's national_id from the EvalContext data map.
     *  Postgres-folding tolerant: tries the canonical key then lowercase. */
    private static String readApplicantNationalId(EvalContext ctx) {
        if (ctx == null || ctx.data == null) return null;
        Object v = ctx.data.get("national_id");
        if (v == null) v = ctx.data.get("nationalid");
        return v == null ? null : v.toString();
    }

    /** Replace {@code $registry.<entity>.<field>} occurrences with their
     *  resolved literal form. {@code null} → the bareword {@code null};
     *  strings → quoted with the quote character that doesn't appear in the
     *  value. Numeric values are NOT detected — we always emit string
     *  literals.
     *
     *  <p><b>Why we don't use SQL-style {@code ''} escape.</b> The DSL
     *  tokeniser in {@link FastPathEvaluator#tokenise} does not support
     *  escape sequences inside quoted strings — it greedily reads characters
     *  until it sees the matching quote char. So {@code 'Mants''ali'}
     *  tokenises as {@code 'Mants'} + {@code 'ali'} + leftover {@code '},
     *  which the parser then mistakes for two atoms separated by an
     *  "operator" called "{@code 'ali'}". Verified May 2026 (L4-1 D-002):
     *  Mants'ali Panyane's PRG_001 evaluation hit
     *  {@code parse_error:unsupported operator: 'ali'}.
     *
     *  <p>Fix: pick the quote character based on which one isn't in the
     *  value. Sotho/African names commonly contain apostrophes
     *  ({@code 'Mants'ali}, {@code Ts'oele}, {@code N'gosi}) but rarely
     *  double quotes; the DSL parser accepts both quote styles
     *  symmetrically. If the value somehow contains both, we fall back to
     *  stripping the rare-double-quote — that's lossy but parseable, and
     *  the fundamental fix would be DSL escape-sequence support which is
     *  ADR-territory not a quick patch. */
    static String substituteRefs(String source, Map<String, String> resolved) {
        if (source == null) return null;
        if (resolved == null || resolved.isEmpty()) return source;
        String out = source;
        for (Map.Entry<String, String> e : resolved.entrySet()) {
            String ref = e.getKey();
            String val = e.getValue();
            String literal;
            if (val == null) {
                literal = "null";
            } else if (val.indexOf('\'') < 0) {
                literal = "'" + val + "'";
            } else if (val.indexOf('"') < 0) {
                literal = "\"" + val + "\"";
            } else {
                // Both quote types present — strip double quotes (rarer)
                // and emit single-quoted. Logged so operators can see when
                // this lossy fallback fires.
                String safe = val.replace("\"", "");
                literal = "'" + safe + "'";
                LogUtil.warn(CLASS_NAME,
                    "substituteRefs: value for " + ref + " contains both ' and \" — "
                  + "stripping \" to keep DSL parseable. Original value preserved in "
                  + "audit inputs_json; this only affects rule comparison.");
            }
            // Quote the ref for replaceAll — it contains $ and dots which are regex metachars.
            out = out.replace(ref, literal);
        }
        return out;
    }

    /** Mirror of {@link FastPathEvaluator#unwrapSource}; kept private here to
     *  avoid a cross-class call. Both paths read the same {@code ruleJson}
     *  storage so they need to handle the legacy {"dsl":"..."} envelope
     *  identically. */
    private static String unwrapSource(String stored) {
        if (stored == null) return null;
        String s = stored.trim();
        if (s.startsWith("{") && s.contains("\"dsl\"")) {
            int q1 = s.indexOf("\"dsl\"");
            int colon = s.indexOf(':', q1);
            if (colon > 0) {
                int v1 = s.indexOf('"', colon + 1);
                int v2 = v1 > 0 ? s.indexOf('"', v1 + 1) : -1;
                if (v1 > 0 && v2 > v1) return s.substring(v1 + 1, v2);
            }
        }
        return s;
    }

    private static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return v == null ? null : v.toString();
    }
}
