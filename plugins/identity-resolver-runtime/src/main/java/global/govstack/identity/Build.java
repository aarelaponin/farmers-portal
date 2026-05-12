package global.govstack.identity;

/**
 * Build stamp baked into the JAR by {@code deploy/repack.sh}. Same pattern as
 * {@code form-quality-runtime/Build.java} — surfaced in plugin descriptions and
 * the Activator startup log so the live JAR's identity is visible.
 *
 * <p>Format: {@code build-NNN @ ISO-8601}. Counter is monotonic, timestamp UTC.
 */
public final class Build {
    /** Bumped by {@code deploy/repack.sh} on every build. */
    public static final int    NUMBER = 14;
    /** UTC ISO-8601 timestamp set at build time. */
    public static final String TIMESTAMP = "2026-04-27T16:45:11Z";
    /** Convenience: short label for plugin descriptions / log lines. */
    public static final String STAMP = "build-" + String.format("%03d", NUMBER) + " @ " + TIMESTAMP;

    private Build() {}
}
