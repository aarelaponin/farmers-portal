package global.govstack.decision;

/**
 * Build stamp baked into the JAR by {@code deploy/repack.sh}. Same pattern as
 * {@code application-engine-runtime} and {@code identity-resolver-runtime}.
 * Surfaced in plugin descriptions and the Activator startup log.
 */
public final class Build {
    public static final int    NUMBER = 7;
    public static final String TIMESTAMP = "2026-04-27T20:05:16Z";
    public static final String STAMP = "build-" + String.format("%03d", NUMBER) + " @ " + TIMESTAMP;
    private Build() {}
}
