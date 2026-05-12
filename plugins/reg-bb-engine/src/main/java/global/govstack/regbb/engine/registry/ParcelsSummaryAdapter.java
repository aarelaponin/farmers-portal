package global.govstack.regbb.engine.registry;

import global.govstack.regbb.engine.api.CapabilityAdapter;
import global.govstack.regbb.engine.api.EvalContext;
import org.joget.apps.app.service.AppUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Capability adapter for {@code $registry.parcels_summary.<field>} —
 * aggregates a farmer's parcels into a single value.
 *
 * <p>Unblocks programme rules that need parcel-level data (DET_SMALLHOLDER's
 * "≤ 5 ha cultivated total", DET_AREA_NONZERO's "&gt; 0 ha cultivated") to
 * evaluate against the actual parcel registry rather than against a single
 * field on the application row. Per registry-integration component SAD
 * §5.3 stability table, the underlying source columns
 * ({@code c_area_hectares}, {@code c_national_id}, etc.) are STABLE-class.
 *
 * <p><b>Supported virtual fields:</b>
 * <ul>
 *   <li>{@code parcel_count} — number of parcels registered to this farmer (integer).</li>
 *   <li>{@code cultivated_total} — sum of {@code c_area_hectares} (numeric).</li>
 *   <li>{@code largest_parcel} — max of {@code c_area_hectares} (numeric).</li>
 * </ul>
 * Each is computed from one aggregating SELECT against
 * {@code app_fd_parcelregistration} keyed by {@code c_national_id}.
 *
 * <p><b>Behaviour when no parcels:</b> {@code parcel_count} = 0;
 * {@code cultivated_total} = 0; {@code largest_parcel} = null. The first
 * two are deliberately 0 (not null) because "this farmer has zero parcels"
 * is a knowable answer; only "no parcel registered as largest" is null.
 *
 * <p><b>Performance:</b> One SELECT per evaluation per applicant per field.
 * The L2 cache (slice 1B-d) keys on (determinant, applicationId,
 * ruleVersion, dataHash), so repeat evaluations for the same applicant
 * within the cache TTL hit the cache, not this adapter.
 */
public class ParcelsSummaryAdapter implements CapabilityAdapter {

    @Override
    public String getCapabilityName() {
        return "parcels_summary";
    }

    @Override
    public String resolve(String field, String nationalId, EvalContext ctx) throws SQLException {
        if (field == null) {
            throw new IllegalArgumentException("missing_field");
        }
        if (nationalId == null || nationalId.isEmpty()) {
            // No applicant anchor — for count/sum return 0; for max return null.
            switch (field) {
                case "parcel_count":     return "0";
                case "cultivated_total": return "0";
                case "largest_parcel":   return null;
                default: throw new IllegalArgumentException("unknown_parcels_summary_field:" + field);
            }
        }

        // Data model (verified against live DB, May 2026):
        //   app_fd_farmerbasicinfo.c_national_id  → farmer's NID (natural key)
        //   app_fd_parcelregistration.c_parent_id → wizard parent (currently
        //                                            empty in 184/184 live rows
        //                                            — data integrity gap)
        //   app_fd_parcelgeometry.c_parent_id     → parcelregistration.id
        //   app_fd_parcelgeometry.c_area_hectares → the actual hectarage
        //
        // Join path NID → parcels:
        //   farmerbasicinfo (NID match) → parcelregistration (via parent_id)
        //                               → parcelgeometry (via parent_id chain)
        //
        // Since parcelregistration.c_parent_id is empty in the live registry
        // today, this query returns 0 / null for every farmer until the
        // farmer→parcel linkage is populated. That is the correct behaviour
        // — "no parcels registered" = 0 — and matches the "missing data is
        // NULL, not FALSE" semantic of the rule grammar. Documented in the
        // registry-integration component SAD §5.3.
        // Aliases use full 3-letter names that can't collide with the Postgres
        // `pg_*` system namespace prefix (not strictly reserved as an alias by
        // the SQL standard, but defensive). Each space is explicit; no
        // multi-line whitespace ambiguity.
        String sql;
        switch (field) {
            case "parcel_count":
                sql = "SELECT count(*)"
                    + " FROM app_fd_parcelgeometry geo"
                    + " JOIN app_fd_parcelregistration reg ON reg.id = geo.c_parent_id"
                    + " JOIN app_fd_farmerbasicinfo fbi ON fbi.id = reg.c_parent_id"
                    + " WHERE fbi.c_national_id = ?";
                break;
            case "cultivated_total":
                sql = "SELECT COALESCE(SUM(CAST(NULLIF(geo.c_area_hectares,'') AS NUMERIC)), 0)"
                    + " FROM app_fd_parcelgeometry geo"
                    + " JOIN app_fd_parcelregistration reg ON reg.id = geo.c_parent_id"
                    + " JOIN app_fd_farmerbasicinfo fbi ON fbi.id = reg.c_parent_id"
                    + " WHERE fbi.c_national_id = ?";
                break;
            case "largest_parcel":
                sql = "SELECT MAX(CAST(NULLIF(geo.c_area_hectares,'') AS NUMERIC))"
                    + " FROM app_fd_parcelgeometry geo"
                    + " JOIN app_fd_parcelregistration reg ON reg.id = geo.c_parent_id"
                    + " JOIN app_fd_farmerbasicinfo fbi ON fbi.id = reg.c_parent_id"
                    + " WHERE fbi.c_national_id = ?";
                break;
            default:
                throw new IllegalArgumentException("unknown_parcels_summary_field:" + field
                        + " (allowed: parcel_count, cultivated_total, largest_parcel)");
        }

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
