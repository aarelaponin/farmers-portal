package global.govstack.regbb.engine.notification;

import org.joget.commons.util.LogUtil;

/**
 * Reads the dispatcher's test-mode override from JVM system properties.
 *
 * <p>Why JVM properties instead of a DB row: this is a one-shot
 * pre-production safety switch (flip {@code Y → N} once when MAFSN
 * authorises live sends). DB-backed UI editing is overkill for a
 * change made once in the project's lifetime; system properties are
 * the standard place for "safety mode" / "kill switch" flags in
 * production Java applications. They survive restarts, they're
 * auditable in {@code setenv.sh}, and they sidestep Joget's two-cache
 * trap that bit us with the {@code notif_global_config} form in
 * W2.5 step 4b (May 2026).
 *
 * <p><b>Properties read (with fail-safe defaults):</b>
 * <ul>
 *   <li>{@code -Dregbb.notif.testMode=Y}  — Y (default) re-routes every
 *       email + SMS to the test addresses below. N goes live to the
 *       per-template recipient resolver.</li>
 *   <li>{@code -Dregbb.notif.testEmail=aarelaponin@gmail.com}  —
 *       where all emails go in test mode.</li>
 *   <li>{@code -Dregbb.notif.testPhone=+26658515039}  — where all SMS
 *       go in test mode.</li>
 * </ul>
 *
 * <p><b>Fail-safe principle:</b> if the property is unset, this class
 * defaults to test mode + Aare's inbox. Going live REQUIRES an explicit
 * {@code -Dregbb.notif.testMode=N} in {@code setenv.sh}. The system
 * cannot accidentally start sending to real citizens because someone
 * forgot to set a property.
 *
 * <p><b>To flip live (one-time operation, requires Joget restart):</b>
 * <pre>
 *   # In Joget's setenv.sh (or your deployment's equivalent):
 *   CATALINA_OPTS="$CATALINA_OPTS -Dregbb.notif.testMode=N"
 *
 *   # Restart Tomcat. The next email/SMS dispatch will go to the
 *   # per-template resolver (APPLICANT, FINANCE_OFFICERS, etc.)
 *   # instead of the test inbox.
 * </pre>
 *
 * <p>To preview what each call would route to before flipping live,
 * grep joget.log for "TEST MODE active — re-routing" lines. The
 * intended-recipient address is logged alongside the test inbox.
 */
public final class NotificationConfig {

    private static final String CLASS_NAME = NotificationConfig.class.getName();

    public static final String PROP_TEST_MODE  = "regbb.notif.testMode";
    public static final String PROP_TEST_EMAIL = "regbb.notif.testEmail";
    public static final String PROP_TEST_PHONE = "regbb.notif.testPhone";

    /** Defaults — used when the property is unset. Fail-safe to test mode. */
    public static final String DEFAULT_TEST_EMAIL = "aarelaponin@gmail.com";
    public static final String DEFAULT_TEST_PHONE = "+26658515039";

    private NotificationConfig() {}

    public static final class Snapshot {
        public final boolean testModeActive;
        public final String  testRecipientEmail;
        public final String  testRecipientPhone;
        public final String  source;

        public Snapshot(boolean a, String e, String p, String s) {
            this.testModeActive     = a;
            this.testRecipientEmail = e;
            this.testRecipientPhone = p;
            this.source             = s;
        }
    }

    /**
     * Resolve the active test-mode configuration. Read directly from JVM
     * system properties on every call — cheap (System.getProperty is a
     * concurrent map lookup), no caching layer needed.
     */
    public static Snapshot get() {
        String mode  = System.getProperty(PROP_TEST_MODE, "Y").trim();
        boolean active = !"N".equalsIgnoreCase(mode);

        String email = System.getProperty(PROP_TEST_EMAIL, DEFAULT_TEST_EMAIL).trim();
        if (email.isEmpty()) email = DEFAULT_TEST_EMAIL;

        String phone = System.getProperty(PROP_TEST_PHONE, DEFAULT_TEST_PHONE).trim();
        if (phone.isEmpty()) phone = DEFAULT_TEST_PHONE;

        return new Snapshot(active, email, phone, "system-property");
    }

    /** Convenience for one-off log lines / diagnostics. */
    public static String describe() {
        Snapshot s = get();
        return "testModeActive=" + s.testModeActive
             + " testEmail=" + s.testRecipientEmail
             + " testPhone=" + s.testRecipientPhone
             + " source=" + s.source;
    }

    /** Test hook — invalidate kept for source compatibility, but it's a no-op now. */
    public static void invalidate() { /* no cache to invalidate */ }
}
