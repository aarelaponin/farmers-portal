package global.govstack.application.service;

import global.govstack.application.model.BenefitItem;
import global.govstack.application.model.DocumentRequirement;
import global.govstack.application.model.EligibilityCriterion;
import global.govstack.application.model.ProgrammeSpec;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads a programme's spec from {@code sp_program} + its tab subforms
 * (eligibility, benefits) into a {@link ProgrammeSpec} value object. All reads
 * via {@link FormDataDao} per the plugin methodology rule.
 *
 * <p>The programme model in this app uses Joget's FormGrid foreign-key
 * convention where child rows on a tab subform link to that tab's id, NOT to
 * the wizard wrapper's id. So:
 * <ul>
 *   <li>{@code sp_program.id = prog001}</li>
 *   <li>{@code sp_program.c_tab_eligibility = prog001-elig}</li>
 *   <li>{@code sp_elig_criterion.c_program_id = prog001-elig} (the tab id)</li>
 * </ul>
 * This reader handles the indirection correctly.
 */
public class ProgrammeSpecReader {

    private static final String CLASS_NAME = ProgrammeSpecReader.class.getName();

    private static final String F_PROGRAM       = "spProgramMain";
    private static final String T_PROGRAM       = "sp_program";
    private static final String F_ELIG          = "spEligCriterionRow";
    private static final String T_ELIG          = "sp_elig_criterion";
    private static final String F_BENEFIT       = "spBenefitItemRow";
    private static final String T_BENEFIT       = "sp_benefit_item";
    private static final String F_DOC_REQ       = "spDocRequirementRow";
    private static final String T_DOC_REQ       = "sp_doc_requirement_row";
    private static final String F_DOC_TYPE_MD   = "md23documentType";
    private static final String T_DOC_TYPE_MD   = "md23documentType";

    private final FormDataDao dao;

    public ProgrammeSpecReader(FormDataDao dao) {
        this.dao = dao;
    }

    /**
     * Materialises the programme spec for a given programme record id.
     * Returns null if the programme record does not exist.
     */
    public ProgrammeSpec read(String programmeId) {
        if (programmeId == null || programmeId.isEmpty()) return null;

        FormRow program = dao.load(F_PROGRAM, T_PROGRAM, programmeId);
        if (program == null) {
            LogUtil.warn(CLASS_NAME, "Programme not found: " + programmeId);
            return null;
        }

        String tabElig    = program.getProperty("tab_eligibility");
        String tabBenef   = program.getProperty("tab_benefits");
        String tabBene    = program.getProperty("tab_beneficiary");
        String tabMonit   = program.getProperty("tab_monitoring");
        String tabDocs    = program.getProperty("tab_documents");
        String code       = program.getProperty("programCode");
        String status     = program.getProperty("status");

        List<EligibilityCriterion> criteria  = readCriteria(tabElig);
        List<BenefitItem>          benefits  = readBenefits(tabBenef);
        List<DocumentRequirement>  documents = readDocuments(tabDocs);

        return new ProgrammeSpec(
                programmeId, code, status,
                tabElig, tabBenef, tabBene, tabMonit, tabDocs,
                criteria, benefits, documents);
    }

    /**
     * Read sp_doc_requirement_row rows for a programme's documents tab id, then
     * resolve each requirement's snapshot fields (label / formats / max-size)
     * from md23documentType, applying any per-row overrides on top.
     *
     * <p>Override semantics: if the override field is null/empty, fall back to
     * the MD.23 row; if it's set, the override wins. Same rule for all three
     * snapshot fields independently.
     *
     * <p>If a programme picks a doc_type_code that isn't in MD.23 (shouldn't
     * happen — the SelectBox is bound to MD.23 — but defensively), the row is
     * still emitted with whatever overrides were typed; missing fields end up
     * empty rather than null, so SeedingService can write empty strings.
     */
    private List<DocumentRequirement> readDocuments(String tabDocumentsId) {
        if (tabDocumentsId == null || tabDocumentsId.isEmpty()) return Collections.emptyList();
        String condition = "WHERE e.customProperties.program_id = ?";
        Object[] args = new Object[] { tabDocumentsId };
        FormRowSet rows = dao.find(F_DOC_REQ, T_DOC_REQ, condition, args,
                "display_order", false, null, null);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        List<DocumentRequirement> out = new ArrayList<>(rows.size());
        for (FormRow r : rows) {
            try {
                String typeCode = nz(r.getProperty("doc_type_code"));
                String labelOv  = nz(r.getProperty("doc_label_override"));
                String formatOv = nz(r.getProperty("accepted_formats_override"));
                String sizeOv   = nz(r.getProperty("max_size_kb_override"));
                String mandat   = nz(r.getProperty("doc_mandatory"));
                String order    = nz(r.getProperty("display_order"));

                // Default snapshot from md23documentType (single-row lookup by code).
                String mdLabel = "", mdFormats = "", mdSizeMb = "";
                if (!typeCode.isEmpty()) {
                    String mdCondition = "WHERE e.customProperties.code = ?";
                    Object[] mdArgs = new Object[] { typeCode };
                    FormRowSet mdRows = dao.find(F_DOC_TYPE_MD, T_DOC_TYPE_MD, mdCondition, mdArgs,
                            null, null, 0, 1);
                    if (mdRows != null && !mdRows.isEmpty()) {
                        FormRow md = mdRows.get(0);
                        mdLabel   = nz(md.getProperty("name"));
                        mdFormats = nz(md.getProperty("allowed_formats"));
                        mdSizeMb  = nz(md.getProperty("max_size_mb"));
                    }
                }

                String resolvedLabel   = labelOv.isEmpty()  ? mdLabel   : labelOv;
                String resolvedFormats = formatOv.isEmpty() ? mdFormats : formatOv;
                String resolvedSizeKb  = sizeOv.isEmpty()   ? mbToKb(mdSizeMb) : sizeOv;

                out.add(new DocumentRequirement(
                        r.getId(),
                        typeCode,
                        resolvedLabel,
                        mandat.isEmpty() ? "Y" : mandat,   // safer default than no value
                        resolvedFormats,
                        resolvedSizeKb,
                        order));
            } catch (RuntimeException e) {
                LogUtil.warn(CLASS_NAME, "Skipping malformed doc requirement " + r.getId()
                        + ": " + e.getMessage());
            }
        }
        return out;
    }

    /** Convert "5" or "5.0" (megabytes) to "5120" (kilobytes). Returns "" on
     *  blank or unparseable input. */
    private static String mbToKb(String mb) {
        if (mb == null || mb.isEmpty()) return "";
        try {
            double v = Double.parseDouble(mb.trim());
            long kb = Math.round(v * 1024.0);
            return Long.toString(kb);
        } catch (NumberFormatException nfe) {
            return "";
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }


    private List<EligibilityCriterion> readCriteria(String tabEligibilityId) {
        if (tabEligibilityId == null || tabEligibilityId.isEmpty()) return Collections.emptyList();
        String condition = "WHERE e.customProperties.program_id = ?";
        Object[] args = new Object[] { tabEligibilityId };
        FormRowSet rows = dao.find(F_ELIG, T_ELIG, condition, args,
                "criterionOrder", false, null, null);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        List<EligibilityCriterion> out = new ArrayList<>(rows.size());
        for (FormRow r : rows) {
            try {
                out.add(new EligibilityCriterion(
                        r.getId(),
                        r.getProperty("fieldName"),
                        r.getProperty("operatorCode"),
                        r.getProperty("criterionValue"),
                        r.getProperty("criterionValueTo"),
                        r.getProperty("ruleType"),
                        r.getProperty("fieldCategory"),
                        r.getProperty("failMessage"),
                        r.getProperty("isMandatory"),
                        r.getProperty("score"),
                        r.getProperty("criterionOrder"),
                        r.getProperty("notes")));
            } catch (RuntimeException e) {
                LogUtil.warn(CLASS_NAME, "Skipping malformed criterion " + r.getId()
                        + ": " + e.getMessage());
            }
        }
        return out;
    }

    private List<BenefitItem> readBenefits(String tabBenefitsId) {
        if (tabBenefitsId == null || tabBenefitsId.isEmpty()) return Collections.emptyList();
        String condition = "WHERE e.customProperties.program_id = ?";
        Object[] args = new Object[] { tabBenefitsId };
        FormRowSet rows = dao.find(F_BENEFIT, T_BENEFIT, condition, args,
                "itemCode", false, null, null);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        List<BenefitItem> out = new ArrayList<>(rows.size());
        for (FormRow r : rows) {
            try {
                out.add(new BenefitItem(
                        r.getId(),
                        r.getProperty("itemCode"),
                        r.getProperty("itemName"),
                        r.getProperty("itemType"),
                        r.getProperty("categoryCode"),
                        r.getProperty("unit"),
                        r.getProperty("unitCost"),
                        r.getProperty("quantity"),
                        r.getProperty("subsidyAmount"),
                        r.getProperty("subsidyPercent"),
                        r.getProperty("farmerContribution"),
                        r.getProperty("totalCost"),
                        r.getProperty("notes")));
            } catch (RuntimeException e) {
                LogUtil.warn(CLASS_NAME, "Skipping malformed benefit " + r.getId()
                        + ": " + e.getMessage());
            }
        }
        return out;
    }
}
