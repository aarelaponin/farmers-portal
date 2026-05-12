package global.govstack.regbb.engine.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of evaluating all Determinants on a screen — the per-field UI toggles
 * to apply.
 *
 * <p>Used by {@code MetaScreenElement} for initial render and by the
 * {@code POST /regbb/eval/screen} endpoint for Ajax conditional UI updates.
 *
 * <p>{@link #degraded} is true if any registry-touching evaluation deferred
 * per spec §6.4 (a SQL-path Determinant in {@code field}/{@code screen}
 * scope was opt-in via {@code allowSlowPath} and the engine returned a
 * deferred toggle state instead of blocking the Ajax round trip on a
 * registry call).
 */
public final class ScreenEvalResult {

    public static final class Toggle {
        public final String fieldId;
        public final boolean visible;
        public final boolean required;
        public Toggle(String fieldId, boolean visible, boolean required) {
            this.fieldId = fieldId;
            this.visible = visible;
            this.required = required;
        }
    }

    public final Map<String, Toggle> toggles;
    public final boolean degraded;
    public final String degradedReason;

    public ScreenEvalResult(Map<String, Toggle> toggles, boolean degraded, String degradedReason) {
        this.toggles = toggles == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(toggles));
        this.degraded = degraded;
        this.degradedReason = degradedReason;
    }
}
