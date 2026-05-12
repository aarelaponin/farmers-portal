package global.govstack.regbb.engine.notification;

import global.govstack.statusframework.api.Status;

/**
 * Status values for the notification lifecycle, registered with
 * {@link global.govstack.statusframework.core.StatusFramework}.
 *
 * <p>Transition map (registered in {@code Activator.start()}):
 * <pre>
 *   (new)   ──► PENDING ──► SENT          (terminal happy path)
 *                 │
 *                 ├──► SKIPPED            (terminal — no recipient in live
 *                 │                          mode, or template disabled)
 *                 │
 *                 └──► FAILED ──► PENDING (operator manual retry)
 *                          │
 *                          └──► DEAD_LETTER (terminal — max retries hit)
 * </pre>
 *
 * <p>Semantics per status:
 * <ul>
 *   <li>{@code PENDING} — dispatch attempted, awaiting result. Set at the
 *       start of every send, before the SMTP / SMS backend call.</li>
 *   <li>{@code SENT} — backend accepted the message. Note: "accepted" is
 *       backend-specific (Gmail SMTP confirms via message-id; LOG_ONLY
 *       always succeeds). The {@code backend} column on
 *       {@code notification_queue} distinguishes real sends from simulated
 *       LOG_ONLY ones.</li>
 *   <li>{@code SKIPPED} — intentionally not sent. Reasons logged in the
 *       audit row's {@code reason} field: template disabled
 *       ({@code emailEnabled=N}), template inactive ({@code isActive=N}),
 *       template body blank, resolver returned empty in live mode
 *       (citizen has no email/phone), backend missing config.</li>
 *   <li>{@code FAILED} — backend rejected or threw. Operator can retry
 *       via the "Retry" row action on the Notification Queue datalist
 *       (FAILED → PENDING). After 5 retries, NotificationQueueWorker
 *       auto-transitions to {@code DEAD_LETTER}.</li>
 *   <li>{@code DEAD_LETTER} — terminal failure. Operator gives up. Row
 *       stays in the queue for forensic purposes; no further automatic
 *       retry happens.</li>
 * </ul>
 *
 * <p>Codes are stored lowercase in the DB ({@code c_status} column) for
 * consistency with the rest of the project's status conventions.
 */
public enum NotifStatus implements Status {

    PENDING     ("pending",     "Pending"),
    SENT        ("sent",        "Sent"),
    SKIPPED     ("skipped",     "Skipped"),
    FAILED      ("failed",      "Failed"),
    DEAD_LETTER ("dead_letter", "Dead Letter");

    private final String code;
    private final String label;

    NotifStatus(String code, String label) {
        this.code  = code;
        this.label = label;
    }

    @Override public String getCode()  { return code; }
    @Override public String getLabel() { return label; }
}
