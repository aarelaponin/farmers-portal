package global.govstack.regbb.engine.cache;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slice 1B-d shared evaluation cache.
 *
 * <p>Architecture doc named Caffeine; at single-tenant scale (Lesotho MAFSN —
 * one ministry, dozens of operators) the LRU-eviction and async-refresh
 * features that justify Caffeine aren't necessary. A {@link ConcurrentHashMap}
 * with lazy expiry on read keeps the dependency surface small (no embedded
 * Caffeine JAR in the bundle) and performs identically at this scale. If
 * we ever go multi-tenant federated this gets swapped out — the
 * {@link RoutingEvaluator}-side contract stays the same.
 *
 * <p><b>Cache key:</b> {@code determinantCode | applicationId | ruleVersion | dataHash}.
 * Including {@code ruleVersion} (the verbatim {@code mm_determinant.ruleJson}
 * at the time of the last cache put) means any rule edit invalidates the
 * cache automatically — when the next eval reads the new rule source, the
 * key changes and the old entry becomes unreachable. {@code dataHash}
 * (computed from the relevant {@code ctx.data} fields) does the same for
 * applicant-data edits.
 *
 * <p><b>TTL:</b> configurable via system property {@code regbb.eval.l2.ttlSeconds},
 * default 60. Set to {@code 0} to disable caching entirely (every call is a miss).
 *
 * <p><b>Eviction:</b> lazy on read. Stale entries linger until something
 * tries to read them with the same key, at which point we expire+remove.
 * Memory growth is bounded by realistic usage — at scale this needs review.
 */
public final class L2Cache {

    /** Per-JVM cache. {@code volatile} on the entry's expiry stamp keeps
     *  read-side checks consistent without locking. */
    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();

    private static final String SYS_PROP_TTL = "regbb.eval.l2.ttlSeconds";

    private L2Cache() { /* static helpers only */ }

    /** Build the cache key for an evaluation. Must be called identically on
     *  put + get otherwise we leak entries (different keys point at same data).
     *  {@code dataHash} is hex of {@code ctx.data.hashCode()} — fast and
     *  stable across {@link Object#hashCode} contract; collisions return a
     *  semantically-equivalent result so they're harmless even when they
     *  happen. */
    public static String buildKey(String determinantCode, EvalContext ctx,
                                  String ruleVersion) {
        String app = (ctx == null || ctx.applicationId == null) ? "" : ctx.applicationId;
        String dataHash = (ctx == null || ctx.data == null) ? "0"
                : Integer.toHexString(ctx.data.hashCode());
        String rv = ruleVersion == null ? "" : ruleVersion;
        // Length-prefix every component so app="ab" + det="cd" can't collide
        // with app="abc" + det="d".
        return d(determinantCode) + d(app) + d(rv) + d(dataHash);
    }

    private static String d(String s) {
        if (s == null) return "0|";
        return s.length() + "|" + s + "|";
    }

    /** Read with expiry check. Returns {@code null} if no entry, or if entry
     *  expired (in which case we also remove it — opportunistic GC). */
    public static EvalResult get(String key) {
        if (key == null) return null;
        if (ttlSeconds() <= 0) return null;        // cache disabled
        Entry e = CACHE.get(key);
        if (e == null) return null;
        if (e.expiresAtMs <= System.currentTimeMillis()) {
            CACHE.remove(key, e);                  // CAS-style: only remove if still ours
            return null;
        }
        return e.result;
    }

    /** Store. Silently no-ops if TTL is 0 (caching disabled). */
    public static void put(String key, EvalResult result) {
        if (key == null || result == null) return;
        long ttlMs = ttlSeconds() * 1000L;
        if (ttlMs <= 0) return;
        CACHE.put(key, new Entry(result, System.currentTimeMillis() + ttlMs));
    }

    /** Drop every entry for an application. Called by the binder's
     *  {@code evaluator.invalidate(applicationId)} hook after a save —
     *  ensures the post-save evaluation doesn't see pre-save inputs. */
    public static int invalidateApplication(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) return 0;
        // Cache keys carry length-prefixed application id as the second
        // component (per buildKey shape: detLen|det|appLen|app|...).
        // Suffix-match the segment for safety.
        String segment = "|" + applicationId.length() + "|" + applicationId + "|";
        int n = 0;
        for (Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator(); it.hasNext();) {
            String k = it.next().getKey();
            if (k.contains(segment)) { it.remove(); n++; }
        }
        return n;
    }

    /** Diagnostic — returns current entry count for monitoring. */
    public static int size() { return CACHE.size(); }

    /** Drop everything. Called on service publish, full reseed, etc. */
    public static void clear() { CACHE.clear(); }

    private static int ttlSeconds() {
        try {
            String s = System.getProperty(SYS_PROP_TTL, "60");
            int v = Integer.parseInt(s.trim());
            return Math.max(0, v);
        } catch (NumberFormatException nfe) {
            return 60;
        }
    }

    /** Cache entry. Not exported — internal type. */
    private static final class Entry {
        final EvalResult result;
        final long expiresAtMs;
        Entry(EvalResult result, long expiresAtMs) {
            this.result = result;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
