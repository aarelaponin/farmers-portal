package global.govstack.application.service;

import global.govstack.application.model.BenefitItem;
import global.govstack.application.model.DocumentRequirement;
import global.govstack.application.model.EligibilityCriterion;
import global.govstack.application.model.ProgrammeSpec;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.UUID;

/**
 * Generates the application's child rows from a programme spec. Idempotent:
 * second invocation for the same application is a no-op (re-seed must be
 * explicit; we never overwrite a farmer's edits).
 *
 * <p>Two child tables are seeded in this build:
 * <ol>
 *   <li>{@code sp_application_eligibility_check} — one row per programme
 *       eligibility criterion. Carries a snapshot of the criterion text and
 *       an empty {@code applicantResponse} field for the farmer to answer.</li>
 *   <li>{@code sp_application_benefit_request} — one row per programme benefit
 *       item. Carries a snapshot of the item details and an empty
 *       {@code requestedQty} for the farmer to fill in.</li>
 * </ol>
 *
 * <p>Document submission seeding is a follow-up (Block 3.1 small extension —
 * needs the new {@code sp_doc_requirement} programme child first).
 */
public class SeedingService {

    private static final String CLASS_NAME = SeedingService.class.getName();

    private static final String F_ELIG_CHECK = "spApplicationEligibility";
    private static final String T_ELIG_CHECK = "sp_application_elig_chk";
    private static final String F_BEN_REQ    = "spApplicationBenefitReq";
    private static final String T_BEN_REQ    = "sp_application_ben_req";
    private static final String F_DOC_REQ    = "spApplicationDocV2";
    private static final String T_DOC_REQ    = "sp_application_doc_v2";

    private final FormDataDao         dao;
    private final ProgrammeSpecReader specReader;

    public SeedingService(FormDataDao dao, ProgrammeSpecReader specReader) {
        this.dao = dao;
        this.specReader = specReader;
    }

    /**
     * Seed the application's child grids from the chosen programme.
     *
     * <p>FormGrid in a Joget wizard pattern stores child rows linked to the
     * <b>tab subform's record id</b> (NOT the wrapper id) — see CLAUDE.md
     * "FormGrid foreignKey convention". So we accept the tab ids explicitly.
     *
     * @param applicationId    the spApplication wrapper record id (used for logging only)
     * @param programmeId      the programme being applied to
     * @param eligibilityTabId sp_application_elig_tab.id — elig child rows' parent_id
     * @param benefitsTabId    sp_application_ben_tab.id  — benefit child rows' parent_id
     * @param documentsTabId   sp_application_doc_tab.id  — doc child rows' parent_id
     * @return number of rows created (across all child tables)
     */
    public int seed(String applicationId, String programmeId,
                    String eligibilityTabId, String benefitsTabId,
                    String documentsTabId) {
        if (applicationId == null || applicationId.isEmpty()) return 0;
        if (programmeId   == null || programmeId.isEmpty())   return 0;

        // Idempotency check uses the tab ids since that's where rows are linked.
        boolean eligAlreadySeeded = eligibilityTabId != null && !eligibilityTabId.isEmpty()
                && childRowsExist(F_ELIG_CHECK, T_ELIG_CHECK, eligibilityTabId);
        boolean benAlreadySeeded  = benefitsTabId != null && !benefitsTabId.isEmpty()
                && childRowsExist(F_BEN_REQ, T_BEN_REQ, benefitsTabId);
        boolean docAlreadySeeded  = documentsTabId != null && !documentsTabId.isEmpty()
                && childRowsExist(F_DOC_REQ, T_DOC_REQ, documentsTabId);
        if (eligAlreadySeeded && benAlreadySeeded && docAlreadySeeded) {
            LogUtil.info(CLASS_NAME, "Application " + applicationId
                    + " already seeded — skipping (idempotent).");
            return 0;
        }

        ProgrammeSpec spec = specReader.read(programmeId);
        if (spec == null) {
            LogUtil.warn(CLASS_NAME, "No programme spec for " + programmeId
                    + " — application " + applicationId + " not seeded.");
            return 0;
        }

        int count = 0;
        if (!eligAlreadySeeded && eligibilityTabId != null && !eligibilityTabId.isEmpty()) {
            count += seedEligibility(eligibilityTabId, spec);
        }
        if (!benAlreadySeeded && benefitsTabId != null && !benefitsTabId.isEmpty()) {
            count += seedBenefits(benefitsTabId, spec);
        }
        if (!docAlreadySeeded && documentsTabId != null && !documentsTabId.isEmpty()) {
            count += seedDocuments(documentsTabId, spec);
        }
        LogUtil.info(CLASS_NAME, "Seeded application " + applicationId
                + " from programme " + programmeId + ": " + count + " rows ("
                + spec.criteria.size() + " eligibility, "
                + spec.benefits.size() + " benefit, "
                + spec.documents.size() + " document). "
                + "elig tab=" + eligibilityTabId + ", ben tab=" + benefitsTabId
                + ", doc tab=" + documentsTabId);
        return count;
    }

    /**
     * Backwards-compatible overload — used by callers that don't know about
     * the documents tab yet. Delegates with documentsTabId=null so the docs
     * branch is skipped silently.
     */
    public int seed(String applicationId, String programmeId,
                    String eligibilityTabId, String benefitsTabId) {
        return seed(applicationId, programmeId, eligibilityTabId, benefitsTabId, null);
    }

    private int seedEligibility(String eligTabId, ProgrammeSpec spec) {
        FormRowSet batch = new FormRowSet();
        batch.setMultiRow(true);
        for (EligibilityCriterion c : spec.criteria) {
            FormRow row = new FormRow();
            row.setId(UUID.randomUUID().toString());
            row.setProperty("parent_id",              eligTabId);
            row.setProperty("programme_criterion_id", c.id);
            row.setProperty("criterion_label",        nz(c.displayLabel()));
            row.setProperty("criterion_severity",
                    "Y".equalsIgnoreCase(c.mandatory) ? "MANDATORY" : "ADVISORY");
            row.setProperty("rule_type",              nz(c.ruleType));
            row.setProperty("field_category",         nz(c.fieldCategory));
            row.setProperty("applicant_response",     "");
            row.setProperty("evaluator_note",         "");
            batch.add(row);
        }
        if (batch.isEmpty()) return 0;
        dao.saveOrUpdate(F_ELIG_CHECK, T_ELIG_CHECK, batch);
        return batch.size();
    }

    private int seedBenefits(String benTabId, ProgrammeSpec spec) {
        FormRowSet batch = new FormRowSet();
        batch.setMultiRow(true);
        for (BenefitItem b : spec.benefits) {
            FormRow row = new FormRow();
            row.setId(UUID.randomUUID().toString());
            row.setProperty("parent_id",            benTabId);
            row.setProperty("programme_benefit_id", b.id);
            row.setProperty("item_label",           nz(b.displayLabel()));
            row.setProperty("item_code",            nz(b.itemCode));
            row.setProperty("unit",                 nz(b.unit));
            row.setProperty("unit_cost",            nz(b.unitCost));
            row.setProperty("subsidy_amount",       nz(b.subsidyAmount));
            row.setProperty("subsidy_percent",      nz(b.subsidyPercent));
            row.setProperty("default_quantity",     nz(b.quantity));
            row.setProperty("requested_qty",        "0");
            row.setProperty("approved_qty",         "");
            row.setProperty("status",               "PENDING");
            batch.add(row);
        }
        if (batch.isEmpty()) return 0;
        dao.saveOrUpdate(F_BEN_REQ, T_BEN_REQ, batch);
        return batch.size();
    }

    private int seedDocuments(String docTabId, ProgrammeSpec spec) {
        FormRowSet batch = new FormRowSet();
        batch.setMultiRow(true);
        for (DocumentRequirement d : spec.documents) {
            FormRow row = new FormRow();
            row.setId(UUID.randomUUID().toString());
            row.setProperty("parent_id",                    docTabId);
            row.setProperty("programme_doc_requirement_id", d.id);
            row.setProperty("doc_code",                     nz(d.docTypeCode));
            row.setProperty("doc_label",                    nz(d.displayLabel()));
            row.setProperty("doc_mandatory",                "Y".equalsIgnoreCase(d.docMandatory) ? "Y" : "N");
            row.setProperty("accepted_formats",             nz(d.acceptedFormats));
            row.setProperty("max_size_kb",                  nz(d.maxSizeKb));
            row.setProperty("uploaded_file",                "");
            row.setProperty("uploaded_at",                  "");
            row.setProperty("status",                       "MISSING");
            row.setProperty("rejection_reason",             "");
            row.setProperty("reviewer_note",                "");
            batch.add(row);
        }
        if (batch.isEmpty()) return 0;
        dao.saveOrUpdate(F_DOC_REQ, T_DOC_REQ, batch);
        return batch.size();
    }

    private boolean childRowsExist(String formId, String tableName, String parentId) {
        String condition = "WHERE e.customProperties.parent_id = ?";
        Object[] args = new Object[] { parentId };
        Long count = dao.count(formId, tableName, condition, args);
        return count != null && count > 0;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
