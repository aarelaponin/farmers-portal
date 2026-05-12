package global.govstack.parcelzonecentring;

/**
 * Build stamp baked into the JAR by {@code deploy/repack.sh}. Surfaces in
 * plugin descriptions and in the "Bundle started" log line so the live JAR's
 * identity is visible.
 */
public final class Build {
    /** Bumped by {@code deploy/repack.sh} on every build. */
    public static final int    NUMBER = 12;
    /** UTC ISO-8601 timestamp set at build time. */
    public static final String TIMESTAMP = "2026-05-08T19:26:42Z";
    /** Convenience: short label for plugin descriptions / log lines. */
    public static final String STAMP = "build-" + String.format("%03d", NUMBER) + " @ " + TIMESTAMP;

    private Build() {}
}
