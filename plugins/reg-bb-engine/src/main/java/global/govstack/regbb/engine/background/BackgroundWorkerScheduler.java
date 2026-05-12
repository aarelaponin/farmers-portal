package global.govstack.regbb.engine.background;

import global.govstack.regbb.engine.processing.EligibilityProcessingWorker;
import org.joget.commons.util.LogUtil;

/**
 * Daemon-thread scheduler that drives {@link EligibilityProcessingWorker}
 * on a fixed-interval cadence so newly-submitted subsidy applications are
 * picked up automatically.
 *
 * <p>Why a daemon thread and not a Joget workflow + scheduler. ADR-030 Step 5
 * left scheduling to a "wire as a tool step in any Joget workflow process"
 * comment that was never followed through — the workflow + deadline plumbing
 * is heavy and brittle to author by hand. A self-contained background thread
 * inside this bundle is small, restarts cleanly when the bundle is reloaded
 * (start() is called from Activator), and can be replaced by a Quartz job
 * later without touching any caller. It runs on the Joget VM, survives user
 * disconnects, and has zero external dependencies.
 *
 * <p>Lifecycle.
 * <ul>
 *   <li>{@code start()} — invoked from {@code Activator.start()} once the
 *       plugin services are registered. Idempotent: a second call while
 *       already running is a no-op.</li>
 *   <li>Loop: sleep {@link #INTERVAL_MS} ms, then call
 *       {@code new EligibilityProcessingWorker().runWorker()}. Errors are
 *       logged and swallowed — one bad poll never kills the loop.</li>
 *   <li>{@code stop()} — invoked from {@code Activator.stop()} on bundle
 *       reload or JVM shutdown. Sets a flag and interrupts the thread.</li>
 * </ul>
 *
 * <p>Production note. This is good enough for UAT and small-scale
 * production. For multi-node Joget deployments where two nodes both run
 * the scheduler, a database-coordinated lease (one row, one node holds
 * it) would prevent duplicate polls. The worker's queue read already
 * handles concurrent races safely (it reads with a lock and writes
 * idempotently), so duplicate polls are correctness-safe — just wasteful.
 * Add lease coordination if/when we move to multi-node.
 */
public final class BackgroundWorkerScheduler {

    private static final String CLASS_NAME = BackgroundWorkerScheduler.class.getName();

    /** Polling interval. 60s is fast enough for citizens to feel "instant"
     *  on the ~3-minute end-to-end UAT scenario, slow enough that an idle
     *  system polls at most once per minute. Tunable here only — no config
     *  property exposed yet (deferred to production-readiness). */
    private static final long INTERVAL_MS = 60_000L;

    private static volatile boolean running = false;
    private static Thread workerThread;

    private BackgroundWorkerScheduler() { /* static-only */ }

    public static synchronized void start() {
        if (running) {
            LogUtil.info(CLASS_NAME, "start() called but already running — no-op");
            return;
        }
        running = true;
        workerThread = new Thread(BackgroundWorkerScheduler::loop,
                                  "BackgroundWorkerScheduler-eligibility");
        workerThread.setDaemon(true);
        workerThread.start();
        LogUtil.info(CLASS_NAME,
                "started; eligibility worker will poll every " + INTERVAL_MS + "ms");
    }

    public static synchronized void stop() {
        if (!running) return;
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        LogUtil.info(CLASS_NAME, "stopped");
    }

    private static void loop() {
        // First poll fires after one INTERVAL_MS — gives the rest of the
        // bundle (and Joget's own startup) time to settle before we hit
        // the DB. If the user wants an immediate first poll, /run-eligibility-worker
        // is still there and unaffected.
        while (running) {
            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!running) break;
            try {
                Object result = new EligibilityProcessingWorker().execute(new java.util.HashMap<>());
                String summary = result == null ? "(no result)" : String.valueOf(result);
                // Log at INFO when work happened, DEBUG when idle (most polls
                // will be idle). Heuristic: summary contains "processed=0" =>
                // idle. Cheap string contains check, no parsing.
                if (summary != null && !summary.contains("processed=0")) {
                    LogUtil.info(CLASS_NAME, "auto-poll: " + summary);
                } else {
                    LogUtil.debug(CLASS_NAME, "auto-poll idle: " + summary);
                }
            } catch (Throwable t) {
                // Never let an exception kill the loop. Joget's worker has
                // its own try/catch around individual application failures;
                // this catch is for systemic problems (DB down, classloader
                // issues) so the scheduler self-heals when transient.
                LogUtil.error(CLASS_NAME, t, "auto-poll failed; continuing");
            }
        }
    }
}
