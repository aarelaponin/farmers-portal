package global.govstack.regbb.engine.lifecycle;

import global.govstack.statusframework.api.Status;

/**
 * Coarse-grained lifecycle states for a subsidy application, registered with
 * {@link global.govstack.statusframework.core.StatusFramework} in
 * {@code Activator.start()}.
 *
 * <p>Transition map (registered in {@code Activator.start()}):
 * <pre>
 *   (new)   ──► DRAFT      ──► SUBMITTED ──► UNDER_REVIEW ──► APPROVED  (terminal happy path)
 *                                                         ──► REJECTED  (terminal)
 *                                                         ──► PENDING_REVIEW (waiting for operator action)
 *                              PENDING_REVIEW ──► UNDER_REVIEW (operator picks it up)
 *               *         ──► WITHDRAWN  (terminal — citizen withdraws)
 * </pre>
 *
 * <p>Phase 1 wiring (W3.1, May 2026) covers SUBMITTED → APPROVED/REJECTED/
 * PENDING_REVIEW. DRAFT requires save-as-draft wizard support (deferred);
 * UNDER_REVIEW requires an operator-opens-for-review event (deferred —
 * operators today only have the decision binder, no separate "open"); and
 * WITHDRAWN requires a citizen-withdraw UI action (deferred).
 *
 * <p>Decoupled from {@code c_status}: the existing fine-grained
 * column keeps its rules-driven values ({@code auto_approved},
 * {@code auto_rejected}, etc.); this enum lives in a separate
 * {@code c_lifecycleState} column. Mapping between the two is captured in
 * {@link AppLifecycleMapper}.
 *
 * <p>Codes are stored lowercase for consistency with the rest of the
 * project's status conventions.
 */
public enum AppLifecycleStatus implements Status {

    DRAFT          ("draft",          "Draft"),
    SUBMITTED      ("submitted",      "Submitted"),
    UNDER_REVIEW   ("under_review",   "Under Review"),
    APPROVED       ("approved",       "Approved"),
    REJECTED       ("rejected",       "Rejected"),
    PENDING_REVIEW ("pending_review", "Pending Review"),
    WITHDRAWN      ("withdrawn",      "Withdrawn");

    private final String code;
    private final String label;

    AppLifecycleStatus(String code, String label) {
        this.code  = code;
        this.label = label;
    }

    @Override public String getCode()  { return code; }
    @Override public String getLabel() { return label; }
}
