package global.govstack.farmer.lib;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FarmerDerivedRefresh — Phase 1 D1.E
 *
 * Recomputes the spFarmerDerived snapshot for one or all farmers.
 *
 * Reads:
 *   - farmerBasicInfo, farmerIncomePrograms (latest), farmerHousehold,
 *     householdMemberForm, parcelRegistration, parcelGeometry,
 *     livestockDetailsForm, spProgParticip
 *
 * Writes:
 *   - spFarmerDerived (upsert by farmerCode)
 *   - audit_log
 *
 * Idempotent. Safe to run multiple times per day.
 *
 * Spec: _form-specs/_phase1/D1.E_FarmerDerivedRefresh.plugin.spec.yaml
 */
public class FarmerDerivedRefresh extends DefaultApplicationPlugin {

    private static final String PLUGIN_NAME = "Farmer Derived Refresh";
    private static final String PLUGIN_DESCRIPTION =
            "Recomputes spFarmerDerived snapshots for farmers — closes Phase 1 D1.E";
    private static final String PLUGIN_VERSION = "1.0.0";
    private static final String REFRESH_SOURCE = "FarmerDerivedRefresh-v1";

    // Form IDs read
    private static final String FORM_FARMER_BASIC = "farmerBasicInfo";
    private static final String FORM_FARMER_INCOME = "farmerIncomePrograms";
    private static final String FORM_FARMER_HOUSEHOLD = "farmerHousehold";
    private static final String FORM_HOUSEHOLD_MEMBER = "householdMemberForm";
    private static final String FORM_PARCEL = "parcelRegistration";
    private static final String FORM_LIVESTOCK = "livestockDetailsForm";
    private static final String FORM_PROG_PARTICIP = "spProgParticip";

    // Form ID written
    private static final String FORM_DERIVED = "spFarmerDerived";

    @Override
    public String getName()        { return PLUGIN_NAME; }
    @Override
    public String getVersion()     { return PLUGIN_VERSION; }
    @Override
    public String getDescription() { return PLUGIN_DESCRIPTION; }
    @Override
    public String getLabel()       { return PLUGIN_NAME; }
    @Override
    public String getClassName()   { return getClass().getName(); }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
                getClass().getName(),
                "/properties/farmerDerivedRefresh.json",
                null,
                true,
                "messages/farmerDerivedRefresh");
    }

    /**
     * Plugin entry point — invoked by Joget when the workflow tool activity fires.
     *
     * @param properties Configuration from the plugin's JSON config + workflow variables
     * @return Map with status, farmersProcessed, errors
     */
    @Override
    public Object execute(Map properties) {
        long start = System.currentTimeMillis();
        String scope = (String) properties.getOrDefault("scope", "all");
        String farmerCode = (String) properties.getOrDefault("farmerCode", "");
        String staleThresholdDaysStr = (String) properties.getOrDefault("staleThresholdDays", "1");
        boolean dryRun = "true".equalsIgnoreCase((String) properties.getOrDefault("dryRun", "false"));
        int staleThresholdDays;
        try {
            staleThresholdDays = Integer.parseInt(staleThresholdDaysStr);
        } catch (NumberFormatException e) {
            staleThresholdDays = 1;
        }

        LogUtil.info(getClassName(),
                "FarmerDerivedRefresh start: scope=" + scope
                        + ", farmerCode=" + farmerCode
                        + ", staleThresholdDays=" + staleThresholdDays
                        + ", dryRun=" + dryRun);

        Map<String, Object> result = new HashMap<>();
        result.put("scope", scope);
        result.put("dryRun", dryRun);

        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

        List<String> farmerCodes;
        if ("single".equalsIgnoreCase(scope)) {
            if (farmerCode == null || farmerCode.isEmpty()) {
                LogUtil.warn(getClassName(), "scope=single but farmerCode is empty — nothing to do");
                result.put("status", "NOOP");
                return result;
            }
            farmerCodes = List.of(farmerCode);
        } else {
            farmerCodes = loadAllFarmerCodes(dao, staleThresholdDays);
        }

        int processed = 0;
        int failed = 0;
        for (String code : farmerCodes) {
            try {
                refreshOneFarmer(dao, code, dryRun);
                processed++;
            } catch (Exception ex) {
                failed++;
                LogUtil.error(getClassName(), ex,
                        "Failed to refresh farmer " + code + ": " + ex.getMessage());
                writeAudit(dao, code, "FAILURE", ex.getMessage(), 0);
            }
        }

        long durationMs = System.currentTimeMillis() - start;
        LogUtil.info(getClassName(),
                "FarmerDerivedRefresh done: processed=" + processed
                        + ", failed=" + failed
                        + ", durationMs=" + durationMs);

        result.put("status", failed == 0 ? "SUCCESS" : "PARTIAL");
        result.put("farmersProcessed", processed);
        result.put("farmersFailed", failed);
        result.put("durationMs", durationMs);
        return result;
    }

    // ------------------------------------------------------------------
    // Per-farmer logic — the core of the plugin
    // Each step has a TODO marker pointing at the spec section that
    // describes it in detail. This keeps the skeleton compilable while
    // making the implementation roadmap explicit.
    // ------------------------------------------------------------------

    /**
     * Recompute and persist spFarmerDerived for one farmer.
     * @see _form-specs/_phase1/D1.E_FarmerDerivedRefresh.plugin.spec.yaml steps 1-9
     */
    protected void refreshOneFarmer(FormDataDao dao, String farmerCode, boolean dryRun) {
        long start = System.currentTimeMillis();

        // ---- Step 1: Load farmer data ----
        FormRow farmerBasic = loadFarmerBasic(dao, farmerCode);
        if (farmerBasic == null) {
            throw new IllegalArgumentException("Farmer not found: " + farmerCode);
        }
        String farmerBasicId = farmerBasic.getId();
        FormRow incomeRow = loadLatestIncome(dao, farmerBasicId);

        // ---- Step 2-7: Compute aggregates ----
        FormRow derived = new FormRow();
        derived.setProperty("farmerCode", farmerCode);
        derived.setProperty("nationalId", farmerBasic.getProperty("nationalId"));

        // TODO: implement step_2_compute_household_aggregates
        //       (totalHouseholdMembers, childrenUnder5/18, adultsOver65,
        //        workingAgeMembers, householdDisabilityCount, householdChronicallyIllCount,
        //        householdOrphanCount, dependencyRatio, femaleHeadedHousehold)
        computeHouseholdAggregates(dao, farmerBasicId, derived);

        // TODO: implement step_3_compute_land_aggregates
        computeLandAggregates(dao, farmerBasic, derived);

        // TODO: implement step_4_compute_livestock_aggregates
        computeLivestockAggregates(dao, farmerBasicId, derived);

        // ---- Step 5: Cross-program history ----
        computeProgramHistory(dao, farmerCode, derived);

        // ---- Step 6: Reconcile food security ----
        reconcileFoodSecurity(incomeRow, derived);

        // ---- Step 7: Vulnerability score ----
        computeVulnerabilityScore(derived);

        // Income / programme self-reported fields (carry over from incomeRow)
        if (incomeRow != null) {
            copyIfPresent(incomeRow, derived, "everOnISP");
            copyIfPresent(incomeRow, derived, "creditDefault", "inCreditDefault");
            copyIfPresent(incomeRow, derived, "mainSourceIncome", "mainIncomeSource");
            copyIfPresent(incomeRow, derived, "averageAnnualIncome");
            copyIfPresent(incomeRow, derived, "monthlyExpenditure");
        }

        // ---- Step 8: Persist ----
        String today = today();
        derived.setProperty("lastRefreshed", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        derived.setProperty("refreshSource", REFRESH_SOURCE);
        derived.setProperty("derivedSnapshotDate", today);

        if (!dryRun) {
            FormRowSet rows = new FormRowSet();
            rows.add(derived);
            // Joget upserts by FormRow.id; we use farmerCode as id-like key
            // — set explicitly to keep upsert by farmerCode
            derived.setId(farmerCode);
            dao.saveOrUpdate(FORM_DERIVED, FORM_DERIVED, rows);
        }

        long durationMs = System.currentTimeMillis() - start;
        writeAudit(dao, farmerCode, "SUCCESS", null, durationMs);
    }

    // ------------------------------------------------------------------
    // Step helpers — currently stubbed; fill in per spec
    // ------------------------------------------------------------------

    protected FormRow loadFarmerBasic(FormDataDao dao, String farmerCode) {
        // farmerCode might match either the FormRow.id OR a c_farmercode field
        // depending on how farmerBasicInfo is keyed.
        // Try ID first, fall back to query.
        FormRow row = dao.load(FORM_FARMER_BASIC, FORM_FARMER_BASIC, farmerCode);
        if (row != null) return row;
        FormRowSet rs = dao.find(FORM_FARMER_BASIC, FORM_FARMER_BASIC,
                "WHERE e.customProperties.farmerCode = ?", new Object[]{farmerCode}, null, null, null, null);
        return rs != null && !rs.isEmpty() ? rs.get(0) : null;
    }

    protected FormRow loadLatestIncome(FormDataDao dao, String farmerBasicId) {
        FormRowSet rs = dao.find(FORM_FARMER_INCOME, FORM_FARMER_INCOME,
                "WHERE e.customProperties.parent_id = ?", new Object[]{farmerBasicId},
                "dateModified", true, 0, 1);
        return rs != null && !rs.isEmpty() ? rs.get(0) : null;
    }

    protected void computeHouseholdAggregates(FormDataDao dao, String farmerBasicId, FormRow out) {
        // TODO: query farmerHousehold then householdMemberForm rows; compute aggregates.
        // Stub: leave fields null (they remain absent from the upsert).
    }

    protected void computeLandAggregates(FormDataDao dao, FormRow farmerBasic, FormRow out) {
        // TODO: query parcelRegistration WHERE farmer_id = farmerBasic.nationalId
        //       sum totalLandOwned/Rented/Available/Cultivated; compute landUtilizationRate.
    }

    protected void computeLivestockAggregates(FormDataDao dao, String farmerBasicId, FormRow out) {
        // TODO: query livestockDetailsForm WHERE parent_id linked through farmerCropsLivestock.
    }

    protected void computeProgramHistory(FormDataDao dao, String farmerCode, FormRow out) {
        FormRowSet rs = dao.find(FORM_PROG_PARTICIP, FORM_PROG_PARTICIP,
                "WHERE e.customProperties.farmerCode = ?", new Object[]{farmerCode},
                null, null, null, null);
        if (rs == null || rs.isEmpty()) {
            out.setProperty("lastBenefitDate", "");
            out.setProperty("totalBenefitsReceivedLT", "0");
            out.setProperty("programsInLast2Seasons", "0");
            out.setProperty("activeSupProgramCount", "0");
            return;
        }

        double lifetimeTotal = 0;
        String maxBenefitDate = null;
        Calendar twoSeasonsAgo = Calendar.getInstance();
        twoSeasonsAgo.add(Calendar.MONTH, -24);
        java.util.Set<String> recentPrograms = new java.util.HashSet<>();
        int activeCount = 0;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        for (FormRow row : rs) {
            // totalBenefitReceived
            try {
                String tb = (String) row.getProperty("totalBenefitReceived");
                if (tb != null && !tb.isEmpty()) lifetimeTotal += Double.parseDouble(tb);
            } catch (NumberFormatException ignored) { /* skip bad row */ }

            // lastBenefitDate
            String lb = (String) row.getProperty("lastBenefitDate");
            if (lb != null && !lb.isEmpty() && (maxBenefitDate == null || lb.compareTo(maxBenefitDate) > 0)) {
                maxBenefitDate = lb;
            }

            // enrollmentDate within last 2 seasons
            String ed = (String) row.getProperty("enrollmentDate");
            if (ed != null && !ed.isEmpty()) {
                try {
                    Date d = sdf.parse(ed);
                    if (d.after(twoSeasonsAgo.getTime())) {
                        String programCode = (String) row.getProperty("programCode");
                        if (programCode != null) recentPrograms.add(programCode);
                    }
                } catch (java.text.ParseException ignored) { /* bad date format; skip */ }
            }

            // active count
            String status = (String) row.getProperty("status");
            if ("ACTIVE".equalsIgnoreCase(status) || "ENROLLED".equalsIgnoreCase(status)) {
                activeCount++;
            }
        }

        out.setProperty("totalBenefitsReceivedLT", String.valueOf(lifetimeTotal));
        out.setProperty("lastBenefitDate", maxBenefitDate != null ? maxBenefitDate : "");
        out.setProperty("programsInLast2Seasons", String.valueOf(recentPrograms.size()));
        out.setProperty("activeSupProgramCount", String.valueOf(activeCount));
    }

    protected void reconcileFoodSecurity(FormRow incomeRow, FormRow out) {
        // Priority 1: self-reported, if recent enough
        if (incomeRow != null) {
            String selfReported = (String) incomeRow.getProperty("foodSecurityCode");
            String dateModified = (String) incomeRow.getProperty("dateModified");
            if (selfReported != null && !selfReported.isEmpty() && isRecent(dateModified, 90)) {
                out.setProperty("currentFoodSecurityStatus", selfReported);
                return;
            }
        }

        // Priority 2: derived heuristic from observable indicators
        // TODO: implement full heuristic per spec step_6_reconcile_food_security
        //   - if (vulnerabilityScore >= 75 && averageAnnualIncome < 5000) -> CRISIS
        //   - else if (vulnerabilityScore >= 50) -> SEVERE
        //   - else if (vulnerabilityScore >= 25 || lastSeasonTotalHarvest < 5) -> MODERATE
        //   - else -> SECURE
        out.setProperty("currentFoodSecurityStatus", "MODERATE");
    }

    protected void computeVulnerabilityScore(FormRow out) {
        // TODO: implement per spec step_7_compute_vulnerability
        // For now, a placeholder
        out.setProperty("vulnerabilityScore", "0");
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    protected List<String> loadAllFarmerCodes(FormDataDao dao, int staleThresholdDays) {
        List<String> codes = new ArrayList<>();
        FormRowSet rs = dao.find(FORM_FARMER_BASIC, FORM_FARMER_BASIC, null, null, null, null, null, null);
        if (rs != null) {
            for (FormRow row : rs) {
                String code = (String) row.getProperty("farmerCode");
                if (code == null || code.isEmpty()) code = row.getId();
                if (code != null) codes.add(code);
            }
        }
        // TODO: filter out farmers whose spFarmerDerived.lastRefreshed is within
        //       staleThresholdDays of today (avoid redundant refresh on scope=all)
        return codes;
    }

    protected void writeAudit(FormDataDao dao, String farmerCode, String status, String error, long durationMs) {
        // TODO: write to audit_log form (existing in this app per CLAUDE.md)
        // For now just LogUtil
        LogUtil.info(getClassName(),
                "audit: farmer=" + farmerCode + " status=" + status
                        + " ms=" + durationMs + (error != null ? " err=" + error : ""));
    }

    protected boolean isRecent(String dateString, int days) {
        if (dateString == null || dateString.isEmpty()) return false;
        try {
            // Joget dateModified is "yyyy-MM-dd HH:mm:ss"
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date d = sdf.parse(dateString);
            Calendar threshold = Calendar.getInstance();
            threshold.add(Calendar.DATE, -days);
            return d.after(threshold.getTime());
        } catch (java.text.ParseException e) {
            return false;
        }
    }

    protected String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    protected void copyIfPresent(FormRow from, FormRow to, String fromKey, String toKey) {
        Object v = from.getProperty(fromKey);
        if (v != null && !"".equals(v)) to.setProperty(toKey, v.toString());
    }

    protected void copyIfPresent(FormRow from, FormRow to, String key) {
        copyIfPresent(from, to, key, key);
    }
}
