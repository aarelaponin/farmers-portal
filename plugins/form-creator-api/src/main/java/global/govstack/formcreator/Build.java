package global.govstack.formcreator;

/**
 * Build stamp baked into the JAR by {@code deploy/repack.sh}. Same pattern as
 * {@code reg-bb-engine/Build.java} and {@code reg-bb-publisher/Build.java}.
 *
 * <p>Format: {@code build-NNN @ ISO-8601}. Counter is monotonic, timestamp UTC.
 *
 * <p>Surfaced in the plugin description (see
 * {@code FormCreatorServiceProvider#getDescription()}) so the running build is
 * visible from the Joget admin "Manage Plugins" page — confirms which JAR is
 * actually loaded after an upload.
 */
public final class Build {
    /** Bumped by {@code deploy/repack.sh} on every build. */
    public static final int    NUMBER = 24;
    /** UTC ISO-8601 timestamp set at build time. */
    public static final String TIMESTAMP = "2026-05-09T19:41:59Z";
    /** Convenience: short label for plugin descriptions / log lines. */
    public static final String STAMP = "build-" + String.format("%03d", NUMBER) + " @ " + TIMESTAMP;

    private Build() {}
}
