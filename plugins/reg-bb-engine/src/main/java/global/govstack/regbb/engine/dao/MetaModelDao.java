package global.govstack.regbb.engine.dao;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code mm_*} meta-records via Joget's {@link FormDataDao} (per the
 * project's hard rule — no raw SQL on {@code app_fd_*} tables).
 *
 * <p>Phase 1 Week 2 scope: read {@code mm_screen} and {@code mm_field} rows
 * for the {@link global.govstack.regbb.engine.element.MetaScreenElement}
 * walker. Future weeks add reads for {@code mm_determinant},
 * {@code mm_required_doc}, {@code mm_benefit}, etc., as the engine surfaces
 * grow.
 */
public class MetaModelDao {

    private static final String CLASS_NAME = MetaModelDao.class.getName();

    private static final String FORM_MM_SCREEN       = "mm_screen";
    private static final String FORM_MM_FIELD        = "mm_field";
    private static final String FORM_MM_CATALOG      = "mm_catalog";
    private static final String FORM_MM_REQUIRED_DOC = "mm_required_doc";
    private static final String FORM_MM_SERVICE      = "mm_service";
    private static final String FORM_MM_ROLE_SCREEN  = "mm_role_screen";
    private static final String FORM_MM_REGISTRATION = "mm_registration";
    private static final String FORM_MM_DETERMINANT  = "mm_determinant";

    private final FormDataDao dao;

    // ---------------------------------------------------------------
    // L4-5 — per-render request cache.
    //
    // The kernel's cold-render path makes 70+ DAO round-trips for a
    // 6-tab × 8-field wizard: 1 listFieldsForScreen per tab + 1
    // findCatalogByCode per SelectBox/Radio/Checkbox + 1
    // findDeterminantByCode per visibility/required toggle + 1
    // listRegistrationsForService for the catalogue + 1
    // listRequiredDocsForService for the documents tab. Many of those
    // calls are duplicates within the same render (the same catalog
    // code referenced by multiple fields, the same determinant evaluated
    // for both visibility and required, etc.). 15-25s render times
    // observed in production; ~70% of that wall is small DAO round-trips.
    //
    // Strategy: a ThreadLocal request cache that wraps every "lookup by
    // key" DAO method. Cleared at the entry of MetaWizardElement /
    // MetaScreenElement renders. Within one render, the same key is
    // resolved at most once. Cache is empty for all subsequent renders
    // — we do NOT want cross-render caching at this layer because
    // mm_* rows can change between requests (analyst edit) and an L1
    // hit would serve stale data.
    //
    // Cleanup: callers pair beginRequest()/endRequest() with try/finally
    // so a thrown exception still clears the ThreadLocal (no leak across
    // recycled Tomcat threads).
    private static final ThreadLocal<Map<String, Object>> REQUEST_CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> REQUEST_DEPTH = ThreadLocal.withInitial(() -> 0);

    /** Open the per-request cache. Ref-counted so nested calls don't fight:
     *  the outer caller creates the cache; inner callers (e.g.
     *  MetaScreenElement rendering inside MetaWizardElement) increment the
     *  depth without touching the cache. Important because MetaWizardElement
     *  walks every tab in sequence, calling MetaScreenElement.renderTemplate
     *  per tab — if the inner render dropped the cache, every subsequent
     *  tab would re-fetch from cold and the cache would have done nothing. */
    public static void beginRequest() {
        int d = REQUEST_DEPTH.get();
        if (d == 0) REQUEST_CACHE.set(new HashMap<>());
        REQUEST_DEPTH.set(d + 1);
    }

    /** Close the per-request cache. Always paired with beginRequest() in
     *  a try/finally. Decrements depth; only the outer call (depth → 0)
     *  actually removes the ThreadLocal entry, so nested renders see one
     *  cache shared across the whole render tree. Robust to imbalance:
     *  if endRequest() is called without a matching begin, the depth
     *  bottoms out at 0 and the cache is unchanged (no-op). */
    public static void endRequest() {
        int d = REQUEST_DEPTH.get();
        if (d <= 1) {
            REQUEST_CACHE.remove();
            REQUEST_DEPTH.remove();
        } else {
            REQUEST_DEPTH.set(d - 1);
        }
    }

    /** Internal: lookup or compute. Returns cached value if present; else
     *  invokes the supplier, caches the result, returns. Null results
     *  are cached (we want to remember "no row matches" to avoid repeating
     *  a fruitless query within a render). */
    @SuppressWarnings("unchecked")
    private <T> T cached(String key, java.util.function.Supplier<T> supplier) {
        Map<String, Object> c = REQUEST_CACHE.get();
        if (c == null) {
            // No active request scope — fall through to direct call.
            return supplier.get();
        }
        Object v;
        if (c.containsKey(key)) {
            v = c.get(key);
        } else {
            v = supplier.get();
            c.put(key, v);
        }
        return (T) v;
    }

    public MetaModelDao() {
        this.dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
    }

    /** Constructor for tests / DI. */
    public MetaModelDao(FormDataDao dao) {
        this.dao = dao;
    }

    /** Look up a {@code mm_screen} row by its primary key (UUID). Returns null if not found. */
    public FormRow findScreenById(String screenId) {
        if (screenId == null || screenId.isEmpty()) return null;
        FormRowSet rows = dao.find(FORM_MM_SCREEN, FORM_MM_SCREEN,
                "WHERE e.id = ?", new Object[] { screenId }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * Look up a {@code mm_screen} row by its business {@code code}. Per D20,
     * cross-entity references in this app use code (not Joget's UUID id) as
     * the FK target; {@link global.govstack.regbb.engine.element.MetaScreenElement}
     * stores a code in its widget property and resolves it through this method.
     * Returns {@code null} if no row matches.
     */
    public FormRow findScreenByCode(String screenCode) {
        if (screenCode == null || screenCode.isEmpty()) return null;
        FormRowSet rows = dao.find(FORM_MM_SCREEN, FORM_MM_SCREEN,
                "WHERE e.customProperties.code = ?",
                new Object[] { screenCode }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * List all {@code mm_field} rows for a given screen, in {@code orderIndex}
     * order. Returns an empty list if none. The {@code screenId} argument is
     * the parent screen's Joget id (UUID) — not its code — because the
     * {@code mm_screen → mm_field} link is a Joget-internal FormGrid FK
     * managed by MultirowFormBinder writing parent.id (per D20).
     */
    public List<FormRow> listFieldsForScreen(String screenId) {
        if (screenId == null || screenId.isEmpty()) return Collections.emptyList();
        return cached("fields:" + screenId, () -> doListFieldsForScreen(screenId));
    }

    private List<FormRow> doListFieldsForScreen(String screenId) {
        FormRowSet rows = dao.find(FORM_MM_FIELD, FORM_MM_FIELD,
                "WHERE e.customProperties.screenId = ?",
                new Object[] { screenId },
                "orderIndex", false, null, null);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        // The dao returns FormRowSet which IS-A List<FormRow>; copy to a stable List.
        List<FormRow> out = new ArrayList<>(rows.size());
        for (FormRow r : rows) {
            try {
                out.add(r);
            } catch (RuntimeException e) {
                LogUtil.warn(CLASS_NAME,
                        "Skipping malformed mm_field row " + r.getId() + ": " + e.getMessage());
            }
        }
        return out;
    }

    /**
     * Look up a {@code mm_catalog} row by primary key (UUID). Returns {@code null}
     * if not found. Kept for completeness; production callers should prefer
     * {@link #findCatalogByCode} per D20 (cross-entity references use codes).
     */
    public FormRow findCatalogById(String catalogId) {
        if (catalogId == null || catalogId.isEmpty()) return null;
        FormRowSet rows = dao.find(FORM_MM_CATALOG, FORM_MM_CATALOG,
                "WHERE e.id = ?", new Object[] { catalogId }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * Look up a {@code mm_catalog} row by its business {@code code}. Per D20,
     * {@code mm_field.optionsCatalogId} stores the catalog's code (not UUID),
     * so the synthesiser uses this method when populating options for select /
     * radio / checkbox widgets.
     */
    public FormRow findCatalogByCode(String catalogCode) {
        if (catalogCode == null || catalogCode.isEmpty()) return null;
        return cached("catalog:" + catalogCode, () -> doFindCatalogByCode(catalogCode));
    }

    private FormRow doFindCatalogByCode(String catalogCode) {
        FormRowSet rows = dao.find(FORM_MM_CATALOG, FORM_MM_CATALOG,
                "WHERE e.customProperties.code = ?",
                new Object[] { catalogCode }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * List {@code mm_required_doc} rows scoped to the service the screen
     * belongs to. Used by the documents-screen renderer; future Determinant
     * filtering (per-registration scoping) refines this to the applicant's
     * applied registration only.
     */
    public List<FormRow> listRequiredDocsForService(String serviceCode) {
        if (serviceCode == null || serviceCode.isEmpty()) return Collections.emptyList();
        return cached("reqdocs:" + serviceCode, () -> doListRequiredDocsForService(serviceCode));
    }

    private List<FormRow> doListRequiredDocsForService(String serviceCode) {
        FormRowSet rows = dao.find(FORM_MM_REQUIRED_DOC, FORM_MM_REQUIRED_DOC,
                "WHERE e.customProperties.serviceId = ?",
                new Object[] { serviceCode },
                "registrationId", false, null, null);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(rows);
    }

    /**
     * Look up a {@code mm_service} row by its business {@code code}. Returns
     * {@code null} if not found.
     */
    public FormRow findServiceByCode(String serviceCode) {
        if (serviceCode == null || serviceCode.isEmpty()) return null;
        FormRowSet rows = dao.find(FORM_MM_SERVICE, FORM_MM_SERVICE,
                "WHERE e.customProperties.code = ?",
                new Object[] { serviceCode }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * Look up a {@code mm_role_screen} row by business {@code code}. Used by
     * {@link global.govstack.regbb.engine.element.MetaWizardElement} to
     * derive the screen list and per-tab read-only mask from the row's
     * {@code sectionsJson} property — fully metadata-driven operator wizards
     * per D24.
     */
    public FormRow findRoleScreenByCode(String roleScreenCode) {
        if (roleScreenCode == null || roleScreenCode.isEmpty()) return null;
        FormRowSet rows = dao.find(FORM_MM_ROLE_SCREEN, FORM_MM_ROLE_SCREEN,
                "WHERE e.customProperties.code = ?",
                new Object[] { roleScreenCode }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * Look up a {@code mm_registration} row by its business {@code code}. Per
     * D20, the application form's {@code applied_programme} column stores the
     * registration's code (not Joget's UUID id), so the eligibility binder
     * resolves it through this method.
     */
    public FormRow findRegistrationByCode(String registrationCode) {
        if (registrationCode == null || registrationCode.isEmpty()) return null;
        FormRowSet rows = dao.find(FORM_MM_REGISTRATION, FORM_MM_REGISTRATION,
                "WHERE e.customProperties.code = ?",
                new Object[] { registrationCode }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * List all {@code mm_registration} rows for the given service code,
     * sorted by {@code code} for deterministic order. Used by L2-2's
     * single-window catalogue page (RegBB §6.1.6) to render every programme
     * under a service with its applicability evaluated upfront.
     *
     * @param serviceCode the {@code mm_service.code} (not UUID, per D20)
     */
    public List<FormRow> listRegistrationsForService(String serviceCode) {
        if (serviceCode == null || serviceCode.isEmpty()) return Collections.emptyList();
        return cached("regs:" + serviceCode, () -> doListRegistrationsForService(serviceCode));
    }

    private List<FormRow> doListRegistrationsForService(String serviceCode) {
        FormRowSet rows = dao.find(FORM_MM_REGISTRATION, FORM_MM_REGISTRATION,
                "WHERE e.customProperties.serviceId = ?",
                new Object[] { serviceCode },
                "code", false, null, null);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(rows);
    }

    /**
     * Look up a {@code mm_determinant} row by its business {@code code}. Per
     * D20, {@code mm_registration.applicabilityDeterminantId} stores the
     * determinant's code (not UUID), so the binder resolves the rule through
     * this method.
     */
    public FormRow findDeterminantByCode(String determinantCode) {
        if (determinantCode == null || determinantCode.isEmpty()) return null;
        return cached("det:" + determinantCode, () -> doFindDeterminantByCode(determinantCode));
    }

    private FormRow doFindDeterminantByCode(String determinantCode) {
        FormRowSet rows = dao.find(FORM_MM_DETERMINANT, FORM_MM_DETERMINANT,
                "WHERE e.customProperties.code = ?",
                new Object[] { determinantCode }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * List all {@code mm_determinant} rows for a given registration code and
     * scope. Slice 1B-a uses this for multi-rule aggregation: the binder
     * walks every {@code scope=applicability} determinant on the applied
     * registration and aggregates the per-rule outcomes per
     * {@code mm_registration.evaluationStrategy} (D6).
     *
     * <p>Sorted by {@code code} for deterministic order — operators reading
     * the {@code rules[]} breakdown in {@code eligibility_outcome} get a
     * stable list instead of insertion-order chaos.
     *
     * @param registrationCode the {@code mm_registration.code} (not UUID, per D20)
     * @param scope            e.g. {@code "applicability"}, {@code "fee"},
     *                         {@code "required_doc"}; null returns rows for
     *                         the registration regardless of scope
     */
    public List<FormRow> listDeterminantsForRegistration(String registrationCode, String scope) {
        if (registrationCode == null || registrationCode.isEmpty()) return Collections.emptyList();
        FormRowSet rows;
        if (scope == null || scope.isEmpty()) {
            rows = dao.find(FORM_MM_DETERMINANT, FORM_MM_DETERMINANT,
                    "WHERE e.customProperties.registrationId = ?",
                    new Object[] { registrationCode },
                    "code", false, null, null);
        } else {
            rows = dao.find(FORM_MM_DETERMINANT, FORM_MM_DETERMINANT,
                    "WHERE e.customProperties.registrationId = ? AND e.customProperties.scope = ?",
                    new Object[] { registrationCode, scope },
                    "code", false, null, null);
        }
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(rows);
    }

    /**
     * List all {@code mm_determinant} rows in the given scope, optionally
     * filtered by service or registration. Used by the policy resolver
     * (ADR-027 / ADR-028) to find {@code initial_status_assignment} and
     * {@code decision_to_status} rules in priority order: programme-specific
     * → service-wide → global.
     *
     * @param scope            scope to filter on (required)
     * @param serviceCode      optional service filter (mm_service.code)
     * @param registrationCode optional programme filter (mm_registration.code)
     */
    public List<FormRow> listDeterminantsByScope(String scope, String serviceCode, String registrationCode) {
        if (scope == null || scope.isEmpty()) return Collections.emptyList();
        FormRowSet rows;
        if (registrationCode != null && !registrationCode.isEmpty()) {
            rows = dao.find(FORM_MM_DETERMINANT, FORM_MM_DETERMINANT,
                    "WHERE e.customProperties.scope = ? AND e.customProperties.registrationId = ?",
                    new Object[] { scope, registrationCode },
                    "code", false, null, null);
        } else if (serviceCode != null && !serviceCode.isEmpty()) {
            rows = dao.find(FORM_MM_DETERMINANT, FORM_MM_DETERMINANT,
                    "WHERE e.customProperties.scope = ? AND e.customProperties.serviceId = ? AND (e.customProperties.registrationId IS NULL OR e.customProperties.registrationId = '')",
                    new Object[] { scope, serviceCode },
                    "code", false, null, null);
        } else {
            rows = dao.find(FORM_MM_DETERMINANT, FORM_MM_DETERMINANT,
                    "WHERE e.customProperties.scope = ? AND (e.customProperties.serviceId IS NULL OR e.customProperties.serviceId = '') AND (e.customProperties.registrationId IS NULL OR e.customProperties.registrationId = '')",
                    new Object[] { scope },
                    "code", false, null, null);
        }
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(rows);
    }
}
