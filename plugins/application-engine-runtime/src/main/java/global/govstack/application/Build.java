package global.govstack.application;

/**
 * Build stamp baked into the JAR by {@code deploy/repack.sh}. Same pattern as
 * {@code form-quality-runtime} and {@code identity-resolver-runtime}. Surfaced
 * in plugin descriptions and the Activator startup log.
 */
public final class Build {
    public static final int    NUMBER = 12;
    public static final String TIMESTAMP = "2026-04-27T23:04:24Z";
    public static final String STAMP = "build-" + String.format("%03d", NUMBER) + " @ " + TIMESTAMP;
    private Build() {}
}
