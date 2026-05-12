package global.govstack.regbb.publisher;

/**
 * Build stamp baked into the JAR by {@code deploy/repack.sh}. Same pattern as
 * sibling plugins.
 */
public final class Build {
    public static final int    NUMBER = 2;
    public static final String TIMESTAMP = "2026-04-29T10:35:00Z";
    public static final String STAMP = "build-" + String.format("%03d", NUMBER) + " @ " + TIMESTAMP;

    private Build() {}
}
