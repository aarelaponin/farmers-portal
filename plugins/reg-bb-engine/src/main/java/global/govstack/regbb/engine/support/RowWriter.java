package global.govstack.regbb.engine.support;

import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.Date;

/**
 * Centralised wrapper for form-data writes from plugin code.
 *
 * <p><b>Why this exists.</b> Joget's native save path
 * ({@code WorkflowFormBinder.store} → {@code AppService.storeFormData}
 * → {@code FormDataDao.saveOrUpdate}) populates row metadata —
 * {@code dateCreated}, {@code dateModified}, {@code createdBy},
 * {@code createdByName}, {@code modifiedBy}, {@code modifiedByName}
 * — before persisting. Plugin code that calls
 * {@code FormDataDao.saveOrUpdate} <i>directly</i> bypasses that step,
 * so the resulting rows have NULL timestamps and no actor attribution.
 *
 * <p>Verified May 2026 (task #235): of 284 {@code app_fd_*} tables
 * in this app, 23 had 100% NULL {@code datecreated}/{@code datemodified}
 * — every one of them written by plugin code via direct
 * {@code FormDataDao.saveOrUpdate}. The native Joget tables
 * (farmerbasicinfo, parcelregistration, farms_registry, household_members,
 * livestock_details) had 100% populated timestamps because they go
 * through {@code WorkflowFormBinder.store}.
 *
 * <p><b>The fix.</b> Use this helper instead of calling
 * {@code FormDataDao.saveOrUpdate} directly. Internally it routes the
 * write through {@code AppService.storeFormData} (the same canonical
 * path Joget uses for native form saves), which sets all the metadata
 * columns before delegating to the DAO. Source-verified against
 * {@code AppServiceImpl.storeFormData} ≈ line 2135–2209
 * ({@code wflow-core/.../service/AppServiceImpl.java}).
 *
 * <p><b>How to migrate a call site.</b> Replace
 * <pre>
 *   FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
 *   dao.saveOrUpdate(formDefId, tableName, rowSet);
 * </pre>
 * with
 * <pre>
 *   RowWriter.save(formDefId, tableName, rowSet);
 * </pre>
 * The wrapper takes the same arguments and returns the persisted row
 * set so the call site can chain on it if needed.
 *
 * <p><b>Per-CLAUDE.md HARD RULE.</b> This wrapper writes through Joget's
 * own DAO chain — exactly the path the rule mandates. It is NOT raw SQL
 * on {@code app_fd_*}.
 *
 * <p><b>Backfilling existing NULL rows.</b> Out of scope for this helper.
 * Existing NULL timestamps are historical and cannot be reconstructed
 * accurately; reports that need event ordering use business-date columns
 * ({@code c_issued_date}, {@code c_redemption_date}, etc.) as the
 * authoritative source. The helper fixes the forward-going problem.
 */
public final class RowWriter {

    private static final String CLASS = RowWriter.class.getName();

    private RowWriter() {
        // utility class
    }

    /**
     * Save (insert or update) a row set through Joget's canonical save
     * path, populating all metadata columns. Same semantics as
     * {@code FormDataDao.saveOrUpdate} from the caller's perspective.
     *
     * @param formDefId  the form definition id (e.g. {@code "im_voucher"})
     * @param tableName  the underlying table name without {@code app_fd_}
     *                   prefix (typically same as {@code formDefId})
     * @param rows       the rows to persist
     * @return the persisted row set (with metadata columns now populated)
     */
    public static FormRowSet save(String formDefId, String tableName, FormRowSet rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        try {
            AppService appService = (AppService) AppUtil
                .getApplicationContext().getBean("appService");
            // 4-arg overload — primaryKeyValue=null lets storeFormData pick
            // up the row's own id (or generate one if missing).
            return appService.storeFormData(formDefId, tableName, rows, null);
        } catch (Exception e) {
            // Fall back to direct DAO + manual timestamp population so a
            // bean-lookup failure or a missing app context doesn't lose
            // the write entirely. This mirrors AppServiceImpl's metadata
            // logic minus the user-attribution (no workflow user manager
            // available outside a request).
            LogUtil.warn(CLASS, "AppService.storeFormData failed for "
                + formDefId + "/" + tableName + ", falling back to "
                + "FormDataDao with manual timestamps: " + e.getMessage());
            return saveWithFallback(formDefId, tableName, rows);
        }
    }

    /**
     * Fallback path used when {@code AppService.storeFormData} is not
     * reachable (e.g. early bundle activation, or a test-time call
     * without a fully-wired Spring context). Sets {@code dateModified}
     * on every row and {@code dateCreated} on rows that don't already
     * have one, then delegates to {@code FormDataDao.saveOrUpdate}.
     */
    private static FormRowSet saveWithFallback(String formDefId,
                                               String tableName,
                                               FormRowSet rows) {
        Date now = new Date();
        for (FormRow row : rows) {
            row.setDateModified(now);
            if (row.getDateCreated() == null) {
                row.setDateCreated(now);
            }
        }
        org.joget.apps.form.dao.FormDataDao dao =
            (org.joget.apps.form.dao.FormDataDao)
                AppUtil.getApplicationContext().getBean("formDataDao");
        dao.saveOrUpdate(formDefId, tableName, rows);
        return rows;
    }
}
