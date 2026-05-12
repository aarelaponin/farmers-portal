package global.govstack.application.binder;

import global.govstack.application.Build;
import global.govstack.application.service.ProgrammeSpecReader;
import global.govstack.application.service.SeedingService;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.UUID;

/**
 * Wired as the form-level {@code loadBinder} on
 * {@code spApplicationEligibilityTab} and {@code spApplicationBenefitsTab}.
 *
 * <p>Why a load-side hook? Joget {@code MultiPagedForm}'s partial-store
 * skips {@code processFormSubmission} — and therefore the postProcessor —
 * for tabs the user hasn't visited yet. The result: when the user first
 * navigates to Tab 2, the tab record exists but no eligibility-check rows
 * have been seeded. We close the gap by running {@link SeedingService} at
 * the moment the tab is rendered: idempotent, runs on every visit, and the
 * {@code FormGrid}'s {@code MultirowFormBinder} loads the freshly-seeded
 * rows in the same render cycle.
 *
 * <p>Resolves the wrapper application id by walking up from whichever tab
 * is being loaded ({@code parent_id} → {@code sp_application.id}), then
 * reads {@code programme_id} either from the wrapper or from Tab 1
 * ({@code sp_application_applicnt}) — wherever the wizard happened to
 * persist it. If no programme has been picked, it bails quietly.
 */
public class SeedingTabLoadBinder extends WorkflowFormBinder {

    private static final String CLASS_NAME = SeedingTabLoadBinder.class.getName();

    private static final String F_APPLICATION   = "spApplication";
    private static final String T_APPLICATION   = "sp_application";
    private static final String F_APPLICANT_TAB = "spApplicationApplicant";
    private static final String T_APPLICANT_TAB = "sp_application_applicnt";
    private static final String F_ELIG_TAB      = "spApplicationEligibilityTab";
    private static final String T_ELIG_TAB      = "sp_application_elig_tab";
    private static final String F_BEN_TAB       = "spApplicationBenefitsTab";
    private static final String T_BEN_TAB       = "sp_application_ben_tab";
    private static final String F_DOC_TAB       = "spApplicationDocumentsTab";
    private static final String T_DOC_TAB       = "sp_application_doc_tab";

    @Override
    public String getName()        { return "Seeding Tab Load Binder"; }
    @Override
    public String getDescription() {
        return "Seeds eligibility / benefit child rows from the chosen "
             + "programme spec when the tab is loaded. ["
             + Build.STAMP + "]";
    }
    @Override
    public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override
    public String getLabel()       { return getName(); }
    @Override
    public String getClassName()   { return getClass().getName(); }
    @Override
    public String getPropertyOptions() { return "[]"; }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        // primaryKey here is the WRAPPER application id (MultiPagedForm
        // convention for tab subforms). We find or create the tab record
        // linked to it, run seeding, and return THAT record so Joget uses
        // the tab's id as the rendered form's primary key — which is what
        // FormGrid's MultirowFormBinder uses as its foreign-key value.
        try {
            FormRow tabRow = ensureSeededAndReturnTab(element, primaryKey);
            if (tabRow != null) {
                FormRowSet result = new FormRowSet();
                result.add(tabRow);
                return result;
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                    "Seed-on-load failed for primaryKey=" + primaryKey + ": " + t.getMessage());
        }
        // Fall back to default behaviour if anything went wrong.
        return super.load(element, primaryKey, formData);
    }

    /**
     * Find or create the tab record for the given wrapper, run seeding, and
     * return the loaded tab row so callers can hand it back to Joget as the
     * form's primary record.
     */
    private FormRow ensureSeededAndReturnTab(Element element, String wrapperId) {
        if (wrapperId == null || wrapperId.isEmpty()) {
            return null;
        }

        String formId = element != null ? element.getPropertyString("id") : "";
        LogUtil.info(CLASS_NAME, "[" + Build.STAMP + "] ensureSeeded form="
                + formId + " wrapperId=" + wrapperId);

        FormDataDao dao = (FormDataDao)
                AppUtil.getApplicationContext().getBean("formDataDao");

        // Identify which tab is being loaded.
        String tabType;       // "elig" / "ben" / "doc"
        String tabFormId;
        String tabTableName;
        if (F_ELIG_TAB.equals(formId)) {
            tabType = "elig"; tabFormId = F_ELIG_TAB; tabTableName = T_ELIG_TAB;
        } else if (F_BEN_TAB.equals(formId)) {
            tabType = "ben";  tabFormId = F_BEN_TAB;  tabTableName = T_BEN_TAB;
        } else if (F_DOC_TAB.equals(formId)) {
            tabType = "doc";  tabFormId = F_DOC_TAB;  tabTableName = T_DOC_TAB;
        } else {
            LogUtil.info(CLASS_NAME, "  form " + formId + " is not a seeded tab — skipping.");
            return null;
        }

        // Wrapper must exist for us to do anything useful.
        FormRow wrapper = dao.load(F_APPLICATION, T_APPLICATION, wrapperId);
        if (wrapper == null) {
            LogUtil.info(CLASS_NAME, "  wrapper " + wrapperId + " not found — skipping.");
            return null;
        }

        // programme_id: prefer wrapper, fall back to Tab 1.
        String programmeId = wrapper.getProperty("programme_id");
        if (programmeId == null || programmeId.isEmpty()) {
            try {
                String condition = "WHERE e.customProperties.parent_id = ?";
                Object[] args = new Object[] { wrapperId };
                FormRowSet rows = dao.find(F_APPLICANT_TAB, T_APPLICANT_TAB,
                        condition, args, null, null, 0, 1);
                if (rows != null && !rows.isEmpty()) {
                    programmeId = rows.get(0).getProperty("programme_id");
                }
            } catch (Throwable t) {
                LogUtil.warn(CLASS_NAME, "applicant-tab lookup failed: " + t.getMessage());
            }
        }

        // Always find or create THIS tab's record so the form has a stable
        // primary key, even if no programme has been picked yet.
        String thisTabId = findOrCreateTabRecord(dao, tabFormId, tabTableName, wrapperId);

        if (programmeId == null || programmeId.isEmpty()) {
            LogUtil.info(CLASS_NAME, "  no programme picked yet — skipping seed (tab=" + thisTabId + ").");
            return loadTabRow(dao, tabFormId, tabTableName, thisTabId, wrapperId);
        }

        // Find/create the OTHER two tabs too so seeding completes all three child tables.
        String eligTabId = "elig".equals(tabType) ? thisTabId
                : findOrCreateTabRecord(dao, F_ELIG_TAB, T_ELIG_TAB, wrapperId);
        String benTabId  = "ben".equals(tabType)  ? thisTabId
                : findOrCreateTabRecord(dao, F_BEN_TAB,  T_BEN_TAB,  wrapperId);
        String docTabId  = "doc".equals(tabType)  ? thisTabId
                : findOrCreateTabRecord(dao, F_DOC_TAB,  T_DOC_TAB,  wrapperId);

        // Mirror programme_id + tab ids + application_code onto wrapper so
        // datalists, banner, and downstream joins always have consistent
        // state. Auto-generate application_code on first run.
        boolean dirty = false;
        if (!programmeId.equals(wrapper.getProperty("programme_id"))) {
            wrapper.setProperty("programme_id", programmeId); dirty = true;
        }
        if (!eligTabId.equals(wrapper.getProperty("tab_eligibility"))) {
            wrapper.setProperty("tab_eligibility", eligTabId); dirty = true;
        }
        if (!benTabId.equals(wrapper.getProperty("tab_benefits"))) {
            wrapper.setProperty("tab_benefits", benTabId); dirty = true;
        }
        if (!docTabId.equals(wrapper.getProperty("tab_documents"))) {
            wrapper.setProperty("tab_documents", docTabId); dirty = true;
        }
        String code = wrapper.getProperty("application_code");
        if (code == null || code.isEmpty()) {
            wrapper.setProperty("application_code", nextApplicationCode(wrapperId));
            dirty = true;
        }
        String status = wrapper.getProperty("status");
        if (status == null || status.isEmpty()) {
            wrapper.setProperty("status", "DRAFT"); dirty = true;
        }
        if (dirty) {
            FormRowSet wrapperBatch = new FormRowSet();
            wrapperBatch.add(wrapper);
            dao.saveOrUpdate(F_APPLICATION, T_APPLICATION, wrapperBatch);
            LogUtil.info(CLASS_NAME, "  wrapper updated (programme_id, tab ids)");
        }

        SeedingService seeding = new SeedingService(dao, new ProgrammeSpecReader(dao));
        int created = seeding.seed(wrapperId, programmeId, eligTabId, benTabId, docTabId);
        LogUtil.info(CLASS_NAME, "  SeedingService.seed returned " + created
                + " (elig=" + eligTabId + ", ben=" + benTabId + ", doc=" + docTabId + ")");

        return loadTabRow(dao, tabFormId, tabTableName, thisTabId, wrapperId);
    }

    /**
     * Generate an application code in the form {@code AP-XXXXXX} where
     * XXXXXX is the first 6 characters of the wrapper id (uppercased).
     * Avoids needing a sequence table while still being deterministic and
     * unique-per-application.
     */
    private String nextApplicationCode(String wrapperId) {
        if (wrapperId == null || wrapperId.length() < 6) {
            return "AP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
        return "AP-" + wrapperId.substring(0, 6).toUpperCase();
    }

    private FormRow loadTabRow(FormDataDao dao, String formId, String tableName,
                               String tabId, String wrapperId) {
        FormRow row = dao.load(formId, tableName, tabId);
        if (row == null) {
            // Fall back to a synthetic row carrying the right id + parent_id,
            // so the rendered form still has a consistent primary key for
            // FormGrid foreign-key matching.
            row = new FormRow();
            row.setId(tabId);
            row.setProperty("parent_id", wrapperId);
        }
        return row;
    }

    /**
     * Look up the tab subform record that links to {@code wrapperId}; if none
     * exists, insert an empty one. Returns the tab record's id.
     */
    private String findOrCreateTabRecord(FormDataDao dao, String formId,
                                         String tableName, String wrapperId) {
        try {
            String condition = "WHERE e.customProperties.parent_id = ?";
            Object[] args = new Object[] { wrapperId };
            FormRowSet existing = dao.find(formId, tableName, condition, args, null, null, 0, 1);
            if (existing != null && !existing.isEmpty()) {
                String id = existing.get(0).getId();
                if (id != null && !id.isEmpty()) return id;
            }
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "findOrCreateTabRecord(" + formId
                    + ") lookup failed: " + t.getMessage());
        }
        FormRow row = new FormRow();
        String newId = UUID.randomUUID().toString();
        row.setId(newId);
        row.setProperty("parent_id", wrapperId);
        FormRowSet batch = new FormRowSet();
        batch.add(row);
        dao.saveOrUpdate(formId, tableName, batch);
        LogUtil.info(CLASS_NAME, "  created new tab record " + newId
                + " in " + tableName + " for wrapper " + wrapperId);
        return newId;
    }
}
