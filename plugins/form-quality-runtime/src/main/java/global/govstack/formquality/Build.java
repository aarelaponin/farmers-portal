package global.govstack.formquality;

/**
 * Build stamp baked into the JAR by {@code deploy/repack.sh}. The build helper
 * rewrites the constants below right before {@code javac} so the stamp is
 * compiled into the bytecode. Surfaced in plugin descriptions and in the
 * "Bundle started" log line so you can confirm the live JAR matches the
 * source you intended to deploy.
 *
 * <p>Format: {@code build-NNN @ ISO-8601}. The counter is monotonic and the
 * timestamp is UTC.
 */
public final class Build {
    /** Bumped by {@code deploy/repack.sh} on every build. */
    public static final int    NUMBER = 14;
    /** UTC ISO-8601 timestamp set at build time. */
    public static final String TIMESTAMP = "2026-05-07T10:23:40Z";
    /** Convenience: short label for plugin descriptions / log lines. */
    public static final String STAMP = "build-" + String.format("%03d", NUMBER) + " @ " + TIMESTAMP;

    private Build() {}
}
