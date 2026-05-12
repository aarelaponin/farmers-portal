package global.govstack.regbb.engine.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable evaluation context passed to every {@link DeterminantEvaluator}
 * call. Carries the full set of inputs the evaluator needs to resolve refs
 * ({@code $applicant.*}, {@code $service.*}, {@code $registration.*},
 * {@code $selected_registrations.*}, {@code $registry.*}) and to key cache
 * entries.
 *
 * <p>Per spec §8.1.
 */
public final class EvalContext {

    /** {@code app_application.id} — for cache key and audit row. */
    public final String applicationId;

    /** {@code mm_service.id} — for meta-record lookup. */
    public final String serviceId;

    /** {@code mm_service.version} — for meta-record version pinning. */
    public final String serviceVersion;

    /** {@code mm_registration.id} values the citizen has selected. */
    public final Set<String> selectedRegistrationIds;

    /** Current application data; resolves {@code $applicant.*} refs. */
    public final Map<String, Object> data;

    /** Joget username — for audit and IM-connector consent context. */
    public final String currentUsername;

    /** {@code X-Request-Id} for cross-system trace, or null. */
    public final String correlationId;

    /**
     * Per-evaluation capability resolution cache, lazily initialised on
     * first access. Key: {@code "<capability>.<field>:<nid>"}. Value: the
     * resolved string (or null sentinel via {@link #CACHE_NULL}). Lives for
     * the lifetime of this {@code EvalContext} instance — typically one
     * binder invocation across all Determinant rules in a chain. Two rules
     * in the same chain that both reference {@code $registry.farmer.first_name}
     * for the same applicant share the cached value.
     *
     * <p>The cache is non-final on a public-final-fields type by design:
     * EvalContext's contract is "immutable inputs"; the cache is an
     * implementation detail of the resolution path that doesn't affect
     * what a downstream observer can see. ConcurrentHashMap so concurrent
     * adapter calls (per-rule capabilities, future parallel rule
     * evaluation) are safe.
     */
    private transient java.util.concurrent.ConcurrentHashMap<String, String> capabilityCache;

    /** Sentinel for cached-null since ConcurrentHashMap rejects null values. */
    public static final String CACHE_NULL = "__NULL__";

    /** Lazily get-or-create the capability cache. */
    public java.util.concurrent.ConcurrentHashMap<String, String> capabilityCache() {
        java.util.concurrent.ConcurrentHashMap<String, String> c = capabilityCache;
        if (c == null) {
            synchronized (this) {
                c = capabilityCache;
                if (c == null) {
                    c = new java.util.concurrent.ConcurrentHashMap<>();
                    capabilityCache = c;
                }
            }
        }
        return c;
    }

    public EvalContext(String applicationId,
                       String serviceId,
                       String serviceVersion,
                       Set<String> selectedRegistrationIds,
                       Map<String, Object> data,
                       String currentUsername,
                       String correlationId) {
        this.applicationId = applicationId;
        this.serviceId = serviceId;
        this.serviceVersion = serviceVersion;
        this.selectedRegistrationIds = selectedRegistrationIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(selectedRegistrationIds));
        this.data = data == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(data));
        this.currentUsername = currentUsername;
        this.correlationId = correlationId;
    }

    /** Builder convenience for tests and adapters. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String applicationId;
        private String serviceId;
        private String serviceVersion;
        private Set<String> selectedRegistrationIds;
        private Map<String, Object> data;
        private String currentUsername;
        private String correlationId;

        public Builder applicationId(String v) { this.applicationId = v; return this; }
        public Builder serviceId(String v) { this.serviceId = v; return this; }
        public Builder serviceVersion(String v) { this.serviceVersion = v; return this; }
        public Builder selectedRegistrationIds(Set<String> v) { this.selectedRegistrationIds = v; return this; }
        public Builder data(Map<String, Object> v) { this.data = v; return this; }
        public Builder currentUsername(String v) { this.currentUsername = v; return this; }
        public Builder correlationId(String v) { this.correlationId = v; return this; }

        public EvalContext build() {
            return new EvalContext(applicationId, serviceId, serviceVersion,
                    selectedRegistrationIds, data, currentUsername, correlationId);
        }
    }
}
