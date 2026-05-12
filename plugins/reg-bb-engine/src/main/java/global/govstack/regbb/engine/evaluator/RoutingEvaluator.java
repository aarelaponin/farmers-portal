package global.govstack.regbb.engine.evaluator;

import global.govstack.regbb.engine.api.DeterminantEvaluator;
import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.api.FeeResult;
import global.govstack.regbb.engine.api.RequiredDocResult;
import global.govstack.regbb.engine.api.ScreenEvalResult;
import global.govstack.regbb.engine.audit.AuditWriter;
import global.govstack.regbb.engine.cache.L2Cache;
import global.govstack.regbb.engine.dao.MetaModelDao;
import org.joget.apps.form.model.FormRow;
import org.joget.commons.util.LogUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per ADR-001 r2 single entry point. Routes per-rule between two internal
 * evaluators by static AST analysis:
 *
 * <ul>
 *   <li>Rule source mentions {@code $registry.*} → {@link SqlPathEvaluator}
 *       (slice 1B-b — pre-resolves registry references via JDBC then hands
 *       back to fast-path).</li>
 *   <li>Otherwise → {@link FastPathEvaluator} (slices 1A / 1B-a — pure
 *       in-memory tree walker over {@code $applicant.*} and
 *       {@code $constant.*}).</li>
 * </ul>
 *
 * <p>Routing is decided <i>once</i> per determinant code per JVM (the source
 * doesn't change without a fixture re-seed which is itself an explicit step).
 * The decision is cached in {@code routingDecisions} so subsequent evaluations
 * skip the AST inspection.
 *
 * <p>Slice 1A's binder spoke directly to {@link FastPathEvaluator}; slice 1B-b
 * upgrades the binder to instantiate this class instead so both paths are
 * available without touching downstream code.
 */
public class RoutingEvaluator implements DeterminantEvaluator {

    private static final String CLASS_NAME = RoutingEvaluator.class.getName();

    private static final String ROUTE_FAST = "fast";
    private static final String ROUTE_SQL  = "sql";

    private final MetaModelDao dao;
    private final FastPathEvaluator fast;
    private final SqlPathEvaluator  sql;
    private final Map<String, String> routingDecisions = new ConcurrentHashMap<>();

    // L4-5 — per-render L1 cache scoped via ThreadLocal.
    //
    // The L2 Caffeine cache (slice 1B-d) helps warm renders but cold-start
    // hits go through full fast/sql dispatch. On a single render the same
    // (determinantCode, ctx-data-hash) tuple is often evaluated multiple
    // times — e.g. DET_BLOCK_FARMING_MEMBER drives visibility of cooperative_name
    // (one eval) AND requiredness of cooperative_name (another eval) AND may
    // be referenced by other fields. This cache deduplicates those calls
    // within one render scope, regardless of whether the L2 cache is cold.
    //
    // Lifecycle mirrors MetaModelDao's request scope: beginRequest creates
    // an empty map, endRequest clears. Nested calls share one map (the
    // outer caller owns it). Calls outside an active scope bypass the cache
    // — direct dispatch with full audit, just as before.
    //
    // Audit semantics: deduped calls do NOT write a fresh audit row for
    // each repeated invocation. The audit captures one row per FIRST
    // evaluation per render, plus the L2 hits across renders. This is the
    // correct semantic — the operator audit list shouldn't show twenty
    // identical rows because one render happened to evaluate the same
    // rule for visibility-then-required.
    private static final ThreadLocal<Map<String, EvalResult>> L1_CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> L1_DEPTH = ThreadLocal.withInitial(() -> 0);

    /** Open the per-render L1 cache. Ref-counted (see MetaModelDao for the
     *  same idiom). MetaWizardElement / MetaScreenElement renderTemplate
     *  open this alongside the DAO cache. */
    public static void beginRequest() {
        int d = L1_DEPTH.get();
        if (d == 0) L1_CACHE.set(new HashMap<>());
        L1_DEPTH.set(d + 1);
    }

    /** Close the per-render L1 cache. Always paired in try/finally. */
    public static void endRequest() {
        int d = L1_DEPTH.get();
        if (d <= 1) {
            L1_CACHE.remove();
            L1_DEPTH.remove();
        } else {
            L1_DEPTH.set(d - 1);
        }
    }

    public RoutingEvaluator() {
        this.dao  = new MetaModelDao();
        this.fast = new FastPathEvaluator(dao);
        this.sql  = new SqlPathEvaluator(dao, fast);
    }

    public RoutingEvaluator(MetaModelDao dao, FastPathEvaluator fast, SqlPathEvaluator sql) {
        this.dao = dao;
        this.fast = fast;
        this.sql = sql;
    }

    @Override
    public EvalResult evaluate(String determinantCode, EvalContext ctx) {
        if (determinantCode == null || determinantCode.isEmpty()) {
            EvalResult err = EvalResult.error("missing_determinant_code", ROUTE_FAST);
            // Audit even the malformed call — operators want to see "someone
            // asked us to evaluate an empty code at 14:35:02".
            long now = System.currentTimeMillis();
            AuditWriter.write("(missing)", ctx, err, "(missing)", now, now);
            return err;
        }

        // L1 cache — per-render dedup. If this exact (code, ctx-data) was
        // already evaluated within the current render scope, return the
        // memoised result without re-dispatching, re-fetching the rule, or
        // re-writing the audit. Bypassed when no scope is active.
        Map<String, EvalResult> l1 = L1_CACHE.get();
        String l1Key = null;
        if (l1 != null) {
            int dataHash = (ctx == null || ctx.data == null) ? 0 : ctx.data.hashCode();
            l1Key = determinantCode + "|" + Integer.toHexString(dataHash);
            EvalResult hit = l1.get(l1Key);
            if (hit != null) return hit;
        }

        long startedAt = System.currentTimeMillis();

        // Snapshot the rule source up front — needed for both the L2 cache key
        // (so rule edits invalidate naturally) and the audit row.
        String ruleSource;
        try {
            FormRow det = dao.findDeterminantByCode(determinantCode);
            ruleSource = det == null ? "(rule no longer in DB)"
                    : String.valueOf(det.getProperty("ruleJson"));
        } catch (Throwable t) {
            ruleSource = "(rule snapshot read failed: " + t.getClass().getSimpleName() + ")";
        }

        // L2 cache lookup. Hits short-circuit fast/sql dispatch entirely.
        // We still emit an audit row on hit (with ruleSource and timing of
        // the cached eval, not the original) so operators can see hit-rate.
        // The audit-row evaluator field is suffixed with "+l2hit" to make
        // hits visually distinct in the audit list.
        String cacheKey = L2Cache.buildKey(determinantCode, ctx, ruleSource);
        EvalResult cached = L2Cache.get(cacheKey);
        if (cached != null) {
            long finishedAt = System.currentTimeMillis();
            EvalResult tagged = new EvalResult(cached.outcome, cached.numericValue,
                    cached.actionTarget,
                    (cached.evaluator == null ? "" : cached.evaluator) + "+l2hit",
                    cached.errorCause);
            AuditWriter.write(determinantCode, ctx, tagged, ruleSource, startedAt, finishedAt);
            if (l1 != null) l1.put(l1Key, cached);
            return cached;
        }

        // Cache miss — dispatch to the right evaluator.
        String route = routingDecisions.computeIfAbsent(determinantCode, this::pickRouteFor);
        EvalResult result;
        if (ROUTE_SQL.equals(route)) {
            result = sql.evaluate(determinantCode, ctx);
        } else {
            result = fast.evaluate(determinantCode, ctx);
        }
        long finishedAt = System.currentTimeMillis();

        // Cache the result for subsequent calls within the TTL window.
        L2Cache.put(cacheKey, result);
        if (l1 != null) l1.put(l1Key, result);

        AuditWriter.write(determinantCode, ctx, result, ruleSource, startedAt, finishedAt);
        return result;
    }

    /** First-evaluation routing decision. Inspects the rule source for any
     *  {@code $registry.*} token; presence forces the SQL path, absence keeps
     *  the rule on fast-path. We deliberately don't try to be clever
     *  (e.g. partially fast-path the {@code $applicant.*} subtree and only
     *  SQL-path the registry subtree) — slice 1B-b's contract is "any
     *  $registry ref → whole rule via SQL path, after registry resolution".
     *  Slice 1B-d (later) can introduce mixed-mode evaluation if profiling
     *  shows it's worth the complexity. */
    private String pickRouteFor(String determinantCode) {
        try {
            FormRow det = dao.findDeterminantByCode(determinantCode);
            if (det == null) {
                LogUtil.warn(CLASS_NAME, "pickRouteFor(" + determinantCode + "): determinant not found; defaulting to fast");
                return ROUTE_FAST;
            }
            String source = String.valueOf(det.getProperty("ruleJson"));
            return source != null && source.contains("$registry") ? ROUTE_SQL : ROUTE_FAST;
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "pickRouteFor(" + determinantCode + ") failed: " + t.getMessage()
                    + "; defaulting to fast");
            return ROUTE_FAST;
        }
    }

    @Override
    public ScreenEvalResult evaluateScreen(String screenId, EvalContext ctx) {
        // Slice 1B-b uses fast-path's behaviour for screen evaluation. Once
        // documents-screen fee/required-doc rules touch the registry, this
        // will need its own routing logic.
        return fast.evaluateScreen(screenId, ctx);
    }

    @Override
    public FeeResult computeFees(String serviceId, EvalContext ctx) {
        return fast.computeFees(serviceId, ctx);
    }

    @Override
    public List<RequiredDocResult> resolveRequiredDocs(String serviceId, EvalContext ctx) {
        return fast.resolveRequiredDocs(serviceId, ctx);
    }

    @Override
    public void invalidate(String applicationId) {
        fast.invalidate(applicationId);
        sql.invalidate(applicationId);
        L2Cache.invalidateApplication(applicationId);
        // The routing cache is per-determinant-code, not per-applicationId, so
        // we don't drop it here. A re-seed of the determinant flushes the JVM
        // restart anyway (the fixture-side change forces a redeploy).
    }

    @Override
    public void invalidateService(String serviceId) {
        fast.invalidateService(serviceId);
        sql.invalidateService(serviceId);
        L2Cache.clear();             // service-level rewrites can affect any rule
        routingDecisions.clear();    // service publish may rewrite rules; safest to drop routing cache
    }

    /** Diagnostic — exposed for tests and the future eval-status endpoint
     *  (spec §8.6 audit). Returns a snapshot of the routing decisions made
     *  so far in this JVM. */
    public Map<String, String> getRoutingDecisionsSnapshot() {
        return new java.util.HashMap<>(routingDecisions);
    }
}
