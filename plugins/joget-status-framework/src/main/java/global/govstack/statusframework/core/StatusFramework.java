package global.govstack.statusframework.core;

import global.govstack.statusframework.api.EntityType;
import global.govstack.statusframework.api.InvalidTransitionException;
import global.govstack.statusframework.api.Status;
import global.govstack.statusframework.api.TransitionAuditEntry;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic status-lifecycle framework. The single source of truth for
 * "can this status transition happen?" and "execute this transition with
 * audit trail." Modelled after the GAM-framework pattern, made generic.
 * <p>
 * <b>Usage:</b> consuming plugins register their transition map at OSGi
 * bundle start time (typically in {@code Activator.start()}):
 * <pre>{@code
 * Map<Status, Set<Status>> tx = new LinkedHashMap<>();
 * tx.put(BankStatus.NEW,       Set.of(BankStatus.IMPORTING));
 * tx.put(BankStatus.IMPORTING, Set.of(BankStatus.IMPORTED, BankStatus.ERROR));
 * StatusFramework.register(BankEntityType.STATEMENT, tx, Set.of(BankStatus.NEW));
 * }</pre>
 * <p>
 * Then call:
 * <pre>{@code
 * StatusFramework.transition(dao, BankEntityType.STATEMENT, recordId,
 *         BankStatus.IMPORTING, "statement-importer", "CSV upload started");
 * }</pre>
 * <p>
 * Two side effects per call:
 * <ol>
 *   <li>The entity row's {@code status} field is updated.</li>
 *   <li>One audit row is written to {@code app_fd_audit_log}.</li>
 * </ol>
 * <p>
 * <b>Thread-safety:</b> registration is backed by {@link ConcurrentHashMap};
 * transition execution is stateless on the framework side (DAO is the user's
 * concern). Safe to call from multiple bundle Activators concurrently.
 * <p>
 * <b>Reflection of the framework's contract:</b>
 * <ul>
 *   <li>{@link #register} — declarative registration of transitions per entity</li>
 *   <li>{@link #canTransition} — pure validation, no DB access</li>
 *   <li>{@link #transition(FormDataDao, EntityType, String, Status, String, String)}
 *       — executes a transition with audit trail using {@link EntityType#getTableName()}
 *       as the target table</li>
 *   <li>{@link #transition(FormDataDao, String, EntityType, String, Status, String, String)}
 *       — same as above but with an explicit table name (when the actual table differs
 *       from the entity's default)</li>
 *   <li>{@link #fromCode} — code → Status lookup using the registered Status pool</li>
 * </ul>
 */
public final class StatusFramework {

    private static final String CLASS_NAME = StatusFramework.class.getName();
    private static final String AUDIT_TABLE = "audit_log";

    private static final ConcurrentHashMap<EntityType, Map<Status, Set<Status>>> TRANSITIONS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<EntityType, Set<Status>> INITIAL_STATUSES =
            new ConcurrentHashMap<>();
    /**
     * Code → Status index. Built up incrementally from each {@link #register}
     * call; populated with every Status that appears in a registered map.
     * Codes are stored case-insensitive (lower-cased on insert and lookup).
     */
    private static final ConcurrentHashMap<String, Status> CODE_INDEX = new ConcurrentHashMap<>();

    private StatusFramework() {
        // static-only
    }

    // ──────────────────────────────────────────────────────────────────
    //  Registration
    // ──────────────────────────────────────────────────────────────────

    /**
     * Register the transition map and initial statuses for an entity type.
     * <p>
     * The provided maps are defensively copied and stored as immutable views.
     * Re-registering an entity type replaces its previous registration (last-write-wins).
     *
     * @param entityType        non-null entity type
     * @param transitions       map of {fromStatus → set of allowed toStatuses}.
     *                          Terminal statuses map to an empty set.
     * @param initialStatuses   set of statuses an entity may be created with
     *                          when {@code currentStatus == null}
     * @throws NullPointerException if any argument is null
     */
    public static void register(EntityType entityType,
                                Map<Status, Set<Status>> transitions,
                                Set<Status> initialStatuses) {
        Objects.requireNonNull(entityType,      "entityType");
        Objects.requireNonNull(transitions,     "transitions");
        Objects.requireNonNull(initialStatuses, "initialStatuses");

        // Defensive immutable copies
        Map<Status, Set<Status>> txCopy = new LinkedHashMap<>();
        for (Map.Entry<Status, Set<Status>> e : transitions.entrySet()) {
            Status from = Objects.requireNonNull(e.getKey(),   "from-status");
            Set<Status> targets = e.getValue() != null
                    ? Collections.unmodifiableSet(new LinkedHashSet<>(e.getValue()))
                    : Collections.emptySet();
            txCopy.put(from, targets);
            CODE_INDEX.put(from.getCode().toLowerCase(Locale.ROOT), from);
            for (Status t : targets) CODE_INDEX.put(t.getCode().toLowerCase(Locale.ROOT), t);
        }
        TRANSITIONS.put(entityType, Collections.unmodifiableMap(txCopy));

        Set<Status> initCopy = Collections.unmodifiableSet(new LinkedHashSet<>(initialStatuses));
        for (Status s : initCopy) CODE_INDEX.put(s.getCode().toLowerCase(Locale.ROOT), s);
        INITIAL_STATUSES.put(entityType, initCopy);

        LogUtil.info(CLASS_NAME, "Registered " + entityType.name()
                + " (transitions: " + transitions.size()
                + ", initial: " + initialStatuses.size() + ")");
    }

    /**
     * Look up a Status by its DB code (case-insensitive). Searches the entire
     * registry; returns {@code null} if no match.
     */
    public static Status fromCode(String code) {
        if (code == null || code.isEmpty()) return null;
        return CODE_INDEX.get(code.toLowerCase(Locale.ROOT));
    }

    // ──────────────────────────────────────────────────────────────────
    //  Validation
    // ──────────────────────────────────────────────────────────────────

    /**
     * Pure validation, no DB access. {@code true} when the transition is
     * allowed by the registered transition map.
     * <p>
     * If {@code currentStatus} is {@code null} (record without status yet),
     * only {@link #isInitialStatus initial statuses} are allowed.
     */
    public static boolean canTransition(EntityType entityType, Status currentStatus,
                                        Status targetStatus) {
        if (entityType == null || targetStatus == null) return false;
        Map<Status, Set<Status>> tx = TRANSITIONS.get(entityType);
        if (tx == null) return false;
        if (currentStatus == null) {
            return isInitialStatus(entityType, targetStatus);
        }
        Set<Status> targets = tx.get(currentStatus);
        return targets != null && targets.contains(targetStatus);
    }

    /**
     * Returns the set of valid target statuses for the given entity and
     * current status. Empty set if the current status is terminal or the
     * entity/status combination is not found.
     */
    public static Set<Status> getValidTransitions(EntityType entityType, Status currentStatus) {
        if (entityType == null || currentStatus == null) return Collections.emptySet();
        Map<Status, Set<Status>> tx = TRANSITIONS.get(entityType);
        if (tx == null) return Collections.emptySet();
        Set<Status> targets = tx.get(currentStatus);
        return targets != null ? targets : Collections.emptySet();
    }

    /**
     * @return true if {@code targetStatus} is a registered initial status
     *         for {@code entityType}.
     */
    public static boolean isInitialStatus(EntityType entityType, Status targetStatus) {
        if (entityType == null || targetStatus == null) return false;
        Set<Status> initial = INITIAL_STATUSES.get(entityType);
        return initial != null && initial.contains(targetStatus);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Execution
    // ──────────────────────────────────────────────────────────────────

    /**
     * Transition an entity using the entity's default table name from
     * {@link EntityType#getTableName()}. Validates, writes the new status,
     * and writes one audit row to {@code app_fd_audit_log}.
     */
    public static void transition(FormDataDao dao, EntityType entityType, String recordId,
                                  Status targetStatus, String triggeredBy, String reason)
            throws InvalidTransitionException {
        executeTransition(dao, entityType.getTableName(), entityType, recordId,
                targetStatus, triggeredBy, reason);
    }

    /**
     * Transition an entity using an explicit table name. Use when the actual
     * Joget form table name differs from {@link EntityType#getTableName()}
     * (e.g. workspace-specific tables).
     */
    public static void transition(FormDataDao dao, String tableName, EntityType entityType,
                                  String recordId, Status targetStatus,
                                  String triggeredBy, String reason)
            throws InvalidTransitionException {
        executeTransition(dao, tableName, entityType, recordId,
                targetStatus, triggeredBy, reason);
    }

    private static void executeTransition(FormDataDao dao, String tableName,
                                          EntityType entityType, String recordId,
                                          Status targetStatus, String triggeredBy,
                                          String reason)
            throws InvalidTransitionException {

        // 1. Load current record
        FormRow row = dao.load(null, tableName, recordId);
        if (row == null) {
            throw new IllegalStateException(
                    "Record not found: " + entityType.name() + " / " + recordId);
        }

        // 2. Read current status
        String currentCode = row.getProperty("status");
        Status currentStatus = null;
        if (currentCode != null && !currentCode.isEmpty()) {
            currentStatus = fromCode(currentCode);
            if (currentStatus == null) {
                throw new IllegalStateException(
                        "Unrecognized status code in database: '" + currentCode
                                + "' for " + entityType.name() + " / " + recordId);
            }
        }

        // 3. Validate
        if (!canTransition(entityType, currentStatus, targetStatus)) {
            throw new InvalidTransitionException(entityType, recordId,
                    currentStatus, targetStatus);
        }

        // 4. Write new status to entity row
        row.setProperty("status", targetStatus.getCode());
        FormRowSet rowSet = new FormRowSet();
        rowSet.add(row);
        dao.saveOrUpdate(null, tableName, rowSet);

        // 5. Write audit row
        TransitionAuditEntry audit = new TransitionAuditEntry(
                entityType, recordId, currentStatus, targetStatus, triggeredBy, reason);
        FormRowSet auditRowSet = new FormRowSet();
        auditRowSet.add(audit.toFormRow());
        dao.saveOrUpdate(AUDIT_TABLE, AUDIT_TABLE, auditRowSet);

        // 6. Log
        String fromCodeStr = currentStatus != null ? currentStatus.getCode() : "null";
        LogUtil.info(CLASS_NAME, "Status transition: " + entityType.name()
                + " " + recordId + " " + fromCodeStr + " → " + targetStatus.getCode());
    }

    // ──────────────────────────────────────────────────────────────────
    //  Convenience
    // ──────────────────────────────────────────────────────────────────

    /**
     * Convenience for callers that don't already hold a {@link FormDataDao}.
     * Retrieves it from the Joget Spring application context.
     */
    public static FormDataDao getFormDataDao() {
        return (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Package-private — for testing only
    // ──────────────────────────────────────────────────────────────────

    /** Test-only: clears all registrations. Never call from production code. */
    static void clearRegistry() {
        TRANSITIONS.clear();
        INITIAL_STATUSES.clear();
        CODE_INDEX.clear();
    }

    /** Test-only: read-only snapshot of the transition map. */
    static Map<EntityType, Map<Status, Set<Status>>> getTransitionsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(TRANSITIONS));
    }

    /** Test-only: read-only snapshot of the initial-status map. */
    static Map<EntityType, Set<Status>> getInitialStatusesSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(INITIAL_STATUSES));
    }

    /** Test-only: read-only snapshot of the code index. */
    static Map<String, Status> getCodeIndexSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(CODE_INDEX));
    }
}
