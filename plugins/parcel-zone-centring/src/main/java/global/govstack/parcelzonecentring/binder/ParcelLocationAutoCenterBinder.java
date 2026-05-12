package global.govstack.parcelzonecentring.binder;

import global.govstack.parcelzonecentring.Build;

import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;

/**
 * Custom storeBinder for the parcelLocation form. Replaces the stock
 * {@link WorkflowFormBinder} and adds a sibling-write step inline with the
 * parcelLocation save.
 *
 * <p><b>The save lifecycle (verified against jw-community 8.x source):</b>
 * <ol>
 *   <li>Citizen submits parcelLocation (either as a per-tab Next click with
 *       {@code partiallyStore=true} or as part of a wizard-level Save).</li>
 *   <li>{@code FormServiceImpl.recursiveExecuteFormStoreBinders} (line ~572)
 *       invokes this binder's {@link #store} inside the same {@code @Transactional}
 *       boundary as the wizard's submit.</li>
 *   <li>We call {@code super.store(...)} first — this is the stock
 *       WorkflowFormBinder behaviour: persist the parcelLocation row via
 *       {@link AppService#storeFormData} and update workflow variables. We do
 *       NOT change that contract.</li>
 *   <li>Then we cascade: look up the (district, agroEcologicalZone) centroid
 *       from md_district_eco_zone, find-or-create the sibling parcelGeometry
 *       row (linked by parent_id), and write
 *       {@code auto_center_lat / auto_center_lon} via
 *       {@link AppService#storeFormData}. Same transaction, same JVM call
 *       chain, no race.</li>
 * </ol>
 *
 * <p><b>Why this works where a post-processor doesn't.</b> Joget's
 * {@code FormUtil.executePostFormSubmissionProccessor} fires from exactly one
 * place — {@code AppServiceImpl.submitForm} line 1985 — and only at the wizard's
 * top-level submit, never on per-tab subform commits. With
 * {@code partiallyStore=true}, the wizard's tabs save individually via
 * {@code storeFormData} which never invokes the post-processor. The parent
 * wizard's post-processor slot is also already occupied by FormQualityPostProcessor.
 * A custom storeBinder is the only extension point that runs inline on every
 * commit of the parent form, regardless of mode.
 *
 * <p><b>Defensive contract.</b> The cascade step is wrapped in a try/catch.
 * If looking up the centroid or writing parcelGeometry fails, we log and
 * swallow — losing the centring convenience is acceptable, breaking the
 * citizen's parcelLocation save is not.
 *
 * <p><b>Idempotent.</b> Safe to re-run on every save. If the parcelGeometry row
 * already exists, we update its auto_center_lat/lon. If it doesn't (typical for
 * the {@code partiallyStore=true} flow where the citizen has finished the
 * General Data tab but not yet visited the Location tab), we create a minimal
 * stub keyed on parent_id; the GIS widget will render the map centred on the
 * (district, zone) centroid when the citizen first opens the Location tab.
 *
 * <p><b>Reference.</b> Pattern verified against
 * {@code api-builder/apibuilder_plugins/.../ApiKeyFormBinder.java:120-178}, which
 * is a production custom FormStoreBinder that combines the parent's commit with
 * a write to a separate persistence target inside the same call.
 */
public class ParcelLocationAutoCenterBinder extends WorkflowFormBinder {

    private static final String CLASS_NAME = ParcelLocationAutoCenterBinder.class.getName();

    private static final String PARCEL_LOCATION_FORM = "parcelLocation";
    private static final String PARCEL_LOCATION_TABLE = "parcelLocation";
    private static final String PARCEL_GEOMETRY_FORM = "parcelGeometry";
    private static final String PARCEL_GEOMETRY_TABLE = "parcelGeometry";
    private static final String PARCEL_REGISTRATION_FORM = "parcelRegistration";
    private static final String PARCEL_REGISTRATION_TABLE = "parcelRegistration";
    private static final String ZONE_LOOKUP_FORM = "md_district_eco_zone";

    @Override public String getName()        { return "Parcel Location Auto-Center Binder"; }
    @Override public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override public String getLabel()       { return getName(); }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() {
        return "Extends WorkflowFormBinder. After parcelLocation saves, looks up "
             + "the (district, agroEcologicalZone) centroid from md_district_eco_zone "
             + "and writes auto_center_lat/lon onto the sibling parcelGeometry row "
             + "(linked by parent_id) so the GIS widget centres the map on first "
             + "render of the Location tab. Runs inside the same transaction as "
             + "the parcelLocation commit — no race. ["
             + Build.STAMP + "]";
    }

    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        // Phase 1: commit the parcelLocation row normally. This is the stock
        // WorkflowFormBinder behaviour — persist via storeFormData and update
        // any workflow variables. Anything that goes wrong here MUST propagate
        // — the citizen's save fails. We do NOT swallow super's exceptions.
        FormRowSet result = super.store(element, rows, formData);

        // Phase 2: cascade auto-center to the sibling parcelGeometry row.
        // Wrapped in try/catch — losing centring is acceptable, breaking save
        // is not. We use the rows we just persisted (in-memory) to read
        // district/zone/parent_id, so we don't need an extra DB round-trip.
        try {
            cascadeAutoCenter(rows);
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                    "ParcelLocationAutoCenterBinder cascade failed: " + t.getMessage());
        }

        return result;
    }

    private void cascadeAutoCenter(FormRowSet rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // parcelLocation typically saves a single row per submit; iterate to be
        // safe in case Joget ever passes more.
        for (FormRow row : rows) {
            cascadeAutoCenterForRow(row);
        }
    }

    private void cascadeAutoCenterForRow(FormRow locRow) {
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

        // The in-memory FormRow keys are the form-field ids (camelCase as
        // declared in the form definition). Try both casings just in case
        // Joget's FormData has been round-tripped through Postgres
        // (CLAUDE.md "Postgres unquoted column folding" gotcha).
        String district = nullSafeProp(locRow, "district");
        String zone     = firstNonNull(
                nullSafeProp(locRow, "agroEcologicalZone"),
                nullSafeProp(locRow, "agroecologicalzone"));
        String parentId = nullSafeProp(locRow, "parent_id");

        if (district == null || zone == null) {
            LogUtil.info(CLASS_NAME, "incomplete parcelLocation row — skipping cascade"
                    + " (district=" + district + ", zone=" + zone + ")");
            return;
        }

        // -- Bootstrap path for NEW parcels under partiallyStore=true ---------
        // When the citizen clicks Next from General Data on a fresh parcel,
        // parcelLocation commits BEFORE the wizard parent (parcelRegistration)
        // exists, so AbstractSubForm.populateSubFormWithParentKey couldn't
        // propagate a parent_id (rootFormPrimaryKeyValue was empty at
        // formatData time — see jw-community AbstractSubForm.java line 297).
        //
        // Without parent_id we can't link parcelLocation to its parcelGeometry
        // sibling, and the GIS widget on the next tab won't find auto_center.
        //
        // Fix: mint a UUID for the wizard parent ourselves. Back-fill
        // parcelLocation.parent_id with that UUID so subsequent form renders
        // resolve the wizard's id via AbstractSubForm propagation. We DEFER
        // creating the parcelRegistration row until we know parcelGeometry's
        // id too, so the parcelRegistration row's c_general_data and
        // c_location tab pointers can both be set in one write.
        boolean bootstrapped = false;
        if (parentId == null) {
            parentId = UuidGenerator.getInstance().getUuid();
            bootstrapped = true;
            LogUtil.info(CLASS_NAME, "parcelLocation " + locRow.getId()
                    + " has no parent_id (new parcel under partiallyStore=true)"
                    + " — bootstrapping with parent_id=" + parentId);

            try {
                // Back-fill parcelLocation.parent_id. AppService.storeFormData
                // will UPDATE the row (it exists; super.store() committed it).
                locRow.setProperty("parent_id", parentId);
                FormRowSet locRs = new FormRowSet();
                locRs.add(locRow);
                appService.storeFormData(PARCEL_LOCATION_FORM, PARCEL_LOCATION_TABLE,
                        locRs, locRow.getId());
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t,
                        "back-fill of parcelLocation.parent_id failed for "
                        + locRow.getId());
                return;
            }
        }

        // Lookup the (district, zone) centroid in MD.95.
        String centroidLat = null, centroidLon = null;
        try {
            FormRowSet zoneRows = dao.find(ZONE_LOOKUP_FORM, ZONE_LOOKUP_FORM,
                    "WHERE e.customProperties.district = ? AND e.customProperties.agro_zone = ?",
                    new Object[]{district, zone},
                    null, false, null, null);
            if (zoneRows == null || zoneRows.isEmpty()) {
                LogUtil.info(CLASS_NAME, "no centroid in md_district_eco_zone for ("
                        + district + ", " + zone + ") — leaving auto_center blank;"
                        + " GIS widget will fall back to its built-in geocoding");
                return;
            }
            FormRow zoneRow = zoneRows.get(0);
            centroidLat = nullSafeProp(zoneRow, "centroid_lat");
            centroidLon = nullSafeProp(zoneRow, "centroid_lon");
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "centroid lookup failed for ("
                    + district + ", " + zone + ")");
            return;
        }

        if (centroidLat == null || centroidLon == null) {
            LogUtil.info(CLASS_NAME, "centroid row found but lat/lon missing for ("
                    + district + ", " + zone + ") — skipping");
            return;
        }

        // Find the existing parcelGeometry row (if the citizen has reached the
        // Location tab before, or this is an edit). Otherwise create a minimal
        // stub so the next render of the Location tab has values to centre on.
        FormRow geomRow;
        String geomId;
        boolean isNew = false;
        try {
            FormRowSet geomRows = dao.find(PARCEL_GEOMETRY_FORM, PARCEL_GEOMETRY_TABLE,
                    "WHERE e.customProperties.parent_id = ?", new Object[]{parentId},
                    null, false, null, null);
            if (geomRows != null && !geomRows.isEmpty()) {
                geomRow = geomRows.get(0);
                geomId = geomRow.getId();
            } else {
                geomRow = new FormRow();
                geomId = UuidGenerator.getInstance().getUuid();
                geomRow.setId(geomId);
                geomRow.setProperty("parent_id", parentId);
                isNew = true;
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "failed locating parcelGeometry for parent_id="
                    + parentId);
            return;
        }

        // Set the centring fields. We update only auto_center_lat/lon; geometry,
        // area_hectares, perimeter_meters, vertex_count etc. are preserved
        // because we re-save the entire FormRow we just loaded.
        geomRow.setProperty("auto_center_lat", centroidLat);
        geomRow.setProperty("auto_center_lon", centroidLon);

        try {
            FormRowSet rs = new FormRowSet();
            rs.add(geomRow);
            appService.storeFormData(PARCEL_GEOMETRY_FORM, PARCEL_GEOMETRY_TABLE, rs, geomId);

            LogUtil.info(CLASS_NAME, (isNew ? "created" : "updated")
                    + " parcelGeometry " + geomId
                    + " (parent_id=" + parentId
                    + ", district=" + district
                    + ", zone=" + zone
                    + ", centroid=" + centroidLat + "," + centroidLon + ")");
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "failed storing parcelGeometry " + geomId);
            return;
        }

        // -- Bootstrap completion: write the parcelRegistration row -----------
        // Only do this when we minted parent_id ourselves (bootstrapped=true).
        // The wizard's eventual final-Save will call AppService.storeFormData
        // on parcelRegistration with the same primaryKey value (because Joget
        // reads it from the URL or from formData, which is now linked to our
        // back-filled parent_id) — that path performs an UPDATE on our existing
        // row rather than creating a duplicate.
        if (bootstrapped) {
            try {
                FormRow regRow = new FormRow();
                regRow.setId(parentId);
                regRow.setProperty("general_data", locRow.getId());
                regRow.setProperty("location",     geomId);
                FormRowSet regRs = new FormRowSet();
                regRs.add(regRow);
                appService.storeFormData(PARCEL_REGISTRATION_FORM, PARCEL_REGISTRATION_TABLE,
                        regRs, parentId);
                LogUtil.info(CLASS_NAME, "bootstrapped parcelRegistration " + parentId
                        + " (general_data=" + locRow.getId()
                        + ", location=" + geomId + ")");
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t,
                        "parcelRegistration bootstrap write failed for parent_id=" + parentId);
                // The cascade itself succeeded — auto_center is on parcelGeometry.
                // The orphan parcelLocation/parcelGeometry rows (with our
                // back-filled parent_id pointing to a non-existent
                // parcelRegistration) will be re-linked by the wizard's final
                // Save when AbstractSubForm.formatData propagates the real id.
            }
        }
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

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
}
