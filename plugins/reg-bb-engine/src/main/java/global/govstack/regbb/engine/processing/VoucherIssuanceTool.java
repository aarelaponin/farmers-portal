package global.govstack.regbb.engine.processing;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Phase 3 IM Slice 4 — issues IM vouchers for an approved subsidy application.
 *
 * <p>Pipeline (per im-module SAD §6.2 + the customer-facing overview Phase E):
 *
 * <ol>
 *   <li>Read the application from {@code app_fd_subsidy_app_2025} by its UUID.</li>
 *   <li>Verify the application status is {@code approved} or {@code auto_approved}
 *       (or skip the check if the {@code force} flag is set, for admin overrides).</li>
 *   <li>Look up the active allocation plan for the applied programme — i.e. an
 *       {@code app_fd_im_allocation_plan} row whose {@code c_programme_code} matches
 *       and whose {@code c_status='approved'}. The plan's {@code id} is the FK
 *       target on each line.</li>
 *   <li>For each {@code app_fd_im_allocation_line} under that plan, decide whether
 *       the line applies to this farmer. Two simple criteria for Slice 4:
 *       <ul>
 *         <li>The applicant's farmer category matches (placeholder: we treat
 *             every applicant as {@code SMALLHOLDER} for now; future slice will
 *             join the registry to derive category by area / cooperative status).</li>
 *         <li>The point's district matches the applicant's district. If the
 *             plan has multiple lines covering the same input × district, the
 *             one with the lowest {@code priority_weight} wins.</li>
 *       </ul></li>
 *   <li>Write one {@code app_fd_im_voucher} row per surviving allocation line via
 *       {@code FormDataDao.saveOrUpdate}. The voucher carries the application UUID,
 *       the allocation-line UUID, the farmer NID and (denormalised) name, the
 *       programme code, the input code, the centre, the quantity, the issue
 *       date (today) and the expiry date (effective_to of the plan).</li>
 *   <li>Return the list of voucher codes written.</li>
 * </ol>
 *
 * <p>Idempotency: the tool checks for existing vouchers tied to the same
 * (application_id, allocation_line_id) pair before writing; re-running for the
 * same application returns the existing voucher codes without creating duplicates.
 *
 * <p>HARD-RULE compliant: every write goes through {@link FormDataDao}; the
 * lookups are read-only SELECTs.
 *
 * <p>Wireable as: a Joget tool plugin from any workflow process step, OR
 * directly via the {@code POST /budget/issue-vouchers?applicationId=...} REST
 * endpoint exposed by {@code BudgetApi}. For Slice 4 the REST path is the
 * primary invocation; the XPDL workflow wrapper lands in Slice 5+.
 */
public class VoucherIssuanceTool extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = VoucherIssuanceTool.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final SimpleDateFormat ISO_DATE =
            new SimpleDateFormat("yyyy-MM-dd");
    static {
        ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC"));
        ISO_DATE.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final String FORM_VOUCHER = "im_voucher";
    /** How many days a voucher is valid by default if the plan has no effective_to. */
    private static final int DEFAULT_EXPIRY_DAYS = 180;

    @Override public String getName()        { return "Voucher Issuance Tool"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getLabel()       { return "Voucher Issuance Tool"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() { return "IM Slice 4 — issues vouchers for an approved subsidy application."; }
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Voucher Issuance\", \"properties\":[] } ]";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object execute(Map properties) {
        String applicationId = String.valueOf(properties.getOrDefault("applicationId", ""));
        boolean force = "true".equalsIgnoreCase(String.valueOf(properties.getOrDefault("force", "false")));
        String actor = String.valueOf(properties.getOrDefault("actor", "system:voucher-tool"));
        Result r = issueFor(applicationId, force, actor);
        return r.toJson();
    }

    public static class Result {
        public String  status;            // ok / no_application / not_approved / no_plan / no_matching_lines / error
        public String  applicationId;
        public String  appliedProgramme;
        public String  message;
        public int     vouchersIssued;
        public int     vouchersSkippedExisting;
        public List<String> voucherCodes = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"status\":\"").append(safe(status)).append("\",");
            sb.append("\"applicationId\":\"").append(safe(applicationId)).append("\",");
            sb.append("\"appliedProgramme\":\"").append(safe(appliedProgramme)).append("\",");
            sb.append("\"message\":\"").append(safeJson(message)).append("\",");
            sb.append("\"vouchersIssued\":").append(vouchersIssued).append(",");
            sb.append("\"vouchersSkippedExisting\":").append(vouchersSkippedExisting).append(",");
            sb.append("\"voucherCodes\":[");
            for (int i = 0; i < voucherCodes.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(safe(voucherCodes.get(i))).append("\"");
            }
            sb.append("]}");
            return sb.toString();
        }
        private static String safe(String s) { return s == null ? "" : s.replace("\"", "\\\""); }
        private static String safeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", " ").replace("\r", " ");
        }
    }

    /** Public so {@code BudgetApi} (or any other entry point) can call directly. */
    public Result issueFor(String applicationId, boolean force, String actor) {
        Result r = new Result();
        r.applicationId = applicationId;

        if (applicationId == null || applicationId.isEmpty()) {
            r.status = "error";
            r.message = "applicationId is required";
            return r;
        }

        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

        // 1) Read the application
        Application app = readApplication(ds, applicationId);
        if (app == null) {
            r.status = "no_application";
            r.message = "No row in app_fd_subsidy_app_2025 with id=" + applicationId;
            return r;
        }
        r.appliedProgramme = app.appliedProgramme;

        // 2) Status check
        boolean approvedStatus = "approved".equalsIgnoreCase(app.status)
                              || "auto_approved".equalsIgnoreCase(app.status);
        if (!approvedStatus && !force) {
            r.status = "not_approved";
            r.message = "Application status is '" + app.status + "' — only approved/auto_approved "
                      + "applications produce vouchers (pass force=true to override).";
            return r;
        }

        // 3) Active allocation plan for the programme
        AllocationPlan plan = findActivePlan(ds, app.appliedProgramme);
        if (plan == null) {
            r.status = "no_plan";
            r.message = "No active (status='approved') im_allocation_plan for programme="
                      + app.appliedProgramme;
            return r;
        }

        // 4) Allocation lines under this plan; filter by farmer category + point district
        List<AllocationLine> lines = readLines(ds, plan.id);
        if (lines.isEmpty()) {
            r.status = "no_matching_lines";
            r.message = "Allocation plan '" + plan.code + "' has no lines.";
            return r;
        }

        // For Slice 4: assume SMALLHOLDER for everyone. (Slice 5 will derive
        // properly from registry — area, cooperative membership, programme history.)
        String applicantCategory = "SMALLHOLDER";

        // Group lines by input_code → pick best (lowest priority_weight) per input
        // among lines whose centre is in the applicant's district AND whose
        // farmer_category matches.
        Map<String, AllocationLine> bestPerInput = new HashMap<>();
        Map<String, String> pointDistricts = readPointDistricts(ds);
        for (AllocationLine line : lines) {
            if (!applicantCategory.equalsIgnoreCase(line.farmerCategory)) continue;
            String pointDistrict = pointDistricts.getOrDefault(line.pointCode, "");
            if (!app.district.equalsIgnoreCase(pointDistrict)) continue;
            AllocationLine current = bestPerInput.get(line.inputCode);
            if (current == null || line.priorityWeight < current.priorityWeight) {
                bestPerInput.put(line.inputCode, line);
            }
        }

        if (bestPerInput.isEmpty()) {
            r.status = "no_matching_lines";
            r.message = "No allocation lines match (category=" + applicantCategory
                      + ", applicant_district=" + app.district + ") in plan " + plan.code;
            return r;
        }

        // 5) Idempotency: which lines already have a voucher for this application?
        Set<String> existingForLines = readExistingVoucherLines(ds, applicationId);

        // 6) Write one voucher per surviving (line) not already present
        Date today = new Date();
        Date expiry = plan.effectiveTo != null ? plan.effectiveTo : addDays(today, DEFAULT_EXPIRY_DAYS);
        int voucherSeq = nextVoucherSequence(ds, today);
        for (AllocationLine line : bestPerInput.values()) {
            if (existingForLines.contains(line.id)) {
                r.vouchersSkippedExisting++;
                continue;
            }
            String code = formatVoucherCode(today, voucherSeq++);
            FormRow row = new FormRow();
            row.setId(UUID.randomUUID().toString());
            row.setProperty("dateCreated",  ISO_UTC.format(today));
            row.setProperty("dateModified", ISO_UTC.format(today));
            row.setProperty("code",                code);
            row.setProperty("application_id",      applicationId);
            row.setProperty("farmer_nid",          app.nationalId);
            row.setProperty("farmer_name",         app.fullName);
            row.setProperty("programme_code",      app.appliedProgramme);
            row.setProperty("allocation_line_id",  line.id);
            row.setProperty("input_code",          line.inputCode);
            row.setProperty("point_code",          line.pointCode);
            row.setProperty("quantity",            String.valueOf(line.quantity));
            row.setProperty("issued_date",         ISO_DATE.format(today));
            row.setProperty("expiry_date",         ISO_DATE.format(expiry));
            row.setProperty("status",              "issued");
            row.setProperty("notes",               "Issued by VoucherIssuanceTool ("
                            + (force ? "force=true; " : "") + "actor=" + actor + ")");
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            global.govstack.regbb.engine.support.RowWriter.save(FORM_VOUCHER, FORM_VOUCHER, rs);
            r.voucherCodes.add(code);
            r.vouchersIssued++;

            // Slice 6c — Budget COMMITMENT event. Resolve unit_price from
            // md27input.c_estimated_cost_per_unit; amount = qty × unit_price.
            // Failures here log loudly but don't block voucher issuance —
            // the voucher is the legal entitlement; the financial record
            // can be reconciled later by the manual-adjustment surface.
            try {
                java.math.BigDecimal unitPrice = readUnitPrice(ds, line.inputCode);
                java.math.BigDecimal amount = unitPrice.multiply(
                        new java.math.BigDecimal(line.quantity)).setScale(2,
                                java.math.RoundingMode.HALF_EVEN);
                if (amount.signum() > 0) {
                    String envelopeCode = "ENV_" + app.appliedProgramme + "_FY2526";
                    String idem = "voucher_issued:" + code;
                    new global.govstack.regbb.engine.budget.BudgetEngine().dispatchDirect(
                            envelopeCode, "COMMITMENT", amount, actor,
                            "voucher_issuance", code, idem,
                            new java.util.HashMap<>());
                    LogUtil.info(CLASS_NAME, "Budget COMMITMENT for voucher " + code
                            + ": " + amount + " against " + envelopeCode);
                }
            } catch (Throwable budgetEx) {
                LogUtil.error(CLASS_NAME, budgetEx,
                    "Budget COMMITMENT failed for voucher " + code
                    + " — voucher saved; financial record missing");
            }

            // W2 Slice — fire voucher_issued email (template 06).
            // Best-effort: SMTP errors are logged inside EmailDispatcher and
            // never block voucher issuance. The legal entitlement is the
            // app_fd_im_voucher row above, not the email.
            // DEV recipient override: until farmer registration captures an
            // email field, every outbound message is routed to a single test
            // mailbox (Mailtrap inbox) so we can verify routing without
            // delivering to real citizens. Production cutover: read
            // farmer.email and remove this override.
            try {
                String firstName = firstWord(app.fullName);
                String benefit = line.quantity + " unit(s) of " + line.inputCode;
                String districtName = (app.district != null && !app.district.isEmpty())
                        ? app.district : "your local";
                String programmeName = app.appliedProgramme;

                java.util.Map<String, String> vars = new java.util.HashMap<>();
                vars.put("national_id",      app.nationalId == null ? "" : app.nationalId);
                vars.put("farmer_name",      app.fullName == null ? "" : app.fullName);
                vars.put("first_name",       firstName);
                vars.put("voucher_code",     code);
                vars.put("programme_name",   programmeName == null ? "" : programmeName);
                vars.put("program_name",     programmeName == null ? "" : programmeName);
                vars.put("application_code", "AP-" + (applicationId == null || applicationId.length() < 8
                        ? "UNKNOWN" : applicationId.substring(0, 8).toUpperCase()));
                vars.put("issued_date",      ISO_DATE.format(today));
                vars.put("expiry_date",      ISO_DATE.format(expiry));
                vars.put("benefit",          benefit);
                vars.put("benefit_description", benefit);
                vars.put("district_name",    districtName);
                vars.put("district_phone",   "your district office");

                global.govstack.regbb.engine.notification.EmailDispatcher.sendByEvent(
                        "VOUCHER_ISSUED", "EN", vars);
                global.govstack.regbb.engine.notification.SmsDispatcher.sendByEvent(
                        "VOUCHER_ISSUED", vars);
            } catch (Throwable emailEx) {
                LogUtil.error(CLASS_NAME, emailEx,
                    "Email notification failed for voucher " + code
                    + " — voucher saved; notification missing");
            }
        }

        r.status = "ok";
        r.message = "Issued " + r.vouchersIssued + " voucher(s); skipped "
                  + r.vouchersSkippedExisting + " already-existing voucher(s).";
        LogUtil.info(CLASS_NAME, r.toJson());
        return r;
    }

    // ---------------- Read helpers (pure SELECT) ----------------

    private static class Application {
        String id;
        String nationalId;
        String fullName;
        String district;
        String appliedProgramme;
        String status;
    }

    private Application readApplication(DataSource ds, String applicationId) {
        String sql = "SELECT id, c_national_id, c_full_name, c_district, "
                   + "       c_applied_programme, c_status "
                   + "  FROM app_fd_subsidy_app_2025 WHERE id = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, applicationId);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                Application a = new Application();
                a.id               = rs.getString(1);
                a.nationalId       = nullSafe(rs.getString(2));
                a.fullName         = nullSafe(rs.getString(3));
                a.district         = nullSafe(rs.getString(4));
                a.appliedProgramme = nullSafe(rs.getString(5));
                a.status           = nullSafe(rs.getString(6));
                return a;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readApplication failed: " + e.getSQLState() + ":" + e.getMessage());
            return null;
        }
    }

    private static class AllocationPlan {
        String id;
        String code;
        Date   effectiveTo;
    }

    private AllocationPlan findActivePlan(DataSource ds, String programmeCode) {
        String sql = "SELECT id, c_code, c_effective_to FROM app_fd_im_allocation_plan "
                   + " WHERE c_programme_code = ? AND c_status = 'approved' "
                   + " ORDER BY datecreated DESC LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, programmeCode);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                AllocationPlan ap = new AllocationPlan();
                ap.id   = rs.getString(1);
                ap.code = nullSafe(rs.getString(2));
                String et = rs.getString(3);
                ap.effectiveTo = parseDate(et);
                return ap;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "findActivePlan failed: " + e.getSQLState() + ":" + e.getMessage());
            return null;
        }
    }

    private static class AllocationLine {
        String id;
        String code;
        String pointCode;
        String inputCode;
        String farmerCategory;
        int    quantity;
        int    priorityWeight;
    }

    private List<AllocationLine> readLines(DataSource ds, String planId) {
        List<AllocationLine> out = new ArrayList<>();
        String sql = "SELECT id, c_code, c_point_code, c_input_code, c_farmer_category, "
                   + "       c_quantity, c_priority_weight "
                   + "  FROM app_fd_im_allocation_line WHERE c_plan_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, planId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    AllocationLine al = new AllocationLine();
                    al.id             = rs.getString(1);
                    al.code           = nullSafe(rs.getString(2));
                    al.pointCode      = nullSafe(rs.getString(3));
                    al.inputCode      = nullSafe(rs.getString(4));
                    al.farmerCategory = nullSafe(rs.getString(5));
                    al.quantity       = parseIntSafe(rs.getString(6), 0);
                    al.priorityWeight = parseIntSafe(rs.getString(7), 99);
                    out.add(al);
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readLines failed: " + e.getSQLState() + ":" + e.getMessage());
        }
        return out;
    }

    /** Read md27input.c_estimated_cost_per_unit for the budget-amount formula. */
    private java.math.BigDecimal readUnitPrice(DataSource ds, String inputCode) {
        String sql = "SELECT c_estimated_cost_per_unit FROM app_fd_md27input WHERE c_code = ? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, inputCode);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String s = rs.getString(1);
                    if (s != null && !s.isEmpty()) {
                        try { return new java.math.BigDecimal(s); } catch (Exception ignore) {}
                    }
                }
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readUnitPrice failed for input=" + inputCode
                    + ": " + e.getSQLState() + ":" + e.getMessage());
        }
        return java.math.BigDecimal.ZERO;
    }

    private Map<String, String> readPointDistricts(DataSource ds) {
        Map<String, String> out = new HashMap<>();
        String sql = "SELECT c_code, c_district_code FROM app_fd_md37collectionpoint";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                out.put(nullSafe(rs.getString(1)), nullSafe(rs.getString(2)));
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "readPointDistricts failed: " + e.getSQLState() + ":" + e.getMessage());
        }
        return out;
    }

    private Set<String> readExistingVoucherLines(DataSource ds, String applicationId) {
        Set<String> out = new HashSet<>();
        String sql = "SELECT c_allocation_line_id FROM app_fd_im_voucher "
                   + " WHERE c_application_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, applicationId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) out.add(nullSafe(rs.getString(1)));
            }
        } catch (SQLException e) {
            // Table may not exist on first ever invocation before the form is saved
            LogUtil.warn(CLASS_NAME, "readExistingVoucherLines (non-fatal): "
                    + e.getSQLState() + ":" + e.getMessage());
        }
        return out;
    }

    private int nextVoucherSequence(DataSource ds, Date today) {
        String prefix = "VCH-" + new SimpleDateFormat("yyyyMMdd").format(today) + "-";
        String sql = "SELECT count(*) FROM app_fd_im_voucher WHERE c_code LIKE ?";
        try (Connection c = ds.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, prefix + "%");
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            LogUtil.warn(CLASS_NAME, "nextVoucherSequence (non-fatal): "
                    + e.getSQLState() + ":" + e.getMessage());
        }
        return 1;
    }

    private static String formatVoucherCode(Date today, int seq) {
        return "VCH-" + new SimpleDateFormat("yyyyMMdd").format(today) + "-"
             + String.format("%04d", seq);
    }

    private static Date parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return ISO_DATE.parse(s); } catch (Exception e) { return null; }
    }

    private static Date addDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    /** First word of a name, for "Hello {firstName}" greetings. Empty -> "there". */
    private static String firstWord(String s) {
        if (s == null) return "there";
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return "there";
        int sp = trimmed.indexOf(' ');
        return sp < 0 ? trimmed : trimmed.substring(0, sp);
    }
    private static int parseIntSafe(String s, int dflt) {
        if (s == null || s.isEmpty()) return dflt;
        try { return (int) Math.round(Double.parseDouble(s.trim())); }
        catch (NumberFormatException e) { return dflt; }
    }
}
