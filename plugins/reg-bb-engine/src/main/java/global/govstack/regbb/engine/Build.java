package global.govstack.regbb.engine;

/**
 * Build stamp baked into the JAR by {@code deploy/repack.sh}. Same pattern as
 * {@code identity-resolver-runtime/Build.java} and
 * {@code form-quality-runtime/Build.java}.
 *
 * <p>Format: {@code build-NNN @ ISO-8601}. Counter is monotonic, timestamp UTC.
 */
public final class Build {
    /** Bumped by {@code deploy/repack.sh} on every build. */
    public static final int    NUMBER = 143;
    /** UTC ISO-8601 timestamp set at build time. */
    public static final String TIMESTAMP = "2026-07-09T15:36:25Z";
    /** Convenience: short label for plugin descriptions / log lines. */
    public static final String STAMP = "build-" + String.format("%03d", NUMBER) + " @ " + TIMESTAMP;

    private Build() {}
}
