package global.govstack.formcreator.service;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Idempotent fixture seeding for {@code mm_*} test data — one HTTP call wipes
 * a chosen set of tables and another bulk-upserts rows from a JSON spec
 * keyed by a business {@code code} column. Lets the analyst rebuild the
 * canonical demo fixture in seconds instead of clicking through the CRUD UIs.
 *
 * <p>Compliant with the project's HARD RULE — no raw SQL on {@code app_fd_*}
 * tables. All reads/writes/deletes go through Joget's {@link FormDataDao}.
 *
 * <p>Two operations:
 * <ul>
 *   <li>{@link #seed(String, JSONArray)} — for each fixture entry (formId +
 *       businessKey + rows), upsert each row by looking up an existing one
 *       whose {@code businessKey} value matches; if found, update in place
 *       (preserving Joget's UUID id); if not, insert with a new UUID.</li>
 *   <li>{@link #clear(String, JSONArray)} — for each formId in order, delete
 *       all rows via FormDataDao. Caller is responsible for ordering child
 *       tables before parent tables to respect FK references.</li>
 * </ul>
 */
public class FixtureSeedService {

    private static final String CLASS_NAME = FixtureSeedService.class.getName();

    private final FormDataDao dao;
    private final AppService  appService;

    public FixtureSeedService() {
        this.dao        = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        this.appService = (AppService)  AppUtil.getApplicationContext().getBean("appService");
    }

    public FixtureSeedService(FormDataDao dao, AppService appService) {
        this.dao        = dao;
        this.appService = appService;
    }

    // ---------------------------------------------------------------------
    //  SEED — upsert keyed by business code
    // ---------------------------------------------------------------------

    /**
     * Seed the supplied fixtures into the named app. Each fixture entry is a
     * JSON object of the shape
     * <pre>
     *   { "formId": "mm_service", "businessKey": "code",
     *     "rows": [ { "code": "INPUT_SUBSIDY_001", "name": "...", ... }, ... ] }
     * </pre>
     * Returns a JSONArray of per-fixture result objects:
     * {@code {formId, inserted, updated, errors:[...]}}
     */
    public JSONArray seed(String appId, JSONArray fixtures) {
        JSONArray results = new JSONArray();
        AppDefinition appDef = resolveAppDef(appId);
        if (appDef == null) {
            JSONObject err = new JSONObject();
            err.put("error", "appDefinition not found for appId=" + appId);
            results.put(err);
            return results;
        }

        for (int i = 0; i < fixtures.length(); i++) {
            JSONObject fx = fixtures.getJSONObject(i);
            String formId      = fx.optString("formId");
            String businessKey = fx.optString("businessKey", "code");
            JSONArray rows     = fx.optJSONArray("rows");
            if (rows == null) rows = new JSONArray();

            JSONObject res = new JSONObject();
            res.put("formId", formId);
            int inserted = 0, updated = 0;
            JSONArray errors = new JSONArray();

            try {
                String tableName = resolveTableName(appDef, formId);
                if (tableName == null) {
                    res.put("error", "form not found: " + formId);
                    res.put("inserted", 0);
                    res.put("updated",  0);
                    res.put("errors",   errors);
                    results.put(res);
                    continue;
                }

                for (int r = 0; r < rows.length(); r++) {
                    JSONObject row = rows.getJSONObject(r);
                    String keyValue = row.optString(businessKey, "");
                    try {
                        boolean wasUpdate = upsertRow(formId, tableName, businessKey, keyValue, row);
                        if (wasUpdate) updated++; else inserted++;
                    } catch (Exception rowError) {
                        JSONObject e = new JSONObject();
                        e.put(businessKey, keyValue);
                        e.put("message",   rowError.getMessage());
                        errors.put(e);
                        LogUtil.error(CLASS_NAME, rowError,
                                "seed row error in " + formId + " for " + businessKey + "=" + keyValue);
                    }
                }
            } catch (Exception fixtureError) {
                LogUtil.error(CLASS_NAME, fixtureError, "seed fixture-level error in " + formId);
                JSONObject e = new JSONObject();
                e.put("message", fixtureError.getMessage());
                errors.put(e);
            }

            res.put("inserted", inserted);
            res.put("updated",  updated);
            res.put("errors",   errors);
            results.put(res);
        }
        return results;
    }

    private boolean upsertRow(String formId, String tableName,
                              String businessKey, String keyValue, JSONObject row) {
        FormRow existing = findByBusinessKey(formId, tableName, businessKey, keyValue);
        FormRow target = (existing != null) ? existing : new FormRow();

        // Copy all columns from the JSON row into the FormRow (Joget map).
        Iterator<String> keys = row.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object v = row.get(k);
            target.setProperty(k, v == null ? "" : v.toString());
        }

        if (existing == null) {
            // Fresh row — give Joget an explicit UUID so we can audit it later.
            target.setId(UuidGenerator.getInstance().getUuid());
        }

        FormRowSet rs = new FormRowSet();
        rs.add(target);
        // Task #235: route through AppService.storeFormData so dateCreated /
        // dateModified / createdBy / modifiedBy get populated. Direct
        // dao.saveOrUpdate left every seeded row's timestamp NULL, which
        // broke ordering on cross-table audit queries that wanted system
        // timestamps as the tie-breaker. AppService is the same path
        // Joget's WorkflowFormBinder uses internally.
        appService.storeFormData(formId, tableName, rs, null);
        return existing != null;
    }

    private FormRow findByBusinessKey(String formId, String tableName,
                                      String businessKey, String keyValue) {
        if (keyValue == null || keyValue.isEmpty()) return null;
        // Joget HQL-ish: column lookup on customProperties.<key>
        FormRowSet rows = dao.find(formId, tableName,
                "WHERE e.customProperties." + businessKey + " = ?",
                new Object[] { keyValue }, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0);
    }

    // ---------------------------------------------------------------------
    //  CLEAR — delete all rows from named tables
    // ---------------------------------------------------------------------

    /**
     * Wipe all rows from the listed forms. Caller orders child tables before
     * parent tables so FK references aren't broken mid-clear.
     */
    public JSONArray clear(String appId, JSONArray formIds) {
        JSONArray results = new JSONArray();
        AppDefinition appDef = resolveAppDef(appId);
        if (appDef == null) {
            JSONObject err = new JSONObject();
            err.put("error", "appDefinition not found for appId=" + appId);
            results.put(err);
            return results;
        }

        for (int i = 0; i < formIds.length(); i++) {
            String formId = formIds.getString(i);
            JSONObject res = new JSONObject();
            res.put("formId", formId);
            try {
                String tableName = resolveTableName(appDef, formId);
                if (tableName == null) {
                    res.put("error", "form not found");
                    res.put("deleted", 0);
                    results.put(res);
                    continue;
                }
                FormRowSet rows = dao.find(formId, tableName, null, null, null, false, null, null);
                int n = (rows == null) ? 0 : rows.size();
                if (n > 0) {
                    List<String> ids = new ArrayList<>(n);
                    for (FormRow r : rows) {
                        if (r.getId() != null) ids.add(r.getId());
                    }
                    if (!ids.isEmpty()) {
                        dao.delete(formId, tableName, ids.toArray(new String[0]));
                    }
                }
                res.put("deleted", n);
            } catch (Exception ex) {
                LogUtil.error(CLASS_NAME, ex, "clear failed for " + formId);
                res.put("error", ex.getMessage());
                res.put("deleted", 0);
            }
            results.put(res);
        }
        return results;
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    private AppDefinition resolveAppDef(String appId) {
        if (appId == null || appId.isEmpty()) {
            return AppUtil.getCurrentAppDefinition();
        }
        // Latest published version, falling back to latest version overall.
        Long published = appService.getPublishedVersion(appId);
        if (published != null) {
            return appService.getAppDefinition(appId, String.valueOf(published));
        }
        return appService.getAppDefinition(appId, null);
    }

    private String resolveTableName(AppDefinition appDef, String formId) {
        try {
            Form form = appService.viewDataForm(
                    appDef.getAppId(), appDef.getVersion().toString(), formId,
                    null, null, null, null, null, null);
            if (form == null) return null;
            return form.getPropertyString("tableName");
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "resolveTableName failed for " + formId + ": " + e.getMessage());
            return null;
        }
    }
}
