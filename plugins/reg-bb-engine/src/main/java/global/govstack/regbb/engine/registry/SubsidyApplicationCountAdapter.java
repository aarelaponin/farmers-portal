package global.govstack.regbb.engine.registry;

import global.govstack.regbb.engine.api.CapabilityAdapter;
import global.govstack.regbb.engine.api.EvalContext;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Capability adapter for {@code $registry.subsidy.<field>} — counts a
 * given applicant's existing subsidy applications, scoped by the
 * programme they are currently applying for.
 *
 * <p>Created for L4-1 D-003: without this capability, the same applicant
 * could submit the same programme twice (and both would auto-approve).
 * Per RegBB §6.5, an applicant should be limited to one application per
 * (registration, period) — but the spec doesn't say HOW. The capability
 * registry pattern (RegBB §3.2 / ADR-020) is the right place: a tiny
 * adapter that lets rule authors express "no prior application" as a
 * Determinant rule, without coupling it to any particular programme.
 *
 * <p><b>Supported virtual fields:</b>
 * <ul>
 *   <li>{@code prior_applications_this_programme} — count of OTHER
 *       application rows with the same {@code c_national_id} AND the
 *       same {@code c_applied_programme} as the current evaluation
 *       context. Excludes the current application's row (looked up by
 *       {@link EvalContext#applicationId}) so an applicant can save and
 *       re-evaluate their own draft without false duplicate signals.
 *   </li>
 * </ul>
 *
 * <p><b>Why not "this period"?</b> The acceptance window for each
 * registration is defined on {@code mm_registration.acceptanceWindowFrom/To}.
 * In practice a registration code (e.g. {@code PRG_2025_001}) is unique to
 * one acceptance window — when the 2026/27 cycle ships, the new code is
 * {@code PRG_2026_001}, not a re-use. So scoping by programme code is
 * equivalent to scoping by period. If a future programme genuinely reuses
 * a code across cycles, this adapter needs a {@code period_window}
 * argument; for now the simpler scope-by-code is sufficient and matches
 * the actual deployment pattern.
 *
 * <p><b>Why we read {@code applied_programme} from {@code ctx.data}.</b>
 * The duplicate check has to know which programme is being applied for,
 * not just the applicant. The capability-adapter signature
 * {@code resolve(field, nationalId, ctx)} gives us the NID and ctx; the
 * applied_programme lives in {@code ctx.data} (populated from the
 * application form's {@code applied_programme} field by the storeBinder
 * before eligibility evaluation). Returning -1 if the programme code
 * isn't available in the context would misclassify; we return null
 * instead and let the rule's null-propagation surface as
 * disposition=indeterminate per ADR-007 — operators see the actual gap.
 */
public class SubsidyApplicationCountAdapter implements CapabilityAdapter {

    private static final String CLASS_NAME = SubsidyApplicationCountAdapter.class.getName();

    @Override
    public String getCapabilityName() {
        return "subsidy";
    }

    @Override
    public String resolve(String field, String nationalId, EvalContext ctx) throws SQLException {
        if (field == null) {
            throw new IllegalArgumentException("missing_field");
        }
        if (!"prior_applications_this_programme".equals(field)) {
            throw new IllegalArgumentException("unknown_subsidy_field:" + field
                    + " (allowed: prior_applications_this_programme)");
        }
        if (nationalId == null || nationalId.isEmpty()) {
            // No applicant anchor → can't query. Returning "0" rather than
            // null is deliberate: "no applicant" can't have prior
            // applications, so 0 is the truthful count. Returning null
            // would propagate to indeterminate, which would misclassify
            // applicants who happen to lack a national_id.
            return "0";
        }
        String appliedProgramme = readAppliedProgramme(ctx);
        if (appliedProgramme == null || appliedProgramme.isEmpty()) {
            // No programme in context → can't scope the count. Surface as
            // null so the rule yields indeterminate; operators see "we
            // couldn't tell which programme" rather than a wrong "0 prior
            // applications" answer that masks the gap.
            LogUtil.warn(CLASS_NAME, "resolve: applied_programme missing from EvalContext.data; "
                    + "cannot scope duplicate-count query — returning null (→ indeterminate)");
            return null;
        }

        // Exclude the current application by id when present, so an
        // applicant editing their own (saved) draft and re-triggering
        // eligibility doesn't see a false-duplicate. ctx.applicationId is
        // null/empty in fresh ?_mode=add submits before the row exists,
        // and is the saved UUID once it does.
        String currentAppId = (ctx != null && ctx.applicationId != null) ? ctx.applicationId : "";

        String sql = "SELECT count(*) FROM app_fd_subsidy_app_2025"
                   + " WHERE c_national_id = ?"
                   + "   AND c_applied_programme = ?"
                   + "   AND id <> ?";
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nationalId);
            stmt.setString(2, appliedProgramme);
            stmt.setString(3, currentAppId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return rs.wasNull() ? "0" : v;
                }
            }
        }
        return "0";
    }

    /** Read applied_programme from EvalContext.data — case-insensitive,
     *  with the camelCase variant tried as a fallback (Joget rows can
     *  fold to lowercase on Postgres). */
    private static String readAppliedProgramme(EvalContext ctx) {
        if (ctx == null || ctx.data == null) return null;
        Object v = ctx.data.get("applied_programme");
        if (v == null) v = ctx.data.get("appliedProgramme");
        return v == null ? null : v.toString();
    }
}
