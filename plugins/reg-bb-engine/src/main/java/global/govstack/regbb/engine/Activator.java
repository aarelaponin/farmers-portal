package global.govstack.regbb.engine;

import global.govstack.regbb.engine.api.BudgetApi;
import global.govstack.regbb.engine.api.RegBbEvalApi;
import global.govstack.regbb.engine.budget.BudgetHintFormatter;
import global.govstack.regbb.engine.binder.RegBbApplicationStoreBinder;
import global.govstack.regbb.engine.binder.RegBbOperatorDecisionBinder;
import global.govstack.regbb.engine.element.MetaScreenElement;
import global.govstack.regbb.engine.element.MetaWizardElement;
import global.govstack.regbb.engine.workflow.RegBbWorkflowEchoTool;

import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for the reg-bb-engine plugin.
 *
 * <p>Phase 1 Week 1 scaffolding: registers no Joget plugins yet — just logs
 * the build stamp at startup so the live JAR's identity is visible. The
 * {@code DeterminantEvaluator} API surface is defined under
 * {@code global.govstack.regbb.engine.api} for downstream callers; the
 * fast-path evaluator implementation lands Week 3 and is registered here.
 *
 * <p>Phase 1 milestones for this Activator:
 * <ul>
 *   <li>Week 1: scaffolding (this) — log only.
 *   <li>Week 2: register {@code MetaScreenElement} (form element).
 *   <li>Week 3: register fast-path {@code DeterminantEvaluator}.
 *   <li>Week 4: register SQL-path bridge to {@code joget-rules-api}'s compiler.
 *   <li>Week 6–8: register userview-generation hook helpers (per D8).
 * </ul>
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        LogUtil.info(Activator.class.getName(),
                "reg-bb-engine starting — " + Build.STAMP);

        // Phase 1 Week 1: register MetaScreenElement so the bundle appears in
        // Manage Plugins with its build stamp visible. The element is a
        // scaffolding stub (returns a placeholder div); Week 2 fills in the
        // mm_screen + mm_field walker.
        registrationList.add(context.registerService(
                MetaScreenElement.class.getName(),
                new MetaScreenElement(),
                null));

        // D24: MetaWizardElement — renders an mm_screen sequence as tabs.
        // Phase 1 Week 3 deliverable; complements MetaScreenElement (single
        // screen) by giving form designers a one-widget wizard for whole
        // services. See _design/decision-log.md D24 and convergence-framework
        // §9.4.
        registrationList.add(context.registerService(
                MetaWizardElement.class.getName(),
                new MetaWizardElement(),
                null));

        // Slice 1A: register RegBbApplicationStoreBinder per ADR-005. The
        // citizen application form references this by className in its
        // storeBinder property; registering here makes the class visible to
        // Joget's plugin manager (used by the form-builder palette and by
        // typed lookups). Per ADR-007: two-transaction shape; never-null
        // outcome discipline.
        registrationList.add(context.registerService(
                RegBbApplicationStoreBinder.class.getName(),
                new RegBbApplicationStoreBinder(),
                null));

        // §6.5 + §8 REST endpoints. Lives in this bundle so it can call
        // RoutingEvaluator directly (no cross-bundle reflection — see
        // CLAUDE.md "Cross-bundle reflection: pin the target's classloader
        // explicitly" for why we abandoned the form-creator-api approach).
        registrationList.add(context.registerService(
                RegBbEvalApi.class.getName(),
                new RegBbEvalApi(),
                null));

        // L3-1 1B-i — Budget Engine dispatch endpoint.
        registrationList.add(context.registerService(
                BudgetApi.class.getName(),
                new BudgetApi(),
                null));

        // Session-authenticated voucher redemption endpoint (no API key).
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.api.BudgetRedeemWeb.class.getName(),
                new global.govstack.regbb.engine.api.BudgetRedeemWeb(),
                null));

        // L3-1 3a — Budget hint column formatter for the operator inbox.
        registrationList.add(context.registerService(
                BudgetHintFormatter.class.getName(),
                new BudgetHintFormatter(),
                null));

        // Phase 2-a operator decision binder — registered as the storeBinder
        // on subsidyApplicationOperator2025. Transitions application status
        // when an operator saves a decision.
        registrationList.add(context.registerService(
                RegBbOperatorDecisionBinder.class.getName(),
                new RegBbOperatorDecisionBinder(),
                null));

        // Phase 2-b: diagnostic process-tool plugin. Sysadmins drop this
        // into any workflow tool step to verify the engine→workflow handoff
        // works (logs context to reg_bb_eval_audit).
        registrationList.add(context.registerService(
                RegBbWorkflowEchoTool.class.getName(),
                new RegBbWorkflowEchoTool(),
                null));

        // L3-1 maker-checker — storeBinder on budget_adjustment_request.
        // Enforces maker ≠ checker on approval, dispatches BudgetEngine on
        // approved status with idempotency. See class javadoc for details.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.binder.BudgetAdjustmentBinder.class.getName(),
                new global.govstack.regbb.engine.binder.BudgetAdjustmentBinder(),
                null));

        // L3-1 threshold automation — process tool plugin scanning every
        // envelope for utilisation crossings (80%/100%/110%). At 110% it
        // auto-flips status='frozen' on the envelope; BudgetEngine.dispatch
        // rejects forward-funnel events on frozen envelopes thereafter.
        // Sysadmins wire this into a scheduled workflow process step.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.budget.BudgetThresholdMonitor.class.getName(),
                new global.govstack.regbb.engine.budget.BudgetThresholdMonitor(),
                null));

        // ADR-030 Step 3 — async eligibility worker. Drains processing_queue
        // rows, runs the eligibility chain (which includes budget dispatch)
        // off the request thread. Wire as a tool step in a Joget workflow
        // process scheduled every 30s, or invoke on demand.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.processing.EligibilityProcessingWorker.class.getName(),
                new global.govstack.regbb.engine.processing.EligibilityProcessingWorker(),
                null));

        // ADR-030 Step 5 — projection refresh job. Schedule every 30s.
        // BudgetEngine.dispatch no longer refreshes inline; this is the
        // single point of refresh.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.budget.BudgetProjectionRefreshJob.class.getName(),
                new global.govstack.regbb.engine.budget.BudgetProjectionRefreshJob(),
                null));

        // IM Phase 3 Slice 4 — voucher issuance tool. Process tool plugin;
        // BudgetApi calls it directly via /budget/issue-vouchers, but it's
        // registered here too so the worker hook can discover it via
        // PluginManager and so it appears in the plugin list.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.processing.VoucherIssuanceTool.class.getName(),
                new global.govstack.regbb.engine.processing.VoucherIssuanceTool(),
                null));

        // IM Phase 3 Slice 5 — voucher redemption tool. Same pattern.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.processing.VoucherRedemptionTool.class.getName(),
                new global.govstack.regbb.engine.processing.VoucherRedemptionTool(),
                null));

        // IM Phase 3 Slice 6a — StockTransactionStoreBinder retired in build-108
        // (consolidation pass). The class file was deleted: zero forms in the
        // live JWA referenced it, zero code outside its own file referenced it,
        // and Slice 6d's StockTransactionPostProcessor below has been the
        // working replacement since build-104. See decision-log.md D37 for the
        // refactor rationale; see CLAUDE.md "Form post-processor vs. storeBinder"
        // for when each pattern applies.

        // IM Phase 3 Slice 6d — stock-transaction POST-processor. Wired on
        // im_stock_transaction's postProcessor property. With Slice 6d the
        // line items moved off the parent into the im_stock_txn_line child
        // grid, which means inventory deltas can no longer be applied inside
        // the parent's storeBinder (Joget walks FormGrid storeBinders AFTER
        // the parent binder). Post-processors run once after the whole form
        // tree has committed — both header + line rows are on disk by then.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.processing.StockTransactionPostProcessor.class.getName(),
                new global.govstack.regbb.engine.processing.StockTransactionPostProcessor(),
                null));

        // IM Phase 3 Slice 9 — voucher expiry sweeper. DefaultApplicationPlugin
        // so it can be wired into a scheduled workflow process step (run daily
        // at 02:00 during the redemption season). Also reachable via the
        // /budget/expire-vouchers REST endpoint for ad-hoc operator triggers.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.processing.VoucherExpirySweeper.class.getName(),
                new global.govstack.regbb.engine.processing.VoucherExpirySweeper(),
                null));

        // IM Phase 3 Slice 10 — voucher cancellation tool. Operator-triggered
        // void of an issued voucher (applicant withdrew, fraud detected, etc.).
        // Releases the budget COMMITMENT and flips status to 'cancelled'.
        // Reachable via /budget/cancel-voucher REST endpoint or as a workflow
        // tool step.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.processing.VoucherCancellationTool.class.getName(),
                new global.govstack.regbb.engine.processing.VoucherCancellationTool(),
                null));

        // B2 — bulk operator decision DataListAction. Wired as a list-action
        // on the operator inbox datalist; lets operators check multiple
        // applications and approve/reject them in one click. Each row goes
        // through the same lifecycle the per-application decision form uses
        // (status patch + audit + budget event). Mirrors the logic in
        // RegBbOperatorDecisionBinder.transitionStatus().
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.binder.BulkOperatorDecisionAction.class.getName(),
                new global.govstack.regbb.engine.binder.BulkOperatorDecisionAction(),
                null));

        // W2 — auto-poll the eligibility worker every 60s so newly-submitted
        // applications get processed without an external trigger. Daemon
        // thread; clean shutdown via stop() below.
        global.govstack.regbb.engine.background.BackgroundWorkerScheduler.start();

        // W2.5 — notification queue worker: drains scheduled email/SMS
        // dispatches at scheduledFor time. Daemon thread; clean shutdown.
        global.govstack.regbb.engine.notification.NotificationQueueWorker.start();

        // W2.6 — register the notification lifecycle with the shared
        // joget-status-framework. Every email/SMS dispatch transitions
        // through this state machine; every flip writes one row to
        // app_fd_audit_log automatically. See decision-log D53.
        registerNotificationLifecycle();

        // W3.1 — register the application lifecycle (DRAFT → SUBMITTED →
        // UNDER_REVIEW → APPROVED/REJECTED/PENDING_REVIEW; WITHDRAWN as
        // citizen escape). Phase 1 only wires SUBMITTED → terminal; DRAFT
        // / UNDER_REVIEW / WITHDRAWN registered as future-ready states.
        // See decision-log D54.
        registerApplicationLifecycle();

        // W2.6 — register the operator-initiated retry + mark-dead-letter
        // datalist actions on the Notification Queue admin list.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.notification.RetryNotificationAction.class.getName(),
                new global.govstack.regbb.engine.notification.RetryNotificationAction(),
                null));
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.notification.MarkDeadLetterAction.class.getName(),
                new global.govstack.regbb.engine.notification.MarkDeadLetterAction(),
                null));

        // W3.2 — operator-side loadBinder that fires the SUBMITTED →
        // UNDER_REVIEW transition the first time an operator opens an
        // application for review. Wired to subsidyApplicationOperator2025's
        // loadBinder slot via patch_app_open_loadbinder.py.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.binder.ApplicationOpenLoadBinder.class.getName(),
                new global.govstack.regbb.engine.binder.ApplicationOpenLoadBinder(),
                null));

        // W3.3 — citizen-side withdraw action on list_my_applications.
        // Transitions any non-terminal lifecycle state → WITHDRAWN.
        registrationList.add(context.registerService(
                global.govstack.regbb.engine.lifecycle.WithdrawApplicationAction.class.getName(),
                new global.govstack.regbb.engine.lifecycle.WithdrawApplicationAction(),
                null));

        LogUtil.info(Activator.class.getName(),
                "reg-bb-engine registered: kernel + RegBB + Slice4-5-6-9-10 + auto-scheduler + notif-queue + state-machine + app-lifecycle — " + Build.STAMP);
    }

    /**
     * Wire the application lifecycle into joget-status-framework. Idempotent —
     * register() with the same EntityType replaces the previous registration.
     *
     * <p>State diagram (see {@link global.govstack.regbb.engine.lifecycle.AppLifecycleStatus}):
     * <pre>
     *   (new) ──► DRAFT      ──► SUBMITTED ──► UNDER_REVIEW ──► APPROVED      (terminal)
     *                                                       ──► REJECTED      (terminal)
     *                                                       ──► PENDING_REVIEW
     *                              PENDING_REVIEW ──► UNDER_REVIEW (operator picks up)
     *               *         ──► WITHDRAWN  (terminal — citizen escape from any non-terminal state)
     * </pre>
     */
    private void registerApplicationLifecycle() {
        java.util.Map<global.govstack.statusframework.api.Status,
                      java.util.Set<global.govstack.statusframework.api.Status>> tx =
                new java.util.LinkedHashMap<>();

        // DRAFT → SUBMITTED or WITHDRAWN
        java.util.Set<global.govstack.statusframework.api.Status> fromDraft = new java.util.LinkedHashSet<>();
        fromDraft.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.SUBMITTED);
        fromDraft.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.WITHDRAWN);
        tx.put(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.DRAFT, fromDraft);

        // SUBMITTED → UNDER_REVIEW, APPROVED, REJECTED, PENDING_REVIEW, WITHDRAWN
        // (allow direct skip to APPROVED/REJECTED because today's
        // auto_approved / auto_rejected from EligibilityProcessingWorker
        // skips the UNDER_REVIEW phase entirely)
        java.util.Set<global.govstack.statusframework.api.Status> fromSubmitted = new java.util.LinkedHashSet<>();
        fromSubmitted.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.UNDER_REVIEW);
        fromSubmitted.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.APPROVED);
        fromSubmitted.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.REJECTED);
        fromSubmitted.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.PENDING_REVIEW);
        fromSubmitted.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.WITHDRAWN);
        tx.put(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.SUBMITTED, fromSubmitted);

        // UNDER_REVIEW → APPROVED, REJECTED, PENDING_REVIEW, WITHDRAWN
        java.util.Set<global.govstack.statusframework.api.Status> fromUnder = new java.util.LinkedHashSet<>();
        fromUnder.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.APPROVED);
        fromUnder.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.REJECTED);
        fromUnder.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.PENDING_REVIEW);
        fromUnder.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.WITHDRAWN);
        tx.put(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.UNDER_REVIEW, fromUnder);

        // PENDING_REVIEW → UNDER_REVIEW (operator picks back up), APPROVED, REJECTED, WITHDRAWN
        java.util.Set<global.govstack.statusframework.api.Status> fromPending = new java.util.LinkedHashSet<>();
        fromPending.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.UNDER_REVIEW);
        fromPending.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.APPROVED);
        fromPending.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.REJECTED);
        fromPending.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.WITHDRAWN);
        tx.put(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.PENDING_REVIEW, fromPending);

        // Terminal states
        tx.put(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.APPROVED,
                java.util.Collections.emptySet());
        tx.put(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.REJECTED,
                java.util.Collections.emptySet());
        tx.put(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.WITHDRAWN,
                java.util.Collections.emptySet());

        java.util.Set<global.govstack.statusframework.api.Status> initial = new java.util.LinkedHashSet<>();
        initial.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.DRAFT);
        initial.add(global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.SUBMITTED);

        global.govstack.statusframework.core.StatusFramework.register(
                global.govstack.regbb.engine.lifecycle.AppEntityType.APPLICATION,
                tx, initial);
    }

    /**
     * Wire the notification lifecycle into joget-status-framework. Idempotent —
     * register() with the same EntityType replaces the previous registration.
     *
     * <p>State diagram (see {@link global.govstack.regbb.engine.notification.NotifStatus}):
     * <pre>
     *   (new) ──► PENDING ──► SENT      (happy path, terminal)
     *               │
     *               ├──► SKIPPED        (terminal: no recipient, template off)
     *               │
     *               └──► FAILED ──► PENDING       (operator retry)
     *                       │
     *                       └──► DEAD_LETTER  (terminal: max retries)
     * </pre>
     */
    private void registerNotificationLifecycle() {
        java.util.Map<global.govstack.statusframework.api.Status,
                      java.util.Set<global.govstack.statusframework.api.Status>> tx =
                new java.util.LinkedHashMap<>();

        java.util.Set<global.govstack.statusframework.api.Status> fromPending = new java.util.LinkedHashSet<>();
        fromPending.add(global.govstack.regbb.engine.notification.NotifStatus.SENT);
        fromPending.add(global.govstack.regbb.engine.notification.NotifStatus.SKIPPED);
        fromPending.add(global.govstack.regbb.engine.notification.NotifStatus.FAILED);
        tx.put(global.govstack.regbb.engine.notification.NotifStatus.PENDING, fromPending);

        java.util.Set<global.govstack.statusframework.api.Status> fromFailed = new java.util.LinkedHashSet<>();
        fromFailed.add(global.govstack.regbb.engine.notification.NotifStatus.PENDING);
        fromFailed.add(global.govstack.regbb.engine.notification.NotifStatus.DEAD_LETTER);
        tx.put(global.govstack.regbb.engine.notification.NotifStatus.FAILED, fromFailed);

        // Terminal states — no outbound transitions.
        tx.put(global.govstack.regbb.engine.notification.NotifStatus.SENT,
                java.util.Collections.emptySet());
        tx.put(global.govstack.regbb.engine.notification.NotifStatus.SKIPPED,
                java.util.Collections.emptySet());
        tx.put(global.govstack.regbb.engine.notification.NotifStatus.DEAD_LETTER,
                java.util.Collections.emptySet());

        java.util.Set<global.govstack.statusframework.api.Status> initial = new java.util.LinkedHashSet<>();
        initial.add(global.govstack.regbb.engine.notification.NotifStatus.PENDING);

        global.govstack.statusframework.core.StatusFramework.register(
                global.govstack.regbb.engine.notification.NotifEntityType.NOTIFICATION,
                tx, initial);
    }

    @Override
    public void stop(BundleContext context) {
        // Stop background workers before unregistering services so poll loops
        // don't hit a half-stopped registry.
        global.govstack.regbb.engine.notification.NotificationQueueWorker.shutdown();
        global.govstack.regbb.engine.background.BackgroundWorkerScheduler.stop();

        if (registrationList != null) {
            for (ServiceRegistration registration : registrationList) {
                registration.unregister();
            }
        }
        LogUtil.info(Activator.class.getName(),
                "reg-bb-engine stopping — " + Build.STAMP);
    }
}
