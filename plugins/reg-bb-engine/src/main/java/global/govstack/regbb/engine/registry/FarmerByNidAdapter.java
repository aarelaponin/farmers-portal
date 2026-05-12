package global.govstack.regbb.engine.registry;

import global.govstack.regbb.engine.api.CapabilityAdapter;
import global.govstack.regbb.engine.api.EvalContext;
import org.joget.apps.app.service.AppUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Capability adapter for {@code $registry.farmer.<field>} — single-row
 * lookup against {@code app_fd_farmerbasicinfo} keyed by national_id.
 *
 * <p>Replaces the hand-coded mapping in
 * {@code SqlPathEvaluator.querySingleColumn} for the {@code farmer} entity.
 * Behaviour preserved:
 * <ul>
 *   <li>Single-row SELECT against {@code app_fd_farmerbasicinfo} keyed by national_id</li>
 *   <li>Identifier validation: field must match {@code \w+}</li>
 *   <li>Returns {@code null} when no row matches</li>
 * </ul>
 *
 * <p><b>Field aliasing.</b> Rule-author-facing field names are NOT a 1:1 map
 * onto the underlying schema — the adapter is the boundary that hides
 * column-naming idiosyncrasies from rule authors and from bot_pull
 * configurations. {@link #FIELD_TO_COLUMN_SQL} maps each whitelisted alias
 * to the SQL fragment that yields the value (a bare column or, for
 * {@code full_name}, a computed {@code TRIM(CONCAT ...)}).
 *
 * <p><b>Why no district / village.</b> Those facts live on the residency
 * tab of the farmer registry (or, today, are not yet captured at all).
 * Until the residency form has stable columns and a fan-out join is
 * justified, those aliases stay out of the whitelist — a loud failure on
 * lookup is less harmful than silent nulls leaking into Determinant rules
 * that depend on them.
 */
public class FarmerByNidAdapter implements CapabilityAdapter {

    /**
     * Whitelist + alias-to-SQL-fragment map. Restricting the keys prevents
     * accidental access to Joget metadata columns (createdBy, modifiedBy
     * etc.) and gives rule authors a clear "unknown_farmer_field:X" error
     * for typos. Each value is a SQL expression (NOT a column name) — most
     * are bare column references but {@code full_name} is computed.
     */
    private static final Map<String, String> FIELD_TO_COLUMN_SQL;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("national_id",   "c_national_id");
        m.put("first_name",    "c_first_name");
        m.put("last_name",     "c_last_name");
        // full_name: not stored on this form. Computed from first_name + last_name.
        // TRIM swallows the dangling space when one side is null/empty.
        m.put("full_name",     "TRIM(COALESCE(c_first_name,'') || ' ' || COALESCE(c_last_name,''))");
        m.put("date_of_birth", "c_date_of_birth");
        m.put("gender",        "c_gender");
        // Schema reality: contact info is c_mobile_number + c_email_address.
        // Aliases preserve the rule-author-facing names from the original
        // hand-coded mapping in SqlPathEvaluator.
        m.put("contact_phone", "c_mobile_number");
        m.put("email",         "c_email_address");
        FIELD_TO_COLUMN_SQL = Collections.unmodifiableMap(m);
    }

    @Override
    public String getCapabilityName() {
        return "farmer";
    }

    @Override
    public String resolve(String field, String nationalId, EvalContext ctx) throws SQLException {
        if (field == null || !field.matches("\\w+")) {
            throw new IllegalArgumentException("invalid_field:" + field);
        }
        String columnSql = FIELD_TO_COLUMN_SQL.get(field);
        if (columnSql == null) {
            throw new IllegalArgumentException("unknown_farmer_field:" + field
                    + " (allowed: " + FIELD_TO_COLUMN_SQL.keySet() + ")");
        }
        if (nationalId == null || nationalId.isEmpty()) {
            return null;  // no applicant anchor → no row → null
        }

        // columnSql is whitelisted (not user input) so safe to splice.
        String sql = "SELECT " + columnSql
                   + " FROM app_fd_farmerbasicinfo"
                   + " WHERE c_national_id = ? LIMIT 1";
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nationalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return rs.wasNull() ? null : v;
                }
            }
        }
        return null;
    }
}
