package global.govstack.application.hook;

import global.govstack.application.Build;
import global.govstack.application.service.ProgrammeSpecReader;
import global.govstack.application.service.SeedingService;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Wired as the form post-processor on {@code spApplication} AND on every tab
 * subform of the application wizard. Runs on every save (Joget contract:
 * {@link DefaultApplicationPlugin}). On the first save where the application
 * has a chosen {@code programme_id}, this seeds the application's
 * eligibility-check and benefit-request child tables from that programme's
 * spec. Subsequent saves are no-ops thanks to {@link SeedingService}'s
 * idempotency check.
 *
 * <p><b>Why wired on tabs too:</b> Joget's {@code MultiPagedForm} partial-store
 * path saves the wrapper via {@code storeFormData()} which bypasses the
 * postProcessor (only {@code processFormSubmission()} fires it). Wiring this
 * tool on each tab means it ALWAYS fires when the user saves a draft or
 * navigates between tabs. The tool resolves the actual wrapper id from
 * whichever record type {@code recordId} represents.
 *
 * <p>Joget invocation contract — same as {@code FormQualityPostProcessor} in
 * {@code form-quality-runtime}: properties Map carries {@code recordId},
 * {@code appDef}, etc., but NOT {@code FormData}.
 */
public class ApplicationSeedingTool extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = ApplicationSeedingTool.class.getName();

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
    private static final String F_DECL_TAB      = "spApplicationDeclaration";
    private static final String T_DECL_TAB      = "sp_application_decln";

    @Override
    public String getName()        { return "Application Seeding Tool"; }
    @Override
    public String getDescription() {
        return "Seeds an application's eligibility / benefit child rows from "
             + "the chosen programme's spec on first save. ["
             + Build.STAMP + "]";
    }
    @Override
    public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override
    public String getLabel()       { return getName(); }
    @Override
    public String getClassName()   { return getClass().getName(); }

    @Override
    public String getPropertyOptions() {
        // No configuration — wired as a postProcessor on spApplication and
        // reads everything from the saved application record itself.
        return "[]";
    }

    @Override
    public Object execute(Map properties) {
        try {
            String recordId = stringProp(properties, "recordId");
            LogUtil.info(CLASS_NAME, "[" + Build.STAMP + "] execute() recordId=" + recordId);
            if (recordId == null || recordId.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No recordId in invocation context — skipping.");
                return null;
            }

            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            // The postProcessor is wired on every tab + the wrapper, so
            // recordId could be a wrapper id OR a tab subform's id. Resolve
            // to the wrapper.
            String wrapperId = resolveWrapperId(dao, recordId);
            if (wrapperId == null) {
                LogUtil.warn(CLASS_NAME, "recordId " + recordId
                        + " is not an application wrapper or tab — skipping.");
                return null;
            }
            LogUtil.info(CLASS_NAME, "  resolved wrapperId=" + wrapperId);

            FormRow application = dao.load(F_APPLICATION, T_APPLICATION, wrapperId);
            if (application == null) {
                LogUtil.warn(CLASS_NAME, "Application " + wrapperId
                        + " not found in " + T_APPLICATION + " — skipping.");
                return null;
            }

            // programme_id lives on Tab 1 (the applicant subform) because the
            // wrapper's main-section fields aren't reliably persisted by
            // MultiPagedForm partial-store. The wrapper carries a HiddenField
            // mirror of programme_id; we backfill it here so datalists that
            // join on sp_application.c_programme_id work.
            String programmeId = application.getProperty("programme_id");
            LogUtil.info(CLASS_NAME, "  wrapper.programme_id=" + programmeId);
            if (programmeId == null || programmeId.isEmpty()) {
                programmeId = readProgrammeIdFromApplicantTab(dao, wrapperId);
                LogUtil.info(CLASS_NAME, "  applicant-tab.programme_id=" + programmeId);
            }
            if (programmeId == null || programmeId.isEmpty()) {
                LogUtil.info(CLASS_NAME, "  no programme picked yet — nothing to seed.");
                return null;
            }

            // Provision Tab 2 + Tab 3 + Tab 4 records if they don't exist yet, so the
            // FormGrid loadBinder finds rows when the user navigates to those
            // tabs for the first time. MultiPagedForm reuses existing tab
            // records keyed by parent_id, so creating them up-front is safe.
            String eligibilityTabId = application.getProperty("tab_eligibility");
            String benefitsTabId    = application.getProperty("tab_benefits");
            String documentsTabId   = application.getProperty("tab_documents");
            boolean wrapperDirty = false;

            if (programmeId != null && !programmeId.isEmpty()
                    && !programmeId.equals(application.getProperty("programme_id"))) {
                application.setProperty("programme_id", programmeId);
                wrapperDirty = true;
            }

            if (eligibilityTabId == null || eligibilityTabId.isEmpty()) {
                eligibilityTabId = ensureTabRecord(dao, F_ELIG_TAB, T_ELIG_TAB, wrapperId);
                application.setProperty("tab_eligibility", eligibilityTabId);
                wrapperDirty = true;
                LogUtil.info(CLASS_NAME, "  provisioned eligibility tab " + eligibilityTabId);
            }
            if (benefitsTabId == null || benefitsTabId.isEmpty()) {
                benefitsTabId = ensureTabRecord(dao, F_BEN_TAB, T_BEN_TAB, wrapperId);
                application.setProperty("tab_benefits", benefitsTabId);
                wrapperDirty = true;
                LogUtil.info(CLASS_NAME, "  provisioned benefits tab " + benefitsTabId);
            }
            if (documentsTabId == null || documentsTabId.isEmpty()) {
                documentsTabId = ensureTabRecord(dao, F_DOC_TAB, T_DOC_TAB, wrapperId);
                application.setProperty("tab_documents", documentsTabId);
                wrapperDirty = true;
                LogUtil.info(CLASS_NAME, "  provisioned documents tab " + documentsTabId);
            }

            if (wrapperDirty) {
                FormRowSet wrapperBatch = new FormRowSet();
                wrapperBatch.add(application);
                dao.saveOrUpdate(F_APPLICATION, T_APPLICATION, wrapperBatch);
                LogUtil.info(CLASS_NAME, "  wrapper updated (programme_id + tab ids)");
            }

            SeedingService seeding = new SeedingService(dao, new ProgrammeSpecReader(dao));
            int rowsCreated = seeding.seed(wrapperId, programmeId,
                    eligibilityTabId, benefitsTabId, documentsTabId);
            LogUtil.info(CLASS_NAME, "  SeedingService.seed(...) returned " + rowsCreated);
            if (rowsCreated > 0) {
                LogUtil.info(CLASS_NAME, "Application " + wrapperId
                        + " seeded from programme " + programmeId
                        + " — " + rowsCreated + " rows created (triggered by save of "
                        + recordId + ").");
            }
        } catch (Throwable t) {
            // Belt-and-braces: never block a save because of a seeding bug.
            LogUtil.error(CLASS_NAME, t,
                    "Application seeding failed: " + t.getMessage());
        }
        return null;
    }

    /**
     * Insert (or return existing) tab subform record linking to the wrapper.
     * If the table already has a row with this parent_id, we reuse it — there
     * should only ever be one of each tab per application.
     */
    private String ensureTabRecord(FormDataDao dao, String formId, String tableName,
                                   String wrapperId) {
        try {
            String condition = "WHERE e.customProperties.parent_id = ?";
            Object[] args = new Object[] { wrapperId };
            FormRowSet existing = dao.find(formId, tableName, condition, args, null, null, 0, 1);
            if (existing != null && !existing.isEmpty()) {
                String id = existing.get(0).getId();
                if (id != null && !id.isEmpty()) return id;
            }
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "ensureTabRecord(" + formId + ") lookup failed: " + t.getMessage());
        }
        // Insert a fresh row.
        FormRow row = new FormRow();
        String newId = UUID.randomUUID().toString();
        row.setId(newId);
        row.setProperty("parent_id", wrapperId);
        FormRowSet batch = new FormRowSet();
        batch.add(row);
        dao.saveOrUpdate(formId, tableName, batch);
        return newId;
    }

    private static String stringProp(Map properties, String key) {
        Object v = properties.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * Read the programme id off the applicant tab record (Tab 1), where the
     * wizard reliably persists it. The applicant tab's parent_id points at
     * the wrapper, so we look up by parent_id.
     */
    private String readProgrammeIdFromApplicantTab(FormDataDao dao, String wrapperId) {
        try {
            String condition = "WHERE e.customProperties.parent_id = ?";
            Object[] args = new Object[] { wrapperId };
            FormRowSet rows = dao.find(F_APPLICANT_TAB, T_APPLICANT_TAB,
                    condition, args, null, null, 0, 1);
            if (rows != null && !rows.isEmpty()) {
                String pid = rows.get(0).getProperty("programme_id");
                if (pid != null && !pid.isEmpty()) return pid;
            }
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "applicant-tab lookup failed: " + t.getMessage());
        }
        return null;
    }

    /**
     * Walk up from whichever record was just saved (wrapper or any tab) to
     * the {@code spApplication} wrapper id. Tabs link to the wrapper via
     * their {@code parent_id} column (the wizard's
     * {@code subFormParentId} convention).
     */
    private String resolveWrapperId(FormDataDao dao, String recordId) {
        // Cheapest first: is recordId already the wrapper?
        if (dao.load(F_APPLICATION, T_APPLICATION, recordId) != null) {
            return recordId;
        }
        // Otherwise check each tab's table — first hit wins.
        String[][] tabs = new String[][] {
            { F_APPLICANT_TAB, T_APPLICANT_TAB },
            { F_ELIG_TAB,      T_ELIG_TAB      },
            { F_BEN_TAB,       T_BEN_TAB       },
            { F_DOC_TAB,       T_DOC_TAB       },
            { F_DECL_TAB,      T_DECL_TAB      }
        };
        for (String[] tab : tabs) {
            FormRow row = dao.load(tab[0], tab[1], recordId);
            if (row != null) {
                String pid = row.getProperty("parent_id");
                if (pid != null && !pid.isEmpty()) {
                    return pid;
                }
            }
        }
        return null;
    }
}
