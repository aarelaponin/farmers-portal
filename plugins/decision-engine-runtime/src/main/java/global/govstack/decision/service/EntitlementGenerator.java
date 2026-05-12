package global.govstack.decision.service;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.UUID;

/**
 * Builds the entitlement record (and its line items) for an approved
 * application. Invoked by {@link global.govstack.decision.hook.DecisionDispatchTool}
 * on the APPROVE branch.
 *
 * <p>Idempotent — if an entitlement already exists with the same
 * application reference (we encode it in {@code entitlementCode}), the
 * second call is a no-op.
 *
 * <p>Maps:
 * <pre>
 *   sp_application + sp_application_applicnt + sp_program
 *      → imEntitlement (1 row)
 *      → imEntitlementItem (N rows, one per APPROVED/REDUCED benefit line)
 * </pre>
 */
public class EntitlementGenerator {

    private static final String CLASS_NAME = EntitlementGenerator.class.getName();

    private static final String F_APPLICATION = "spApplication";
    private static final String T_APPLICATION = "sp_application";
    private static final String F_APPLICANT_TAB = "spApplicationApplicant";
    private static final String T_APPLICANT_TAB = "sp_application_applicnt";
    private static final String F_BENEFIT_REQ   = "spApplicationBenefitReq";
    private static final String T_BENEFIT_REQ   = "sp_application_ben_req";
    private static final String F_PROGRAM       = "spProgramMain";
    private static final String T_PROGRAM       = "sp_program";

    private static final String F_ENTITLEMENT   = "imEntitlement";
    private static final String T_ENTITLEMENT   = "imEntitlement";
    private static final String F_ENT_ITEM      = "imEntitlementItem";
    private static final String T_ENT_ITEM      = "imEntitlementItem";

    private final FormDataDao dao;

    public EntitlementGenerator(FormDataDao dao) {
        this.dao = dao;
    }

    /**
     * @param applicationId  wrapper application id
     * @return the generated entitlement code, or {@code null} if nothing
     *         was generated (already exists or required data missing)
     */
    public String generate(String applicationId, String approvedBy) {
        if (applicationId == null || applicationId.isEmpty()) return null;

        FormRow application = dao.load(F_APPLICATION, T_APPLICATION, applicationId);
        if (application == null) {
            LogUtil.warn(CLASS_NAME, "application " + applicationId + " not found.");
            return null;
        }

        String entitlementCode = "EN-" + applicationId.substring(0, 6).toUpperCase();

        // Idempotency check: lookup entitlement by code
        if (entitlementExists(entitlementCode)) {
            LogUtil.info(CLASS_NAME, "entitlement " + entitlementCode
                    + " already exists for application " + applicationId
                    + " — skipping (idempotent).");
            return entitlementCode;
        }

        // Pull applicant identity from Tab 1
        FormRow applicant = findByParent(F_APPLICANT_TAB, T_APPLICANT_TAB, applicationId);

        // Pull programme code via Joget's FormDataDao. Property lookup is
        // case-sensitive; PostgreSQL case-folds the column to c_programcode,
        // so the property comes back as lowercase "programcode" — not the
        // camelCase field id. Try both to be safe.
        String programmeId = application.getProperty("programme_id");
        String programmeCode = nz(programmeId);
        if (programmeId != null && !programmeId.isEmpty()) {
            FormRow programme = dao.load(F_PROGRAM, T_PROGRAM, programmeId);
            if (programme != null) {
                String c = programme.getProperty("programcode");
                if (c == null || c.isEmpty()) c = programme.getProperty("programCode");
                if (c != null && !c.isEmpty()) programmeCode = c;
            }
        }

        // Pull approved benefit lines (or all PENDING lines if none APPROVED)
        FormRowSet benefitLines = findBenefitLinesForApplication(applicationId);
        if (benefitLines == null || benefitLines.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "no benefit lines for application " + applicationId
                    + " — entitlement not generated.");
            return null;
        }

        BigDecimal totalSubsidy = BigDecimal.ZERO;
        BigDecimal totalContrib = BigDecimal.ZERO;
        BigDecimal totalValue   = BigDecimal.ZERO;

        // Money math:
        //   lineValue   = qty * unitCost
        //   lineSubsidy = lineValue * subsidyPercent / 100
        //   lineContrib = lineValue - lineSubsidy
        // (Don't multiply qty * subsidy_amount — subsidy_amount in the
        //  programme spec is the TOTAL line subsidy at default qty, not a
        //  per-unit rate, so it'd give nonsense for non-default quantities.)
        BigDecimal HUNDRED = new BigDecimal("100");
        for (FormRow line : benefitLines) {
            // Soft-cap policy (B): operator's approved_qty wins if set,
            // else fall back to the farmer's requested_qty, else the
            // programme's default. The Decision tab's benefit grid lets the
            // operator override approved_qty per line; if they don't, the
            // entitlement is issued at the requested amount.
            BigDecimal qty       = num(line.getProperty("approved_qty"),
                                        line.getProperty("requested_qty"),
                                        line.getProperty("default_quantity"));
            BigDecimal unitCost  = num(line.getProperty("unit_cost"));
            BigDecimal subPct    = num(line.getProperty("subsidy_percent"));
            BigDecimal lineValue = qty.multiply(unitCost);
            BigDecimal lineSubsidy = lineValue
                    .multiply(subPct)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal lineContrib = lineValue.subtract(lineSubsidy);
            if (lineContrib.signum() < 0) lineContrib = BigDecimal.ZERO;
            totalSubsidy = totalSubsidy.add(lineSubsidy);
            totalContrib = totalContrib.add(lineContrib);
            totalValue   = totalValue.add(lineValue);
            line.setProperty("__qty",         qty.toPlainString());
            line.setProperty("__lineValue",   lineValue.toPlainString());
            line.setProperty("__lineSubsidy", lineSubsidy.toPlainString());
            line.setProperty("__lineContrib", lineContrib.toPlainString());
            line.setProperty("__subsidyPct",  subPct.toPlainString());
        }

        // Build header
        FormRow header = new FormRow();
        header.setId(UUID.randomUUID().toString());
        header.setProperty("entitlementCode", entitlementCode);
        header.setProperty("status",          "APPROVED");
        header.setProperty("packageCode",     nz(programmeCode));
        header.setProperty("campaignCode",    nz(programmeCode));
        if (applicant != null) {
            header.setProperty("farmerCode",       nz(applicant.getProperty("applicant_id")));
            header.setProperty("farmerName",
                    join(applicant.getProperty("applicant_first_name"),
                         applicant.getProperty("applicant_last_name")));
            header.setProperty("farmerNationalId", nz(applicant.getProperty("national_id")));
            header.setProperty("farmerPhone",      nz(applicant.getProperty("applicant_mobile")));
            header.setProperty("districtCode",     nz(applicant.getProperty("applicant_district")));
        }
        header.setProperty("totalSubsidyValue",   totalSubsidy.toPlainString());
        header.setProperty("totalEntitleValue",   totalValue.toPlainString());
        header.setProperty("farmerContribTotal",  totalContrib.toPlainString());
        header.setProperty("approvedDate",        new Date().toString());
        header.setProperty("createdDate",         new Date().toString());
        header.setProperty("calculationDetails",
                "Generated from sp_application " + applicationId + " by " + nz(approvedBy));

        FormRowSet headerBatch = new FormRowSet();
        headerBatch.add(header);
        dao.saveOrUpdate(F_ENTITLEMENT, T_ENTITLEMENT, headerBatch);

        // Build items
        FormRowSet itemBatch = new FormRowSet();
        itemBatch.setMultiRow(true);
        for (FormRow line : benefitLines) {
            FormRow item = new FormRow();
            item.setId(UUID.randomUUID().toString());
            item.setProperty("entitlementCode", entitlementCode);
            item.setProperty("inputCode",       nz(line.getProperty("item_code")));
            item.setProperty("inputName",       nz(line.getProperty("item_label")));
            item.setProperty("inputCategory",   "");
            item.setProperty("unit",            nz(line.getProperty("unit")));
            item.setProperty("qtyEntitled",     nz(line.getProperty("__qty")));
            item.setProperty("qtyRedeemed",     "0");
            item.setProperty("subsidyAmount",   nz(line.getProperty("__lineSubsidy")));
            item.setProperty("farmerContrib",   nz(line.getProperty("__lineContrib")));
            item.setProperty("unitValue",       nz(line.getProperty("unit_cost")));
            item.setProperty("itemStatus",      "PENDING_REDEMPTION");
            itemBatch.add(item);
        }
        if (!itemBatch.isEmpty()) {
            dao.saveOrUpdate(F_ENT_ITEM, T_ENT_ITEM, itemBatch);
        }

        LogUtil.info(CLASS_NAME, "entitlement " + entitlementCode + " issued from application "
                + applicationId + ": " + benefitLines.size() + " item(s), "
                + "subsidy=" + totalSubsidy + ", contrib=" + totalContrib);
        return entitlementCode;
    }

    private boolean entitlementExists(String entitlementCode) {
        try {
            String condition = "WHERE e.customProperties.entitlementCode = ?";
            Object[] args = new Object[] { entitlementCode };
            FormRowSet rows = dao.find(F_ENTITLEMENT, T_ENTITLEMENT,
                    condition, args, null, null, 0, 1);
            return rows != null && !rows.isEmpty();
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "entitlement existence check failed: " + t.getMessage());
            return false;
        }
    }

    private FormRow findByParent(String formId, String tableName, String parentId) {
        try {
            String condition = "WHERE e.customProperties.parent_id = ?";
            Object[] args = new Object[] { parentId };
            FormRowSet rows = dao.find(formId, tableName, condition, args, null, null, 0, 1);
            if (rows != null && !rows.isEmpty()) return rows.get(0);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "findByParent " + formId + " failed: " + t.getMessage());
        }
        return null;
    }

    private FormRowSet findBenefitLinesForApplication(String applicationId) {
        // Walk: wrapper → tab_benefits id → benefit lines where parent_id = tab_benefits id
        FormRow wrapper = dao.load(F_APPLICATION, T_APPLICATION, applicationId);
        if (wrapper == null) return null;
        String benTabId = wrapper.getProperty("tab_benefits");
        if (benTabId == null || benTabId.isEmpty()) return null;
        try {
            String condition = "WHERE e.customProperties.parent_id = ?"
                             + " AND (e.customProperties.status = 'APPROVED'"
                             + "      OR e.customProperties.status = 'REDUCED'"
                             + "      OR e.customProperties.status = 'PENDING')";
            Object[] args = new Object[] { benTabId };
            return dao.find(F_BENEFIT_REQ, T_BENEFIT_REQ, condition, args, null, null, 0, 999);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "benefit-line lookup failed: " + t.getMessage());
            return null;
        }
    }

    private static BigDecimal num(String... candidates) {
        for (String s : candidates) {
            if (s == null) continue;
            try {
                return new BigDecimal(s.trim());
            } catch (Exception ignore) { /* try next */ }
        }
        return BigDecimal.ZERO;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String join(String a, String b) {
        String left = nz(a).trim(); String right = nz(b).trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + " " + right;
    }
}
