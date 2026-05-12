package global.govstack.parcelzonecentring.hook;

import global.govstack.parcelzonecentring.Build;

import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.util.Map;

/**
 * Post-processor that pre-populates the GIS map's auto-centre coordinates on the
 * parcelGeometry record from md_district_eco_zone whenever the (district,
 * agroEcologicalZone) pair changes.
 *
 * <p><b>Dual-mode trigger.</b> Whichever form the operator wires this on,
 * {@link #execute(Map)} works:
 * <ul>
 *   <li>Wired on <b>parcelLocation</b> (recommended for a wizard with
 *       {@code partiallyStore=true}) — fires when the citizen finishes the
 *       General Data tab. recordId is parcelLocation.id; we read the row,
 *       look up its centroid, and find-or-create the sibling parcelGeometry
 *       row (linked by parent_id).</li>
 *   <li>Wired on <b>parcelGeometry</b> (use when the wizard's tabs commit only
 *       at final Save) — fires after parcelGeometry's storeBinder. recordId
 *       is parcelGeometry.id; we read it, look up the sibling parcelLocation
 *       by parent_id, look up the centroid, then re-save parcelGeometry with
 *       the auto-centre values applied. Because we run AFTER parcelGeometry's
 *       storeBinder, our write survives.</li>
 * </ul>
 *
 * <p>Detection is by lookup: try parcelGeometry-by-id first, fall back to
 * parcelLocation-by-id. Whichever returns a row tells us the trigger context.
 *
 * <p><b>Defensive contract:</b> every code path inside {@link #execute(Map)} is
 * wrapped in a try/catch. The save itself has already committed by the time we
 * run — any error here only loses the centring convenience, never the parcel data.
 *
 * <p><b>Idempotent:</b> safe to re-run on every save. If the parcelGeometry row
 * already exists, we update its auto_center_lat/lon. If it doesn't (typical for
 * the partiallyStore=true case where General Data tab saves before the citizen
 * has reached the Geometry tab), we create a minimal stub keyed on parent_id;
 * the citizen's polygon capture will fill geometry / area / perimeter later.
 *
 * <p>Per CLAUDE.md HARD RULE: all writes go through {@link AppService#storeFormData}
 * (NOT raw FormDataDao.saveOrUpdate, which leaves NULL timestamps).
 */
public class ParcelZoneCentroidPostProcessor extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = ParcelZoneCentroidPostProcessor.class.getName();

    private static final String PARCEL_LOCATION_FORM = "parcelLocation";
    private static final String PARCEL_GEOMETRY_FORM = "parcelGeometry";
    private static final String PARCEL_GEOMETRY_TABLE = "parcelGeometry";
    private static final String ZONE_LOOKUP_FORM = "md_district_eco_zone";

    @Override public String getName()        { return "Parcel Zone Centroid Post-Processor"; }
    @Override public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override public String getLabel()       { return getName(); }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() {
        return "On parcelLocation OR parcelGeometry save, looks up the (district, "
             + "agroEcologicalZone) centroid in md_district_eco_zone and writes "
             + "auto_center_lat/lon onto the parcelGeometry row so the GIS widget "
             + "can centre the map. Wire on parcelLocation when the wizard has "
             + "partiallyStore=true (per-tab commits); wire on parcelGeometry when "
             + "the wizard saves all tabs in one shot. ["
             + Build.STAMP + "]";
    }

    @Override
    public String getPropertyOptions() {
        // No configurable properties — the plugin is hard-wired to parcelLocation /
        // parcelGeometry / md_district_eco_zone. Empty array keeps the App Composer
        // properties dialog clean.
        return "[]";
    }

    @Override
    public Object execute(Map properties) {
        // === OUTER CATCH ============================================================
        // Anything thrown inside MUST NOT propagate. The save has already committed
        // by the time we run — losing centring is acceptable, breaking save is not.
        try {

            String recordId = stringProp(properties, "recordId");
            if (recordId == null || recordId.isEmpty()) {
                LogUtil.info(CLASS_NAME, "no recordId in invocation context — skipping");
                return null;
            }

            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

            // 1. Detect which form fired us. Try parcelGeometry-by-id first, then
            //    parcelLocation-by-id. Whichever returns a row tells us the trigger
            //    context. We need this because the post-processor can be wired on
            //    EITHER form (see class Javadoc).
            FormRow geomRow = findById(dao, PARCEL_GEOMETRY_FORM, PARCEL_GEOMETRY_TABLE, recordId);
            FormRow loc     = (geomRow == null)
                    ? findById(dao, PARCEL_LOCATION_FORM, PARCEL_LOCATION_FORM, recordId)
                    : null;

            String triggerForm;
            String parentId;
            if (geomRow != null) {
                triggerForm = PARCEL_GEOMETRY_FORM;
                parentId    = nullSafeProp(geomRow, "parent_id");
            } else if (loc != null) {
                triggerForm = PARCEL_LOCATION_FORM;
                parentId    = nullSafeProp(loc, "parent_id");
            } else {
                LogUtil.info(CLASS_NAME, "recordId=" + recordId
                        + " not found in parcelGeometry OR parcelLocation — skipping"
                        + " (wrong form wired? race? deleted?)");
                return null;
            }

            if (parentId == null) {
                LogUtil.info(CLASS_NAME, triggerForm + " " + recordId
                        + " has no parent_id — skipping (orphaned record?)");
                return null;
            }

            // 2. If we don't have the parcelLocation row yet (we were triggered by
            //    parcelGeometry), find it by parent_id.
            if (loc == null) {
                loc = findOneByParentId(dao, PARCEL_LOCATION_FORM, PARCEL_LOCATION_FORM, parentId);
                if (loc == null) {
                    LogUtil.info(CLASS_NAME, "no parcelLocation found for parent_id=" + parentId
                            + " — skipping (citizen hasn't filled the General Data tab yet)");
                    return null;
                }
            }

            // Postgres folds unquoted identifiers to lowercase, so the column is
            // c_agroecologicalzone even though the form-field id is camelCase.
            // Joget's FormRow keys may come back lowercased on Postgres — try
            // both casings (CLAUDE.md "Postgres unquoted column folding" gotcha).
            String district = nullSafeProp(loc, "district");
            String zone     = firstNonNull(
                    nullSafeProp(loc, "agroEcologicalZone"),
                    nullSafeProp(loc, "agroecologicalzone"));

            if (district == null || zone == null) {
                LogUtil.info(CLASS_NAME, "parcelLocation for parent_id=" + parentId
                        + " has incomplete keys (district=" + district + ", zone=" + zone
                        + ") — skipping");
                return null;
            }

            // 3. Look up the centroid for this (district, zone) --------------------
            String centroidLat = null, centroidLon = null;
            try {
                FormRowSet zoneRows = dao.find(ZONE_LOOKUP_FORM, ZONE_LOOKUP_FORM,
                        "WHERE e.customProperties.district = ? AND e.customProperties.agro_zone = ?",
                        new Object[]{district, zone},
                        null, false, null, null);
                if (zoneRows == null || zoneRows.isEmpty()) {
                    LogUtil.info(CLASS_NAME, "no centroid in md_district_eco_zone for ("
                            + district + ", " + zone + ") — leaving auto_center_lat/lon blank;"
                            + " GIS widget will fall back to its built-in geocoding");
                    return null;
                }
                FormRow zoneRow = zoneRows.get(0);
                centroidLat = nullSafeProp(zoneRow, "centroid_lat");
                centroidLon = nullSafeProp(zoneRow, "centroid_lon");
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t, "centroid lookup failed for ("
                        + district + ", " + zone + ")");
                return null;
            }

            if (centroidLat == null || centroidLon == null) {
                LogUtil.info(CLASS_NAME, "centroid row found but lat/lon missing for ("
                        + district + ", " + zone + ") — skipping");
                return null;
            }

            // 4. Find or create the parcelGeometry row to update. We may already
            //    have it (if we were triggered by parcelGeometry) or we may need
            //    to look it up by parent_id (if triggered by parcelLocation in a
            //    partiallyStore=true wizard, where the citizen hasn't reached the
            //    Geometry tab yet).
            String geomId;
            boolean isNew = false;
            if (geomRow == null) {
                geomRow = findOneByParentId(dao, PARCEL_GEOMETRY_FORM, PARCEL_GEOMETRY_TABLE, parentId);
                if (geomRow == null) {
                    geomRow = new FormRow();
                    geomId = UuidGenerator.getInstance().getUuid();
                    geomRow.setId(geomId);
                    geomRow.setProperty("parent_id", parentId);
                    isNew = true;
                } else {
                    geomId = geomRow.getId();
                }
            } else {
                geomId = geomRow.getId();
            }

            // 5. Set the centring fields. The full FormRow (including any existing
            //    geometry / area / perimeter / vertex_count) gets re-saved, so
            //    existing data is preserved. When triggered by parcelGeometry we
            //    run AFTER its storeBinder, so our write is the LAST write — it
            //    survives. When triggered by parcelLocation, parcelGeometry's
            //    storeBinder hasn't run for this save cycle (different tab), so
            //    no race.
            geomRow.setProperty("auto_center_lat", centroidLat);
            geomRow.setProperty("auto_center_lon", centroidLon);

            try {
                FormRowSet rs = new FormRowSet();
                rs.add(geomRow);
                appService.storeFormData(PARCEL_GEOMETRY_FORM, PARCEL_GEOMETRY_TABLE, rs, geomId);

                LogUtil.info(CLASS_NAME, (isNew ? "created" : "updated")
                        + " parcelGeometry " + geomId
                        + " (trigger=" + triggerForm
                        + ", parent_id=" + parentId
                        + ", district=" + district
                        + ", zone=" + zone
                        + ", centroid=" + centroidLat + "," + centroidLon + ")");
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t, "failed storing parcelGeometry " + geomId);
                return null;
            }

        } catch (Throwable t) {
            // Outer safety net. If we got here, something we didn't anticipate broke.
            LogUtil.error(CLASS_NAME, t,
                    "ParcelZoneCentroidPostProcessor outer failure: " + t.getMessage());
        }
        return null;
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private static String stringProp(Map properties, String key) {
        if (properties == null) return null;
        Object v = properties.get(key);
        return v == null ? null : v.toString();
    }

    private static String nullSafeProp(FormRow row, String key) {
        if (row == null) return null;
        String v = row.getProperty(key);
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** Returns the first non-null argument, or null if all are null. */
    private static String firstNonNull(String... vs) {
        if (vs == null) return null;
        for (String v : vs) {
            if (v != null) return v;
        }
        return null;
    }

    /** Look up a single row by primary key, swallowing any DAO error. */
    private static FormRow findById(FormDataDao dao, String formId, String tableName, String id) {
        try {
            FormRowSet rs = dao.find(formId, tableName,
                    "WHERE e.id = ?", new Object[]{id},
                    null, false, null, null);
            return (rs != null && !rs.isEmpty()) ? rs.get(0) : null;
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "findById failed for "
                    + formId + " id=" + id);
            return null;
        }
    }

    /** Look up the first row whose parent_id matches, swallowing any DAO error. */
    private static FormRow findOneByParentId(FormDataDao dao, String formId, String tableName, String parentId) {
        try {
            FormRowSet rs = dao.find(formId, tableName,
                    "WHERE e.customProperties.parent_id = ?", new Object[]{parentId},
                    null, false, null, null);
            return (rs != null && !rs.isEmpty()) ? rs.get(0) : null;
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "findOneByParentId failed for "
                    + formId + " parent_id=" + parentId);
            return null;
        }
    }
}
