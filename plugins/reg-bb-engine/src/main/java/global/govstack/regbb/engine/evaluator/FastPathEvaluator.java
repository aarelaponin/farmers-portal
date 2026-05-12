package global.govstack.regbb.engine.evaluator;

import global.govstack.regbb.engine.api.DeterminantEvaluator;
import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.api.FeeResult;
import global.govstack.regbb.engine.api.RequiredDocResult;
import global.govstack.regbb.engine.api.ScreenEvalResult;
import global.govstack.regbb.engine.dao.MetaModelDao;
import org.joget.apps.form.model.FormRow;
import org.joget.commons.util.LogUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Slice 1A fast-path evaluator. Single-bundle layout per ADR-002 r2.
 *
 * <p>Implements {@link DeterminantEvaluator} for the subset of rules slice 1A
 * needs: Boolean operators ({@code eq}, {@code neq}, {@code lt}, {@code lte},
 * {@code gt}, {@code gte}, {@code in}, {@code notIn}, {@code and}, {@code or},
 * {@code not}) over {@code $applicant.*} and {@code $constant.*} references.
 * Other operators or any {@code $registry.*} reference return
 * {@link EvalResult.Outcome#ERROR} cleanly with cause {@code routed_to_sql_path}
 * — slice 1B's RoutingEvaluator will dispatch those to the SQL path.
 *
 * <p>Per ADR-003: rule storage is the DSL source string verbatim. This
 * evaluator parses a deliberately narrow subset of the DSL inline (no ANTLR
 * delegation in slice 1A). Accepted shapes per rule:
 *
 * <ul>
 *   <li>{@code <ref> <op> <value>} — single comparison
 *   <li>{@code <ref> in constant.<catalogCode>} — set membership via mm_catalog
 *   <li>{@code <ref> in [literal,literal,...]} — inline set membership
 *   <li>{@code <expr> AND/OR <expr>} — Boolean composition
 *   <li>{@code NOT (<expr>)} — Boolean negation
 *   <li>{@code (<expr>)} — grouping
 * </ul>
 *
 * <p>Per ADR-008: L1 cache only at slice 1A — a {@link ThreadLocal} per-request
 * map. L2 (Caffeine) lands at slice 1D.
 *
 * <p>Per spec §4.3.0.1 null-propagation: any operator with a null operand
 * returns {@link EvalResult.Outcome#NULL}.
 */
public class FastPathEvaluator implements DeterminantEvaluator {

    private static final String CLASS_NAME = FastPathEvaluator.class.getName();
    private static final String EVAL = "fast";

    private final ThreadLocal<java.util.Map<String, EvalResult>> l1 =
            ThreadLocal.withInitial(java.util.HashMap::new);

    private final MetaModelDao dao;

    /** Production constructor — looks up MetaModelDao via Joget Spring context. */
    public FastPathEvaluator() {
        this.dao = new MetaModelDao();
    }

    /** Test constructor — caller supplies the DAO. */
    public FastPathEvaluator(MetaModelDao dao) {
        this.dao = dao;
    }

    @Override
    public EvalResult evaluate(String determinantCode, EvalContext ctx) {
        if (determinantCode == null || determinantCode.isEmpty()) {
            return EvalResult.error("missing_determinant_code", EVAL);
        }
        String cacheKey = cacheKey(determinantCode, ctx);
        EvalResult cached = l1.get().get(cacheKey);
        if (cached != null) return cached;

        try {
            FormRow det = dao.findDeterminantByCode(determinantCode);
            if (det == null) return EvalResult.error("determinant_not_found:" + determinantCode, EVAL);

            String ruleSource = unwrapSource(prop(det, "ruleJson"));
            if (ruleSource == null || ruleSource.trim().isEmpty()) {
                return EvalResult.error("rule_source_empty", EVAL);
            }

            EvalResult result = evaluateExpr(ruleSource.trim(), ctx);
            l1.get().put(cacheKey, result);
            return result;
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "FastPathEvaluator.evaluate failed for " + determinantCode);
            return EvalResult.error("internal:" + t.getClass().getSimpleName(), EVAL);
        }
    }

    @Override
    public ScreenEvalResult evaluateScreen(String screenId, EvalContext ctx) {
        return new ScreenEvalResult(Collections.emptyMap(), false, "evaluateScreen_not_implemented_in_slice_1A");
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
    public void invalidate(String applicationId) {
        l1.get().clear();
    }

    @Override
    public void invalidateService(String serviceId) {
        l1.get().clear();
    }

    // === Tiny rule-source parser-evaluator. Slice 1A is deliberately narrow. ===

    EvalResult evaluateExpr(String src, EvalContext ctx) {
        try {
            List<String> tokens = tokenise(src);
            ExprParser p = new ExprParser(tokens, ctx, dao);
            Object value = p.parseOr();
            if (p.hasMore()) {
                return EvalResult.error("trailing_input:" + p.peek(), EVAL);
            }
            if (value == null) return EvalResult.nullResult(EVAL);
            if (value instanceof Boolean) return EvalResult.bool((Boolean) value, EVAL);
            return EvalResult.error("non_boolean_result:" + value.getClass().getSimpleName(), EVAL);
        } catch (RoutedToSqlException e) {
            return EvalResult.error("routed_to_sql_path:" + e.getMessage(), EVAL);
        } catch (ParseException e) {
            return EvalResult.error("parse_error:" + e.getMessage(), EVAL);
        } catch (Throwable t) {
            return EvalResult.error("eval_error:" + t.getClass().getSimpleName() + ":" + t.getMessage(), EVAL);
        }
    }

    static List<String> tokenise(String src) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                i++;
            } else if (c == '\'' || c == '"') {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                char q = c;
                StringBuilder s = new StringBuilder();
                s.append(q);
                i++;
                while (i < src.length() && src.charAt(i) != q) { s.append(src.charAt(i)); i++; }
                if (i < src.length()) { s.append(q); i++; }
                out.add(s.toString());
            } else if (c == '(' || c == ')' || c == '[' || c == ']' || c == ',') {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                out.add(String.valueOf(c));
                i++;
            } else {
                cur.append(c);
                i++;
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    static class ExprParser {
        private final List<String> tokens;
        private final EvalContext ctx;
        private final MetaModelDao dao;
        private int pos = 0;

        ExprParser(List<String> tokens, EvalContext ctx, MetaModelDao dao) {
            this.tokens = tokens; this.ctx = ctx; this.dao = dao;
        }
        boolean hasMore() { return pos < tokens.size(); }
        String peek() { return pos < tokens.size() ? tokens.get(pos) : null; }
        String consume() { return tokens.get(pos++); }

        Object parseOr() {
            Object left = parseAnd();
            while (hasMore() && eqIgnoreCase(peek(), "OR", "||")) { consume(); left = orOp(left, parseAnd()); }
            return left;
        }

        Object parseAnd() {
            Object left = parseNot();
            while (hasMore() && eqIgnoreCase(peek(), "AND", "&&")) { consume(); left = andOp(left, parseNot()); }
            return left;
        }

        Object parseNot() {
            if (hasMore() && eqIgnoreCase(peek(), "NOT", "!")) {
                consume();
                Object v = parseNot();
                if (v == null) return null;
                if (v instanceof Boolean) return !((Boolean) v);
                throw new ParseException("NOT requires boolean operand");
            }
            return parseCmp();
        }

        Object parseCmp() {
            if (hasMore() && "(".equals(peek())) {
                consume();
                Object inner = parseOr();
                if (!hasMore() || !")".equals(consume())) throw new ParseException("missing closing paren");
                return inner;
            }
            Object left = parseAtom();
            if (!hasMore()) throw new ParseException("expected operator after " + left);
            String op = peek();
            String opLower = op.toLowerCase();
            if (eqIgnoreCase(op, "==", "eq")) { consume(); return equalsOp(left, parseAtom()); }
            else if (eqIgnoreCase(op, "!=", "neq", "<>")) { consume(); Object eq = equalsOp(left, parseAtom()); return eq == null ? null : !((Boolean) eq); }
            else if ("<".equals(op) || eqIgnoreCase(op, "lt"))   { consume(); return cmp(left, parseAtom(), -1, false); }
            else if ("<=".equals(op) || eqIgnoreCase(op, "lte")) { consume(); return cmp(left, parseAtom(), -1, true); }
            else if (">".equals(op) || eqIgnoreCase(op, "gt"))   { consume(); return cmp(left, parseAtom(), 1, false); }
            else if (">=".equals(op) || eqIgnoreCase(op, "gte")) { consume(); return cmp(left, parseAtom(), 1, true); }
            else if ("in".equalsIgnoreCase(op)) { consume(); return inOp(left, parseList()); }
            else if ("notIn".equalsIgnoreCase(opLower) || "not_in".equalsIgnoreCase(opLower) || "notin".equalsIgnoreCase(opLower)) {
                consume();
                Object in = inOp(left, parseList());
                return in == null ? null : !((Boolean) in);
            }
            else throw new ParseException("unsupported operator: " + op);
        }

        Object parseList() {
            if (hasMore() && "[".equals(peek())) {
                consume();
                List<Object> items = new ArrayList<>();
                while (hasMore() && !"]".equals(peek())) {
                    items.add(parseAtom());
                    if (hasMore() && ",".equals(peek())) consume();
                }
                if (hasMore()) consume();
                return items;
            }
            return parseAtom();
        }

        Object parseAtom() {
            if (!hasMore()) throw new ParseException("expected atom");
            String t = consume();
            if (t.length() >= 2 && (t.charAt(0) == '\'' || t.charAt(0) == '"') &&
                t.charAt(t.length() - 1) == t.charAt(0)) {
                return t.substring(1, t.length() - 1);
            }
            if (looksLikeNumber(t)) {
                try { return Double.parseDouble(t); } catch (NumberFormatException ignore) {}
            }
            if ("true".equalsIgnoreCase(t)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(t)) return Boolean.FALSE;
            if ("null".equalsIgnoreCase(t)) return null;
            String ref = t.startsWith("$") ? t.substring(1) : t;
            int dot = ref.indexOf('.');
            if (dot < 0) return resolveApplicant(ref);
            String scope = ref.substring(0, dot).toLowerCase();
            String path = ref.substring(dot + 1);
            switch (scope) {
                case "applicant": return resolveApplicant(path);
                case "constant":  return resolveConstant(path);
                case "registry":  throw new RoutedToSqlException("$registry.* in fast path");
                case "service":
                case "registration":
                case "selected_registrations":
                    throw new RoutedToSqlException(scope + ".* not in slice 1A");
                default: throw new ParseException("unknown ref scope: " + scope);
            }
        }

        private Object resolveApplicant(String path) {
            if (ctx == null || ctx.data == null) return null;
            Object v = ctx.data.get(path);
            if (v == null) v = ctx.data.get(path.toLowerCase());
            if (v == null && path.contains(".")) {
                v = ctx.data.get(path.replace('.', '_'));
                if (v == null) v = ctx.data.get(path.replace('.', '_').toLowerCase());
            }
            return v;
        }

        private Object resolveConstant(String catalogCode) {
            if (dao == null) return null;
            FormRow cat = dao.findCatalogByCode(catalogCode);
            if (cat == null) return null;
            String json = prop(cat, "itemsJson");
            if (json == null || json.isEmpty()) return Collections.emptyList();
            return parseCatalogValues(json);
        }
    }

    static List<Object> parseCatalogValues(String json) {
        List<Object> out = new ArrayList<>();
        if (json == null) return out;
        int i = 0;
        while (i < json.length()) {
            int idx = json.indexOf("\"value\"", i);
            if (idx < 0) break;
            int colon = json.indexOf(':', idx);
            if (colon < 0) break;
            int q1 = json.indexOf('"', colon + 1);
            if (q1 < 0) break;
            int q2 = json.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            out.add(json.substring(q1 + 1, q2));
            i = q2 + 1;
        }
        return out;
    }

    // --- operators with null-propagation ---

    static Object equalsOp(Object a, Object b) {
        if (a == null || b == null) return null;
        return stringOf(a).equals(stringOf(b));
    }

    static Object cmp(Object a, Object b, int sign, boolean orEqual) {
        if (a == null || b == null) return null;
        try {
            double da = Double.parseDouble(stringOf(a));
            double db = Double.parseDouble(stringOf(b));
            int c = Double.compare(da, db);
            if (sign < 0) return orEqual ? c <= 0 : c < 0;
            return orEqual ? c >= 0 : c > 0;
        } catch (NumberFormatException nfe) {
            int c = stringOf(a).compareTo(stringOf(b));
            if (sign < 0) return orEqual ? c <= 0 : c < 0;
            return orEqual ? c >= 0 : c > 0;
        }
    }

    static Object inOp(Object a, Object b) {
        if (a == null || b == null) return null;
        Collection<?> coll = (b instanceof Collection) ? (Collection<?>) b : Collections.singletonList(b);
        String needle = stringOf(a);
        for (Object item : coll) {
            if (item != null && stringOf(item).equals(needle)) return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    static Object andOp(Object a, Object b) {
        if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) return Boolean.FALSE;
        if (a == null || b == null) return null;
        return ((Boolean) a) && ((Boolean) b);
    }

    static Object orOp(Object a, Object b) {
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return Boolean.TRUE;
        if (a == null || b == null) return null;
        return ((Boolean) a) || ((Boolean) b);
    }

    static String stringOf(Object o) { return o == null ? null : String.valueOf(o); }

    static boolean eqIgnoreCase(String s, String... candidates) {
        if (s == null) return false;
        for (String c : candidates) if (s.equalsIgnoreCase(c)) return true;
        return false;
    }

    static boolean looksLikeNumber(String t) {
        return t != null && t.matches("-?\\d+(\\.\\d+)?");
    }

    static String unwrapSource(String stored) {
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

    static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return v == null ? null : v.toString();
    }

    String cacheKey(String determinantCode, EvalContext ctx) {
        if (ctx == null) return determinantCode + "||";
        return determinantCode + "|" + (ctx.applicationId == null ? "" : ctx.applicationId)
                + "|" + (ctx.serviceVersion == null ? "" : ctx.serviceVersion)
                + "|" + (ctx.data == null ? "" : Integer.toHexString(ctx.data.hashCode()));
    }

    static class ParseException extends RuntimeException { ParseException(String m) { super(m); } }
    static class RoutedToSqlException extends RuntimeException { RoutedToSqlException(String m) { super(m); } }
}
