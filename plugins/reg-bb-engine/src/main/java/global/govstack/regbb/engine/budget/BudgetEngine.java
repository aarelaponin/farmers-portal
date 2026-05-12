package global.govstack.regbb.engine.budget;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.evaluator.RoutingEvaluator;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L3-1 Budget Engine — implements the public-sector fund-accounting
 * methodology in {@code _design/architecture/components/budget-accounting-methodology.md}.
 *
 * <p>Single entry point: {@link #dispatch(DispatchRequest)}. Given an
 * mm_action code and an applicant context, the engine:
 *
 * <ol>
 *   <li>Looks up the action's {@code triggerJson} (eventType +
 *       amountFormulaRule + optional condition).</li>
 *   <li>Evaluates the optional condition rule; skips if FALSE.</li>
 *   <li>Resolves the envelope-level amount via {@link RoutingEvaluator}
 *       on the named amountFormulaRule.</li>
 *   <li>Reads the envelope's source-contribution rows and prorates the
 *       amount with banker's rounding (largest source rounds last to
 *       absorb residual — methodology §6.3).</li>
 *   <li>Pre-flight verifies invariants (no-negative AVAILABLE per
 *       methodology §4.6).</li>
 *   <li>Writes balanced journal entries — one per
 *       (envelope|source) × subaccount-pair — to {@code app_fd_budget_event}
 *       through {@link FormDataDao}, all sharing one transaction_id.</li>
 *   <li>Maintains beneficiary sub-ledger: opens BNF account on
 *       PRE_COMMITMENT, closes on RELEASE_PRE_COMMITMENT.</li>
 *   <li>Refreshes {@code budget_projection} materialised view.</li>
 * </ol>
 *
 * <p><b>Idempotency.</b> Every dispatch carries an idempotency key
 * computed from (actionCode + applicationId + eventType). If a journal
 * entry with that key already exists, the dispatch is a no-op (returns
 * the original transaction_id). Prevents double-posting on retry.
 *
 * <p><b>Per-bundle co-location.</b> Per the L3-1 1B amendment to ADR-022:
 * the Budget Engine lives in the same bundle as the rest of reg-bb-engine
 * to avoid OSGi cross-classloader complexity. Sub-package separation
 * ({@code .budget.*}) gives most of SRP's structural value without
 * bundle-boundary tax.
 */
public final class BudgetEngine {

    private static final String CLASS_NAME = BudgetEngine.class.getName();

    private static final String FORM_BUDGET_ENVELOPE     = "budget_envelope";
    private static final String FORM_BUDGET_ENVELOPE_SRC = "budget_envelope_source";
    private static final String FORM_BUDGET_EVENT        = "budget_event";
    private static final String FORM_BNF_SUBLEDGER       = "beneficiary_subledger";
    private static final String FORM_MM_ACTION           = "mm_action";

    /**
     * Output of a successful dispatch — the transaction_id (linking
     * journal lines), the per-account postings actually written, and
     * the resulting projection state for the affected envelope.
     */
    public static final class DispatchResult {
        public final String  status;          // "posted" | "skipped_condition" | "no_op_idempotent"
        public final String  transactionId;
        public final String  envelopeCode;
        public final String  eventType;
        public final BigDecimal amount;
        public final List<JournalLine> journalLines;
        public final String  beneficiaryAccountCode;  // non-null on PRE_COMMITMENT
        public final String  errorCause;       // non-null on failure
        public final long    elapsedMs;

        DispatchResult(String status, String txId, String envelopeCode, String eventType,
                       BigDecimal amount, List<JournalLine> lines, String bnfCode,
                       String errorCause, long elapsedMs) {
            this.status = status;
            this.transactionId = txId;
            this.envelopeCode = envelopeCode;
            this.eventType = eventType;
            this.amount = amount;
            this.journalLines = lines;
            this.beneficiaryAccountCode = bnfCode;
            this.errorCause = errorCause;
            this.elapsedMs = elapsedMs;
        }
    }

    public static final class JournalLine {
        public final String accountPath;
        public final String direction;      // "debit" | "credit"
        public final BigDecimal amount;
        JournalLine(String accountPath, String direction, BigDecimal amount) {
            this.accountPath = accountPath;
            this.direction   = direction;
            this.amount      = amount;
        }
        @Override public String toString() {
            return String.format("%s %s %s", direction, amount.toPlainString(), accountPath);
        }
    }

    /**
     * Input to dispatch. Most fields come from the upstream lifecycle hook;
     * applicantData is the full row data the amount-formula rule
     * evaluates against.
     */
    public static final class DispatchRequest {
        public final String actionCode;
        public final String applicationId;
        public final String envelopeCode;        // optional override; if null, the listener resolves from applied_programme
        public final String actor;               // user-id or "system"
        public final String correlationType;     // "subsidy_application" etc.
        public final String sourceModule;        // "subsidy" | "im" | "manual"
        public final Map<String, Object> applicantData;

        public DispatchRequest(String actionCode, String applicationId, String envelopeCode,
                               String actor, String correlationType, String sourceModule,
                               Map<String, Object> applicantData) {
            this.actionCode      = actionCode;
            this.applicationId   = applicationId;
            this.envelopeCode    = envelopeCode;
            this.actor           = actor != null ? actor : "system";
            this.correlationType = correlationType != null ? correlationType : "subsidy_application";
            this.sourceModule    = sourceModule != null ? sourceModule : "subsidy";
            this.applicantData   = applicantData != null ? applicantData : new HashMap<>();
        }
    }

    private final FormDataDao dao;
    private final RoutingEvaluator evaluator;

    public BudgetEngine() {
        this.dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        this.evaluator = new RoutingEvaluator();
    }

    public BudgetEngine(FormDataDao dao, RoutingEvaluator evaluator) {
        this.dao = dao;
        this.evaluator = evaluator;
    }

    // -----------------------------------------------------------------
    //  Main entry point
    // -----------------------------------------------------------------

    public DispatchResult dispatch(DispatchRequest req) {
        long started = System.currentTimeMillis();
        try {
            // 1. Load mm_action by code.
            FormRow action = findActionByCode(req.actionCode);
            if (action == null) {
                return error(req, "action_not_found:" + req.actionCode, started);
            }
            String kind = nz(prop(action, "kind"));
            if (!"budget_event".equalsIgnoreCase(kind)) {
                return error(req, "action_not_budget_event:kind=" + kind, started);
            }

            // 2. Parse triggerJson — eventType + amountFormulaRule + optional condition.
            String triggerJsonStr = nz(prop(action, "triggerJson"));
            String configJsonStr  = nz(prop(action, "configJson"));
            String eventType         = jsonValueOf(triggerJsonStr, "eventType");
            String amountFormulaRule = jsonValueOf(triggerJsonStr, "amountFormulaRule");
            String conditionRule     = jsonValueOf(triggerJsonStr, "conditionRule");
            String envelopeCodeFromTrigger = jsonValueOf(triggerJsonStr, "envelopeCode");

            if (eventType == null || eventType.isEmpty()) {
                return error(req, "trigger_missing_eventType", started);
            }
            if (amountFormulaRule == null || amountFormulaRule.isEmpty()) {
                return error(req, "trigger_missing_amountFormulaRule", started);
            }

            // 3. Optional condition — skip if FALSE.
            if (conditionRule != null && !conditionRule.isEmpty()) {
                EvalResult cr = evaluator.evaluate(conditionRule,
                        EvalContext.builder().data(req.applicantData)
                                .applicationId(req.applicationId).build());
                if (cr != null && cr.outcome == EvalResult.Outcome.FALSE) {
                    return new DispatchResult("skipped_condition", null, null, eventType,
                            null, new ArrayList<>(), null, null,
                            System.currentTimeMillis() - started);
                }
            }

            // 4. Resolve envelope.
            String envelopeCode = req.envelopeCode != null && !req.envelopeCode.isEmpty()
                    ? req.envelopeCode
                    : envelopeCodeFromTrigger;
            if (envelopeCode == null || envelopeCode.isEmpty()) {
                // Future: derive from mm_registration[applied_programme].budgetEnvelopeCode.
                // For 1B-i, require it to be passed in.
                return error(req, "envelope_unresolved", started);
            }

            // 5. Resolve amount.
            //
            // 1B-i strategy: amount = mm_determinant.targetValue (a flat
            // numeric string, e.g. "2000.00"). The closed-twenty rule
            // grammar (ADR-001) is boolean-only — full expression
            // evaluation for amount formulas (e.g. drought = base + per-
            // dependent) is 1C work, requiring a numeric expression
            // extension to FastPathEvaluator. For 1B-i, simple flat
            // amounts cover all four 2025 programmes; ruleJson stays
            // empty (or carries an applicability gate that future
            // versions can evaluate against the applicant context below).
            FormRow amountRule = findDeterminantByCode(amountFormulaRule);
            if (amountRule == null) {
                return error(req, "amount_rule_not_found:" + amountFormulaRule, started);
            }
            BigDecimal amount = parseDecimal(prop(amountRule, "targetValue"));
            if (amount == null || amount.signum() <= 0) {
                return error(req, "amount_rule_target_value_invalid:"
                        + amountFormulaRule + ":" + prop(amountRule, "targetValue"), started);
            }
            amount = amount.setScale(2, RoundingMode.HALF_EVEN);
            String ruleVersion = amountFormulaRule;  // future: read mm_determinant.version

            // 6. Idempotency check — has this exact (action, application, event)
            // been posted already?
            String idempotencyKey = req.actionCode + "|" + req.applicationId + "|" + eventType;
            String priorTxId = findPriorTransactionByIdempotency(idempotencyKey);
            if (priorTxId != null) {
                return new DispatchResult("no_op_idempotent", priorTxId, envelopeCode, eventType,
                        amount, new ArrayList<>(), null, null,
                        System.currentTimeMillis() - started);
            }

            // 7. Read envelope + source contributions.
            FormRow envelope = findEnvelopeByCode(envelopeCode);
            if (envelope == null) {
                return error(req, "envelope_not_found:" + envelopeCode, started);
            }
            // Envelope freeze check — reject any forward-funnel motion when
            // the envelope is frozen or closed. RELEASE_* events are still
            // allowed so investigators can release stuck reservations.
            String envStatus = nz(prop(envelope, "status"));
            if (("frozen".equalsIgnoreCase(envStatus) || "closed".equalsIgnoreCase(envStatus))
                    && !isReleaseEvent(eventType)) {
                String reason = prop(envelope, "frozen_reason");
                if (reason == null || reason.isEmpty()) reason = "no reason recorded";
                return error(req, "envelope_" + envStatus + ":" + envelopeCode
                        + ":" + reason, started);
            }
            List<FormRow> sources = listSourcesForEnvelope(envelopeCode);
            if (sources.isEmpty()) {
                return error(req, "envelope_has_no_sources:" + envelopeCode, started);
            }

            // 8. Pre-flight — for amount-decreasing transactions, verify
            // AVAILABLE >= amount on the envelope. Read the projection.
            if (isAvailableConsumer(eventType)) {
                BigDecimal envAvailable = readProjectionAvailable(envelopeCode);
                if (envAvailable == null) envAvailable = BigDecimal.ZERO;
                if (envAvailable.compareTo(amount) < 0) {
                    // Future: budget_overrun_policy rule check. For 1B-i, hard-reject.
                    return error(req, "insufficient_budget:available=" + envAvailable
                            + ",need=" + amount, started);
                }
            }

            // 9. Compose journal entries per methodology §3.
            List<JournalLine> envLines = composeEnvelopeJournal(envelopeCode, eventType, amount);
            Map<String, BigDecimal> sourceProrations = prorateAcrossSources(sources, amount);
            List<JournalLine> srcLines = composeSourceJournals(sources, eventType, sourceProrations);
            List<JournalLine> allLines = new ArrayList<>(envLines.size() + srcLines.size());
            allLines.addAll(envLines);
            allLines.addAll(srcLines);

            // 10. Persist all lines under one transaction_id.
            String transactionId = UUID.randomUUID().toString();
            for (JournalLine line : allLines) {
                writeEvent(transactionId, eventType, envelopeCode, line, idempotencyKey,
                        ruleVersion, req);
            }

            // 11. Sub-ledger maintenance.
            String bnfCode = null;
            if ("PRE_COMMITMENT".equals(eventType)) {
                bnfCode = openBeneficiaryAccount(envelopeCode, req, amount, sourceProrations,
                        ruleVersion);
            } else if ("RELEASE_PRE_COMMITMENT".equals(eventType)) {
                bnfCode = closeBeneficiaryAccount(req.applicationId, "released");
            } else if ("EXPENSE".equals(eventType)) {
                bnfCode = decrementBeneficiaryBalance(req.applicationId, amount);
            }

            // 12. Per ADR-030 Step 5: projection refresh moved out of the
            // dispatch hot path. BudgetProjectionRefreshJob runs every 30s
            // on a schedule. Dashboards see at most 30s of lag; the operator
            // decision form's budget-hint widget refreshes per-envelope on
            // demand when it renders. Removing the inline refresh here
            // dropped per-dispatch cost from ~2-3s to ~50-100ms.

            return new DispatchResult("posted", transactionId, envelopeCode, eventType,
                    amount, allLines, bnfCode, null,
                    System.currentTimeMillis() - started);

        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "dispatch failed for action=" + req.actionCode);
            return error(req, "internal:" + t.getClass().getSimpleName() + ":"
                    + (t.getMessage() == null ? "" : t.getMessage()), started);
        }
    }

    // -----------------------------------------------------------------
    //  L3-1 maker-checker — direct dispatch for manual adjustments
    // -----------------------------------------------------------------

    /**
     * Direct dispatch entry point for manual adjustments (budget_adjustment_request
     * approval flow). Bypasses the mm_action / mm_determinant resolution path
     * because the amount is supplied by the operator on the form rather than
     * coming from a determinant rule.
     *
     * <p>Use cases: MANUAL_TOP_UP, MANUAL_CLAWBACK, two-leg REALLOCATION
     * (caller dispatches CLAWBACK then TOP_UP with a shared correlation_id).
     *
     * <p>Same idempotency / pre-flight / journal / sub-ledger guarantees as
     * the standard {@link #dispatch} flow.
     */
    public DispatchResult dispatchDirect(String envelopeCode, String eventType,
                                          BigDecimal amount, String actor,
                                          String correlationType, String correlationId,
                                          String idempotencyKey,
                                          Map<String, Object> applicantData) {
        long started = System.currentTimeMillis();
        // Reuse the public DispatchRequest as the audit-trail context. actionCode
        // here is synthetic ("MANUAL_<eventType>_<idempotencyKey>") so the audit
        // event has a stable handle even though no mm_action row exists.
        DispatchRequest req = new DispatchRequest(
                "MANUAL_" + eventType + ":" + (idempotencyKey != null && !idempotencyKey.isEmpty() ? idempotencyKey : correlationId),
                correlationId,
                envelopeCode,
                actor,
                correlationType != null ? correlationType : "manual_adjustment",
                "budget_adjustment",
                applicantData);
        try {
            if (eventType == null || eventType.isEmpty()) {
                return error(req, "eventType_required", started);
            }
            if (amount == null || amount.signum() <= 0) {
                return error(req, "amount_must_be_positive", started);
            }
            amount = amount.setScale(2, RoundingMode.HALF_EVEN);

            // Idempotency: caller-supplied key (or fall back to one we derive).
            String idem = (idempotencyKey != null && !idempotencyKey.isEmpty())
                    ? idempotencyKey
                    : ("manual:" + correlationId + ":" + eventType);
            String priorTxId = findPriorTransactionByIdempotency(idem);
            if (priorTxId != null) {
                return new DispatchResult("no_op_idempotent", priorTxId, envelopeCode, eventType,
                        amount, new ArrayList<>(), null, null,
                        System.currentTimeMillis() - started);
            }

            // Resolve envelope (must exist).
            FormRow envelope = findEnvelopeByCode(envelopeCode);
            if (envelope == null) {
                return error(req, "envelope_not_found:" + envelopeCode, started);
            }
            // Freeze check — manual adjustments are forward-funnel motion
            // and must respect freeze, but a MANUAL_CLAWBACK on a frozen
            // envelope is actually a recovery action and SHOULD be allowed.
            // Distinguish: frozen rejects MANUAL_TOP_UP, allows MANUAL_CLAWBACK.
            // Closed rejects everything.
            String envStatus = nz(prop(envelope, "status"));
            if ("closed".equalsIgnoreCase(envStatus)) {
                return error(req, "envelope_closed:" + envelopeCode, started);
            }
            if ("frozen".equalsIgnoreCase(envStatus) && !"MANUAL_CLAWBACK".equals(eventType)) {
                String reason = prop(envelope, "frozen_reason");
                if (reason == null || reason.isEmpty()) reason = "no reason recorded";
                return error(req, "envelope_frozen:" + envelopeCode + ":" + reason, started);
            }

            // Pre-flight for AVAILABLE-consuming events (MANUAL_CLAWBACK).
            if (isAvailableConsumer(eventType)) {
                BigDecimal envAvailable = readProjectionAvailable(envelopeCode);
                if (envAvailable == null) envAvailable = BigDecimal.ZERO;
                if (envAvailable.compareTo(amount) < 0) {
                    return error(req, "insufficient_budget:available=" + envAvailable
                            + ",need=" + amount, started);
                }
            }

            // Compose envelope-level journal. Source-contribution journals
            // intentionally skipped for manual adjustments — these are
            // envelope-level corrections, not source allocations.
            List<JournalLine> envLines = composeEnvelopeJournal(envelopeCode, eventType, amount);

            // Persist under one transaction_id.
            String transactionId = UUID.randomUUID().toString();
            String ruleVersion = "manual";
            for (JournalLine line : envLines) {
                writeEvent(transactionId, eventType, envelopeCode, line, idem,
                        ruleVersion, req);
            }

            // ADR-030 Step 5: refresh deferred to BudgetProjectionRefreshJob.
            return new DispatchResult("posted", transactionId, envelopeCode, eventType,
                    amount, envLines, null, null,
                    System.currentTimeMillis() - started);
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "dispatchDirect failed for envelope=" + envelopeCode
                    + " eventType=" + eventType);
            return error(req, "internal:" + t.getClass().getSimpleName() + ":"
                    + (t.getMessage() == null ? "" : t.getMessage()), started);
        }
    }

    // -----------------------------------------------------------------
    //  L3-1 1B-ii — lifecycle hook for the subsidy storeBinders
    // -----------------------------------------------------------------

    /**
     * Map an application's current status to the budget events that need
     * firing, then dispatch them. Called from
     * {@code RegBbApplicationStoreBinder} (citizen submit / auto-decision)
     * and {@code RegBbOperatorDecisionBinder} (operator decision).
     *
     * <p>Status → events:
     * <ul>
     *   <li>{@code pending_operator_review} or {@code pending_data_clarification}
     *       → RESERVATION (citizen has staked a claim).</li>
     *   <li>{@code auto_approved} or {@code approved}
     *       → RESERVATION + PRE_COMMITMENT (claim → obligation).</li>
     *   <li>{@code auto_rejected} or {@code rejected}
     *       → RESERVATION + RELEASE_RESERVATION (claim made, then released).
     *       Net is zero, but both events stay in the ledger for audit
     *       per methodology §6.2 (no UPDATE / DELETE).</li>
     *   <li>{@code sent_back} → no change (status stays pending).</li>
     * </ul>
     *
     * <p>Idempotency by (actionCode, applicationId, eventType) ensures that
     * re-firing on re-save is a no-op — so a citizen submit fires
     * RESERVATION, the operator-side approve fires RESERVATION (no-op) AND
     * PRE_COMMITMENT (new posting). Clean.
     *
     * <p>All-failures-soft: any per-event dispatch failure is logged but
     * doesn't abort the lifecycle. The application save has already
     * happened; budget posting failures must not break user flow. A
     * future reconciliation job catches any divergence between the
     * application table and the ledger.
     */
    public static void fireForLifecycle(String applicationId, String programmeCode,
                                        String newStatus,
                                        Map<String, Object> applicantData,
                                        String actor) {
        if (applicationId == null || applicationId.isEmpty()) return;
        if (programmeCode == null || programmeCode.isEmpty()) return;
        if (newStatus == null || newStatus.isEmpty()) return;

        String envelopeCode = resolveEnvelopeForProgramme(programmeCode);
        String suffix = programmeShortSuffix(programmeCode);  // "001" from "PRG_2025_001"
        if (envelopeCode == null || suffix == null) {
            LogUtil.warn(CLASS_NAME, "fireForLifecycle: cannot resolve envelope/suffix for programme="
                    + programmeCode);
            return;
        }

        BudgetEngine engine = new BudgetEngine();
        switch (newStatus) {
            case "auto_approved":
            case "approved":
                fireSafe(engine, "BUDGET_RESERVE_ON_SUBMIT_PRG_" + suffix,
                         applicationId, envelopeCode, applicantData, actor);
                fireSafe(engine, "BUDGET_PRE_COMMIT_ON_APPROVE_PRG_" + suffix,
                         applicationId, envelopeCode, applicantData, actor);
                break;
            case "auto_rejected":
            case "rejected":
                fireSafe(engine, "BUDGET_RESERVE_ON_SUBMIT_PRG_" + suffix,
                         applicationId, envelopeCode, applicantData, actor);
                fireSafe(engine, "BUDGET_RELEASE_ON_REJECT_PRG_" + suffix,
                         applicationId, envelopeCode, applicantData, actor);
                break;
            case "pending_operator_review":
            case "pending_data_clarification":
                fireSafe(engine, "BUDGET_RESERVE_ON_SUBMIT_PRG_" + suffix,
                         applicationId, envelopeCode, applicantData, actor);
                break;
            case "sent_back":
                // No budget change — application is back in pending state with
                // its prior RESERVATION still held.
                break;
            default:
                LogUtil.warn(CLASS_NAME, "fireForLifecycle: unrecognised status='" + newStatus
                        + "' for applicationId=" + applicationId + " — no events fired");
        }
    }

    /** Naming convention for envelope code: ENV_<programme>_FY<yy><yy+1>.
     *  For 2025/26 cycle: PRG_2025_001 → ENV_PRG_2025_001_FY2526. Future
     *  envelopes (2026/27 cycle) will need a fiscal-year resolution layer
     *  reading from mm_registration; this hardcoded form is a single-cycle
     *  shortcut. */
    private static String resolveEnvelopeForProgramme(String programmeCode) {
        if (programmeCode == null || programmeCode.isEmpty()) return null;
        // Pull the year from the programme code if it has one (PRG_<YYYY>_<NNN>).
        String[] parts = programmeCode.split("_");
        if (parts.length >= 3) {
            try {
                int year = Integer.parseInt(parts[1]);
                int next = year + 1;
                String fy = "FY" + (year % 100) + (next % 100);
                return "ENV_" + programmeCode + "_" + fy;
            } catch (NumberFormatException ignore) { }
        }
        // Fall back to the 2025/26 cycle.
        return "ENV_" + programmeCode + "_FY2526";
    }

    /** Last segment of a programme code, used to compose mm_action codes
     *  by appending to BUDGET_RESERVE_ON_SUBMIT_PRG_ etc. */
    private static String programmeShortSuffix(String programmeCode) {
        if (programmeCode == null) return null;
        int idx = programmeCode.lastIndexOf('_');
        if (idx < 0) return programmeCode;
        return programmeCode.substring(idx + 1);
    }

    /** Best-effort dispatch — logs failure but doesn't propagate. */
    private static void fireSafe(BudgetEngine engine, String actionCode, String applicationId,
                                 String envelopeCode, Map<String, Object> applicantData,
                                 String actor) {
        try {
            DispatchRequest req = new DispatchRequest(actionCode, applicationId, envelopeCode,
                    actor, "subsidy_application", "subsidy", applicantData);
            DispatchResult r = engine.dispatch(req);
            if (!"posted".equals(r.status) && !"no_op_idempotent".equals(r.status)
                    && !"skipped_condition".equals(r.status)) {
                LogUtil.warn(CLASS_NAME, "fireForLifecycle: " + actionCode + " for app "
                        + applicationId + " status=" + r.status + " cause=" + r.errorCause);
            } else {
                LogUtil.debug(CLASS_NAME, "fireForLifecycle: " + actionCode + " → "
                        + r.status + " tx=" + r.transactionId);
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "fireForLifecycle dispatch failed: " + actionCode
                    + " for app " + applicationId);
        }
    }

    // -----------------------------------------------------------------
    //  Journal composition per methodology §3
    // -----------------------------------------------------------------

    /** True when the event reverses a prior forward-funnel motion (releases
     *  funds back to AVAILABLE). Frozen envelopes accept these so admins can
     *  unstick reservations during an investigation. */
    private static boolean isReleaseEvent(String eventType) {
        if (eventType == null) return false;
        return eventType.startsWith("RELEASE_");
    }

    /** Map an event type to whether it consumes AVAILABLE (forward funnel
     *  motion) — the pre-flight check applies only to these. */
    private static boolean isAvailableConsumer(String eventType) {
        switch (eventType) {
            case "RESERVATION":
            case "MANUAL_CLAWBACK":   // takes funds OUT of AVAILABLE; pre-flight
                                      // must verify AVAILABLE ≥ amount.
                return true;
            default:
                return false;  // PRE_COMMITMENT / COMMITMENT / EXPENSE move money
                               // *within* the envelope, not out of AVAILABLE.
                               // MANUAL_TOP_UP adds to AVAILABLE; no pre-flight.
        }
    }

    /** Envelope-level journal entries for a transaction type. Per
     *  methodology §3 — debit-the-target-subaccount, credit-the-source-
     *  subaccount within the same envelope. */
    private static List<JournalLine> composeEnvelopeJournal(String envCode, String eventType,
                                                            BigDecimal amount) {
        List<JournalLine> out = new ArrayList<>(2);
        switch (eventType) {
            case "ALLOCATION":
                out.add(line(envCode + ".ALLOCATED", "debit",  amount));
                out.add(line(envCode + ".AVAILABLE", "debit",  amount));
                break;
            case "RESERVATION":
                out.add(line(envCode + ".RESERVED",  "debit",  amount));
                out.add(line(envCode + ".AVAILABLE", "credit", amount));
                break;
            case "RELEASE_RESERVATION":
                out.add(line(envCode + ".AVAILABLE", "debit",  amount));
                out.add(line(envCode + ".RESERVED",  "credit", amount));
                break;
            case "PRE_COMMITMENT":
                out.add(line(envCode + ".PRE_COMMITTED", "debit",  amount));
                out.add(line(envCode + ".RESERVED",      "credit", amount));
                break;
            case "RELEASE_PRE_COMMITMENT":
                out.add(line(envCode + ".AVAILABLE",     "debit",  amount));
                out.add(line(envCode + ".PRE_COMMITTED", "credit", amount));
                break;
            case "COMMITMENT":
                out.add(line(envCode + ".COMMITTED",      "debit",  amount));
                out.add(line(envCode + ".PRE_COMMITTED",  "credit", amount));
                break;
            case "RELEASE_COMMITMENT":
                out.add(line(envCode + ".AVAILABLE", "debit",  amount));
                out.add(line(envCode + ".COMMITTED", "credit", amount));
                break;
            case "EXPENSE":
                out.add(line(envCode + ".EXPENSED",  "debit",  amount));
                out.add(line(envCode + ".COMMITTED", "credit", amount));
                break;
            case "MANUAL_TOP_UP":
                // Donor adds funds, or correction increases the envelope.
                // Mirrors ALLOCATION but dispatched manually (correlation_type =
                // "manual_adjustment", actor = approver). Maintains the
                // ALLOCATED = AVAILABLE + RESERVED + PRE_COMMITTED + COMMITTED
                // + EXPENSED identity by debiting both ALLOCATED and AVAILABLE
                // by the same amount. Source-contribution journals are
                // intentionally not generated for manual adjustments — these
                // are envelope-level corrections; if a particular source
                // needs separate treatment, log a separate adjustment per
                // source contribution.
                out.add(line(envCode + ".ALLOCATED", "debit", amount));
                out.add(line(envCode + ".AVAILABLE", "debit", amount));
                break;
            case "MANUAL_CLAWBACK":
                // Reverses MANUAL_TOP_UP. Decreases envelope size; only valid
                // if AVAILABLE ≥ amount (enforced by isAvailableConsumer
                // pre-flight). Maintains identity symmetrically.
                out.add(line(envCode + ".ALLOCATED", "credit", amount));
                out.add(line(envCode + ".AVAILABLE", "credit", amount));
                break;
            default:
                throw new IllegalArgumentException("unsupported_event_type:" + eventType);
        }
        return out;
    }

    /** Source-contribution journal entries — same shape as envelope, but
     *  per source contribution and at the prorated amount. */
    private static List<JournalLine> composeSourceJournals(List<FormRow> sources, String eventType,
                                                           Map<String, BigDecimal> prorations) {
        List<JournalLine> out = new ArrayList<>(sources.size() * 2);
        for (FormRow src : sources) {
            String srcCode = nz(prop(src, "code"));
            BigDecimal srcAmt = prorations.get(srcCode);
            if (srcAmt == null || srcAmt.signum() == 0) continue;
            // Same pattern as envelope, but with source-contribution path.
            switch (eventType) {
                case "ALLOCATION":
                    out.add(line(srcCode + ".ALLOCATED", "debit",  srcAmt));
                    out.add(line(srcCode + ".AVAILABLE", "debit",  srcAmt));
                    break;
                case "RESERVATION":
                    out.add(line(srcCode + ".RESERVED",  "debit",  srcAmt));
                    out.add(line(srcCode + ".AVAILABLE", "credit", srcAmt));
                    break;
                case "RELEASE_RESERVATION":
                    out.add(line(srcCode + ".AVAILABLE", "debit",  srcAmt));
                    out.add(line(srcCode + ".RESERVED",  "credit", srcAmt));
                    break;
                case "PRE_COMMITMENT":
                    out.add(line(srcCode + ".PRE_COMMITTED", "debit",  srcAmt));
                    out.add(line(srcCode + ".RESERVED",      "credit", srcAmt));
                    break;
                case "RELEASE_PRE_COMMITMENT":
                    out.add(line(srcCode + ".AVAILABLE",     "debit",  srcAmt));
                    out.add(line(srcCode + ".PRE_COMMITTED", "credit", srcAmt));
                    break;
                case "COMMITMENT":
                    out.add(line(srcCode + ".COMMITTED",     "debit",  srcAmt));
                    out.add(line(srcCode + ".PRE_COMMITTED", "credit", srcAmt));
                    break;
                case "RELEASE_COMMITMENT":
                    out.add(line(srcCode + ".AVAILABLE", "debit",  srcAmt));
                    out.add(line(srcCode + ".COMMITTED", "credit", srcAmt));
                    break;
                case "EXPENSE":
                    out.add(line(srcCode + ".EXPENSED",  "debit",  srcAmt));
                    out.add(line(srcCode + ".COMMITTED", "credit", srcAmt));
                    break;
            }
        }
        return out;
    }

    /** Banker's rounding with largest-source-rounds-last residual absorption.
     *  Methodology §6.3: source amounts must sum exactly to envelope amount. */
    static Map<String, BigDecimal> prorateAcrossSources(List<FormRow> sources, BigDecimal amount) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (sources.isEmpty()) return out;

        // Compute total share — should be 100 but be defensive.
        BigDecimal totalShare = BigDecimal.ZERO;
        for (FormRow s : sources) {
            BigDecimal sh = parseDecimal(prop(s, "share_percent"));
            if (sh != null) totalShare = totalShare.add(sh);
        }
        if (totalShare.signum() == 0) return out;

        // Identify the largest source so it absorbs rounding residual.
        FormRow largest = null;
        BigDecimal largestShare = BigDecimal.ZERO;
        for (FormRow s : sources) {
            BigDecimal sh = parseDecimal(prop(s, "share_percent"));
            if (sh != null && sh.compareTo(largestShare) > 0) {
                largest = s; largestShare = sh;
            }
        }
        if (largest == null) largest = sources.get(0);

        BigDecimal allocated = BigDecimal.ZERO;
        for (FormRow s : sources) {
            String code = nz(prop(s, "code"));
            if (s == largest) continue;
            BigDecimal sh = parseDecimal(prop(s, "share_percent"));
            BigDecimal portion = amount.multiply(sh).divide(totalShare, 2, RoundingMode.HALF_EVEN);
            out.put(code, portion);
            allocated = allocated.add(portion);
        }
        // Largest source gets the residual so the sum exactly equals the envelope amount.
        out.put(nz(prop(largest, "code")), amount.subtract(allocated));
        return out;
    }

    // -----------------------------------------------------------------
    //  Persistence — through Joget's FormDataDao + JDBC for view refresh
    // -----------------------------------------------------------------

    private FormRow findActionByCode(String code) {
        FormRowSet rs = dao.find(FORM_MM_ACTION, FORM_MM_ACTION,
                "WHERE e.customProperties.code = ?", new Object[] { code },
                null, false, null, null);
        return (rs == null || rs.isEmpty()) ? null : rs.get(0);
    }

    private FormRow findDeterminantByCode(String code) {
        FormRowSet rs = dao.find("mm_determinant", "mm_determinant",
                "WHERE e.customProperties.code = ?", new Object[] { code },
                null, false, null, null);
        return (rs == null || rs.isEmpty()) ? null : rs.get(0);
    }

    private FormRow findEnvelopeByCode(String code) {
        FormRowSet rs = dao.find(FORM_BUDGET_ENVELOPE, FORM_BUDGET_ENVELOPE,
                "WHERE e.customProperties.code = ?", new Object[] { code },
                null, false, null, null);
        return (rs == null || rs.isEmpty()) ? null : rs.get(0);
    }

    private List<FormRow> listSourcesForEnvelope(String envelopeCode) {
        FormRowSet rs = dao.find(FORM_BUDGET_ENVELOPE_SRC, FORM_BUDGET_ENVELOPE_SRC,
                "WHERE e.customProperties.envelope_code = ?",
                new Object[] { envelopeCode },
                "code", false, null, null);
        return (rs == null || rs.isEmpty()) ? new ArrayList<>() : new ArrayList<>(rs);
    }

    private String findPriorTransactionByIdempotency(String key) {
        // Query DB directly — read-only is HARD-RULE-permitted.
        String sql = "SELECT c_transaction_id FROM app_fd_budget_event "
                   + "WHERE c_idempotency_key = ? LIMIT 1";
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, key);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException sqle) {
            LogUtil.warn(CLASS_NAME, "idempotency lookup failed: " + sqle.getMessage());
        }
        return null;
    }

    private BigDecimal readProjectionAvailable(String envelopeCode) {
        String sql = "SELECT available FROM budget_projection WHERE envelope_code = ?";
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, envelopeCode);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        } catch (SQLException sqle) {
            LogUtil.warn(CLASS_NAME, "projection lookup failed: " + sqle.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private void writeEvent(String txId, String eventType, String envelopeCode, JournalLine line,
                            String idempotencyKey, String ruleVersion, DispatchRequest req) {
        FormRow row = new FormRow();
        row.setId(UUID.randomUUID().toString());
        Date now = new Date();
        row.setProperty("dateCreated",  iso(now));
        row.setProperty("dateModified", iso(now));
        row.setProperty("transaction_id",  txId);
        row.setProperty("event_type",      eventType);
        row.setProperty("envelope_code",   envelopeCode);
        row.setProperty("account_path",    line.accountPath);
        row.setProperty("direction",       line.direction);
        row.setProperty("amount",          line.amount.toPlainString());
        row.setProperty("currency",        "LSL");
        row.setProperty("correlation_id",  req.applicationId == null ? "" : req.applicationId);
        row.setProperty("correlation_type", req.correlationType);
        row.setProperty("source_module",   req.sourceModule);
        row.setProperty("actor",           req.actor);
        row.setProperty("action_code",     req.actionCode);
        row.setProperty("rule_version",    ruleVersion == null ? "" : ruleVersion);
        row.setProperty("idempotency_key", idempotencyKey);
        FormRowSet rs = new FormRowSet();
        rs.add(row);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_BUDGET_EVENT, FORM_BUDGET_EVENT, rs);
    }

    private String openBeneficiaryAccount(String envelopeCode, DispatchRequest req,
                                          BigDecimal amountTotal,
                                          Map<String, BigDecimal> prorations,
                                          String ruleVersion) {
        String code = "BNF_" + req.applicationId;
        FormRow row = new FormRow();
        row.setId(UUID.randomUUID().toString());
        Date now = new Date();
        row.setProperty("dateCreated",  iso(now));
        row.setProperty("dateModified", iso(now));
        row.setProperty("code",                  code);
        row.setProperty("envelope_code",         envelopeCode);
        row.setProperty("application_id",        nz(req.applicationId));
        row.setProperty("applicant_national_id", nz(str(req.applicantData.get("national_id"))));
        row.setProperty("applicant_name",        nz(str(req.applicantData.get("full_name"))));
        row.setProperty("amount_total",          amountTotal.toPlainString());
        row.setProperty("amount_by_source_json", jsonEncode(prorations));
        row.setProperty("status",                "open");
        row.setProperty("pre_committed_at",      iso(now));
        row.setProperty("rule_version",          nz(ruleVersion));
        FormRowSet rs = new FormRowSet();
        rs.add(row);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_BNF_SUBLEDGER, FORM_BNF_SUBLEDGER, rs);
        return code;
    }

    private String closeBeneficiaryAccount(String applicationId, String reason) {
        if (applicationId == null) return null;
        String code = "BNF_" + applicationId;
        FormRowSet rs = dao.find(FORM_BNF_SUBLEDGER, FORM_BNF_SUBLEDGER,
                "WHERE e.customProperties.code = ?", new Object[] { code },
                null, false, null, null);
        if (rs == null || rs.isEmpty()) return null;
        FormRow existing = rs.get(0);
        existing.setProperty("status", reason);
        existing.setProperty("amount_total", "0.00");
        existing.setProperty("closed_at", iso(new Date()));
        existing.setProperty("dateModified", iso(new Date()));
        FormRowSet save = new FormRowSet();
        save.add(existing);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_BNF_SUBLEDGER, FORM_BNF_SUBLEDGER, save);
        return code;
    }

    private String decrementBeneficiaryBalance(String applicationId, BigDecimal amount) {
        if (applicationId == null) return null;
        String code = "BNF_" + applicationId;
        FormRowSet rs = dao.find(FORM_BNF_SUBLEDGER, FORM_BNF_SUBLEDGER,
                "WHERE e.customProperties.code = ?", new Object[] { code },
                null, false, null, null);
        if (rs == null || rs.isEmpty()) return null;
        FormRow existing = rs.get(0);
        BigDecimal current = parseDecimal(prop(existing, "amount_total"));
        if (current == null) current = BigDecimal.ZERO;
        BigDecimal next = current.subtract(amount).setScale(2, RoundingMode.HALF_EVEN);
        existing.setProperty("amount_total", next.toPlainString());
        Date now = new Date();
        existing.setProperty("expensed_at", iso(now));
        existing.setProperty("dateModified", iso(now));
        if (next.signum() <= 0) {
            existing.setProperty("status", "closed");
            existing.setProperty("closed_at", iso(now));
        } else {
            existing.setProperty("status", "partially_expensed");
        }
        FormRowSet save = new FormRowSet();
        save.add(existing);
        global.govstack.regbb.engine.support.RowWriter.save(FORM_BNF_SUBLEDGER, FORM_BNF_SUBLEDGER, save);
        return code;
    }

    /** ADR-030 Step 5: callable by BudgetProjectionRefreshJob (scheduled
     *  every 30s) and by per-render refresh in the operator decision
     *  form's budget-hint widget. No longer called from dispatch /
     *  dispatchDirect. */
    public void refreshProjection() {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection c = ds.getConnection();
             // CONCURRENTLY: writers blocking on each other was the dominant
             // cost in the L4 parity run — sequential dispatches each held a
             // long ExclusiveLock during refresh. CONCURRENTLY uses a snapshot
             // and only takes a brief lock at swap time, so concurrent
             // dispatches no longer queue. Requires the unique index
             // idx_budget_projection_envelope (already created in
             // 001_budget_projection_view.sql).
             PreparedStatement p1 = c.prepareStatement("REFRESH MATERIALIZED VIEW CONCURRENTLY budget_projection");
             PreparedStatement p2 = c.prepareStatement("REFRESH MATERIALIZED VIEW CONCURRENTLY budget_projection_by_source")) {
            p1.execute();
            p2.execute();
        } catch (SQLException sqle) {
            LogUtil.warn(CLASS_NAME, "projection refresh failed: " + sqle.getMessage());
        }
    }

    // -----------------------------------------------------------------
    //  Tiny helpers — kept private to this class to avoid sprawl
    // -----------------------------------------------------------------

    private DispatchResult error(DispatchRequest req, String cause, long started) {
        return new DispatchResult("error", null, req.envelopeCode, null, null,
                new ArrayList<>(), null, cause, System.currentTimeMillis() - started);
    }

    private static JournalLine line(String path, String dir, BigDecimal amt) {
        return new JournalLine(path, dir, amt.setScale(2, RoundingMode.HALF_EVEN));
    }

    private static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.getProperty(key);
        if (v == null) v = row.getProperty(key.toLowerCase());
        return v == null ? null : v.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException nfe) { return null; }
    }

    private static String iso(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(d);
    }

    /** Tiny field extractor — handles flat top-level JSON; the action's
     *  triggerJson is small and authored by analysts, so a regex-style
     *  read is enough. Returns null if the key is absent or value is empty. */
    private static String jsonValueOf(String json, String key) {
        if (json == null) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        // Skip whitespace
        int p = colon + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        if (p >= json.length()) return null;
        char first = json.charAt(p);
        if (first == '"') {
            int end = json.indexOf('"', p + 1);
            if (end < 0) return null;
            return json.substring(p + 1, end);
        }
        // Numeric/bool — read until comma, brace, whitespace
        int end = p;
        while (end < json.length()) {
            char ch = json.charAt(end);
            if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) break;
            end++;
        }
        return json.substring(p, end).trim();
    }

    private static String jsonEncode(Map<String, BigDecimal> m) {
        if (m == null || m.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> e : m.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":")
              .append(e.getValue().toPlainString());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }
}
